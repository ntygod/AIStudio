package com.inkflow.module.content.dto;

import com.inkflow.module.content.entity.ChapterStatus;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 章节数据传输对象
 */
public record ChapterDto(
    UUID id,
    UUID projectId,
    UUID volumeId,
    String title,
    String summary,
    int orderIndex,
    ChapterStatus status,
    int wordCount,
    Map<String, Object> metadata,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
