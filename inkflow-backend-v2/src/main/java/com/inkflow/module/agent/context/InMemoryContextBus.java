package com.inkflow.module.agent.context;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存实现的 Context Bus
 * 用于测试和单机部署场景
 * 
 * Requirements: 16.1-16.5
 */
@Slf4j
public class InMemoryContextBus implements ContextBus {
    
    private final Map<String, SessionContext> contexts = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<ContextEvent>> eventSinks = new ConcurrentHashMap<>();
    
    @Override
    public void publish(String sessionId, ContextEvent event) {
        log.debug("[ContextBus] 发布事件: sessionId={}, eventType={}, sourceAgent={}", 
                sessionId, event.eventType(), event.sourceAgent());
        
        Sinks.Many<ContextEvent> sink = eventSinks.get(sessionId);
        if (sink != null) {
            sink.tryEmitNext(event);
        }
        
        // 确保会话存在
        contexts.computeIfAbsent(sessionId, SessionContext::empty);
    }
    
    @Override
    public SessionContext getContext(String sessionId) {
        SessionContext context = contexts.get(sessionId);
        if (context == null) {
            log.debug("[ContextBus] 会话不存在，创建空上下文: sessionId={}", sessionId);
            context = SessionContext.empty(sessionId);
            contexts.put(sessionId, context);
        } else {
            // 更新最后访问时间
            context = context.touch();
            contexts.put(sessionId, context);
        }
        return context;
    }
    
    @Override
    public Flux<ContextEvent> subscribe(String sessionId) {
        log.debug("[ContextBus] 订阅会话事件: sessionId={}", sessionId);
        
        Sinks.Many<ContextEvent> sink = eventSinks.computeIfAbsent(sessionId, 
                k -> Sinks.many().multicast().onBackpressureBuffer());
        
        return sink.asFlux();
    }
    
    @Override
    public void updateContext(String sessionId, SessionContext context) {
        log.debug("[ContextBus] 更新会话上下文: sessionId={}, phase={}", 
                sessionId, context.currentPhase());
        contexts.put(sessionId, context.touch());
    }
    
    @Override
    public void clearContext(String sessionId) {
        log.debug("[ContextBus] 清除会话上下文: sessionId={}", sessionId);
        contexts.remove(sessionId);
        
        Sinks.Many<ContextEvent> sink = eventSinks.remove(sessionId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }
    
    @Override
    public boolean hasSession(String sessionId) {
        return contexts.containsKey(sessionId);
    }
    
    /**
     * 清理过期会话
     */
    public void cleanupExpiredSessions() {
        log.info("[ContextBus] 开始清理过期会话...");
        int cleaned = 0;
        
        for (Map.Entry<String, SessionContext> entry : contexts.entrySet()) {
            if (entry.getValue().isExpired()) {
                clearContext(entry.getKey());
                cleaned++;
            }
        }
        
        log.info("[ContextBus] 清理完成，移除 {} 个过期会话", cleaned);
    }
    
    /**
     * 获取当前会话数量
     */
    public int getSessionCount() {
        return contexts.size();
    }
}
