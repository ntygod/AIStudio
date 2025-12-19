package com.inkflow.module.rag.dto;

/**
 * 断路器状态枚举
 * 用于EmbeddingService和RerankerService的断路器模式实现。
 *
 * @author zsg
 * @date 2025/12/17
 */
public enum CircuitBreakerState {
    
    /**
     * 关闭状态 - 正常工作
     * 所有请求正常处理，失败计数器在工作
     */
    CLOSED,
    
    /**
     * 打开状态 - 熔断中
     * 所有请求立即拒绝，不调用外部服务
     */
    OPEN,
    
    /**
     * 半开状态 - 测试恢复
     * 允许单个测试请求通过，根据结果决定转换到CLOSED或OPEN
     */
    HALF_OPEN
}
