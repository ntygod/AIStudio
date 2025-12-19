package com.inkflow.module.content.entity;

import com.inkflow.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

/**
 * 分卷实体
 * 
 * 代表小说的一个分卷/卷册，用于组织章节
 */
@Entity
@Table(name = "volumes", indexes = {
    @Index(name = "idx_volumes_project_id", columnList = "project_id"),
    @Index(name = "idx_volumes_order", columnList = "project_id, order_index")
})
public class Volume extends BaseEntity {
    
    /**
     * 所属项目ID
     */
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    
    /**
     * 分卷标题
     */
    @Column(nullable = false, length = 200)
    private String title;
    
    /**
     * 分卷简介
     */
    @Column(columnDefinition = "TEXT")
    private String description;
    
    /**
     * 排序索引
     */
    @Column(name = "order_index", nullable = false)
    private int orderIndex;
    
    // ==================== Getters & Setters ====================
    
    public UUID getProjectId() {
        return projectId;
    }
    
    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
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
    
    public int getOrderIndex() {
        return orderIndex;
    }
    
    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }
}
