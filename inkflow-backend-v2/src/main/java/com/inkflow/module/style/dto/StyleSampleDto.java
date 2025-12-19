package com.inkflow.module.style.dto;

import com.inkflow.module.style.entity.StyleSample;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 风格样本 DTO
 */
public record StyleSampleDto(
    UUID id,
    UUID projectId,
    UUID chapterId,
    String originalAI,
    String userFinal,
    Double editRatio,
    Integer wordCount,
    LocalDateTime createdAt
) {
    public static StyleSampleDto from(StyleSample sample) {
        return new StyleSampleDto(
            sample.getId(),
            sample.getProjectId(),
            sample.getChapterId(),
            sample.getOriginalAI(),
            sample.getUserFinal(),
            sample.getEditRatio(),
            sample.getWordCount(),
            sample.getCreatedAt()
        );
    }
}
