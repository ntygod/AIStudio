package com.inkflow.module.extraction.dto;

import java.util.List;
import java.util.UUID;

/**
 * 提取结果
 */
public record ExtractionResult(
    UUID projectId,
    UUID sourceChapterId,
    List<ExtractedEntity> entities,
    List<ExtractedRelationship> relationships,
    ExtractionStatus status,
    String message
) {
    public enum ExtractionStatus {
        SUCCESS,
        PARTIAL,
        FAILED
    }

    public static ExtractionResult success(
            UUID projectId, UUID sourceChapterId,
            List<ExtractedEntity> entities, List<ExtractedRelationship> relationships) {
        return new ExtractionResult(projectId, sourceChapterId, entities, relationships, 
                ExtractionStatus.SUCCESS, null);
    }

    public static ExtractionResult partial(
            UUID projectId, UUID sourceChapterId,
            List<ExtractedEntity> entities, List<ExtractedRelationship> relationships,
            String message) {
        return new ExtractionResult(projectId, sourceChapterId, entities, relationships, 
                ExtractionStatus.PARTIAL, message);
    }

    public static ExtractionResult failed(UUID projectId, UUID sourceChapterId, String message) {
        return new ExtractionResult(projectId, sourceChapterId, List.of(), List.of(), 
                ExtractionStatus.FAILED, message);
    }
}
