package com.inkflow.module.session.service;

import com.inkflow.module.session.dto.SessionDto;
import com.inkflow.module.session.dto.SessionCreateRequest;
import com.inkflow.module.session.entity.UserSession;
import com.inkflow.module.session.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 会话管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionManagementService {

    private static final Duration DEFAULT_SESSION_DURATION = Duration.ofHours(24);
    private static final Duration CLEANUP_RETENTION = Duration.ofDays(7);

    private final UserSessionRepository sessionRepository;
    private final SessionPersistenceService persistenceService;

    /**
     * 创建会话
     */
    @Transactional
    public SessionDto createSession(UUID userId, SessionCreateRequest request) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plus(DEFAULT_SESSION_DURATION);

        UserSession session = UserSession.builder()
                .userId(userId)
                .deviceInfo(request.getDeviceInfo())
                .ipAddress(request.getIpAddress())
                .userAgent(request.getUserAgent())
                .currentProjectId(request.getCurrentProjectId())
                .lastActivityAt(now)
                .expiresAt(expiresAt)
                .active(true)
                .build();

        session = sessionRepository.save(session);
        persistenceService.persistToRedis(session);
        
        log.info("创建会话: userId={}, sessionId={}", userId, session.getId());
        return toDto(session);
    }

    /**
     * 获取会话
     */
    @Transactional(readOnly = true)
    public Optional<SessionDto> getSession(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .filter(s -> s.isActive() && !s.isExpired())
                .map(this::toDto);
    }

    /**
     * 更新活动时间
     */
    @Transactional
    public void updateActivity(UUID sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.updateActivity();
            sessionRepository.save(session);
            persistenceService.persistToRedis(session);
        });
    }

    /**
     * 终止会话
     */
    @Transactional
    public void terminateSession(UUID sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.terminate();
            sessionRepository.save(session);
            persistenceService.removeFromRedis(sessionId);
            log.info("终止会话: sessionId={}", sessionId);
        });
    }

    /**
     * 终止用户所有会话（除了指定的）
     */
    @Transactional
    public int terminateAllSessions(UUID userId, UUID exceptSessionId) {
        List<UserSession> sessions = sessionRepository.findActiveSessionsExcept(userId, exceptSessionId);
        sessions.forEach(session -> {
            session.terminate();
            persistenceService.removeFromRedis(session.getId());
        });
        sessionRepository.saveAll(sessions);
        log.info("终止用户所有会话: userId={}, count={}", userId, sessions.size());
        return sessions.size();
    }

    /**
     * 获取用户活跃会话
     */
    @Transactional(readOnly = true)
    public List<SessionDto> getActiveSessions(UUID userId) {
        return sessionRepository.findByUserIdAndActiveTrueOrderByLastActivityAtDesc(userId)
                .stream()
                .filter(s -> !s.isExpired())
                .map(this::toDto)
                .toList();
    }

    /**
     * 清理过期会话（定时任务）
     * 
     * Requirements: 3.3, 4.4 - 会话过期时自动清理 Redis 数据，24小时不活跃标记为过期
     */
    @Scheduled(fixedRate = 3600000) // 每小时执行
    @Transactional
    public void cleanupExpiredSessions() {
        log.debug("开始清理过期会话...");
        LocalDateTime now = LocalDateTime.now();
        
        // 1. 查找所有过期但仍标记为活跃的会话，清理 Redis
        List<UserSession> expiredSessions = sessionRepository.findAll().stream()
                .filter(UserSession::isActive)
                .filter(UserSession::isExpired)
                .toList();
        
        int redisCleanedCount = 0;
        for (UserSession session : expiredSessions) {
            try {
                persistenceService.removeFromRedis(session.getId());
                redisCleanedCount++;
            } catch (Exception e) {
                log.warn("清理 Redis 会话失败: sessionId={}", session.getId(), e);
            }
        }
        
        // 2. 在数据库中标记过期会话为非活跃
        int expired = sessionRepository.expireSessions(now);
        
        // 3. 删除超过保留期的旧会话
        LocalDateTime cutoff = now.minus(CLEANUP_RETENTION);
        int deleted = sessionRepository.deleteExpiredSessions(cutoff);
        
        if (expired > 0 || deleted > 0 || redisCleanedCount > 0) {
            log.info("清理会话完成: 过期={}, 删除={}, Redis清理={}", expired, deleted, redisCleanedCount);
        }
    }

    private SessionDto toDto(UserSession session) {
        return SessionDto.builder()
                .id(session.getId())
                .userId(session.getUserId())
                .deviceInfo(session.getDeviceInfo())
                .ipAddress(session.getIpAddress())
                .userAgent(session.getUserAgent())
                .currentProjectId(session.getCurrentProjectId())
                .currentPhase(session.getCurrentPhase())
                .lastActivityAt(session.getLastActivityAt())
                .expiresAt(session.getExpiresAt())
                .active(session.isActive())
                .createdAt(session.getCreatedAt())
                .build();
    }
}
