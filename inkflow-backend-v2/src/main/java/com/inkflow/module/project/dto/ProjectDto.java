package com.inkflow.module.project.dto;

import com.inkflow.module.project.entity.CreationPhase;
import com.inkflow.module.project.entity.Project;
import com.inkflow.module.project.entity.ProjectStatus;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 项目信息DTO
 */
public record ProjectDto(
    UUID id,
    UUID userId,
    String title,
    String description,
    String coverUrl,
    ProjectStatus status,
    CreationPhase creationPhase,
    Map<String, Object> metadata,
    Map<String, Object> worldSettings,
    Long wordCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    /**
     * 从实体转换为DTO（不含字数统计）
     */
    public static ProjectDto fromEntity(Project project) {
        return fromEntity(project, null);
    }
    
    /**
     * 从实体转换为DTO（含字数统计）
     */
    public static ProjectDto fromEntity(Project project, Long wordCount) {
        return new ProjectDto(
            project.getId(),
            project.getUserId(),
            project.getTitle(),
            project.getDescription(),
            project.getCoverUrl(),
            project.getStatus(),
            project.getCreationPhase(),
            project.getMetadata(),
            project.getWorldSettings(),
            wordCount,
            project.getCreatedAt(),
            project.getUpdatedAt()
        );
    }
}
