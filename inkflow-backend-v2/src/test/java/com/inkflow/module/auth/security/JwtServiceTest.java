package com.inkflow.module.auth.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JWT服务单元测试
 */
@DisplayName("JWT服务测试")
class JwtServiceTest {
    
    private JwtService jwtService;
    
    // 测试用的256位密钥（Base64编码的32字节）
    private static final String TEST_SECRET = "dGhpcy1pcy1hLXZlcnktc2VjdXJlLWtleS1mb3ItdGVzdGluZw==";
    
    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
            TEST_SECRET,
            Duration.ofMinutes(15),
            Duration.ofDays(7),
            "inkflow-test"
        );
        jwtService = new JwtService(properties);
    }
    
    @Test
    @DisplayName("生成访问令牌应包含正确的用户信息")
    void generateAccessToken_shouldContainCorrectUserInfo() {
        // Given
        UUID userId = UUID.randomUUID();
        String username = "testuser";
        
        // When
        String token = jwtService.generateAccessToken(userId, username);
        
        // Then
        assertThat(token).isNotBlank();
        
        Claims claims = jwtService.parseToken(token);
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("username", String.class)).isEqualTo(username);
        assertThat(claims.get("type", String.class)).isEqualTo("access");
        assertThat(claims.getIssuer()).isEqualTo("inkflow-test");
    }
    
    @Test
    @DisplayName("生成访问令牌应支持额外声明")
    void generateAccessToken_shouldSupportAdditionalClaims() {
        // Given
        UUID userId = UUID.randomUUID();
        String username = "testuser";
        Map<String, Object> claims = Map.of(
            "role", "admin",
            "projectId", UUID.randomUUID().toString()
        );
        
        // When
        String token = jwtService.generateAccessToken(userId, username, claims);
        
        // Then
        Claims parsedClaims = jwtService.parseToken(token);
        assertThat(parsedClaims.get("role", String.class)).isEqualTo("admin");
        assertThat(parsedClaims.get("projectId", String.class)).isNotBlank();
    }
    
    @Test
    @DisplayName("验证有效令牌应返回true")
    void validateToken_shouldReturnTrueForValidToken() {
        // Given
        String token = jwtService.generateAccessToken(UUID.randomUUID(), "testuser");
        
        // When & Then
        assertThat(jwtService.validateToken(token)).isTrue();
    }
    
    @Test
    @DisplayName("验证无效令牌应返回false")
    void validateToken_shouldReturnFalseForInvalidToken() {
        // Given
        String invalidToken = "invalid.token.here";
        
        // When & Then
        assertThat(jwtService.validateToken(invalidToken)).isFalse();
    }
    
    @Test
    @DisplayName("提取用户ID应返回正确的UUID")
    void extractUserId_shouldReturnCorrectUUID() {
        // Given
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateAccessToken(userId, "testuser");
        
        // When
        UUID extractedUserId = jwtService.extractUserId(token);
        
        // Then
        assertThat(extractedUserId).isEqualTo(userId);
    }
    
    @Test
    @DisplayName("提取用户名应返回正确的值")
    void extractUsername_shouldReturnCorrectValue() {
        // Given
        String username = "testuser";
        String token = jwtService.generateAccessToken(UUID.randomUUID(), username);
        
        // When
        String extractedUsername = jwtService.extractUsername(token);
        
        // Then
        assertThat(extractedUsername).isEqualTo(username);
    }
    
    @Test
    @DisplayName("生成刷新令牌值应返回唯一字符串")
    void generateRefreshTokenValue_shouldReturnUniqueString() {
        // When
        String token1 = jwtService.generateRefreshTokenValue();
        String token2 = jwtService.generateRefreshTokenValue();
        
        // Then
        assertThat(token1).isNotBlank();
        assertThat(token2).isNotBlank();
        assertThat(token1).isNotEqualTo(token2);
    }
    
    @Test
    @DisplayName("获取过期时间应返回配置的值")
    void getExpirationSeconds_shouldReturnConfiguredValues() {
        // When & Then
        assertThat(jwtService.getAccessTokenExpirationSeconds()).isEqualTo(15 * 60);
        assertThat(jwtService.getRefreshTokenExpirationSeconds()).isEqualTo(7 * 24 * 60 * 60);
    }
}
