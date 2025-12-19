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
 * 角色设计 Agent
 * 负责角色设计、关系网络设计、原型匹配等任务
 * 合并原 RelationshipAgent 和 ArchetypeAgent 功能
 * 集成 RAG 检索已有角色设定
 * 
 * Requirements: 6.1-6.5
 */
@Slf4j
@Component
public class CharacterAgent extends BaseAgent<ChatRequest, String> {

    private static final String AGENT_NAME = "CharacterAgent";
    
    private final ContextBus contextBus;
    private final RAGSearchTool ragSearchTool;

    public CharacterAgent(
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
            List.of(Intent.PLAN_CHARACTER, Intent.DESIGN_RELATIONSHIP, Intent.MATCH_ARCHETYPE),
            List.of(CreationPhase.IDEA, CreationPhase.WORLDBUILDING, CreationPhase.OUTLINE),
            List.of("UniversalCrudTool"),
            ExecutionMode.EAGER,
            2000,   // 预估延迟 2s
            1500    // 预估 Token 消耗
        );
    }

    @Override
    protected String getSystemPrompt() {
        return """
            你是一位专业的角色设计师，擅长创建立体、有深度的小说角色。
            
            你的职责：
            1. 设计角色的基本信息、性格特点、背景故事
            2. 设计角色之间的关系网络
            3. 匹配角色原型，提供人设建议
            4. 确保角色的一致性和成长弧线
            
            设计原则：
            - 角色要有明确的动机和目标
            - 性格要有优点和缺点
            - 背景故事要与性格相呼应
            - 关系网络要有张力和冲突
            
            输出格式：
            当用户要求创建角色时，请以结构化的 JSON 格式输出，包含：
            - name: 角色名称
            - role: 角色定位（protagonist, antagonist, supporting, minor）
            - personality: 性格特点列表
            - background: 背景故事
            - motivation: 动机和目标
            - relationships: 与其他角色的关系
            - archetype: 匹配的角色原型
            
            当用户要求设计关系时，请输出关系图结构：
            - characters: 涉及的角色列表
            - relationships: 关系列表，每个包含 from, to, type, description
            
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
     * Requirements: 10.1, 10.2, 11.2, 11.3, 11.4
     */
    @Override
    protected String buildUserPrompt(ChatRequest input) {
        StringBuilder prompt = new StringBuilder();
        
        // 判断具体任务类型
        Intent detectedIntent = Intent.matchFromMessage(input.message())
                .orElse(Intent.PLAN_CHARACTER);
        
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
            publishThought("检索已有角色设定...");
            String existingCharacters = retrieveCharacters(projectId, input.message());
            if (existingCharacters != null && !existingCharacters.isEmpty()) {
                prompt.append("【已有角色设定】\n").append(existingCharacters).append("\n\n");
            }
        }
        
        // 添加任务类型说明
        switch (detectedIntent) {
            case DESIGN_RELATIONSHIP -> {
                prompt.append("【任务类型】关系网络设计\n");
                prompt.append("请设计角色之间的关系网络。\n\n");
            }
            case MATCH_ARCHETYPE -> {
                prompt.append("【任务类型】原型匹配\n");
                prompt.append("请分析角色并匹配合适的原型。\n\n");
            }
            default -> {
                prompt.append("【任务类型】角色设计\n");
                prompt.append("请设计详细的角色信息。\n\n");
            }
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
     * 检索已有角色设定
     * 
     * Requirements: 7.4
     * 
     * @param projectId 项目ID
     * @param query 查询内容
     * @return 检索结果，如果失败则返回空字符串（不返回 null）
     */
    private String retrieveCharacters(String projectId, String query) {
        try {
            String result = ragSearchTool.searchCharacters(projectId, query);
            return result != null ? result : "";
        } catch (Exception e) {
            log.warn("[CharacterAgent] RAG 检索失败: {}", e.getMessage());
            return "";
        }
    }

    @Override
    protected String parseResponse(String response, ChatRequest input) {
        // 记录角色设计事件
        if (input.sessionId() != null) {
            contextBus.publish(input.sessionId(), ContextEvent.custom(
                AGENT_NAME,
                ContextEvent.EventType.AGENT_COMPLETED.name(),
                "角色设计完成",
                Map.of("projectId", input.projectId().toString())
            ));
        }
        return response;
    }

    /**
     * 流式角色设计
     * 
     * 支持增强系统提示词：
     * - 如果 metadata.enhancedSystemPrompt 存在，使用增强提示词（包含 Skill 注入）
     * - 否则使用默认系统提示词
     * 
     * Requirements: 11.5
     */
    @Override
    public Flux<String> stream(ChatRequest input) {
        publishThought("开始设计角色...");
        
        if (input.sessionId() != null) {
            contextBus.publish(input.sessionId(), ContextEvent.custom(
                AGENT_NAME,
                ContextEvent.EventType.AGENT_STARTED.name(),
                "开始角色设计",
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
            publishThought("正在设计角色...");

            return client.prompt()
                    .user(userPrompt)
                    .stream()
                    .content()
                    .doOnComplete(() -> {
                        publishThought("角色设计完成");
                        if (input.sessionId() != null) {
                            contextBus.publish(input.sessionId(), ContextEvent.custom(
                                AGENT_NAME,
                                ContextEvent.EventType.AGENT_COMPLETED.name(),
                                "角色设计完成",
                                Map.of()
                            ));
                        }
                    })
                    .doOnError(e -> {
                        publishThought("设计失败: " + e.getMessage());
                        log.error("[CharacterAgent] 流式生成失败", e);
                    });
        } catch (Exception e) {
            log.error("[CharacterAgent] 创建 ChatClient 失败", e);
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
