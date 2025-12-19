package com.inkflow.module.auth.repository;

import com.inkflow.module.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 刷新令牌数据访问层
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    
    /**
     * 根据令牌值查找
     */
    Optional<RefreshToken> findByToken(String token);
    
    /**
     * 查找用户的所有有效令牌
     */
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.userId = :userId AND rt.revoked = false AND rt.expiresAt > :now")
    List<RefreshToken> findValidTokensByUserId(@Param("userId") UUID userId, @Param("now") LocalDateTime now);
    
    /**
     * 撤销用户的所有令牌（登出所有设备）
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true, rt.revokedAt = :now WHERE rt.userId = :userId AND rt.revoked = false")
    void revokeAllByUserId(@Param("userId") UUID userId, @Param("now") LocalDateTime now);
    
    /**
     * 撤销指定令牌
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true, rt.revokedAt = :now WHERE rt.token = :token")
    void revokeByToken(@Param("token") String token, @Param("now") LocalDateTime now);
    
    /**
     * 删除过期的令牌（定时清理任务）
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now OR (rt.revoked = true AND rt.revokedAt < :cutoff)")
    int deleteExpiredTokens(@Param("now") LocalDateTime now, @Param("cutoff") LocalDateTime cutoff);
    
    /**
     * 统计用户活跃设备数
     */
    @Query("SELECT COUNT(rt) FROM RefreshToken rt WHERE rt.userId = :userId AND rt.revoked = false AND rt.expiresAt > :now")
    long countActiveDevices(@Param("userId") UUID userId, @Param("now") LocalDateTime now);
}
