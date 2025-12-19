package com.inkflow.module.agent.event;

import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Tool 状态事件
 * 用于追踪 Tool 调用的开始和结束
 * 
 * Requirements: 15.2
 */
public class ToolStatusEvent extends ApplicationEvent {
    
    private final String toolName;
    private final ToolStatus status;
    private final String agentName;
    private final Map<String, Object> parameters;
    private final Object result;
    private final String errorMessage;
    private final long durationMs;
    private final LocalDateTime eventTime;
    
    /**
     * 创建 Tool 开始事件
     */
    public static ToolStatusEvent start(Object source, String toolName, String agentName, Map<String, Object> parameters) {
        return new ToolStatusEvent(source, toolName, ToolStatus.STARTED, agentName, parameters, null, null, 0);
    }
    
    /**
     * 创建 Tool 完成事件
     */
    public static ToolStatusEvent complete(Object source, String toolName, String agentName, Object result, long durationMs) {
        return new ToolStatusEvent(source, toolName, ToolStatus.COMPLETED, agentName, null, result, null, durationMs);
    }
    
    /**
     * 创建 Tool 失败事件
     */
    public static ToolStatusEvent failed(Object source, String toolName, String agentName, String errorMessage, long durationMs) {
        return new ToolStatusEvent(source, toolName, ToolStatus.FAILED, agentName, null, null, errorMessage, durationMs);
    }
    
    private ToolStatusEvent(Object source, String toolName, ToolStatus status, String agentName,
                           Map<String, Object> parameters, Object result, String errorMessage, long durationMs) {
        super(source);
        this.toolName = toolName;
        this.status = status;
        this.agentName = agentName;
        this.parameters = parameters;
        this.result = result;
        this.errorMessage = errorMessage;
        this.durationMs = durationMs;
        this.eventTime = LocalDateTime.now();
    }
    
    public String getToolName() {
        return toolName;
    }
    
    public ToolStatus getStatus() {
        return status;
    }
    
    public String getAgentName() {
        return agentName;
    }
    
    public Map<String, Object> getParameters() {
        return parameters;
    }
    
    public Object getResult() {
        return result;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public long getDurationMs() {
        return durationMs;
    }
    
    /**
     * 获取事件发生时间
     */
    public LocalDateTime getEventTime() {
        return eventTime;
    }
    
    /**
     * Tool 状态
     */
    public enum ToolStatus {
        STARTED,
        COMPLETED,
        FAILED
    }
}
