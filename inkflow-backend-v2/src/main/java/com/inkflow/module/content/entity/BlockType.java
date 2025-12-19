package com.inkflow.module.content.entity;

/**
 * 剧情块类型枚举
 */
public enum BlockType {
    
    /**
     * 叙述 - 描写、叙事
     */
    NARRATIVE,
    
    /**
     * 对话 - 角色对话
     */
    DIALOGUE,
    
    /**
     * 心理 - 心理描写
     */
    THOUGHT,
    
    /**
     * 动作 - 动作描写
     */
    ACTION,
    
    /**
     * 环境 - 环境描写
     */
    ENVIRONMENT,
    
    /**
     * 过渡 - 场景过渡
     */
    TRANSITION,
    
    /**
     * 注释 - 作者注释（不显示在正文）
     */
    COMMENT
}
