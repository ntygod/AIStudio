package com.inkflow.module.snapshot.dto;

import com.inkflow.module.snapshot.entity.ChapterSnapshot;

import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 章节快照 DTO
 */
public record ChapterSnapshotDto(
    UUID id,
    UUID chapterId,
    String content,
    Integer wordCount,
    String note,
    long timestamp
) {
    public static ChapterSnapshotDto from(ChapterSnapshot snapshot) {
        return new ChapterSnapshotDto(
            snapshot.getId(),
            snapshot.getChapterId(),
            snapshot.getContent(),
            snapshot.getWordCount(),
            snapshot.getNote(),
            snapshot.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli()
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private UUID chapterId;
        private String content;
        private Integer wordCount;
        private String note;
        private long timestamp;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder chapterId(UUID chapterId) {
            this.chapterId = chapterId;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder wordCount(Integer wordCount) {
            this.wordCount = wordCount;
            return this;
        }

        public Builder note(String note) {
            this.note = note;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public ChapterSnapshotDto build() {
            return new ChapterSnapshotDto(id, chapterId, content, wordCount, note, timestamp);
        }
    }
}
