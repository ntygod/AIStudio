package com.inkflow.module.wiki.event;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * 知识条目变更事件
 * 
 * 当知识条目创建、更新或删除时发布此事件，
 * 用于触发embedding生成和一致性检查
 */
public class WikiEntryChangedEvent extends ApplicationEvent {

    private final UUID entryId;
    private final UUID projectId;
    private final String changeType; // CREATED, UPDATED, DELETED

    public WikiEntryChangedEvent(Object source, UUID entryId, UUID projectId, String changeType) {
        super(source);
        this.entryId = entryId;
        this.projectId = projectId;
        this.changeType = changeType;
    }

    public UUID getEntryId() {
        return entryId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getChangeType() {
        return changeType;
    }
}
