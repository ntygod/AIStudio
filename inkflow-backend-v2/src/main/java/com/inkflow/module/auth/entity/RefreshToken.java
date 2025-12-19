package com.inkflow.module.auth.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 刷新令牌实体
 * 
 * 用于实现JWT的刷新机制：
 * - Access Token: 15分钟有效期
 * - Refresh Token: 7天有效期
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
    @Index(name = "idx_refresh_tokens_token", columnList = "token", unique = true),
    @Index(name = "idx_refresh_tokens_user_id", columnList = "user_id"),
    @Index(name = "idx_refresh_tokens_expires_at", columnList = "expires_at")
})
public class RefreshToken {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * 关联用户ID
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    /**
     * 刷新令牌值（随机生成的安全字符串）
     */
    @Column(nullable = false, unique = true, length = 255)
    private String token;
    
    /**
     * 过期时间
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    
    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    /**
     * 设备信息（用于多设备管理）
     */
    @Column(name = "device_info", length = 500)
    private String deviceInfo;
    
    /**
     * IP地址
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    /**
     * 是否已撤销
     */
    @Column(nullable = false)
    private boolean revoked = false;
    
    /**
     * 撤销时间
     */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
    
    // ==================== 构造函数 ====================
    
    public RefreshToken() {
        this.createdAt = LocalDateTime.now();
    }
    
    public RefreshToken(UUID userId, String token, LocalDateTime expiresAt) {
        this();
        this.userId = userId;
        this.token = token;
        this.expiresAt = expiresAt;
    }
    
    // ==================== Getters & Setters ====================
    
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public UUID getUserId() {
        return userId;
    }
    
    public void setUserId(UUID userId) {
        this.userId = userId;
    }
    
    public String getToken() {
        return token;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
    
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public String getDeviceInfo() {
        return deviceInfo;
    }
    
    public void setDeviceInfo(String deviceInfo) {
        this.deviceInfo = deviceInfo;
    }
    
    public String getIpAddress() {
        return ipAddress;
    }
    
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
    
    public boolean isRevoked() {
        return revoked;
    }
    
    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }
    
    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }
    
    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }
    
    // ==================== 业务方法 ====================
    
    /**
     * 检查令牌是否有效
     */
    public boolean isValid() {
        return !revoked && LocalDateTime.now().isBefore(expiresAt);
    }
    
    /**
     * 撤销令牌
     */
    public void revoke() {
        this.revoked = true;
        this.revokedAt = LocalDateTime.now();
    }
}
