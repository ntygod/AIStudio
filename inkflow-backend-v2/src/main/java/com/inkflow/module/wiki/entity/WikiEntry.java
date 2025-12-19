package com.inkflow.module.wiki.entity;

import com.inkflow.common.entity.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Where;

import java.util.UUID;

/**
 * 知识条目实体 (聚合根)
 * 
 * 代表小说世界观中的一个设定条目，如角色、地点、物品、事件等
 */
@Entity
@Table(name = "wiki_entries")
@Where(clause = "deleted_at IS NULL")
public class WikiEntry extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String title;

    /**
     * 条目类型: character, location, item, event, concept, power_system, organization
     */
    @Column(nullable = false)
    private String type;

    @Column(columnDefinition = "TEXT")
    private String content;

    /**
     * 别名数组 (支持多名称搜索)
     */
    @Column(columnDefinition = "TEXT[]")
    private String[] aliases;

    /**
     * 标签数组
     */
    @Column(columnDefinition = "TEXT[]")
    private String[] tags;

    /**
     * 时间版本 (用于区分不同故事时间点的设定)
     * 如: "第一卷", "修炼前", "觉醒后"
     */
    @Column(name = "time_version")
    private String timeVersion;

    // Getters and Setters
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String[] getAliases() {
        return aliases;
    }

    public void setAliases(String[] aliases) {
        this.aliases = aliases;
    }

    public String[] getTags() {
        return tags;
    }

    public void setTags(String[] tags) {
        this.tags = tags;
    }

    public String getTimeVersion() {
        return timeVersion;
    }

    public void setTimeVersion(String timeVersion) {
        this.timeVersion = timeVersion;
    }

    // 业务方法

    /**
     * 添加别名
     */
    public void addAlias(String alias) {
        if (this.aliases == null) {
            this.aliases = new String[]{alias};
        } else {
            String[] newAliases = new String[this.aliases.length + 1];
            System.arraycopy(this.aliases, 0, newAliases, 0, this.aliases.length);
            newAliases[this.aliases.length] = alias;
            this.aliases = newAliases;
        }
    }

    /**
     * 添加标签
     */
    public void addTag(String tag) {
        if (this.tags == null) {
            this.tags = new String[]{tag};
        } else {
            String[] newTags = new String[this.tags.length + 1];
            System.arraycopy(this.tags, 0, newTags, 0, this.tags.length);
            newTags[this.tags.length] = tag;
            this.tags = newTags;
        }
    }

    /**
     * 软删除
     */
    public void softDelete() {
        this.markDeleted();
    }
}
