package com.inkflow.module.agent.workflow;

import com.inkflow.module.agent.context.ContextBus;
import com.inkflow.module.agent.context.ContextEvent;
import com.inkflow.module.agent.core.Intent;
import com.inkflow.module.agent.dto.ChatRequest;
import com.inkflow.module.agent.event.SSEEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流执行器
 * 根据 Intent 选择并执行对应的工作流
 * 职责：
 * 1. 维护 Intent → Workflow 映射
 * 2. 根据意图选择工作流
 * 3. 发布思考事件
 * 4. 执行工作流并返回 SSE 流
 * 5. 处理错误和降级
 *
 */
@Slf4j
@Component
public class WorkflowExecutor {
    
    private final Map<WorkflowType, Workflow> workflowsByType;
    private final Map<Intent, Workflow> workflowsByIntent;
    private final ContextBus contextBus;
    private final Workflow fallbackWorkflow;
    
    /**
     * 构造函数
     * 注入所有 Workflow 实现，构建 Intent → Workflow 映射
     * 
     * @param workflows 所有工作流实现
     * @param contextBus 上下文总线
     */
    public WorkflowExecutor(List<Workflow> workflows, ContextBus contextBus) {
        this.contextBus = contextBus;
        this.workflowsByType = new EnumMap<>(WorkflowType.class);
        this.workflowsByIntent = new EnumMap<>(Intent.class);
        
        // 注册所有工作流
        for (Workflow workflow : workflows) {
            WorkflowType type = workflow.getType();
            workflowsByType.put(type, workflow);
            
            // 构建 Intent → Workflow 映射
            for (Intent intent : workflow.getSupportedIntents()) {
                workflowsByIntent.put(intent, workflow);
                log.debug("[WorkflowExecutor] 注册映射: {} -> {}", intent, workflow.getName());
            }
            
            log.info("[WorkflowExecutor] 注册工作流: {} ({})", workflow.getName(), type);
        }
        
        // 设置降级工作流（SimpleAgentWorkflow）
        this.fallbackWorkflow = workflowsByType.get(WorkflowType.SIMPLE_AGENT);
        
        log.info("[WorkflowExecutor] 初始化完成，注册 {} 个工作流，{} 个意图映射", 
            workflowsByType.size(), workflowsByIntent.size());
    }
    
    /**
     * 执行工作流
     * 根据 Intent 选择工作流并执行
     * 
     * @param intent 用户意图
     * @param request 聊天请求
     * @return SSE 事件流
     */
    public Flux<ServerSentEvent<String>> execute(Intent intent, ChatRequest request) {
        return Flux.defer(() -> {
            // 选择工作流
            Workflow workflow = selectWorkflow(intent);
            
            // 发布思考事件
            publishThought(request.sessionId(), 
                "选择工作流: " + workflow.getName() + " (意图: " + intent + ")");
            
            log.info("[WorkflowExecutor] 执行工作流: {} for intent: {}, projectId: {}", 
                workflow.getName(), intent, request.projectId());
            
            // 执行工作流
            return workflow.execute(request)
                .onErrorResume(error -> handleExecutionError(error, request, workflow));
        });
    }
    
    /**
     * 根据 WorkflowType 执行工作流
     * 
     * @param type 工作流类型
     * @param request 聊天请求
     * @return SSE 事件流
     */
    public Flux<ServerSentEvent<String>> executeByType(WorkflowType type, ChatRequest request) {
        return Flux.defer(() -> {
            Workflow selectedWorkflow = workflowsByType.get(type);
            
            if (selectedWorkflow == null) {
                log.warn("[WorkflowExecutor] 未找到工作流类型: {}, 使用降级工作流", type);
                selectedWorkflow = fallbackWorkflow;
            }
            
            if (selectedWorkflow == null) {
                return Flux.just(SSEEvent.error("系统错误: 未找到可用的工作流").toServerSentEvent());
            }
            
            final Workflow finalWorkflow = selectedWorkflow;
            publishThought(request.sessionId(), "选择工作流: " + finalWorkflow.getName());
            
            return finalWorkflow.execute(request)
                .onErrorResume(error -> handleExecutionError(error, request, finalWorkflow));
        });
    }
    
    /**
     * 选择工作流
     * 根据 Intent 选择对应的工作流，未找到时降级到 SimpleAgentWorkflow
     * 
     * @param intent 用户意图
     * @return 工作流
     */
    private Workflow selectWorkflow(Intent intent) {
        // 优先从 Intent 映射中查找
        Workflow workflow = workflowsByIntent.get(intent);
        
        if (workflow != null) {
            return workflow;
        }
        
        // 尝试从 WorkflowType 映射中查找
        WorkflowType type = WorkflowType.fromIntent(intent);
        Workflow typeWorkflow = workflowsByType.get(type);
        
        if (typeWorkflow != null) {
            return typeWorkflow;
        }
        
        // 降级到 fallback 工作流
        log.warn("[WorkflowExecutor] 未找到意图 {} 对应的工作流，降级到 SimpleAgentWorkflow", intent);
        
        if (fallbackWorkflow != null) {
            return fallbackWorkflow;
        }
        
        // 最终降级：返回第一个可用的工作流
        return workflowsByType.values().stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("没有可用的工作流"));
    }
    
    /**
     * 处理执行错误
     * 
     * @param error 错误
     * @param request 请求
     * @param workflow 工作流
     * @return 错误事件流
     * @see Requirements 1.5
     */
    private Flux<ServerSentEvent<String>> handleExecutionError(
            Throwable error, ChatRequest request, Workflow workflow) {
        
        log.error("[WorkflowExecutor] 工作流 {} 执行失败: {}", 
            workflow.getName(), error.getMessage(), error);
        
        publishThought(request.sessionId(), "执行出错: " + error.getMessage());
        
        return Flux.just(
            SSEEvent.error("工作流执行失败: " + error.getMessage()).toServerSentEvent(),
            SSEEvent.done().toServerSentEvent()
        );
    }
    
    /**
     * 发布思考事件
     */
    private void publishThought(String sessionId, String thought) {
        if (sessionId != null && contextBus != null) {
            contextBus.publish(sessionId, 
                ContextEvent.custom("WorkflowExecutor", "thought", thought, Map.of()));
        }
        log.debug("[WorkflowExecutor] {}", thought);
    }
    
    /**
     * 获取指定意图对应的工作流
     */
    public Workflow getWorkflowForIntent(Intent intent) {
        return selectWorkflow(intent);
    }
    
    /**
     * 获取指定类型的工作流
     */
    public Workflow getWorkflowByType(WorkflowType type) {
        return workflowsByType.get(type);
    }
    
    /**
     * 检查是否支持指定意图
     */
    public boolean supportsIntent(Intent intent) {
        return workflowsByIntent.containsKey(intent) || 
               WorkflowType.fromIntent(intent) != WorkflowType.SIMPLE_AGENT ||
               fallbackWorkflow != null;
    }
    
    /**
     * 获取所有已注册的工作流
     */
    public List<Workflow> getAllWorkflows() {
        return List.copyOf(workflowsByType.values());
    }
    
    /**
     * 获取所有支持的意图
     */
    public List<Intent> getSupportedIntents() {
        return List.copyOf(workflowsByIntent.keySet());
    }
}
