package com.inkflow.module.agent.routing;

import com.inkflow.module.agent.core.*;
import com.inkflow.module.agent.dto.ChatRequest;
import com.inkflow.module.agent.event.SSEEvent;
import com.inkflow.module.agent.event.SSEEventType;
import com.inkflow.module.agent.workflow.WorkflowExecutor;
import com.inkflow.module.project.entity.CreationPhase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 路由器
 * 负责根据用户意图将请求路由到合适的工作流
 * 
 * 路由流程：
 * 1. 尝试 Fast Path（intentHint 或命令前缀）
 * 2. 如果无法 Fast Path，使用 ThinkingAgent 分析意图
 * 3. 委托给 WorkflowExecutor 执行对应工作流
 * 
 * @see Requirements 10.4
 */
@Slf4j
@Component
public class AgentRouter {
    
    private final FastPathFilter fastPathFilter;
    private final ThinkingAgent thinkingAgent;
    private final WorkflowExecutor workflowExecutor;
    private final Map<String, CapableAgent<?, ?>> agents = new ConcurrentHashMap<>();
    
    /**
     * 构造函数
     * 
     * @param fastPathFilter Fast Path 过滤器
     * @param thinkingAgent 意图分析 Agent
     * @param workflowExecutor 工作流执行器
     * @param agentList 所有 Agent 列表（用于兼容性和查询）
     * @see Requirements 10.4
     */
    public AgentRouter(
            FastPathFilter fastPathFilter,
            ThinkingAgent thinkingAgent,
            WorkflowExecutor workflowExecutor,
            List<CapableAgent<?, ?>> agentList) {
        this.fastPathFilter = fastPathFilter;
        this.thinkingAgent = thinkingAgent;
        this.workflowExecutor = workflowExecutor;
        
        // 注册所有 Agent（用于兼容性查询）
        for (CapableAgent<?, ?> agent : agentList) {
            agents.put(agent.getName(), agent);
            log.info("[AgentRouter] 注册 Agent: {}", agent.getName());
        }
    }
    
    /**
     * 路由请求到合适的工作流
     * 路由流程：
     * 1. 尝试 Fast Path（intentHint 或命令前缀）
     * 2. 如果无法 Fast Path，使用 ThinkingAgent 分析意图
     * 3. 委托给 WorkflowExecutor 执行对应工作流
     * 
     * @param request 聊天请求
     * @return SSE 流式响应
     */
    public Flux<ServerSentEvent<String>> route(ChatRequest request) {
        return Flux.defer(() -> {
            // 1. 尝试 Fast Path
            Optional<FastPathResult> fastPathResult = fastPathFilter.tryFastPath(request);
            
            IntentResult intentResult;
            Flux<ServerSentEvent<String>> prefixEvents;
            
            if (fastPathResult.isPresent()) {
                // Fast Path 成功
                FastPathResult fp = fastPathResult.get();
                intentResult = new IntentResult(
                    fp.intent(),
                    1.0,
                    List.of(),
                    IntentResult.IntentSource.FAST_PATH,
                    fp.targetAgent()
                );
                prefixEvents = Flux.just(
                    SSEEvent.thought("Fast Path 路由: " + fp.intent()).toServerSentEvent()
                );
            } else {
                // 2. 使用 ThinkingAgent 分析
                intentResult = thinkingAgent.analyze(request.message(), request.currentPhase());
                prefixEvents = Flux.just(
                    SSEEvent.thought("分析用户意图...").toServerSentEvent(),
                    SSEEvent.thought("意图识别: " + intentResult.intent() + 
                            " (置信度: " + Math.round(intentResult.confidence() * 100) + "%)").toServerSentEvent()
                );
            }
            
            // 3. 应用阶段优先级调整（仅在低置信度时）
            Intent finalIntent = applyPhasePriorityForIntent(intentResult, request.currentPhase());
            
            log.info("[AgentRouter] 路由请求: intent={}, confidence={}, projectId={}", 
                finalIntent, intentResult.confidence(), request.projectId());
            
            // 4. 委托给 WorkflowExecutor 执行
            return Flux.concat(prefixEvents,
                workflowExecutor.execute(finalIntent, request)
            );
        }).onErrorResume(e -> {
            log.error("[AgentRouter] 路由失败: {}", e.getMessage(), e);
            return Flux.just(SSEEvent.error("路由失败: " + e.getMessage()).toServerSentEvent());
        });
    }
    
    /**
     * 应用阶段优先级调整（返回调整后的 Intent）
     * 根据当前创作阶段调整目标意图
     * 
     * @param intentResult 意图识别结果
     * @param phase 当前创作阶段
     * @return 调整后的意图
     */
    private Intent applyPhasePriorityForIntent(IntentResult intentResult, CreationPhase phase) {
        if (phase == null) {
            return intentResult.intent();
        }
        
        // 如果意图置信度很高，不调整
        if (intentResult.confidence() >= 0.9) {
            return intentResult.intent();
        }
        
        // 根据阶段调整优先意图
        Intent phasePreferredIntent = getPhasePreferredIntent(phase);
        if (phasePreferredIntent != null && phasePreferredIntent != intentResult.intent()) {
            log.debug("[AgentRouter] 阶段优先级调整: {} -> {} (phase={})", 
                    intentResult.intent(), phasePreferredIntent, phase);
            return phasePreferredIntent;
        }
        
        return intentResult.intent();
    }
    
    /**
     * 获取阶段优先意图
     * 
     * @param phase 创作阶段
     * @return 该阶段优先的意图
     */
    private Intent getPhasePreferredIntent(CreationPhase phase) {
        return switch (phase) {
            case IDEA, WORLDBUILDING -> Intent.PLAN_WORLD;
            case CHARACTER -> Intent.PLAN_CHARACTER;
            case OUTLINE -> Intent.PLAN_OUTLINE;
            case WRITING -> Intent.WRITE_CONTENT;
            case REVISION -> Intent.CHECK_CONSISTENCY;
            default -> null;
        };
    }
    
    /**
     * 应用阶段优先级调整（返回 Agent 名称，保留用于兼容性）
     * 根据当前创作阶段调整目标 Agent
     * 
     * @deprecated 使用 {@link #applyPhasePriorityForIntent(IntentResult, CreationPhase)} 代替
     */
    @Deprecated
    private String applyPhasePriority(IntentResult intentResult, CreationPhase phase) {
        Intent adjustedIntent = applyPhasePriorityForIntent(intentResult, phase);
        return adjustedIntent.getTargetAgentName();
    }
    
    /**
     * 获取阶段优先 Agent（保留用于兼容性）
     * 
     * @deprecated 使用 {@link #getPhasePreferredIntent(CreationPhase)} 代替
     */
    @Deprecated
    private String getPhasePreferredAgent(CreationPhase phase) {
        Intent intent = getPhasePreferredIntent(phase);
        return intent != null ? intent.getTargetAgentName() : null;
    }
    
    /**
     * 获取所有 Agent 能力
     */
    public List<AgentCapability> getCapabilities() {
        return agents.values().stream()
                .map(CapableAgent::getCapability)
                .toList();
    }
    
    /**
     * 获取指定 Agent
     */
    public Optional<CapableAgent<?, ?>> getAgent(String name) {
        return Optional.ofNullable(agents.get(name));
    }
    
    /**
     * 获取支持指定意图的 Agent 列表
     */
    public List<CapableAgent<?, ?>> getAgentsForIntent(Intent intent) {
        return agents.values().stream()
                .filter(agent -> agent.getCapability().supportsIntent(intent))
                .toList();
    }
    
    /**
     * 获取适用于指定阶段的 Agent 列表
     */
    public List<CapableAgent<?, ?>> getAgentsForPhase(CreationPhase phase) {
        return agents.values().stream()
                .filter(agent -> agent.getCapability().applicableToPhase(phase))
                .toList();
    }
    
    /**
     * 构建内容事件
     */
    private ServerSentEvent<String> buildContentEvent(String content) {
        return ServerSentEvent.<String>builder()
                .event(SSEEventType.CONTENT.getValue())
                .data(content)
                .build();
    }
}
