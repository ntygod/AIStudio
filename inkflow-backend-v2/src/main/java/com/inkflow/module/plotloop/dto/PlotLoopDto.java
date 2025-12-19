package com.inkflow.module.plotloop.dto;

import com.inkflow.module.plotloop.entity.PlotLoop;
import com.inkflow.module.plotloop.entity.PlotLoopStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 伏笔DTO
 */
public record PlotLoopDto(
    UUID id,
    UUID projectId,
    String title,
    String description,
    PlotLoopStatus status,
    UUID introChapterId,
    Integer introChapterOrder,
    UUID resolutionChapterId,
    Integer resolutionChapterOrder,
    String abandonReason,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static PlotLoopDto from(PlotLoop entity) {
        return new PlotLoopDto(
            entity.getId(),
            entity.getProjectId(),
            entity.getTitle(),
            entity.getDescription(),
            entity.getStatus(),
            entity.getIntroChapterId(),
            entity.getIntroChapterOrder(),
            entity.getResolutionChapterId(),
            entity.getResolutionChapterOrder(),
            entity.getAbandonReason(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
