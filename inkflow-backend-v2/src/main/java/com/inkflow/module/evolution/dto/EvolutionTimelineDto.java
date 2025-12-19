package com.inkflow.module.evolution.dto;

import com.inkflow.module.evolution.entity.EntityType;
import com.inkflow.module.evolution.entity.EvolutionTimeline;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 演进时间线DTO
 */
public record EvolutionTimelineDto(
    UUID id,
    UUID projectId,
    EntityType entityType,
    UUID entityId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<StateSnapshotDto> snapshots
) {
    public static EvolutionTimelineDto from(EvolutionTimeline timeline) {
        return new EvolutionTimelineDto(
            timeline.getId(),
            timeline.getProjectId(),
            timeline.getEntityType(),
            timeline.getEntityId(),
            timeline.getCreatedAt(),
            timeline.getUpdatedAt(),
            null
        );
    }

    public static EvolutionTimelineDto from(EvolutionTimeline timeline, List<StateSnapshotDto> snapshots) {
        return new EvolutionTimelineDto(
            timeline.getId(),
            timeline.getProjectId(),
            timeline.getEntityType(),
            timeline.getEntityId(),
            timeline.getCreatedAt(),
            timeline.getUpdatedAt(),
            snapshots
        );
    }
}
