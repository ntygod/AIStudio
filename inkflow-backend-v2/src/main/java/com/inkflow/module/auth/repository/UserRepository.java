package com.inkflow.module.auth.repository;

import com.inkflow.module.auth.entity.User;
import com.inkflow.module.auth.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 用户数据访问层
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    
    /**
     * 根据邮箱查找用户
     */
    Optional<User> findByEmail(String email);
    
    /**
     * 根据用户名查找用户
     */
    Optional<User> findByUsername(String username);
    
    /**
     * 根据邮箱或用户名查找用户（登录时使用）
     */
    @Query("SELECT u FROM User u WHERE u.email = :identifier OR u.username = :identifier")
    Optional<User> findByEmailOrUsername(@Param("identifier") String identifier);
    
    /**
     * 检查邮箱是否已存在
     */
    boolean existsByEmail(String email);
    
    /**
     * 检查用户名是否已存在
     */
    boolean existsByUsername(String username);
    
    /**
     * 更新最后登录时间
     */
    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :loginTime, u.failedLoginAttempts = 0, u.lockedUntil = null WHERE u.id = :userId")
    void updateLoginSuccess(@Param("userId") UUID userId, @Param("loginTime") LocalDateTime loginTime);
    
    /**
     * 增加登录失败次数
     */
    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = u.failedLoginAttempts + 1 WHERE u.id = :userId")
    void incrementFailedAttempts(@Param("userId") UUID userId);
    
    /**
     * 锁定账户
     */
    @Modifying
    @Query("UPDATE User u SET u.lockedUntil = :lockedUntil WHERE u.id = :userId")
    void lockAccount(@Param("userId") UUID userId, @Param("lockedUntil") LocalDateTime lockedUntil);
    
    /**
     * 更新用户状态
     */
    @Modifying
    @Query("UPDATE User u SET u.status = :status WHERE u.id = :userId")
    void updateStatus(@Param("userId") UUID userId, @Param("status") UserStatus status);
    
    /**
     * 验证邮箱
     */
    @Modifying
    @Query("UPDATE User u SET u.emailVerified = true WHERE u.id = :userId")
    void verifyEmail(@Param("userId") UUID userId);
}
