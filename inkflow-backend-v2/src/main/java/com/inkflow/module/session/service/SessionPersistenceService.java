package com.inkflow.module.session.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.module.session.entity.UserSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * 会话持久化服务
 * 使用 Redis 存储会话状态，支持服务重启恢复
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionPersistenceService {

    private static final String SESSION_KEY_PREFIX = "session:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 持久化会话到 Redis
     */
    public void persistToRedis(UserSession session) {
        String key = getSessionKey(session.getId());
        try {
            String json = objectMapper.writeValueAsString(SessionData.from(session));
            Duration ttl = calculateTTL(session);
            redisTemplate.opsForValue().set(key, json, ttl);
            log.debug("会话已持久化到 Redis: sessionId={}", session.getId());
        } catch (JsonProcessingException e) {
            log.error("序列化会话失败: sessionId={}", session.getId(), e);
        }
    }

    /**
     * 从 Redis 恢复会话
     */
    public Optional<SessionData> restoreFromRedis(UUID sessionId) {
        String key = getSessionKey(sessionId);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            SessionData data = objectMapper.readValue(json, SessionData.class);
            log.debug("从 Redis 恢复会话: sessionId={}", sessionId);
            return Optional.of(data);
        } catch (JsonProcessingException e) {
            log.error("反序列化会话失败: sessionId={}", sessionId, e);
            return Optional.empty();
        }
    }

    /**
     * 从 Redis 删除会话
     */
    public void removeFromRedis(UUID sessionId) {
        String key = getSessionKey(sessionId);
        Boolean deleted = redisTemplate.delete(key);
        if (Boolean.TRUE.equals(deleted)) {
            log.debug("从 Redis 删除会话: sessionId={}", sessionId);
        }
    }

    /**
     * 更新会话 TTL
     */
    public void updateTTL(UUID sessionId, Duration ttl) {
        String key = getSessionKey(sessionId);
        redisTemplate.expire(key, ttl);
    }

    /**
     * 检查会话是否存在于 Redis
     */
    public boolean existsInRedis(UUID sessionId) {
        String key = getSessionKey(sessionId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    private String getSessionKey(UUID sessionId) {
        return SESSION_KEY_PREFIX + sessionId.toString();
    }

    private Duration calculateTTL(UserSession session) {
        if (session.getExpiresAt() == null) {
            return DEFAULT_TTL;
        }
        Duration remaining = Duration.between(java.time.LocalDateTime.now(), session.getExpiresAt());
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /**
     * 会话数据传输对象（用于 Redis 序列化）
     */
    public record SessionData(
            UUID id,
            UUID userId,
            String deviceInfo,
            String ipAddress,
            UUID currentProjectId,
            String currentPhase,
            String lastActivityAt,
            String expiresAt,
            boolean active
    ) {
        public static SessionData from(UserSession session) {
            return new SessionData(
                    session.getId(),
                    session.getUserId(),
                    session.getDeviceInfo(),
                    session.getIpAddress(),
                    session.getCurrentProjectId(),
                    session.getCurrentPhase() != null ? session.getCurrentPhase().name() : null,
                    session.getLastActivityAt() != null ? session.getLastActivityAt().toString() : null,
                    session.getExpiresAt() != null ? session.getExpiresAt().toString() : null,
                    session.isActive()
            );
        }
    }
}
