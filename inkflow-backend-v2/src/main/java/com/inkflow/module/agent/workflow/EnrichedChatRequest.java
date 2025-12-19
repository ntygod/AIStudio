package com.inkflow.module.agent.workflow;

import com.inkflow.module.agent.dto.ChatRequest;
import com.inkflow.module.agent.skill.SkillSlot;

import java.util.List;
import java.util.UUID;

/**
 * 增强的聊天请求
 * 包含预处理上下文和激活的技能
 * 
 * 设计说明：
 * - ChatRequest 是 record，不可变
 * - EnrichedChatRequest 包装 ChatRequest 并添加上下文
 * - 用于 Workflow 内部传递增强信息
 * 
 * @see Requirements 11.1, 11.5
 */
public record EnrichedChatRequest(
    ChatRequest original,
    PreprocessingContext context,
    List<SkillSlot> activeSkills,
    String enhancedSystemPrompt
) {
    
    /**
     * 从原始请求创建（空上下文）
     */
    public static EnrichedChatRequest from(ChatRequest request) {
        return new EnrichedChatRequest(request, PreprocessingContext.empty(), List.of(), null);
    }
    
    /**
     * 添加预处理上下文
     */
    public EnrichedChatRequest withContext(PreprocessingContext context) {
        return new EnrichedChatRequest(original, context, activeSkills, enhancedSystemPrompt);
    }
    
    /**
     * 添加技能和增强的系统提示词
     */
    public EnrichedChatRequest withSkills(List<SkillSlot> skills, String systemPrompt) {
        return new EnrichedChatRequest(original, context, skills, systemPrompt);
    }
    
    /**
     * 便捷方法：获取项目 ID
     */
    public UUID projectId() {
        return original.projectId();
    }
    
    /**
     * 便捷方法：获取用户消息
     */
    public String message() {
        return original.message();
    }
    
    /**
     * 便捷方法：获取会话 ID
     */
    public String sessionId() {
        return original.sessionId();
    }
    
    /**
     * 检查是否有预处理上下文
     */
    public boolean hasContext() {
        return context != null && !context.isEmpty();
    }
    
    /**
     * 检查是否有激活的技能
     */
    public boolean hasActiveSkills() {
        return activeSkills != null && !activeSkills.isEmpty();
    }
    
    /**
     * 检查是否有增强的系统提示词
     */
    public boolean hasEnhancedPrompt() {
        return enhancedSystemPrompt != null && !enhancedSystemPrompt.isBlank();
    }
}
