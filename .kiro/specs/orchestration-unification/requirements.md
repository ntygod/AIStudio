# Requirements Document

## Introduction

本文档定义了 InkFlow V2 后端的 Orchestration 架构统一重构。目标是消除 `agent` 模块和 `ai_bridge` 模块中重复的 orchestration 代码，并将 ChatController 集成到统一的 AgentRouter 架构中。

**目标代码库**: `inkflow-backend-v2`

**背景问题**:
1. `agent/orchestration` 和 `ai_bridge/orchestration` 存在大量重复代码
2. ChatController 直接调用 SpringAIChatService，绕过了 AgentRouter
3. 两套 API 并存（`/api/v1/chat` 和 `/api/v2/agent/chat`）导致维护成本高

**整合目标**:
- 统一使用 `agent` 模块的 orchestration 作为主架构
- ChatController 通过 AgentRouter 路由请求
- 废弃 `ai_bridge/orchestration` 目录

## Glossary

- **AgentRouter**: 根据用户意图将请求路由到合适 Agent 的组件（位于 `agent/routing`）
- **AgentOrchestrator**: 编排多个 Agent 协作执行的组件
- **SpringAIChatService**: 旧架构中直接调用 LLM 的服务（将被废弃）
- **FastPathFilter**: 跳过意图分析的快速路由机制
- **ThinkingAgent**: 意图分析 Agent（规则引擎 + 小模型混合）
- **ContextBus**: Session 级别的上下文共享组件
- **ChainExecutionContext**: ai_bridge 模块中的链式执行上下文（需合并）

## 新架构执行流程

### 统一请求处理流程

```
用户请求
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│                    Controller Layer                          │
│  ChatController (/api/v1/chat)                              │
│  AgentController (/api/v2/agent/chat)                       │
│                         │                                    │
│                         ▼                                    │
│              ┌─────────────────────┐                        │
│              │   AgentRouter       │                        │
│              └─────────────────────┘                        │
└─────────────────────────────────────────────────────────────┘
                          │
          ┌───────────────┴───────────────┐
          ▼                               ▼
┌─────────────────────┐       ┌─────────────────────┐
│   FastPathFilter    │       │   ThinkingAgent     │
│   (intentHint存在)   │       │   (需要意图分析)     │
└─────────────────────┘       └─────────────────────┘
          │                               │
          └───────────────┬───────────────┘
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                    Agent Layer                               │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐           │
│  │ WriterAgent │ │ ChatAgent   │ │ PlannerAgent│ ...       │
│  └─────────────┘ └─────────────┘ └─────────────┘           │
│         │               │               │                    │
│         └───────────────┴───────────────┘                   │
│                         │                                    │
│                         ▼                                    │
│              ┌─────────────────────┐                        │
│              │   PromptInjector    │ ← Skill Slots 注入     │
│              └─────────────────────┘                        │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                    Tool Layer                                │
│  RAGSearchTool, StyleRetrieveTool, PreflightTool, etc.      │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                    LLM Provider                              │
│  DynamicChatModelFactory → DeepSeek / OpenAI / Local        │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
                    SSE 流式响应
```

### 详细执行步骤

1. **请求接收**: ChatController 或 AgentController 接收 HTTP 请求
2. **路由决策**: 
   - 检查 `intentHint` 参数，存在则走 FastPath
   - 否则调用 ThinkingAgent 进行意图分析
3. **Agent 选择**: AgentRouter 根据意图选择目标 Agent
4. **技能注入**: PromptInjector 根据 Agent 类型和 CreationPhase 注入 Skills
5. **上下文获取**: 从 ContextBus 获取 SessionContext
6. **Tool 调用**: Agent 按需调用 RAGSearchTool 等工具
7. **LLM 调用**: 通过 DynamicChatModelFactory 调用 LLM
8. **流式响应**: 通过 SSE 返回流式内容

## Requirements

### Requirement 1: ChatController 集成 AgentRouter

**User Story:** As a developer, I want ChatController to route requests through AgentRouter, so that all chat requests benefit from multi-agent collaboration.

#### Acceptance Criteria

1. WHEN ChatController receives a chat request THEN the ChatController SHALL delegate to AgentRouter instead of SpringAIChatService
2. WHEN the request contains intentHint parameter THEN the AgentRouter SHALL use FastPath routing
3. WHEN the request does not contain intentHint THEN the AgentRouter SHALL use ThinkingAgent for intent analysis
4. WHEN routing completes THEN the system SHALL return SSE stream with unified event types (content, thought, tool_start, tool_end, done, error)
5. WHEN ChatController is refactored THEN the ChatController SHALL maintain backward compatibility with existing request/response format

### Requirement 2: 请求格式适配

**User Story:** As a developer, I want a unified request adapter, so that ChatController requests can be converted to AgentRouter format.

#### Acceptance Criteria

1. WHEN ChatController receives a ChatRequest THEN the system SHALL convert it to agent.dto.ChatRequest format
2. WHEN phase parameter is missing THEN the system SHALL use PhaseInferenceService to determine the phase
3. WHEN conversationId is missing THEN the system SHALL generate one based on userId and projectId
4. WHEN metadata is needed THEN the system SHALL include userId, projectId, and other context in the request

### Requirement 3: 合并 AgentOrchestrator

**User Story:** As a developer, I want a single AgentOrchestrator implementation, so that there is no code duplication.

#### Acceptance Criteria

1. WHEN the system needs parallel Agent execution THEN the AgentOrchestrator SHALL use Virtual Threads with CompletableFuture
2. WHEN the system needs chain execution THEN the AgentOrchestrator SHALL support ChainExecutionContext from ai_bridge module
3. WHEN any Agent fails THEN the AgentOrchestrator SHALL implement retry logic with exponential backoff
4. WHEN orchestration completes THEN the AgentOrchestrator SHALL emit completion events with timing metrics
5. WHEN merging is complete THEN the ai_bridge AgentOrchestrator SHALL be marked as @Deprecated

### Requirement 4: 合并 SceneCreationOrchestrator

**User Story:** As a developer, I want a single SceneCreationOrchestrator, so that scene creation logic is unified.

#### Acceptance Criteria

1. WHEN creating a scene THEN the SceneCreationOrchestrator SHALL use WriterAgent from agent module
2. WHEN scene creation needs RAG context THEN the SceneCreationOrchestrator SHALL integrate HybridSearchService
3. WHEN scene creation completes THEN the SceneCreationOrchestrator SHALL trigger ConsistencyAgent for validation
4. WHEN merging is complete THEN the ai_bridge SceneCreationOrchestrator SHALL be marked as @Deprecated

### Requirement 5: 废弃 SpringAIChatService

**User Story:** As a developer, I want to deprecate SpringAIChatService, so that all AI interactions go through the Agent architecture.

#### Acceptance Criteria

1. WHEN refactoring is complete THEN the SpringAIChatService SHALL be marked as @Deprecated
2. WHEN SpringAIChatService is deprecated THEN all callers SHALL be migrated to use AgentRouter
3. WHEN migration is complete THEN the system SHALL log warnings for any remaining SpringAIChatService usage
4. WHEN deprecation period ends THEN the SpringAIChatService SHALL be removed in a future release

### Requirement 6: 保持 API 兼容性

**User Story:** As a frontend developer, I want the API to remain compatible, so that existing integrations continue to work.

#### Acceptance Criteria

1. WHEN ChatController is refactored THEN the endpoint path `/api/v1/chat/stream` SHALL remain unchanged
2. WHEN ChatController is refactored THEN the request body format SHALL remain compatible
3. WHEN ChatController is refactored THEN the SSE event format SHALL remain compatible
4. WHEN new features are added THEN the system SHALL use optional parameters to maintain backward compatibility

### Requirement 7: 统一事件类型

**User Story:** As a developer, I want unified SSE event types, so that frontend can handle responses consistently.

#### Acceptance Criteria

1. WHEN streaming content THEN the system SHALL emit events with type "content"
2. WHEN Agent is thinking THEN the system SHALL emit events with type "thought"
3. WHEN Tool execution starts THEN the system SHALL emit events with type "tool_start"
4. WHEN Tool execution ends THEN the system SHALL emit events with type "tool_end"
5. WHEN processing completes THEN the system SHALL emit events with type "done" including token usage
6. WHEN error occurs THEN the system SHALL emit events with type "error" with user-friendly message

### Requirement 8: 迁移 ai_bridge 特有功能

**User Story:** As a developer, I want to preserve useful features from ai_bridge, so that no functionality is lost during migration.

#### Acceptance Criteria

1. WHEN merging orchestration THEN the system SHALL preserve ChainExecutionContext for complex workflows
2. WHEN merging orchestration THEN the system SHALL preserve ParallelResult2/ParallelResult3 type-safe results
3. WHEN ChoreographerAgent functionality is needed THEN the system SHALL implement it as ActionSkill in WriterAgent
4. WHEN PsychologistAgent functionality is needed THEN the system SHALL implement it as PsychologySkill in WriterAgent

