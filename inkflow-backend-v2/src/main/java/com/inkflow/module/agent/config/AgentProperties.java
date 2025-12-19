package com.inkflow.module.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Agent 配置属性
 */
@ConfigurationProperties(prefix = "inkflow.agent")
public record AgentProperties(
    FastPathConfig fastPath,
    ThinkingConfig thinking,
    LazyExecutionConfig lazyExecution
) {
    
    /**
     * Fast Path 配置
     */
    public record FastPathConfig(
        boolean enabled,
        List<String> commandPrefixes
    ) {
        public FastPathConfig {
            if (enabled && (commandPrefixes == null || commandPrefixes.isEmpty())) {
                commandPrefixes = List.of("/write", "/plan", "/check", "/name", "/world", "/character");
            }
        }
    }
    
    /**
     * ThinkingAgent 配置
     */
    public record ThinkingConfig(
        double ruleConfidenceThreshold,
        String llmModel,
        long timeoutMs
    ) {
        public ThinkingConfig {
            if (ruleConfidenceThreshold <= 0) {
                ruleConfidenceThreshold = 0.9;
            }
            if (llmModel == null || llmModel.isBlank()) {
                llmModel = "deepseek-chat";
            }
            if (timeoutMs <= 0) {
                timeoutMs = 2000;
            }
        }
    }
    
    /**
     * 懒执行配置
     */
    public record LazyExecutionConfig(
        AutoTriggerConfig autoTrigger
    ) {}
    
    /**
     * 自动触发配置
     */
    public record AutoTriggerConfig(
        String summary,
        String extraction
    ) {
        public AutoTriggerConfig {
            if (summary == null) {
                summary = "after-chapter-complete";
            }
            if (extraction == null) {
                extraction = "after-content-save";
            }
        }
    }
}
