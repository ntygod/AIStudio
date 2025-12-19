package com.inkflow.module.project.entity;

/**
 * 项目状态枚举
 */
public enum ProjectStatus {
    
    /**
     * 草稿 - 创作中
     */
    DRAFT,
    
    /**
     * 已发布 - 公开可见
     */
    PUBLISHED,
    
    /**
     * 已完结 - 创作完成
     */
    COMPLETED,
    
    /**
     * 已归档 - 暂停创作
     */
    ARCHIVED
}
