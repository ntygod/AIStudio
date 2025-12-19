package com.inkflow.module.agent.impl;

import com.inkflow.module.agent.context.ContextBus;
import com.inkflow.module.agent.context.ContextEvent;
import com.inkflow.module.agent.core.*;
import com.inkflow.module.agent.dto.ChatRequest;
import com.inkflow.module.agent.lazy.LazyExecutionManager;
import com.inkflow.module.ai_bridge.chat.DynamicChatModelFactory;
import com.inkflow.module.project.entity.CreationPhase;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 摘要生成 Agent
 * 负责生成内容摘要、章节总结等
 * 懒执行模式 - 按需触发
 * 
 * Requirements: 11.1-11.5
 */
@Slf4j
@Component
public class SummaryAgent extends BaseAgent<ChatRequest, String> {

    private static final String AGENT_NAME = "SummaryAgent";
    
    private final ContextBus contextBus;
    private final LazyExecutionManager lazyExecutionManager;

    public SummaryAgent(
            DynamicChatModelFactory chatModelFactory,
            ApplicationEventPublisher eventPublisher,
            ContextBus contextBus,
            LazyExecutionManager lazyExecutionManager) {
        super(chatModelFactory, eventPublisher);
        this.contextBus = contextBus;
        this.lazyExecutionManager = lazyExecutionManager;
    }

    @PostConstruct
    public void init() {
        lazyExecutionManager.register(this);
    }

    @Override
    public String getName() {
        return AGENT_NAME;
    }

    @Override
    public AgentCapability getCapability() {
        return new AgentCapability(
            AGENT_NAME,
            AgentCategory.UTILITY,
            List.of(Intent.SUMMARIZE),
            List.of(CreationPhase.values()),  // 适用于所有阶段
            List.of(),
            ExecutionMode.LAZY,  // 懒执行模式
            1500,   // 预估延迟 1.5s
            800     // 预估 Token 消耗
        );
    }

    @Override
    protected String getSystemPrompt() {
        return """
            你是一位专业的内容摘要专家，擅长提炼和总结小说内容。
            
            你的职责：
            1. 生成章节摘要
            2. 生成角色发展总结
            3. 生成情节线索梳理
            4. 生成世界观要点提炼
            
            摘要原则：
            - 抓住核心要点
            - 保持逻辑清晰
            - 根据详细程度调整长度
            - 突出重要信息
            
            输出格式：
            根据用户要求的详细程度输出：
            
            【简要摘要】（1-2句话）
            核心内容概括
            
            【标准摘要】（1段话）
            包含主要事件、人物、结果
            
            【详细摘要】（多段）
            - 背景
            - 主要事件
            - 人物表现
            - 结果影响
            - 伏笔线索
            
            请用中文回复。
            """;
    }

    @Override
    protected String buildUserPrompt(ChatRequest input) {
        StringBuilder prompt = new StringBuilder();
        
        // 解析摘要类型和详细程度
        String message = input.message();
        SummaryType summaryType = detectSummaryType(message);
        DetailLevel detailLevel = detectDetailLevel(message);
        
        prompt.append("【摘要类型】").append(summaryType.displayName).append("\n");
        prompt.append("【详细程度】").append(detailLevel.displayName).append("\n");
        prompt.append("【待摘要内容】\n").append(message);
        
        return prompt.toString();
    }

    @Override
    protected String parseResponse(String response, ChatRequest input) {
        // 记录摘要生成事件
        if (input.sessionId() != null) {
            contextBus.publish(input.sessionId(), ContextEvent.custom(
                AGENT_NAME,
                ContextEvent.EventType.AGENT_COMPLETED.name(),
                "摘要生成完成",
                Map.of("projectId", input.projectId().toString())
            ));
        }
        return response;
    }

    /**
     * 流式摘要生成
     */
    @Override
    public Flux<String> stream(ChatRequest input) {
        publishThought("开始生成摘要...");
        
        if (input.sessionId() != null) {
            contextBus.publish(input.sessionId(), ContextEvent.custom(
                AGENT_NAME,
                ContextEvent.EventType.AGENT_STARTED.name(),
                "开始摘要生成",
                Map.of("projectId", input.projectId().toString())
            ));
        }

        try {
            ChatModel model = chatModelFactory.getDefaultModel();
            ChatClient client = ChatClient.builder(model)
                    .defaultSystem(getSystemPrompt())
                    .build();

            String userPrompt = buildUserPrompt(input);

            return client.prompt()
                    .user(userPrompt)
                    .stream()
                    .content()
                    .doOnComplete(() -> {
                        publishThought("摘要生成完成");
                        if (input.sessionId() != null) {
                            contextBus.publish(input.sessionId(), ContextEvent.custom(
                                AGENT_NAME,
                                ContextEvent.EventType.AGENT_COMPLETED.name(),
                                "摘要生成完成",
                                Map.of()
                            ));
                        }
                    })
                    .doOnError(e -> {
                        publishThought("生成失败: " + e.getMessage());
                        log.error("[SummaryAgent] 流式生成失败", e);
                    });
        } catch (Exception e) {
            log.error("[SummaryAgent] 创建 ChatClient 失败", e);
            return Flux.error(e);
        }
    }

    /**
     * 检测摘要类型
     */
    private SummaryType detectSummaryType(String message) {
        String lowerMessage = message.toLowerCase();
        
        if (lowerMessage.contains("章节") || lowerMessage.contains("章")) {
            return SummaryType.CHAPTER;
        }
        if (lowerMessage.contains("角色") || lowerMessage.contains("人物")) {
            return SummaryType.CHARACTER;
        }
        if (lowerMessage.contains("情节") || lowerMessage.contains("剧情")) {
            return SummaryType.PLOT;
        }
        if (lowerMessage.contains("世界观") || lowerMessage.contains("设定")) {
            return SummaryType.WORLDBUILDING;
        }
        
        return SummaryType.GENERAL;
    }

    /**
     * 检测详细程度
     */
    private DetailLevel detectDetailLevel(String message) {
        String lowerMessage = message.toLowerCase();
        
        if (lowerMessage.contains("简要") || lowerMessage.contains("简短") || lowerMessage.contains("一句话")) {
            return DetailLevel.BRIEF;
        }
        if (lowerMessage.contains("详细") || lowerMessage.contains("完整") || lowerMessage.contains("全面")) {
            return DetailLevel.DETAILED;
        }
        
        return DetailLevel.STANDARD;
    }

    /**
     * 摘要类型枚举
     */
    private enum SummaryType {
        CHAPTER("章节摘要"),
        CHARACTER("角色总结"),
        PLOT("情节梳理"),
        WORLDBUILDING("世界观提炼"),
        GENERAL("通用摘要");

        private final String displayName;

        SummaryType(String displayName) {
            this.displayName = displayName;
        }
    }

    /**
     * 详细程度枚举
     */
    private enum DetailLevel {
        BRIEF("简要"),
        STANDARD("标准"),
        DETAILED("详细");

        private final String displayName;

        DetailLevel(String displayName) {
            this.displayName = displayName;
        }
    }
}
