package com.inkflow.module.agent.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis 实现的 Context Bus
 * 支持分布式部署和跨实例事件传递
 * 
 * Requirements: 16.1-16.5
 */
@Slf4j
public class RedisContextBus implements ContextBus {
    
    private static final String CONTEXT_KEY_PREFIX = "agent:context:";
    private static final String EVENT_CHANNEL_PREFIX = "agent:events:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;
    private final ObjectMapper objectMapper;
    private final Map<String, Sinks.Many<ContextEvent>> localSinks = new ConcurrentHashMap<>();
    
    public RedisContextBus(
            RedisTemplate<String, String> redisTemplate,
            RedisMessageListenerContainer listenerContainer,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.listenerContainer = listenerContainer;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public void publish(String sessionId, ContextEvent event) {
        log.debug("[RedisContextBus] 发布事件: sessionId={}, eventType={}, sourceAgent={}", 
                sessionId, event.eventType(), event.sourceAgent());
        
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            String channel = EVENT_CHANNEL_PREFIX + sessionId;
            
            // 发布到 Redis 频道
            redisTemplate.convertAndSend(channel, eventJson);
            
            // 同时发送到本地订阅者
            Sinks.Many<ContextEvent> localSink = localSinks.get(sessionId);
            if (localSink != null) {
                localSink.tryEmitNext(event);
            }
            
        } catch (JsonProcessingException e) {
            log.error("[RedisContextBus] 序列化事件失败: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public SessionContext getContext(String sessionId) {
        String key = CONTEXT_KEY_PREFIX + sessionId;
        String contextJson = redisTemplate.opsForValue().get(key);
        
        if (contextJson == null) {
            log.debug("[RedisContextBus] 会话不存在，创建空上下文: sessionId={}", sessionId);
            SessionContext context = SessionContext.empty(sessionId);
            saveContext(sessionId, context);
            return context;
        }
        
        try {
            SessionContext context = objectMapper.readValue(contextJson, SessionContext.class);
            // 更新最后访问时间
            context = context.touch();
            saveContext(sessionId, context);
            return context;
        } catch (JsonProcessingException e) {
            log.error("[RedisContextBus] 反序列化上下文失败: {}", e.getMessage(), e);
            return SessionContext.empty(sessionId);
        }
    }
    
    @Override
    public Flux<ContextEvent> subscribe(String sessionId) {
        log.debug("[RedisContextBus] 订阅会话事件: sessionId={}", sessionId);
        
        // 创建本地 Sink
        Sinks.Many<ContextEvent> sink = localSinks.computeIfAbsent(sessionId,
                k -> Sinks.many().multicast().onBackpressureBuffer());
        
        // 订阅 Redis 频道
        String channel = EVENT_CHANNEL_PREFIX + sessionId;
        listenerContainer.addMessageListener((message, pattern) -> {
            try {
                String eventJson = new String(message.getBody());
                ContextEvent event = objectMapper.readValue(eventJson, ContextEvent.class);
                sink.tryEmitNext(event);
            } catch (Exception e) {
                log.error("[RedisContextBus] 处理 Redis 消息失败: {}", e.getMessage(), e);
            }
        }, new ChannelTopic(channel));
        
        return sink.asFlux();
    }
    
    @Override
    public void updateContext(String sessionId, SessionContext context) {
        log.debug("[RedisContextBus] 更新会话上下文: sessionId={}, phase={}", 
                sessionId, context.currentPhase());
        saveContext(sessionId, context.touch());
    }
    
    @Override
    public void clearContext(String sessionId) {
        log.debug("[RedisContextBus] 清除会话上下文: sessionId={}", sessionId);
        
        String key = CONTEXT_KEY_PREFIX + sessionId;
        redisTemplate.delete(key);
        
        // 清理本地 Sink
        Sinks.Many<ContextEvent> sink = localSinks.remove(sessionId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }
    
    @Override
    public boolean hasSession(String sessionId) {
        String key = CONTEXT_KEY_PREFIX + sessionId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
    
    /**
     * 保存上下文到 Redis
     */
    private void saveContext(String sessionId, SessionContext context) {
        try {
            String key = CONTEXT_KEY_PREFIX + sessionId;
            String contextJson = objectMapper.writeValueAsString(context);
            redisTemplate.opsForValue().set(key, contextJson, DEFAULT_TTL);
        } catch (JsonProcessingException e) {
            log.error("[RedisContextBus] 序列化上下文失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 获取当前会话数量（近似值）
     */
    public long getSessionCount() {
        var keys = redisTemplate.keys(CONTEXT_KEY_PREFIX + "*");
        return keys != null ? keys.size() : 0;
    }
}
