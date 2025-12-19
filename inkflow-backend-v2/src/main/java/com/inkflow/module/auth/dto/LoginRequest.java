package com.inkflow.module.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求DTO
 */
public record LoginRequest(
    /**
     * 用户标识（邮箱或用户名）
     */
    @NotBlank(message = "用户名或邮箱不能为空")
    String identifier,
    
    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    String password,
    
    /**
     * 设备信息（可选，用于多设备管理）
     */
    String deviceInfo
) {}
