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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * 一致性检查服务
 * 检测设定冲突和逻辑矛盾
 */
@Service
public class ConsistencyCheckService {

    private static final Logger log = LoggerFactory.getLogger(ConsistencyCheckService.class);

    private final DynamicChatModelFactory chatModelFactory;
    private final StateRetrievalService stateRetrievalService;
    private final ObjectMapper objectMapper;

    public ConsistencyCheckService(
            DynamicChatModelFactory chatModelFactory,
            StateRetrievalService stateRetrievalService,
            ObjectMapper objectMapper) {
        this.chatModelFactory = chatModelFactory;
        this.stateRetrievalService = stateRetrievalService;
        this.objectMapper = objectMapper;
    }

    /**
     * 检查章节内容与设定的一致性
     */
    public List<InconsistencyReport> checkConsistency(
            UUID projectId,
            UUID chapterId,
            Integer chapterOrder,
            String chapterContent,
            List<EntityReference> referencedEntities) {

        var executor = Executors.newVirtualThreadPerTaskExecutor();

        // 并行检查每个实体
        List<CompletableFuture<List<InconsistencyReport>>> futures = referencedEntities.stream()
                .map(ref -> CompletableFuture.supplyAsync(() ->
                        checkEntityConsistency(ref, chapterOrder, chapterContent), executor))
                .toList();

        // 等待所有检查完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 合并结果
        return futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .toList();
    }

    /**
     * 检查单个实体的一致性
     */
    private List<InconsistencyReport> checkEntityConsistency(
            EntityReference ref,
            Integer chapterOrder,
            String chapterContent) {

        try {
            // 获取实体在该章节时的状态
            Optional<Map<String, Object>> stateOpt = stateRetrievalService
                    .getStateAtChapter(ref.entityType(), ref.entityId(), chapterOrder);

            if (stateOpt.isEmpty()) {
                return Collections.emptyList();
            }

            Map<String, Object> expectedState = stateOpt.get();

            // 使用AI检查一致性
            return checkWithAI(ref, expectedState, chapterContent);

        } catch (Exception e) {
            log.error("Failed to check consistency for entity {}", ref.entityId(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 使用AI检查一致性
     */
    private List<InconsistencyReport> checkWithAI(
            EntityReference ref,
            Map<String, Object> expectedState,
            String chapterContent) {

        String prompt = buildCheckPrompt(ref, expectedState, chapterContent);

        try {
            ChatModel model = chatModelFactory.getDefaultModel();
            ChatClient chatClient = ChatClient.builder(model).build();
            String response = chatClient.prompt()
                    .system("""
                        你是一个专业的小说一致性检查助手。
                        请检查章节内容是否与设定状态一致。
                        
                        返回JSON数组格式的不一致项，每项包含：
                        - fieldPath: 不一致的字段
                        - expectedValue: 设定中的值
                        - actualValue: 章节中描述的值
                        - description: 不一致描述
                        - suggestion: 修复建议
                        - severity: 严重程度 (INFO/WARNING/ERROR)
                        
                        如果没有不一致，返回空数组 []
                        """)
                    .user(prompt)
                    .call()
                    .content();

            return parseCheckResponse(response, ref);

        } catch (Exception e) {
            log.error("AI consistency check failed", e);
            return Collections.emptyList();
        }
    }

    /**
     * 构建检查提示词
     */
    private String buildCheckPrompt(
            EntityReference ref,
            Map<String, Object> expectedState,
            String chapterContent) {

        return String.format("""
            ## 检查任务
            检查章节内容是否与%s「%s」的设定一致。
            
            ## 设定状态
            ```json
            %s
            ```
            
            ## 章节内容
            %s
            
            ## 检查要点
            1. 角色性格是否与设定一致
            2. 角色关系是否正确
            3. 角色状态（如受伤、情绪等）是否连贯
            4. 世界观设定是否被违反
            5. 时间线是否合理
            """,
                ref.entityType().getValue(),
                ref.entityName(),
                toJson(expectedState),
                chapterContent.length() > 3000 ? chapterContent.substring(0, 3000) + "..." : chapterContent
        );
    }

    /**
     * 解析检查响应
     */
    private List<InconsistencyReport> parseCheckResponse(String response, EntityReference ref) {
        try {
            String json = extractJson(response);
            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> rawReports = objectMapper.readValue(
                    json, new TypeReference<>() {});

            return rawReports.stream()
                    .map(map -> InconsistencyReport.builder()
                            .entityId(ref.entityId())
                            .entityType(ref.entityType())
                            .entityName(ref.entityName())
                            .fieldPath((String) map.get("fieldPath"))
                            .expectedValue((String) map.get("expectedValue"))
                            .actualValue((String) map.get("actualValue"))
                            .description((String) map.get("description"))
                            .suggestion((String) map.get("suggestion"))
                            .severity(parseSeverity((String) map.get("severity")))
                            .build())
                    .toList();

        } catch (Exception e) {
            log.error("Failed to parse check response: {}", response, e);
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
     * 实体引用记录
     */
    public record EntityReference(
            UUID entityId,
            EntityType entityType,
            String entityName
    ) {}
}
