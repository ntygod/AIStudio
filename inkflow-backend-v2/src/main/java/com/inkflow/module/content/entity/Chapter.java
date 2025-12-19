package com.inkflow.module.content.entity;

import com.inkflow.common.entity.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 章节实体
 * 
 * 代表小说的一个章节，包含：
 * - 基本信息（标题、摘要）
 * - 章节状态
 * - 元数据（JSONB）
 */
@Entity
@Table(name = "chapters", indexes = {
    @Index(name = "idx_chapters_volume_id", columnList = "volume_id"),
    @Index(name = "idx_chapters_project_id", columnList = "project_id"),
    @Index(name = "idx_chapters_order", columnList = "volume_id, order_index")
})
public class Chapter extends BaseEntity {
    
    /**
     * 所属项目ID（冗余字段，便于查询）
     */
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    
    /**
     * 所属分卷ID
     */
    @Column(name = "volume_id", nullable = false)
    private UUID volumeId;
    
    /**
     * 章节标题
     */
    @Column(nullable = false, length = 200)
    private String title;
    
    /**
     * 章节摘要/大纲
     */
    @Column(columnDefinition = "TEXT")
    private String summary;
    
    /**
     * 排序索引
     */
    @Column(name = "order_index", nullable = false)
    private int orderIndex;
    
    /**
     * 章节状态
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChapterStatus status = ChapterStatus.DRAFT;
    
    /**
     * 字数统计
     */
    @Column(name = "word_count", nullable = false)
    private int wordCount = 0;
    
    /**
     * 章节元数据（JSONB）
     * 
     * 可存储：
     * - outline: 详细大纲
     * - notes: 创作笔记
     * - characters: 出场角色ID列表
     * - locations: 场景地点
     */
    @Column(columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata = new HashMap<>();
    
    // ==================== Getters & Setters ====================
    
    public UUID getProjectId() {
        return projectId;
    }
    
    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }
    
    public UUID getVolumeId() {
        return volumeId;
    }
    
    public void setVolumeId(UUID volumeId) {
        this.volumeId = volumeId;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getSummary() {
        return summary;
    }
    
    public void setSummary(String summary) {
        this.summary = summary;
    }
    
    public int getOrderIndex() {
        return orderIndex;
    }
    
    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }
    
    public ChapterStatus getStatus() {
        return status;
    }
    
    public void setStatus(ChapterStatus status) {
        this.status = status;
    }
    
    public int getWordCount() {
        return wordCount;
    }
    
    public void setWordCount(int wordCount) {
        this.wordCount = wordCount;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
