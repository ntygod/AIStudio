package com.inkflow.module.agent.dto;

import com.inkflow.module.agent.core.Intent;
import com.inkflow.module.project.entity.CreationPhase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * 聊天请求 DTO
 * 
 * Requirements: 1.1, 2.1
 */
public record ChatRequest(
    /**
     * 用户消息
     */
    @NotBlank(message = "消息不能为空")
    String message,
    
    /**
     * 项目 ID
     */
    @NotNull(message = "项目ID不能为空")
    UUID projectId,
    
    /**
     * 会话 ID
     */
    String sessionId,
    
    /**
     * 当前创作阶段
     */
    CreationPhase currentPhase,
    
    /**
     * 意图提示（用于 Fast Path）
     * 如果提供，将跳过 ThinkingAgent 直接路由
     */
    Intent intentHint,
    
    /**
     * 额外元数据
     */
    Map<String, Object> metadata
) {
    
    /**
     * 简化构造器
     */
    public ChatRequest(String message, UUID projectId) {
        this(message, projectId, null, null, null, null);
    }
    
    /**
     * 带阶段的构造器
     */
    public ChatRequest(String message, UUID projectId, CreationPhase phase) {
        this(message, projectId, null, phase, null, null);
    }
    
    /**
     * 检查是否有 Fast Path 提示
     */
    public boolean hasFastPathHint() {
        return intentHint != null;
    }
    
    /**
     * 检查消息是否以命令前缀开头
     */
    public boolean hasCommandPrefix() {
        if (message == null) return false;
        return message.startsWith("/write") ||
               message.startsWith("/plan") ||
               message.startsWith("/check") ||
               message.startsWith("/name") ||
               message.startsWith("/world") ||
               message.startsWith("/character");
    }
    
    /**
     * 从命令前缀解析意图
     */
    public Intent parseCommandIntent() {
        if (message == null) return null;
        
        if (message.startsWith("/write")) return Intent.WRITE_CONTENT;
        if (message.startsWith("/plan")) return Intent.PLAN_OUTLINE;
        if (message.startsWith("/check")) return Intent.CHECK_CONSISTENCY;
        if (message.startsWith("/name")) return Intent.GENERATE_NAME;
        if (message.startsWith("/world")) return Intent.PLAN_WORLD;
        if (message.startsWith("/character")) return Intent.PLAN_CHARACTER;
        
        return null;
    }
}
