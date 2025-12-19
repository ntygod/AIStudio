package com.inkflow.module.evolution.dto;

import com.inkflow.module.evolution.entity.ChangeRecord;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 变更记录DTO
 */
public record ChangeRecordDto(
    UUID id,
    UUID snapshotId,
    String fieldPath,
    String oldValue,
    String newValue,
    String changeReason,
    String sourceText,
    LocalDateTime createdAt
) {
    public static ChangeRecordDto from(ChangeRecord record) {
        return new ChangeRecordDto(
            record.getId(),
            record.getSnapshotId(),
            record.getFieldPath(),
            record.getOldValue(),
            record.getNewValue(),
            record.getChangeReason(),
            record.getSourceText(),
            record.getCreatedAt()
        );
    }
}
