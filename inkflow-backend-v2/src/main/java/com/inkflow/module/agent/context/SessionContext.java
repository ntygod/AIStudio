package com.inkflow.module.agent.context;

import com.inkflow.module.project.entity.CreationPhase;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话上下文
 * 存储 Agent 执行过程中的共享状态
 * 
 * Requirements: 16.1-16.5
 */
public record SessionContext(
    String sessionId,
    UUID projectId,
    UUID userId,
    CreationPhase currentPhase,
    List<RecentEntity> recentEntities,
    Map<String, Object> workingMemory,
    LocalDateTime createdAt,
    LocalDateTime lastAccessedAt
) {
    
    /**
     * 创建空的会话上下文
     */
    public static SessionContext empty(String sessionId) {
        return new SessionContext(
            sessionId,
            null,
            null,
            null,
            List.of(),
            new ConcurrentHashMap<>(),
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }
    
    /**
     * 创建带项目信息的会话上下文
     */
    public static SessionContext forProject(String sessionId, UUID projectId, UUID userId, CreationPhase phase) {
        return new SessionContext(
            sessionId,
            projectId,
            userId,
            phase,
            List.of(),
            new ConcurrentHashMap<>(),
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }
    
    /**
     * 更新最后访问时间
     */
    public SessionContext touch() {
        return new SessionContext(
            sessionId,
            projectId,
            userId,
            currentPhase,
            recentEntities,
            workingMemory,
            createdAt,
            LocalDateTime.now()
        );
    }
    
    /**
     * 更新创作阶段
     */
    public SessionContext withPhase(CreationPhase newPhase) {
        return new SessionContext(
            sessionId,
            projectId,
            userId,
            newPhase,
            recentEntities,
            workingMemory,
            createdAt,
            LocalDateTime.now()
        );
    }
    
    /**
     * 添加最近实体
     */
    public SessionContext withRecentEntity(RecentEntity entity) {
        var newEntities = new java.util.ArrayList<>(recentEntities);
        newEntities.add(0, entity);
        // 保留最近 20 个实体
        List<RecentEntity> trimmedEntities = newEntities.size() > 20 
            ? List.copyOf(newEntities.subList(0, 20))
            : List.copyOf(newEntities);
        return new SessionContext(
            sessionId,
            projectId,
            userId,
            currentPhase,
            trimmedEntities,
            workingMemory,
            createdAt,
            LocalDateTime.now()
        );
    }
    
    /**
     * 设置工作内存值
     */
    public SessionContext withMemory(String key, Object value) {
        var newMemory = new ConcurrentHashMap<>(workingMemory);
        newMemory.put(key, value);
        return new SessionContext(
            sessionId,
            projectId,
            userId,
            currentPhase,
            recentEntities,
            newMemory,
            createdAt,
            LocalDateTime.now()
        );
    }
    
    /**
     * 获取工作内存值
     */
    @SuppressWarnings("unchecked")
    public <T> T getMemory(String key, Class<T> type) {
        Object value = workingMemory.get(key);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * 检查会话是否过期（默认 24 小时）
     */
    public boolean isExpired() {
        return lastAccessedAt.plusHours(24).isBefore(LocalDateTime.now());
    }
}
