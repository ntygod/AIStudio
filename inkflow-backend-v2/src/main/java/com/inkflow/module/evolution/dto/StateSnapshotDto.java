package com.inkflow.module.evolution.dto;

import com.inkflow.module.evolution.entity.ChangeType;
import com.inkflow.module.evolution.entity.StateSnapshot;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 状态快照DTO
 */
public record StateSnapshotDto(
    UUID id,
    UUID timelineId,
    UUID chapterId,
    Integer chapterOrder,
    Boolean isKeyframe,
    Map<String, Object> stateData,
    String changeSummary,
    ChangeType changeType,
    BigDecimal aiConfidence,
    LocalDateTime createdAt,
    List<ChangeRecordDto> changeRecords
) {
    public static StateSnapshotDto from(StateSnapshot snapshot) {
        return new StateSnapshotDto(
            snapshot.getId(),
            snapshot.getTimelineId(),
            snapshot.getChapterId(),
            snapshot.getChapterOrder(),
            snapshot.getIsKeyframe(),
            snapshot.getStateData(),
            snapshot.getChangeSummary(),
            snapshot.getChangeType(),
            snapshot.getAiConfidence(),
            snapshot.getCreatedAt(),
            null
        );
    }

    public static StateSnapshotDto from(StateSnapshot snapshot, List<ChangeRecordDto> changeRecords) {
        return new StateSnapshotDto(
            snapshot.getId(),
            snapshot.getTimelineId(),
            snapshot.getChapterId(),
            snapshot.getChapterOrder(),
            snapshot.getIsKeyframe(),
            snapshot.getStateData(),
            snapshot.getChangeSummary(),
            snapshot.getChangeType(),
            snapshot.getAiConfidence(),
            snapshot.getCreatedAt(),
            changeRecords
        );
    }
}
