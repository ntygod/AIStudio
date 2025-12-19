package com.inkflow.module.auth.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * JWT令牌服务
 * 
 * 功能：
 * - 生成访问令牌（Access Token）
 * - 验证和解析令牌
 * - 提取用户信息
 * 
 * 安全特性：
 * - 使用HMAC-SHA256签名算法
 * - 访问令牌15分钟过期
 * - 刷新令牌7天过期
 */
@Service
public class JwtService {
    
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    
    private final JwtProperties properties;
    private final SecretKey secretKey;
    
    public JwtService(JwtProperties properties) {
        this.properties = properties;
        // 从配置的密钥字符串生成SecretKey
        this.secretKey = Keys.hmacShaKeyFor(
            properties.secret().getBytes(StandardCharsets.UTF_8)
        );
    }
    
    /**
     * 生成访问令牌
     * 
     * @param userId 用户ID
     * @param username 用户名
     * @param claims 额外的声明
     * @return JWT访问令牌
     */
    public String generateAccessToken(UUID userId, String username, Map<String, Object> claims) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(properties.getAccessTokenExpirationSeconds());
        
        JwtBuilder builder = Jwts.builder()
            .subject(userId.toString())
            .issuer(properties.issuer())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .claim("username", username)
            .claim("type", "access");
        
        // 添加额外声明
        if (claims != null && !claims.isEmpty()) {
            claims.forEach(builder::claim);
        }
        
        return builder.signWith(secretKey).compact();
    }
    
    /**
     * 生成访问令牌（简化版）
     */
    public String generateAccessToken(UUID userId, String username) {
        return generateAccessToken(userId, username, null);
    }
    
    /**
     * 生成刷新令牌值（随机字符串，非JWT）
     * 
     * @return 安全的随机刷新令牌
     */
    public String generateRefreshTokenValue() {
        return UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
    }
    
    /**
     * 验证并解析令牌
     * 
     * @param token JWT令牌
     * @return 解析后的Claims
     * @throws JwtException 令牌无效时抛出
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
    
    /**
     * 验证令牌是否有效
     * 
     * @param token JWT令牌
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("JWT令牌已过期: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.debug("JWT令牌格式错误: {}", e.getMessage());
        } catch (SecurityException e) {
            log.debug("JWT签名验证失败: {}", e.getMessage());
        } catch (Exception e) {
            log.debug("JWT令牌验证失败: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * 从令牌中提取用户ID
     * 
     * @param token JWT令牌
     * @return 用户ID
     */
    public UUID extractUserId(String token) {
        Claims claims = parseToken(token);
        return UUID.fromString(claims.getSubject());
    }
    
    /**
     * 从令牌中提取用户名
     * 
     * @param token JWT令牌
     * @return 用户名
     */
    public String extractUsername(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }
    
    /**
     * 检查令牌是否即将过期（5分钟内）
     * 
     * @param token JWT令牌
     * @return 是否即将过期
     */
    public boolean isTokenExpiringSoon(String token) {
        try {
            Claims claims = parseToken(token);
            Date expiration = claims.getExpiration();
            // 5分钟内过期视为即将过期
            return expiration.getTime() - System.currentTimeMillis() < 5 * 60 * 1000;
        } catch (Exception e) {
            return true;
        }
    }
    
    /**
     * 获取访问令牌过期时间（秒）
     */
    public long getAccessTokenExpirationSeconds() {
        return properties.getAccessTokenExpirationSeconds();
    }
    
    /**
     * 获取刷新令牌过期时间（秒）
     */
    public long getRefreshTokenExpirationSeconds() {
        return properties.getRefreshTokenExpirationSeconds();
    }
}
