package com.inkflow.module.session.repository;

import com.inkflow.module.session.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    List<UserSession> findByUserIdAndActiveTrue(UUID userId);

    List<UserSession> findByUserIdAndActiveTrueOrderByLastActivityAtDesc(UUID userId);

    @Query("SELECT s FROM UserSession s WHERE s.userId = :userId AND s.active = true AND s.id != :exceptId")
    List<UserSession> findActiveSessionsExcept(@Param("userId") UUID userId, @Param("exceptId") UUID exceptId);

    @Modifying
    @Query("UPDATE UserSession s SET s.active = false WHERE s.userId = :userId AND s.id != :exceptId")
    int terminateAllExcept(@Param("userId") UUID userId, @Param("exceptId") UUID exceptId);

    @Modifying
    @Query("UPDATE UserSession s SET s.active = false WHERE s.expiresAt < :now AND s.active = true")
    int expireSessions(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(s) FROM UserSession s WHERE s.userId = :userId AND s.active = true")
    long countActiveSessions(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.active = false AND s.expiresAt < :cutoff")
    int deleteExpiredSessions(@Param("cutoff") LocalDateTime cutoff);
}
