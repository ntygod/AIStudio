package com.inkflow.module.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * RAG服务健康状态DTO
 * 
 * @author zsg
 * @date 2025/12/17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagHealthStatus {

    /**
     * 整体健康状态
     */
    private String status;

    /**
     * Embedding服务状态
     */
    private ServiceStatus embeddingService;

    /**
     * Reranker服务状态
     */
    private ServiceStatus rerankerService;

    /**
     * 全文搜索服务状态
     */
    private ServiceStatus fullTextSearchService;

    /**
     * 检查时间
     */
    private Instant timestamp;

    /**
     * 单个服务状态
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceStatus {
        /**
         * 服务是否可用
         */
        private boolean available;

        /**
         * 断路器状态
         */
        private String circuitBreakerState;

        /**
         * 连续失败次数
         */
        private int consecutiveFailures;

        /**
         * 最后失败时间
         */
        private Long lastFailureTime;

        /**
         * 附加信息
         */
        private String message;
    }

    /**
     * 创建健康状态
     */
    public static RagHealthStatus create(
            ServiceStatus embeddingStatus,
            ServiceStatus rerankerStatus,
            ServiceStatus fullTextStatus) {
        
        boolean allHealthy = embeddingStatus.isAvailable() 
                && rerankerStatus.isAvailable() 
                && fullTextStatus.isAvailable();
        
        boolean anyHealthy = embeddingStatus.isAvailable() 
                || rerankerStatus.isAvailable() 
                || fullTextStatus.isAvailable();
        
        String overallStatus;
        if (allHealthy) {
            overallStatus = "UP";
        } else if (anyHealthy) {
            overallStatus = "DEGRADED";
        } else {
            overallStatus = "DOWN";
        }
        
        return RagHealthStatus.builder()
                .status(overallStatus)
                .embeddingService(embeddingStatus)
                .rerankerService(rerankerStatus)
                .fullTextSearchService(fullTextStatus)
                .timestamp(Instant.now())
                .build();
    }
}
