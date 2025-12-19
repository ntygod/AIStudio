package com.inkflow.module.rag.service;

import com.inkflow.module.rag.config.RagProperties;
import com.inkflow.module.rag.dto.RerankResult;
import com.inkflow.module.rag.dto.SearchResult;
import com.inkflow.module.rag.entity.KnowledgeChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合检索服务 - 使用RRF (Reciprocal Rank Fusion) 算法
 * 结合向量检索和全文检索，使用RRF算法融合结果。
 * 支持并行检索、优雅降级和可选的重排序。
 * RRF公式: Score = 1.0 / (k + rank)，其中k=60
 * 
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private final EmbeddingService embeddingService;
    private final FullTextSearchService fullTextSearchService;
    private final RerankerService rerankerService;
    private final RagProperties ragProperties;
    @Nullable
    private final ParentChildSearchService parentChildSearchService;

    /**
     * RRF常数k，默认60
     * 公式: Score = 1.0 / (k + rank)
     */
    private static final double DEFAULT_RRF_K = 60.0;

    // ==================== 主要搜索API ====================

    /**
     * 执行混合检索
     * 并行执行向量检索和全文检索，使用RRF算法融合结果。
     * 
     * @param projectId 项目ID
     * @param query 查询文本
     * @param limit 结果数量限制
     * @return 融合后的搜索结果
     */
    public Mono<List<SearchResult>> search(UUID projectId, String query, Integer limit) {
        int searchLimit = limit != null ? limit : getDefaultTopK();
        int recallLimit = searchLimit * getRecallMultiplier();

        log.debug("Hybrid search: projectId={}, query='{}', limit={}, recallLimit={}", 
                projectId, truncateForLog(query), searchLimit, recallLimit);

        // 并行执行向量检索和全文检索
        return Mono.zip(
                // 向量检索
                executeVectorSearch(projectId, query, recallLimit),
                // 全文检索（包装为Mono以支持并行）
                executeFullTextSearch(projectId, query, recallLimit)
        ).flatMap(tuple -> {
            List<SearchResult> vectorResults = tuple.getT1();
            List<SearchResult> fullTextResults = tuple.getT2();

            log.debug("Retrieved {} vector results and {} full-text results", 
                    vectorResults.size(), fullTextResults.size());

            // 应用RRF融合算法
            List<SearchResult> fusedResults = applyReciprocalRankFusion(
                    vectorResults, fullTextResults, searchLimit);

            // 可选：应用重排序
            if (isRerankerEnabled() && !fusedResults.isEmpty()) {
                return applyReranking(query, fusedResults, searchLimit);
            }

            return Mono.just(fusedResults);
        }).onErrorResume(error -> {
            log.error("Hybrid search failed: {}", error.getMessage());
            return Mono.just(Collections.emptyList());
        });
    }

    /**
     * 按来源类型过滤的混合检索
     */
    public Mono<List<SearchResult>> searchBySourceType(
            UUID projectId,
            String sourceType,
            String query,
            Integer limit) {

        int searchLimit = limit != null ? limit : getDefaultTopK();
        int recallLimit = searchLimit * getRecallMultiplier();

        return Mono.zip(
                executeVectorSearchBySourceType(projectId, sourceType, query, recallLimit),
                executeFullTextSearchBySourceType(projectId, sourceType, query, recallLimit)
        ).flatMap(tuple -> {
            List<SearchResult> fusedResults = applyReciprocalRankFusion(
                    tuple.getT1(), tuple.getT2(), searchLimit);

            if (isRerankerEnabled() && !fusedResults.isEmpty()) {
                return applyReranking(query, fusedResults, searchLimit);
            }

            return Mono.just(fusedResults);
        }).onErrorResume(error -> {
            log.error("Hybrid search by source type failed: {}", error.getMessage());
            return Mono.just(Collections.emptyList());
        });
    }

    /**
     * 为AI生成构建上下文
     * 
     * @param projectId 项目ID
     * @param query 查询文本
     * @param limit 最大结果数
     * @return 格式化的上下文字符串
     */
    public Mono<String> buildContextForGeneration(UUID projectId, String query, int limit) {
        return search(projectId, query, limit)
                .map(this::formatResultsAsContext);
    }

    // ==================== RRF融合算法 ====================

    /**
     * 应用RRF (Reciprocal Rank Fusion) 算法
     * 
     * RRF公式: Score = 1.0 / (k + rank)
     * 对于同时出现在两个结果集中的文档，累加其RRF分数。
     * 
     * @param vectorResults 向量检索结果
     * @param fullTextResults 全文检索结果
     * @param limit 结果数量限制
     * @return 融合后的结果（按RRF分数降序排列）
     */
    public List<SearchResult> applyReciprocalRankFusion(
            List<SearchResult> vectorResults,
            List<SearchResult> fullTextResults,
            int limit) {

        double rrfK = getRrfK();
        
        // 使用sourceId作为去重键的RRF分数映射
        Map<UUID, Double> rrfScores = new LinkedHashMap<>();
        // 保存每个sourceId对应的SearchResult
        Map<UUID, SearchResult> resultMap = new LinkedHashMap<>();

        // 处理向量检索结果 - rank从1开始
        for (int rank = 0; rank < vectorResults.size(); rank++) {
            SearchResult result = vectorResults.get(rank);
            UUID key = result.getSourceId();
            double rrfScore = 1.0 / (rrfK + rank + 1); // rank从1开始
            
            rrfScores.merge(key, rrfScore, Double::sum);
            resultMap.putIfAbsent(key, result);
        }

        // 处理全文检索结果 - rank从1开始
        for (int rank = 0; rank < fullTextResults.size(); rank++) {
            SearchResult result = fullTextResults.get(rank);
            UUID key = result.getSourceId();
            double rrfScore = 1.0 / (rrfK + rank + 1); // rank从1开始
            
            rrfScores.merge(key, rrfScore, Double::sum);
            resultMap.putIfAbsent(key, result);
        }

        // 按RRF分数降序排序并设置分数
        return rrfScores.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                .limit(limit)
                .map(entry -> {
                    SearchResult result = resultMap.get(entry.getKey());
                    result.setRrfScore(entry.getValue());
                    return result;
                })
                .collect(Collectors.toList());
    }

    // ==================== 向量检索 ====================

    /**
     * 执行向量检索（带优雅降级）
     * 根据配置选择使用父子块检索策略或传统向量检索。
     * 
     * 策略选择逻辑：
     * 1. 如果 ragProperties.search().useParentChild() 为 true 且 parentChildSearchService 可用，使用父子块检索
     * 2. 否则使用传统的 EmbeddingService.searchWithScore()
     * 3. 父子块检索失败时自动降级到传统检索
     */
    private Mono<List<SearchResult>> executeVectorSearch(UUID projectId, String query, int limit) {
        // 检查是否启用父子块检索策略
        if (isParentChildSearchEnabled()) {
            log.debug("Using parent-child search strategy for projectId={}", projectId);
            return parentChildSearchService.search(projectId, query, limit)
                    .doOnNext(results -> log.debug("Parent-child search returned {} results", results.size()))
                    .onErrorResume(error -> {
                        log.warn("Parent-child search failed, falling back to traditional vector search: {}", 
                                error.getMessage());
                        return executeTraditionalVectorSearch(projectId, query, limit);
                    });
        }
        
        return executeTraditionalVectorSearch(projectId, query, limit);
    }

    /**
     * 执行传统向量检索（直接使用 EmbeddingService）
     */
    private Mono<List<SearchResult>> executeTraditionalVectorSearch(UUID projectId, String query, int limit) {
        return embeddingService.searchWithScore(projectId, query, limit)
                .doOnNext(results -> log.debug("Traditional vector search returned {} results", results.size()))
                .onErrorResume(error -> {
                    log.warn("Vector search failed, continuing with empty results: {}", error.getMessage());
                    return Mono.just(Collections.emptyList());
                });
    }

    /**
     * 按来源类型执行向量检索（带优雅降级）
     * 根据配置选择使用父子块检索策略或传统向量检索。
     */
    private Mono<List<SearchResult>> executeVectorSearchBySourceType(
            UUID projectId, String sourceType, String query, int limit) {
        // 检查是否启用父子块检索策略
        if (isParentChildSearchEnabled()) {
            log.debug("Using parent-child search by type strategy for projectId={}, sourceType={}", 
                    projectId, sourceType);
            return parentChildSearchService.searchBySourceType(projectId, sourceType, query, limit)
                    .doOnNext(results -> log.debug("Parent-child search by type returned {} results", results.size()))
                    .onErrorResume(error -> {
                        log.warn("Parent-child search by type failed, falling back to traditional: {}", 
                                error.getMessage());
                        return executeTraditionalVectorSearchBySourceType(projectId, sourceType, query, limit);
                    });
        }
        
        return executeTraditionalVectorSearchBySourceType(projectId, sourceType, query, limit);
    }

    /**
     * 执行传统的按来源类型向量检索
     */
    private Mono<List<SearchResult>> executeTraditionalVectorSearchBySourceType(
            UUID projectId, String sourceType, String query, int limit) {
        return embeddingService.search(projectId, query, sourceType, limit)
                .doOnNext(results -> log.debug("Traditional vector search by type returned {} results", results.size()))
                .onErrorResume(error -> {
                    log.warn("Vector search by type failed: {}", error.getMessage());
                    return Mono.just(Collections.emptyList());
                });
    }

    // ==================== 全文检索 ====================

    /**
     * 执行全文检索（带优雅降级）
     *
     */
    private Mono<List<SearchResult>> executeFullTextSearch(UUID projectId, String query, int limit) {
        return Mono.fromCallable(() -> fullTextSearchService.search(projectId, query, null, limit))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(results -> log.debug("Full-text search returned {} results", results.size()))
                .onErrorResume(error -> {
                    log.warn("Full-text search failed, continuing with empty results: {}", error.getMessage());
                    return Mono.just(Collections.emptyList());
                });
    }

    /**
     * 按来源类型执行全文检索（带优雅降级）
     */
    private Mono<List<SearchResult>> executeFullTextSearchBySourceType(
            UUID projectId, String sourceType, String query, int limit) {
        return Mono.fromCallable(() -> fullTextSearchService.search(projectId, query, sourceType, limit))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(results -> log.debug("Full-text search by type returned {} results", results.size()))
                .onErrorResume(error -> {
                    log.warn("Full-text search by type failed: {}", error.getMessage());
                    return Mono.just(Collections.emptyList());
                });
    }

    // ==================== 重排序 ====================

    /**
     * 应用重排序
     *
     */
    private Mono<List<SearchResult>> applyReranking(
            String query, 
            List<SearchResult> results, 
            int limit) {

        if (results.isEmpty()) {
            return Mono.just(results);
        }

        List<String> candidates = results.stream()
                .map(SearchResult::getContent)
                .collect(Collectors.toList());

        return rerankerService.rerank(query, candidates, limit)
                .map(rerankResults -> {
                    // 根据重排序结果重新排列
                    List<SearchResult> rerankedResults = new ArrayList<>();
                    for (RerankResult rerankResult : rerankResults) {
                        int originalIndex = rerankResult.getIndex();
                        if (originalIndex >= 0 && originalIndex < results.size()) {
                            SearchResult result = results.get(originalIndex);
                            result.setRerankerScore(rerankResult.getScore());
                            rerankedResults.add(result);
                        }
                    }
                    log.debug("Reranking completed, {} results", rerankedResults.size());
                    return rerankedResults;
                })
                .onErrorResume(error -> {
                    log.warn("Reranking failed, returning original results: {}", error.getMessage());
                    return Mono.just(results.stream().limit(limit).collect(Collectors.toList()));
                });
    }

    // ==================== 上下文构建 ====================

    /**
     * 将搜索结果格式化为AI上下文
     */
    private String formatResultsAsContext(List<SearchResult> results) {
        if (results.isEmpty()) {
            return "";
        }

        // 按来源类型分组
        Map<String, List<SearchResult>> grouped = results.stream()
                .collect(Collectors.groupingBy(SearchResult::getSourceType));

        StringBuilder context = new StringBuilder();

        // 章节内容
        appendGroupedResults(context, grouped.get(KnowledgeChunk.SOURCE_TYPE_STORY_BLOCK), "相关章节内容");

        // 角色设定
        appendGroupedResults(context, grouped.get(KnowledgeChunk.SOURCE_TYPE_CHARACTER), "相关角色设定");

        // 百科设定
        appendGroupedResults(context, grouped.get(KnowledgeChunk.SOURCE_TYPE_WIKI_ENTRY), "相关百科设定");

        return context.toString().trim();
    }

    /**
     * 追加分组结果到上下文
     */
    private void appendGroupedResults(StringBuilder context, List<SearchResult> results, String title) {
        if (results == null || results.isEmpty()) return;

        context.append("【").append(title).append("】\n");
        for (SearchResult result : results) {
            String name = "";
            if (result.getMetadata() != null) {
                name = result.getMetadata().getOrDefault("name",
                        result.getMetadata().getOrDefault("title", "")).toString();
            }
            if (!name.isEmpty()) {
                context.append("- ").append(name).append(": ");
            } else {
                context.append("- ");
            }
            context.append(truncateForContext(result.getContent())).append("\n");
        }
        context.append("\n");
    }

    // ==================== 配置获取 ====================

    private double getRrfK() {
        return ragProperties.hybridSearch().rrfK();
    }

    private int getDefaultTopK() {
        return ragProperties.hybridSearch().defaultTopK();
    }

    private int getRecallMultiplier() {
        return ragProperties.hybridSearch().recallMultiplier();
    }

    private boolean isRerankerEnabled() {
        return ragProperties.hybridSearch().enableReranker() && 
               ragProperties.reranker().enabled();
    }

    /**
     * 检查是否启用父子块检索策略
     * 需要同时满足：配置启用 + ParentChildSearchService 可用
     */
    private boolean isParentChildSearchEnabled() {
        return ragProperties.search().useParentChild() && parentChildSearchService != null;
    }

    // ==================== 辅助方法 ====================

    /**
     * 截断内容用于上下文
     */
    private String truncateForContext(String content) {
        if (content == null) return "";
        if (content.length() <= 500) return content;

        String sub = content.substring(0, 500);
        int lastDot = sub.lastIndexOf("。");
        int lastPeriod = sub.lastIndexOf(".");
        int cutOff = Math.max(lastDot, lastPeriod);

        if (cutOff > 300) {
            return sub.substring(0, cutOff + 1) + "...";
        }
        return sub + "...";
    }

    /**
     * 截断文本用于日志
     */
    private String truncateForLog(String text) {
        if (text == null) return "";
        if (text.length() <= 50) return text;
        return text.substring(0, 50) + "...";
    }
}
