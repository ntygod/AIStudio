package com.inkflow.module.agent.impl;

import com.inkflow.module.agent.context.ContextBus;
import com.inkflow.module.agent.context.ContextEvent;
import com.inkflow.module.agent.core.*;
import com.inkflow.module.agent.workflow.PreprocessingContext;
import com.inkflow.module.agent.dto.ChatRequest;
import com.inkflow.module.ai_bridge.chat.DynamicChatModelFactory;
import com.inkflow.module.ai_bridge.tool.RAGSearchTool;
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
 * 世界观构建 Agent
 * 负责世界观设计、灵感激发等任务
 * 合并原 IdeaAgent 功能
 * 集成 RAG 检索已有世界观设定
 * 
 * Requirements: 5.1-5.5
 */
@Slf4j
@Component
public class WorldBuilderAgent extends BaseAgent<ChatRequest, String> {

    private static final String AGENT_NAME = "WorldBuilderAgent";
    
    private final ContextBus contextBus;
    private final RAGSearchTool ragSearchTool;

    public WorldBuilderAgent(
            DynamicChatModelFactory chatModelFactory,
            ApplicationEventPublisher eventPublisher,
            ContextBus contextBus,
            RAGSearchTool ragSearchTool) {
        super(chatModelFactory, eventPublisher);
        this.contextBus = contextBus;
        this.ragSearchTool = ragSearchTool;
    }

    @Override
    public String getName() {
        return AGENT_NAME;
    }

    @Override
    public AgentCapability getCapability() {
        return new AgentCapability(
            AGENT_NAME,
            AgentCategory.CREATIVE,
            List.of(Intent.PLAN_WORLD, Intent.BRAINSTORM_IDEA),
            List.of(CreationPhase.IDEA, CreationPhase.WORLDBUILDING),
            List.of("UniversalCrudTool"),
            ExecutionMode.EAGER,
            2000,   // 预估延迟 2s
            1500    // 预估 Token 消耗
        );
    }

    @Override
    protected String getSystemPrompt() {
        return """
            你是一位专业的世界观架构师，擅长构建各种类型的小说世界观。
            
            你的职责：
            1. 帮助用户设计完整的世界观体系
            2. 提供创意灵感和构思建议
            3. 设计力量体系、魔法体系、修炼体系等
            4. 构建历史背景、地理环境、社会结构
            
            设计原则：
            - 内部逻辑自洽
            - 有足够的深度和细节
            - 为故事发展留有空间
            - 考虑与角色和情节的关联
            
            输出格式：
            当用户要求创建设定时，请以结构化的 JSON 格式输出，包含：
            - name: 设定名称
            - type: 设定类型（world_setting, power_system, geography, history, society）
            - description: 详细描述
            - rules: 规则列表（如适用）
            - connections: 与其他设定的关联
            
            请用中文回复，保持专业和创意。
            """;
    }

    /**
     * 构建用户提示词
     * 
     * "哑 Agent" 策略：
     * 1. 优先从 metadata.preprocessingContext 获取上下文（由 Workflow 预处理提供）
     * 2. 如果没有预处理上下文，降级到直接 RAG 调用（向后兼容）
     * 
     * Requirements: 10.1, 10.2
     */
    @Override
    protected String buildUserPrompt(ChatRequest input) {
        StringBuilder prompt = new StringBuilder();
        
        // 判断是灵感激发还是世界观设计
        Intent detectedIntent = Intent.matchFromMessage(input.message())
                .orElse(Intent.PLAN_WORLD);
        
        // 尝试从 metadata 获取预处理上下文
        PreprocessingContext preprocessingContext = getPreprocessingContext(input);
        
        if (preprocessingContext != null && !preprocessingContext.isEmpty()) {
            // 使用 Workflow 预处理的上下文（"哑 Agent" 模式）
            String contextStr = preprocessingContext.toContextString();
            if (!contextStr.isEmpty()) {
                prompt.append(contextStr).append("\n\n");
            }
            publishThought("使用预处理上下文");
        } else {
            // 降级：自己做 RAG（向后兼容，支持直接调用 Agent）
            String projectId = input.projectId().toString();
            publishThought("检索已有世界观设定...");
            String existingWiki = retrieveWikiEntries(projectId, input.message());
            if (existingWiki != null && !existingWiki.isEmpty()) {
                prompt.append("【已有世界观设定】\n").append(existingWiki).append("\n\n");
            }
        }
        
        // 添加任务类型说明
        if (detectedIntent == Intent.BRAINSTORM_IDEA) {
            prompt.append("【任务类型】灵感激发\n");
            prompt.append("请提供创意建议和构思方向。\n\n");
        } else {
            prompt.append("【任务类型】世界观设计\n");
            prompt.append("请设计详细的世界观设定。\n\n");
        }
        
        prompt.append("【用户需求】\n").append(input.message());
        
        return prompt.toString();
    }
    
    /**
     * 从 ChatRequest.metadata 中获取预处理上下文
     * 
     * Requirements: 11.2, 11.3
     * 
     * @param input 聊天请求
     * @return 预处理上下文，如果不存在则返回 null
     */
    private PreprocessingContext getPreprocessingContext(ChatRequest input) {
        if (input.metadata() == null) {
            return null;
        }
        Object context = input.metadata().get("preprocessingContext");
        if (context instanceof PreprocessingContext pc) {
            return pc;
        }
        return null;
    }

    /**
     * 检索已有世界观设定
     * 
     * Requirements: 7.4
     * 
     * @param projectId 项目ID
     * @param query 查询内容
     * @return 检索结果，如果失败则返回空字符串（不返回 null）
     */
    private String retrieveWikiEntries(String projectId, String query) {
        try {
            String result = ragSearchTool.searchWiki(projectId, query);
            return result != null ? result : "";
        } catch (Exception e) {
            log.warn("[WorldBuilderAgent] RAG 检索失败: {}", e.getMessage());
            return "";
        }
    }

    @Override
    protected String parseResponse(String response, ChatRequest input) {
        // 记录世界观设计事件
        if (input.sessionId() != null) {
            contextBus.publish(input.sessionId(), ContextEvent.custom(
                AGENT_NAME,
                ContextEvent.EventType.AGENT_COMPLETED.name(),
                "世界观设计完成",
                Map.of("projectId", input.projectId().toString())
            ));
        }
        return response;
    }

    /**
     * 流式世界观设计
     * 
     * 支持增强系统提示词：
     * - 如果 metadata.enhancedSystemPrompt 存在，使用增强提示词（包含 Skill 注入）
     * - 否则使用默认系统提示词
     * 
     * Requirements: 11.5
     */
    @Override
    public Flux<String> stream(ChatRequest input) {
        publishThought("开始构建世界观...");
        
        if (input.sessionId() != null) {
            contextBus.publish(input.sessionId(), ContextEvent.custom(
                AGENT_NAME,
                ContextEvent.EventType.AGENT_STARTED.name(),
                "开始世界观设计",
                Map.of("projectId", input.projectId().toString())
            ));
        }

        try {
            ChatModel model = chatModelFactory.getDefaultModel();
            
            // 获取系统提示词（优先使用增强提示词）
            String systemPrompt = getEnhancedSystemPrompt(input);
            
            ChatClient client = ChatClient.builder(model)
                    .defaultSystem(systemPrompt)
                    .build();

            String userPrompt = buildUserPrompt(input);
            publishThought("正在设计世界观...");

            return client.prompt()
                    .user(userPrompt)
                    .stream()
                    .content()
                    .doOnComplete(() -> {
                        publishThought("世界观设计完成");
                        if (input.sessionId() != null) {
                            contextBus.publish(input.sessionId(), ContextEvent.custom(
                                AGENT_NAME,
                                ContextEvent.EventType.AGENT_COMPLETED.name(),
                                "世界观设计完成",
                                Map.of()
                            ));
                        }
                    })
                    .doOnError(e -> {
                        publishThought("设计失败: " + e.getMessage());
                        log.error("[WorldBuilderAgent] 流式生成失败", e);
                    });
        } catch (Exception e) {
            log.error("[WorldBuilderAgent] 创建 ChatClient 失败", e);
            return Flux.error(e);
        }
    }
    
    /**
     * 获取增强的系统提示词
     * 
     * 如果 metadata 中有 enhancedSystemPrompt（由 Workflow 的 Skill 注入提供），使用它
     * 否则使用默认系统提示词
     * 
     * Requirements: 11.5
     * 
     * @param input 聊天请求
     * @return 系统提示词
     */
    private String getEnhancedSystemPrompt(ChatRequest input) {
        if (input.metadata() != null && input.metadata().containsKey("enhancedSystemPrompt")) {
            Object enhanced = input.metadata().get("enhancedSystemPrompt");
            if (enhanced instanceof String enhancedPrompt && !enhancedPrompt.isBlank()) {
                publishThought("使用增强系统提示词（含 Skill 注入）");
                return enhancedPrompt;
            }
        }
        return getSystemPrompt();
    }
}
