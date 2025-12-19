package com.inkflow.module.evolution.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.module.ai_bridge.chat.DynamicChatModelFactory;
import com.inkflow.module.evolution.dto.InconsistencyReport;
import com.inkflow.module.evolution.dto.InconsistencyReport.Severity;
import com.inkflow.module.evolution.entity.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 预检服务
 * 在生成内容前检查逻辑合理性
 */
@Service
public class PreflightService {

    private static final Logger log = LoggerFactory.getLogger(PreflightService.class);

    private final DynamicChatModelFactory chatModelFactory;
    private final StateRetrievalService stateRetrievalService;
    private final ObjectMapper objectMapper;

    public PreflightService(
            DynamicChatModelFactory chatModelFactory,
            StateRetrievalService stateRetrievalService,
            ObjectMapper objectMapper) {
        this.chatModelFactory = chatModelFactory;
        this.stateRetrievalService = stateRetrievalService;
        this.objectMapper = objectMapper;
    }

    /**
     * 预检生成请求
     */
    public PreflightResult preflight(PreflightRequest request) {
        List<InconsistencyReport> issues = new ArrayList<>();

        // 1. 检查角色状态
        issues.addAll(checkCharacterStates(request));

        // 2. 检查场景逻辑
        issues.addAll(checkSceneLogic(request));

        // 3. 检查时间线
        issues.addAll(checkTimeline(request));

        // 判断是否可以继续
        boolean canProceed = issues.stream()
                .noneMatch(i -> i.severity() == Severity.ERROR);

        return new PreflightResult(canProceed, issues);
    }

    /**
     * 检查角色状态
     */
    private List<InconsistencyReport> checkCharacterStates(PreflightRequest request) {
        List<InconsistencyReport> issues = new ArrayList<>();

        for (UUID characterId : request.involvedCharacterIds()) {
            Optional<Map<String, Object>> stateOpt = stateRetrievalService
                    .getStateAtChapter(EntityType.CHARACTER, characterId, request.chapterOrder());

            if (stateOpt.isEmpty()) {
                continue;
            }

            Map<String, Object> state = stateOpt.get();

            // 检查角色是否存活
            Object status = state.get("status");
            if ("DEAD".equals(status) || "DECEASED".equals(status)) {
                issues.add(InconsistencyReport.builder()
                        .entityId(characterId)
                        .entityType(EntityType.CHARACTER)
                        .entityName((String) state.get("name"))
                        .fieldPath("status")
                        .expectedValue("ALIVE")
                        .actualValue(String.valueOf(status))
                        .description("角色已死亡，无法参与场景")
                        .suggestion("移除该角色或使用回忆/闪回场景")
                        .severity(Severity.ERROR)
                        .build());
            }

            // 检查角色是否在场
            Object location = state.get("currentLocation");
            if (request.sceneLocation() != null && location != null
                    && !request.sceneLocation().equals(location)) {
                issues.add(InconsistencyReport.builder()
                        .entityId(characterId)
                        .entityType(EntityType.CHARACTER)
                        .entityName((String) state.get("name"))
                        .fieldPath("currentLocation")
                        .expectedValue(request.sceneLocation())
                        .actualValue(String.valueOf(location))
                        .description("角色当前不在场景位置")
                        .suggestion("添加角色移动描写或调整场景位置")
                        .severity(Severity.WARNING)
                        .build());
            }
        }

        return issues;
    }

    /**
     * 检查场景逻辑
     */
    private List<InconsistencyReport> checkSceneLogic(PreflightRequest request) {
        if (request.sceneDescription() == null || request.sceneDescription().isBlank()) {
            return Collections.emptyList();
        }

        try {
            ChatModel model = chatModelFactory.getDefaultModel();
            ChatClient chatClient = ChatClient.builder(model).build();
            String response = chatClient.prompt()
                    .system("""
                        你是一个小说逻辑检查助手。
                        检查场景描述是否存在逻辑问题。
                        
                        返回JSON数组格式的问题列表，每项包含：
                        - description: 问题描述
                        - suggestion: 修复建议
                        - severity: 严重程度 (INFO/WARNING/ERROR)
                        
                        如果没有问题，返回空数组 []
                        """)
                    .user(String.format("""
                        ## 场景描述
                        %s
                        
                        ## 参与角色数量
                        %d
                        
                        ## 场景位置
                        %s
                        
                        请检查：
                        1. 场景是否物理上可行
                        2. 角色数量是否合理
                        3. 是否有明显的逻辑漏洞
                        """,
                            request.sceneDescription(),
                            request.involvedCharacterIds().size(),
                            request.sceneLocation()))
                    .call()
                    .content();

            return parseLogicCheckResponse(response);

        } catch (Exception e) {
            log.error("Scene logic check failed", e);
            return Collections.emptyList();
        }
    }

    /**
     * 检查时间线
     */
    private List<InconsistencyReport> checkTimeline(PreflightRequest request) {
        List<InconsistencyReport> issues = new ArrayList<>();

        // 检查章节顺序
        if (request.chapterOrder() <= 0) {
            issues.add(InconsistencyReport.builder()
                    .fieldPath("chapterOrder")
                    .description("章节顺序无效")
                    .severity(Severity.ERROR)
                    .build());
        }

        // 检查时间设定
        if (request.sceneTime() != null && request.previousSceneTime() != null) {
            // 简单的时间顺序检查
            if (request.sceneTime().isBefore(request.previousSceneTime())) {
                issues.add(InconsistencyReport.builder()
                        .fieldPath("sceneTime")
                        .expectedValue(request.previousSceneTime().toString())
                        .actualValue(request.sceneTime().toString())
                        .description("场景时间早于前一场景")
                        .suggestion("调整时间设定或使用闪回标记")
                        .severity(Severity.WARNING)
                        .build());
            }
        }

        return issues;
    }

    /**
     * 解析逻辑检查响应
     */
    private List<InconsistencyReport> parseLogicCheckResponse(String response) {
        try {
            String json = extractJson(response);
            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> rawIssues = objectMapper.readValue(
                    json, new TypeReference<>() {});

            return rawIssues.stream()
                    .map(map -> InconsistencyReport.builder()
                            .description((String) map.get("description"))
                            .suggestion((String) map.get("suggestion"))
                            .severity(parseSeverity((String) map.get("severity")))
                            .build())
                    .toList();

        } catch (Exception e) {
            log.error("Failed to parse logic check response", e);
            return Collections.emptyList();
        }
    }

    private Severity parseSeverity(String value) {
        if (value == null) return Severity.WARNING;
        try {
            return Severity.valueOf(value.toUpperCase());
        } catch (Exception e) {
            return Severity.WARNING;
        }
    }

    private String extractJson(String response) {
        int start = response.indexOf('[');
        int end = response.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return null;
    }

    /**
     * 预检请求
     */
    public record PreflightRequest(
            UUID projectId,
            Integer chapterOrder,
            List<UUID> involvedCharacterIds,
            String sceneDescription,
            String sceneLocation,
            java.time.LocalDateTime sceneTime,
            java.time.LocalDateTime previousSceneTime
    ) {}

    /**
     * 预检结果
     */
    public record PreflightResult(
            boolean canProceed,
            List<InconsistencyReport> issues
    ) {}
}
