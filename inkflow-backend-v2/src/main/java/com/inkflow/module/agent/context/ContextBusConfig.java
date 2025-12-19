package com.inkflow.module.agent.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Context Bus 配置
 * 根据配置选择使用 Redis 或内存实现
 *
 * @author zsg
 * @date 2025/12/15
 */
@Slf4j
@Configuration
@EnableScheduling
public class ContextBusConfig {
    
    /**
     * Redis 实现的 Context Bus
     * 当 inkflow.agent.context-bus.type=redis 时启用
     */
    @Bean
    @ConditionalOnProperty(name = "inkflow.agent.context-bus.type", havingValue = "redis")
    public ContextBus redisContextBus(
            RedisTemplate<String, String> redisTemplate,
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {
        
        log.info("[ContextBusConfig] 使用 Redis 实现的 Context Bus");
        
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.afterPropertiesSet();
        container.start();
        
        return new RedisContextBus(redisTemplate, container, objectMapper);
    }
    
    /**
     * 内存实现的 Context Bus
     * 默认实现，当没有配置 Redis 时使用
     */
    @Bean
    @ConditionalOnMissingBean(ContextBus.class)
    public ContextBus inMemoryContextBus() {
        log.info("[ContextBusConfig] 使用内存实现的 Context Bus");
        return new InMemoryContextBus();
    }
    
    /**
     * 定时清理过期会话
     * 每小时执行一次
     */
    @Bean
    public SessionCleanupTask sessionCleanupTask(ContextBus contextBus) {
        return new SessionCleanupTask(contextBus);
    }
    
    /**
     * 会话清理任务
     */
    @Slf4j
    public static class SessionCleanupTask {
        
        private final ContextBus contextBus;
        
        public SessionCleanupTask(ContextBus contextBus) {
            this.contextBus = contextBus;
        }
        
        @Scheduled(fixedRate = 3600000) // 每小时执行
        public void cleanup() {
            if (contextBus instanceof InMemoryContextBus inMemoryBus) {
                log.info("[SessionCleanup] 开始清理过期会话...");
                inMemoryBus.cleanupExpiredSessions();
            }
            // Redis 实现依赖 TTL 自动过期，无需手动清理
        }
    }
}
