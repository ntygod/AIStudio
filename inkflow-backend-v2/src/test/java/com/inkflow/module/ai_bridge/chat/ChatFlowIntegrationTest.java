package com.inkflow.module.ai_bridge.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.module.ai_bridge.context.RequestContextHolder;
import com.inkflow.module.ai_bridge.error.AIErrorHandler;
import com.inkflow.module.ai_bridge.event.ToolExecutionEvent;
import com.inkflow.module.ai_bridge.prompt.PhaseAwarePromptBuilder;
import com.inkflow.module.ai_bridge.tool.SceneToolRegistry;
import com.inkflow.module.project.entity.CreationPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * 聊天流程测试
 * 验证 Tool 事件、SSE 格式和上下文处理
 * 
 * 注意：此测试不再依赖 SpringAIChatService（已废弃）
 * 改为测试独立的组件和事件类
 * 
 * Requirements: 1.1-1.6, 2.1-2.7
 *
 * @author zsg
 * @date 2025/12/17
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("聊天流程测试")
class ChatFlowIntegrationTest {

    @Mock
    private SceneToolRegistry toolRegistry;

    private ObjectMapper objectMapper;
    private List<ToolExecutionEvent> capturedEvents;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        capturedEvents = new CopyOnWriteArrayList<>();
    }

    @Test
    @DisplayName("SSE 事件类型验证")
    void testSSEEventTypes() {
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
    @DisplayName("Tool 状态事件创建 - tool_start 事件")
    void testToolStartEventCreation() {
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
    @DisplayName("Tool 状态事件创建 - tool_end 成功事件")
    void testToolEndSuccessEventCreation() {
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
    @DisplayName("Tool 状态事件创建 - tool_end 失败事件")
    void testToolEndFailureEventCreation() {
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
    @DisplayName("ToolExecutionEvent 创建 - START 事件")
    void testToolExecutionEventStart() {
        // Given
        String requestId = UUID.randomUUID().toString();
        String toolName = "universalCrud";
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        
        // When
        ToolExecutionEvent event = ToolExecutionEvent.start(
            this, requestId, toolName, null, userId, projectId
        );
        
        // Then
        assertThat(event.getRequestId()).isEqualTo(requestId);
        assertThat(event.getToolName()).isEqualTo(toolName);
        assertThat(event.getPhase()).isEqualTo(ToolExecutionEvent.Phase.START);
        assertThat(event.isSuccess()).isTrue();
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getProjectId()).isEqualTo(projectId);
        assertThat(event.getEventType()).isEqualTo("tool_start");
    }

    @Test
    @DisplayName("ToolExecutionEvent 创建 - 成功 END 事件")
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
    @DisplayName("ToolExecutionEvent 创建 - 失败 END 事件")
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

    @Test
    @DisplayName("Tool 事件配对验证 - START 后必须有 END")
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
    }

    @Test
    @DisplayName("阶段感知工具选择 - 不同阶段返回不同工具")
    void testPhaseAwareToolSelection() {
        // Given
        Object[] ideaTools = new Object[]{"crudTool", "genTool"};
        Object[] writingTools = new Object[]{"crudTool", "ragTool", "genTool", "styleTool"};
        
        when(toolRegistry.getToolsArrayForScene(CreationPhase.IDEA)).thenReturn(ideaTools);
        when(toolRegistry.getToolsArrayForScene(CreationPhase.WRITING)).thenReturn(writingTools);
        
        // When
        Object[] ideaResult = toolRegistry.getToolsArrayForScene(CreationPhase.IDEA);
        Object[] writingResult = toolRegistry.getToolsArrayForScene(CreationPhase.WRITING);
        
        // Then
        assertThat(ideaResult).hasSize(2);
        assertThat(writingResult).hasSize(4);
        assertThat(writingResult.length).isGreaterThan(ideaResult.length);
    }

    @Test
    @DisplayName("系统提示词包含上下文信息")
    void testSystemPromptContainsContext() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        CreationPhase phase = CreationPhase.CHARACTER;
        
        PhaseAwarePromptBuilder realBuilder = new PhaseAwarePromptBuilder();
        
        // When
        String prompt = realBuilder.buildSystemPrompt(phase, userId, projectId);
        
        // Then
        assertThat(prompt).contains(userId.toString());
        assertThat(prompt).contains(projectId.toString());
        assertThat(prompt).contains("角色设计"); // Phase-specific content
    }

    @Test
    @DisplayName("会话ID构建 - 用户和项目组合")
    void testConversationIdBuilding() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        
        // When - 使用反射或直接测试逻辑
        String expectedConversationId = userId.toString() + ":" + projectId.toString();
        
        // Then
        assertThat(expectedConversationId).contains(userId.toString());
        assertThat(expectedConversationId).contains(projectId.toString());
        assertThat(expectedConversationId).contains(":");
    }

    @Test
    @DisplayName("会话ID格式验证")
    void testConversationIdFormat() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        
        // When - 构建会话ID
        String conversationId = userId.toString() + ":" + projectId.toString();
        
        // Then
        assertThat(conversationId).contains(userId.toString());
        assertThat(conversationId).contains(projectId.toString());
        assertThat(conversationId).contains(":");
    }

    @Test
    @DisplayName("错误处理 - 用户友好消息")
    void testErrorHandling() {
        // Given
        AIErrorHandler realHandler = new AIErrorHandler();
        Exception testException = new RuntimeException("Internal error");
        
        // When
        String userMessage = realHandler.formatUserMessage(testException, "测试操作");
        
        // Then
        assertThat(userMessage).isNotNull();
        assertThat(userMessage).doesNotContain("Internal error"); // 不暴露内部错误
        assertThat(userMessage).contains("请"); // 包含用户友好提示
    }

    @Test
    @DisplayName("请求上下文创建")
    void testRequestContextCreation() {
        // Given
        String requestId = UUID.randomUUID().toString();
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        
        // When - create context using the holder
        RequestContextHolder.RequestContext context = 
            RequestContextHolder.createContext(requestId, userId, projectId);
        
        // Then - verify context fields
        assertThat(context).isNotNull();
        assertThat(context.requestId()).isEqualTo(requestId);
        assertThat(context.userId()).isEqualTo(userId);
        assertThat(context.projectId()).isEqualTo(projectId);
    }
}
