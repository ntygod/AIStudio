package com.inkflow.module.agent.workflow.impl;

import com.inkflow.module.agent.context.ContextBus;
import com.inkflow.module.agent.core.CapableAgent;
import com.inkflow.module.agent.core.Intent;
import com.inkflow.module.agent.dto.ChatRequest;
import com.inkflow.module.agent.impl.CharacterAgent;
import com.inkflow.module.agent.impl.WorldBuilderAgent;
import com.inkflow.module.agent.orchestration.AgentOrchestrator;
import com.inkflow.module.agent.skill.PromptInjector;
import com.inkflow.module.agent.workflow.*;
import com.inkflow.module.character.service.CharacterArchetypeService;
import com.inkflow.module.character.service.RelationshipGraphService;
import com.inkflow.module.rag.dto.SearchResult;
import com.inkflow.module.rag.service.HybridSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.Executors;

/**
 * 创意设计工作流
 * 
 * 执行流程：
 * 1. 并行预处理：RAG检索 + 原型库获取
 * 2. 动态选择 Agent：CharacterAgent 或 WorldBuilderAgent
 * 3. 异步后处理：关系图更新（不阻塞响应）
 *
 */
@Slf4j
@Component
public class CreativeDesignWorkflow extends AbstractWorkflow {
    
    private final CharacterAgent characterAgent;
    private final WorldBuilderAgent worldBuilderAgent;
    private final HybridSearchService hybridSearchService;
    private final CharacterArchetypeService archetypeService;
    private final RelationshipGraphService relationshipGraphService;
    
    public CreativeDesignWorkflow(
            AgentOrchestrator orchestrator,
            PromptInjector promptInjector,
            ContextBus contextBus,
            CharacterAgent characterAgent,
            WorldBuilderAgent worldBuilderAgent,
            HybridSearchService hybridSearchService,
            CharacterArchetypeService archetypeService,
            RelationshipGraphService relationshipGraphService) {
        super(orchestrator, promptInjector, contextBus);
        this.characterAgent = characterAgent;
        this.worldBuilderAgent = worldBuilderAgent;
        this.hybridSearchService = hybridSearchService;
        this.archetypeService = archetypeService;
        this.relationshipGraphService = relationshipGraphService;
    }
    
    @Override
    public String getName() {
        return "创意设计";
    }
    
    @Override
    public List<Intent> getSupportedIntents() {
        return List.of(
            Intent.PLAN_CHARACTER,
            Intent.DESIGN_RELATIONSHIP,
            Intent.MATCH_ARCHETYPE,
            Intent.PLAN_WORLD,
            Intent.BRAINSTORM_IDEA
        );
    }
    
    @Override
    public WorkflowType getType() {
        return WorkflowType.CREATIVE_DESIGN;
    }
    
    /**
     * 动态选择 Agent
     * PLAN_WORLD, BRAINSTORM_IDEA → WorldBuilderAgent
     * 其他 → CharacterAgent
     *
     */
    @Override
    protected CapableAgent<ChatRequest, String> getMainAgent(ChatRequest request) {
        Intent intent = detectIntent(request);
        
        return switch (intent) {
            case PLAN_WORLD, BRAINSTORM_IDEA -> worldBuilderAgent;
            default -> characterAgent;
        };
    }
    
    @Override
    protected boolean needsSkillInjection() {
        return false; // 创意设计不需要 Skill 注入
    }
    
    /**
     * 并行预处理：RAG检索 + 原型库获取
     *
     */
    @Override
    protected Mono<PreprocessingContext> preprocess(ChatRequest request) {
        publishThought(request.sessionId(), "并行预处理: RAG检索 + 原型库");
        
        UUID projectId = request.projectId();
        String query = request.message();
        
        return Mono.zip(
            // Task 1: RAG 检索
            hybridSearchService.search(projectId, query, 5)
                .onErrorResume(e -> {
                    log.warn("[CreativeDesignWorkflow] RAG检索失败: {}", e.getMessage());
                    return Mono.just(Collections.emptyList());
                }),
            
            // Task 2: 原型库获取 (使用 findAll 方法)
            Mono.fromCallable(() -> archetypeService.findAll())
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.warn("[CreativeDesignWorkflow] 原型库获取失败: {}", e.getMessage());
                    return Mono.just(Collections.emptyList());
                })
        ).map(tuple -> {
            List<SearchResult> ragResults = tuple.getT1();
            var archetypes = tuple.getT2();
            
            log.debug("[CreativeDesignWorkflow] 预处理完成: RAG={}, 原型={}",
                ragResults.size(), archetypes.size());
            
            return new PreprocessingContext(
                ragResults,
                Map.of(),
                "",
                Map.of("archetypes", archetypes)
            );
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    /**
     * 异步后处理：关系图更新
     * 不阻塞响应，异步执行
     *
     */
    @Override
    protected Mono<PostProcessingResult> postprocess(ChatRequest request, String generatedContent) {
        Intent intent = detectIntent(request);
        
        // 只有角色相关的意图才需要更新关系图
        if (intent == Intent.PLAN_CHARACTER || intent == Intent.DESIGN_RELATIONSHIP) {
            // 异步更新关系图（不阻塞响应）
            // 使用 buildGraph 方法重新构建关系图
            Executors.newVirtualThreadPerTaskExecutor().submit(() -> {
                try {
                    publishThought(request.sessionId(), "异步更新角色关系图...");
                    relationshipGraphService.buildGraph(request.projectId());
                    log.debug("[CreativeDesignWorkflow] 关系图更新完成");
                } catch (Exception e) {
                    log.error("[CreativeDesignWorkflow] 关系图更新失败: {}", e.getMessage(), e);
                }
            });
        }
        
        // 返回空，不阻塞响应
        return Mono.empty();
    }
    
    /**
     * 检测意图
     */
    private Intent detectIntent(ChatRequest request) {
        // intentHint 是 Intent 类型
        Intent intentHint = request.intentHint();
        if (intentHint != null) {
            return intentHint;
        }
        
        return Intent.matchFromMessage(request.message())
            .filter(getSupportedIntents()::contains)
            .orElse(Intent.PLAN_CHARACTER);
    }
}
