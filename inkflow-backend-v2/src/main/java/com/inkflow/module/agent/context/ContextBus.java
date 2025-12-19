package com.inkflow.module.agent.context;

import reactor.core.publisher.Flux;

/**
 * Context Bus 接口
 * 用于 Agent 间的上下文共享和事件传递
 *
 * @author zsg
 * @date 2025/12/15
 */
public interface ContextBus {
    
    /**
     * 发布上下文事件
     * 
     * @param sessionId 会话 ID
     * @param event 上下文事件
     */
    void publish(String sessionId, ContextEvent event);
    
    /**
     * 获取会话上下文
     * 
     * @param sessionId 会话 ID
     * @return 会话上下文，如果不存在则返回空上下文
     */
    SessionContext getContext(String sessionId);
    
    /**
     * 订阅会话事件
     * 
     * @param sessionId 会话 ID
     * @return 事件流
     */
    Flux<ContextEvent> subscribe(String sessionId);
    
    /**
     * 更新会话上下文
     * 
     * @param sessionId 会话 ID
     * @param context 新的会话上下文
     */
    void updateContext(String sessionId, SessionContext context);
    
    /**
     * 清除会话上下文
     * 
     * @param sessionId 会话 ID
     */
    void clearContext(String sessionId);
    
    /**
     * 检查会话是否存在
     * 
     * @param sessionId 会话 ID
     * @return 是否存在
     */
    boolean hasSession(String sessionId);
}
