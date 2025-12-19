package com.inkflow.module.agent.core;

/**
 * Agent 执行模式
 * 
 * Requirements: 19.5
 */
public enum ExecutionMode {
    
    /**
     * 立即执行 - 路由后立即执行
     * 适用于核心创作 Agent
     */
    EAGER,
    
    /**
     * 懒执行 - 按需触发
     * 适用于工具类 Agent（如 SummaryAgent、ExtractionAgent）
     */
    LAZY
}
