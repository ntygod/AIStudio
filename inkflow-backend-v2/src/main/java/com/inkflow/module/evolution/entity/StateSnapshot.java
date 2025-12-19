package com.inkflow.module.evolution.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 状态快照实体
 * 支持关键帧+增量策略减少存储
 */
@Entity
@Table(name = "state_snapshots")
public class StateSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "timeline_id", nullable = false)
    private UUID timelineId;

    @Column(name = "chapter_id", nullable = false)
    private UUID chapterId;

    @Column(name = "chapter_order", nullable = false)
    private Integer chapterOrder;

    @Column(name = "is_keyframe")
    private Boolean isKeyframe = false;

    @Column(name = "state_data", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> stateData;

    @Column(name = "change_summary", columnDefinition = "TEXT")
    private String changeSummary;

    @Column(name = "change_type", length = 50)
    @Enumerated(EnumType.STRING)
    private ChangeType changeType = ChangeType.UPDATE;

    @Column(name = "ai_confidence", precision = 3, scale = 2)
    private BigDecimal aiConfidence;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public StateSnapshot() {}

    // Builder pattern for convenience
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final StateSnapshot snapshot = new StateSnapshot();

        public Builder timelineId(UUID timelineId) {
            snapshot.timelineId = timelineId;
            return this;
        }

        public Builder chapterId(UUID chapterId) {
            snapshot.chapterId = chapterId;
            return this;
        }

        public Builder chapterOrder(Integer chapterOrder) {
            snapshot.chapterOrder = chapterOrder;
            return this;
        }

        public Builder isKeyframe(Boolean isKeyframe) {
            snapshot.isKeyframe = isKeyframe;
            return this;
        }

        public Builder stateData(Map<String, Object> stateData) {
            snapshot.stateData = stateData;
            return this;
        }

        public Builder changeSummary(String changeSummary) {
            snapshot.changeSummary = changeSummary;
            return this;
        }

        public Builder changeType(ChangeType changeType) {
            snapshot.changeType = changeType;
            return this;
        }

        public Builder aiConfidence(BigDecimal aiConfidence) {
            snapshot.aiConfidence = aiConfidence;
            return this;
        }

        public StateSnapshot build() {
            return snapshot;
        }
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTimelineId() {
        return timelineId;
    }

    public void setTimelineId(UUID timelineId) {
        this.timelineId = timelineId;
    }

    public UUID getChapterId() {
        return chapterId;
    }

    public void setChapterId(UUID chapterId) {
        this.chapterId = chapterId;
    }

    public Integer getChapterOrder() {
        return chapterOrder;
    }

    public void setChapterOrder(Integer chapterOrder) {
        this.chapterOrder = chapterOrder;
    }

    public Boolean getIsKeyframe() {
        return isKeyframe;
    }

    public void setIsKeyframe(Boolean keyframe) {
        isKeyframe = keyframe;
    }

    public Map<String, Object> getStateData() {
        return stateData;
    }

    public void setStateData(Map<String, Object> stateData) {
        this.stateData = stateData;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(String changeSummary) {
        this.changeSummary = changeSummary;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public void setChangeType(ChangeType changeType) {
        this.changeType = changeType;
    }

    public BigDecimal getAiConfidence() {
        return aiConfidence;
    }

    public void setAiConfidence(BigDecimal aiConfidence) {
        this.aiConfidence = aiConfidence;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
