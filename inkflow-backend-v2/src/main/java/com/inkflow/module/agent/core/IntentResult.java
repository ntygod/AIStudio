package com.inkflow.module.agent.core;

import java.util.List;

/**
 * 意图分析结果
 *
 * @param intent 识别的主要意图
 * @param confidence 置信度 (0.0 - 1.0)
 * @param alternatives 备选意图列表
 * @param source 结果来源（规则引擎或 LLM）
 * @param targetAgent 目标 Agent 名称
 */
public record IntentResult(
    Intent intent,
    double confidence,
    List<Intent> alternatives,
    IntentSource source,
    String targetAgent
) {
    
    /**
     * 从规则引擎创建结果
     */
    public static IntentResult fromRule(Intent intent, double confidence) {
        return new IntentResult(
            intent,
            confidence,
            List.of(),
            IntentSource.RULE_ENGINE,
            intent.getTargetAgentName()
        );
    }
    
    /**
     * 从 LLM 分析创建结果
     */
    public static IntentResult fromLLM(Intent intent, double confidence, List<Intent> alternatives) {
        return new IntentResult(
            intent,
            confidence,
            alternatives != null ? alternatives : List.of(),
            IntentSource.LLM,
            intent.getTargetAgentName()
        );
    }
    
    /**
     * 创建默认结果（通用对话）
     */
    public static IntentResult defaultChat() {
        return new IntentResult(
            Intent.GENERAL_CHAT,
            0.5,
            List.of(),
            IntentSource.DEFAULT,
            "ChatAgent"
        );
    }
    
    /**
     * 检查是否为高置信度结果
     */
    public boolean isHighConfidence() {
        return confidence >= 0.9;
    }
    
    /**
     * 检查是否需要备选方案
     */
    public boolean needsAlternatives() {
        return confidence < 0.7;
    }
    
    /**
     * 意图来源
     */
    public enum IntentSource {
        /**
         * 规则引擎匹配
         */
        RULE_ENGINE,
        
        /**
         * LLM 分析
         */
        LLM,
        
        /**
         * Fast Path 直接指定
         */
        FAST_PATH,
        
        /**
         * 默认值
         */
        DEFAULT
    }
}
