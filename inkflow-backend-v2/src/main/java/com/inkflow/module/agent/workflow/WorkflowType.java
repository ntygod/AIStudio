package com.inkflow.module.agent.workflow;

import com.inkflow.module.agent.core.Intent;

import java.util.List;

/**
 * 工作流类型枚举
 * 定义不同类型的工作流及其支持的意图
 * 
 * @see Requirements 1.1
 */
public enum WorkflowType {
    
    /**
     * 内容生成工作流
     * 预处理: RAG + 角色状态 + 风格
     * 后处理: 一致性检查（同步）
     */
    CONTENT_GENERATION("内容生成", List.of(Intent.WRITE_CONTENT)),
    
    /**
     * 创意设计工作流
     * 预处理: RAG + 原型库
     * 后处理: 关系图/知识库更新（异步）
     */
    CREATIVE_DESIGN("创意设计", List.of(
        Intent.PLAN_CHARACTER, 
        Intent.DESIGN_RELATIONSHIP, 
        Intent.MATCH_ARCHETYPE,
        Intent.PLAN_WORLD, 
        Intent.BRAINSTORM_IDEA
    )),
    
    /**
     * 规划工作流
     * 预处理: RAG + 伏笔状态
     * 后处理: 无
     */
    PLANNING("大纲规划", List.of(
        Intent.PLAN_OUTLINE, 
        Intent.MANAGE_PLOTLOOP, 
        Intent.ANALYZE_PACING
    )),
    
    /**
     * 质量检查工作流
     * 预处理: RAG + Preflight
     * 后处理: 无
     */
    QUALITY_CHECK("质量检查", List.of(
        Intent.CHECK_CONSISTENCY, 
        Intent.ANALYZE_STYLE
    )),

    /**
     * 简单 Agent 工作流
     * 预处理: 无
     * 后处理: 无
     */
    SIMPLE_AGENT("简单任务", List.of(
        Intent.GENERAL_CHAT, 
        Intent.GENERATE_NAME, 
        Intent.SUMMARIZE, 
        Intent.EXTRACT_ENTITY
    )),
    
    /**
     * 链式工作流（头脑风暴扩写）
     * Requirements: 14.1
     */
    BRAINSTORM_EXPAND("头脑风暴扩写", List.of(Intent.BRAINSTORM_AND_EXPAND)),
    
    /**
     * 链式工作流（大纲到章节）
     * Requirements: 14.2
     */
    OUTLINE_TO_CHAPTER("大纲到章节", List.of(Intent.OUTLINE_TO_CHAPTER)),
    
    /**
     * 链式工作流（角色到场景）
     * Requirements: 14.3
     */
    CHARACTER_TO_SCENE("角色到场景", List.of(Intent.CHARACTER_TO_SCENE));
    
    private final String displayName;
    private final List<Intent> supportedIntents;
    
    WorkflowType(String displayName, List<Intent> supportedIntents) {
        this.displayName = displayName;
        this.supportedIntents = supportedIntents;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public List<Intent> getSupportedIntents() {
        return supportedIntents;
    }
    
    /**
     * 根据意图获取对应的工作流类型
     * 
     * @param intent 意图
     * @return 工作流类型，默认返回 SIMPLE_AGENT
     */
    public static WorkflowType fromIntent(Intent intent) {
        for (WorkflowType type : values()) {
            if (type.supportedIntents.contains(intent)) {
                return type;
            }
        }
        return SIMPLE_AGENT;
    }
    
    /**
     * 检查是否支持指定意图
     */
    public boolean supports(Intent intent) {
        return supportedIntents.contains(intent);
    }
    
    /**
     * 检查是否为链式工作流
     */
    public boolean isChainWorkflow() {
        return this == BRAINSTORM_EXPAND || this == OUTLINE_TO_CHAPTER || this == CHARACTER_TO_SCENE;
    }
    
    /**
     * 检查是否需要预处理
     */
    public boolean needsPreprocessing() {
        return this != SIMPLE_AGENT && !isChainWorkflow();
    }
}
