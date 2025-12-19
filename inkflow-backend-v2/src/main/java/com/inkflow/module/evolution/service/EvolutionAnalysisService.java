package com.inkflow.module.evolution.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.module.ai_bridge.chat.DynamicChatModelFactory;
import com.inkflow.module.evolution.dto.StateChange;
import com.inkflow.module.evolution.entity.*;
import com.inkflow.module.evolution.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * 演进分析服务
 * AI驱动的角色/设定状态变化分析
 */
@Service
public class EvolutionAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(EvolutionAnalysisService.class);

    private final DynamicChatModelFactory chatModelFactory;
    private final StateSnapshotService snapshotService;
    private final EvolutionTimelineRepository timelineRepository;
    private final ObjectMapper objectMapper;

    public EvolutionAnalysisService(
            DynamicChatModelFactory chatModelFactory,
            StateSnapshotService snapshotService,
            EvolutionTimelineRepository timelineRepository,
            ObjectMapper objectMapper) {
        this.chatModelFactory = chatModelFactory;
        this.snapshotService = snapshotService;
        this.timelineRepository = timelineRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 分析章节内容，提取角色状态变化
     */
    @Transactional
    public List<StateChange> analyzeChapterForEvolution(
            UUID projectId, UUID chapterId, Integer chapterOrder, String chapterContent,
            UUID entityId, EntityType entityType, Map<String, Object> currentEntityState) {

        try {
            // 获取或创建时间线
            EvolutionTimeline timeline = getOrCreateTimeline(projectId, entityType, entityId);

            // 获取之前的状态
            Optional<Map<String, Object>> previousStateOpt = snapshotService
                    .getStateAtChapter(entityType, entityId, chapterOrder - 1);

            // 使用AI分析状态变化
            List<StateChange> changes = analyzeWithAI(
                    chapterContent, currentEntityState,
                    previousStateOpt.orElse(Collections.emptyMap()), entityType);

            // 创建快照
            if (!changes.isEmpty() || previousStateOpt.isEmpty()) {
                snapshotService.createSnapshot(
                        timeline.getId(),
                        chapterId,
                        chapterOrder,
                        currentEntityState,
                        changes,
                        BigDecimal.valueOf(0.85) // AI置信度
                );
            }

            return changes;

        } catch (Exception e) {
            log.error("Failed to analyze chapter for evolution", e);
            return Collections.emptyList();
        }
    }

    /**
     * 批量分析多个实体的演进
     */
    public CompletableFuture<Map<UUID, List<StateChange>>> analyzeMultipleEntities(
            UUID projectId, UUID chapterId, Integer chapterOrder, String chapterContent,
            Map<UUID, EntityInfo> entities) {

        var executor = Executors.newVirtualThreadPerTaskExecutor();

        List<CompletableFuture<Map.Entry<UUID, List<StateChange>>>> futures = entities.entrySet()
                .stream()
                .map(entry -> CompletableFuture.supplyAsync(() -> {
                    List<StateChange> changes = analyzeChapterForEvolution(
                            projectId, chapterId, chapterOrder, chapterContent,
                            entry.getKey(), entry.getValue().entityType(),
                            entry.getValue().currentState());
                    return Map.entry(entry.getKey(), changes);
                }, executor))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(HashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), HashMap::putAll));
    }

    /**
     * 使用AI分析状态变化
     */
    private List<StateChange> analyzeWithAI(
            String chapterContent,
            Map<String, Object> currentState,
            Map<String, Object> previousState,
            EntityType entityType) {

        String prompt = buildAnalysisPrompt(chapterContent, currentState, previousState, entityType);

        try {
            ChatModel model = chatModelFactory.getDefaultModel();
            ChatClient chatClient = ChatClient.builder(model).build();
            String response = chatClient.prompt()
                    .system("""
                        你是一个专业的小说分析助手，负责分析章节内容中角色或设定的状态变化。
                        请以JSON数组格式返回变化列表，每个变化包含：
                        - fieldPath: 变化的字段路径
                        - oldValue: 旧值
                        - newValue: 新值
                        - changeReason: 变化原因
                        - sourceText: 触发变化的原文片段
                        
                        如果没有变化，返回空数组 []
                        """)
                    .user(prompt)
                    .call()
                    .content();

            return parseAIResponse(response);

        } catch (Exception e) {
            log.error("AI analysis failed", e);
            return Collections.emptyList();
        }
    }

    /**
     * 构建分析提示词
     */
    private String buildAnalysisPrompt(
            String chapterContent,
            Map<String, Object> currentState,
            Map<String, Object> previousState,
            EntityType entityType) {

        return String.format("""
            ## 分析任务
            分析以下章节内容，识别%s的状态变化。
            
            ## 当前状态
            ```json
            %s
            ```
            
            ## 之前状态
            ```json
            %s
            ```
            
            ## 章节内容
            %s
            
            ## 要求
            1. 识别状态字段的变化（如status, mood, relationships等）
            2. 提取触发变化的原文片段
            3. 分析变化原因
            4. 返回JSON数组格式
            """,
                entityType.getValue(),
                toJson(currentState),
                toJson(previousState),
                chapterContent.length() > 3000 ? chapterContent.substring(0, 3000) + "..." : chapterContent
        );
    }

    /**
     * 解析AI响应
     */
    private List<StateChange> parseAIResponse(String response) {
        try {
            // 提取JSON部分
            String json = extractJson(response);
            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> rawChanges = objectMapper.readValue(
                    json, new TypeReference<>() {});

            return rawChanges.stream()
                    .map(map -> new StateChange(
                            (String) map.get("fieldPath"),
                            map.get("oldValue"),
                            map.get("newValue"),
                            (String) map.get("changeReason"),
                            (String) map.get("sourceText")
                    ))
                    .toList();

        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", response, e);
            return Collections.emptyList();
        }
    }

    /**
     * 从响应中提取JSON
     */
    private String extractJson(String response) {
        int start = response.indexOf('[');
        int end = response.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return null;
    }

    /**
     * 获取或创建时间线
     */
    private EvolutionTimeline getOrCreateTimeline(UUID projectId, EntityType entityType, UUID entityId) {
        return timelineRepository.findByEntityTypeAndEntityId(entityType, entityId)
                .orElseGet(() -> {
                    EvolutionTimeline timeline = new EvolutionTimeline(projectId, entityType, entityId);
                    return timelineRepository.save(timeline);
                });
    }

    /**
     * 将对象序列化为JSON字符串
     * 当序列化失败时，返回包含错误信息的JSON对象而非空对象
     * 
     * @param obj 要序列化的对象
     * @return JSON字符串，失败时返回包含error字段的JSON
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("JSON serialization failed for object of type: {}", 
                    obj != null ? obj.getClass().getName() : "null", e);
            return String.format("{\"error\":\"JSON serialization failed\",\"errorType\":\"%s\",\"errorMessage\":\"%s\"}", 
                    e.getClass().getSimpleName(), 
                    escapeJsonString(e.getMessage()));
        }
    }
    
    /**
     * 转义JSON字符串中的特殊字符
     */
    private String escapeJsonString(String input) {
        if (input == null) {
            return "null";
        }
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    /**
     * 为实体创建演进快照（便捷方法）
     * 用于从事件监听器调用，简化接口
     * 
     * Requirements: 6.1 - 章节保存时自动创建状态快照
     * 
     * @param projectId 项目ID
     * @param entityId 实体ID
     * @param entityType 实体类型
     * @param currentState 当前状态
     */
    @Transactional
    public void createSnapshotForEntity(UUID projectId, UUID entityId, EntityType entityType, 
                                        Map<String, Object> currentState) {
        try {
            // 获取或创建时间线
            EvolutionTimeline timeline = getOrCreateTimeline(projectId, entityType, entityId);
            
            // 创建快照（不关联具体章节，使用当前时间作为顺序）
            // 这种快照用于记录实体本身的变更，而非章节中的状态
            snapshotService.createSnapshot(
                    timeline.getId(),
                    null, // 不关联章节
                    0,    // 章节顺序为0表示非章节关联的快照
                    currentState,
                    Collections.emptyList(), // 无具体变更记录
                    BigDecimal.valueOf(1.0)  // 直接变更，置信度为1
            );
            
            log.info("Created evolution snapshot for entity: type={}, id={}", entityType, entityId);
        } catch (Exception e) {
            log.error("Failed to create evolution snapshot for entity: type={}, id={}", 
                    entityType, entityId, e);
        }
    }

    /**
     * 实体信息记录
     */
    public record EntityInfo(EntityType entityType, Map<String, Object> currentState) {}
}
