package com.inkflow.module.consistency.cdc;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 实体变更事件
 * 从 PostgreSQL LISTEN/NOTIFY 接收的变更通知
 *
 * @author zsg
 * @date 2025/12/17
 */
public record EntityChangeEvent(
        String table,
        String operation,
        UUID id,
        UUID projectId,
        LocalDateTime timestamp
) {
    
    /**
     * 判断是否为角色变更
     */
    public boolean isCharacterChange() {
        return "characters".equalsIgnoreCase(table);
    }
    
    /**
     * 判断是否为Wiki条目变更
     */
    public boolean isWikiEntryChange() {
        return "wiki_entries".equalsIgnoreCase(table);
    }
    
    /**
     * 判断是否为伏笔变更
     */
    public boolean isPlotLoopChange() {
        return "plot_loops".equalsIgnoreCase(table);
    }
    
    /**
     * 判断是否为创建操作
     */
    public boolean isInsert() {
        return "INSERT".equalsIgnoreCase(operation);
    }
    
    /**
     * 判断是否为更新操作
     */
    public boolean isUpdate() {
        return "UPDATE".equalsIgnoreCase(operation);
    }
    
    /**
     * 判断是否为删除操作
     */
    public boolean isDelete() {
        return "DELETE".equalsIgnoreCase(operation);
    }
}
