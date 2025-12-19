package com.inkflow.module.agent.core;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 用户意图枚举
 * 用于路由决策
 *
 */
public enum Intent {
    
    // ========== 内容生成类 ==========
    /**
     * 写作内容生成
     */
    WRITE_CONTENT("写作", "续写", "扩写", "生成", "写一段", "帮我写"),
    
    // ========== 世界观类 ==========
    /**
     * 世界观设计
     */
    PLAN_WORLD("世界观", "设定", "力量体系", "魔法体系", "修炼体系"),
    
    /**
     * 灵感激发
     */
    BRAINSTORM_IDEA("灵感", "创意", "想法", "点子", "构思"),
    
    // ========== 角色类 ==========
    /**
     * 角色设计
     */
    PLAN_CHARACTER("角色", "人物", "主角", "配角", "反派"),
    
    /**
     * 关系设计
     */
    DESIGN_RELATIONSHIP("关系", "人际", "关系网"),
    
    /**
     * 原型匹配
     */
    MATCH_ARCHETYPE("原型", "类型", "人设"),
    
    // ========== 规划类 ==========
    /**
     * 大纲规划
     */
    PLAN_OUTLINE("大纲", "规划", "结构", "章节"),
    
    /**
     * 伏笔管理
     */
    MANAGE_PLOTLOOP("伏笔", "埋线", "回收", "线索"),
    
    /**
     * 节奏分析
     */
    ANALYZE_PACING("节奏", "张力", "起伏"),
    
    // ========== 质量类 ==========
    /**
     * 一致性检查
     */
    CHECK_CONSISTENCY("检查", "一致性", "矛盾", "冲突", "bug"),
    
    /**
     * 文风分析
     */
    ANALYZE_STYLE("文风", "风格", "语言风格"),
    
    // ========== 链式工作流类 ==========
    /**
     * 头脑风暴并扩写
     * Requirements: 14.1
     */
    BRAINSTORM_AND_EXPAND("头脑风暴扩写", "想法扩写", "创意扩展", "灵感扩写"),
    
    /**
     * 大纲到章节
     * Requirements: 14.2
     */
    OUTLINE_TO_CHAPTER("大纲转章节", "大纲扩写", "章节生成", "从大纲写"),
    
    /**
     * 角色到场景
     * Requirements: 14.3
     */
    CHARACTER_TO_SCENE("角色出场", "角色场景", "人物登场", "角色亮相"),
    
    // ========== 工具类 ==========
    /**
     * 名称生成
     */
    GENERATE_NAME("起名", "名字", "取名", "命名"),
    
    /**
     * 实体抽取
     */
    EXTRACT_ENTITY("抽取", "提取", "识别"),
    
    /**
     * 摘要生成
     */
    SUMMARIZE("摘要", "总结", "概括"),
    
    // ========== 通用 ==========
    /**
     * 通用对话
     */
    GENERAL_CHAT("聊天", "问答", "帮助", "你好", "请问");
    
    private final String[] keywords;
    
    Intent(String... keywords) {
        this.keywords = keywords;
    }
    
    public String[] getKeywords() {
        return keywords;
    }
    
    public List<String> getKeywordList() {
        return Arrays.asList(keywords);
    }
    
    /**
     * 根据消息内容匹配意图
     * 
     * @param message 用户消息
     * @return 匹配的意图（如果有）
     */
    public static Optional<Intent> matchFromMessage(String message) {
        if (message == null || message.isBlank()) {
            return Optional.of(GENERAL_CHAT);
        }
        
        String lowerMessage = message.toLowerCase();
        
        for (Intent intent : values()) {
            for (String keyword : intent.keywords) {
                if (lowerMessage.contains(keyword.toLowerCase())) {
                    return Optional.of(intent);
                }
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * 获取意图对应的目标 Agent 名称
     */
    public String getTargetAgentName() {
        return switch (this) {
            case WRITE_CONTENT -> "WriterAgent";
            case PLAN_WORLD, BRAINSTORM_IDEA -> "WorldBuilderAgent";
            case PLAN_CHARACTER, DESIGN_RELATIONSHIP, MATCH_ARCHETYPE -> "CharacterAgent";
            case PLAN_OUTLINE, MANAGE_PLOTLOOP, ANALYZE_PACING -> "PlannerAgent";
            case CHECK_CONSISTENCY, ANALYZE_STYLE -> "ConsistencyAgent";
            case GENERATE_NAME -> "NameGeneratorAgent";
            case EXTRACT_ENTITY -> "ExtractionAgent";
            case SUMMARIZE -> "SummaryAgent";
            case GENERAL_CHAT -> "ChatAgent";
            // 链式工作流 - 返回第一个 Agent
            case BRAINSTORM_AND_EXPAND -> "WorldBuilderAgent";
            case OUTLINE_TO_CHAPTER -> "PlannerAgent";
            case CHARACTER_TO_SCENE -> "CharacterAgent";
        };
    }
}
