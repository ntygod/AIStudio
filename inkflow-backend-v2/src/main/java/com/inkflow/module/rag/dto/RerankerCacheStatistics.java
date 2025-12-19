package com.inkflow.module.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 重排序缓存统计DTO
 * 用于监控RerankerService的缓存性能。
 *
 * @author zsg
 * @date 2025/12/17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankerCacheStatistics {

    /**
     * 缓存命中次数
     */
    private long cacheHits;

    /**
     * 缓存未命中次数
     */
    private long cacheMisses;

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
    private int cacheSize;

    /**
     * 最大缓存大小
     */
    private int maxCacheSize;

    /**
     * 计算命中率
     */
    public static RerankerCacheStatistics create(
            long cacheHits, 
            long cacheMisses, 
            int cacheSize, 
            int maxCacheSize) {
        long totalRequests = cacheHits + cacheMisses;
        double hitRate = totalRequests > 0 ? (cacheHits * 100.0 / totalRequests) : 0.0;
        
        return RerankerCacheStatistics.builder()
                .cacheHits(cacheHits)
                .cacheMisses(cacheMisses)
                .totalRequests(totalRequests)
                .hitRate(hitRate)
                .cacheSize(cacheSize)
                .maxCacheSize(maxCacheSize)
                .build();
    }
}
