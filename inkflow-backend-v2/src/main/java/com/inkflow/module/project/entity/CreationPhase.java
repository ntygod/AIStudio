package com.inkflow.module.project.entity;

/**
 * 创作阶段枚举
 * 
 * 定义小说创作的不同阶段，用于：
 * - 引导用户按流程创作
 * - 根据阶段提供不同的AI工具
 * - 追踪创作进度
 */
public enum CreationPhase {
    
    /**
     * 灵感阶段 - 收集创意和灵感
     */
    IDEA("灵感收集", "收集创意灵感，确定故事核心概念"),
    
    /**
     * 世界观阶段 - 构建世界设定
     */
    WORLDBUILDING("世界构建", "设计世界观、力量体系、地理环境"),
    
    /**
     * 角色阶段 - 设计角色
     */
    CHARACTER("角色设计", "创建主要角色，设定性格、背景、关系"),
    
    /**
     * 大纲阶段 - 规划故事结构
     */
    OUTLINE("大纲规划", "设计故事主线、分卷结构、章节大纲"),
    
    /**
     * 写作阶段 - 正式创作
     */
    WRITING("正式写作", "按大纲进行章节创作"),
    
    /**
     * 修订阶段 - 修改完善
     */
    REVISION("修订完善", "检查一致性、优化文笔、修复漏洞"),
    
    /**
     * 完成阶段 - 创作完成
     */
    COMPLETED("创作完成", "作品已完结");
    
    private final String displayName;
    private final String description;
    
    CreationPhase(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 获取下一个阶段
     */
    public CreationPhase next() {
        int nextOrdinal = this.ordinal() + 1;
        CreationPhase[] phases = values();
        if (nextOrdinal < phases.length) {
            return phases[nextOrdinal];
        }
        return this;
    }
    
    /**
     * 获取上一个阶段
     */
    public CreationPhase previous() {
        int prevOrdinal = this.ordinal() - 1;
        if (prevOrdinal >= 0) {
            return values()[prevOrdinal];
        }
        return this;
    }
    
    /**
     * 检查是否可以进入写作阶段
     */
    public boolean canStartWriting() {
        return this.ordinal() >= OUTLINE.ordinal();
    }
}
