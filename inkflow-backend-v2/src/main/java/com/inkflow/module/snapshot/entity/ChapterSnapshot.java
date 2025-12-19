package com.inkflow.module.snapshot.entity;

import com.inkflow.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

/**
 * 章节快照实体
 * 存储章节内容的历史版本
 */
@Entity
@Table(name = "chapter_snapshots", indexes = {
    @Index(name = "idx_chapter_snapshots_chapter_id", columnList = "chapter_id"),
    @Index(name = "idx_chapter_snapshots_created_at", columnList = "created_at")
})
public class ChapterSnapshot extends BaseEntity {

    @Column(name = "chapter_id", nullable = false)
    private UUID chapterId;

    /**
     * 快照内容
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * 字数统计
     */
    @Column(name = "word_count")
    private Integer wordCount;

    /**
     * 快照备注
     */
    @Column(length = 500)
    private String note;

    // Getters and Setters
    public UUID getChapterId() {
        return chapterId;
    }

    public void setChapterId(UUID chapterId) {
        this.chapterId = chapterId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getWordCount() {
        return wordCount;
    }

    public void setWordCount(Integer wordCount) {
        this.wordCount = wordCount;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ChapterSnapshot snapshot = new ChapterSnapshot();

        public Builder chapterId(UUID chapterId) {
            snapshot.chapterId = chapterId;
            return this;
        }

        public Builder content(String content) {
            snapshot.content = content;
            return this;
        }

        public Builder wordCount(Integer wordCount) {
            snapshot.wordCount = wordCount;
            return this;
        }

        public Builder note(String note) {
            snapshot.note = note;
            return this;
        }

        public ChapterSnapshot build() {
            return snapshot;
        }
    }
}
