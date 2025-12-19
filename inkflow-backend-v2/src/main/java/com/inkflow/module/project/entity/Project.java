package com.inkflow.module.project.entity;

import com.inkflow.common.entity.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 项目实体（聚合根）
 * 
 * 代表一部小说作品，包含：
 * - 基本信息（标题、简介、封面）
 * - 元数据（JSONB存储，灵活扩展）
 * - 创作阶段状态
 */
@Entity
@Table(name = "projects", indexes = {
    @Index(name = "idx_projects_user_id", columnList = "user_id"),
    @Index(name = "idx_projects_status", columnList = "status")
})
public class Project extends BaseEntity {
    
    /**
     * 所属用户ID
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    /**
     * 项目标题
     */
    @Column(nullable = false, length = 200)
    private String title;
    
    /**
     * 项目简介
     */
    @Column(columnDefinition = "TEXT")
    private String description;
    
    /**
     * 封面图片URL
     */
    @Column(name = "cover_url", length = 500)
    private String coverUrl;
    
    /**
     * 项目状态
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status = ProjectStatus.DRAFT;
    
    /**
     * 当前创作阶段
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "creation_phase", nullable = false, length = 30)
    private CreationPhase creationPhase = CreationPhase.IDEA;
    
    /**
     * 项目元数据（JSONB）
     * 
     * 可存储：
     * - genre: 类型（玄幻、都市、科幻等）
     * - tags: 标签列表
     * - targetWordCount: 目标字数
     * - currentWordCount: 当前字数
     * - settings: 用户自定义设置
     */
    @Column(columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata = new HashMap<>();
    
    /**
     * 世界观设定（JSONB）
     * 
     * 存储世界观的结构化数据：
     * - worldName: 世界名称
     * - era: 时代背景
     * - powerSystem: 力量体系
     * - geography: 地理设定
     * - history: 历史背景
     */
    @Column(name = "world_settings", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> worldSettings = new HashMap<>();
    
    // ==================== Getters & Setters ====================
    
    public UUID getUserId() {
        return userId;
    }
    
    public void setUserId(UUID userId) {
        this.userId = userId;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getCoverUrl() {
        return coverUrl;
    }
    
    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }
    
    public ProjectStatus getStatus() {
        return status;
    }
    
    public void setStatus(ProjectStatus status) {
        this.status = status;
    }
    
    public CreationPhase getCreationPhase() {
        return creationPhase;
    }
    
    public void setCreationPhase(CreationPhase creationPhase) {
        this.creationPhase = creationPhase;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    public Map<String, Object> getWorldSettings() {
        return worldSettings;
    }
    
    public void setWorldSettings(Map<String, Object> worldSettings) {
        this.worldSettings = worldSettings;
    }
    
    // ==================== 业务方法 ====================
    
    /**
     * 获取元数据中的值
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadataValue(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }
    
    /**
     * 设置元数据值
     */
    public void setMetadataValue(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }
    
    /**
     * 获取当前字数
     */
    public int getCurrentWordCount() {
        Integer count = getMetadataValue("currentWordCount", Integer.class);
        return count != null ? count : 0;
    }
    
    /**
     * 更新当前字数
     */
    public void updateWordCount(int wordCount) {
        setMetadataValue("currentWordCount", wordCount);
    }
}
