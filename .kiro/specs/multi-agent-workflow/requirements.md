# Requirements Document

## Introduction

本文档定义了 InkFlow V2 后端的多 Agent 工作流编排系统。目标是让 AgentOrchestrator 的高级编排能力（并行执行、链式执行）真正发挥作用，而不是仅仅作为单 Agent 路由器。

**目标代码库**: `inkflow-backend-v2`

**背景问题**:
1. AgentOrchestrator 有 `executeParallel`、`executeChain`、`executeAny` 等方法，但从未被调用
2. AgentRouter 只选择单个 Agent 执行，没有多 Agent 协作
3. 每个 Agent 内部独立做 RAG 检索，缺乏统一的预处理阶段
4. Skill 注入在 Agent 内部，应该在工作流层面统一管理
5. SceneCreationOrchestrator 是唯一的编排实例，但模式没有泛化

**整合目标**:
- 根据 Intent 类型选择对应的工作流
- 工作流统一管理并行预处理、Agent 执行、后处理
- 复用 AgentOrchestrator 的高级编排能力

## Glossary

- **Workflow**: 针对特定意图类型的完整执行流程，包含预处理、Agent 执行、后处理
- **WorkflowExecutor**: 工作流执行器，根据 Intent 选择并执行对应工作流
- **PreprocessingContext**: 并行预处理阶段收集的上下文数据
- **PostProcessor**: 后处理器，如一致性检查、关系图更新等
- **AgentOrchestrator**: 底层编排器，提供并行/链式/竞争执行能力
- **Intent**: 用户意图枚举（WRITE_CONTENT, PLAN_CHARACTER 等）
- **CreationPhase**: 创作阶段枚举（IDEA, WORLDBUILDING, CHARACTER, OUTLINE, WRITING, REVISION）

## 工作流架构

### 整体执行流程

```
用户请求
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│                    AgentRouter                               │
│  FastPath / ThinkingAgent → IntentResult                    │
└─────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│                    WorkflowExecutor                          │
│  根据 Intent 选择工作流:                                      │
│  - WRITE_CONTENT → ContentGenerationWorkflow                │
│  - PLAN_CHARACTER → CharacterDesignWorkflow                 │
│  - PLAN_OUTLINE → OutlinePlanningWorkflow                   │
│  - CHECK_CONSISTENCY → ConsistencyCheckWorkflow             │
│  - GENERAL_CHAT → SimpleAgentWorkflow                       │
└─────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│                    Workflow Execution                        │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Phase 1: 并行预处理 (executeParallel)                │   │
│  │  ├─ RAG 检索                                        │   │
│  │  ├─ 状态获取                                        │   │
│  │  └─ 风格/设定获取                                   │   │
│  └─────────────────────────────────────────────────────┘   │
│                          │                                   │
│                          ▼                                   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Phase 2: Agent 执行 (stream)                        │   │
│  │  - 注入预处理上下文                                  │   │
│  │  - 注入适用的 Skills                                │   │
│  │  - 流式生成内容                                     │   │
│  └─────────────────────────────────────────────────────┘   │
│                          │                                   │
│                          ▼                                   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Phase 3: 后处理 (异步)                              │   │
│  │  - 一致性检查                                       │   │
│  │  - 关系图更新                                       │   │
│  │  - 知识库更新                                       │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
    │
    ▼
SSE 流式响应
```

### 工作流类型

| 工作流 | 触发意图 | 预处理 | 主 Agent | 后处理 |
|--------|----------|--------|----------|--------|
| ContentGenerationWorkflow | WRITE_CONTENT | RAG + 角色状态 + 风格 | WriterAgent | ConsistencyAgent |
| CharacterDesignWorkflow | PLAN_CHARACTER, DESIGN_RELATIONSHIP, MATCH_ARCHETYPE | RAG + 原型库 | CharacterAgent | 关系图更新 |
| WorldBuildingWorkflow | PLAN_WORLD, BRAINSTORM_IDEA | RAG | WorldBuilderAgent | 知识库更新 |
| OutlinePlanningWorkflow | PLAN_OUTLINE, MANAGE_PLOTLOOP, ANALYZE_PACING | RAG + 伏笔状态 | PlannerAgent | 无 |
| ConsistencyCheckWorkflow | CHECK_CONSISTENCY, ANALYZE_STYLE | RAG + Preflight | ConsistencyAgent | 无 |
| SimpleAgentWorkflow | GENERAL_CHAT, GENERATE_NAME, SUMMARIZE, EXTRACT_ENTITY | 无 | 对应 Agent | 无 |

### 链式工作流类型

| 工作流 | 触发意图 | Agent 链 | 用户交互 |
|--------|----------|----------|----------|
| BrainstormExpandWorkflow | BRAINSTORM_AND_EXPAND | BrainstormAgent → [用户选择] → WriterAgent | 选择结局/方案 |
| OutlineToChapterWorkflow | OUTLINE_TO_CHAPTER | PlannerAgent → WriterAgent | 无 |
| CharacterToSceneWorkflow | CHARACTER_TO_SCENE | CharacterAgent → WriterAgent | 无 |

## Requirements

### Requirement 1: 工作流执行器

**User Story:** As a developer, I want a WorkflowExecutor that selects and executes the appropriate workflow based on Intent, so that complex tasks benefit from multi-agent collaboration.

#### Acceptance Criteria

1. WHEN AgentRouter determines the Intent THEN the WorkflowExecutor SHALL select the corresponding workflow
2. WHEN a workflow is selected THEN the WorkflowExecutor SHALL execute all phases in order (preprocessing, agent execution, post-processing)
3. WHEN the Intent is GENERAL_CHAT or utility intents THEN the WorkflowExecutor SHALL use SimpleAgentWorkflow without preprocessing
4. WHEN workflow execution completes THEN the WorkflowExecutor SHALL return a unified SSE stream
5. WHEN any phase fails THEN the WorkflowExecutor SHALL emit error events and attempt graceful degradation

### Requirement 2: 并行预处理

**User Story:** As a developer, I want preprocessing tasks to run in parallel, so that context gathering is efficient.

#### Acceptance Criteria

1. WHEN a workflow requires preprocessing THEN the system SHALL execute all preprocessing tasks in parallel using AgentOrchestrator.executeParallel
2. WHEN RAG retrieval is needed THEN the system SHALL use HybridSearchService to fetch relevant context
3. WHEN character state is needed THEN the system SHALL fetch current character states from StateRetrievalService
4. WHEN style samples are needed THEN the system SHALL use StyleRetrieveTool to fetch matching samples
5. WHEN any preprocessing task fails THEN the system SHALL continue with available context and log warnings

### Requirement 3: 内容生成工作流

**User Story:** As a writer, I want content generation to automatically include relevant context and style matching, so that generated content is consistent with my story.

#### Acceptance Criteria

1. WHEN WRITE_CONTENT intent is detected THEN the system SHALL execute ContentGenerationWorkflow
2. WHEN preprocessing completes THEN the system SHALL inject RAG context, character states, and style samples into WriterAgent prompt via PreprocessingContext
3. WHEN WriterAgent is invoked THEN the system SHALL inject applicable Skills (ActionSkill, PsychologySkill, DescriptionSkill) based on content type and preprocessing context
4. WHEN content generation completes THEN the system SHALL synchronously trigger ConsistencyAgent for validation BEFORE sending the done event
5. WHEN consistency check completes THEN the system SHALL emit a check_result event containing the validation results

### Requirement 4: 角色设计工作流

**User Story:** As a writer, I want character design to consider existing characters and archetypes, so that new characters fit into my story.

#### Acceptance Criteria

1. WHEN PLAN_CHARACTER, DESIGN_RELATIONSHIP, or MATCH_ARCHETYPE intent is detected THEN the system SHALL execute CharacterDesignWorkflow
2. WHEN preprocessing completes THEN the system SHALL inject existing character information and archetype library into CharacterAgent prompt
3. WHEN character design completes THEN the system SHALL asynchronously update the relationship graph
4. WHEN relationship changes are detected THEN the system SHALL emit relationship update events

### Requirement 5: 大纲规划工作流

**User Story:** As a writer, I want outline planning to consider existing chapters and plot loops, so that the story structure is coherent.

#### Acceptance Criteria

1. WHEN PLAN_OUTLINE, MANAGE_PLOTLOOP, or ANALYZE_PACING intent is detected THEN the system SHALL execute OutlinePlanningWorkflow
2. WHEN preprocessing completes THEN the system SHALL inject existing chapter summaries and plot loop status into PlannerAgent prompt
3. WHEN outline planning completes THEN the system SHALL emit structure update events

### Requirement 6: 一致性检查工作流

**User Story:** As a writer, I want consistency checks to have full context, so that issues are accurately identified.

#### Acceptance Criteria

1. WHEN CHECK_CONSISTENCY or ANALYZE_STYLE intent is detected THEN the system SHALL execute ConsistencyCheckWorkflow
2. WHEN preprocessing completes THEN the system SHALL run Preflight checks and RAG retrieval in parallel
3. WHEN ConsistencyAgent completes THEN the system SHALL emit structured issue reports

### Requirement 7: 简单 Agent 工作流

**User Story:** As a developer, I want simple tasks to execute without unnecessary overhead, so that response time is minimized.

#### Acceptance Criteria

1. WHEN GENERAL_CHAT, GENERATE_NAME, SUMMARIZE, or EXTRACT_ENTITY intent is detected THEN the system SHALL execute SimpleAgentWorkflow
2. WHEN SimpleAgentWorkflow is executed THEN the system SHALL directly invoke the target Agent without preprocessing
3. WHEN the Agent completes THEN the system SHALL return the response without post-processing

### Requirement 8: Skill 注入集成

**User Story:** As a developer, I want Skills to be injected at the workflow level, so that skill selection is consistent and centralized.

#### Acceptance Criteria

1. WHEN a workflow prepares Agent execution THEN the system SHALL use PromptInjector to determine applicable Skills
2. WHEN content type keywords are detected THEN the system SHALL auto-select relevant Skills (e.g., "打斗" triggers ActionSkill)
3. WHEN Skills are injected THEN the system SHALL emit thought events indicating which Skills are active
4. WHEN no Skills are applicable THEN the system SHALL proceed with base Agent capabilities

### Requirement 9: 事件和可观测性

**User Story:** As a developer, I want workflow execution to emit detailed events in a predictable order, so that the frontend can display progress and the system is observable.

#### Acceptance Criteria

1. WHEN preprocessing starts THEN the system SHALL emit "workflow_preprocessing" thought events
2. WHEN each preprocessing task completes THEN the system SHALL emit task completion events with timing
3. WHEN Agent execution starts THEN the system SHALL emit "agent_started" events
4. WHEN post-processing starts THEN the system SHALL emit "processing_check" events BEFORE the done event
5. WHEN post-processing completes THEN the system SHALL emit "check_result" events containing the results
6. WHEN workflow completes THEN the system SHALL emit "done" event as the final event in the stream
7. WHEN events are emitted THEN the system SHALL maintain the order: preprocessing → content → processing_check → check_result → done

### Requirement 10: 代码整洁与"哑 Agent"策略

**User Story:** As a developer, I want the codebase to be clean and maintainable with clear separation of concerns, so that future development is efficient.

#### Acceptance Criteria

1. WHEN WorkflowExecutor is implemented THEN the system SHALL modify Agent.buildUserPrompt() to prioritize metadata.preprocessingContext over direct RAG calls
2. WHEN Agent classes are refactored THEN the system SHALL ensure Agents check for PreprocessingContext in metadata first, and only fallback to direct RAG calls when context is absent (backward compatibility)
3. WHEN workflows are implemented THEN the system SHALL delete SceneCreationOrchestrator as its functionality is subsumed by ContentGenerationWorkflow
4. WHEN AgentRouter is refactored THEN the system SHALL delegate to WorkflowExecutor instead of directly executing Agents
5. WHEN unused code is identified THEN the system SHALL delete it rather than deprecate it

### Requirement 11: 数据流向约束

**User Story:** As a developer, I want clear data flow constraints, so that the architecture is predictable and maintainable.

#### Acceptance Criteria

1. WHEN Workflow executes preprocessing THEN the system SHALL store results in ChatRequest.metadata under key "preprocessingContext"
2. WHEN Agent.buildUserPrompt() is called THEN the system SHALL first check metadata.preprocessingContext for context data
3. IF metadata.preprocessingContext exists THEN the Agent SHALL use it without making additional RAG/State/Style service calls
4. IF metadata.preprocessingContext is absent THEN the Agent SHALL fallback to direct service calls for backward compatibility
5. WHEN Agent receives enhanced system prompt THEN the system SHALL store it in metadata under key "enhancedSystemPrompt"

### Requirement 12: SceneCreationOrchestrator 迁移

**User Story:** As a developer, I want SceneCreationOrchestrator functionality migrated to ContentGenerationWorkflow, so that there is a single source of truth for content generation orchestration.

#### Acceptance Criteria

1. WHEN ContentGenerationWorkflow is implemented THEN the system SHALL replicate all RAG retrieval logic from SceneCreationOrchestrator.retrieveRagContext()
2. WHEN ContentGenerationWorkflow is implemented THEN the system SHALL replicate consistency check triggering from SceneCreationOrchestrator.triggerConsistencyCheck()
3. WHEN ContentGenerationWorkflow is verified working THEN the system SHALL delete SceneCreationOrchestrator class
4. WHEN SceneCreationOrchestrator is deleted THEN the system SHALL update any code that references it to use ContentGenerationWorkflow

### Requirement 13: 多 Agent 链式执行

**User Story:** As a writer, I want to execute complex tasks that require multiple Agents working in sequence, so that I can accomplish sophisticated creative workflows.

#### Acceptance Criteria

1. WHEN a workflow requires multiple Agents in sequence THEN the system SHALL use AgentOrchestrator.executeChainWithContext() to manage execution
2. WHEN chain execution proceeds THEN the system SHALL pass the previous Agent's output as context to the next Agent via ChainExecutionContext
3. WHEN any Agent in the chain fails THEN the system SHALL abort the chain and emit an error event with the failure context
4. WHEN chain execution completes THEN the system SHALL emit a summary event containing all Agent outputs
5. WHEN user interaction is required between chain steps THEN the system SHALL pause execution and emit a "user_input_required" event

### Requirement 14: 链式执行工作流

**User Story:** As a writer, I want predefined chain workflows for common multi-step tasks, so that I can easily accomplish complex creative goals.

#### Acceptance Criteria

1. WHEN BRAINSTORM_AND_EXPAND intent is detected THEN the system SHALL execute BrainstormExpandWorkflow with chain: [BrainstormAgent → UserSelection → WriterAgent]
2. WHEN OUTLINE_TO_CHAPTER intent is detected THEN the system SHALL execute OutlineToChapterWorkflow with chain: [PlannerAgent → WriterAgent]
3. WHEN CHARACTER_TO_SCENE intent is detected THEN the system SHALL execute CharacterToSceneWorkflow with chain: [CharacterAgent → WriterAgent]
4. WHEN chain workflow is executed THEN the system SHALL emit thought events for each Agent transition
5. WHEN chain workflow requires user selection THEN the system SHALL present options and wait for user input before proceeding
6. WHEN a request contains metadata.chainContext THEN the AbstractChainWorkflow SHALL automatically invoke continueChain() instead of starting a new chain
7. WHEN user selection is received after chain pause THEN the system SHALL resume chain execution from the interaction point with the user's selection

### Requirement 15: Reactor 线程安全

**User Story:** As a developer, I want workflow execution to be non-blocking, so that the system maintains high throughput under concurrent load.

#### Acceptance Criteria

1. WHEN Workflow.preprocess() executes parallel tasks THEN the system SHALL NOT block Reactor/Netty IO threads
2. WHEN AgentOrchestrator.executeParallel3() is called within Reactor context THEN the system SHALL use Mono.fromFuture() or subscribeOn(Schedulers.boundedElastic()) to avoid blocking
3. WHEN CompletableFuture.join() or get() is used THEN the system SHALL ensure execution occurs on a bounded elastic scheduler, not on Netty IO threads
4. WHEN workflow execution completes THEN the system SHALL release all thread resources properly

### Requirement 16: Agent 无状态约束

**User Story:** As a developer, I want Agents to be stateless, so that concurrent requests do not interfere with each other.

#### Acceptance Criteria

1. WHEN Agent classes are implemented THEN the Agent SHALL NOT store request-specific state in instance fields
2. WHEN systemPrompt or context is needed THEN the Agent SHALL receive it via method parameters or ChatRequest.metadata, not via instance fields
3. WHEN multiple concurrent requests execute THEN each request SHALL have isolated context without cross-contamination
4. WHEN EnrichedChatRequest is used THEN the Agent SHALL extract context from the request object, not from shared state
