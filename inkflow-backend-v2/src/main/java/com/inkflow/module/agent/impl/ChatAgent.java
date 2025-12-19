package com.inkflow.module.agent.impl;

import com.inkflow.module.agent.context.ContextBus;
import com.inkflow.module.agent.context.ContextEvent;
import com.inkflow.module.agent.context.SessionContext;
import com.inkflow.module.agent.core.*;
import com.inkflow.module.agent.dto.ChatRequest;
import com.inkflow.module.ai_bridge.chat.DynamicChatModelFactory;
import com.inkflow.module.project.entity.CreationPhase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 通用对话 Agent
 * 处理一般性对话和问答，检测专业意图并推荐专业 Agent
 * 
 * Requirements: 9.1-9.5
 */
@Slf4j
@Component
public class ChatAgent extends BaseAgent<ChatRequest, String> {

    private static final String AGENT_NAME = "ChatAgent";
    
    private final ContextBus contextBus;

    public ChatAgent(
            DynamicChatModelFactory chatModelFactory,
            ApplicationEventPublisher eventPublisher,
            ContextBus contextBus) {
        super(chatModelFactory, eventPublisher);
        this.contextBus = contextBus;
    }

    @Override
    public String getName() {
        return AGENT_NAME;
    }

    @Override
    public AgentCapability getCapability() {
        return new AgentCapability(
            AGENT_NAME,
            AgentCategory.ROUTING,
            List.of(Intent.GENERAL_CHAT),
            List.of(CreationPhase.values()),  // 适用于所有阶段
            List.of(),  // 无需特殊工具
            ExecutionMode.EAGER,
            500,   // 预估延迟 500ms
            200    // 预估 Token 消耗
        );
    }

    @Override
    protected String getSystemPrompt() {
        return """
            你是 InkFlow 小说创作助手，专门帮助用户进行小说创作。
            
            你的职责：
            1. 回答用户关于小说创作的一般性问题
            2. 提供写作建议和灵感
            3. 当检测到用户有专业需求时，推荐使用专业功能
            
            专业功能推荐规则：
            - 如果用户想要生成内容，推荐使用 /write 命令
            - 如果用户想要规划大纲，推荐使用 /plan 命令
            - 如果用户想要设计角色，推荐使用 /character 命令
            - 如果用户想要构建世界观，推荐使用 /world 命令
            - 如果用户想要检查一致性，推荐使用 /check 命令
            - 如果用户想要起名，推荐使用 /name 命令
            
            请用中文回复，保持友好和专业的语气。
            """;
    }

    @Override
    protected String buildUserPrompt(ChatRequest input) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(input.message());
        
        // 添加上下文信息
        if (input.sessionId() != null) {
            SessionContext context = contextBus.getContext(input.sessionId());
            if (context != null && context.currentPhase() != null) {
                prompt.append("\n\n[当前创作阶段: ").append(context.currentPhase().name()).append("]");
            }
        }
        
        return prompt.toString();
    }

    @Override
    protected String parseResponse(String response, ChatRequest input) {
        // 记录对话完成事件
        if (input.sessionId() != null) {
            contextBus.publish(input.sessionId(), ContextEvent.agentCompleted(
                AGENT_NAME, 
                "对话完成", 
                System.currentTimeMillis()
            ));
        }
        return response;
    }

    /**
     * 流式对话
     * 支持 SSE 流式响应
     */
    @Override
    public Flux<String> stream(ChatRequest input) {
        publishThought("开始对话...");
        
        // 发布开始事件
        if (input.sessionId() != null) {
            contextBus.publish(input.sessionId(), ContextEvent.custom(
                AGENT_NAME,
                ContextEvent.EventType.AGENT_STARTED.name(),
                "开始对话",
                Map.of("message", input.message())
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
                        publishThought("对话完成");
                        if (input.sessionId() != null) {
                            contextBus.publish(input.sessionId(), ContextEvent.agentCompleted(
                                AGENT_NAME, "对话完成", System.currentTimeMillis()
                            ));
                        }
                    })
                    .doOnError(e -> {
                        publishThought("对话失败: " + e.getMessage());
                        log.error("[ChatAgent] 流式对话失败", e);
                    });
        } catch (Exception e) {
            log.error("[ChatAgent] 创建 ChatClient 失败", e);
            return Flux.error(e);
        }
    }

    /**
     * 检测是否应该推荐专业 Agent
     */
    public Intent detectSpecializedIntent(String message) {
        return Intent.matchFromMessage(message)
                .filter(intent -> intent != Intent.GENERAL_CHAT)
                .orElse(null);
    }
}
