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
 * 名称生成 Agent
 * 负责生成角色名、地名、物品名等
 * 懒执行模式 - 按需触发
 * 
 * Requirements: 10.1-10.5
 */
@Slf4j
@Component
public class NameGeneratorAgent extends BaseAgent<ChatRequest, String> {

    private static final String AGENT_NAME = "NameGeneratorAgent";
    
    private final ContextBus contextBus;
    private final LazyExecutionManager lazyExecutionManager;

    public NameGeneratorAgent(
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
            List.of(Intent.GENERATE_NAME),
            List.of(CreationPhase.values()),  // 适用于所有阶段
            List.of(),
            ExecutionMode.LAZY,  // 懒执行模式
            1000,   // 预估延迟 1s
            500     // 预估 Token 消耗
        );
    }

    @Override
    protected String getSystemPrompt() {
        return """
            你是一位专业的名称设计师，擅长为小说创作各种名称。
            
            你的职责：
            1. 生成符合世界观的角色名称
            2. 生成有意境的地名
            3. 生成有特色的物品名、技能名、组织名等
            
            命名原则：
            - 符合世界观设定（玄幻、仙侠、都市、西幻等）
            - 朗朗上口，易于记忆
            - 有一定的含义或寓意
            - 避免与知名作品重复
            
            输出格式：
            请以列表形式输出多个候选名称，每个名称附带简短的含义说明：
            
            1. 名称1 - 含义说明
            2. 名称2 - 含义说明
            3. 名称3 - 含义说明
            ...
            
            请用中文回复，提供至少5个候选名称。
            """;
    }

    @Override
    protected String buildUserPrompt(ChatRequest input) {
        StringBuilder prompt = new StringBuilder();
        
        // 解析命名类型
        String message = input.message();
        NameType nameType = detectNameType(message);
        
        prompt.append("【命名类型】").append(nameType.displayName).append("\n");
        prompt.append("【命名要求】\n").append(message);
        
        return prompt.toString();
    }

    @Override
    protected String parseResponse(String response, ChatRequest input) {
        // 记录名称生成事件
        if (input.sessionId() != null) {
            contextBus.publish(input.sessionId(), ContextEvent.custom(
                AGENT_NAME,
                ContextEvent.EventType.AGENT_COMPLETED.name(),
                "名称生成完成",
                Map.of("projectId", input.projectId().toString())
            ));
        }
        return response;
    }

    /**
     * 流式名称生成
     */
    @Override
    public Flux<String> stream(ChatRequest input) {
        publishThought("开始生成名称...");
        
        if (input.sessionId() != null) {
            contextBus.publish(input.sessionId(), ContextEvent.custom(
                AGENT_NAME,
                ContextEvent.EventType.AGENT_STARTED.name(),
                "开始名称生成",
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
                        publishThought("名称生成完成");
                        if (input.sessionId() != null) {
                            contextBus.publish(input.sessionId(), ContextEvent.custom(
                                AGENT_NAME,
                                ContextEvent.EventType.AGENT_COMPLETED.name(),
                                "名称生成完成",
                                Map.of()
                            ));
                        }
                    })
                    .doOnError(e -> {
                        publishThought("生成失败: " + e.getMessage());
                        log.error("[NameGeneratorAgent] 流式生成失败", e);
                    });
        } catch (Exception e) {
            log.error("[NameGeneratorAgent] 创建 ChatClient 失败", e);
            return Flux.error(e);
        }
    }

    /**
     * 检测命名类型
     */
    private NameType detectNameType(String message) {
        String lowerMessage = message.toLowerCase();
        
        if (lowerMessage.contains("角色") || lowerMessage.contains("人物") || lowerMessage.contains("人名")) {
            return NameType.CHARACTER;
        }
        if (lowerMessage.contains("地名") || lowerMessage.contains("地点") || lowerMessage.contains("城市")) {
            return NameType.LOCATION;
        }
        if (lowerMessage.contains("物品") || lowerMessage.contains("道具") || lowerMessage.contains("武器")) {
            return NameType.ITEM;
        }
        if (lowerMessage.contains("技能") || lowerMessage.contains("招式") || lowerMessage.contains("法术")) {
            return NameType.SKILL;
        }
        if (lowerMessage.contains("组织") || lowerMessage.contains("门派") || lowerMessage.contains("势力")) {
            return NameType.ORGANIZATION;
        }
        
        return NameType.GENERAL;
    }

    /**
     * 命名类型枚举
     */
    private enum NameType {
        CHARACTER("角色名"),
        LOCATION("地名"),
        ITEM("物品名"),
        SKILL("技能名"),
        ORGANIZATION("组织名"),
        GENERAL("通用名称");

        private final String displayName;

        NameType(String displayName) {
            this.displayName = displayName;
        }
    }
}
