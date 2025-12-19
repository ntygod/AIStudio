package com.inkflow.module.agent.skill;

import com.inkflow.module.agent.context.SessionContext;
import com.inkflow.module.project.entity.CreationPhase;

import java.util.Map;
import java.util.UUID;

/**
 * 技能上下文
 * 提供技能执行所需的上下文信息
 * 
 * Requirements: 17.1-17.5
 */
public record SkillContext(
    UUID projectId,
    UUID userId,
    CreationPhase currentPhase,
    String userMessage,
    Map<String, Object> metadata,
    SessionContext sessionContext
) {
    
    /**
     * 从 SessionContext 创建 SkillContext
     */
    public static SkillContext from(SessionContext session, String userMessage) {
        return new SkillContext(
            session.projectId(),
            session.userId(),
            session.currentPhase(),
            userMessage,
            Map.of(),
            session
        );
    }
    
    /**
     * 从 SessionContext 创建带元数据的 SkillContext
     */
    public static SkillContext from(SessionContext session, String userMessage, Map<String, Object> metadata) {
        return new SkillContext(
            session.projectId(),
            session.userId(),
            session.currentPhase(),
            userMessage,
            metadata,
            session
        );
    }
    
    /**
     * 创建空的 SkillContext（用于测试）
     */
    public static SkillContext empty() {
        return new SkillContext(
            null,
            null,
            null,
            "",
            Map.of(),
            null
        );
    }
    
    /**
     * 获取元数据值
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * 检查用户消息是否包含关键词
     */
    public boolean messageContains(String keyword) {
        return userMessage != null && userMessage.contains(keyword);
    }
    
    /**
     * 检查用户消息是否包含任一关键词
     */
    public boolean messageContainsAny(String... keywords) {
        if (userMessage == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (userMessage.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
