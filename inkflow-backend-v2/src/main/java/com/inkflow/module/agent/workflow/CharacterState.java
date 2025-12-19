package com.inkflow.module.agent.workflow;

import java.util.Map;
import java.util.UUID;

/**
 * 角色状态
 * 用于在预处理阶段传递角色的当前状态信息
 * 
 * @see Requirements 2.3
 */
public record CharacterState(
    UUID characterId,
    String name,
    String currentState,
    Map<String, Object> attributes
) {
    
    /**
     * 创建简单的角色状态
     */
    public static CharacterState of(UUID characterId, String name, String currentState) {
        return new CharacterState(characterId, name, currentState, Map.of());
    }
    
    /**
     * 创建带属性的角色状态
     */
    public static CharacterState of(UUID characterId, String name, String currentState, Map<String, Object> attributes) {
        return new CharacterState(characterId, name, currentState, attributes);
    }
    
    /**
     * 获取属性值
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return attributes != null ? (T) attributes.get(key) : null;
    }
    
    /**
     * 检查是否有指定属性
     */
    public boolean hasAttribute(String key) {
        return attributes != null && attributes.containsKey(key);
    }
}
