package com.inkflow.module.content.dto;

import com.inkflow.module.content.entity.BlockType;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 剧情块数据传输对象
 */
public record StoryBlockDto(
    UUID id,
    UUID chapterId,
    String content,
    BlockType blockType,
    String rank,
    int wordCount,
    Map<String, Object> metadata,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
