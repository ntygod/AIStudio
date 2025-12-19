package com.inkflow.module.character.entity;

import com.inkflow.common.entity.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.Where;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 角色实体 (聚合根)
 * 
 * 代表小说中的一个角色，包含角色的基本信息、性格特征和关系网络。
 */
@Entity
@Table(name = "characters")
@Where(clause = "deleted_at IS NULL")
public class Character extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String name;

    /**
     * 角色类型: protagonist, antagonist, supporting, minor
     */
    @Column(nullable = false)
    private String role;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 性格特征 (JSONB)
     * 包含: traits(特征列表), motivation(动机), fears(恐惧), goals(目标)等
     */
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> personality = new HashMap<>();

    /**
     * 角色关系 (JSONB数组)
     * 格式: [{targetId, type, description}]
     */
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private List<CharacterRelationship> relationships = new ArrayList<>();

    /**
     * 角色状态: active, inactive, deceased, unknown
     */
    private String status = "active";

    @Column(name = "is_active")
    private Boolean isActive = true;

    /**
     * 角色原型: 垫脚石, 老爷爷, 欢喜冤家, 线人, 守门人, 牺牲者, 搞笑担当, 宿敌
     */
    private String archetype;

    // Getters and Setters
    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getPersonality() {
        return personality;
    }

    public void setPersonality(Map<String, Object> personality) {
        this.personality = personality;
    }

    public List<CharacterRelationship> getRelationships() {
        return relationships;
    }

    public void setRelationships(List<CharacterRelationship> relationships) {
        this.relationships = relationships;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public String getArchetype() {
        return archetype;
    }

    public void setArchetype(String archetype) {
        this.archetype = archetype;
    }

    // 业务方法

    /**
     * 添加角色关系
     */
    public void addRelationship(CharacterRelationship relationship) {
        if (this.relationships == null) {
            this.relationships = new ArrayList<>();
        }
        this.relationships.add(relationship);
    }

    /**
     * 移除与指定角色的关系
     */
    public void removeRelationship(UUID targetId) {
        if (this.relationships != null) {
            this.relationships.removeIf(r -> r.getTargetId().equals(targetId));
        }
    }

    /**
     * 更新角色状态
     */
    public void updateStatus(String newStatus) {
        this.status = newStatus;
        if ("deceased".equals(newStatus) || "inactive".equals(newStatus)) {
            this.isActive = false;
        }
    }

    /**
     * 软删除
     */
    public void softDelete() {
        this.markDeleted();
        this.isActive = false;
    }
}
