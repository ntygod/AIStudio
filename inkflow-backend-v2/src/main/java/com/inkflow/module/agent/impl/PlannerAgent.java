package com.inkflow.module.agent.impl;

import com.inkflow.module.agent.context.ContextBus;
import com.inkflow.module.agent.context.ContextEvent;
import com.inkflow.module.agent.core.*;
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
 * 规划 Agent
 * 负责大纲规划、伏笔管理、节奏分析等任务
 * 合并原 PlotLoopAgent 和 PacingAgent 功能
 * 集成 RAG 检索已有章节和设定
 *
 */
@Slf4j
@Component
public class PlannerAgent extends BaseAgent<ChatRequest, String> {

    private static final String AGENT_NAME = "PlannerAgent";
    
    private final ContextBus contextBus;
    private final RAGSearchTool ragSearchTool;

    public PlannerAgent(
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
            List.of(Intent.PLAN_OUTLINE, Intent.MANAGE_PLOTLOOP, Intent.ANALYZE_PACING),
            List.of(CreationPhase.OUTLINE, CreationPhase.WRITING),
            List.of("UniversalCrudTool"),
            ExecutionMode.EAGER,
            2500,   // 预估延迟 2.5s
            1800    // 预估 Token 消耗
        );
    }

    @Override
    protected String getSystemPrompt() {
        return """
            你是一位专业的故事规划师，擅长设计小说的整体结构和节奏。
            
            你的职责：
            1. 设计小说大纲和章节结构
            2. 管理伏笔的埋设和回收
            3. 分析和优化故事节奏
            4. 确保情节的逻辑性和吸引力
            
            规划原则：
            - 三幕结构或英雄之旅等经典叙事结构
            - 伏笔要有明确的埋设点和回收点
            - 节奏要有张有弛，高潮迭起
            - 每个章节要有明确的目的
            
            输出格式：
            
            【大纲规划】请以结构化格式输出：
            - title: 小说标题
            - volumes: 卷列表，每卷包含 title, summary, chapters
            - chapters: 章节列表，每章包含 title, summary, keyEvents
            
            【伏笔管理】请以结构化格式输出：
            - plotLoops: 伏笔列表，每个包含：
              - title: 伏笔名称
              - description: 伏笔描述
              - plantChapter: 埋设章节
              - harvestChapter: 回收章节（可选）
              - status: 状态（planted, harvested, abandoned）
            
            【节奏分析】请输出：
            - analysis: 当前节奏分析
            - suggestions: 优化建议列表
            - tensionCurve: 张力曲线描述
            
            请用中文回复，保持专业和系统性。
            """;
    }

    @Override
    protected String buildUserPrompt(ChatRequest input) {
        StringBuilder prompt = new StringBuilder();
        
        // 判断具体任务类型
        Intent detectedIntent = Intent.matchFromMessage(input.message())
                .orElse(Intent.PLAN_OUTLINE);
        
        // RAG 检索已有章节内容和设定
        String projectId = input.projectId().toString();
        publishThought("检索已有章节和设定...");
        String existingContent = retrieveExistingContent(projectId, input.message());
        if (existingContent != null && !existingContent.isEmpty()) {
            prompt.append("【已有内容参考】\n").append(existingContent).append("\n\n");
        }
        
        switch (detectedIntent) {
            case MANAGE_PLOTLOOP -> {
                prompt.append("【任务类型】伏笔管理\n");
                prompt.append("请帮助管理伏笔的埋设和回收。\n\n");
            }
            case ANALYZE_PACING -> {
                prompt.append("【任务类型】节奏分析\n");
                prompt.append("请分析故事节奏并提供优化建议。\n\n");
            }
            default -> {
                prompt.append("【任务类型】大纲规划\n");
                prompt.append("请设计详细的故事大纲。\n\n");
            }
        }
        
        prompt.append("【用户需求】\n").append(input.message());
        
        return prompt.toString();
    }

    /**
     * 检索已有章节内容和设定
     * 
     * Requirements: 7.4
     * 
     * @param projectId 项目ID
     * @param query 查询内容
     * @return 检索结果，如果失败则返回空字符串（不返回 null）
     */
    private String retrieveExistingContent(String projectId, String query) {
        try {
            // 检索章节内容
            String chapters = ragSearchTool.searchChapters(projectId, query);
            // 检索相关设定
            String knowledge = ragSearchTool.searchKnowledge(projectId, query, 3);
            
            StringBuilder result = new StringBuilder();
            if (chapters != null && !chapters.contains("未找到")) {
                result.append("【已有章节】\n").append(chapters).append("\n");
            }
            if (knowledge != null && !knowledge.contains("未找到")) {
                result.append("【相关设定】\n").append(knowledge);
            }
            return result.toString();
        } catch (Exception e) {
            log.warn("[PlannerAgent] RAG 检索失败: {}", e.getMessage());
            return "";
        }
    }

    @Override
    protected String parseResponse(String response, ChatRequest input) {
        // 记录规划完成事件
        if (input.sessionId() != null) {
            contextBus.publish(input.sessionId(), ContextEvent.custom(
                AGENT_NAME,
                ContextEvent.EventType.AGENT_COMPLETED.name(),
                "规划完成",
                Map.of("projectId", input.projectId().toString())
            ));
        }
        return response;
    }

    /**
     * 流式规划
     */
    @Override
    public Flux<String> stream(ChatRequest input) {
        publishThought("开始规划...");
        
        if (input.sessionId() != null) {
            contextBus.publish(input.sessionId(), ContextEvent.custom(
                AGENT_NAME,
                ContextEvent.EventType.AGENT_STARTED.name(),
                "开始规划",
                Map.of("projectId", input.projectId().toString())
            ));
        }

        try {
            ChatModel model = chatModelFactory.getDefaultModel();
            ChatClient client = ChatClient.builder(model)
                    .defaultSystem(getSystemPrompt())
                    .build();

            String userPrompt = buildUserPrompt(input);
            publishThought("正在规划...");

            return client.prompt()
                    .user(userPrompt)
                    .stream()
                    .content()
                    .doOnComplete(() -> {
                        publishThought("规划完成");
                        if (input.sessionId() != null) {
                            contextBus.publish(input.sessionId(), ContextEvent.custom(
                                AGENT_NAME,
                                ContextEvent.EventType.AGENT_COMPLETED.name(),
                                "规划完成",
                                Map.of()
                            ));
                        }
                    })
                    .doOnError(e -> {
                        publishThought("规划失败: " + e.getMessage());
                        log.error("[PlannerAgent] 流式生成失败", e);
                    });
        } catch (Exception e) {
            log.error("[PlannerAgent] 创建 ChatClient 失败", e);
            return Flux.error(e);
        }
    }
}
