package com.inkflow.module.plotloop.entity;

import com.inkflow.common.entity.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Where;

import java.util.UUID;

/**
 * 伏笔实体 (聚合根)
 * 
 * 代表小说中的一个伏笔或悬念，需要在后续章节中回收
 */
@Entity
@Table(name = "plot_loops")
@Where(clause = "deleted_at IS NULL")
public class PlotLoop extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 伏笔状态: OPEN, URGENT, CLOSED, ABANDONED
     */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PlotLoopStatus status = PlotLoopStatus.OPEN;

    /**
     * 引入章节ID
     */
    @Column(name = "intro_chapter_id")
    private UUID introChapterId;

    /**
     * 引入章节顺序 (用于计算是否超过10章)
     */
    @Column(name = "intro_chapter_order")
    private Integer introChapterOrder;

    /**
     * 解决章节ID
     */
    @Column(name = "resolution_chapter_id")
    private UUID resolutionChapterId;

    /**
     * 解决章节顺序
     */
    @Column(name = "resolution_chapter_order")
    private Integer resolutionChapterOrder;

    /**
     * 放弃原因 (当status=ABANDONED时)
     */
    @Column(name = "abandon_reason", columnDefinition = "TEXT")
    private String abandonReason;

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public PlotLoopStatus getStatus() {
        return status;
    }

    public void setStatus(PlotLoopStatus status) {
        this.status = status;
    }

    public UUID getIntroChapterId() {
        return introChapterId;
    }

    public void setIntroChapterId(UUID introChapterId) {
        this.introChapterId = introChapterId;
    }

    public Integer getIntroChapterOrder() {
        return introChapterOrder;
    }

    public void setIntroChapterOrder(Integer introChapterOrder) {
        this.introChapterOrder = introChapterOrder;
    }

    public UUID getResolutionChapterId() {
        return resolutionChapterId;
    }

    public void setResolutionChapterId(UUID resolutionChapterId) {
        this.resolutionChapterId = resolutionChapterId;
    }

    public Integer getResolutionChapterOrder() {
        return resolutionChapterOrder;
    }

    public void setResolutionChapterOrder(Integer resolutionChapterOrder) {
        this.resolutionChapterOrder = resolutionChapterOrder;
    }

    public String getAbandonReason() {
        return abandonReason;
    }

    public void setAbandonReason(String abandonReason) {
        this.abandonReason = abandonReason;
    }

    // 业务方法

    /**
     * 检查是否应该标记为紧急 (超过10章未回收)
     */
    public boolean shouldBeUrgent(int currentChapterOrder) {
        if (status != PlotLoopStatus.OPEN) {
            return false;
        }
        if (introChapterOrder == null) {
            return false;
        }
        return currentChapterOrder - introChapterOrder > 10;
    }

    /**
     * 标记为紧急
     */
    public void markAsUrgent() {
        if (status == PlotLoopStatus.OPEN) {
            this.status = PlotLoopStatus.URGENT;
        }
    }

    /**
     * 解决伏笔
     */
    public void resolve(UUID chapterId, Integer chapterOrder) {
        this.status = PlotLoopStatus.CLOSED;
        this.resolutionChapterId = chapterId;
        this.resolutionChapterOrder = chapterOrder;
    }

    /**
     * 放弃伏笔
     */
    public void abandon(String reason) {
        this.status = PlotLoopStatus.ABANDONED;
        this.abandonReason = reason;
    }

    /**
     * 重新打开伏笔
     */
    public void reopen() {
        this.status = PlotLoopStatus.OPEN;
        this.resolutionChapterId = null;
        this.resolutionChapterOrder = null;
        this.abandonReason = null;
    }

    /**
     * 软删除
     */
    public void softDelete() {
        this.markDeleted();
    }
}
