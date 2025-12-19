package com.inkflow.module.ai_bridge.event;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Tool 执行事件
 * 用于在 Tool 执行开始和结束时发布事件，供前端 SSE 流消费
 * 
 * Requirements: 6.1, 6.2
 *
 * @author zsg
 * @date 2025/12/17
 */
public class ToolExecutionEvent extends ApplicationEvent {

    /**
     * 执行阶段枚举
     */
    public enum Phase {
        /** Tool 开始执行 */
        START,
        /** Tool 执行结束 */
        END
    }

    private final String requestId;
    private final String toolName;
    private final Phase phase;
    private final boolean success;
    private final Instant eventTimestamp;
    private final Map<String, Object> parameters;
    private final String resultSummary;
    private final String errorMessage;
    private final Long durationMs;
    private final UUID userId;
    private final UUID projectId;

    /**
     * 创建 START 事件
     */
    public static ToolExecutionEvent start(
            Object source,
            String requestId,
            String toolName,
            Map<String, Object> parameters,
            UUID userId,
            UUID projectId) {
        return new ToolExecutionEvent(
            source, requestId, toolName, Phase.START, true,
            parameters, null, null, null, userId, projectId
        );
    }

    /**
     * 创建成功的 END 事件
     */
    public static ToolExecutionEvent success(
            Object source,
            String requestId,
            String toolName,
            String resultSummary,
            Long durationMs,
            UUID userId,
            UUID projectId) {
        return new ToolExecutionEvent(
            source, requestId, toolName, Phase.END, true,
            null, resultSummary, null, durationMs, userId, projectId
        );
    }

    /**
     * 创建失败的 END 事件
     */
    public static ToolExecutionEvent failure(
            Object source,
            String requestId,
            String toolName,
            String errorMessage,
            Long durationMs,
            UUID userId,
            UUID projectId) {
        return new ToolExecutionEvent(
            source, requestId, toolName, Phase.END, false,
            null, null, errorMessage, durationMs, userId, projectId
        );
    }

    private ToolExecutionEvent(
            Object source,
            String requestId,
            String toolName,
            Phase phase,
            boolean success,
            Map<String, Object> parameters,
            String resultSummary,
            String errorMessage,
            Long durationMs,
            UUID userId,
            UUID projectId) {
        super(source);
        this.requestId = requestId;
        this.toolName = toolName;
        this.phase = phase;
        this.success = success;
        this.eventTimestamp = Instant.now();
        this.parameters = parameters;
        this.resultSummary = resultSummary;
        this.errorMessage = errorMessage;
        this.durationMs = durationMs;
        this.userId = userId;
        this.projectId = projectId;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getToolName() {
        return toolName;
    }

    public Phase getPhase() {
        return phase;
    }

    public boolean isSuccess() {
        return success;
    }

    public Instant getEventTimestamp() {
        return eventTimestamp;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    /**
     * 获取 SSE 事件类型名称
     */
    public String getEventType() {
        return switch (phase) {
            case START -> "tool_start";
            case END -> "tool_end";
        };
    }

    @Override
    public String toString() {
        return "ToolExecutionEvent{" +
            "requestId='" + requestId + '\'' +
            ", toolName='" + toolName + '\'' +
            ", phase=" + phase +
            ", success=" + success +
            ", eventTimestamp=" + eventTimestamp +
            ", durationMs=" + durationMs +
            '}';
    }
}
