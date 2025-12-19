package com.inkflow.module.agent.context;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 上下文事件
 * 用于 Agent 间的事件传递和状态同步
 *
 * @author zsg
 * @date 2025/12/15
 */
public record ContextEvent(
    String eventId,
    String eventType,
    String sourceAgent,
    Object payload,
    Map<String, Object> metadata,
    LocalDateTime timestamp
) {
    
    /**
     * 创建内容生成事件
     */
    public static ContextEvent contentGenerated(String sourceAgent, String content, Map<String, Object> metadata) {
        return new ContextEvent(
            UUID.randomUUID().toString(),
            EventType.CONTENT_GENERATED.name(),
            sourceAgent,
            content,
            metadata,
            LocalDateTime.now()
        );
    }
    
    /**
     * 创建实体更新事件
     */
    public static ContextEvent entityUpdated(String sourceAgent, UUID entityId, String entityType, Object entity) {
        return new ContextEvent(
            UUID.randomUUID().toString(),
            EventType.ENTITY_UPDATED.name(),
            sourceAgent,
            entity,
            Map.of("entityId", entityId.toString(), "entityType", entityType),
            LocalDateTime.now()
        );
    }
    
    /**
     * 创建一致性警告事件
     */
    public static ContextEvent consistencyWarning(String sourceAgent, String warning, Map<String, Object> details) {
        return new ContextEvent(
            UUID.randomUUID().toString(),
            EventType.CONSISTENCY_WARNING.name(),
            sourceAgent,
            warning,
            details,
            LocalDateTime.now()
        );
    }
    
    /**
     * 创建阶段变更事件
     */
    public static ContextEvent phaseChanged(String sourceAgent, String oldPhase, String newPhase) {
        return new ContextEvent(
            UUID.randomUUID().toString(),
            EventType.PHASE_CHANGED.name(),
            sourceAgent,
            newPhase,
            Map.of("oldPhase", oldPhase, "newPhase", newPhase),
            LocalDateTime.now()
        );
    }
    
    /**
     * 创建 Agent 完成事件
     */
    public static ContextEvent agentCompleted(String sourceAgent, Object result, long durationMs) {
        return new ContextEvent(
            UUID.randomUUID().toString(),
            EventType.AGENT_COMPLETED.name(),
            sourceAgent,
            result,
            Map.of("durationMs", durationMs),
            LocalDateTime.now()
        );
    }
    
    /**
     * 创建自定义事件
     */
    public static ContextEvent custom(String sourceAgent, String eventType, Object payload, Map<String, Object> metadata) {
        return new ContextEvent(
            UUID.randomUUID().toString(),
            eventType,
            sourceAgent,
            payload,
            metadata,
            LocalDateTime.now()
        );
    }
    
    /**
     * 事件类型枚举
     */
    public enum EventType {
        CONTENT_GENERATED,
        ENTITY_UPDATED,
        ENTITY_CREATED,
        ENTITY_DELETED,
        CONSISTENCY_WARNING,
        PHASE_CHANGED,
        AGENT_STARTED,
        AGENT_COMPLETED,
        AGENT_FAILED,
        TOOL_INVOKED,
        CUSTOM
    }
}
