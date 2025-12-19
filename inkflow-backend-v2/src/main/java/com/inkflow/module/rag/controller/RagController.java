package com.inkflow.module.rag.controller;

import com.inkflow.module.rag.dto.*;
import com.inkflow.module.rag.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RAG检索增强生成控制器
 * 提供混合检索和监控端点。
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
@Tag(name = "RAG", description = "检索增强生成API")
public class RagController {

    private final EmbeddingService embeddingService;
    private final HybridSearchService hybridSearchService;
    private final RerankerService rerankerService;
    private final EmbeddingCacheService embeddingCacheService;

    /**
     * 混合检索 - 结合向量检索和全文检索
     * POST /api/rag/search
     *
     */
    @PostMapping("/search")
    @Operation(summary = "混合检索", description = "结合向量检索和全文检索，使用RRF算法融合结果")
    public Mono<ResponseEntity<List<SearchResult>>> search(
            @Parameter(description = "项目ID") @RequestParam UUID projectId,
            @Parameter(description = "查询文本") @RequestParam String query,
            @Parameter(description = "来源类型过滤") @RequestParam(required = false) String sourceType,
            @Parameter(description = "返回结果数量") @RequestParam(required = false, defaultValue = "10") Integer limit) {

        log.debug("混合检索请求: projectId={}, query='{}', sourceType={}, limit={}", 
                projectId, truncateForLog(query), sourceType, limit);

        if (sourceType != null && !sourceType.isBlank()) {
            return hybridSearchService.searchBySourceType(projectId, sourceType, query, limit)
                    .map(ResponseEntity::ok);
        }
        return hybridSearchService.search(projectId, query, limit)
                .map(ResponseEntity::ok);
    }

    /**
     * 获取RAG服务健康状态
     * GET /api/rag/health
     *
     */
    @GetMapping("/health")
    @Operation(summary = "获取健康状态", description = "获取RAG服务的断路器状态和健康信息")
    public ResponseEntity<RagHealthStatus> getHealthStatus() {
        log.debug("获取RAG健康状态");

        // Embedding服务状态
        RagHealthStatus.ServiceStatus embeddingStatus = RagHealthStatus.ServiceStatus.builder()
                .available(embeddingService.getCircuitBreakerState() != CircuitBreakerState.OPEN)
                .circuitBreakerState(embeddingService.getCircuitBreakerState().name())
                .consecutiveFailures(embeddingService.getConsecutiveFailures())
                .lastFailureTime(embeddingService.getLastFailureTime())
                .message(getEmbeddingStatusMessage())
                .build();

        // Reranker服务状态
        RagHealthStatus.ServiceStatus rerankerStatus = RagHealthStatus.ServiceStatus.builder()
                .available(rerankerService.isServiceAvailable())
                .circuitBreakerState(rerankerService.getCircuitBreakerState().name())
                .consecutiveFailures(rerankerService.getConsecutiveFailures())
                .message(getRerankerStatusMessage())
                .build();

        // 全文搜索服务状态（全文搜索没有断路器，始终可用）
        RagHealthStatus.ServiceStatus fullTextStatus = RagHealthStatus.ServiceStatus.builder()
                .available(true)
                .circuitBreakerState("N/A")
                .consecutiveFailures(0)
                .message("Full-text search service is always available")
                .build();

        return ResponseEntity.ok(RagHealthStatus.create(embeddingStatus, rerankerStatus, fullTextStatus));
    }

    /**
     * 获取缓存统计信息
     * GET /api/rag/cache/stats
     *
     */
    @GetMapping("/cache/stats")
    @Operation(summary = "获取缓存统计", description = "获取Embedding和Reranker缓存的统计信息")
    public ResponseEntity<RagCacheStatistics> getCacheStatistics() {
        log.debug("获取缓存统计");

        // Embedding缓存统计
        EmbeddingCacheService.CacheStats embeddingStats = embeddingCacheService.getStats();
        RagCacheStatistics.EmbeddingCacheStats embeddingCacheStats = RagCacheStatistics.EmbeddingCacheStats.builder()
                .hits(embeddingStats.hits())
                .misses(embeddingStats.misses())
                .totalRequests(embeddingStats.hits() + embeddingStats.misses())
                .hitRate(embeddingStats.hitRate())
                .cacheSize(embeddingStats.size())
                .maxCacheSize(embeddingStats.maxSize())
                .build();

        // Reranker缓存统计
        RerankerCacheStatistics rerankerStats = rerankerService.getCacheStatistics();

        return ResponseEntity.ok(RagCacheStatistics.create(embeddingCacheStats, rerankerStats));
    }

    /**
     * 获取项目嵌入统计
     */
    @GetMapping("/stats/{projectId}")
    @Operation(summary = "获取嵌入统计", description = "获取项目的嵌入统计信息")
    public ResponseEntity<Map<String, Long>> getStatistics(
            @PathVariable UUID projectId) {
        log.debug("获取项目嵌入统计: projectId={}", projectId);
        return ResponseEntity.ok(embeddingService.getStatistics(projectId));
    }

    /**
     * 删除项目的所有嵌入
     */
    @DeleteMapping("/project/{projectId}")
    @Operation(summary = "删除项目嵌入", description = "删除项目的所有嵌入数据")
    public ResponseEntity<Void> deleteProjectEmbeddings(@PathVariable UUID projectId) {
        log.info("删除项目嵌入: projectId={}", projectId);
        embeddingService.deleteByProjectId(projectId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 删除指定来源的嵌入
     */
    @DeleteMapping("/source/{sourceId}")
    @Operation(summary = "删除来源嵌入", description = "删除指定来源的嵌入数据")
    public ResponseEntity<Void> deleteSourceEmbeddings(@PathVariable UUID sourceId) {
        log.info("删除来源嵌入: sourceId={}", sourceId);
        embeddingService.deleteBySourceId(sourceId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 清空所有缓存
     */
    @PostMapping("/cache/clear")
    @Operation(summary = "清空缓存", description = "清空Embedding和Reranker缓存")
    public Mono<ResponseEntity<Void>> clearCache() {
        log.info("清空所有RAG缓存");
        rerankerService.clearCache();
        return embeddingCacheService.clear()
                .thenReturn(ResponseEntity.noContent().build());
    }

    /**
     * 重置断路器
     */
    @PostMapping("/circuit-breaker/reset")
    @Operation(summary = "重置断路器", description = "手动重置Embedding和Reranker服务的断路器")
    public ResponseEntity<Void> resetCircuitBreakers() {
        log.info("重置所有断路器");
        embeddingService.resetCircuitBreaker();
        rerankerService.resetCircuitBreaker();
        return ResponseEntity.noContent().build();
    }

    private String truncateForLog(String text) {
        if (text == null) return "";
        if (text.length() <= 50) return text;
        return text.substring(0, 50) + "...";
    }

    private String getEmbeddingStatusMessage() {
        CircuitBreakerState state = embeddingService.getCircuitBreakerState();
        return switch (state) {
            case CLOSED -> "Embedding service is healthy";
            case OPEN -> "Embedding service circuit breaker is open, requests will be rejected";
            case HALF_OPEN -> "Embedding service is recovering, testing with single request";
        };
    }

    private String getRerankerStatusMessage() {
        if (!rerankerService.isServiceAvailable()) {
            return "Reranker service is unavailable, falling back to score-based ranking";
        }
        CircuitBreakerState state = rerankerService.getCircuitBreakerState();
        return switch (state) {
            case CLOSED -> "Reranker service is healthy";
            case OPEN -> "Reranker service circuit breaker is open, using fallback";
            case HALF_OPEN -> "Reranker service is recovering";
        };
    }
}
