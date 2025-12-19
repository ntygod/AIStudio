package com.inkflow.module.agent.impl;

import com.inkflow.module.agent.context.ContextBus;
import com.inkflow.module.agent.context.ContextEvent;
import com.inkflow.module.agent.context.SessionContext;
import com.inkflow.module.agent.workflow.PreprocessingContext;
import com.inkflow.module.agent.core.*;
import com.inkflow.module.agent.dto.ChatRequest;
import com.inkflow.module.ai_bridge.chat.DynamicChatModelFactory;
import com.inkflow.module.ai_bridge.tool.RAGSearchTool;
import com.inkflow.module.ai_bridge.tool.StyleRetrieveTool;
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
 * 写作 Agent
 * 负责内容生成、续写、扩写等核心创作任务
 * 集成 RAG 检索和风格匹配
 * 
 * Requirements: 4.1-4.6
 */
@Slf4j
@Component
public class WriterAgent extends BaseAgent<ChatRequest, String> {

    private static final String AGENT_NAME = "WriterAgent";
    
    private final ContextBus contextBus;
    private final RAGSearchTool ragSearchTool;
    private final StyleRetrieveTool styleRetrieveTool;

    public WriterAgent(
            DynamicChatModelFactory chatModelFactory,
            ApplicationEventPublisher eventPublisher,
            ContextBus contextBus,
            RAGSearchTool ragSearchTool,
            StyleRetrieveTool styleRetrieveTool) {
        super(chatModelFactory, eventPublisher);
        this.contextBus = contextBus;
        this.ragSearchTool = ragSearchTool;
        this.styleRetrieveTool = styleRetrieveTool;
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
            List.of(Intent.WRITE_CONTENT),
            List.of(CreationPhase.WRITING, CreationPhase.REVISION),
            List.of("RAGSearchTool", "StyleRetrieveTool"),
            ExecutionMode.EAGER,
            3000,   // 预估延迟 3s（内容生成较慢）
            2000    // 预估 Token 消耗
        );
    }

    @Override
    protected String getSystemPrompt() {
        return """
            你是一位专业的小说创作助手，擅长各种类型的小说写作。
            
            你的职责：
            1. 根据用户要求生成高质量的小说内容
            2. 保持与已有内容的风格一致性
            3. 遵循已建立的世界观和角色设定
            4. 注意情节的连贯性和逻辑性
            
            写作原则：
            - 展示而非告知（Show, don't tell）
            - 保持人物性格的一致性
            - 注意节奏和张力的把控
            - 使用生动的描写和对话
            
            请用中文创作，保持文学性和可读性。
            """;
    }

    /**
     * 构建用户提示词
     * 
     * "哑 Agent" 策略：
     * 1. 优先从 metadata.preprocessingContext 获取上下文（由 Workflow 预处理提供）
     * 2. 如果没有预处理上下文，降级到直接 RAG 调用（向后兼容）
     */
    @Override
    protected String buildUserPrompt(ChatRequest input) {
        StringBuilder prompt = new StringBuilder();
        
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
            
            // RAG 检索相关设定
            publishThought("检索相关设定...");
            String ragContext = retrieveContext(projectId, input.message());
            if (ragContext != null && !ragContext.isEmpty()) {
                prompt.append("【相关设定】\n").append(ragContext).append("\n\n");
            }
            
            // 获取风格样本
            publishThought("匹配写作风格...");
            String styleContext = retrieveStyle(projectId, input.message());
            if (styleContext != null && !styleContext.isEmpty()) {
                prompt.append("【参考风格】\n").append(styleContext).append("\n\n");
            }
        }
        
        // 添加会话上下文（无论是否有预处理上下文都添加）
        if (input.sessionId() != null) {
            SessionContext context = contextBus.getContext(input.sessionId());
            if (context != null && !context.recentEntities().isEmpty()) {
                prompt.append("【最近讨论的内容】\n");
                context.recentEntities().stream()
                    .limit(5)
                    .forEach(entity -> prompt.append("- ")
                        .append(entity.entityType()).append(": ")
                        .append(entity.entityName()).append("\n"));
                prompt.append("\n");
            }
        }
        
        // 用户请求
        prompt.append("【创作要求】\n").append(input.message());
        
        return prompt.toString();
    }
    
    /**
     * 从 ChatRequest.metadata 中获取预处理上下文
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

    @Override
    protected String parseResponse(String response, ChatRequest input) {
        // 记录内容生成事件
        if (input.sessionId() != null) {
            contextBus.publish(input.sessionId(), ContextEvent.contentGenerated(
                AGENT_NAME,
                "内容生成完成",
                Map.of(
                    "contentLength", response.length(),
                    "projectId", input.projectId().toString()
                )
            ));
        }
        return response;
    }

    /**
     * 流式内容生成
     * 
     * 支持增强系统提示词：
     * - 如果 metadata.enhancedSystemPrompt 存在，使用增强提示词（包含 Skill 注入）
     * - 否则使用默认系统提示词
     *
     */
    @Override
    public Flux<String> stream(ChatRequest input) {
        publishThought("开始创作...");
        
        // 发布开始事件
        if (input.sessionId() != null) {
            contextBus.publish(input.sessionId(), ContextEvent.custom(
                AGENT_NAME,
                ContextEvent.EventType.AGENT_STARTED.name(),
                "开始创作",
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
            publishThought("正在生成内容...");

            return client.prompt()
                    .user(userPrompt)
                    .stream()
                    .content()
                    .doOnComplete(() -> {
                        publishThought("创作完成");
                        if (input.sessionId() != null) {
                            contextBus.publish(input.sessionId(), ContextEvent.contentGenerated(
                                AGENT_NAME, "创作完成", Map.of()
                            ));
                        }
                    })
                    .doOnError(e -> {
                        publishThought("创作失败: " + e.getMessage());
                        log.error("[WriterAgent] 流式生成失败", e);
                    });
        } catch (Exception e) {
            log.error("[WriterAgent] 创建 ChatClient 失败", e);
            return Flux.error(e);
        }
    }
    
    /**
     * 获取增强的系统提示词
     * 如果 metadata 中有 enhancedSystemPrompt（由 Workflow 的 Skill 注入提供），使用它
     * 否则使用默认系统提示词
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

    /**
     * 检索相关上下文
     * 
     * @param projectId 项目ID
     * @param query 查询内容
     * @return 检索结果，如果失败则返回空字符串（不返回 null）
     */
    private String retrieveContext(String projectId, String query) {
        try {
            String result = ragSearchTool.searchKnowledge(projectId, query, 5);
            return result != null ? result : "";
        } catch (Exception e) {
            log.warn("[WriterAgent] RAG 检索失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 检索风格样本
     * 
     * Requirements: 7.5
     * 
     * @param projectId 项目ID
     * @param context 上下文内容
     * @return 风格样本，如果失败则返回默认风格配置（不返回 null）
     */
    private String retrieveStyle(String projectId, String context) {
        try {
            String result = styleRetrieveTool.retrieveStyleSamples(projectId, context, 3);
            return result != null ? result : getDefaultStyleConfig();
        } catch (Exception e) {
            log.warn("[WriterAgent] 风格检索失败: {}", e.getMessage());
            return getDefaultStyleConfig();
        }
    }

    /**
     * 获取默认风格配置
     * 
     * Requirements: 7.5
     * 
     * @return 默认风格配置字符串
     */
    private String getDefaultStyleConfig() {
        return """
            【默认风格指南】
            - 语言风格：流畅自然，避免生硬
            - 叙事视角：根据上下文自动适配
            - 描写密度：适中，注重情节推进
            - 对话风格：符合角色性格特点
            """;
    }
}
