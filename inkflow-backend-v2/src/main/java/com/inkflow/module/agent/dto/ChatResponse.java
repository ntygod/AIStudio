package com.inkflow.module.agent.dto;

import com.inkflow.module.agent.core.Intent;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 聊天响应 DTO
 * 
 * Requirements: 15.1-15.5
 */
public record ChatResponse(
    /**
     * 响应内容
     */
    String content,
    
    /**
     * 处理该请求的 Agent 名称
     */
    String agentName,
    
    /**
     * 识别的意图
     */
    Intent intent,
    
    /**
     * Token 使用量
     */
    TokenUsage tokenUsage,
    
    /**
     * 处理延迟（毫秒）
     */
    long latencyMs,
    
    /**
     * 响应时间
     */
    LocalDateTime timestamp,
    
    /**
     * 额外数据（如结构化输出）
     */
    Map<String, Object> data
) {
    
    /**
     * 简化构造器
     */
    public static ChatResponse of(String content, String agentName) {
        return new ChatResponse(
            content,
            agentName,
            null,
            null,
            0,
            LocalDateTime.now(),
            null
        );
    }
    
    /**
     * 带 Token 使用量的构造器
     */
    public static ChatResponse of(String content, String agentName, TokenUsage tokenUsage, long latencyMs) {
        return new ChatResponse(
            content,
            agentName,
            null,
            tokenUsage,
            latencyMs,
            LocalDateTime.now(),
            null
        );
    }
    
    /**
     * Token 使用量
     */
    public record TokenUsage(
        int promptTokens,
        int completionTokens,
        int totalTokens
    ) {
        public static TokenUsage of(int prompt, int completion) {
            return new TokenUsage(prompt, completion, prompt + completion);
        }
    }
}
