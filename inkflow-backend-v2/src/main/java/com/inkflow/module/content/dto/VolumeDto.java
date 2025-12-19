package com.inkflow.module.content.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 分卷数据传输对象
 */
public record VolumeDto(
    UUID id,
    UUID projectId,
    String title,
    String description,
    int orderIndex,
    int chapterCount,
    Long wordCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
