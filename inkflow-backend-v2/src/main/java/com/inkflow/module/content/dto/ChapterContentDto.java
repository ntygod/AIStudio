package com.inkflow.module.content.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 章节内容数据传输对象
 * 用于编辑器获取和保存章节内容
 */
public record ChapterContentDto(
    UUID id,
    UUID chapterId,
    String content,
    int wordCount,
    LocalDateTime updatedAt
) {
}
