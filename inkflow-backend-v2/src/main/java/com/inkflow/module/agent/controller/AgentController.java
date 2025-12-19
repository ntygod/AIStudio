package com.inkflow.module.agent.controller;

import com.inkflow.module.agent.core.AgentCapability;
import com.inkflow.module.agent.core.Intent;
import com.inkflow.module.agent.dto.ChatRequest;
import com.inkflow.module.agent.dto.ChatRequestDto;
import com.inkflow.module.agent.dto.ChatResponseDto;
import com.inkflow.module.agent.lazy.LazyExecutionManager;
import com.inkflow.module.agent.orchestration.AgentOrchestrator;
import com.inkflow.module.agent.routing.AgentRouter;
import com.inkflow.module.agent.service.RequestAdapterService;
import com.inkflow.module.agent.tool.ToolRegistry;
import com.inkflow.module.agent.workflow.WorkflowExecutor;
import com.inkflow.module.auth.entity.User;
import com.inkflow.module.project.entity.CreationPhase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent API 控制器
 * 提供统一的 Agent 交互接口
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/agent")
@RequiredArgsConstructor
@Tag(name = "Agent API", description = "统一 Agent 交互接口")
public class AgentController {

    private final AgentRouter agentRouter;
    private final AgentOrchestrator agentOrchestrator;
    private final WorkflowExecutor workflowExecutor;
    private final LazyExecutionManager lazyExecutionManager;
    private final ToolRegistry toolRegistry;
    private final RequestAdapterService requestAdapterService;

    // ========== 核心聊天端点 ==========

    /**
     * 统一聊天接口（流式 SSE）
     * 
     * 根据请求内容自动判断：
     * - 普通聊天：通过 AgentRouter 路由到合适的工作流
     * - 场景创作：通过 WorkflowExecutor 执行 ContentGenerationWorkflow
     * 
     * 场景创作触发条件（满足任一）：
     * - sceneType 不为空
     * - chapterId 不为空
     * - characterIds 不为空
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式聊天", description = "统一聊天接口，支持普通聊天和场景创作")
    public Flux<ServerSentEvent<String>> chat(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChatRequestDto request) {
        
        UUID userId = user != null ? user.getId() : null;
        ChatRequest agentRequest = requestAdapterService.adapt(request, userId);
        
        log.info("[AgentController] 请求: projectId={}, sessionId={}, isScene={}, consistency={}", 
                agentRequest.projectId(), agentRequest.sessionId(), 
                request.isSceneCreation(), request.consistencyEnabled());
        
        // 根据请求类型选择执行路径
        if (request.isSceneCreation()) {
            // 场景创作通过 WorkflowExecutor 执行 ContentGenerationWorkflow
            return executeContentGeneration(agentRequest);
        }
        // 普通聊天通过 AgentRouter 路由（AgentRouter 内部委托给 WorkflowExecutor）
        return agentRouter.route(agentRequest);
    }

    /**
     * 非流式聊天接口
     *
     */
    @PostMapping("/chat/simple")
    @Operation(summary = "非流式聊天", description = "发送消息并获取完整响应")
    public ChatResponseDto chatSimple(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChatRequestDto request) {
        
        UUID userId = user != null ? user.getId() : null;
        ChatRequest agentRequest = requestAdapterService.adapt(request, userId);
        
        log.info("[AgentController] 简单聊天: projectId={}, sessionId={}", 
                agentRequest.projectId(), agentRequest.sessionId());
        
        // 收集流式响应
        Flux<ServerSentEvent<String>> flux = request.isSceneCreation()
            ? executeContentGeneration(agentRequest)
            : agentRouter.route(agentRequest);
        
        StringBuilder content = new StringBuilder();
        flux.filter(event -> "content".equals(event.event()))
            .map(ServerSentEvent::data)
            .doOnNext(content::append)
            .blockLast();
        
        return new ChatResponseDto(
            content.toString(),
            agentRequest.sessionId(),
            agentRequest.currentPhase() != null ? agentRequest.currentPhase().name() : null
        );
    }

    /**
     * 执行内容生成工作流
     * 
     * ContentGenerationWorkflow 包含：
     * - 并行预处理（RAG + 角色状态 + 风格样本）
     * - Skill 注入
     * - WriterAgent 执行
     * - 同步一致性检查
     */
    private Flux<ServerSentEvent<String>> executeContentGeneration(ChatRequest request) {
        return workflowExecutor.execute(Intent.WRITE_CONTENT, request)
            .onErrorResume(e -> {
                log.error("[AgentController] 内容生成失败", e);
                return Flux.just(
                    ServerSentEvent.<String>builder().event("error").data(e.getMessage()).build(),
                    ServerSentEvent.<String>builder().event("done").data("[DONE]").build()
                );
            });
    }

    // ========== Agent 能力端点 ==========

    @GetMapping("/capabilities")
    @Operation(summary = "获取 Agent 能力")
    public ResponseEntity<List<AgentCapability>> getCapabilities() {
        return ResponseEntity.ok(agentRouter.getCapabilities());
    }

    @GetMapping("/capabilities/phase/{phase}")
    @Operation(summary = "获取阶段 Agent 能力")
    public ResponseEntity<List<AgentCapability>> getCapabilitiesForPhase(@PathVariable CreationPhase phase) {
        return ResponseEntity.ok(
            agentRouter.getAgentsForPhase(phase).stream()
                .map(agent -> agent.getCapability())
                .toList()
        );
    }

    // ========== 懒执行 Agent 端点 ==========

    @GetMapping("/lazy-agents")
    @Operation(summary = "获取懒执行 Agent")
    public ResponseEntity<Map<String, String>> getLazyAgents() {
        return ResponseEntity.ok(lazyExecutionManager.getAgentCapabilities());
    }

    @PostMapping("/lazy-agents/{agentName}/execute")
    @Operation(summary = "触发懒执行 Agent")
    public ResponseEntity<String> triggerLazyAgent(
            @PathVariable String agentName,
            @Valid @RequestBody ChatRequest request) {
        
        if (!lazyExecutionManager.isRegistered(agentName)) {
            return ResponseEntity.badRequest().body("未找到懒执行 Agent: " + agentName);
        }
        return ResponseEntity.ok(lazyExecutionManager.triggerExecution(agentName, request));
    }

    // ========== 工具端点 ==========

    @GetMapping("/tools")
    @Operation(summary = "获取工具列表")
    public ResponseEntity<List<ToolSummary>> getTools() {
        return ResponseEntity.ok(
            toolRegistry.getAllTools().stream()
                .map(t -> new ToolSummary(t.name(), t.description(), t.beanClassName()))
                .toList()
        );
    }

    @GetMapping("/tools/phase/{phase}")
    @Operation(summary = "获取阶段工具")
    public ResponseEntity<List<ToolSummary>> getToolsForPhase(@PathVariable CreationPhase phase) {
        return ResponseEntity.ok(
            toolRegistry.getToolsForPhase(phase).stream()
                .map(t -> new ToolSummary(t.name(), t.description(), t.beanClassName()))
                .toList()
        );
    }

    @GetMapping("/tools/stats")
    @Operation(summary = "获取工具统计")
    public ResponseEntity<Map<String, ToolRegistry.ToolStats>> getToolStats() {
        return ResponseEntity.ok(toolRegistry.getToolStats());
    }

    public record ToolSummary(String name, String description, String provider) {}
}
