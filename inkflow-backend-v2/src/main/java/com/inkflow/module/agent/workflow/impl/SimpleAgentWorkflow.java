package com.inkflow.module.agent.workflow.impl;

import com.inkflow.module.agent.core.CapableAgent;
import com.inkflow.module.agent.core.Intent;
import com.inkflow.module.agent.dto.ChatRequest;
import com.inkflow.module.agent.event.SSEEvent;
import com.inkflow.module.agent.impl.*;
import com.inkflow.module.agent.workflow.Workflow;
import com.inkflow.module.agent.workflow.WorkflowType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 简单 Agent 工作流
 * 直接执行对应 Agent，无预处理、无 Skill 注入、无后处理
 * 
 * 支持的意图：
 * - GENERAL_CHAT → ChatAgent
 * - GENERATE_NAME → NameGeneratorAgent
 * - SUMMARIZE → SummaryAgent
 * - EXTRACT_ENTITY → ExtractionAgent
 *
 */
@Slf4j
@Component
public class SimpleAgentWorkflow implements Workflow {
    
    private final Map<Intent, CapableAgent<ChatRequest, String>> agentMap;
    private final ChatAgent chatAgent;
    
    public SimpleAgentWorkflow(
            ChatAgent chatAgent,
            NameGeneratorAgent nameGeneratorAgent,
            SummaryAgent summaryAgent,
            ExtractionAgent extractionAgent) {
        
        this.chatAgent = chatAgent;
        this.agentMap = new EnumMap<>(Intent.class);
        
        // 配置 Intent → Agent 映射
        agentMap.put(Intent.GENERAL_CHAT, chatAgent);
        agentMap.put(Intent.GENERATE_NAME, nameGeneratorAgent);
        agentMap.put(Intent.SUMMARIZE, summaryAgent);
        agentMap.put(Intent.EXTRACT_ENTITY, extractionAgent);
        
        log.info("[SimpleAgentWorkflow] 初始化完成，注册 {} 个 Agent 映射", agentMap.size());
    }
    
    @Override
    public String getName() {
        return "简单任务";
    }
    
    @Override
    public List<Intent> getSupportedIntents() {
        return List.of(
            Intent.GENERAL_CHAT,
            Intent.GENERATE_NAME,
            Intent.SUMMARIZE,
            Intent.EXTRACT_ENTITY
        );
    }
    
    @Override
    public WorkflowType getType() {
        return WorkflowType.SIMPLE_AGENT;
    }
    
    /**
     * 直接执行 Agent
     * 无预处理、无 Skill 注入、无后处理
     *
     */
    @Override
    public Flux<ServerSentEvent<String>> execute(ChatRequest request) {
        return Flux.defer(() -> {
            // 根据意图选择 Agent
            Intent intent = detectIntent(request);
            CapableAgent<ChatRequest, String> agent = agentMap.get(intent);
            
            if (agent == null) {
                // 降级到 ChatAgent
                log.warn("[SimpleAgentWorkflow] 未找到意图 {} 对应的 Agent，降级到 ChatAgent", intent);
                agent = chatAgent;
            }
            
            log.debug("[SimpleAgentWorkflow] 执行 Agent: {} for intent: {}", agent.getName(), intent);
            
            final CapableAgent<ChatRequest, String> finalAgent = agent;
            
            // 直接执行，无预处理
            return Flux.just(SSEEvent.thought("执行 " + finalAgent.getName()).toServerSentEvent())
                .concatWith(
                    finalAgent.stream(request)
                        .map(content -> SSEEvent.content(content).toServerSentEvent())
                )
                .concatWith(Flux.just(SSEEvent.done().toServerSentEvent()))
                .onErrorResume(error -> {
                    log.error("[SimpleAgentWorkflow] Agent 执行失败: {}", error.getMessage(), error);
                    return Flux.just(
                        SSEEvent.error("执行失败: " + error.getMessage()).toServerSentEvent(),
                        SSEEvent.done().toServerSentEvent()
                    );
                });
        });
    }
    
    /**
     * 检测意图
     * 优先使用 intentHint，否则从消息中匹配
     */
    private Intent detectIntent(ChatRequest request) {
        // 优先使用 intentHint (intentHint 是 Intent 类型)
        Intent intentHint = request.intentHint();
        if (intentHint != null) {
            return intentHint;
        }
        
        // 从消息中匹配意图
        return Intent.matchFromMessage(request.message())
            .filter(agentMap::containsKey)
            .orElse(Intent.GENERAL_CHAT);
    }
}
