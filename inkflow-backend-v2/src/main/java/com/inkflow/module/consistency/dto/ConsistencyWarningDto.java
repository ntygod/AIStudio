package com.inkflow.module.consistency.dto;

import com.inkflow.module.consistency.entity.ConsistencyWarning;
import com.inkflow.module.consistency.entity.ConsistencyWarning.Severity;
import com.inkflow.module.consistency.entity.ConsistencyWarning.WarningType;
import com.inkflow.module.evolution.entity.EntityType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 一致性警告 DTO
 *
 * @author zsg
 * @date 2025/12/17
 */
public record ConsistencyWarningDto(
        UUID id,
        UUID projectId,
        UUID entityId,
        EntityType entityType,
        String entityName,
        WarningType warningType,
        Severity severity,
        String description,
        String suggestion,
        String fieldPath,
        String expectedValue,
        String actualValue,
        List<UUID> relatedEntityIds,
        String suggestedResolution,
        boolean resolved,
        boolean dismissed,
        String resolutionMethod,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt
) {
    
    /**
     * 从实体转换为 DTO
     */
    public static ConsistencyWarningDto fromEntity(ConsistencyWarning entity) {
        return new ConsistencyWarningDto(
                entity.getId(),
                entity.getProjectId(),
                entity.getEntityId(),
                entity.getEntityType(),
                entity.getEntityName(),
                entity.getWarningType(),
                entity.getSeverity(),
                entity.getDescription(),
                entity.getSuggestion(),
                entity.getFieldPath(),
                entity.getExpectedValue(),
                entity.getActualValue(),
                entity.getRelatedEntityIds(),
                entity.getSuggestedResolution(),
                entity.isResolved(),
                entity.isDismissed(),
                entity.getResolutionMethod(),
                entity.getResolvedAt(),
                entity.getCreatedAt()
        );
    }
}
