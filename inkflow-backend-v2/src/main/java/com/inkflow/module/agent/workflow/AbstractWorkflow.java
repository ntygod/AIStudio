package com.inkflow.module.agent.workflow;

import com.inkflow.module.agent.context.ContextBus;
import com.inkflow.module.agent.context.ContextEvent;
import com.inkflow.module.agent.core.BaseAgent;
import com.inkflow.module.agent.core.CapableAgent;
import com.inkflow.module.agent.dto.ChatRequest;
import com.inkflow.module.agent.event.SSEEvent;
import com.inkflow.module.agent.orchestration.AgentOrchestrator;
import com.inkflow.module.agent.skill.PromptInjector;
import com.inkflow.module.agent.skill.SkillContext;
import com.inkflow.module.agent.skill.SkillSlot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作流抽象基类
 * 提供 预处理 → Skill 注入 → Agent 执行 → 后处理 的模板方法
 *
 */
@Slf4j
public abstract class AbstractWorkflow implements Workflow {
    
    protected final AgentOrchestrator orchestrator;
    protected final PromptInjector promptInjector;
    protected final ContextBus contextBus;
    
    protected AbstractWorkflow(AgentOrchestrator orchestrator, 
                               PromptInjector promptInjector, 
                               ContextBus contextBus) {
        this.orchestrator = orchestrator;
        this.promptInjector = promptInjector;
        this.contextBus = contextBus;
    }
    
    /**
     * Phase 0: 并行预处理（子类实现）
     * 默认返回空上下文
     *
     */
    protected Mono<PreprocessingContext> preprocess(ChatRequest request) {
        return Mono.just(PreprocessingContext.empty());
    }

    /**
     * 获取主执行 Agent
     */
    protected abstract CapableAgent<ChatRequest, String> getMainAgent(ChatRequest request);
    
    /**
     * 是否需要 Skill 注入
     */
    protected boolean needsSkillInjection() {
        return false;
    }
    
    /**
     * Phase 3: 同步后处理（子类实现，可选）
     * 返回后处理结果，会作为 check_result 事件发送
     *
     */
    protected Mono<PostProcessingResult> postprocess(ChatRequest request, String generatedContent) {
        return Mono.empty();
    }
    
    /**
     * 执行工作流
     * 模板方法：预处理 → Skill 注入 → Agent 执行 → 后处理
     *
     */
    @Override
    public final Flux<ServerSentEvent<String>> execute(ChatRequest request) {
        StringBuilder generatedContent = new StringBuilder();
        
        return preprocess(request)  // Phase 0: 并行预处理
            .doOnNext(ctx -> {
                if (!ctx.isEmpty()) {
                    publishThought(request.sessionId(), "预处理完成，获取到 " + 
                        (ctx.ragResults() != null ? ctx.ragResults().size() : 0) + " 条相关设定");
                }
            })
            .flatMapMany(context -> {
                // Phase 1: Skill 注入（基于预处理结果）
                ChatRequest enrichedRequest = enrichRequest(request, context);
                
                if (needsSkillInjection()) {
                    enrichedRequest = injectSkills(enrichedRequest, context);
                }
                
                // Phase 2: Agent 执行
                CapableAgent<ChatRequest, String> agent = getMainAgent(request);
                publishThought(request.sessionId(), "执行 " + agent.getName());
                return agent.stream(enrichedRequest);
            })
            .doOnNext(chunk -> generatedContent.append(chunk))
            .map(content -> SSEEvent.content(content).toServerSentEvent())
            .concatWith(executePostProcessing(request, generatedContent));  // Phase 3: 同步后处理
    }

    /**
     * 将预处理上下文注入到请求的 metadata 中
     */
    protected ChatRequest enrichRequest(ChatRequest request, PreprocessingContext context) {
        Map<String, Object> metadata = new HashMap<>(
            request.metadata() != null ? request.metadata() : Map.of()
        );
        metadata.put("preprocessingContext", context);
        
        return new ChatRequest(
            request.message(),
            request.projectId(),
            request.sessionId(),
            request.currentPhase(),
            request.intentHint(),
            metadata
        );
    }
    
    /**
     * 执行后处理阶段
     *
     */
    private Flux<ServerSentEvent<String>> executePostProcessing(ChatRequest request, StringBuilder content) {
        return Flux.defer(() -> {
            String generated = content.toString();
            if (generated.isEmpty()) {
                return Flux.just(SSEEvent.done().toServerSentEvent());
            }
            
            return postprocess(request, generated)
                .flatMapMany(result -> Flux.just(
                    SSEEvent.thought("正在检查一致性...").toServerSentEvent(),
                    createCheckResultEvent(result),
                    SSEEvent.done().toServerSentEvent()
                ))
                .switchIfEmpty(Flux.just(SSEEvent.done().toServerSentEvent()));
        });
    }
    
    /**
     * 创建检查结果事件
     */
    private ServerSentEvent<String> createCheckResultEvent(PostProcessingResult result) {
        return ServerSentEvent.<String>builder()
            .event("check_result")
            .data(result.toJson())
            .build();
    }

    /**
     * 注入技能到请求
     *
     */
    @SuppressWarnings("unchecked")
    private ChatRequest injectSkills(ChatRequest request, PreprocessingContext context) {
        CapableAgent<ChatRequest, String> agent = getMainAgent(request);
        
        // 构建技能上下文
        SkillContext skillContext = new SkillContext(
            request.projectId(),
            null,  // userId 从 request 中获取
            request.currentPhase(),
            request.message(),
            Map.of("preprocessingContext", context),
            null
        );
        
        // 获取适用的技能
        Class<? extends BaseAgent<?, ?>> agentClass = (Class<? extends BaseAgent<?, ?>>) agent.getClass();
        List<SkillSlot> skills = promptInjector.autoSelectSkills(agentClass, skillContext);
        
        if (!skills.isEmpty()) {
            publishThought(request.sessionId(), 
                "激活技能: " + skills.stream().map(SkillSlot::getName).collect(Collectors.joining(", ")));
            
            // 构建增强的系统提示词
            // 注意：BaseAgent.getSystemPrompt() 是 protected，这里使用空字符串作为基础
            // 实际的系统提示词会在 Agent 内部处理
            String basePrompt = "";
            String enhancedPrompt = promptInjector.buildEnhancedSystemPrompt(basePrompt, agentClass, skillContext);
            
            // 将增强提示词注入到 metadata
            Map<String, Object> metadata = new HashMap<>(
                request.metadata() != null ? request.metadata() : Map.of()
            );
            metadata.put("enhancedSystemPrompt", enhancedPrompt);
            metadata.put("activeSkills", skills.stream().map(SkillSlot::getName).toList());
            
            return new ChatRequest(
                request.message(),
                request.projectId(),
                request.sessionId(),
                request.currentPhase(),
                request.intentHint(),
                metadata
            );
        }
        
        return request;
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
