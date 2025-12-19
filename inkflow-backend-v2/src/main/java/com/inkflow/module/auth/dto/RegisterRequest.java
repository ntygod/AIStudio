package com.inkflow.module.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求DTO
 */
public record RegisterRequest(
    /**
     * 用户名
     * - 3-30个字符
     * - 只允许字母、数字、下划线
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 30, message = "用户名长度必须在3-30个字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    String username,
    
    /**
     * 邮箱
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    String email,
    
    /**
     * 密码
     * - 至少8个字符
     * - 必须包含大小写字母和数字
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 100, message = "密码长度必须在8-100个字符之间")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
        message = "密码必须包含大小写字母和数字"
    )
    String password,
    
    /**
     * 显示名称（可选）
     */
    @Size(max = 100, message = "显示名称不能超过100个字符")
    String displayName
) {}
