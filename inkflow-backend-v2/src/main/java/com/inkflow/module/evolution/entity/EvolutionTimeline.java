package com.inkflow.module.evolution.entity;

import com.inkflow.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

/**
 * 演进时间线聚合根
 * 记录角色/设定随剧情发展的变化轨迹
 */
@Entity
@Table(name = "evolution_timelines",
       uniqueConstraints = @UniqueConstraint(columnNames = {"entity_type", "entity_id"}))
public class EvolutionTimeline extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "entity_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private EntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    public EvolutionTimeline() {}

    public EvolutionTimeline(UUID projectId, EntityType entityType, UUID entityId) {
        this.projectId = projectId;
        this.entityType = entityType;
        this.entityId = entityId;
    }

    // Getters and Setters
    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }
}
