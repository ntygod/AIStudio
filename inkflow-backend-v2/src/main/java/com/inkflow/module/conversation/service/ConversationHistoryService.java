package com.inkflow.module.conversation.service;

import com.inkflow.module.ai_bridge.chat.DynamicChatModelFactory;
import com.inkflow.module.conversation.entity.ConversationHistory;
import com.inkflow.module.conversation.repository.ConversationHistoryRepository;
import com.inkflow.module.project.entity.CreationPhase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 对话历史服务
 * 提供对话历史的保存、查询、摘要功能
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationHistoryService {

    private final ConversationHistoryRepository repository;
    private final DynamicChatModelFactory modelFactory;

    @Value("${inkflow.conversation.window-size:20}")
    private int defaultWindowSize;

    @Value("${inkflow.conversation.summarize-threshold:50}")
    private int summarizeThreshold;

    /**
     * 保存消息到对话历史
     */
    @Transactional
    public ConversationHistory save(UUID userId, UUID projectId, UUID sessionId,
                                     String role, String content, CreationPhase phase) {
        Integer maxOrder = repository.findMaxMessageOrder(sessionId);
        
        ConversationHistory history = ConversationHistory.builder()
                .userId(userId)
                .projectId(projectId)
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .messageOrder(maxOrder + 1)
                .creationPhase(phase)
                .build();
        
        return repository.save(history);
    }

    /**
     * 保存消息（带工具调用信息）
     */
    @Transactional
    public ConversationHistory save(UUID userId, UUID projectId, UUID sessionId,
                                     String role, String content, CreationPhase phase,
                                     Map<String, Object> toolCalls) {
        Integer maxOrder = repository.findMaxMessageOrder(sessionId);
        
        ConversationHistory history = ConversationHistory.builder()
                .userId(userId)
                .projectId(projectId)
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .toolCalls(toolCalls)
                .messageOrder(maxOrder + 1)
                .creationPhase(phase)
                .build();
        
        return repository.save(history);
    }

    /**
     * 批量保存消息
     */
    @Transactional
    public List<ConversationHistory> saveAll(List<ConversationHistory> histories) {
        return repository.saveAll(histories);
    }

    /**
     * 按会话ID查询消息
     */
    @Transactional(readOnly = true)
    public List<ConversationHistory> findBySessionId(UUID sessionId) {
        return repository.findBySessionIdOrderByMessageOrderAsc(sessionId);
    }

    /**
     * 按会话ID查询最近N条消息
     */
    @Transactional(readOnly = true)
    public List<ConversationHistory> findRecentBySessionId(UUID sessionId, int limit) {
        List<ConversationHistory> recent = repository.findRecentBySessionId(
                sessionId, PageRequest.of(0, limit));
        // 反转顺序，使最早的消息在前
        Collections.reverse(recent);
        return recent;
    }

    /**
     * 按项目ID查询消息
     */
    @Transactional(readOnly = true)
    public List<ConversationHistory> findByProject(UUID projectId) {
        return findByProject(projectId, defaultWindowSize);
    }

    /**
     * 按项目ID查询消息（指定数量）
     */
    @Transactional(readOnly = true)
    public List<ConversationHistory> findByProject(UUID projectId, int limit) {
        return repository.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(0, limit));
    }

    /**
     * 获取用户在项目中的最近会话ID
     */
    @Transactional(readOnly = true)
    public Optional<UUID> findLatestSessionId(UUID userId, UUID projectId) {
        return repository.findLatestSessionId(userId, projectId);
    }

    /**
     * 创建新会话
     */
    public UUID createNewSession() {
        return UUID.randomUUID();
    }

    /**
     * 将对话历史转换为 Spring AI Message 列表
     */
    public List<Message> toMessages(List<ConversationHistory> histories) {
        return histories.stream()
                .map(this::toMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 单条历史转换为 Message
     */
    private Message toMessage(ConversationHistory history) {
        return switch (history.getRole().toLowerCase()) {
            case "user" -> new UserMessage(history.getContent());
            case "assistant" -> new AssistantMessage(history.getContent());
            case "system" -> new SystemMessage(history.getContent());
            default -> null; // tool 消息暂不处理
        };
    }

    /**
     * 摘要对话历史
     * 当消息数量超过阈值时，使用 AI 生成摘要
     */
    @Transactional(readOnly = true)
    public String summarize(UUID sessionId) {
        List<ConversationHistory> histories = findBySessionId(sessionId);
        
        if (histories.isEmpty()) {
            return "暂无对话历史";
        }
        
        if (histories.size() < summarizeThreshold) {
            // 消息数量较少，直接返回简单摘要
            return buildSimpleSummary(histories);
        }
        
        // 使用 AI 生成摘要
        return generateAISummary(histories);
    }

    /**
     * 构建简单摘要
     */
    private String buildSimpleSummary(List<ConversationHistory> histories) {
        StringBuilder summary = new StringBuilder();
        summary.append("对话包含 ").append(histories.size()).append(" 条消息。\n");
        
        // 统计角色分布
        Map<String, Long> roleCount = histories.stream()
                .collect(Collectors.groupingBy(ConversationHistory::getRole, Collectors.counting()));
        
        summary.append("角色分布: ");
        roleCount.forEach((role, count) -> 
            summary.append(role).append("(").append(count).append(") "));
        
        // 获取最后一条用户消息
        histories.stream()
                .filter(h -> "user".equalsIgnoreCase(h.getRole()))
                .reduce((first, second) -> second)
                .ifPresent(last -> 
                    summary.append("\n最后话题: ").append(truncate(last.getContent(), 100)));
        
        return summary.toString();
    }

    /**
     * 使用 AI 生成摘要
     */
    private String generateAISummary(List<ConversationHistory> histories) {
        try {
            ChatModel model = modelFactory.getDefaultModel();
            ChatClient client = ChatClient.builder(model).build();
            
            // 构建对话内容
            StringBuilder conversation = new StringBuilder();
            for (ConversationHistory h : histories) {
                conversation.append(h.getRole()).append(": ")
                        .append(truncate(h.getContent(), 200)).append("\n");
            }
            
            String prompt = """
                请简洁地总结以下对话的主要内容和进展，不超过200字：
                
                %s
                """.formatted(conversation.toString());
            
            return client.prompt()
                    .user(prompt)
                    .call()
                    .content();
                    
        } catch (Exception e) {
            log.error("AI 摘要生成失败: {}", e.getMessage());
            return buildSimpleSummary(histories);
        }
    }

    /**
     * 清除会话历史
     */
    @Transactional
    public void clearSession(UUID sessionId) {
        repository.deleteBySessionId(sessionId);
        log.info("会话历史已清除: {}", sessionId);
    }

    /**
     * 清除项目的所有对话历史
     */
    @Transactional
    public void clearProject(UUID projectId) {
        repository.deleteByProjectId(projectId);
        log.info("项目对话历史已清除: {}", projectId);
    }

    /**
     * 清理过期的对话历史
     */
    @Transactional
    public int cleanupOldHistory(int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int deleted = repository.deleteOlderThan(cutoff);
        log.info("清理了 {} 条过期对话历史（{}天前）", deleted, retentionDays);
        return deleted;
    }

    /**
     * 获取会话消息数量
     */
    @Transactional(readOnly = true)
    public long countBySession(UUID sessionId) {
        return repository.countBySessionId(sessionId);
    }

    /**
     * 截断字符串
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}
