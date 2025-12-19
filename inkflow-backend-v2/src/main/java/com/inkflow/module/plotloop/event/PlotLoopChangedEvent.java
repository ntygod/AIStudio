package com.inkflow.module.plotloop.event;

import com.inkflow.module.plotloop.entity.PlotLoopStatus;
import org.springframework.context.ApplicationEvent;

import java.util.Map;
import java.util.UUID;

/**
 * 伏笔变更事件
 * 
 * 当伏笔创建、更新或删除时发布此事件，
 * 用于触发一致性检查和演进快照创建
 * 
 * Requirements: 3.1, 3.2, 3.3
 */
public class PlotLoopChangedEvent extends ApplicationEvent {

    private final UUID projectId;
    private final UUID plotLoopId;
    private final String title;
    private final PlotLoopStatus status;
    private final PlotLoopStatus previousStatus;
    private final Operation operation;
    private final Map<String, Object> currentState;

    public PlotLoopChangedEvent(
            Object source,
            UUID projectId,
            UUID plotLoopId,
            String title,
            PlotLoopStatus status,
            PlotLoopStatus previousStatus,
            Operation operation,
            Map<String, Object> currentState
    ) {
        super(source);
        this.projectId = projectId;
        this.plotLoopId = plotLoopId;
        this.title = title;
        this.status = status;
        this.previousStatus = previousStatus;
        this.operation = operation;
        this.currentState = currentState;
    }

    /**
     * 操作类型枚举
     */
    public enum Operation {
        CREATE,
        UPDATE,
        STATUS_CHANGE,
        DELETE
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getPlotLoopId() {
        return plotLoopId;
    }

    public String getTitle() {
        return title;
    }

    public PlotLoopStatus getStatus() {
        return status;
    }

    public PlotLoopStatus getPreviousStatus() {
        return previousStatus;
    }

    public Operation getOperation() {
        return operation;
    }

    public Map<String, Object> getCurrentState() {
        return currentState;
    }

    /**
     * 检查是否为状态变更
     */
    public boolean isStatusChange() {
        return operation == Operation.STATUS_CHANGE || 
               (previousStatus != null && previousStatus != status);
    }

    /**
     * 检查是否为解决操作
     */
    public boolean isResolved() {
        return status == PlotLoopStatus.CLOSED;
    }

    /**
     * 便捷工厂方法 - 创建事件
     */
    public static PlotLoopChangedEvent created(Object source, UUID projectId, UUID plotLoopId,
                                                String title, Map<String, Object> state) {
        return new PlotLoopChangedEvent(source, projectId, plotLoopId, title,
                PlotLoopStatus.OPEN, null, Operation.CREATE, state);
    }

    /**
     * 便捷工厂方法 - 更新事件
     */
    public static PlotLoopChangedEvent updated(Object source, UUID projectId, UUID plotLoopId,
                                                String title, PlotLoopStatus status, 
                                                Map<String, Object> state) {
        return new PlotLoopChangedEvent(source, projectId, plotLoopId, title,
                status, null, Operation.UPDATE, state);
    }

    /**
     * 便捷工厂方法 - 状态变更事件
     */
    public static PlotLoopChangedEvent statusChanged(Object source, UUID projectId, UUID plotLoopId,
                                                      String title, PlotLoopStatus newStatus,
                                                      PlotLoopStatus previousStatus,
                                                      Map<String, Object> state) {
        return new PlotLoopChangedEvent(source, projectId, plotLoopId, title,
                newStatus, previousStatus, Operation.STATUS_CHANGE, state);
    }

    /**
     * 便捷工厂方法 - 删除事件
     */
    public static PlotLoopChangedEvent deleted(Object source, UUID projectId, UUID plotLoopId,
                                                String title) {
        return new PlotLoopChangedEvent(source, projectId, plotLoopId, title,
                null, null, Operation.DELETE, null);
    }
}
