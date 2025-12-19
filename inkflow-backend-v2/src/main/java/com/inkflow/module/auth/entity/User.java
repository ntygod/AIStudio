package com.inkflow.module.auth.entity;

import com.inkflow.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 用户实体
 * 
 * 支持多种认证方式：
 * - 邮箱/密码登录
 * - Passkey (WebAuthn) 无密码登录
 * - OAuth2 第三方登录
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_email", columnList = "email", unique = true),
    @Index(name = "idx_users_username", columnList = "username", unique = true)
})
public class User extends BaseEntity {
    
    /**
     * 用户名（唯一）
     */
    @Column(nullable = false, unique = true, length = 50)
    private String username;
    
    /**
     * 邮箱（唯一）
     */
    @Column(nullable = false, unique = true, length = 100)
    private String email;
    
    /**
     * 密码哈希（BCrypt）
     * 使用Passkey时可为空
     */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;
    
    /**
     * 显示名称
     */
    @Column(name = "display_name", length = 100)
    private String displayName;
    
    /**
     * 头像URL（支持 Base64 Data URL 或外部 URL）
     */
    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;
    
    /**
     * 个人简介
     */
    @Column(length = 500)
    private String bio;
    
    /**
     * 账户状态
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;
    
    /**
     * 邮箱是否已验证
     */
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;
    
    /**
     * 最后登录时间
     */
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    
    /**
     * 登录失败次数（用于账户锁定）
     */
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;
    
    /**
     * 账户锁定截止时间
     */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;
    
    // ==================== Getters & Setters ====================
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getPasswordHash() {
        return passwordHash;
    }
    
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    public String getAvatarUrl() {
        return avatarUrl;
    }
    
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
    
    public String getBio() {
        return bio;
    }
    
    public void setBio(String bio) {
        this.bio = bio;
    }
    
    public UserStatus getStatus() {
        return status;
    }
    
    public void setStatus(UserStatus status) {
        this.status = status;
    }
    
    public boolean isEmailVerified() {
        return emailVerified;
    }
    
    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }
    
    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }
    
    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
    
    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }
    
    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }
    
    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }
    
    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }
    
    // ==================== 业务方法 ====================
    
    /**
     * 检查账户是否被锁定
     */
    public boolean isLocked() {
        return lockedUntil != null && LocalDateTime.now().isBefore(lockedUntil);
    }
    
    /**
     * 记录登录成功
     */
    public void recordLoginSuccess() {
        this.lastLoginAt = LocalDateTime.now();
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }
    
    /**
     * 记录登录失败
     * 连续失败5次后锁定账户30分钟
     */
    public void recordLoginFailure() {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= 5) {
            this.lockedUntil = LocalDateTime.now().plusMinutes(30);
        }
    }
}
