package com.inkflow.module.rag.service;

import com.inkflow.module.rag.config.RagProperties;
import com.inkflow.module.rag.dto.CircuitBreakerState;
import com.inkflow.module.rag.dto.SearchResult;
import com.inkflow.module.rag.entity.KnowledgeChunk;
import com.inkflow.module.rag.repository.KnowledgeChunkRepository;
import com.inkflow.module.rag.repository.SimilarityProjection;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 向量嵌入服务（带断路器模式）
 * 调用Spring AI 1.1.2 EmbeddingModel接口生成向量，
 * 集成断路器模式实现容错。
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final EmbeddingCacheService embeddingCacheService;
    private final RagProperties ragProperties;

    private static final int DEFAULT_SEARCH_LIMIT = 10;

    // ==================== 断路器状态 ====================

    /** 断路器当前状态 */
    private final AtomicReference<CircuitBreakerState> circuitBreakerState = 
            new AtomicReference<>(CircuitBreakerState.CLOSED);
    
    /** 连续失败次数 */
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    
    /** 最后一次失败时间 */
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    
    /** 断路器打开时间 */
    private final AtomicLong circuitOpenedTime = new AtomicLong(0);

    @Autowired
    public EmbeddingService(
            EmbeddingModel embeddingModel,
            KnowledgeChunkRepository knowledgeChunkRepository,
            EmbeddingCacheService embeddingCacheService,
            RagProperties ragProperties) {
        this.embeddingModel = embeddingModel;
        this.knowledgeChunkRepository = knowledgeChunkRepository;
        this.embeddingCacheService = embeddingCacheService;
        this.ragProperties = ragProperties;
    }
    
    /**
     * 启动时验证 EmbeddingModel bean 可用性
     * 仅记录日志，不阻止启动（TEI 服务可能稍后启动）
     */
    @PostConstruct
    public void validateEmbeddingModelOnStartup() {
        log.info("检查 EmbeddingModel bean 可用性...");
        
        // 检查 EmbeddingModel bean 是否为 null
        if (embeddingModel == null) {
            log.warn("EmbeddingModel bean 不可用。请确保 Spring AI 配置正确。" +
                "检查 application.yml 中的 spring.ai.openai 配置。");
            return;
        }
        
        log.info("EmbeddingModel bean 已配置。首次调用时将验证连接。");
    }

    // ==================== 断路器配置 ====================
    
    private int getFailureThreshold() {
        return ragProperties.embedding().circuitBreaker().failureThreshold();
    }
    
    private long getRecoveryTimeoutMs() {
        return ragProperties.embedding().circuitBreaker().recoveryTimeoutMs();
    }
    
    private boolean isCircuitBreakerEnabled() {
        return ragProperties.embedding().circuitBreaker().enabled();
    }

    // ==================== 向量生成 API ====================

    /**
     * 生成单个文本的向量嵌入（带断路器保护）
     *
     * @param text 输入文本
     * @return 向量数组
     */
    public Mono<float[]> generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return Mono.empty();
        }

        // 先检查缓存
        return embeddingCacheService.get(text)
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("缓存未命中，调用Embedding API: {}", truncateForLog(text));
                    return callEmbeddingApiWithCircuitBreaker(text)
                            .flatMap(embedding -> 
                                embeddingCacheService.put(text, embedding)
                                    .thenReturn(embedding)
                            );
                }));
    }


    /**
     * 批量生成向量嵌入（带断路器保护）
     *
     * @param texts 文本列表
     * @return 向量列表
     */
    public Mono<List<float[]>> generateEmbeddingsBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Mono.just(Collections.emptyList());
        }

        return callEmbeddingApiBatchWithCircuitBreaker(texts);
    }

    // ==================== 断路器核心逻辑 ====================

    /**
     * 带断路器保护的单个向量生成
     */
    private Mono<float[]> callEmbeddingApiWithCircuitBreaker(String text) {
        if (!isCircuitBreakerEnabled()) {
            return callEmbeddingApi(text);
        }

        // 检查断路器状态
        CircuitBreakerState state = checkAndUpdateCircuitBreakerState();
        
        return switch (state) {
            case OPEN -> {
                long remainingMs = getRemainingRecoveryTime();
                log.warn("断路器打开中，拒绝请求。剩余恢复时间: {}ms", remainingMs);
                yield Mono.error(new CircuitBreakerOpenException(remainingMs));
            }
            case HALF_OPEN -> {
                log.info("断路器半开状态，允许测试请求");
                yield callEmbeddingApi(text)
                        .doOnSuccess(result -> {
                            recordSuccess();
                            log.info("测试请求成功，断路器关闭");
                        })
                        .doOnError(error -> {
                            recordFailure();
                            log.warn("测试请求失败，断路器重新打开: {}", error.getMessage());
                        });
            }
            case CLOSED -> callEmbeddingApi(text)
                    .doOnSuccess(result -> recordSuccess())
                    .doOnError(error -> recordFailure());
        };
    }

    /**
     * 带断路器保护的批量向量生成
     */
    private Mono<List<float[]>> callEmbeddingApiBatchWithCircuitBreaker(List<String> texts) {
        if (!isCircuitBreakerEnabled()) {
            return callEmbeddingApiBatch(texts);
        }

        CircuitBreakerState state = checkAndUpdateCircuitBreakerState();
        
        return switch (state) {
            case OPEN -> {
                long remainingMs = getRemainingRecoveryTime();
                log.warn("断路器打开中，拒绝批量请求。剩余恢复时间: {}ms", remainingMs);
                yield Mono.error(new CircuitBreakerOpenException(remainingMs));
            }
            case HALF_OPEN -> {
                log.info("断路器半开状态，允许批量测试请求");
                yield callEmbeddingApiBatch(texts)
                        .doOnSuccess(result -> {
                            recordSuccess();
                            log.info("批量测试请求成功，断路器关闭");
                        })
                        .doOnError(error -> {
                            recordFailure();
                            log.warn("批量测试请求失败，断路器重新打开: {}", error.getMessage());
                        });
            }
            case CLOSED -> callEmbeddingApiBatch(texts)
                    .doOnSuccess(result -> recordSuccess())
                    .doOnError(error -> recordFailure());
        };
    }


    /**
     * 检查并更新断路器状态
     */
    private CircuitBreakerState checkAndUpdateCircuitBreakerState() {
        CircuitBreakerState currentState = circuitBreakerState.get();
        
        if (currentState == CircuitBreakerState.OPEN) {
            long elapsedSinceOpen = System.currentTimeMillis() - circuitOpenedTime.get();
            if (elapsedSinceOpen >= getRecoveryTimeoutMs()) {
                // 恢复超时已过，转换到半开状态
                if (circuitBreakerState.compareAndSet(CircuitBreakerState.OPEN, CircuitBreakerState.HALF_OPEN)) {
                    log.info("断路器状态变更: OPEN -> HALF_OPEN (恢复超时: {}ms)", elapsedSinceOpen);
                    return CircuitBreakerState.HALF_OPEN;
                }
            }
        }
        
        return circuitBreakerState.get();
    }

    /**
     * 记录成功调用（内部方法）
     */
    private void recordSuccess() {
        CircuitBreakerState previousState = circuitBreakerState.get();
        
        if (previousState == CircuitBreakerState.HALF_OPEN) {
            // 半开状态下成功，关闭断路器
            if (circuitBreakerState.compareAndSet(CircuitBreakerState.HALF_OPEN, CircuitBreakerState.CLOSED)) {
                consecutiveFailures.set(0);
                log.info("断路器状态变更: HALF_OPEN -> CLOSED");
            }
        } else if (previousState == CircuitBreakerState.CLOSED) {
            // 正常状态下成功，重置失败计数
            consecutiveFailures.set(0);
        }
    }

    /**
     * 记录失败调用（内部方法）
     */
    private void recordFailure() {
        lastFailureTime.set(System.currentTimeMillis());
        int failures = consecutiveFailures.incrementAndGet();
        
        CircuitBreakerState currentState = circuitBreakerState.get();
        
        if (currentState == CircuitBreakerState.HALF_OPEN) {
            // 半开状态下失败，重新打开断路器
            if (circuitBreakerState.compareAndSet(CircuitBreakerState.HALF_OPEN, CircuitBreakerState.OPEN)) {
                circuitOpenedTime.set(System.currentTimeMillis());
                log.warn("断路器状态变更: HALF_OPEN -> OPEN (测试请求失败)");
            }
        } else if (currentState == CircuitBreakerState.CLOSED && failures >= getFailureThreshold()) {
            // 达到失败阈值，打开断路器
            if (circuitBreakerState.compareAndSet(CircuitBreakerState.CLOSED, CircuitBreakerState.OPEN)) {
                circuitOpenedTime.set(System.currentTimeMillis());
                log.error("断路器状态变更: CLOSED -> OPEN (连续失败 {} 次)", failures);
            }
        }
    }

    /**
     * 获取剩余恢复时间
     */
    private long getRemainingRecoveryTime() {
        long elapsed = System.currentTimeMillis() - circuitOpenedTime.get();
        return Math.max(0, getRecoveryTimeoutMs() - elapsed);
    }


    // ==================== 断路器状态查询 ====================

    /**
     * 获取当前断路器状态
     */
    public CircuitBreakerState getCircuitBreakerState() {
        return circuitBreakerState.get();
    }

    /**
     * 获取连续失败次数
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    /**
     * 获取最后失败时间
     */
    public long getLastFailureTime() {
        return lastFailureTime.get();
    }

    /**
     * 手动重置断路器
     */
    public void resetCircuitBreaker() {
        circuitBreakerState.set(CircuitBreakerState.CLOSED);
        consecutiveFailures.set(0);
        lastFailureTime.set(0);
        circuitOpenedTime.set(0);
        log.info("断路器已手动重置");
    }

    // ==================== 底层API调用 ====================

    /**
     * 调用Embedding API (内部方法)
     */
    private Mono<float[]> callEmbeddingApi(String text) {
        return Mono.fromCallable(() -> embeddingModel.embed(text))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(v -> log.debug("Embedding生成成功，维度: {}", v.length))
                .doOnError(e -> log.error("Embedding生成失败: {}", e.getMessage()));
    }

    /**
     * 批量调用Embedding API
     */
    private Mono<List<float[]>> callEmbeddingApiBatch(List<String> texts) {
        return Mono.fromCallable(() -> {
            EmbeddingResponse response = embeddingModel.call(
                    new EmbeddingRequest(texts, null)
            );
            return response.getResults().stream()
                    .map(embedding -> embedding.getOutput())
                    .collect(Collectors.toList());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ==================== 向量存储 API ====================

    /**
     * 删除来源实体的所有嵌入
     */
    @Transactional
    public void deleteBySourceId(UUID sourceId) {
        knowledgeChunkRepository.deleteBySourceId(sourceId);
        log.info("删除来源的嵌入: {}", sourceId);
    }

    /**
     * 删除项目的所有嵌入
     */
    @Transactional
    public void deleteByProjectId(UUID projectId) {
        knowledgeChunkRepository.deleteByProjectId(projectId);
        log.info("删除项目的所有嵌入: {}", projectId);
    }

    // ==================== 向量搜索 API ====================

    /**
     * 使用向量相似性搜索相似内容
     *
     * @param projectId 项目ID
     * @param query 查询文本
     * @param limit 最大结果数
     * @return 搜索结果列表
     */
    public Mono<List<SearchResult>> search(UUID projectId, String query, Integer limit) {
        return search(projectId, query, null, limit);
    }

    /**
     * 带来源类型过滤的相似性搜索
     */
    public Mono<List<SearchResult>> search(
            UUID projectId,
            String query,
            String sourceType,
            Integer limit) {

        int searchLimit = limit != null ? limit : DEFAULT_SEARCH_LIMIT;

        return generateEmbedding(query)
                .map(queryEmbedding -> {
                    String vectorString = toVectorString(queryEmbedding);

                    List<KnowledgeChunk> results;
                    if (sourceType != null && !sourceType.isBlank()) {
                        results = knowledgeChunkRepository.findSimilarByProjectIdAndSourceType(
                                projectId, sourceType, vectorString, searchLimit);
                    } else {
                        results = knowledgeChunkRepository.findSimilarByProjectId(
                                projectId, vectorString, searchLimit);
                    }

                    return results.stream()
                            .map(this::toSearchResult)
                            .collect(Collectors.toList());
                })
                .doOnSuccess(results -> log.debug("搜索在项目 {} 中返回 {} 个结果", projectId, results.size()))
                .doOnError(e -> log.error("项目 {} 搜索失败: {}", projectId, e.getMessage()));
    }

    /**
     * 带相似度分数的搜索
     *
     * @param projectId 项目ID
     * @param query 查询文本
     * @param limit 最大结果数
     * @return 带相似度分数的搜索结果列表
     */
    public Mono<List<SearchResult>> searchWithScore(UUID projectId, String query, Integer limit) {
        int searchLimit = limit != null ? limit : DEFAULT_SEARCH_LIMIT;

        return generateEmbedding(query)
                .map(queryEmbedding -> {
                    String vectorString = toVectorString(queryEmbedding);

                    List<SimilarityProjection> results = knowledgeChunkRepository.findSimilarWithScore(
                            projectId, vectorString, searchLimit);

                    return results.stream()
                            .map(this::toSearchResultWithScore)
                            .collect(Collectors.toList());
                });
    }


    // ==================== 统计 API ====================

    /**
     * 获取项目的嵌入统计信息
     */
    public Map<String, Long> getStatistics(UUID projectId) {
        Map<String, Long> stats = new HashMap<>();
        stats.put("total", knowledgeChunkRepository.countByProjectId(projectId));
        stats.put("storyBlocks", knowledgeChunkRepository.countByProjectIdAndSourceType(projectId, KnowledgeChunk.SOURCE_TYPE_STORY_BLOCK));
        stats.put("characters", knowledgeChunkRepository.countByProjectIdAndSourceType(projectId, KnowledgeChunk.SOURCE_TYPE_CHARACTER));
        stats.put("wikiEntries", knowledgeChunkRepository.countByProjectIdAndSourceType(projectId, KnowledgeChunk.SOURCE_TYPE_WIKI_ENTRY));
        stats.put("dirty", knowledgeChunkRepository.countByProjectIdAndIsDirtyTrue(projectId));
        return stats;
    }

    // ==================== 辅助方法 ====================

    private String toVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private String truncateContent(String content) {
        if (content.length() <= 10000) {
            return content;
        }
        return content.substring(0, 10000) + "...";
    }

    private String truncateForLog(String text) {
        if (text.length() <= 50) {
            return text;
        }
        return text.substring(0, 50) + "...";
    }

    private SearchResult toSearchResult(KnowledgeChunk chunk) {
        return SearchResult.builder()
                .id(chunk.getId())
                .sourceType(chunk.getSourceType())
                .sourceId(chunk.getSourceId())
                .content(chunk.getContent())
                .metadata(chunk.getMetadata())
                .chunkLevel(chunk.getChunkLevel())
                .parentId(chunk.getParentId())
                .build();
    }

    private SearchResult toSearchResultWithScore(SimilarityProjection projection) {
        SearchResult result = SearchResult.builder()
                .id(projection.getId())
                .sourceType(projection.getSourceType())
                .sourceId(projection.getSourceId())
                .content(projection.getContent())
                .chunkLevel(projection.getChunkLevel())
                .parentId(projection.getParentId())
                .blockOrder(projection.getChunkOrder())
                .build();
        result.setCosineDistanceAndCalculateSimilarity(projection.getCosineDistance());
        return result;
    }

    // ==================== 异常类 ====================

    /**
     * 断路器打开异常
     */
    public static class CircuitBreakerOpenException extends RuntimeException {
        @Getter
        private final long remainingRecoveryTimeMs;

        public CircuitBreakerOpenException(long remainingRecoveryTimeMs) {
            super("Circuit breaker is open, retry after " + remainingRecoveryTimeMs + "ms");
            this.remainingRecoveryTimeMs = remainingRecoveryTimeMs;
        }
    }
    
    /**
     * EmbeddingService 启动异常
     * 当 EmbeddingModel bean 不可用或验证失败时抛出
     */
    public static class EmbeddingServiceStartupException extends RuntimeException {
        
        public EmbeddingServiceStartupException(String message) {
            super(message);
        }
        
        public EmbeddingServiceStartupException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
