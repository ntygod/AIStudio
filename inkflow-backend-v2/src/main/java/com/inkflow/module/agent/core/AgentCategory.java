package com.inkflow.module.agent.core;

import lombok.Getter;

/**
 * Agent 分类
 *
 * @author zsg
 * @date 2025/12/17
 */
@Getter
public enum AgentCategory {
    
    /**
     * 路由层 - 负责意图分析和路由决策
     * 包括: ThinkingAgent, ChatAgent
     */
    ROUTING("路由层", "负责意图分析和请求路由"),
    
    /**
     * 创作层 - 负责核心创作任务
     * 包括: WorldBuilderAgent, CharacterAgent, PlannerAgent, WriterAgent
     */
    CREATIVE("创作层", "负责核心创作任务"),
    
    /**
     * 质量层 - 负责质量保障
     * 包括: ConsistencyAgent
     */
    QUALITY("质量层", "负责一致性检查和质量保障"),
    
    /**
     * 工具层 - 提供辅助功能
     * 包括: NameGeneratorAgent, SummaryAgent, ExtractionAgent
     */
    UTILITY("工具层", "提供辅助功能，按需触发");
    
    private final String displayName;
    private final String description;
    
    AgentCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

}
