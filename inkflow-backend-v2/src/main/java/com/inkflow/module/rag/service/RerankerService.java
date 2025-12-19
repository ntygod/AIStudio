package com.inkflow.module.rag.service;

import com.inkflow.module.rag.config.RagProperties;
import com.inkflow.module.rag.dto.CircuitBreakerState;
import com.inkflow.module.rag.dto.RerankerCacheStatistics;
import com.inkflow.module.rag.dto.RerankResult;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 重排序服务 - 整合本地BGE模型调用、断路器模式和缓存功能
 * 功能特性:
 * 1. 本地 bge-reranker-v2-m3 模型调用
 * 2. 断路器模式（失败阈值3，恢复超时20秒）
 * 3. 内存缓存（5分钟过期）
 * 4. 降级到基于得分的排序
 * 
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Service
public class RerankerService {

    private final WebClient webClient;
    private final RagProperties.RerankerConfig config;
    
    // 断路器状态
    private volatile CircuitBreakerState circuitBreakerState = CircuitBreakerState.CLOSED;
    private volatile long lastFailureTime = 0;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    
    // 缓存
    private final ConcurrentHashMap<String, CacheEntry<?>> memoryCache = new ConcurrentHashMap<>();
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong totalRequests = new AtomicLong(0);
    
    // 服务可用性状态 - 启动验证后设置
    @Getter
    private final AtomicBoolean serviceDisabled = new AtomicBoolean(false);
    @Getter
    private volatile String disabledReason = null;

    public RerankerService(WebClient.Builder webClientBuilder, RagProperties ragProperties) {
        this.config = ragProperties.reranker();
        this.webClient = webClientBuilder
            .baseUrl(config.endpoint())
            .build();
    }
    
    /**
     * 启动时验证 Reranker 端点可达性
     * 如果端点不可达，将禁用重排序功能并记录错误日志
     */
    @PostConstruct
    public void validateEndpointOnStartup() {
        if (!config.enabled()) {
            log.info("Reranker service is disabled by configuration");
            return;
        }
        
        log.info("Validating Reranker endpoint: {}", config.endpoint());
        
        try {
            // 发送健康检查请求
            Boolean isHealthy = performHealthCheck()
                .timeout(Duration.ofMillis(config.timeoutMs()))
                .onErrorReturn(false)
                .block(Duration.ofSeconds(10));
            
            if (Boolean.TRUE.equals(isHealthy)) {
                log.info("Reranker endpoint validation successful: {}", config.endpoint());
            } else {
                disableService("Reranker endpoint health check failed: " + config.endpoint());
            }
        } catch (Exception e) {
            disableService("Reranker endpoint unreachable: " + config.endpoint() + " - " + e.getMessage());
        }
    }
    
    /**
     * 执行健康检查
     */
    private Mono<Boolean> performHealthCheck() {
        // 尝试调用 rerank API 进行简单测试
        return webClient.get()
            .uri("/health")
            .retrieve()
            .toBodilessEntity()
            .map(response -> response.getStatusCode().is2xxSuccessful())
            .onErrorResume(e -> {
                // 如果 /health 端点不存在，尝试用简单的 rerank 请求测试
                log.debug("Health endpoint not available, trying rerank test: {}", e.getMessage());
                return testRerankEndpoint();
            });
    }
    
    /**
     * 通过简单的 rerank 请求测试端点可用性
     */
    private Mono<Boolean> testRerankEndpoint() {
        Map<String, Object> testRequest = Map.of(
            "query", "test",
            "texts", List.of("test document"),
            "top_k", 1
        );
        
        return webClient.post()
            .uri(config.apiPath())
            .bodyValue(testRequest)
            .retrieve()
            .toBodilessEntity()
            .map(response -> response.getStatusCode().is2xxSuccessful())
            .onErrorResume(WebClientResponseException.class, e -> {
                // 4xx 错误表示端点可达但请求格式可能有问题，仍然认为服务可用
                if (e.getStatusCode().is4xxClientError()) {
                    log.debug("Reranker endpoint reachable (returned {})", e.getStatusCode());
                    return Mono.just(true);
                }
                return Mono.just(false);
            })
            .onErrorReturn(false);
    }
    
    /**
     * 禁用服务并记录原因
     */
    private void disableService(String reason) {
        serviceDisabled.set(true);
        disabledReason = reason;
        log.error("Reranker service disabled: {}", reason);
        log.warn("Reranking functionality will fall back to score-based ranking");
    }
    
    /**
     * 检查服务是否因启动验证失败而被禁用
     */
    public boolean isDisabledDueToStartupValidation() {
        return serviceDisabled.get();
    }

    /**
     * 重排序检索结果
     * 
     * @param query 查询文本
     * @param candidates 候选文本列表
     * @param topK 返回结果数量（可选，默认返回全部）
     * @return 重排序后的结果（按得分降序排列）
     */
    public Mono<List<RerankResult>> rerank(String query, List<String> candidates, Integer topK) {
        if (!config.enabled()) {
            log.debug("Reranker is disabled by configuration, returning original order with default scores");
            return Mono.just(createDefaultResults(candidates));
        }
        
        // 检查是否因启动验证失败而被禁用
        if (serviceDisabled.get()) {
            log.debug("Reranker is disabled due to startup validation failure, falling back to score-based ranking");
            return fallbackToScoreBasedRanking(query, candidates, topK);
        }
        
        if (query == null || query.trim().isEmpty()) {
            log.warn("Empty query provided for reranking");
            return Mono.just(List.of());
        }
        
        if (candidates == null || candidates.isEmpty()) {
            log.warn("Empty candidates provided for reranking");
            return Mono.just(List.of());
        }
        
        // 过滤空候选文本
        List<String> validCandidates = candidates.stream()
            .filter(doc -> doc != null && !doc.trim().isEmpty())
            .toList();
        
        if (validCandidates.isEmpty()) {
            log.warn("No valid candidates after filtering for reranking");
            return Mono.just(List.of());
        }
        
        totalRequests.incrementAndGet();
        
        // 检查缓存
        if (config.enableCache()) {
            String cacheKey = generateRerankCacheKey(query, validCandidates, topK);
            CacheEntry<?> cachedEntry = memoryCache.get(cacheKey);
            if (cachedEntry != null && !cachedEntry.isExpired() && cachedEntry.getValue() instanceof List) {
                cacheHits.incrementAndGet();
                log.debug("Reranker cache hit for query: {} chars, {} candidates", query.length(), validCandidates.size());
                @SuppressWarnings("unchecked")
                List<RerankResult> results = (List<RerankResult>) cachedEntry.getValue();
                return Mono.just(results);
            }
            cacheMisses.incrementAndGet();
        }
        
        log.debug("Reranking {} candidates for query: {}", validCandidates.size(), 
            query.length() > 50 ? query.substring(0, 50) + "..." : query);
        
        return rerankWithResilience(query, validCandidates, topK);
    }


    /**
     * 计算两个文本的相似度得分
     * 用于语义断崖检测
     * 
     * @param text1 文本1
     * @param text2 文本2
     * @return 相似度得分（0-1）
     */
    public Mono<Double> calculateSimilarity(String text1, String text2) {
        if (!config.enabled()) {
            log.debug("Reranker is disabled by configuration, returning default similarity");
            return Mono.just(0.5);
        }
        
        // 检查是否因启动验证失败而被禁用
        if (serviceDisabled.get()) {
            log.debug("Reranker is disabled due to startup validation failure, falling back to simple similarity");
            return fallbackToSimpleSimilarity(text1, text2);
        }
        
        if (text1 == null || text1.trim().isEmpty() || 
            text2 == null || text2.trim().isEmpty()) {
            log.warn("Empty text provided for similarity calculation");
            return Mono.just(0.0);
        }
        
        totalRequests.incrementAndGet();
        
        // 检查缓存
        if (config.enableCache()) {
            String cacheKey = generateSimilarityCacheKey(text1, text2);
            CacheEntry<?> cachedEntry = memoryCache.get(cacheKey);
            if (cachedEntry != null && !cachedEntry.isExpired() && cachedEntry.getValue() instanceof Double) {
                cacheHits.incrementAndGet();
                log.debug("Similarity cache hit for texts: {} chars, {} chars", text1.length(), text2.length());
                return Mono.just((Double) cachedEntry.getValue());
            }
            cacheMisses.incrementAndGet();
        }
        
        return calculateSimilarityWithResilience(text1, text2);
    }

    /**
     * 批量计算相邻句子的相似度
     * 
     * @param sentences 句子列表
     * @return 相似度列表（长度 = sentences.size() - 1）
     */
    public Mono<List<Double>> calculateAdjacentSimilarities(List<String> sentences) {
        if (sentences == null || sentences.size() < 2) {
            log.warn("Need at least 2 sentences for adjacent similarity calculation");
            return Mono.just(List.of());
        }
        
        // 过滤空句子
        List<String> validSentences = sentences.stream()
            .filter(sentence -> sentence != null && !sentence.trim().isEmpty())
            .collect(Collectors.toList());
        
        if (validSentences.size() < 2) {
            log.warn("Need at least 2 valid sentences for adjacent similarity calculation");
            return Mono.just(List.of());
        }
        
        // 构建相邻句子对的相似度计算任务
        List<Mono<Double>> similarityMonos = new ArrayList<>();
        for (int i = 0; i < validSentences.size() - 1; i++) {
            String sentence1 = validSentences.get(i);
            String sentence2 = validSentences.get(i + 1);
            similarityMonos.add(calculateSimilarity(sentence1, sentence2)
                .onErrorReturn(0.0));
        }
        
        if (similarityMonos.isEmpty()) {
            return Mono.just(List.of());
        }
        
        // 特殊处理：只有一个相似度计算任务时
        if (similarityMonos.size() == 1) {
            return similarityMonos.get(0)
                .map(similarity -> {
                    List<Double> result = new ArrayList<>();
                    result.add(similarity);
                    return result;
                })
                .onErrorReturn(List.of())
                .defaultIfEmpty(List.of());
        }
        
        // 并行计算所有相似度
        return Mono.zip(similarityMonos, results -> {
            List<Double> similarities = new ArrayList<>();
            for (Object result : results) {
                if (result instanceof Double) {
                    similarities.add((Double) result);
                } else {
                    similarities.add(0.0);
                }
            }
            return similarities;
        }).doOnNext(similarities -> 
            log.debug("Calculated {} adjacent similarities", similarities.size()))
        .onErrorReturn(List.of())
        .defaultIfEmpty(List.of());
    }


    // ==================== 断路器相关方法 ====================

    /**
     * 带弹性处理的重排序
     */
    private Mono<List<RerankResult>> rerankWithResilience(String query, List<String> candidates, Integer topK) {
        // 检查断路器状态
        if (isCircuitBreakerOpen()) {
            log.warn("Circuit breaker is open, falling back to score-based ranking immediately");
            return fallbackToScoreBasedRanking(query, candidates, topK);
        }
        
        return callRerankAPI(query, candidates, topK)
            .doOnNext(results -> {
                recordSuccess();
                // 缓存结果
                if (config.enableCache()) {
                    String cacheKey = generateRerankCacheKey(query, candidates, topK);
                    cacheResult(cacheKey, results);
                }
            })
            .doOnError(error -> recordFailure())
            .onErrorResume(error -> {
                log.warn("Reranker API failed: {}, attempting fallback", error.getMessage());
                return fallbackToScoreBasedRanking(query, candidates, topK);
            });
    }

    /**
     * 带弹性处理的相似度计算
     */
    private Mono<Double> calculateSimilarityWithResilience(String text1, String text2) {
        // 检查断路器状态
        if (isCircuitBreakerOpen()) {
            log.warn("Circuit breaker is open, falling back to simple similarity immediately");
            return fallbackToSimpleSimilarity(text1, text2);
        }
        
        return callSimilarityAPI(text1, text2)
            .doOnNext(similarity -> {
                recordSuccess();
                // 缓存结果
                if (config.enableCache()) {
                    String cacheKey = generateSimilarityCacheKey(text1, text2);
                    cacheResult(cacheKey, similarity);
                }
            })
            .doOnError(error -> recordFailure())
            .onErrorResume(error -> {
                log.warn("Similarity API failed: {}, attempting fallback", error.getMessage());
                return fallbackToSimpleSimilarity(text1, text2);
            });
    }

    /**
     * 检查断路器是否开启
     */
    private boolean isCircuitBreakerOpen() {
        if (!config.circuitBreaker().enabled()) {
            return false;
        }
        
        if (circuitBreakerState == CircuitBreakerState.CLOSED) {
            return false;
        }
        
        // 检查是否到了恢复时间
        if (circuitBreakerState == CircuitBreakerState.OPEN) {
            long timeSinceLastFailure = System.currentTimeMillis() - lastFailureTime;
            if (timeSinceLastFailure > config.circuitBreaker().recoveryTimeoutMs()) {
                log.info("Circuit breaker recovery timeout reached, transitioning to HALF_OPEN");
                circuitBreakerState = CircuitBreakerState.HALF_OPEN;
                return false; // 允许一个测试请求
            }
        }
        
        return circuitBreakerState == CircuitBreakerState.OPEN;
    }

    /**
     * 记录成功调用
     */
    private void recordSuccess() {
        if (consecutiveFailures.get() > 0 || circuitBreakerState != CircuitBreakerState.CLOSED) {
            log.info("Reranker service recovered after {} failures, closing circuit breaker", 
                consecutiveFailures.get());
            consecutiveFailures.set(0);
            circuitBreakerState = CircuitBreakerState.CLOSED;
        }
    }

    /**
     * 记录失败调用
     */
    private void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        lastFailureTime = System.currentTimeMillis();
        
        if (failures >= config.circuitBreaker().failureThreshold() && 
            circuitBreakerState != CircuitBreakerState.OPEN) {
            log.error("Opening reranker circuit breaker after {} consecutive failures", failures);
            circuitBreakerState = CircuitBreakerState.OPEN;
        }
    }


    // ==================== API调用方法 ====================

    /**
     * 调用 Reranker API
     */
    private Mono<List<RerankResult>> callRerankAPI(String query, List<String> candidates, Integer topK) {
        Map<String, Object> requestBody = Map.of(
            "query", query,
            "texts", candidates,
            "top_k", topK != null ? topK : candidates.size()
        );
        
        return webClient.post()
            .uri(config.apiPath())
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(RerankResponse.class)
            .timeout(Duration.ofMillis(config.timeoutMs()))
            .retryWhen(createRetrySpec())
            .map(response -> response.getData().stream()
                .map(data -> RerankResult.of(data.getIndex(), data.getScore(), data.getDocument()))
                .toList())
            .doOnSubscribe(subscription -> 
                log.debug("Calling rerank API with {} candidates", candidates.size()))
            .doOnNext(results -> 
                log.debug("Rerank API returned {} results", results.size()))
            .onErrorMap(WebClientResponseException.class, this::mapWebClientException);
    }

    /**
     * 调用 Similarity API
     */
    private Mono<Double> callSimilarityAPI(String text1, String text2) {
        String similarityPath = buildSimilarityApiPath();
        
        Map<String, String> requestBody = Map.of(
            "text1", text1.trim(),
            "text2", text2.trim()
        );
        
        return webClient.post()
            .uri(similarityPath)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(SimilarityResponse.class)
            .timeout(Duration.ofMillis(config.timeoutMs()))
            .retryWhen(createRetrySpec())
            .map(SimilarityResponse::getSimilarity)
            .doOnSubscribe(subscription -> 
                log.debug("Calculating similarity between texts: {} chars vs {} chars", 
                    text1.length(), text2.length()))
            .doOnNext(similarity -> 
                log.debug("Similarity calculated: {}", similarity))
            .onErrorMap(WebClientResponseException.class, this::mapWebClientException);
    }

    /**
     * 构建 Similarity API 路径
     */
    private String buildSimilarityApiPath() {
        String apiPath = config.apiPath();
        if (apiPath.contains("/rerank")) {
            return apiPath.replace("/rerank", "/similarity");
        }
        return "/similarity";
    }

    /**
     * 创建重试策略
     */
    private Retry createRetrySpec() {
        return Retry.backoff(config.maxRetries(), Duration.ofMillis(config.retryDelayMs()))
            .maxBackoff(Duration.ofSeconds(1))
            .filter(this::isRetryableException)
            .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                Throwable lastException = retrySignal.failure();
                log.error("Max retries ({}) exceeded for reranker service", config.maxRetries(), lastException);
                return new RuntimeException("Reranker service max retries exceeded", lastException);
            });
    }

    /**
     * 判断异常是否可重试
     */
    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof WebClientResponseException webClientException) {
            int statusCode = webClientException.getStatusCode().value();
            return statusCode >= 500 || statusCode == 429;
        }
        return throwable instanceof java.net.ConnectException ||
               throwable instanceof java.util.concurrent.TimeoutException;
    }

    /**
     * 映射 WebClient 异常
     */
    private RuntimeException mapWebClientException(WebClientResponseException ex) {
        int statusCode = ex.getStatusCode().value();
        String responseBody = ex.getResponseBodyAsString();
        
        return switch (statusCode) {
            case 400 -> new IllegalArgumentException("Invalid request to reranker API: " + responseBody);
            case 401 -> new SecurityException("Unauthorized access to reranker API: " + responseBody);
            case 429 -> new RuntimeException("Rate limit exceeded for reranker API: " + responseBody);
            default -> new RuntimeException("Reranker API error (status " + statusCode + "): " + responseBody);
        };
    }


    // ==================== 降级方法 ====================

    /**
     * 降级到基于得分的排序
     */
    private Mono<List<RerankResult>> fallbackToScoreBasedRanking(String query, List<String> candidates, Integer topK) {
        if (!config.enableFallback()) {
            return Mono.error(new RuntimeException("Reranker service unavailable and fallback disabled"));
        }
        
        log.info("Falling back to score-based ranking for {} candidates", candidates.size());
        
        return Mono.fromCallable(() -> {
            List<RerankResult> results = new ArrayList<>();
            
            for (int i = 0; i < candidates.size(); i++) {
                String doc = candidates.get(i);
                double score = calculateFallbackScore(query, doc);
                results.add(RerankResult.of(i, score, doc));
            }
            
            // 按得分降序排序
            results.sort(RerankResult::compareByScore);
            
            // 返回topK结果
            int limit = topK != null ? Math.min(topK, results.size()) : results.size();
            return results.subList(0, limit);
        });
    }

    /**
     * 降级到简单相似度计算
     */
    private Mono<Double> fallbackToSimpleSimilarity(String text1, String text2) {
        if (!config.enableFallback()) {
            return Mono.error(new RuntimeException("Reranker service unavailable and fallback disabled"));
        }
        
        log.debug("Falling back to simple similarity calculation");
        
        return Mono.fromCallable(() -> calculateSimpleSimilarity(text1, text2));
    }

    /**
     * 计算降级得分（基于关键词匹配和文本长度）
     */
    private double calculateFallbackScore(String query, String document) {
        if (query == null || document == null) {
            return 0.0;
        }
        
        String queryLower = query.toLowerCase();
        String docLower = document.toLowerCase();
        
        // 关键词匹配得分
        String[] queryWords = queryLower.split("\\s+");
        int matches = 0;
        for (String word : queryWords) {
            if (word.length() > 1 && docLower.contains(word)) {
                matches++;
            }
        }
        
        double keywordScore = queryWords.length > 0 ? (double) matches / queryWords.length : 0.0;
        
        // 文本长度得分（适中长度得分更高）
        double lengthScore = Math.min(1.0, document.length() / 500.0);
        if (document.length() > 1000) {
            lengthScore = Math.max(0.5, 1.0 - (document.length() - 1000) / 2000.0);
        }
        
        return keywordScore * 0.7 + lengthScore * 0.3;
    }

    /**
     * 计算简单相似度（基于词汇重叠的Jaccard相似度）
     */
    private double calculateSimpleSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) {
            return 0.0;
        }
        
        String[] words1 = text1.toLowerCase().split("\\s+");
        String[] words2 = text2.toLowerCase().split("\\s+");
        
        Set<String> set1 = new HashSet<>(Arrays.asList(words1));
        Set<String> set2 = new HashSet<>(Arrays.asList(words2));
        
        // 计算交集
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        
        // 计算并集
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    /**
     * 创建默认结果（当reranker禁用时）
     */
    private List<RerankResult> createDefaultResults(List<String> candidates) {
        List<RerankResult> results = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            results.add(RerankResult.of(i, 1.0, candidates.get(i)));
        }
        return results;
    }


    // ==================== 缓存相关方法 ====================

    /**
     * 缓存结果
     */
    private <T> void cacheResult(String cacheKey, T value) {
        CacheEntry<T> entry = new CacheEntry<>(value, 
            System.currentTimeMillis() + config.cacheExpirationMs());
        memoryCache.put(cacheKey, entry);
        
        // 检查缓存大小限制
        if (memoryCache.size() > config.cacheMaxSize()) {
            evictOldestEntries();
        }
        
        log.debug("Cached result for key: {}", cacheKey.substring(0, Math.min(16, cacheKey.length())));
    }

    /**
     * 清理最旧的缓存条目
     */
    private void evictOldestEntries() {
        int targetSize = (int) (config.cacheMaxSize() * 0.8);
        int toEvict = memoryCache.size() - targetSize;
        
        if (toEvict <= 0) {
            return;
        }
        
        memoryCache.entrySet().stream()
            .sorted((e1, e2) -> Long.compare(e1.getValue().getExpirationTime(), e2.getValue().getExpirationTime()))
            .limit(toEvict)
            .forEach(entry -> memoryCache.remove(entry.getKey()));
        
        log.debug("Evicted {} oldest cache entries, cache size now: {}", toEvict, memoryCache.size());
    }

    /**
     * 生成重排序缓存键
     */
    private String generateRerankCacheKey(String query, List<String> candidates, Integer topK) {
        StringBuilder sb = new StringBuilder();
        sb.append("rerank_").append(query.hashCode()).append("_");
        sb.append(candidates.hashCode()).append("_").append(topK);
        return calculateHash(sb.toString());
    }

    /**
     * 生成相似度缓存键（确保顺序无关）
     */
    private String generateSimilarityCacheKey(String text1, String text2) {
        String key = text1.compareTo(text2) < 0 ? 
            "sim_" + text1.hashCode() + "_" + text2.hashCode() :
            "sim_" + text2.hashCode() + "_" + text1.hashCode();
        return calculateHash(key);
    }

    /**
     * 计算哈希值
     */
    private String calculateHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(input.hashCode());
        }
    }

    /**
     * 清除所有缓存
     */
    public void clearCache() {
        memoryCache.clear();
        cacheHits.set(0);
        cacheMisses.set(0);
        totalRequests.set(0);
        log.info("All reranker caches cleared");
    }

    /**
     * 获取缓存统计信息
     */
    public RerankerCacheStatistics getCacheStatistics() {
        return RerankerCacheStatistics.create(
            cacheHits.get(),
            cacheMisses.get(),
            memoryCache.size(),
            config.cacheMaxSize()
        );
    }


    // ==================== 监控方法 ====================

    /**
     * 获取断路器状态
     */
    public CircuitBreakerState getCircuitBreakerState() {
        return circuitBreakerState;
    }

    /**
     * 获取连续失败次数
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    /**
     * 手动重置断路器
     */
    public void resetCircuitBreaker() {
        log.info("Manually resetting reranker circuit breaker");
        circuitBreakerState = CircuitBreakerState.CLOSED;
        consecutiveFailures.set(0);
        lastFailureTime = 0;
    }

    /**
     * 检查服务是否可用
     * 
     * 服务不可用的情况：
     * 1. 配置中禁用
     * 2. 启动验证失败导致禁用
     * 3. 断路器打开
     */
    public boolean isServiceAvailable() {
        return config.enabled() && !serviceDisabled.get() && circuitBreakerState != CircuitBreakerState.OPEN;
    }
    
    /**
     * 手动重新验证端点并尝试启用服务
     * 
     * @return 验证是否成功
     */
    public Mono<Boolean> revalidateEndpoint() {
        if (!config.enabled()) {
            return Mono.just(false);
        }
        
        log.info("Re-validating Reranker endpoint: {}", config.endpoint());
        
        return performHealthCheck()
            .timeout(Duration.ofMillis(config.timeoutMs()))
            .doOnNext(isHealthy -> {
                if (isHealthy) {
                    serviceDisabled.set(false);
                    disabledReason = null;
                    resetCircuitBreaker();
                    log.info("Reranker service re-enabled after successful validation");
                } else {
                    log.warn("Reranker endpoint re-validation failed");
                }
            })
            .onErrorResume(e -> {
                log.error("Reranker endpoint re-validation error: {}", e.getMessage());
                return Mono.just(false);
            });
    }

    // ==================== 内部类 ====================

    /**
     * 缓存条目
     */
    private static class CacheEntry<T> {
        private final T value;
        private final long expirationTime;
        
        public CacheEntry(T value, long expirationTime) {
            this.value = value;
            this.expirationTime = expirationTime;
        }
        
        public T getValue() {
            return value;
        }
        
        public long getExpirationTime() {
            return expirationTime;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }
    }

    /**
     * Rerank API 响应数据结构
     */
    @Data
    private static class RerankResponse {
        private String object;
        private List<RerankData> data;
        private String model;
    }

    /**
     * 单个 Rerank 结果数据
     */
    @Data
    private static class RerankData {
        private int index;
        private double score;
        private String document;
    }

    /**
     * 相似度 API 响应数据结构
     */
    @Data
    private static class SimilarityResponse {
        private double similarity;
    }
}
