package com.inkflow.module.agent.workflow.chain;

import com.inkflow.module.agent.context.ContextBus;
import com.inkflow.module.agent.context.ContextEvent;
import com.inkflow.module.agent.core.CapableAgent;
import com.inkflow.module.agent.dto.ChatRequest;
import com.inkflow.module.agent.event.SSEEvent;
import com.inkflow.module.agent.orchestration.AgentOrchestrator;
import com.inkflow.module.agent.orchestration.chain.ChainExecutionContext;
import com.inkflow.module.agent.skill.PromptInjector;
import com.inkflow.module.agent.workflow.Workflow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 链式工作流抽象基类
 * 支持多 Agent 顺序执行，前一个 Agent 的输出作为后一个的输入
 * 支持用户交互（暂停等待用户选择）
 * 
 * @author zsg
 */
@Slf4j
public abstract class AbstractChainWorkflow implements Workflow {
    
    protected final AgentOrchestrator orchestrator;
    protected final PromptInjector promptInjector;
    protected final ContextBus contextBus;
    
    protected AbstractChainWorkflow(AgentOrchestrator orchestrator,
                                    PromptInjector promptInjector,
                                    ContextBus contextBus) {
        this.orchestrator = orchestrator;
        this.promptInjector = promptInjector;
        this.contextBus = contextBus;
    }
    
    /**
     * 获取 Agent 执行链
     * 子类必须实现此方法定义链式执行的步骤
     * 
     * @param request 原始请求
     * @return 链步骤列表
     */
    protected abstract List<ChainStep> getChainSteps(ChatRequest request);
    
    /**
     * 是否需要用户交互
     * 子类可覆盖此方法启用用户交互
     * 
     * @return true 如果需要用户交互
     */
    protected boolean requiresUserInteraction() {
        return false;
    }

    
    /**
     * 获取用户交互点（在哪个步骤后暂停）
     * 子类可覆盖此方法指定交互点
     * 
     * @return 步骤索引，-1 表示不需要用户交互
     */
    protected int getUserInteractionAfterStep() {
        return -1;
    }
    
    /**
     * 执行工作流
     * 
     * 关键逻辑：在 execute() 入口检查 metadata.chainContext 自动判断是否继续链
     */
    @Override
    public Flux<ServerSentEvent<String>> execute(ChatRequest request) {
        // 断点续传：检查请求是否包含链式上下文（用户交互后的后续请求）
        if (request.metadata() != null && request.metadata().containsKey("chainContext")) {
            String userSelection = request.message();
            publishThought(request.sessionId(), "继续链式执行，用户选择: " + userSelection);
            return continueChain(request, userSelection);
        }
        
        // 否则，开始新的链
        List<ChainStep> steps = getChainSteps(request);
        
        if (!requiresUserInteraction()) {
            // 无用户交互：直接执行完整链
            return executeFullChain(request, steps);
        } else {
            // 有用户交互：执行到交互点后暂停
            return executeWithUserInteraction(request, steps);
        }
    }
    
    /**
     * 执行完整链（无用户交互）
     */
    private Flux<ServerSentEvent<String>> executeFullChain(ChatRequest request, List<ChainStep> steps) {
        return Flux.defer(() -> {
            List<CapableAgent<ChatRequest, String>> agents = steps.stream()
                .filter(ChainStep::isAgentStep)
                .map(ChainStep::agent)
                .toList();
            
            if (agents.isEmpty()) {
                return Flux.just(SSEEvent.done().toServerSentEvent());
            }
            
            publishThought(request.sessionId(), "开始链式执行: " + agents.size() + " 个 Agent");
            
            // 使用 subscribeOn 避免阻塞 Netty IO 线程
            return Mono.fromFuture(() -> 
                orchestrator.executeChainWithContextAsync(
                    request.message(),
                    agents,
                    request
                )
            )
            .subscribeOn(Schedulers.boundedElastic())
            .flatMapMany(context -> buildChainResultFlux(context, request.sessionId()));
        });
    }
    
    /**
     * 构建链式执行结果的 SSE 流
     * 
     * Requirements: 13.3
     */
    private Flux<ServerSentEvent<String>> buildChainResultFlux(ChainExecutionContext context, String sessionId) {
        if (context.isAborted()) {
            publishThought(sessionId, "链式执行中断: " + context.getAbortReason());
            return Flux.just(
                SSEEvent.error("链式执行中断: " + context.getAbortReason()).toServerSentEvent()
            );
        }
        
        // 发送所有 Agent 的输出
        return Flux.fromIterable(context.getAllOutputs())
            .flatMap(output -> {
                if (output.success()) {
                    return Flux.just(
                        SSEEvent.thought(output.agentName() + " 完成").toServerSentEvent(),
                        SSEEvent.content(output.content()).toServerSentEvent()
                    );
                } else {
                    return Flux.just(
                        SSEEvent.thought(output.agentName() + " 失败: " + output.errorMessage()).toServerSentEvent()
                    );
                }
            })
            .concatWith(Flux.just(
                createChainSummaryEvent(context),
                SSEEvent.done().toServerSentEvent()
            ));
    }

    
    /**
     * 执行带用户交互的链
     *
     */
    private Flux<ServerSentEvent<String>> executeWithUserInteraction(ChatRequest request, List<ChainStep> steps) {
        int interactionPoint = getUserInteractionAfterStep();
        
        if (interactionPoint < 0 || interactionPoint >= steps.size()) {
            // 无效的交互点，执行完整链
            return executeFullChain(request, steps);
        }
        
        // 分割步骤：交互前 + 交互后
        List<ChainStep> beforeInteraction = steps.subList(0, interactionPoint + 1);
        
        return Flux.defer(() -> {
            // 执行交互前的步骤
            List<CapableAgent<ChatRequest, String>> beforeAgents = beforeInteraction.stream()
                .filter(ChainStep::isAgentStep)
                .map(ChainStep::agent)
                .toList();
            
            if (beforeAgents.isEmpty()) {
                // 没有 Agent 步骤，直接发送交互请求
                return Flux.just(
                    createUserInteractionEvent(null),
                    SSEEvent.done().toServerSentEvent()
                );
            }
            
            publishThought(request.sessionId(), "执行交互前步骤: " + beforeAgents.size() + " 个 Agent");
            
            return Mono.fromFuture(() -> 
                orchestrator.executeChainWithContextAsync(
                    request.message(),
                    beforeAgents,
                    request
                )
            )
            .subscribeOn(Schedulers.boundedElastic())
            .flatMapMany(context -> {
                if (context.isAborted()) {
                    return Flux.just(
                        SSEEvent.error("链式执行中断: " + context.getAbortReason()).toServerSentEvent()
                    );
                }
                
                // 发送交互前的输出
                Flux<ServerSentEvent<String>> beforeOutput = Flux.fromIterable(context.getAllOutputs())
                    .flatMap(output -> {
                        if (output.success()) {
                            return Flux.just(
                                SSEEvent.thought(output.agentName() + " 完成").toServerSentEvent(),
                                SSEEvent.content(output.content()).toServerSentEvent()
                            );
                        } else {
                            return Flux.just(
                                SSEEvent.thought(output.agentName() + " 失败").toServerSentEvent()
                            );
                        }
                    });
                
                // 发送用户交互请求事件
                // 注意：此时流暂停，等待用户通过新请求继续
                // 用户选择后，前端应发送新请求，带上 metadata.chainContext
                Flux<ServerSentEvent<String>> interactionRequest = Flux.just(
                    createUserInteractionEvent(context)
                );
                
                return Flux.concat(beforeOutput, interactionRequest);
            });
        });
    }
    
    /**
     * 继续执行链（用户选择后）
     * 
     * Requirements: 14.6, 14.7
     * 
     * @param request 包含 chainContext 的请求
     * @param userSelection 用户的选择
     * @return SSE 事件流
     */
    public Flux<ServerSentEvent<String>> continueChain(ChatRequest request, String userSelection) {
        List<ChainStep> steps = getChainSteps(request);
        int interactionPoint = getUserInteractionAfterStep();
        
        if (interactionPoint < 0 || interactionPoint >= steps.size() - 1) {
            // 无效的交互点或没有后续步骤
            return Flux.just(SSEEvent.done().toServerSentEvent());
        }
        
        List<ChainStep> afterInteraction = steps.subList(interactionPoint + 1, steps.size());
        
        // 构建带用户选择的新请求
        String enrichedMessage = buildEnrichedMessage(request.message(), userSelection);
        
        // 从 metadata 中获取之前的上下文（如果有）
        Map<String, Object> newMetadata = new HashMap<>(
            request.metadata() != null ? request.metadata() : Map.of()
        );
        newMetadata.put("userSelection", userSelection);
        // 移除 chainContext 避免无限循环
        newMetadata.remove("chainContext");
        
        ChatRequest enrichedRequest = new ChatRequest(
            enrichedMessage,
            request.projectId(),
            request.sessionId(),
            request.currentPhase(),
            request.intentHint(),
            newMetadata
        );
        
        publishThought(request.sessionId(), "继续执行后续步骤: " + afterInteraction.size() + " 个步骤");
        
        return executeFullChain(enrichedRequest, afterInteraction);
    }

    
    /**
     * 构建包含用户选择的增强消息
     */
    private String buildEnrichedMessage(String originalMessage, String userSelection) {
        return String.format("""
            用户选择: %s
            
            原始请求: %s
            
            请基于用户的选择继续处理。
            """, userSelection, originalMessage);
    }
    
    /**
     * 构建交互选项
     * 子类可以覆盖此方法提供具体的选项格式
     * 
     * @param context 链式执行上下文（可能为 null）
     * @return 交互选项的 JSON 字符串
     */
    protected String buildInteractionOptions(ChainExecutionContext context) {
        if (context == null) {
            return """
                {
                    "type": "selection",
                    "message": "请选择一个选项继续",
                    "options": []
                }
                """;
        }
        
        // 默认实现：返回最后一个 Agent 的输出作为选项
        String lastOutput = context.getFinalResult();
        return String.format("""
            {
                "type": "selection",
                "message": "请选择一个方案继续",
                "content": %s
            }
            """, escapeJson(lastOutput));
    }
    
    /**
     * 创建用户交互事件
     */
    private ServerSentEvent<String> createUserInteractionEvent(ChainExecutionContext context) {
        String options = buildInteractionOptions(context);
        return ServerSentEvent.<String>builder()
            .event("user_input_required")
            .data(options)
            .build();
    }
    
    /**
     * 创建链式执行摘要事件
     */
    private ServerSentEvent<String> createChainSummaryEvent(ChainExecutionContext context) {
        String summary = String.format("""
            {
                "executionId": "%s",
                "agentCount": %d,
                "successCount": %d,
                "failureCount": %d,
                "summary": "%s"
            }
            """,
            context.getExecutionId(),
            context.getAllOutputs().size(),
            context.getSuccessCount(),
            context.getFailureCount(),
            escapeJson(context.getSummary())
        );
        
        return ServerSentEvent.<String>builder()
            .event("chain_summary")
            .data(summary)
            .build();
    }
    
    /**
     * 简单的 JSON 转义
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            + "\"";
    }
    
    /**
     * 发布思考事件
     */
    protected void publishThought(String sessionId, String thought) {
        if (sessionId != null && contextBus != null) {
            contextBus.publish(sessionId, ContextEvent.custom(getName(), "thought", thought, Map.of()));
        }
        log.debug("[{}] {}", getName(), thought);
    }
}
