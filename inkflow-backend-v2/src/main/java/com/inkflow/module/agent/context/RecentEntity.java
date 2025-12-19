package com.inkflow.module.agent.context;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 最近访问的实体
 * 用于追踪 Agent 最近处理的实体，便于上下文关联
 * 
 * Requirements: 16.1-16.5
 */
public record RecentEntity(
    UUID entityId,
    String entityType,
    String entityName,
    String summary,
    LocalDateTime accessedAt
) {
    
    /**
     * 创建角色实体引用
     */
    public static RecentEntity character(UUID id, String name, String summary) {
        return new RecentEntity(id, "CHARACTER", name, summary, LocalDateTime.now());
    }
    
    /**
     * 创建设定实体引用
     */
    public static RecentEntity wiki(UUID id, String title, String summary) {
        return new RecentEntity(id, "WIKI", title, summary, LocalDateTime.now());
    }
    
    /**
     * 创建章节实体引用
     */
    public static RecentEntity chapter(UUID id, String title, String summary) {
        return new RecentEntity(id, "CHAPTER", title, summary, LocalDateTime.now());
    }
    
    /**
     * 创建伏笔实体引用
     */
    public static RecentEntity plotLoop(UUID id, String title, String summary) {
        return new RecentEntity(id, "PLOTLOOP", title, summary, LocalDateTime.now());
    }
}
