# Design Document

## Overview

本设计文档描述多 Agent 工作流编排系统的架构和实现细节。

**当前代码分析**：
1. `WriterAgent`/`CharacterAgent` 在 `buildUserPrompt()` 中直接调用 `RAGSearchTool`（串行）
2. `AgentOrchestrator` 已有 `executeParallel()`、`executeChain()` 等方法，但未被充分利用
3. `AgentRouter` 直接执行 Agent，没有 Workflow 层
4. `PromptInjector` 存在但从未被调用
5. `ChatRequest` 是 record 类型，需要新建 `EnrichedChatRequest`

**核心设计原则**：
1. **"哑 Agent" 策略**：Agent 只负责 LLM 调用，不做 RAG/状态查询。所有上下文由 Workflow 预处理阶段提供。
2. **并行预处理**：利用 `AgentOrchestrator.executeParallel3()` 并行执行 RAG、状态获取、风格获取。
3. **同步后处理反馈**：一致性检查在发送 `done` 之前完成，确保前端能收到结果。
4. **Skill 注入依赖上下文**：调用 `PromptInjector.autoSelectSkills()` 在预处理之后。

**与 UPGRADE.md 反馈的对齐**：
- ✅ 采纳"哑 Agent"策略，移除 Agent 内部的 RAG 调用
- ✅ 引入 `preprocess()` 钩子，使用 `AgentOrchestrator.executeParallel3()` 并行执行
- ✅ 后处理在 `done` 之前完成，发送 `check_result` 事件
- ✅ Skill 注入移到预处理之后，调用已有的 `PromptInjector`
- ⚠️ 多 Agent 链式执行：保留 `executeChain()` 能力，但当前设计以单 Agent 为主

**与 buchong.md 补充建议的对齐**：
- ✅ 链式断点续传：在 `AbstractChainWorkflow.execute()` 入口检查 `chainContext` 自动判断是否继续链
- ✅ Reactor 线程桥接：使用 `Mono.fromFuture()` 或 `subscribeOn(Schedulers.boundedElastic())` 避免阻塞 Netty IO 线程
- ✅ Agent 无状态约束：Agent 必须无状态，所有上下文通过 `EnrichedChatRequest` 传递，禁止存储到实例字段

## Architecture

### 组件架构

```
┌─────────────────────────────────────────────────────────────┐
│                    AgentController                           │
│                         │                                    │
│                         ▼                                    │
│              ┌─────────────────────┐                        │
│              │   AgentRouter       │                        │
│              │   (意图识别)         │                        │
│              └─────────────────────┘                        │
│                         │                                    │
│                         ▼                                    │
│              ┌─────────────────────┐                        │
│              │  WorkflowExecutor   │ ← 新增组件              │
│              │   (工作流选择执行)   │                        │
│              └─────────────────────┘                        │
└─────────────────────────────────────────────────────────────┘
                          │
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│ ContentGeneration│ │ CreativeDesign  │ │ SimpleAgent     │
│ Workflow        │ │ Workflow        │ │ Workflow        │
│ (预处理+写作+检查)│ │ (预处理+设计)   │ │ (直接执行)      │
└─────────────────┘ └─────────────────┘ └─────────────────┘
```


### 工作流执行流程（修订版）

```
┌─────────────────────────────────────────────────────────────┐
│                    Workflow Execution                        │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Phase 0: 并行预处理 (Preprocessing)                  │   │
│  │                                                      │   │
│  │  AgentOrchestrator.executeParallel(                 │   │
│  │    () -> ragService.retrieve(request),              │   │
│  │    () -> stateService.getCharacterStates(projectId),│   │
│  │    () -> styleService.getSamples(projectId)         │   │
│  │  )                                                  │   │
│  │                                                      │   │
│  │  → PreprocessingContext { rag, states, styles }     │   │
│  └─────────────────────────────────────────────────────┘   │
│                          │                                   │
│                          ▼                                   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Phase 1: Skill 注入 (基于预处理结果)                 │   │
│  │                                                      │   │
│  │  - 分析 RAG 结果中的角色状态（如"重伤"）            │   │
│  │  - 根据用户消息关键词 + 上下文选择 Skills           │   │
│  │  - 构建增强的系统提示词                             │   │
│  │                                                      │   │
│  │  → EnhancedRequest { prompt, context, skills }      │   │
│  └─────────────────────────────────────────────────────┘   │
│                          │                                   │
│                          ▼                                   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Phase 2: Agent 执行 (流式)                          │   │
│  │                                                      │   │
│  │  - Agent 只接收 Prompt + Context，不做 RAG          │   │
│  │  - agent.stream(enrichedRequest)                    │   │
│  │                                                      │   │
│  │  → Flux<String> (SSE 流)                            │   │
│  └─────────────────────────────────────────────────────┘   │
│                          │                                   │
│                          ▼                                   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Phase 3: 同步后处理 (在 done 之前)                   │   │
│  │                                                      │   │
│  │  - 发送 "processing_check" 事件                     │   │
│  │  - 执行一致性检查                                   │   │
│  │  - 发送 "check_result" 事件                         │   │
│  │  - 最后发送 "done" 事件                             │   │
│  │                                                      │   │
│  │  → 用户能看到检查结果                               │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 数据流向（关键约束）

```
┌─────────────────────────────────────────────────────────────┐
│                    数据流向约束                              │
│                                                              │
│  ❌ 禁止: Agent → Repository/SearchService                  │
│  ✅ 允许: Workflow → Service → Agent (via Context)          │
│                                                              │
│  Agent 的输入:                                               │
│  - systemPrompt: String (包含 Skill 注入的提示词)           │
│  - userPrompt: String (用户消息)                            │
│  - context: Map<String, Object> (预处理结果)                │
│                                                              │
│  Agent 的输出:                                               │
│  - Flux<String> (流式生成内容)                              │
└─────────────────────────────────────────────────────────────┘
```


### 工作流分类

| 工作流 | 触发意图 | 预处理 | Skill 注入 | 后处理 |
|--------|----------|--------|-----------|--------|
| ContentGenerationWorkflow | WRITE_CONTENT | RAG + 角色状态 + 风格 | ActionSkill, PsychologySkill, DescriptionSkill | ConsistencyAgent 检查 (同步) |
| CreativeDesignWorkflow | PLAN_CHARACTER, DESIGN_RELATIONSHIP, MATCH_ARCHETYPE, PLAN_WORLD, BRAINSTORM_IDEA | RAG + 原型库 | 无 | 关系图/知识库更新 (异步) |
| PlanningWorkflow | PLAN_OUTLINE, MANAGE_PLOTLOOP, ANALYZE_PACING | RAG + 伏笔状态 | 无 | 无 |
| QualityCheckWorkflow | CHECK_CONSISTENCY, ANALYZE_STYLE | RAG + Preflight | 无 | 无 |
| SimpleAgentWorkflow | GENERAL_CHAT, GENERATE_NAME, SUMMARIZE, EXTRACT_ENTITY | 无 | 无 | 无 |

## Components and Interfaces

### 1. PreprocessingContext 与现有 Context 体系的关系

**现有 context 目录分析：**

| 类 | 职责 | 生命周期 |
|---|------|---------|
| `SessionContext` | 会话级状态（recentEntities, workingMemory） | 跨请求持久化 |
| `ContextBus` | Agent 间事件传递和状态共享 | 应用级 |
| `ContextEvent` | 事件类型定义 | 单次事件 |
| `RecentEntity` | 最近访问的实体引用 | 会话级 |

**新增 PreprocessingContext 的定位：**

| 维度 | SessionContext | PreprocessingContext |
|------|----------------|---------------------|
| 生命周期 | 会话级（跨请求） | 请求级（单次请求） |
| 存储位置 | ContextBus（内存/Redis） | ChatRequest.metadata |
| 用途 | Agent 间状态共享 | Workflow → Agent 数据传递 |
| 内容 | 最近实体、工作内存 | RAG 结果、角色状态、风格 |

**设计决策：**
- `PreprocessingContext` 放在 `com.inkflow.module.agent.workflow` 包下
- 可选：将预处理结果缓存到 `SessionContext.workingMemory` 中，避免重复查询

```java
package com.inkflow.module.agent.workflow;

import com.inkflow.module.rag.dto.SearchResult;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 预处理上下文
 * 请求级别的上下文，包含 RAG 检索结果、角色状态、风格样本等
 * 
 * 与 SessionContext 的区别：
 * - SessionContext: 会话级，跨请求持久化，存储最近实体和工作内存
 * - PreprocessingContext: 请求级，单次请求有效，存储预处理结果
 */
public record PreprocessingContext(
    List<SearchResult> ragResults,           // 复用已有的 SearchResult
    Map<UUID, CharacterState> characterStates,
    String styleContext,                      // 风格上下文字符串
    Map<String, Object> additionalContext
) {
    public static PreprocessingContext empty() {
        return new PreprocessingContext(List.of(), Map.of(), "", Map.of());
    }
    
    /**
     * 转换为可注入到 Prompt 的字符串
     */
    public String toContextString() {
        StringBuilder sb = new StringBuilder();
        
        if (ragResults != null && !ragResults.isEmpty()) {
            sb.append("## 相关设定\n");
            for (SearchResult r : ragResults) {
                String typeLabel = switch (r.getSourceType()) {
                    case "character" -> "[角色]";
                    case "wiki_entry" -> "[设定]";
                    case "story_block" -> "[章节]";
                    default -> "[" + r.getSourceType() + "]";
                };
                sb.append("- ").append(typeLabel).append(" ").append(r.getContent()).append("\n");
            }
        }
        
        if (characterStates != null && !characterStates.isEmpty()) {
            sb.append("\n## 角色当前状态\n");
            characterStates.forEach((id, state) -> 
                sb.append("- ").append(state.name()).append(": ").append(state.currentState()).append("\n"));
        }
        
        if (styleContext != null && !styleContext.isBlank()) {
            sb.append("\n## 风格参考\n").append(styleContext);
        }
        
        return sb.toString();
    }
}

/**
 * 角色状态（简化版）
 */
public record CharacterState(
    UUID characterId,
    String name,
    String currentState,
    Map<String, Object> attributes
) {}
```

### 2. EnrichedChatRequest

增强的聊天请求，包含预处理上下文。由于 `ChatRequest` 是 record 类型，我们创建新的包装类型。

```java
package com.inkflow.module.agent.workflow;

import com.inkflow.module.agent.dto.ChatRequest;
import com.inkflow.module.agent.skill.SkillSlot;
import java.util.List;

/**
 * 增强的聊天请求
 * 包含预处理上下文和激活的技能
 * 
 * 设计说明：
 * - ChatRequest 是 record，不可变
 * - EnrichedChatRequest 包装 ChatRequest 并添加上下文
 * - Agent 接收 EnrichedChatRequest 而不是 ChatRequest
 */
public record EnrichedChatRequest(
    ChatRequest original,
    PreprocessingContext context,
    List<SkillSlot> activeSkills,
    String enhancedSystemPrompt
) {
    /**
     * 从原始请求创建（空上下文）
     */
    public static EnrichedChatRequest from(ChatRequest request) {
        return new EnrichedChatRequest(request, PreprocessingContext.empty(), List.of(), null);
    }
    
    /**
     * 添加预处理上下文
     */
    public EnrichedChatRequest withContext(PreprocessingContext context) {
        return new EnrichedChatRequest(original, context, activeSkills, enhancedSystemPrompt);
    }
    
    /**
     * 添加技能和增强的系统提示词
     */
    public EnrichedChatRequest withSkills(List<SkillSlot> skills, String systemPrompt) {
        return new EnrichedChatRequest(original, context, skills, systemPrompt);
    }
    
    /**
     * 便捷方法：获取项目 ID
     */
    public java.util.UUID projectId() {
        return original.projectId();
    }
    
    /**
     * 便捷方法：获取用户消息
     */
    public String message() {
        return original.message();
    }
    
    /**
     * 便捷方法：获取会话 ID
     */
    public String sessionId() {
        return original.sessionId();
    }
}
```


### 3. WorkflowExecutor

工作流执行器，根据 Intent 选择并执行对应工作流。

```java
@Component
public class WorkflowExecutor {
    
    private final Map<WorkflowType, Workflow> workflows;
    private final ContextBus contextBus;
    
    public Flux<ServerSentEvent<String>> execute(Intent intent, ChatRequest request) {
        WorkflowType type = WorkflowType.fromIntent(intent);
        Workflow workflow = workflows.get(type);
        
        publishThought(request.sessionId(), "选择工作流: " + type.getDisplayName());
        return workflow.execute(request);
    }
    
    private void publishThought(UUID sessionId, String thought) {
        contextBus.publish(new ContextEvent(sessionId, "thought", thought));
    }
}
```

### 4. Workflow 接口

```java
public interface Workflow {
    
    String getName();
    
    List<Intent> getSupportedIntents();
    
    Flux<ServerSentEvent<String>> execute(ChatRequest request);
}
```

### 5. AbstractWorkflow（修订版）

工作流基类，提供 预处理 → Skill 注入 → Agent 执行 → 后处理 的模板。

```java
public abstract class AbstractWorkflow implements Workflow {
    
    protected final AgentOrchestrator orchestrator;
    protected final PromptInjector promptInjector;
    protected final ContextBus contextBus;
    
    /**
     * Phase 0: 并行预处理（子类实现）
     * 默认返回空上下文
     */
    protected Mono<PreprocessingContext> preprocess(ChatRequest request) {
        return Mono.just(PreprocessingContext.empty());
    }
    
    /**
     * 获取主执行 Agent
     */
    protected abstract Agent getMainAgent(ChatRequest request);
    
    /**
     * 是否需要 Skill 注入
     */
    protected boolean needsSkillInjection() {
        return false;
    }
    
    /**
     * Phase 3: 同步后处理（子类实现，可选）
     * 返回后处理结果，会作为 check_result 事件发送
     */
    protected Mono<PostProcessingResult> postprocess(ChatRequest request, String generatedContent) {
        return Mono.empty();
    }
    
    @Override
    public final Flux<ServerSentEvent<String>> execute(ChatRequest request) {
        StringBuilder generatedContent = new StringBuilder();
        
        return preprocess(request)  // Phase 0: 并行预处理
            .doOnNext(ctx -> publishThought(request.sessionId(), "预处理完成，获取到 " + ctx.ragResults().size() + " 条相关设定"))
            .flatMapMany(context -> {
                // Phase 1: Skill 注入（基于预处理结果）
                EnrichedChatRequest enrichedRequest = EnrichedChatRequest.from(request).withContext(context);
                if (needsSkillInjection()) {
                    enrichedRequest = injectSkills(enrichedRequest);
                }
                
                // Phase 2: Agent 执行
                Agent agent = getMainAgent(request);
                publishThought(request.sessionId(), "执行 " + agent.getName());
                return agent.stream(enrichedRequest);
            })
            .doOnNext(chunk -> generatedContent.append(chunk))
            .map(content -> SSEEvent.content(content).toServerSentEvent())
            .concatWith(executePostProcessing(request, generatedContent));  // Phase 3: 同步后处理
    }
    
    private Flux<ServerSentEvent<String>> executePostProcessing(ChatRequest request, StringBuilder content) {
        return Flux.defer(() -> {
            String generated = content.toString();
            if (generated.isEmpty()) {
                return Flux.just(SSEEvent.done().toServerSentEvent());
            }
            
            return postprocess(request, generated)
                .flatMapMany(result -> Flux.just(
                    SSEEvent.event("processing_check", "正在检查一致性...").toServerSentEvent(),
                    SSEEvent.event("check_result", result.toJson()).toServerSentEvent(),
                    SSEEvent.done().toServerSentEvent()
                ))
                .switchIfEmpty(Flux.just(SSEEvent.done().toServerSentEvent()));
        });
    }
    
    private EnrichedChatRequest injectSkills(EnrichedChatRequest request) {
        SkillContext skillContext = new SkillContext(
            request.original().currentPhase(),
            request.original().message(),
            Map.of("context", request.context())  // 传入预处理上下文
        );
        
        List<SkillSlot> skills = promptInjector.autoSelectSkills(
            getMainAgent(request.original()).getClass(),
            skillContext
        );
        
        if (!skills.isEmpty()) {
            publishThought(request.original().sessionId(), 
                "激活技能: " + skills.stream().map(SkillSlot::getName).collect(Collectors.joining(", ")));
            String enhancedPrompt = promptInjector.buildEnhancedPrompt(skills, skillContext);
            return request.withSkills(skills, enhancedPrompt);
        }
        return request;
    }
}
```


### 6. ContentGenerationWorkflow（修订版）

内容生成工作流：并行预处理 + Skill 注入 + WriterAgent + 同步一致性检查

```java
@Component
public class ContentGenerationWorkflow extends AbstractWorkflow {
    
    private final WriterAgent writerAgent;
    private final ConsistencyAgent consistencyAgent;
    private final HybridSearchService hybridSearchService;  // 已有服务
    private final StateRetrievalService stateService;
    private final StyleRetrieveTool styleRetrieveTool;  // 复用已有 Tool
    
    @Override
    public String getName() { return "内容生成"; }
    
    @Override
    public List<Intent> getSupportedIntents() {
        return List.of(Intent.WRITE_CONTENT);
    }
    
    @Override
    protected CapableAgent<ChatRequest, String> getMainAgent(ChatRequest request) {
        return writerAgent;
    }
    
    @Override
    protected boolean needsSkillInjection() {
        return true;
    }
    
    @Override
    protected Mono<PreprocessingContext> preprocess(ChatRequest request) {
        publishThought(request.sessionId(), "并行预处理: RAG检索 + 角色状态 + 风格样本");
        
        // 使用 AgentOrchestrator.executeParallel3() 类型安全的并行执行
        // Requirements: 15.1, 15.2, 15.3 - 使用 subscribeOn(Schedulers.boundedElastic()) 避免阻塞 Netty IO 线程
        return Mono.fromCallable(() -> {
            var results = orchestrator.executeParallel3(
                // Task 1: RAG 检索
                () -> hybridSearchService.search(request.projectId(), request.message(), 5).block(),
                // Task 2: 角色状态
                () -> stateService.getCurrentStates(request.projectId()),
                // Task 3: 风格样本（复用已有 Tool 的逻辑）
                () -> hybridSearchService.buildContextForGeneration(
                    request.projectId(), request.message(), 3).block()
            );
            
            return new PreprocessingContext(
                results.result1() != null ? results.result1() : List.of(),
                results.result2() != null ? results.result2() : Map.of(),
                results.result3() != null ? results.result3() : "",
                Map.of()
            );
        }).subscribeOn(Schedulers.boundedElastic()); // 关键：调度到弹性线程池，避免阻塞 Netty IO 线程
    }
    
    @Override
    protected Mono<PostProcessingResult> postprocess(ChatRequest request, String generatedContent) {
        publishThought(request.sessionId(), "执行一致性检查...");
        
        return Mono.fromCallable(() -> {
            // 复用 ConsistencyAgent 的同步执行
            EnrichedChatRequest checkRequest = EnrichedChatRequest.from(
                new ChatRequest(
                    generatedContent,
                    request.projectId(),
                    request.sessionId(),
                    request.currentPhase(),
                    null,
                    Map.of("checkType", "post_generation")
                )
            );
            String result = consistencyAgent.execute(checkRequest);
            return new PostProcessingResult("consistency_check", result, List.of());
        });
    }
}
```

### 7. CreativeDesignWorkflow

创意设计工作流：并行预处理 + CharacterAgent/WorldBuilderAgent + 异步副作用

```java
@Component
public class CreativeDesignWorkflow extends AbstractWorkflow {
    
    private final CharacterAgent characterAgent;
    private final WorldBuilderAgent worldBuilderAgent;
    private final HybridSearchService ragService;
    private final CharacterArchetypeService archetypeService;
    private final RelationshipGraphService relationshipService;
    
    private static final ExecutorService ASYNC_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    
    @Override
    public String getName() { return "创意设计"; }
    
    @Override
    public List<Intent> getSupportedIntents() {
        return List.of(
            Intent.PLAN_CHARACTER, Intent.DESIGN_RELATIONSHIP, Intent.MATCH_ARCHETYPE,
            Intent.PLAN_WORLD, Intent.BRAINSTORM_IDEA
        );
    }
    
    @Override
    protected Agent getMainAgent(ChatRequest request) {
        Intent intent = Intent.matchFromMessage(request.message()).orElse(Intent.PLAN_CHARACTER);
        return switch (intent) {
            case PLAN_WORLD, BRAINSTORM_IDEA -> worldBuilderAgent;
            default -> characterAgent;
        };
    }
    
    @Override
    protected Mono<PreprocessingContext> preprocess(ChatRequest request) {
        publishThought(request.sessionId(), "并行预处理: RAG检索 + 原型库");
        
        return Mono.fromFuture(() -> orchestrator.executeParallel(
            () -> ragService.hybridSearch(request.projectId(), request.message(), 5),
            () -> archetypeService.getAllArchetypes()
        )).map(results -> new PreprocessingContext(
            (List<SearchResult>) results[0],
            Map.of(),
            List.of(),
            Map.of("archetypes", results[1])
        ));
    }
    
    @Override
    protected Mono<PostProcessingResult> postprocess(ChatRequest request, String generatedContent) {
        // 异步更新关系图（不阻塞响应）
        Intent intent = Intent.matchFromMessage(request.message()).orElse(Intent.PLAN_CHARACTER);
        
        if (intent == Intent.PLAN_CHARACTER || intent == Intent.DESIGN_RELATIONSHIP) {
            CompletableFuture.runAsync(() -> {
                publishThought(request.sessionId(), "异步更新角色关系图...");
                relationshipService.refreshGraph(request.projectId());
            }, ASYNC_EXECUTOR);
        }
        
        return Mono.empty();  // 不阻塞，直接返回 done
    }
}
```


### 8. SimpleAgentWorkflow

简单工作流：直接执行，无预处理，无 Skill 注入，无后处理

```java
@Component
public class SimpleAgentWorkflow implements Workflow {
    
    private final Map<Intent, Agent> agentMap;
    
    @Override
    public String getName() { return "简单任务"; }
    
    @Override
    public List<Intent> getSupportedIntents() {
        return List.of(
            Intent.GENERAL_CHAT, Intent.GENERATE_NAME, 
            Intent.SUMMARIZE, Intent.EXTRACT_ENTITY
        );
    }
    
    @Override
    public Flux<ServerSentEvent<String>> execute(ChatRequest request) {
        Intent intent = Intent.matchFromMessage(request.message()).orElse(Intent.GENERAL_CHAT);
        Agent agent = agentMap.get(intent);
        
        // 直接执行，无预处理
        EnrichedChatRequest enrichedRequest = EnrichedChatRequest.from(request);
        
        return agent.stream(enrichedRequest)
            .map(content -> SSEEvent.content(content).toServerSentEvent())
            .concatWith(Flux.just(SSEEvent.done().toServerSentEvent()));
    }
}
```

### 9. Agent 接口修改策略

**现状分析**：
- 当前 `Agent<I, O>` 接口是泛型的，`BaseAgent<ChatRequest, String>` 实现它
- 所有 Agent 都继承 `BaseAgent`，接收 `ChatRequest`
- 直接修改接口会影响所有 Agent

**Agent 无状态约束（Requirements: 16.1-16.4）**：

⚠️ **关键约束**：Agent 类必须是无状态的，禁止在实例字段中存储请求相关的状态。

```java
// ❌ 错误示例：在实例字段存储请求状态（会导致并发污染）
@Component
public class WriterAgent extends BaseAgent<ChatRequest, String> {
    private String currentSystemPrompt;  // 禁止！
    private PreprocessingContext currentContext;  // 禁止！
    
    public void setContext(PreprocessingContext ctx) {
        this.currentContext = ctx;  // 并发请求会互相覆盖
    }
}

// ✅ 正确示例：所有状态通过参数传递
@Component
public class WriterAgent extends BaseAgent<ChatRequest, String> {
    // 只有无状态的依赖注入
    private final HybridSearchService searchService;
    private final ContextBus contextBus;
    
    @Override
    protected String buildUserPrompt(ChatRequest input) {
        // 从 input.metadata() 获取上下文，不存储到字段
        PreprocessingContext context = (PreprocessingContext) 
            input.metadata().get("preprocessingContext");
        // ...
    }
}
```

**修改策略**：采用适配器模式，保持向后兼容

```java
/**
 * 方案 A：在 Workflow 层做适配（推荐）
 * 不修改 Agent 接口，在 Workflow 中将 EnrichedChatRequest 转换为 ChatRequest
 * 并将上下文注入到 ChatRequest.metadata 中
 */
public abstract class AbstractWorkflow {
    
    protected Flux<String> executeAgent(CapableAgent<ChatRequest, String> agent, EnrichedChatRequest enriched) {
        // 将上下文注入到 metadata
        Map<String, Object> metadata = new HashMap<>(
            enriched.original().metadata() != null ? enriched.original().metadata() : Map.of()
        );
        metadata.put("preprocessingContext", enriched.context());
        metadata.put("enhancedSystemPrompt", enriched.enhancedSystemPrompt());
        
        // 创建新的 ChatRequest（带上下文）
        ChatRequest requestWithContext = new ChatRequest(
            enriched.original().message(),
            enriched.original().projectId(),
            enriched.original().sessionId(),
            enriched.original().currentPhase(),
            enriched.original().intentHint(),
            metadata
        );
        
        return agent.stream(requestWithContext);
    }
}

/**
 * 方案 B：修改 Agent 的 buildUserPrompt 方法
 * Agent 从 metadata 中读取预处理上下文，而不是自己调用 RAG
 */
// 在 WriterAgent.buildUserPrompt() 中：
@Override
protected String buildUserPrompt(ChatRequest input) {
    StringBuilder prompt = new StringBuilder();
    
    // 从 metadata 获取预处理上下文（如果有）
    PreprocessingContext context = (PreprocessingContext) 
        input.metadata().get("preprocessingContext");
    
    if (context != null && !context.toContextString().isEmpty()) {
        // 使用预处理的上下文
        prompt.append(context.toContextString()).append("\n\n");
    } else {
        // 降级：自己做 RAG（向后兼容）
        String ragContext = retrieveContext(input.projectId().toString(), input.message());
        if (ragContext != null && !ragContext.isEmpty()) {
            prompt.append("【相关设定】\n").append(ragContext).append("\n\n");
        }
    }
    
    prompt.append("【创作要求】\n").append(input.message());
    return prompt.toString();
}
```

### 10. WriterAgent 修改示例

修改 `buildUserPrompt()` 方法，优先使用预处理上下文，降级时才自己做 RAG。

```java
/**
 * WriterAgent 修改后的 buildUserPrompt 方法
 * 
 * 变更点：
 * 1. 优先从 metadata.preprocessingContext 获取上下文
 * 2. 如果没有预处理上下文，降级到原有的 RAG 调用（向后兼容）
 * 3. 系统提示词支持 Skill 注入
 */
@Override
protected String buildUserPrompt(ChatRequest input) {
    StringBuilder prompt = new StringBuilder();
    
    // 尝试从 metadata 获取预处理上下文
    PreprocessingContext context = null;
    if (input.metadata() != null) {
        context = (PreprocessingContext) input.metadata().get("preprocessingContext");
    }
    
    if (context != null) {
        // 使用 Workflow 预处理的上下文
        String contextStr = context.toContextString();
        if (!contextStr.isEmpty()) {
            prompt.append(contextStr).append("\n\n");
        }
        publishThought("使用预处理上下文");
    } else {
        // 降级：自己做 RAG（向后兼容，支持直接调用 Agent）
        publishThought("检索相关设定...");
        String ragContext = retrieveContext(input.projectId().toString(), input.message());
        if (ragContext != null && !ragContext.isEmpty()) {
            prompt.append("【相关设定】\n").append(ragContext).append("\n\n");
        }
        
        publishThought("匹配写作风格...");
        String styleContext = retrieveStyle(input.projectId().toString(), input.message());
        if (styleContext != null && !styleContext.isEmpty()) {
            prompt.append("【参考风格】\n").append(styleContext).append("\n\n");
        }
    }
    
    // 添加会话上下文（保持不变）
    if (input.sessionId() != null) {
        SessionContext sessionContext = contextBus.getContext(input.sessionId());
        if (sessionContext != null && !sessionContext.recentEntities().isEmpty()) {
            prompt.append("【最近讨论的内容】\n");
            sessionContext.recentEntities().stream()
                .limit(5)
                .forEach(entity -> prompt.append("- ")
                    .append(entity.entityType()).append(": ")
                    .append(entity.entityName()).append("\n"));
            prompt.append("\n");
        }
    }
    
    prompt.append("【创作要求】\n").append(input.message());
    return prompt.toString();
}

/**
 * 修改后的 getSystemPrompt 方法
 * 支持 Skill 注入的增强提示词
 */
@Override
protected String getSystemPrompt() {
    // 基础系统提示词
    return """
        你是一位专业的小说创作助手，擅长各种类型的小说写作。
        ...（保持原有内容）
        """;
}

/**
 * 新增：获取增强的系统提示词
 * 如果 metadata 中有 enhancedSystemPrompt，使用它
 */
protected String getEnhancedSystemPrompt(ChatRequest input) {
    if (input.metadata() != null && input.metadata().containsKey("enhancedSystemPrompt")) {
        return (String) input.metadata().get("enhancedSystemPrompt");
    }
    return getSystemPrompt();
}
```

## Data Models

### WorkflowType 枚举

```java
public enum WorkflowType {
    CONTENT_GENERATION("内容生成", List.of(Intent.WRITE_CONTENT)),
    CREATIVE_DESIGN("创意设计", List.of(
        Intent.PLAN_CHARACTER, Intent.DESIGN_RELATIONSHIP, Intent.MATCH_ARCHETYPE,
        Intent.PLAN_WORLD, Intent.BRAINSTORM_IDEA
    )),
    PLANNING("大纲规划", List.of(
        Intent.PLAN_OUTLINE, Intent.MANAGE_PLOTLOOP, Intent.ANALYZE_PACING
    )),
    QUALITY_CHECK("质量检查", List.of(
        Intent.CHECK_CONSISTENCY, Intent.ANALYZE_STYLE
    )),
    SIMPLE_AGENT("简单任务", List.of(
        Intent.GENERAL_CHAT, Intent.GENERATE_NAME, Intent.SUMMARIZE, Intent.EXTRACT_ENTITY
    ));
    
    private final String displayName;
    private final List<Intent> supportedIntents;
    
    public static WorkflowType fromIntent(Intent intent) {
        for (WorkflowType type : values()) {
            if (type.supportedIntents.contains(intent)) {
                return type;
            }
        }
        return SIMPLE_AGENT;
    }
}
```

### PostProcessingResult

后处理结果。

```java
public record PostProcessingResult(
    String type,
    String content,
    List<ConsistencyWarning> warnings
) {
    public String toJson() {
        return new ObjectMapper().writeValueAsString(this);
    }
}
```

## 链式执行工作流设计

### 11. AbstractChainWorkflow

链式工作流基类，支持多 Agent 顺序执行和用户交互。

```java
package com.inkflow.module.agent.workflow;

import com.inkflow.module.agent.core.CapableAgent;
import com.inkflow.module.agent.dto.ChatRequest;
import com.inkflow.module.agent.orchestration.AgentOrchestrator;
import com.inkflow.module.agent.orchestration.chain.ChainExecutionContext;
import com.inkflow.module.agent.event.SSEEvent;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 链式工作流基类
 * 支持多 Agent 顺序执行，前一个 Agent 的输出作为后一个的输入
 * 支持用户交互（暂停等待用户选择）
 * 
 * Requirements: 13.1-13.5, 14.1-14.5
 */
public abstract class AbstractChainWorkflow implements Workflow {
    
    protected final AgentOrchestrator orchestrator;
    
    /**
     * 获取 Agent 执行链
     */
    protected abstract List<ChainStep> getChainSteps(ChatRequest request);
    
    /**
     * 是否需要用户交互
     */
    protected boolean requiresUserInteraction() {
        return false;
    }
    
    /**
     * 获取用户交互点（在哪个步骤后暂停）
     */
    protected int getUserInteractionAfterStep() {
        return -1; // -1 表示不需要用户交互
    }
    
    @Override
    public Flux<ServerSentEvent<String>> execute(ChatRequest request) {
        // 断点续传：检查请求是否包含链式上下文（用户交互后的后续请求）
        // Requirements: 14.6, 14.7
        if (request.metadata() != null && request.metadata().containsKey("chainContext")) {
            String userSelection = request.message(); // 用户的新输入作为选择
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
                .filter(step -> step.type() == ChainStepType.AGENT)
                .map(ChainStep::agent)
                .toList();
            
            publishThought(request.sessionId(), "开始链式执行: " + agents.size() + " 个 Agent");
            
            return Mono.fromFuture(() -> 
                orchestrator.executeChainWithContextAsync(
                    request.message(),
                    agents,
                    request
                )
            ).flatMapMany(context -> {
                if (context.isAborted()) {
                    return Flux.just(
                        SSEEvent.error("链式执行中断: " + context.getAbortReason()).toServerSentEvent()
                    );
                }
                
                // 发送所有 Agent 的输出
                return Flux.fromIterable(context.getAllOutputs())
                    .flatMap(output -> Flux.just(
                        SSEEvent.thought(output.agentName() + " 完成").toServerSentEvent(),
                        SSEEvent.content(output.content()).toServerSentEvent()
                    ))
                    .concatWith(Flux.just(
                        SSEEvent.event("chain_summary", context.getSummary()).toServerSentEvent(),
                        SSEEvent.done().toServerSentEvent()
                    ));
            });
        });
    }
    
    /**
     * 执行带用户交互的链
     */
    private Flux<ServerSentEvent<String>> executeWithUserInteraction(ChatRequest request, List<ChainStep> steps) {
        int interactionPoint = getUserInteractionAfterStep();
        
        // 分割步骤：交互前 + 交互后
        List<ChainStep> beforeInteraction = steps.subList(0, interactionPoint + 1);
        List<ChainStep> afterInteraction = steps.subList(interactionPoint + 1, steps.size());
        
        return Flux.defer(() -> {
            // 执行交互前的步骤
            List<CapableAgent<ChatRequest, String>> beforeAgents = beforeInteraction.stream()
                .filter(step -> step.type() == ChainStepType.AGENT)
                .map(ChainStep::agent)
                .toList();
            
            return Mono.fromFuture(() -> 
                orchestrator.executeChainWithContextAsync(
                    request.message(),
                    beforeAgents,
                    request
                )
            ).flatMapMany(context -> {
                if (context.isAborted()) {
                    return Flux.just(
                        SSEEvent.error("链式执行中断: " + context.getAbortReason()).toServerSentEvent()
                    );
                }
                
                // 发送交互前的输出
                Flux<ServerSentEvent<String>> beforeOutput = Flux.fromIterable(context.getAllOutputs())
                    .flatMap(output -> Flux.just(
                        SSEEvent.thought(output.agentName() + " 完成").toServerSentEvent(),
                        SSEEvent.content(output.content()).toServerSentEvent()
                    ));
                
                // 发送用户交互请求事件
                Flux<ServerSentEvent<String>> interactionRequest = Flux.just(
                    SSEEvent.event("user_input_required", buildInteractionOptions(context)).toServerSentEvent()
                );
                
                // 注意：此时流暂停，等待用户通过新请求继续
                // 用户选择后，前端应发送新请求，带上 metadata.chainContext 和 metadata.userSelection
                
                return Flux.concat(beforeOutput, interactionRequest);
            });
        });
    }
    
    /**
     * 继续执行链（用户选择后）
     */
    public Flux<ServerSentEvent<String>> continueChain(ChatRequest request, String userSelection) {
        List<ChainStep> steps = getChainSteps(request);
        int interactionPoint = getUserInteractionAfterStep();
        List<ChainStep> afterInteraction = steps.subList(interactionPoint + 1, steps.size());
        
        // 构建带用户选择的新请求
        String enrichedMessage = "用户选择: " + userSelection + "\n\n原始请求: " + request.message();
        ChatRequest enrichedRequest = new ChatRequest(
            enrichedMessage,
            request.projectId(),
            request.sessionId(),
            request.currentPhase(),
            request.intentHint(),
            request.metadata()
        );
        
        List<CapableAgent<ChatRequest, String>> afterAgents = afterInteraction.stream()
            .filter(step -> step.type() == ChainStepType.AGENT)
            .map(ChainStep::agent)
            .toList();
        
        return executeFullChain(enrichedRequest, afterInteraction.stream()
            .map(step -> new ChainStep(step.type(), step.agent(), step.description()))
            .toList());
    }
    
    /**
     * 构建交互选项
     */
    protected String buildInteractionOptions(ChainExecutionContext context) {
        // 子类可以覆盖此方法提供具体的选项
        return context.getFinalResult();
    }
    
    protected void publishThought(String sessionId, String thought) {
        // 发布思考事件
    }
}

/**
 * 链步骤类型
 */
public enum ChainStepType {
    AGENT,           // Agent 执行
    USER_INTERACTION // 用户交互
}

/**
 * 链步骤
 */
public record ChainStep(
    ChainStepType type,
    CapableAgent<ChatRequest, String> agent,
    String description
) {
    public static ChainStep agent(CapableAgent<ChatRequest, String> agent, String description) {
        return new ChainStep(ChainStepType.AGENT, agent, description);
    }
    
    public static ChainStep userInteraction(String description) {
        return new ChainStep(ChainStepType.USER_INTERACTION, null, description);
    }
}
```

### 12. BrainstormExpandWorkflow

头脑风暴 + 扩写工作流：先生成多个方案，用户选择后扩写。

```java
@Component
public class BrainstormExpandWorkflow extends AbstractChainWorkflow {
    
    private final BrainstormAgent brainstormAgent;
    private final WriterAgent writerAgent;
    
    @Override
    public String getName() { return "头脑风暴扩写"; }
    
    @Override
    public List<Intent> getSupportedIntents() {
        return List.of(Intent.BRAINSTORM_AND_EXPAND);
    }
    
    @Override
    protected List<ChainStep> getChainSteps(ChatRequest request) {
        return List.of(
            ChainStep.agent(brainstormAgent, "生成多个创意方案"),
            ChainStep.userInteraction("用户选择方案"),
            ChainStep.agent(writerAgent, "扩写选中的方案")
        );
    }
    
    @Override
    protected boolean requiresUserInteraction() {
        return true;
    }
    
    @Override
    protected int getUserInteractionAfterStep() {
        return 0; // 在第一个 Agent（BrainstormAgent）后暂停
    }
    
    @Override
    protected String buildInteractionOptions(ChainExecutionContext context) {
        // 解析 BrainstormAgent 的输出，提取选项
        String brainstormOutput = context.getFinalResult();
        return """
            {
                "type": "selection",
                "message": "请选择一个方案进行扩写",
                "options": %s
            }
            """.formatted(brainstormOutput);
    }
}
```

### 13. OutlineToChapterWorkflow

大纲到章节工作流：先规划大纲，然后生成章节内容。

```java
@Component
public class OutlineToChapterWorkflow extends AbstractChainWorkflow {
    
    private final PlannerAgent plannerAgent;
    private final WriterAgent writerAgent;
    
    @Override
    public String getName() { return "大纲到章节"; }
    
    @Override
    public List<Intent> getSupportedIntents() {
        return List.of(Intent.OUTLINE_TO_CHAPTER);
    }
    
    @Override
    protected List<ChainStep> getChainSteps(ChatRequest request) {
        return List.of(
            ChainStep.agent(plannerAgent, "规划章节大纲"),
            ChainStep.agent(writerAgent, "生成章节内容")
        );
    }
    
    // 无用户交互，使用默认实现
}
```

### 14. CharacterToSceneWorkflow

角色到场景工作流：先设计角色，然后生成角色出场场景。

```java
@Component
public class CharacterToSceneWorkflow extends AbstractChainWorkflow {
    
    private final CharacterAgent characterAgent;
    private final WriterAgent writerAgent;
    
    @Override
    public String getName() { return "角色到场景"; }
    
    @Override
    public List<Intent> getSupportedIntents() {
        return List.of(Intent.CHARACTER_TO_SCENE);
    }
    
    @Override
    protected List<ChainStep> getChainSteps(ChatRequest request) {
        return List.of(
            ChainStep.agent(characterAgent, "设计角色详情"),
            ChainStep.agent(writerAgent, "生成角色出场场景")
        );
    }
    
    // 无用户交互，使用默认实现
}
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Intent-to-Workflow Mapping Consistency

*For any* valid Intent, the WorkflowExecutor SHALL always select the same WorkflowType, and the selected workflow SHALL support that Intent.

**Validates: Requirements 1.1**

### Property 2: Preprocessing Context Injection

*For any* workflow that defines a non-empty `preprocess()` method, the Agent SHALL receive a ChatRequest with metadata containing "preprocessingContext", and when this context exists, the Agent SHALL NOT make direct calls to RAG/State services.

**Validates: Requirements 2.1, 10.1, 11.1-11.4**

### Property 3: ContentGenerationWorkflow Triggers Synchronous Consistency Check

*For any* WRITE_CONTENT intent execution that completes successfully, the system SHALL invoke ConsistencyAgent BEFORE sending the `done` event, and SHALL emit a `check_result` event with the consistency check results.

**Validates: Requirements 3.4, 3.5**

### Property 4: Skill Selection Based on Context

*For any* ContentGenerationWorkflow execution, Skill selection SHALL occur AFTER preprocessing completes, and the SkillContext SHALL include the preprocessing results.

**Validates: Requirements 8.2**

### Property 5: SimpleAgentWorkflow Direct Execution

*For any* Intent in SimpleAgentWorkflow's supported list, the workflow SHALL directly invoke the corresponding Agent with an empty PreprocessingContext, without calling `preprocess()` or `postprocess()`.

**Validates: Requirements 7.2, 7.3**

### Property 6: CreativeDesignWorkflow Updates Relationship Graph

*For any* PLAN_CHARACTER or DESIGN_RELATIONSHIP intent execution that completes successfully, the system SHALL asynchronously invoke RelationshipGraphService.refreshGraph().

**Validates: Requirements 4.3**

### Property 7: Event Ordering

*For any* workflow execution, the event sequence SHALL be: [preprocessing events] → [content events] → [processing_check event (if applicable)] → [check_result event (if applicable)] → [done event].

**Validates: Requirements 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7**

### Property 8: Chain Execution Context Propagation

*For any* chain workflow execution, the output of Agent N SHALL be available to Agent N+1 via ChainExecutionContext.buildNextInput(), and the final ChainExecutionContext SHALL contain outputs from all executed Agents.

**Validates: Requirements 13.1, 13.2**

### Property 9: Chain Execution Abort on Failure

*For any* chain workflow execution where Agent N fails, the system SHALL abort the chain, set ChainExecutionContext.aborted to true, and emit an error event containing the failure context.

**Validates: Requirements 13.3**

### Property 10: User Interaction Pause in Chain Workflow

*For any* chain workflow that requires user interaction, the system SHALL emit a "user_input_required" event at the interaction point and pause execution until continueChain() is called with user selection.

**Validates: Requirements 13.5, 14.5**

### Property 11: Agent Fallback to Direct RAG

*For any* Agent execution where metadata.preprocessingContext is absent, the Agent SHALL fallback to direct RAG/State/Style service calls for backward compatibility.

**Validates: Requirements 11.4**

### Property 12: Chain Continuation from Context

*For any* request containing metadata.chainContext, the AbstractChainWorkflow SHALL invoke continueChain() instead of starting a new chain, and the chain SHALL resume from the interaction point with the user's selection.

**Validates: Requirements 14.6, 14.7**

### Property 13: Non-Blocking Preprocessing

*For any* workflow preprocessing execution, the system SHALL NOT block Reactor/Netty IO threads, and all blocking operations SHALL be scheduled on Schedulers.boundedElastic().

**Validates: Requirements 15.1, 15.2, 15.3**

### Property 14: Agent Statelessness

*For any* Agent class, the Agent SHALL NOT store request-specific state in instance fields, and concurrent requests SHALL have isolated context without cross-contamination.

**Validates: Requirements 16.1, 16.2, 16.3, 16.4**

## Error Handling

### 预处理失败

- 单个预处理任务失败：记录警告，继续执行，使用空结果
- 所有预处理任务失败：记录错误，使用空 PreprocessingContext 继续执行 Agent

### Agent 执行失败

- 重试机制：使用指数退避重试（最多 3 次）
- 最终失败：发送 error 事件，返回用户友好的错误消息

### 后处理失败

- 同步后处理失败：记录错误，发送 error 事件，但仍然发送 done 事件
- 异步后处理失败：记录错误日志，不影响用户响应

## Testing Strategy

### 单元测试

- WorkflowType.fromIntent() 映射正确性
- PreprocessingContext.toContextString() 格式化
- EnrichedChatRequest 构建和转换

### 属性测试 (jqwik)

- Property 1: Intent-to-Workflow 映射一致性
- Property 2: 预处理上下文注入
- Property 5: SimpleAgentWorkflow 直接执行
- Property 7: 事件顺序

### 集成测试

- ContentGenerationWorkflow 完整流程（Mock Agent 和 Services）
- 同步后处理触发验证
- 事件顺序验证

## 代码变更清单

### 需要新增的代码

1. **`PreprocessingContext`** - 预处理上下文 record
2. **`EnrichedChatRequest`** - 增强的聊天请求 record
3. **`PostProcessingResult`** - 后处理结果 record
4. **`Workflow` 接口** - 工作流接口
5. **`AbstractWorkflow`** - 工作流抽象基类
6. **`WorkflowExecutor`** - 工作流执行器
7. **`ContentGenerationWorkflow`** - 内容生成工作流
8. **`CreativeDesignWorkflow`** - 创意设计工作流
9. **`SimpleAgentWorkflow`** - 简单 Agent 工作流
10. **`WorkflowType` 枚举** - 工作流类型
11. **`AbstractChainWorkflow`** - 链式工作流抽象基类
12. **`ChainStep`** - 链步骤 record
13. **`ChainStepType`** - 链步骤类型枚举
14. **`BrainstormExpandWorkflow`** - 头脑风暴扩写工作流
15. **`OutlineToChapterWorkflow`** - 大纲到章节工作流
16. **`CharacterToSceneWorkflow`** - 角色到场景工作流
17. **新增 Intent**: `BRAINSTORM_AND_EXPAND`, `OUTLINE_TO_CHAPTER`, `CHARACTER_TO_SCENE`

### 需要修改的代码

1. **`WriterAgent.buildUserPrompt()`** - 优先使用 metadata 中的预处理上下文
2. **`CharacterAgent.buildUserPrompt()`** - 同上
3. **`WorldBuilderAgent.buildUserPrompt()`** - 同上（如果有 RAG 调用）
4. **`AgentRouter.route()`** - 委托给 WorkflowExecutor
5. **`BaseAgent.stream()`** - 支持从 metadata 读取增强的系统提示词

### 需要删除的代码

1. **`SceneCreationOrchestrator`** - 功能被 ContentGenerationWorkflow 取代

### 需要保留的代码

1. **`AgentOrchestrator.executeParallel3()`** - 用于并行预处理
2. **`AgentOrchestrator.executeChain()`** - 保留用于未来多 Agent 协作
3. **`PromptInjector`** - 用于 Skill 注入
4. **`RAGSearchTool`** - 被 Workflow 调用（不再被 Agent 直接调用）
5. **`StyleRetrieveTool`** - 同上
6. **`HybridSearchService`** - 被 Workflow 调用
7. **Agent 内部的 RAG 调用** - 保留作为降级方案（向后兼容，仅当 metadata.preprocessingContext 不存在时触发）

### UPGRADE.md 反馈对齐总结

| UPGRADE.md 观点 | 本设计的处理 | 状态 |
|----------------|-------------|------|
| RAG 应该在 Workflow 层做，不是 Agent 层 | Agent 优先使用 metadata.preprocessingContext，降级时才自己做 RAG | ✅ 采纳 |
| 异步后处理无法反馈给前端 | 改为同步后处理，在 done 之前发送 check_result | ✅ 采纳 |
| Skill 注入应该在预处理之后 | Phase 1 在 Phase 0 之后执行，基于预处理结果选择 Skills | ✅ 采纳 |
| SimpleAgentWorkflow 可能退化成 AgentRouter | 明确 Simple Agent 不需要 RAG，如果需要则不 Simple | ✅ 明确 |
| 多 Agent 链式执行能力缺失 | 新增 AbstractChainWorkflow 和三个具体链式工作流，支持用户交互 | ✅ 完善 |
