package com.inkflow.module.agent.dto;

import io.swagger.v3.oas.annotations.Parameter;

/**
 * 聊天响应 DTO（非流式）
 */
public record ChatResponseDto(
    @Parameter(description = "AI 响应内容")
    String content,
    
    @Parameter(description = "会话ID")
    String sessionId,
    
    @Parameter(description = "使用的创作阶段")
    String phase
) {}
