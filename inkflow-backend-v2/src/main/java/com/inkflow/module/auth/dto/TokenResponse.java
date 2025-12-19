package com.inkflow.module.auth.dto;

/**
 * 令牌响应DTO
 */
public record TokenResponse(
    /**
     * 访问令牌（JWT）
     */
    String accessToken,
    
    /**
     * 刷新令牌
     */
    String refreshToken,
    
    /**
     * 令牌类型
     */
    String tokenType,
    
    /**
     * 访问令牌过期时间（秒）
     */
    long expiresIn,
    
    /**
     * 用户信息
     */
    UserDto user
) {
    /**
     * 创建Bearer类型的令牌响应
     */
    public static TokenResponse bearer(String accessToken, String refreshToken, long expiresIn, UserDto user) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}
