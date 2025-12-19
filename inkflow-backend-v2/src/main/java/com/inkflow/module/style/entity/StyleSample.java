package com.inkflow.module.style.entity;

import com.inkflow.common.entity.BaseEntity;
import com.inkflow.common.util.HalfVecType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.util.UUID;

/**
 * 风格样本实体
 * 存储用户对 AI 生成内容的修改，用于学习用户写作风格
 */
@Entity
@Table(name = "style_samples")
public class StyleSample extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "chapter_id")
    private UUID chapterId;

    /**
     * AI 原始生成内容
     */
    @Column(name = "original_ai", columnDefinition = "TEXT", nullable = false)
    private String originalAI;

    /**
     * 用户修改后的内容
     */
    @Column(name = "user_final", columnDefinition = "TEXT", nullable = false)
    private String userFinal;

    /**
     * 编辑比例 (0.0 - 1.0)
     */
    @Column(name = "edit_ratio", nullable = false)
    private Double editRatio;

    /**
     * 内容向量（用于相似度检索，BGE-M3 1024维半精度）
     */
    @Column(name = "vector", columnDefinition = "halfvec(1024)")
    @Type(HalfVecType.class)
    private float[] vector;

    /**
     * 字数统计
     */
    @Column(name = "word_count")
    private Integer wordCount;

    // Getters and Setters
    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public UUID getChapterId() {
        return chapterId;
    }

    public void setChapterId(UUID chapterId) {
        this.chapterId = chapterId;
    }

    public String getOriginalAI() {
        return originalAI;
    }

    public void setOriginalAI(String originalAI) {
        this.originalAI = originalAI;
    }

    public String getUserFinal() {
        return userFinal;
    }

    public void setUserFinal(String userFinal) {
        this.userFinal = userFinal;
    }

    public Double getEditRatio() {
        return editRatio;
    }

    public void setEditRatio(Double editRatio) {
        this.editRatio = editRatio;
    }

    public float[] getVector() {
        return vector;
    }

    public void setVector(float[] vector) {
        this.vector = vector;
    }

    public Integer getWordCount() {
        return wordCount;
    }

    public void setWordCount(Integer wordCount) {
        this.wordCount = wordCount;
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final StyleSample sample = new StyleSample();

        public Builder projectId(UUID projectId) {
            sample.projectId = projectId;
            return this;
        }

        public Builder chapterId(UUID chapterId) {
            sample.chapterId = chapterId;
            return this;
        }

        public Builder originalAI(String originalAI) {
            sample.originalAI = originalAI;
            return this;
        }

        public Builder userFinal(String userFinal) {
            sample.userFinal = userFinal;
            return this;
        }

        public Builder editRatio(Double editRatio) {
            sample.editRatio = editRatio;
            return this;
        }

        public Builder vector(float[] vector) {
            sample.vector = vector;
            return this;
        }

        public Builder wordCount(Integer wordCount) {
            sample.wordCount = wordCount;
            return this;
        }

        public StyleSample build() {
            return sample;
        }
    }
}
