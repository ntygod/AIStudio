package com.inkflow.module.wiki.dto;

import com.inkflow.module.wiki.entity.WikiEntry;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 知识条目DTO
 */
public record WikiEntryDto(
    UUID id,
    UUID projectId,
    String title,
    String type,
    String content,
    String[] aliases,
    String[] tags,
    String timeVersion,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static WikiEntryDto from(WikiEntry entity) {
        return new WikiEntryDto(
            entity.getId(),
            entity.getProjectId(),
            entity.getTitle(),
            entity.getType(),
            entity.getContent(),
            entity.getAliases(),
            entity.getTags(),
            entity.getTimeVersion(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
