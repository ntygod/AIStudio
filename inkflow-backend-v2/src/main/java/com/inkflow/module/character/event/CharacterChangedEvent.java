package com.inkflow.module.character.event;

import org.springframework.context.ApplicationEvent;

import java.util.Map;
import java.util.UUID;

/**
 * 角色变更事件
 * 
 * 当角色创建、更新或删除时发布此事件，
 * 用于触发一致性检查和演进快照创建
 * 
 * Requirements: 2.1, 2.2, 2.3, 2.4
 */
public class CharacterChangedEvent extends ApplicationEvent {

    private final UUID projectId;
    private final UUID characterId;
    private final String characterName;
    private final Operation operation;
    private final Map<String, Object> currentState;
    private final Map<String, Object> previousState;

    public CharacterChangedEvent(
            Object source,
            UUID projectId,
            UUID characterId,
            String characterName,
            Operation operation,
            Map<String, Object> currentState,
            Map<String, Object> previousState
    ) {
        super(source);
        this.projectId = projectId;
        this.characterId = characterId;
        this.characterName = characterName;
        this.operation = operation;
        this.currentState = currentState;
        this.previousState = previousState;
    }

    /**
     * 操作类型枚举
     */
    public enum Operation {
        CREATE,
        UPDATE,
        DELETE
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getCharacterId() {
        return characterId;
    }

    public String getCharacterName() {
        return characterName;
    }

    public Operation getOperation() {
        return operation;
    }

    public Map<String, Object> getCurrentState() {
        return currentState;
    }

    public Map<String, Object> getPreviousState() {
        return previousState;
    }

    /**
     * 便捷工厂方法 - 创建事件
     */
    public static CharacterChangedEvent created(Object source, UUID projectId, UUID characterId, 
                                                 String characterName, Map<String, Object> state) {
        return new CharacterChangedEvent(source, projectId, characterId, characterName, 
                Operation.CREATE, state, null);
    }

    /**
     * 便捷工厂方法 - 更新事件
     */
    public static CharacterChangedEvent updated(Object source, UUID projectId, UUID characterId,
                                                 String characterName, Map<String, Object> currentState,
                                                 Map<String, Object> previousState) {
        return new CharacterChangedEvent(source, projectId, characterId, characterName,
                Operation.UPDATE, currentState, previousState);
    }

    /**
     * 便捷工厂方法 - 删除事件
     */
    public static CharacterChangedEvent deleted(Object source, UUID projectId, UUID characterId,
                                                 String characterName) {
        return new CharacterChangedEvent(source, projectId, characterId, characterName,
                Operation.DELETE, null, null);
    }
}
