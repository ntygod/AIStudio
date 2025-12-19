package com.inkflow.module.agent.core;

/**
 * 具有能力声明的 Agent 接口
 * 扩展基础 Agent 接口，添加能力声明支持
 * 
 * Requirements: 19.1, 19.2
 *
 * @param <I> 输入类型
 * @param <O> 输出类型
 */
public interface CapableAgent<I, O> extends Agent<I, O> {
    
    /**
     * 获取 Agent 能力声明
     * 用于路由决策和能力查询
     */
    AgentCapability getCapability();
    
    /**
     * 检查是否支持指定意图
     */
    default boolean supportsIntent(Intent intent) {
        return getCapability().supportsIntent(intent);
    }
    
    /**
     * 获取执行模式
     */
    default ExecutionMode getExecutionMode() {
        return getCapability().executionMode();
    }
    
    /**
     * 获取 Agent 分类
     */
    default AgentCategory getCategory() {
        return getCapability().category();
    }
}
