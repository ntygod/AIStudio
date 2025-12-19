package com.inkflow.module.agent.routing;

import com.inkflow.module.agent.core.*;
import com.inkflow.module.agent.event.AgentThoughtEvent;
import com.inkflow.module.ai_bridge.chat.DynamicChatModelFactory;
import com.inkflow.module.project.entity.CreationPhase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 思考 Agent
 * 负责分析用户意图并决定路由目标
 * 
 * 工作流程：
 * 1. 首先使用规则引擎进行快速分类（<10ms）
 * 2. 如果规则引擎置信度 >= 0.9，直接返回结果
 * 3. 否则使用轻量级 LLM 进行分析（~500ms）
 * 
 * Requirements: 3.1-3.6
 */
@Slf4j
@Component
public class ThinkingAgent {
    
    private static final String AGENT_NAME = "ThinkingAgent";
    private static final double RULE_CONFIDENCE_THRESHOLD = 0.9;
    private static final long LLM_TIMEOUT_MS = 2000;
    
    private final RuleBasedClassifier ruleClassifier;
    private final DynamicChatModelFactory chatModelFactory;
    private final ApplicationEventPublisher eventPublisher;
    
    public ThinkingAgent(
            RuleBasedClassifier ruleClassifier,
            DynamicChatModelFactory chatModelFactory,
            ApplicationEventPublisher eventPublisher) {
        this.ruleClassifier = ruleClassifier;
        this.chatModelFactory = chatModelFactory;
        this.eventPublisher = eventPublisher;
    }
    
    /**
     * 分析用户意图
     * 
     * @param message 用户消息
     * @param phase 当前创作阶段
     * @return 意图分析结果
     */
    public IntentResult analyze(String message, CreationPhase phase) {
        publishThought("开始分析用户意图...");
        
        // 1. 规则引擎快速分类
        long ruleStartTime = System.currentTimeMillis();
        RuleBasedClassifier.ClassificationResult ruleResult = ruleClassifier.classify(message, phase);
        long ruleElapsed = System.currentTimeMillis() - ruleStartTime;
        
        log.debug("[ThinkingAgent] 规则引擎分类: intent={}, confidence={}, 耗时={}ms",
                ruleResult.intent(), ruleResult.confidence(), ruleElapsed);
        
        // 2. 如果规则引擎置信度足够高，直接返回
        if (ruleResult.isHighConfidence()) {
            publishThought(String.format("规则引擎识别意图: %s (置信度: %.0f%%)", 
                    ruleResult.intent(), ruleResult.confidence() * 100));
            return IntentResult.fromRule(ruleResult.intent(), ruleResult.confidence());
        }
        
        // 3. 需要 LLM 分析
        publishThought("规则引擎置信度不足，启用 LLM 分析...");
        
        try {
            IntentResult llmResult = analyzewithLLM(message, phase, ruleResult);
            publishThought(String.format("LLM 识别意图: %s (置信度: %.0f%%)", 
                    llmResult.intent(), llmResult.confidence() * 100));
            return llmResult;
        } catch (Exception e) {
            log.warn("[ThinkingAgent] LLM 分析失败，使用规则引擎结果: {}", e.getMessage());
            publishThought("LLM 分析失败，使用规则引擎结果");
            // 降级到规则引擎结果
            return IntentResult.fromRule(ruleResult.intent(), ruleResult.confidence());
        }
    }
    
    /**
     * 使用 LLM 分析意图
     */
    private IntentResult analyzewithLLM(String message, CreationPhase phase, 
            RuleBasedClassifier.ClassificationResult ruleHint) {
        
        ChatModel model = chatModelFactory.getDefaultModel();
        ChatClient client = ChatClient.builder(model)
                .defaultSystem(buildSystemPrompt())
                .build();
        
        String userPrompt = buildUserPrompt(message, phase, ruleHint);
        
        String response = client.prompt()
                .user(userPrompt)
                .call()
                .content();
        
        return parseResponse(response, ruleHint);
    }
    
    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt() {
        return """
            你是一个意图分析专家，负责分析用户在小说创作过程中的意图。
            
            可能的意图类型：
            - WRITE_CONTENT: 写作内容生成（续写、扩写、生成）
            - PLAN_WORLD: 世界观设计（设定、力量体系）
            - BRAINSTORM_IDEA: 灵感激发（创意、想法）
            - PLAN_CHARACTER: 角色设计（人物、主角）
            - DESIGN_RELATIONSHIP: 关系设计（人际关系）
            - MATCH_ARCHETYPE: 原型匹配（人设类型）
            - PLAN_OUTLINE: 大纲规划（章节结构）
            - MANAGE_PLOTLOOP: 伏笔管理（埋线、回收）
            - ANALYZE_PACING: 节奏分析（张力曲线）
            - CHECK_CONSISTENCY: 一致性检查（矛盾、冲突）
            - ANALYZE_STYLE: 文风分析（语言风格）
            - GENERATE_NAME: 名称生成（起名）
            - SUMMARIZE: 摘要生成（总结）
            - GENERAL_CHAT: 通用对话（闲聊、问答）
            
            请分析用户消息，返回最可能的意图和置信度（0-1）。
            
            输出格式（JSON）：
            {"intent": "INTENT_NAME", "confidence": 0.85, "alternatives": ["ALT1", "ALT2"]}
            """;
    }
    
    /**
     * 构建用户提示词
     */
    private String buildUserPrompt(String message, CreationPhase phase, 
            RuleBasedClassifier.ClassificationResult ruleHint) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("用户消息: ").append(message).append("\n");
        
        if (phase != null) {
            prompt.append("当前创作阶段: ").append(phase.name()).append("\n");
        }
        
        if (ruleHint != null) {
            prompt.append("规则引擎提示: ").append(ruleHint.intent())
                  .append(" (置信度: ").append(ruleHint.confidence()).append(")\n");
        }
        
        prompt.append("\n请分析用户意图。");
        return prompt.toString();
    }
    
    /**
     * 解析 LLM 响应
     * 
     * Requirements: 7.2, 7.3
     * - 如果无法提取 intent，返回规则引擎结果
     * - 如果无法提取 confidence，使用默认值 0.5
     */
    private IntentResult parseResponse(String response, RuleBasedClassifier.ClassificationResult ruleHint) {
        try {
            // 简单解析 JSON 响应
            String intentStr = extractJsonValue(response, "intent");
            String confidenceStr = extractJsonValue(response, "confidence");
            
            // 如果无法提取 intent，降级到规则引擎结果
            if (intentStr.isEmpty()) {
                log.warn("[ThinkingAgent] 无法从响应中提取 intent，使用规则引擎结果");
                return IntentResult.fromRule(ruleHint.intent(), ruleHint.confidence());
            }
            
            Intent intent = Intent.valueOf(intentStr);
            // 如果无法提取 confidence，使用默认值 0.5
            double confidence = confidenceStr.isEmpty() ? 0.5 : Double.parseDouble(confidenceStr);
            
            // 提取备选意图
            List<Intent> alternatives = List.of();
            String altStr = extractJsonArray(response, "alternatives");
            if (!altStr.isEmpty()) {
                alternatives = java.util.Arrays.stream(altStr.split(","))
                        .map(String::trim)
                        .map(s -> s.replace("\"", ""))
                        .filter(s -> !s.isEmpty())
                        .map(Intent::valueOf)
                        .toList();
            }
            
            return IntentResult.fromLLM(intent, confidence, alternatives);
            
        } catch (Exception e) {
            log.warn("[ThinkingAgent] 解析 LLM 响应失败: {}", e.getMessage());
            // 降级到规则引擎结果
            return IntentResult.fromRule(ruleHint.intent(), ruleHint.confidence());
        }
    }
    
    /**
     * 从 JSON 字符串提取值
     * 
     * Requirements: 7.2
     * 
     * @param json JSON 字符串
     * @param key 要提取的键
     * @return 提取的值，如果未找到则返回空字符串（不返回 null）
     */
    private String extractJsonValue(String json, String key) {
        if (json == null || json.isBlank() || key == null) {
            return "";
        }
        String pattern = "\"" + key + "\"\\s*:\\s*\"?([^,\"\\}]+)\"?";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }
    
    /**
     * 从 JSON 字符串提取数组
     * 
     * Requirements: 7.3
     * 
     * @param json JSON 字符串
     * @param key 要提取的键
     * @return 提取的数组内容，如果未找到则返回空字符串（不返回 null）
     */
    private String extractJsonArray(String json, String key) {
        if (json == null || json.isBlank() || key == null) {
            return "";
        }
        String pattern = "\"" + key + "\"\\s*:\\s*\\[([^\\]]+)\\]";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }
    
    /**
     * 发布思考过程事件
     */
    private void publishThought(String thought) {
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new AgentThoughtEvent(this, AGENT_NAME, thought));
        }
        log.debug("[{}] {}", AGENT_NAME, thought);
    }
}
