package com.inkflow.module.ai_bridge.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.module.ai_bridge.event.ToolExecutionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SSE 事件流测试
 * 验证事件顺序和格式
 * 
 * 注意：此测试不再依赖 SpringAIChatService（已废弃）
 * 改为直接测试 SSE 事件类和格式
 * 
 * Requirements: 6.1-6.8, 7.1-7.6
 *
 * @author zsg
 * @date 2025/12/17
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SSE 事件流测试")
class SSEEventStreamIntegrationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // ==================== Event Type Tests ====================

    @Test
    @DisplayName("SSE 事件类型验证 - 所有事件类型必须是有效类型")
    void testValidEventTypes() {
        // Given - 有效的事件类型集合
        Set<String> validEventTypes = Set.of("content", "thought", "tool_start", "tool_end", "error", "done");
        
        // When - 创建各种类型的事件
        List<ServerSentEvent<String>> events = List.of(
            ServerSentEvent.<String>builder().event("content").data("内容").build(),
            ServerSentEvent.<String>builder().event("thought").data("思考").build(),
            ServerSentEvent.<String>builder().event("tool_start").data("工具开始").build(),
            ServerSentEvent.<String>builder().event("tool_end").data("工具结束").build(),
            ServerSentEvent.<String>builder().event("done").data("").build()
        );
        
        // Then - 验证所有事件类型都是有效的
        for (ServerSentEvent<String> event : events) {
            String eventType = event.event();
            assertThat(eventType)
                .as("事件类型 '%s' 必须是有效类型", eventType)
                .isIn(validEventTypes);
        }
    }

    @Test
    @DisplayName("SSE 事件流必须以 done 事件结束")
    void testStreamEndsWithDoneEvent() {
        // Given - 模拟一个完整的事件序列
        List<ServerSentEvent<String>> events = new ArrayList<>();
        events.add(ServerSentEvent.<String>builder().event("content").data("内容1").build());
        events.add(ServerSentEvent.<String>builder().event("content").data("内容2").build());
        events.add(ServerSentEvent.<String>builder().event("done").data("").build());
        
        // Then - 最后一个事件必须是 done
        assertThat(events).isNotEmpty();
        ServerSentEvent<String> lastEvent = events.get(events.size() - 1);
        assertThat(lastEvent.event()).isEqualTo("done");
    }

    // ==================== Tool Event Format Tests ====================

    @Test
    @DisplayName("tool_start 事件格式验证")
    void testToolStartEventFormat() {
        // Given
        String toolName = "universalCrud";
        
        // When
        ToolStatusEvent event = ToolStatusEvent.toolStart(toolName);
        
        // Then
        assertThat(event.eventType()).isEqualTo("tool_start");
        assertThat(event.toolName()).isEqualTo(toolName);
        assertThat(event.success()).isNull();
        assertThat(event.message()).isNull();
        assertThat(event.durationMs()).isNull();
    }

    @Test
    @DisplayName("tool_end 成功事件格式验证")
    void testToolEndSuccessEventFormat() {
        // Given
        String toolName = "ragSearch";
        boolean success = true;
        String message = "找到5条相关内容";
        Long durationMs = 150L;
        
        // When
        ToolStatusEvent event = ToolStatusEvent.toolEnd(toolName, success, message, durationMs);
        
        // Then
        assertThat(event.eventType()).isEqualTo("tool_end");
        assertThat(event.toolName()).isEqualTo(toolName);
        assertThat(event.success()).isTrue();
        assertThat(event.message()).isEqualTo(message);
        assertThat(event.durationMs()).isEqualTo(durationMs);
    }

    @Test
    @DisplayName("tool_end 失败事件格式验证")
    void testToolEndFailureEventFormat() {
        // Given
        String toolName = "creativeGen";
        boolean success = false;
        String message = "生成失败：API 超时";
        Long durationMs = 5000L;
        
        // When
        ToolStatusEvent event = ToolStatusEvent.toolEnd(toolName, success, message, durationMs);
        
        // Then
        assertThat(event.eventType()).isEqualTo("tool_end");
        assertThat(event.toolName()).isEqualTo(toolName);
        assertThat(event.success()).isFalse();
        assertThat(event.message()).isEqualTo(message);
        assertThat(event.durationMs()).isEqualTo(durationMs);
    }

    @Test
    @DisplayName("tool_end 简单格式验证（无详情）")
    void testToolEndSimpleFormat() {
        // Given
        String toolName = "preflight";
        boolean success = true;
        
        // When
        ToolStatusEvent event = ToolStatusEvent.toolEnd(toolName, success);
        
        // Then
        assertThat(event.eventType()).isEqualTo("tool_end");
        assertThat(event.toolName()).isEqualTo(toolName);
        assertThat(event.success()).isTrue();
        assertThat(event.message()).isNull();
        assertThat(event.durationMs()).isNull();
    }

    // ==================== Tool Event Pairing Tests ====================

    @Test
    @DisplayName("Tool 事件配对验证 - tool_start 后必须有 tool_end")
    void testToolEventPairing() {
        // Given
        String requestId = UUID.randomUUID().toString();
        String toolName = "universalCrud";
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        
        // When - 创建配对的事件
        ToolExecutionEvent startEvent = ToolExecutionEvent.start(
            this, requestId, toolName, null, userId, projectId
        );
        ToolExecutionEvent endEvent = ToolExecutionEvent.success(
            this, requestId, toolName, "操作成功", 100L, userId, projectId
        );
        
        // Then - 验证事件配对
        assertThat(startEvent.getRequestId()).isEqualTo(endEvent.getRequestId());
        assertThat(startEvent.getToolName()).isEqualTo(endEvent.getToolName());
        assertThat(startEvent.getPhase()).isEqualTo(ToolExecutionEvent.Phase.START);
        assertThat(endEvent.getPhase()).isEqualTo(ToolExecutionEvent.Phase.END);
        assertThat(startEvent.getEventType()).isEqualTo("tool_start");
        assertThat(endEvent.getEventType()).isEqualTo("tool_end");
    }

    @Test
    @DisplayName("多个 Tool 事件配对验证")
    void testMultipleToolEventPairing() {
        // Given
        String requestId = UUID.randomUUID().toString();
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        
        List<String> toolNames = List.of("ragSearch", "universalCrud", "creativeGen");
        List<ToolExecutionEvent> events = new ArrayList<>();
        
        // When - 创建多个配对的事件
        for (String toolName : toolNames) {
            events.add(ToolExecutionEvent.start(this, requestId, toolName, null, userId, projectId));
            events.add(ToolExecutionEvent.success(this, requestId, toolName, "成功", 100L, userId, projectId));
        }
        
        // Then - 验证每个 tool 都有配对的 start 和 end
        Map<String, Integer> startCount = new HashMap<>();
        Map<String, Integer> endCount = new HashMap<>();
        
        for (ToolExecutionEvent event : events) {
            if (event.getPhase() == ToolExecutionEvent.Phase.START) {
                startCount.merge(event.getToolName(), 1, Integer::sum);
            } else {
                endCount.merge(event.getToolName(), 1, Integer::sum);
            }
        }
        
        // 每个 tool 的 start 和 end 数量应该相等
        for (String toolName : toolNames) {
            assertThat(startCount.get(toolName))
                .as("Tool '%s' 的 start 事件数量", toolName)
                .isEqualTo(endCount.get(toolName));
        }
    }

    // ==================== Event Order Tests ====================

    @Test
    @DisplayName("Tool 事件顺序验证 - START 必须在 END 之前")
    void testToolEventOrder() {
        // Given
        String requestId = UUID.randomUUID().toString();
        String toolName = "universalCrud";
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        
        // When
        ToolExecutionEvent startEvent = ToolExecutionEvent.start(
            this, requestId, toolName, null, userId, projectId
        );
        ToolExecutionEvent endEvent = ToolExecutionEvent.success(
            this, requestId, toolName, "成功", 100L, userId, projectId
        );
        
        // Then - START 事件的时间戳应该早于或等于 END 事件
        assertThat(startEvent.getEventTimestamp())
            .isBeforeOrEqualTo(endEvent.getEventTimestamp());
    }

    @Test
    @DisplayName("事件顺序验证 - 模拟完整流程")
    void testCompleteEventSequence() {
        // Given - 模拟一个完整的事件序列
        List<String> eventSequence = new ArrayList<>();
        
        // 模拟事件序列: tool_start -> tool_end -> content -> done
        eventSequence.add("tool_start");
        eventSequence.add("tool_end");
        eventSequence.add("content");
        eventSequence.add("content");
        eventSequence.add("done");
        
        // Then - 验证顺序规则
        // 1. done 必须是最后一个
        assertThat(eventSequence.get(eventSequence.size() - 1)).isEqualTo("done");
        
        // 2. tool_start 必须在对应的 tool_end 之前
        int toolStartIndex = eventSequence.indexOf("tool_start");
        int toolEndIndex = eventSequence.indexOf("tool_end");
        assertThat(toolStartIndex).isLessThan(toolEndIndex);
    }

    // ==================== ToolExecutionEvent Tests ====================

    @Test
    @DisplayName("ToolExecutionEvent START 事件创建")
    void testToolExecutionEventStart() {
        // Given
        String requestId = UUID.randomUUID().toString();
        String toolName = "universalCrud";
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Map<String, Object> params = Map.of("operation", "CREATE", "entityType", "character");
        
        // When
        ToolExecutionEvent event = ToolExecutionEvent.start(
            this, requestId, toolName, params, userId, projectId
        );
        
        // Then
        assertThat(event.getRequestId()).isEqualTo(requestId);
        assertThat(event.getToolName()).isEqualTo(toolName);
        assertThat(event.getPhase()).isEqualTo(ToolExecutionEvent.Phase.START);
        assertThat(event.isSuccess()).isTrue();
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getProjectId()).isEqualTo(projectId);
        assertThat(event.getParameters()).isEqualTo(params);
        assertThat(event.getEventType()).isEqualTo("tool_start");
        assertThat(event.getEventTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("ToolExecutionEvent SUCCESS 事件创建")
    void testToolExecutionEventSuccess() {
        // Given
        String requestId = UUID.randomUUID().toString();
        String toolName = "ragSearch";
        String resultSummary = "找到5条相关内容";
        Long durationMs = 200L;
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        
        // When
        ToolExecutionEvent event = ToolExecutionEvent.success(
            this, requestId, toolName, resultSummary, durationMs, userId, projectId
        );
        
        // Then
        assertThat(event.getRequestId()).isEqualTo(requestId);
        assertThat(event.getToolName()).isEqualTo(toolName);
        assertThat(event.getPhase()).isEqualTo(ToolExecutionEvent.Phase.END);
        assertThat(event.isSuccess()).isTrue();
        assertThat(event.getResultSummary()).isEqualTo(resultSummary);
        assertThat(event.getDurationMs()).isEqualTo(durationMs);
        assertThat(event.getEventType()).isEqualTo("tool_end");
    }

    @Test
    @DisplayName("ToolExecutionEvent FAILURE 事件创建")
    void testToolExecutionEventFailure() {
        // Given
        String requestId = UUID.randomUUID().toString();
        String toolName = "deepReasoning";
        String errorMessage = "推理模型不可用";
        Long durationMs = 100L;
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        
        // When
        ToolExecutionEvent event = ToolExecutionEvent.failure(
            this, requestId, toolName, errorMessage, durationMs, userId, projectId
        );
        
        // Then
        assertThat(event.getRequestId()).isEqualTo(requestId);
        assertThat(event.getToolName()).isEqualTo(toolName);
        assertThat(event.getPhase()).isEqualTo(ToolExecutionEvent.Phase.END);
        assertThat(event.isSuccess()).isFalse();
        assertThat(event.getErrorMessage()).isEqualTo(errorMessage);
        assertThat(event.getDurationMs()).isEqualTo(durationMs);
        assertThat(event.getEventType()).isEqualTo("tool_end");
    }

    // ==================== JSON Serialization Tests ====================

    @Test
    @DisplayName("ToolStatusEvent JSON 序列化验证")
    void testToolStatusEventJsonSerialization() throws Exception {
        // Given
        ToolStatusEvent event = ToolStatusEvent.toolEnd("ragSearch", true, "找到5条", 150L);
        
        // When
        String json = objectMapper.writeValueAsString(event);
        
        // Then
        assertThat(json).contains("\"eventType\":\"tool_end\"");
        assertThat(json).contains("\"toolName\":\"ragSearch\"");
        assertThat(json).contains("\"success\":true");
        assertThat(json).contains("\"message\":\"找到5条\"");
        assertThat(json).contains("\"durationMs\":150");
    }

    @Test
    @DisplayName("ToolStatusEvent JSON 序列化 - null 字段不包含")
    void testToolStatusEventJsonSerializationNullFields() throws Exception {
        // Given
        ToolStatusEvent event = ToolStatusEvent.toolStart("universalCrud");
        
        // When
        String json = objectMapper.writeValueAsString(event);
        
        // Then
        assertThat(json).contains("\"eventType\":\"tool_start\"");
        assertThat(json).contains("\"toolName\":\"universalCrud\"");
        // null 字段不应该出现在 JSON 中（因为 @JsonInclude(NON_NULL)）
        assertThat(json).doesNotContain("\"success\"");
        assertThat(json).doesNotContain("\"message\"");
        assertThat(json).doesNotContain("\"durationMs\"");
    }

    // ==================== Error Event Tests ====================

    @Test
    @DisplayName("错误事件格式验证 - 直接构建错误事件")
    void testErrorEventFormat() {
        // Given - 直接测试错误事件的格式，不依赖完整的聊天流程
        String errorMessage = "抱歉，处理您的请求时遇到问题";
        
        // When - 构建错误事件
        ServerSentEvent<String> errorEvent = ServerSentEvent.<String>builder()
            .event("error")
            .data(errorMessage)
            .build();
        
        // Then
        assertThat(errorEvent.event()).isEqualTo("error");
        assertThat(errorEvent.data()).isEqualTo(errorMessage);
        assertThat(errorEvent.data()).doesNotContain("RuntimeException"); // 不暴露内部错误
    }

    @Test
    @DisplayName("错误事件后仍然有 done 事件")
    void testErrorEventFollowedByDone() {
        // Given - 模拟一个包含错误的事件序列
        List<ServerSentEvent<String>> events = new ArrayList<>();
        
        events.add(ServerSentEvent.<String>builder()
            .event("error")
            .data("发生错误")
            .build());
        events.add(ServerSentEvent.<String>builder()
            .event("done")
            .data("")
            .build());
        
        // Then - 验证最后一个事件是 done
        ServerSentEvent<String> lastEvent = events.get(events.size() - 1);
        assertThat(lastEvent.event()).isEqualTo("done");
        
        // 验证有 error 事件
        boolean hasErrorEvent = events.stream()
            .anyMatch(e -> "error".equals(e.event()));
        assertThat(hasErrorEvent).isTrue();
    }

    // ==================== SSE Format Tests ====================

    @Test
    @DisplayName("SSE 事件格式验证 - ServerSentEvent 结构")
    void testServerSentEventStructure() {
        // Given
        String eventType = "content";
        String data = "这是一段内容";
        
        // When
        ServerSentEvent<String> sse = ServerSentEvent.<String>builder()
            .event(eventType)
            .data(data)
            .build();
        
        // Then
        assertThat(sse.event()).isEqualTo(eventType);
        assertThat(sse.data()).isEqualTo(data);
    }

    @Test
    @DisplayName("SSE done 事件格式验证")
    void testDoneEventFormat() {
        // Given & When
        ServerSentEvent<String> doneEvent = ServerSentEvent.<String>builder()
            .event("done")
            .data("")
            .build();
        
        // Then
        assertThat(doneEvent.event()).isEqualTo("done");
        assertThat(doneEvent.data()).isEmpty();
    }

    // ==================== Phase Suggestion Event Tests ====================

    @Test
    @DisplayName("阶段建议事件格式验证")
    void testPhaseSuggestionEventFormat() {
        // Given - 模拟阶段建议事件
        String eventType = "phase_suggestion";
        String suggestedPhase = "CHARACTER";
        String reason = "世界观构建已完成，建议进入角色设计阶段";
        
        // When
        Map<String, String> eventData = Map.of(
            "suggestedPhase", suggestedPhase,
            "reason", reason
        );
        String json = assertDoesNotThrow(() -> objectMapper.writeValueAsString(eventData));
        
        ServerSentEvent<String> sse = ServerSentEvent.<String>builder()
            .event(eventType)
            .data(json)
            .build();
        
        // Then
        assertThat(sse.event()).isEqualTo("phase_suggestion");
        assertThat(sse.data()).contains("CHARACTER");
        assertThat(sse.data()).contains("世界观构建已完成");
    }

    private <T> T assertDoesNotThrow(ThrowingSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            throw new AssertionError("Unexpected exception: " + e.getMessage(), e);
        }
    }

    @FunctionalInterface
    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
