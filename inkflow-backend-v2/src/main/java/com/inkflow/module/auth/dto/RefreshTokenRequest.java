package com.inkflow.module.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 刷新令牌请求DTO
 */
public record RefreshTokenRequest(
    /**
     * 刷新令牌
     */
    @NotBlank(message = "刷新令牌不能为空")
    String refreshToken
) {}
