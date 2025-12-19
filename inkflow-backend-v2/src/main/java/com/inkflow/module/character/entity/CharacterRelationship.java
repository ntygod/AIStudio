package com.inkflow.module.character.entity;

import java.io.Serializable;
import java.util.UUID;

/**
 * 角色关系值对象
 * 
 * 存储在Character实体的relationships JSONB字段中
 */
public class CharacterRelationship implements Serializable {

    /**
     * 目标角色ID
     */
    private UUID targetId;

    /**
     * 关系类型: friend, enemy, family, lover, mentor, student, rival, ally
     */
    private String type;

    /**
     * 关系描述
     */
    private String description;

    /**
     * 关系强度 (1-10)
     */
    private Integer strength;

    /**
     * 是否双向关系
     */
    private Boolean bidirectional;

    public CharacterRelationship() {
    }

    public CharacterRelationship(UUID targetId, String type, String description) {
        this.targetId = targetId;
        this.type = type;
        this.description = description;
        this.strength = 5;
        this.bidirectional = true;
    }

    // Getters and Setters
    public UUID getTargetId() {
        return targetId;
    }

    public void setTargetId(UUID targetId) {
        this.targetId = targetId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getStrength() {
        return strength;
    }

    public void setStrength(Integer strength) {
        this.strength = strength;
    }

    public Boolean getBidirectional() {
        return bidirectional;
    }

    public void setBidirectional(Boolean bidirectional) {
        this.bidirectional = bidirectional;
    }
}
