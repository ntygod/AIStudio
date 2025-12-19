package com.inkflow.module.ai_bridge.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 持久化对话记忆
 * 将对话历史存储到数据库
 * 
 * Requirements: 7.3
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@RequiredArgsConstructor
public class PersistentChatMemory implements ChatMemory {

    private final JdbcTemplate jdbcTemplate;
    private final int maxMessages;

    /**
     * 添加消息到对话历史
     */
    @Override
    public void add(String conversationId, List<Message> messages) {
        for (Message message : messages) {
            String role = getMessageRole(message);
            String content = message.getText();

            String sql = """
                INSERT INTO conversation_history (id, conversation_id, role, content, created_at)
                VALUES (?, ?, ?, ?, ?)
                """;

            jdbcTemplate.update(sql,
                    UUID.randomUUID(),
                    UUID.fromString(conversationId),
                    role,
                    content,
                    Timestamp.valueOf(LocalDateTime.now()));
        }

        // 清理超出限制的旧消息
        trimMessages(conversationId);
    }

    /**
     * 获取对话历史 (Spring AI 1.1.2 接口)
     */
    @Override
    public List<Message> get(String conversationId) {
        String sql = """
            SELECT role, content FROM conversation_history
            WHERE conversation_id = ?
            ORDER BY created_at DESC
            LIMIT ?
            """;

        List<Message> messages = new ArrayList<>();

        jdbcTemplate.query(sql, rs -> {
            String role = rs.getString("role");
            String content = rs.getString("content");
            messages.add(createMessage(role, content));
        }, UUID.fromString(conversationId), maxMessages);

        // 反转顺序，使最早的消息在前
        java.util.Collections.reverse(messages);
        return messages;
    }

    /**
     * 清除对话历史
     */
    @Override
    public void clear(String conversationId) {
        String sql = "DELETE FROM conversation_history WHERE conversation_id = ?";
        int deleted = jdbcTemplate.update(sql, UUID.fromString(conversationId));
        log.debug("清除对话历史: conversationId={}, deleted={}", conversationId, deleted);
    }

    /**
     * 获取消息角色
     */
    private String getMessageRole(Message message) {
        if (message instanceof UserMessage) {
            return "user";
        } else if (message instanceof AssistantMessage) {
            return "assistant";
        } else if (message instanceof SystemMessage) {
            return "system";
        }
        return "user";
    }

    /**
     * 根据角色创建消息
     */
    private Message createMessage(String role, String content) {
        return switch (role.toLowerCase()) {
            case "assistant" -> new AssistantMessage(content);
            case "system" -> new SystemMessage(content);
            default -> new UserMessage(content);
        };
    }

    /**
     * 清理超出限制的旧消息
     */
    private void trimMessages(String conversationId) {
        String countSql = "SELECT COUNT(*) FROM conversation_history WHERE conversation_id = ?";
        Integer count = jdbcTemplate.queryForObject(countSql, Integer.class, UUID.fromString(conversationId));

        if (count != null && count > maxMessages) {
            int toDelete = count - maxMessages;
            String deleteSql = """
                DELETE FROM conversation_history
                WHERE id IN (
                    SELECT id FROM conversation_history
                    WHERE conversation_id = ?
                    ORDER BY created_at ASC
                    LIMIT ?
                )
                """;
            jdbcTemplate.update(deleteSql, UUID.fromString(conversationId), toDelete);
            log.debug("清理旧消息: conversationId={}, deleted={}", conversationId, toDelete);
        }
    }
}
