package com.inkflow.module.ai_bridge.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChatMemory工厂
 * 支持内存和持久化两种对话记忆实现
 * 
 * Requirements: 7.3
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMemoryFactory {

    private final JdbcTemplate jdbcTemplate;

    @Value("${inkflow.chat.memory.max-messages:20}")
    private int maxMessages;

    @Value("${inkflow.chat.memory.type:persistent}")
    private String memoryType;

    /**
     * 缓存已创建的ChatMemory实例
     */
    private final Map<String, ChatMemory> memoryCache = new ConcurrentHashMap<>();

    /**
     * 记忆类型
     */
    public enum MemoryType {
        IN_MEMORY,
        PERSISTENT
    }

    /**
     * 获取或创建ChatMemory
     * 
     * @param conversationId 对话ID
     * @return ChatMemory实例
     */
    public ChatMemory getOrCreate(String conversationId) {
        return memoryCache.computeIfAbsent(conversationId, id -> createMemory());
    }

    /**
     * 获取指定类型的ChatMemory
     */
    public ChatMemory getOrCreate(String conversationId, MemoryType type) {
        String cacheKey = conversationId + ":" + type.name();
        return memoryCache.computeIfAbsent(cacheKey, id -> createMemory(type));
    }

    /**
     * 创建默认类型的ChatMemory
     */
    public ChatMemory createMemory() {
        MemoryType type = "persistent".equalsIgnoreCase(memoryType) 
                ? MemoryType.PERSISTENT 
                : MemoryType.IN_MEMORY;
        return createMemory(type);
    }

    /**
     * 创建指定类型的ChatMemory
     */
    public ChatMemory createMemory(MemoryType type) {
        return switch (type) {
            case IN_MEMORY -> createInMemoryMemory();
            case PERSISTENT -> createPersistentMemory();
        };
    }

    /**
     * 创建内存ChatMemory
     */
    private ChatMemory createInMemoryMemory() {
        log.debug("创建MessageWindowChatMemory, maxMessages={}", maxMessages);
        return MessageWindowChatMemory.builder()
                .maxMessages(maxMessages)
                .build();
    }

    /**
     * 创建持久化ChatMemory
     */
    private ChatMemory createPersistentMemory() {
        log.debug("创建PersistentChatMemory, maxMessages={}", maxMessages);
        return new PersistentChatMemory(jdbcTemplate, maxMessages);
    }

    /**
     * 清除指定对话的记忆
     */
    public void clearMemory(String conversationId) {
        ChatMemory memory = memoryCache.get(conversationId);
        if (memory != null) {
            memory.clear(conversationId);
            memoryCache.remove(conversationId);
        }
    }

    /**
     * 清除所有缓存的记忆
     */
    public void clearAllCache() {
        memoryCache.clear();
        log.info("ChatMemory缓存已清除");
    }

    /**
     * 获取缓存的记忆数量
     */
    public int getCachedMemoryCount() {
        return memoryCache.size();
    }

    /**
     * 获取配置的最大消息数
     */
    public int getMaxMessages() {
        return maxMessages;
    }
}
