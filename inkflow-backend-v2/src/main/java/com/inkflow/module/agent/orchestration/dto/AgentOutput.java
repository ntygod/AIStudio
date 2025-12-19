package com.inkflow.module.agent.orchestration.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent 执行输出
 * 封装 Agent 执行的结果信息
 * 
 * Requirements: 3.2, 8.1
 *
 * @author zsg
 * @date 2025/12/17
 */
public record AgentOutput(
    /**
     * Agent 名称
     */
    String agentName,
    
    /**
     * 输出内容
     */
    String content,
    
    /**
     * 执行是否成功
     */
    boolean success,
    
    /**
     * 错误信息（如果失败）
     */
    String errorMessage,
    
    /**
     * 执行时间戳
     */
    LocalDateTime executedAt,
    
    /**
     * 额外元数据
     */
    Map<String, Object> metadata
) {
    
    /**
     * 创建成功输出
     */
    public static AgentOutput success(String agentName, String content) {
        return new AgentOutput(agentName, content, true, null, LocalDateTime.now(), Map.of());
    }
    
    /**
     * 创建成功输出（带元数据）
     */
    public static AgentOutput success(String agentName, String content, Map<String, Object> metadata) {
        return new AgentOutput(agentName, content, true, null, LocalDateTime.now(), metadata);
    }
    
    /**
     * 创建失败输出
     */
    public static AgentOutput failure(String agentName, String errorMessage) {
        return new AgentOutput(agentName, null, false, errorMessage, LocalDateTime.now(), Map.of());
    }
    
    /**
     * 创建失败输出（带异常）
     */
    public static AgentOutput failure(String agentName, Throwable exception) {
        return new AgentOutput(
            agentName, 
            null, 
            false, 
            exception.getMessage(), 
            LocalDateTime.now(), 
            Map.of("exceptionType", exception.getClass().getSimpleName())
        );
    }
}
