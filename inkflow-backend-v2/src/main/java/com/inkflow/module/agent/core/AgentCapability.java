package com.inkflow.module.agent.core;

import com.inkflow.module.project.entity.CreationPhase;

import java.util.List;

/**
 * Agent 能力声明
 * 用于路由决策和能力查询
 *
 * @param agentName Agent 名称
 * @param category Agent 分类
 * @param supportedIntents 支持的意图列表
 * @param applicablePhases 适用的创作阶段
 * @param requiredTools 需要的工具列表
 * @param executionMode 执行模式
 * @param estimatedLatencyMs 预估延迟（毫秒）
 * @param estimatedTokenCost 预估 Token 消耗
 */
public record AgentCapability(
    String agentName,
    AgentCategory category,
    List<Intent> supportedIntents,
    List<CreationPhase> applicablePhases,
    List<String> requiredTools,
    ExecutionMode executionMode,
    int estimatedLatencyMs,
    int estimatedTokenCost
) {
    
    /**
     * 检查是否支持指定意图
     */
    public boolean supportsIntent(Intent intent) {
        return supportedIntents.contains(intent);
    }
    
    /**
     * 检查是否适用于指定阶段
     */
    public boolean applicableToPhase(CreationPhase phase) {
        return applicablePhases.contains(phase);
    }
    
    /**
     * 检查是否为懒执行模式
     */
    public boolean isLazy() {
        return executionMode == ExecutionMode.LAZY;
    }
}
