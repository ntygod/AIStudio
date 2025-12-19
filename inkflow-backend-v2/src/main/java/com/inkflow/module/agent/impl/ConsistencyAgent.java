package com.inkflow.module.agent.impl;

import com.inkflow.module.agent.context.ContextBus;
import com.inkflow.module.agent.context.ContextEvent;
import com.inkflow.module.agent.core.*;
import com.inkflow.module.agent.dto.ChatRequest;
import com.inkflow.module.ai_bridge.chat.DynamicChatModelFactory;
import com.inkflow.module.ai_bridge.tool.PreflightTool;
import com.inkflow.module.ai_bridge.tool.RAGSearchTool;
import com.inkflow.module.consistency.dto.ConsistencyWarningDto;
import com.inkflow.module.consistency.dto.CreateWarningRequest;
import com.inkflow.module.consistency.entity.ConsistencyWarning.Severity;
import com.inkflow.module.consistency.entity.ConsistencyWarning.WarningType;
import com.inkflow.module.consistency.service.ConsistencyWarningService;
import com.inkflow.module.consistency.service.ProactiveConsistencyService;
import com.inkflow.module.consistency.service.RuleCheckerService;
import com.inkflow.module.evolution.entity.EntityType;
import com.inkflow.module.project.entity.CreationPhase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 一致性检查 Agent
 * 负责一致性检查、文风分析等质量保障任务
 * 合并原 StyleAgent 功能
 * 
 * Requirements: 5.1, 5.2, 5.3, 5.4, 8.1-8.6
 */
@Slf4j
@Component
public class ConsistencyAgent extends BaseAgent<ChatRequest, String> {

    private static final String AGENT_NAME = "ConsistencyAgent";
    
    private final ContextBus contextBus;
    private final RAGSearchTool ragSearchTool;
    private final PreflightTool preflightTool;
    private final ProactiveConsistencyService proactiveConsistencyService;
    private final ConsistencyWarningService warningService;
    @Nullable
    private final RuleCheckerService ruleCheckerService;

    public ConsistencyAgent(
            DynamicChatModelFactory chatModelFactory,
            ApplicationEventPublisher eventPublisher,
            ContextBus contextBus,
            RAGSearchTool ragSearchTool,
            PreflightTool preflightTool,
            ProactiveConsistencyService proactiveConsistencyService,
            ConsistencyWarningService warningService,
            @Nullable RuleCheckerService ruleCheckerService) {
        super(chatModelFactory, eventPublisher);
        this.contextBus = contextBus;
        this.ragSearchTool = ragSearchTool;
        this.preflightTool = preflightTool;
        this.proactiveConsistencyService = proactiveConsistencyService;
        this.warningService = warningService;
        this.ruleCheckerService = ruleCheckerService;
    }

    @Override
    public String getName() {
        return AGENT_NAME;
    }

    @Override
    public AgentCapability getCapability() {
        return new AgentCapability(
            AGENT_NAME,
            AgentCategory.QUALITY,
            List.of(Intent.CHECK_CONSISTENCY, Intent.ANALYZE_STYLE),
            List.of(CreationPhase.WRITING, CreationPhase.REVISION),
            List.of("RAGSearchTool", "PreflightTool"),
            ExecutionMode.EAGER,
            3000,   // 预估延迟 3s（需要检索和分析）
            2000    // 预估 Token 消耗
        );
    }

    @Override
    protected String getSystemPrompt() {
        return """
            你是一位专业的小说质量审核专家，擅长发现和修正各种问题。
            
            你的职责：
            1. 检查内容与已有设定的一致性
            2. 发现情节逻辑漏洞和矛盾
            3. 分析文风一致性
            4. 提供修正建议
            
            检查维度：
            - 角色一致性：性格、外貌、能力是否前后一致
            - 世界观一致性：设定、规则是否自洽
            - 时间线一致性：事件顺序是否合理
            - 逻辑一致性：因果关系是否成立
            - 文风一致性：语言风格是否统一
            
            输出格式：
            
            【一致性检查】请输出：
            - issues: 问题列表，每个包含：
              - type: 问题类型（character, world, timeline, logic）
              - severity: 严重程度（error, warning, info）
              - description: 问题描述
              - location: 问题位置
              - suggestion: 修正建议
            - summary: 总体评估
            
            【文风分析】请输出：
            - style: 当前文风特点
            - consistency: 一致性评分（1-10）
            - issues: 风格不一致的地方
            - suggestions: 改进建议
            
            请用中文回复，保持客观和专业。
            """;
    }

    @Override
    protected String buildUserPrompt(ChatRequest input) {
        StringBuilder prompt = new StringBuilder();
        
        // 判断具体任务类型
        Intent detectedIntent = Intent.matchFromMessage(input.message())
                .orElse(Intent.CHECK_CONSISTENCY);
        
        // 获取相关设定作为参考
        String projectId = input.projectId().toString();
        publishThought("检索相关设定...");
        String context = retrieveContext(projectId, input.message());
        
        if (context != null && !context.isEmpty()) {
            prompt.append("【已有设定参考】\n").append(context).append("\n\n");
        }
        
        // 执行预检查
        if (detectedIntent == Intent.CHECK_CONSISTENCY) {
            publishThought("执行预检查...");
            String preflightResult = runPreflight(projectId, input.message());
            if (preflightResult != null && !preflightResult.isEmpty()) {
                prompt.append("【预检查结果】\n").append(preflightResult).append("\n\n");
            }
        }
        
        switch (detectedIntent) {
            case ANALYZE_STYLE -> {
                prompt.append("【任务类型】文风分析\n");
                prompt.append("请分析文风一致性并提供建议。\n\n");
            }
            default -> {
                prompt.append("【任务类型】一致性检查\n");
                prompt.append("请检查内容一致性并列出问题。\n\n");
            }
        }
        
        prompt.append("【待检查内容】\n").append(input.message());
        
        return prompt.toString();
    }

    @Override
    protected String parseResponse(String response, ChatRequest input) {
        // 解析响应并存储警告
        List<ParsedWarning> warnings = parseWarningsFromResponse(response);
        if (!warnings.isEmpty()) {
            storeWarnings(input.projectId(), warnings);
        }
        
        // 记录检查完成事件
        if (input.sessionId() != null) {
            contextBus.publish(input.sessionId(), ContextEvent.custom(
                AGENT_NAME,
                ContextEvent.EventType.AGENT_COMPLETED.name(),
                "一致性检查完成",
                Map.of(
                    "projectId", input.projectId().toString(),
                    "warningCount", warnings.size()
                )
            ));
        }
        return response;
    }
    
    /**
     * 从 AI 响应中解析警告
     * Requirements: 5.3
     */
    private List<ParsedWarning> parseWarningsFromResponse(String response) {
        List<ParsedWarning> warnings = new ArrayList<>();
        
        if (response == null || response.isBlank()) {
            return warnings;
        }
        
        // 简单解析：查找包含问题标记的行
        String[] lines = response.split("\n");
        for (String line : lines) {
            String lowerLine = line.toLowerCase();
            
            // 检测严重程度
            Severity severity = Severity.INFO;
            if (lowerLine.contains("error") || lowerLine.contains("错误") || lowerLine.contains("严重")) {
                severity = Severity.ERROR;
            } else if (lowerLine.contains("warning") || lowerLine.contains("警告") || lowerLine.contains("问题")) {
                severity = Severity.WARNING;
            }
            
            // 检测问题类型
            WarningType warningType = WarningType.OTHER;
            if (lowerLine.contains("character") || lowerLine.contains("角色")) {
                warningType = WarningType.CHARACTER_INCONSISTENCY;
            } else if (lowerLine.contains("timeline") || lowerLine.contains("时间")) {
                warningType = WarningType.TIMELINE_CONFLICT;
            } else if (lowerLine.contains("world") || lowerLine.contains("世界观") || lowerLine.contains("设定")) {
                warningType = WarningType.SETTING_VIOLATION;
            }
            
            // 只记录包含问题关键词的行
            if (lowerLine.contains("问题") || lowerLine.contains("不一致") || 
                lowerLine.contains("矛盾") || lowerLine.contains("冲突") ||
                lowerLine.contains("error") || lowerLine.contains("warning")) {
                warnings.add(new ParsedWarning(line.trim(), severity, warningType));
            }
        }
        
        return warnings;
    }
    
    /**
     * 存储警告到数据库
     * Requirements: 5.2
     */
    private void storeWarnings(UUID projectId, List<ParsedWarning> warnings) {
        for (ParsedWarning warning : warnings) {
            try {
                CreateWarningRequest request = new CreateWarningRequest(
                        projectId,
                        null, // entityId - 从 AI 响应中无法确定
                        null, // entityType
                        null, // entityName
                        warning.warningType(),
                        warning.severity(),
                        warning.description(),
                        "请检查并修正此问题", // suggestion
                        null, // fieldPath
                        null, // expectedValue
                        null, // actualValue
                        null, // relatedEntityIds
                        null  // suggestedResolution
                );
                
                ConsistencyWarningDto saved = warningService.createWarning(request);
                if (saved != null) {
                    log.debug("[ConsistencyAgent] 存储警告: id={}, type={}", 
                            saved.id(), warning.warningType());
                }
            } catch (Exception e) {
                log.warn("[ConsistencyAgent] 存储警告失败: {}", e.getMessage());
            }
        }
    }
    
    /**
     * 解析的警告记录
     */
    private record ParsedWarning(String description, Severity severity, WarningType warningType) {}

    /**
     * 流式检查
     */
    @Override
    public Flux<String> stream(ChatRequest input) {
        publishThought("开始一致性检查...");
        
        if (input.sessionId() != null) {
            contextBus.publish(input.sessionId(), ContextEvent.custom(
                AGENT_NAME,
                ContextEvent.EventType.AGENT_STARTED.name(),
                "开始一致性检查",
                Map.of("projectId", input.projectId().toString())
            ));
        }

        try {
            ChatModel model = chatModelFactory.getDefaultModel();
            ChatClient client = ChatClient.builder(model)
                    .defaultSystem(getSystemPrompt())
                    .build();

            String userPrompt = buildUserPrompt(input);
            publishThought("正在分析...");

            return client.prompt()
                    .user(userPrompt)
                    .stream()
                    .content()
                    .doOnComplete(() -> {
                        publishThought("检查完成");
                        if (input.sessionId() != null) {
                            contextBus.publish(input.sessionId(), ContextEvent.custom(
                                AGENT_NAME,
                                ContextEvent.EventType.AGENT_COMPLETED.name(),
                                "检查完成",
                                Map.of()
                            ));
                        }
                    })
                    .doOnError(e -> {
                        publishThought("检查失败: " + e.getMessage());
                        log.error("[ConsistencyAgent] 流式生成失败", e);
                    });
        } catch (Exception e) {
            log.error("[ConsistencyAgent] 创建 ChatClient 失败", e);
            return Flux.error(e);
        }
    }

    /**
     * 检索相关上下文
     * 
     * Requirements: 7.4
     * 
     * @param projectId 项目ID
     * @param query 查询内容
     * @return 检索结果，如果失败则返回空字符串（不返回 null）
     */
    private String retrieveContext(String projectId, String query) {
        try {
            String result = ragSearchTool.searchKnowledge(projectId, query, 10);
            return result != null ? result : "";
        } catch (Exception e) {
            log.warn("[ConsistencyAgent] RAG 检索失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 执行预检查
     * 
     * Requirements: 7.4
     * 
     * @param projectId 项目ID
     * @param content 待检查内容
     * @return 预检查结果，如果失败则返回空字符串（不返回 null）
     */
    private String runPreflight(String projectId, String content) {
        try {
            // 将内容作为节拍传入预检工具
            String result = preflightTool.runPreflight(projectId, content, false);
            return result != null ? result : "";
        } catch (Exception e) {
            log.warn("[ConsistencyAgent] 预检查失败: {}", e.getMessage());
            return "";
        }
    }
}
