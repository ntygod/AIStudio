package com.inkflow.module.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT配置属性
 * 
 * 配置示例:
 * inkflow:
 *   security:
 *     jwt:
 *       secret: your-256-bit-secret-key
 *       access-token-expiration: 15m
 *       refresh-token-expiration: 7d
 */
@ConfigurationProperties(prefix = "inkflow.security.jwt")
public record JwtProperties(
    /**
     * JWT签名密钥（至少256位）
     */
    String secret,
    
    /**
     * 访问令牌过期时间（默认15分钟）
     */
    Duration accessTokenExpiration,
    
    /**
     * 刷新令牌过期时间（默认7天）
     */
    Duration refreshTokenExpiration,
    
    /**
     * 令牌签发者
     */
    String issuer
) {
    /**
     * 默认构造函数，设置默认值
     */
    public JwtProperties {
        if (accessTokenExpiration == null) {
            accessTokenExpiration = Duration.ofMinutes(15);
        }
        if (refreshTokenExpiration == null) {
            refreshTokenExpiration = Duration.ofDays(7);
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "inkflow";
        }
    }
    
    /**
     * 获取访问令牌过期秒数
     */
    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpiration.toSeconds();
    }
    
    /**
     * 获取刷新令牌过期秒数
     */
    public long getRefreshTokenExpirationSeconds() {
        return refreshTokenExpiration.toSeconds();
    }
}
