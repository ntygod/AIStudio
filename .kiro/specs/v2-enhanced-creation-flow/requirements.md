# Requirements Document: InkFlow V2 Enhanced Creation Flow

## Introduction

本文档定义了 InkFlow V2 增强版创作流程的需求。V2 的核心目标是提供一个**统一入口、智能分流、引导式创作**的 AI 辅助创作系统。

### 核心目标

1. **统一入口，智能分流** - 用户只需输入一句话，系统自动判断是普通聊天、创意请求还是复杂创作任务
2. **引导式创作流程** - 对于创作请求，系统主动引导用户按正确流程创作（项目配置 → 世界观 → 角色 → 大纲 → 写作）
3. **复杂请求分解执行** - 对于复杂请求，系统能分解为多个子任务并依次执行（如更新项目名、创建角色、生成主线）
4. **跨线程上下文安全** - 在 Virtual Threads 环境下确保请求上下文正确传递

### 技术栈规格

- **核心框架**: Spring Boot 3.5.x (Spring Framework 6.2)
- **AI框架**: Spring AI 1.1.2 (ChatClient, Advisors, Function Calling)
- **运行时**: Java 22 (Virtual Threads, ScopedValue)
- **数据库**: PostgreSQL 18+ (pgvector, LISTEN/NOTIFY)
- **缓存**: Redis Stack 7.4+

## Glossary

- **CreationPhase**: 创作阶段枚举 (IDEA, WORLDBUILDING, CHARACTER, OUTLINE, WRITING, REVISION, COMPLETED)
- **SceneToolRegistry**: 场景工具注册表，根据阶段返回对应的 Tool 组合
- **PhaseAwarePromptBuilder**: 阶段感知提示词构建器，根据阶段生成系统提示词
- **ToolExecutionAspect**: Tool 执行切面，拦截 Tool 调用并发布事件
- **RequestContextHolder**: 请求上下文持有器，使用 ScopedValue 实现跨线程上下文传递
- **Tool Calling**: AI 模型原生能力，根据用户输入自动决定是否调用 Tool

## Requirements

### Requirement 1: AI 原生 Tool Calling 驱动 (AI-Native Tool Calling)

**User Story:** As a 创作者, I want to 只需输入一句话 AI 就能自动决定如何处理, so that 我无需关心底层如何处理。

#### Acceptance Criteria

1. WHEN a user sends a message THEN the Novel_Platform SHALL let AI model decide whether to call tools
2. WHEN AI decides to call tools THEN the Novel_Platform SHALL execute tools and return results to AI
3. WHEN AI decides not to call tools THEN the Novel_Platform SHALL return AI's direct response
4. WHEN a complex request requires multiple operations THEN the AI SHALL call multiple tools in sequence
5. THE Novel_Platform SHALL provide rich tool descriptions to help AI make correct decisions
6. THE Novel_Platform SHALL use Spring AI 1.1.2 Function Calling with @Tool annotation

---

### Requirement 2: 复杂任务自动执行 (Complex Task Auto-Execution)

**User Story:** As a 创作者, I want to 用一句话描述复杂需求 AI 能自动分解执行, so that 我不需要一步步手动操作。

#### Acceptance Criteria

1. WHEN a complex request is received (e.g., "写修仙小说归墟，主角萧炎，想故事主线") THEN the AI SHALL call multiple tools in sequence
2. WHEN AI needs to update project THEN the AI SHALL call UniversalCrudTool with operation=UPDATE, entityType=project
3. WHEN AI needs to create character THEN the AI SHALL call UniversalCrudTool with operation=CREATE, entityType=character
4. WHEN AI needs to generate content THEN the AI SHALL call CreativeGenTool or use direct generation
5. WHEN each tool completes THEN the Novel_Platform SHALL emit tool_end event with result
6. WHEN all operations complete THEN the AI SHALL summarize what was done in natural language
7. THE Novel_Platform SHALL provide clear tool descriptions to guide AI's multi-step execution

---

### Requirement 3: 引导式创作流程 (Guided Creation Flow)

**User Story:** As a 创作者, I want to 系统引导我按正确流程创作, so that 我不会遗漏重要的创作步骤。

#### Acceptance Criteria

1. WHEN a new project is created THEN the Novel_Platform SHALL guide user through IDEA phase first
2. WHEN IDEA phase is complete THEN the Novel_Platform SHALL suggest moving to WORLDBUILDING phase
3. WHEN WORLDBUILDING phase is complete THEN the Novel_Platform SHALL suggest moving to CHARACTER phase
4. WHEN CHARACTER phase is complete THEN the Novel_Platform SHALL suggest moving to OUTLINE phase
5. WHEN OUTLINE phase is complete THEN the Novel_Platform SHALL suggest moving to WRITING phase
6. WHEN user attempts to skip phases THEN the Novel_Platform SHALL warn about missing prerequisites
7. THE Novel_Platform SHALL track phase completion status based on entity existence (characters, wiki entries, outline)

---

### Requirement 4: 阶段感知工具选择 (Phase-Aware Tool Selection)

**User Story:** As a 系统, I want to 根据创作阶段选择合适的工具, so that AI在不同阶段使用最相关的工具集。

#### Acceptance Criteria

1. WHEN phase is IDEA THEN the Novel_Platform SHALL provide tools: UniversalCrud (project), CreativeGen
2. WHEN phase is WORLDBUILDING THEN the Novel_Platform SHALL provide tools: UniversalCrud, RAGSearch, CreativeGen
3. WHEN phase is CHARACTER THEN the Novel_Platform SHALL provide tools: UniversalCrud, RAGSearch, CreativeGen, ArchetypeGen
4. WHEN phase is OUTLINE THEN the Novel_Platform SHALL provide tools: UniversalCrud, RAGSearch, Preflight, DeepReasoning
5. WHEN phase is WRITING THEN the Novel_Platform SHALL provide tools: UniversalCrud, RAGSearch, StyleRetrieve, Preflight, DeepReasoning
6. WHEN phase is REVISION THEN the Novel_Platform SHALL provide tools: UniversalCrud, RAGSearch, Preflight, ConsistencyCheck
7. THE Novel_Platform SHALL use SceneToolRegistry for centralized tool management

---

### Requirement 5: 阶段特定系统提示词 (Phase-Specific System Prompts)

**User Story:** As a 系统, I want to 根据创作阶段生成针对性的系统提示词, so that AI理解当前阶段的任务重点。

#### Acceptance Criteria

1. WHEN building system prompt THEN the Novel_Platform SHALL include base prompt with userId and projectId for tool auto-fill
2. WHEN phase is IDEA THEN the Novel_Platform SHALL include prompt for collecting inspiration, genre, and core concept
3. WHEN phase is WORLDBUILDING THEN the Novel_Platform SHALL include prompt for world rules, power systems, geography
4. WHEN phase is CHARACTER THEN the Novel_Platform SHALL include prompt for character creation, personality, relationships
5. WHEN phase is OUTLINE THEN the Novel_Platform SHALL include prompt for plot design, foreshadowing, story arcs
6. WHEN phase is WRITING THEN the Novel_Platform SHALL include prompt for content creation with style consistency
7. WHEN phase is REVISION THEN the Novel_Platform SHALL include prompt for editing, conflict resolution, polish
8. THE Novel_Platform SHALL include available tool descriptions in system prompt

---

### Requirement 6: Tool 状态事件流 (Tool Status Event Stream)

**User Story:** As a 前端开发者, I want to 接收详细的执行状态事件, so that 我可以向用户展示 AI 正在执行的操作。

#### Acceptance Criteria

1. WHEN a Tool starts execution THEN the Novel_Platform SHALL emit tool_start event with tool name and parameters
2. WHEN a Tool completes execution THEN the Novel_Platform SHALL emit tool_end event with success status and result summary
3. WHEN a sub-task starts THEN the Novel_Platform SHALL emit task_start event with task description
4. WHEN a sub-task completes THEN the Novel_Platform SHALL emit task_end event with result
5. WHEN content is being generated THEN the Novel_Platform SHALL emit content event with text chunk
6. WHEN phase transition is suggested THEN the Novel_Platform SHALL emit phase_suggestion event
7. WHEN all processing is complete THEN the Novel_Platform SHALL emit done event
8. THE Novel_Platform SHALL use SSE (Server-Sent Events) format for all event types

---

### Requirement 7: 跨线程请求上下文 (Cross-Thread Request Context)

**User Story:** As a 系统, I want to 在 Virtual Threads 环境下安全传递请求上下文, so that Tool 可以自动获取 userId 和 projectId。

#### Acceptance Criteria

1. WHEN a chat request starts THEN the Novel_Platform SHALL bind requestId, userId, projectId to ScopedValue
2. WHEN a Tool is invoked in any thread THEN the Novel_Platform SHALL access context from ScopedValue
3. WHEN spawning Virtual Threads THEN the Novel_Platform SHALL ensure ScopedValue is inherited
4. WHEN a chat request completes THEN the Novel_Platform SHALL automatically release ScopedValue binding
5. WHEN a chat request is cancelled THEN the Novel_Platform SHALL handle cleanup gracefully
6. THE Novel_Platform SHALL use Java 22 ScopedValue instead of ThreadLocal for Virtual Thread compatibility

---

### Requirement 8: 智能模型路由 (Hybrid Model Routing)

**User Story:** As a 系统, I want to 根据创作阶段和任务复杂度选择最优模型, so that 不同场景使用最合适的模型。

#### Acceptance Criteria

1. WHEN phase is IDEA or WORLDBUILDING THEN the Novel_Platform SHALL use creative-optimized model
2. WHEN phase is OUTLINE and deep reasoning required THEN the Novel_Platform SHALL use reasoning model (e.g., DeepSeek R1)
3. WHEN phase is WRITING THEN the Novel_Platform SHALL use writing-optimized model
4. WHEN phase is REVISION THEN the Novel_Platform SHALL use analysis-optimized model
5. WHEN cloud model is unavailable THEN the Novel_Platform SHALL fallback to local model with warning
6. THE Novel_Platform SHALL support model routing configuration per scene type via DynamicChatModelFactory

---

### Requirement 9: 主动式一致性检查 (Proactive Consistency Check)

**User Story:** As a 创作者, I want to 系统在后台静默检查一致性, so that 我能在需要时获知设定冲突而不被频繁打扰。

#### Acceptance Criteria

1. WHEN multiple WikiEntries are updated in batch THEN the Novel_Platform SHALL debounce and trigger single consistency check
2. WHEN consistency check is triggered THEN the Novel_Platform SHALL use rule-based check first (low cost)
3. WHEN rule-based check finds potential issues THEN the Novel_Platform SHALL optionally use AI check (configurable)
4. WHEN inconsistency is detected THEN the Novel_Platform SHALL store warning silently without interrupting user
5. WHEN user explicitly requests preflight check THEN the Novel_Platform SHALL return all pending warnings
6. WHEN user enters REVISION phase THEN the Novel_Platform SHALL proactively show pending warnings
7. THE Novel_Platform SHALL implement rate limiting: max 1 check per project per 5 minutes
8. THE Novel_Platform SHALL use Virtual Threads for non-blocking async execution
9. THE Novel_Platform SHALL NOT use AI for every check to control costs (rule-based by default)

---

### Requirement 10: 创意生成工具 (CreativeGenTool)

**User Story:** As a 创作者, I want to AI能生成各种创意内容, so that 我可以快速获得角色设定、章节内容、大纲等。

#### Acceptance Criteria

1. WHEN AI needs to generate chapter content THEN the Novel_Platform SHALL provide CreativeGenTool with CHAPTER_CONTENT type
2. WHEN AI needs to generate character background THEN the Novel_Platform SHALL provide CreativeGenTool with CHARACTER_BACKGROUND type
3. WHEN AI needs to generate outline THEN the Novel_Platform SHALL provide CreativeGenTool with OUTLINE type
4. WHEN AI needs to generate world structure THEN the Novel_Platform SHALL provide CreativeGenTool with WORLD_STRUCTURE type
5. WHEN AI needs to generate story block THEN the Novel_Platform SHALL provide CreativeGenTool with STORY_BLOCK type
6. WHEN generating content THEN the Novel_Platform SHALL retrieve style samples and RAG context automatically
7. THE Novel_Platform SHALL support generation types: CHAPTER_CONTENT, CHARACTER_BACKGROUND, OUTLINE, WORLD_STRUCTURE, STORY_BLOCK, PROJECT_INITIALIZATION, NAMES

---

### Requirement 11: 深度推理工具 (DeepReasoningTool)

**User Story:** As a 创作者, I want to AI能进行深度逻辑推理, so that 复杂的剧情分析和角色心理分析更加准确。

#### Acceptance Criteria

1. WHEN AI encounters complex plot analysis THEN the Novel_Platform SHALL provide DeepReasoningTool
2. WHEN AI encounters character psychology analysis THEN the Novel_Platform SHALL provide DeepReasoningTool
3. WHEN AI encounters foreshadowing design THEN the Novel_Platform SHALL provide DeepReasoningTool
4. WHEN DeepReasoningTool is called THEN the Novel_Platform SHALL use reasoning model (e.g., DeepSeek R1)
5. WHEN reasoning model is unavailable THEN the Novel_Platform SHALL fallback to standard model with warning
6. THE Novel_Platform SHALL NOT use DeepReasoningTool for simple tasks to control costs

---

### Requirement 12: 逻辑预检工具 (PreflightTool)

**User Story:** As a 创作者, I want to AI能在生成内容前检查逻辑冲突, so that 生成的内容与已有设定保持一致。

#### Acceptance Criteria

1. WHEN AI is about to generate chapter content THEN the Novel_Platform SHALL optionally call PreflightTool
2. WHEN PreflightTool is called THEN the Novel_Platform SHALL check character consistency
3. WHEN PreflightTool is called THEN the Novel_Platform SHALL check timeline conflicts
4. WHEN PreflightTool is called THEN the Novel_Platform SHALL check unclosed plot loops
5. WHEN conflicts are found THEN the Novel_Platform SHALL return detailed report with suggestions
6. THE Novel_Platform SHALL support both rule-based and AI-enhanced preflight checks

---

### Requirement 13: 风格检索工具 (StyleRetrieveTool)

**User Story:** As a 创作者, I want to AI能参考我的写作风格, so that 生成的内容与我的风格保持一致。

#### Acceptance Criteria

1. WHEN AI is generating content THEN the Novel_Platform SHALL optionally call StyleRetrieveTool
2. WHEN StyleRetrieveTool is called THEN the Novel_Platform SHALL retrieve user's style samples
3. WHEN style samples are retrieved THEN the Novel_Platform SHALL build style guidance prompt
4. WHEN no style samples exist THEN the Novel_Platform SHALL use default style guidance
5. THE Novel_Platform SHALL support style features: sentence length, dialogue ratio, vocabulary richness



---

### Requirement 14: 对话历史持久化 (Conversation History Persistence)

**User Story:** As a 创作者, I want to 我的对话历史被保存, so that 我可以随时回顾和恢复之前的创作上下文。

#### Acceptance Criteria

1. WHEN a chat message is sent THEN the Novel_Platform SHALL persist the message to database
2. WHEN a user returns to a project THEN the Novel_Platform SHALL load recent conversation history
3. WHEN loading history THEN the Novel_Platform SHALL respect configurable window size (default 20 messages)
4. WHEN conversation exceeds window size THEN the Novel_Platform SHALL summarize older messages
5. THE Novel_Platform SHALL store: role, content, timestamp, projectId, userId

---

### Requirement 15: 创作进度追踪 (Creation Progress Tracking)

**User Story:** As a 创作者, I want to 看到我的创作进度, so that 我知道还有多少工作要完成。

#### Acceptance Criteria

1. WHEN user views project THEN the Novel_Platform SHALL show current creation phase
2. WHEN user views project THEN the Novel_Platform SHALL show phase completion percentage
3. WHEN phase completion criteria are met THEN the Novel_Platform SHALL suggest advancing to next phase
4. THE Novel_Platform SHALL track: character count, wiki entry count, outline completion, chapter count, word count

---

### Requirement 16: 会话恢复提示 (Session Resume Prompt)

**User Story:** As a 创作者, I want to 回来时知道上次在做什么, so that 我可以快速恢复创作状态。

#### Acceptance Criteria

1. WHEN user starts a new chat session THEN the Novel_Platform SHALL check for previous session
2. WHEN previous session exists THEN the Novel_Platform SHALL summarize last activity
3. WHEN summarizing THEN the Novel_Platform SHALL include: last phase, last action, pending tasks
4. THE Novel_Platform SHALL NOT interrupt user if they start with a new topic

---

### Requirement 17: Tool 调用日志 (Tool Invocation Logging)

**User Story:** As a 开发者, I want to 记录所有 Tool 调用, so that 我可以调试和分析系统行为。

#### Acceptance Criteria

1. WHEN a Tool is invoked THEN the Novel_Platform SHALL log: tool name, parameters, timestamp, userId, projectId
2. WHEN a Tool completes THEN the Novel_Platform SHALL log: success status, duration, result summary
3. WHEN a Tool fails THEN the Novel_Platform SHALL log: error type, error message, stack trace
4. THE Novel_Platform SHALL support log retention policy (default 30 days)
5. THE Novel_Platform SHALL expose API for querying tool invocation history

---

### Requirement 18: 错误处理与重试 (Error Handling and Retry)

**User Story:** As a 创作者, I want to 系统能优雅处理错误, so that 我的创作不会因为临时故障而中断。

#### Acceptance Criteria

1. WHEN AI model call fails THEN the Novel_Platform SHALL retry with exponential backoff (max 3 times)
2. WHEN Tool execution fails THEN the Novel_Platform SHALL return user-friendly error message
3. WHEN retry exhausted THEN the Novel_Platform SHALL suggest alternative actions
4. WHEN rate limit is hit THEN the Novel_Platform SHALL queue request and notify user of delay
5. THE Novel_Platform SHALL NOT expose internal error details to user
