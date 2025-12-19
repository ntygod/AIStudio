# Requirements Document

## Introduction

本规范定义了 InkFlow V2 后端的统一 Agent 架构重构。目标是将现有的单一 LLM 调用模式（ChatController → SpringAIChatService）重构为分层的 Agent 架构，使主流程也能享受多 Agent 协作的好处。

**目标代码库**: `inkflow-backend-v2`（V1 已废弃）

**重要决策**: 
- 本项目为全新项目，无需考虑向后兼容
- SpringAIChatService 将被移除，所有 AI 交互通过 AgentRouter 进行
- 采用精简的 10 Agent 方案，通过 Skill Slots 机制扩展能力

核心架构分为四层：
1. **Controller Layer** - 接收用户请求，路由到合适的 Agent
2. **Agent Layer** - 10 个精简的智能体（见下方 Agent 分类）
3. **Tool Layer** - 封装好的工具（RAGSearchTool、UniversalCrudTool 等）
4. **Domain Services** - 底层业务服务（CharacterService、WikiService 等）

### Agent 分类体系（精简版 - 10 个 Agent）

#### 1. 自动路由层 Agent（2 个）
- **ThinkingAgent**: 意图分析与路由决策（规则引擎 + 小模型混合）
- **ChatAgent**: 通用对话与兜底处理

#### 2. 专业创作层 Agent（4 个）
- **WorldBuilderAgent**: 世界观设计（力量体系、地理、历史、文化）+ 灵感激发
- **CharacterAgent**: 角色设计 + 关系网络 + 原型匹配
- **PlannerAgent**: 大纲规划 + 伏笔管理 + 节奏控制
- **WriterAgent**: 核心内容生成，通过 Skill Slots 动态注入技能

#### 3. 质量保障层 Agent（1 个）
- **ConsistencyAgent**: 一致性检查 + 文风分析

#### 4. 工具层 Agent（3 个，懒执行）
- **NameGeneratorAgent**: 人名、地名、物品名生成（高频使用，独立保留）
- **SummaryAgent**: 内容摘要生成
- **ExtractionAgent**: 实体与关系抽取

### 已移除的 Agent（通过合并或 Skill Slots 替代）
- IdeaAgent → 合并到 WorldBuilderAgent
- RelationshipAgent → 合并到 CharacterAgent
- ArchetypeAgent → 合并到 CharacterAgent
- PlotLoopAgent → 合并到 PlannerAgent
- PacingAgent → 合并到 PlannerAgent
- StyleAgent → 合并到 ConsistencyAgent
- PolishAgent → WriterAgent 的 PolishSkill 替代
- CriticAgent → ConsistencyAgent 可覆盖
- TranslationAgent → 移除（需求极低，可后续按需添加）

## Glossary

- **Agent**: 具有特定职责的智能体，封装 LLM 调用和业务逻辑
- **AgentRouter**: 根据用户意图将请求路由到合适 Agent 的组件
- **Tool**: Agent 可调用的封装好的功能单元，通过 Spring AI @Tool 注解定义
- **Orchestrator**: 编排多个 Agent 协作执行的组件
- **Intent**: 用户消息中识别出的意图类型
- **CreationPhase**: 小说创作阶段（IDEA、WORLDBUILDING、CHARACTER、OUTLINE、WRITING、REVISION、COMPLETED）
- **AgentCategory**: Agent 分类（ROUTING、CREATIVE、QUALITY、UTILITY）
- **ExecutionMode**: Agent 执行模式（EAGER 立即执行、LAZY 懒执行）
- **SkillSlot**: 可动态注入到 Agent 的技能单元，通过 Prompt Injection 方式增强 Agent 能力
- **SkillRegistry**: 管理所有可用技能的注册表，支持运行时动态扩展
- **PromptInjector**: 将技能动态注入到 Agent 系统提示词的组件
- **ContextBus**: Session 级别的上下文共享组件
- **FastPath**: 跳过 ThinkingAgent 意图分析的快速路由机制

## Requirements

### Requirement 1: Agent 路由机制

**User Story:** As a developer, I want the system to automatically route user requests to the appropriate Agent based on intent, so that each request is handled by the most suitable specialist.

#### Acceptance Criteria

1. WHEN a user sends a chat message THEN the AgentRouter SHALL analyze the message intent and route to the appropriate Agent
2. WHEN the intent is "content generation" (写作、续写、扩写) THEN the AgentRouter SHALL route to WriterAgent
3. WHEN the intent is "world building" (世界观、设定、力量体系) THEN the AgentRouter SHALL route to WorldBuilderAgent
4. WHEN the intent is "character design" (角色、人物、关系) THEN the AgentRouter SHALL route to CharacterAgent
5. WHEN the intent is "planning" (大纲、规划、伏笔) THEN the AgentRouter SHALL route to PlannerAgent
6. WHEN the intent is "consistency check" (检查、一致性、矛盾) THEN the AgentRouter SHALL route to ConsistencyAgent
7. WHEN the intent is "name generation" (起名、名字) THEN the AgentRouter SHALL route to NameGeneratorAgent
8. WHEN the intent is "general chat" (闲聊、问答) THEN the AgentRouter SHALL route to ChatAgent
9. WHEN the intent cannot be determined THEN the AgentRouter SHALL default to ChatAgent with fallback behavior

### Requirement 2: Fast Path 机制

**User Story:** As a developer, I want to skip intent analysis when the intent is already known, so that response latency is minimized.

#### Acceptance Criteria

1. WHEN the request contains an intentHint parameter THEN the FastPathFilter SHALL skip ThinkingAgent and route directly
2. WHEN the user clicks a specific function button (如"生成大纲") THEN the frontend SHALL pass intentHint to enable Fast Path
3. WHEN the message starts with a command prefix (如 /write, /plan) THEN the FastPathFilter SHALL recognize and route directly
4. WHEN Fast Path is used THEN the system SHALL log the skip for monitoring purposes
5. WHEN Fast Path routing fails THEN the system SHALL fallback to ThinkingAgent analysis

### Requirement 3: ThinkingAgent 意图分析

**User Story:** As a system, I want a ThinkingAgent to analyze user intent before routing, so that requests are accurately classified and handled.

#### Acceptance Criteria

1. WHEN ThinkingAgent receives a user message THEN the ThinkingAgent SHALL first try rule-based classification (latency < 10ms)
2. WHEN rule-based classification confidence is above 0.9 THEN the ThinkingAgent SHALL return the result without LLM call
3. WHEN rule-based classification is uncertain THEN the ThinkingAgent SHALL use a lightweight LLM model for analysis
4. WHEN analyzing intent THEN the ThinkingAgent SHALL consider the current CreationPhase as context
5. WHEN confidence score is below 0.7 THEN the ThinkingAgent SHALL include alternative intents in the result
6. WHEN ThinkingAgent completes analysis THEN the ThinkingAgent SHALL emit an AgentThoughtEvent for observability

### Requirement 4: WriterAgent 内容生成

**User Story:** As a user, I want the WriterAgent to generate high-quality novel content with dynamic skill enhancement, so that I can efficiently create my story.

#### Acceptance Criteria

1. WHEN WriterAgent receives a writing request THEN the WriterAgent SHALL use RAGSearchTool to retrieve relevant context
2. WHEN generating content THEN the WriterAgent SHALL use StyleRetrieveTool to match the user's writing style
3. WHEN generating content THEN the PromptInjector SHALL inject applicable skills (DialogueSkill, ActionSkill, etc.)
4. WHEN generating content THEN the WriterAgent SHALL stream the response for real-time feedback
5. WHEN content generation completes THEN the WriterAgent SHALL trigger ConsistencyAgent for validation
6. WHEN WriterAgent encounters an error THEN the WriterAgent SHALL return a graceful error message and log the failure

### Requirement 5: WorldBuilderAgent 世界观构建

**User Story:** As a user in the IDEA/WORLDBUILDING phase, I want the WorldBuilderAgent to help me design consistent world settings and spark creative ideas, so that my story has a solid foundation.

#### Acceptance Criteria

1. WHEN WorldBuilderAgent receives a vague concept THEN the WorldBuilderAgent SHALL generate multiple creative directions with brief descriptions
2. WHEN WorldBuilderAgent receives a world concept THEN the WorldBuilderAgent SHALL generate structured world settings including geography, history, and culture
3. WHEN designing power systems THEN the WorldBuilderAgent SHALL ensure internal consistency and clear limitations
4. WHEN generating world elements THEN the WorldBuilderAgent SHALL check for conflicts with existing settings using RAGSearchTool
5. WHEN WorldBuilderAgent completes design THEN the WorldBuilderAgent SHALL output structured JSON for WikiEntry creation

### Requirement 6: CharacterAgent 角色设计

**User Story:** As a user in the CHARACTER phase, I want the CharacterAgent to help me create well-rounded characters with relationships and archetypes, so that my story has compelling characters.

#### Acceptance Criteria

1. WHEN CharacterAgent receives a character concept THEN the CharacterAgent SHALL generate comprehensive character profiles
2. WHEN designing characters THEN the CharacterAgent SHALL include personality traits, backstory, motivations, and flaws
3. WHEN CharacterAgent receives character list THEN the CharacterAgent SHALL suggest meaningful relationships between characters
4. WHEN designing characters THEN the CharacterAgent SHALL match appropriate archetypes (英雄、导师、阴影等)
5. WHEN CharacterAgent completes design THEN the CharacterAgent SHALL output structured JSON for Character entity creation

### Requirement 7: PlannerAgent 大纲规划

**User Story:** As a user in the OUTLINE phase, I want the PlannerAgent to help me design story outlines with plot loops and pacing, so that I can structure my novel effectively.

#### Acceptance Criteria

1. WHEN PlannerAgent receives a planning request THEN the PlannerAgent SHALL analyze existing story structure
2. WHEN generating outline THEN the PlannerAgent SHALL consider character arcs and plot loops
3. WHEN user creates a plot loop THEN the PlannerAgent SHALL suggest appropriate placement and payoff timing
4. WHEN checking plot loops THEN the PlannerAgent SHALL warn about unresolved threads approaching deadlines
5. WHEN analyzing pacing THEN the PlannerAgent SHALL evaluate tension curves and suggest adjustments
6. WHEN PlannerAgent generates outline THEN the PlannerAgent SHALL return structured JSON with chapters and beats

### Requirement 8: ConsistencyAgent 一致性检查

**User Story:** As a user, I want the system to automatically check content consistency and style, so that my novel maintains logical coherence and unified voice.

#### Acceptance Criteria

1. WHEN ConsistencyAgent receives content THEN the ConsistencyAgent SHALL check for character consistency using RAGSearchTool
2. WHEN ConsistencyAgent detects inconsistencies THEN the ConsistencyAgent SHALL return a list of warnings with severity levels
3. WHEN inconsistencies are critical THEN the ConsistencyAgent SHALL suggest corrections
4. WHEN ConsistencyAgent receives sample text THEN the ConsistencyAgent SHALL analyze and extract style characteristics
5. WHEN style inconsistency is detected THEN the ConsistencyAgent SHALL suggest corrections to match established style
6. WHEN ConsistencyAgent completes THEN the ConsistencyAgent SHALL emit consistency check results as events

### Requirement 9: ChatAgent 通用对话

**User Story:** As a user, I want a ChatAgent for general conversation and questions, so that I can get help with any aspect of my writing.

#### Acceptance Criteria

1. WHEN ChatAgent receives a general question THEN the ChatAgent SHALL provide helpful responses using available context
2. WHEN ChatAgent cannot answer THEN the ChatAgent SHALL acknowledge limitations and suggest alternatives
3. WHEN ChatAgent detects a specialized intent THEN the ChatAgent SHALL recommend routing to the appropriate Agent
4. WHEN ChatAgent responds THEN the ChatAgent SHALL maintain conversation history for context continuity
5. WHEN ChatAgent is used as fallback THEN the ChatAgent SHALL handle the request gracefully without errors

### Requirement 10: NameGeneratorAgent 名称生成

**User Story:** As a user, I want the NameGeneratorAgent to create appropriate names for characters, places, and items, so that my story has immersive naming.

#### Acceptance Criteria

1. WHEN NameGeneratorAgent receives a naming request THEN the NameGeneratorAgent SHALL generate names matching the specified style and culture
2. WHEN generating character names THEN the NameGeneratorAgent SHALL consider gender, social status, and era
3. WHEN generating place names THEN the NameGeneratorAgent SHALL reflect geographical features and cultural background
4. WHEN generating names THEN the NameGeneratorAgent SHALL provide at least 5 options with meaning explanations
5. WHEN user specifies constraints THEN the NameGeneratorAgent SHALL filter results to match requirements

### Requirement 11: SummaryAgent 摘要生成

**User Story:** As a user, I want the SummaryAgent to generate summaries of my content, so that I can quickly review and reference my work.

#### Acceptance Criteria

1. WHEN SummaryAgent receives content THEN the SummaryAgent SHALL generate concise summaries at specified detail levels
2. WHEN summarizing chapters THEN the SummaryAgent SHALL capture key plot points and character developments
3. WHEN generating summaries THEN the SummaryAgent SHALL maintain consistency with existing summaries
4. WHEN user specifies length THEN the SummaryAgent SHALL adjust summary detail accordingly
5. WHEN SummaryAgent completes summary THEN the SummaryAgent SHALL output structured data for storage

### Requirement 12: ExtractionAgent 实体抽取

**User Story:** As a user, I want the ExtractionAgent to automatically identify entities and relationships from my content, so that my knowledge base stays updated.

#### Acceptance Criteria

1. WHEN ExtractionAgent receives new content THEN the ExtractionAgent SHALL identify characters, locations, items, and events
2. WHEN extracting entities THEN the ExtractionAgent SHALL detect relationships between identified entities
3. WHEN new entities are found THEN the ExtractionAgent SHALL suggest WikiEntry or Character creation
4. WHEN entity conflicts are detected THEN the ExtractionAgent SHALL flag for user resolution
5. WHEN ExtractionAgent completes extraction THEN the ExtractionAgent SHALL trigger RAG index updates

### Requirement 13: Agent 协作编排

**User Story:** As a developer, I want Agents to collaborate through the Orchestrator, so that complex tasks can be handled by multiple specialists.

#### Acceptance Criteria

1. WHEN a complex task requires multiple Agents THEN the AgentOrchestrator SHALL coordinate parallel execution using Virtual Threads
2. WHEN Agents execute in parallel THEN the AgentOrchestrator SHALL aggregate results and handle partial failures
3. WHEN Agent chain execution is needed THEN the AgentOrchestrator SHALL pass outputs between Agents sequentially
4. WHEN any Agent fails THEN the AgentOrchestrator SHALL implement retry logic with exponential backoff
5. WHEN orchestration completes THEN the AgentOrchestrator SHALL emit completion events with timing metrics

### Requirement 14: Tool 统一注册机制

**User Story:** As a developer, I want a unified Tool registration mechanism, so that Agents can discover and use Tools consistently.

#### Acceptance Criteria

1. WHEN the application starts THEN the ToolRegistry SHALL auto-discover all @Tool annotated methods
2. WHEN an Agent requests Tools THEN the ToolRegistry SHALL return Tools filtered by CreationPhase
3. WHEN a Tool is invoked THEN the ToolRegistry SHALL log invocation details for observability
4. WHEN Tool invocation fails THEN the ToolRegistry SHALL provide meaningful error messages
5. WHEN new Tools are added THEN the ToolRegistry SHALL register them without code changes to existing Agents

### Requirement 15: 流式响应与事件

**User Story:** As a user, I want real-time feedback during AI processing, so that I can see progress and intermediate results.

#### Acceptance Criteria

1. WHEN an Agent processes a request THEN the Agent SHALL emit AgentThoughtEvent for each processing step
2. WHEN Tools are invoked THEN the system SHALL emit ToolStatusEvent with start/end status
3. WHEN streaming content THEN the system SHALL use SSE with event types: content, thought, tool_start, tool_end, warning, error, done
4. WHEN an error occurs THEN the system SHALL emit an error event with user-friendly message
5. WHEN processing completes THEN the system SHALL emit a done event with token usage and latency metrics

### Requirement 16: Context Bus 上下文共享

**User Story:** As a developer, I want Agents to share context within a session, so that they can collaborate effectively without redundant data fetching.

#### Acceptance Criteria

1. WHEN an Agent creates or modifies an entity THEN the Agent SHALL publish a ContextEvent to the ContextBus
2. WHEN an Agent needs context THEN the Agent SHALL retrieve SessionContext from the ContextBus
3. WHEN ContextBus receives an event THEN the ContextBus SHALL update the session's working memory
4. WHEN multiple sessions exist THEN the ContextBus SHALL ensure session isolation
5. WHEN session expires THEN the ContextBus SHALL clean up associated context data

### Requirement 17: Skill Slots 动态技能注入

**User Story:** As a developer, I want to dynamically inject skills into Agents through Skill Slots, so that Agents can be extended without modifying their core code.

#### Acceptance Criteria

1. WHEN the application starts THEN the SkillRegistry SHALL auto-discover all SkillSlot implementations
2. WHEN an Agent processes a request THEN the PromptInjector SHALL inject applicable skills based on Agent type and CreationPhase
3. WHEN a skill generates a prompt fragment THEN the PromptInjector SHALL append it to the Agent's system prompt in priority order
4. WHEN user message contains skill-related keywords THEN the system SHALL auto-select relevant skills
5. WHEN a new SkillSlot is registered at runtime THEN the SkillRegistry SHALL make it available immediately without restart

### Requirement 18: Agent 阶段感知

**User Story:** As a developer, I want Agents to be aware of the current CreationPhase, so that they provide phase-appropriate assistance.

#### Acceptance Criteria

1. WHEN an Agent receives a request THEN the Agent SHALL consider the current CreationPhase in its response
2. WHEN in IDEA/WORLDBUILDING phase THEN the AgentRouter SHALL prioritize WorldBuilderAgent
3. WHEN in CHARACTER phase THEN the AgentRouter SHALL prioritize CharacterAgent
4. WHEN in OUTLINE phase THEN the AgentRouter SHALL prioritize PlannerAgent
5. WHEN in WRITING phase THEN the AgentRouter SHALL prioritize WriterAgent
6. WHEN in REVISION phase THEN the AgentRouter SHALL prioritize ConsistencyAgent

### Requirement 19: Agent 能力声明

**User Story:** As a developer, I want each Agent to declare its capabilities, so that the router can make informed decisions.

#### Acceptance Criteria

1. WHEN an Agent is registered THEN the Agent SHALL declare its supported intents, CreationPhases, and ExecutionMode
2. WHEN AgentRouter queries capabilities THEN the AgentRouter SHALL receive structured capability metadata
3. WHEN capabilities include tool requirements THEN the Agent SHALL specify required Tools
4. WHEN querying Agent capabilities THEN the system SHALL return response time estimates and resource requirements
5. WHEN Agent is marked as LAZY execution THEN the LazyExecutionManager SHALL control its trigger conditions

### Requirement 20: 统一 API 入口

**User Story:** As a developer, I want a clean unified API entry point, so that all AI interactions go through the Agent architecture.

#### Acceptance Criteria

1. WHEN a user sends a chat request THEN the ChatController SHALL delegate to AgentRouter
2. WHEN AgentRouter receives a request THEN the AgentRouter SHALL check FastPath first, then analyze intent if needed
3. WHEN Agent execution completes THEN the system SHALL return SSE stream with unified event types
4. WHEN multiple Agents are needed THEN the system SHALL use AgentOrchestrator for coordination
5. WHEN refactoring is complete THEN the system SHALL remove SpringAIChatService and related legacy code
