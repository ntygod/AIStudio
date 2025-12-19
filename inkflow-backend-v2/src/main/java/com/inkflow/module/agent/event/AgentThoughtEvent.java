package com.inkflow.module.agent.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * Agent思考过程事件
 * 用于实时展示Agent的思考过程
 * 
 * 迁移自 ai_bridge.orchestration.event.AgentThoughtEvent
 * Requirements: 7.8
 *
 * @author zsg
 * @date 2025/12/17
 */
@Getter
public class AgentThoughtEvent extends ApplicationEvent {

    /**
     * Agent名称
     */
    private final String agentName;

    /**
     * 思考内容
     */
    private final String thought;

    /**
     * 事件时间
     */
    private final LocalDateTime eventTime;

    public AgentThoughtEvent(Object source, String agentName, String thought) {
        super(source);
        this.agentName = agentName;
        this.thought = thought;
        this.eventTime = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s", eventTime, agentName, thought);
    }
}
