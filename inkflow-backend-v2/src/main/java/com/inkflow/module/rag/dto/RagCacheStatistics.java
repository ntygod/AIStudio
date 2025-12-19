package com.inkflow.module.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * RAG缓存统计DTO
 * 整合Embedding缓存和Reranker缓存的统计信息。
 * 
 * @author zsg
 * @date 2025/12/17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagCacheStatistics {

    /**
     * Embedding缓存统计
     */
    private EmbeddingCacheStats embeddingCache;

    /**
     * Reranker缓存统计
     */
    private RerankerCacheStatistics rerankerCache;

    /**
     * 统计时间
     */
    private Instant timestamp;

    /**
     * Embedding缓存统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmbeddingCacheStats {
        /**
         * 缓存命中次数
         */
        private long hits;

        /**
         * 缓存未命中次数
         */
        private long misses;

        /**
         * 总请求次数
         */
        private long totalRequests;

        /**
         * 命中率 (0-100%)
         */
        private double hitRate;

        /**
         * 当前缓存大小
         */
        private long cacheSize;

        /**
         * 最大缓存大小
         */
        private long maxCacheSize;
    }

    /**
     * 创建缓存统计
     */
    public static RagCacheStatistics create(
            EmbeddingCacheStats embeddingStats,
            RerankerCacheStatistics rerankerStats) {
        return RagCacheStatistics.builder()
                .embeddingCache(embeddingStats)
                .rerankerCache(rerankerStats)
                .timestamp(Instant.now())
                .build();
    }
}
