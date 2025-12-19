package com.inkflow.module.ratelimit.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 限流指标服务
 * 收集和提供限流相关的统计数据
 */
@Slf4j
@Service
public class RateLimitMetricsService {

    private final ConcurrentHashMap<String, RateLimitMetrics> metricsMap = new ConcurrentHashMap<>();
    
    // 全局统计
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalAllowed = new AtomicLong(0);
    private final AtomicLong totalRejected = new AtomicLong(0);

    /**
     * 记录限流命中
     */
    public void recordHit(String key, boolean allowed) {
        totalRequests.incrementAndGet();
        
        if (allowed) {
            totalAllowed.incrementAndGet();
        } else {
            totalRejected.incrementAndGet();
        }
        
        metricsMap.compute(key, (k, existing) -> {
            if (existing == null) {
                existing = new RateLimitMetrics(key);
            }
            existing.record(allowed);
            return existing;
        });
    }

    /**
     * 获取指定key的指标
     */
    public RateLimitMetrics getMetrics(String key) {
        return metricsMap.getOrDefault(key, new RateLimitMetrics(key));
    }

    /**
     * 获取所有指标
     */
    public Map<String, RateLimitMetrics> getAllMetrics() {
        return Map.copyOf(metricsMap);
    }


    /**
     * 获取全局统计摘要
     */
    public GlobalMetricsSummary getGlobalSummary() {
        return new GlobalMetricsSummary(
                totalRequests.get(),
                totalAllowed.get(),
                totalRejected.get(),
                metricsMap.size()
        );
    }

    /**
     * 重置所有指标
     */
    public void resetAll() {
        metricsMap.clear();
        totalRequests.set(0);
        totalAllowed.set(0);
        totalRejected.set(0);
        log.info("限流指标已重置");
    }

    /**
     * 重置指定key的指标
     */
    public void reset(String key) {
        metricsMap.remove(key);
    }

    /**
     * 限流指标
     */
    @Getter
    public static class RateLimitMetrics {
        private final String key;
        private final AtomicLong totalRequests = new AtomicLong(0);
        private final AtomicLong allowedRequests = new AtomicLong(0);
        private final AtomicLong rejectedRequests = new AtomicLong(0);
        private volatile LocalDateTime firstRequestAt;
        private volatile LocalDateTime lastRequestAt;

        public RateLimitMetrics(String key) {
            this.key = key;
        }

        public void record(boolean allowed) {
            totalRequests.incrementAndGet();
            if (allowed) {
                allowedRequests.incrementAndGet();
            } else {
                rejectedRequests.incrementAndGet();
            }
            
            LocalDateTime now = LocalDateTime.now();
            if (firstRequestAt == null) {
                firstRequestAt = now;
            }
            lastRequestAt = now;
        }

        public double getRejectionRate() {
            long total = totalRequests.get();
            if (total == 0) return 0.0;
            return (double) rejectedRequests.get() / total * 100;
        }
    }

    /**
     * 全局指标摘要
     */
    public record GlobalMetricsSummary(
            long totalRequests,
            long totalAllowed,
            long totalRejected,
            int uniqueKeys
    ) {
        public double getRejectionRate() {
            if (totalRequests == 0) return 0.0;
            return (double) totalRejected / totalRequests * 100;
        }
    }
}
