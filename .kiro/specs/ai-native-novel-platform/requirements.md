# Requirements Document

## Introduction

本文档定义了一个全新的AI原生小说创作平台（代号：InkFlow 2.0）的需求规格。该平台以DDD（领域驱动设计）为架构思想，Spring Boot 3.5.x + Spring AI 1.1.2为核心技术栈，旨在打造一个真正以AI为核心驱动力的长篇小说创作系统。

与传统写作工具不同，本平台将AI深度融入创作全流程，通过智能对话编排、RAG知识检索、多模型协同等能力，让AI成为作者的专业创作伙伴。

### 技术栈规格

- **核心框架**: Spring Boot 3.5.x (Spring Framework 6.2)
- **AI框架**: Spring AI 1.1.2 (支持 Advisors, Fluent ChatClient & Function Calling)
- **运行时**: Java 22 (Virtual Threads 正式发布, Structured Concurrency 预览)
- **数据库**: PostgreSQL 16+ (pgvector扩展, HNSW索引)
- **缓存**: Redis Stack 7.4+ (支持向量缓存与JSON操作)
- **架构风格**: Event-Driven Agentic Mesh (事件驱动智能体网格)
- **API风格**: REST + SSE (流式传输) + GraphQL (可选)

> **Agent 实现范式**: Agent = ChatClient + CompletableFuture + Virtual Threads。使用 `Executors.newVirtualThreadPerTaskExecutor()` 实现并行 Agent 协同。

## Glossary

- **Novel_Platform**: AI原生小说创作平台系统
- **Project**: 小说项目，包含完整的小说内容和设定
- **Volume**: 分卷，小说的大章节划分单位
- **Chapter**: 章节，分卷下的内容组织单位
- **StoryBlock**: 剧情块，章节内的最小内容单元（200-500字）
- **Character**: 角色实体，包含角色设定和状态
- **WikiEntry**: 知识条目，世界观设定的基本单位
- **PlotLoop**: 伏笔，需要回收的悬念或线索
- **CreationPhase**: 创作阶段，标识用户当前所处的创作流程
- **RAG**: 检索增强生成，通过向量检索增强AI生成质量
- **Embedding**: 向量嵌入，文本的向量化表示
- **ChatMemory**: 对话记忆，存储多轮对话历史
- **Tool**: AI工具，Spring AI的函数调用能力
- **DomainAdapter**: 领域适配器，连接AI工具与领域服务
- **Aggregate**: 聚合根，DDD中的核心领域对象
- **BoundedContext**: 限界上下文，DDD中的领域边界

## Requirements

### Requirement 1: 用户认证与授权

**User Story:** As a 创作者, I want to 安全地登录和管理我的账户, so that 我的创作内容得到保护且可以跨设备访问。

#### Acceptance Criteria

1. WHEN a user submits valid credentials THEN the Novel_Platform SHALL issue a JWT access token with 15-minute expiration and a refresh token with 7-day expiration
2. WHEN a user's access token expires THEN the Novel_Platform SHALL allow token refresh using a valid refresh token without requiring re-authentication
3. WHEN a user attempts to access protected resources without valid authentication THEN the Novel_Platform SHALL return HTTP 401 status and reject the request
4. WHEN a user registers with an email address THEN the Novel_Platform SHALL validate email format and ensure uniqueness before creating the account
5. WHEN a user's API keys are stored THEN the Novel_Platform SHALL encrypt them using AES-256 encryption before persistence

---

### Requirement 2: 项目管理（Project聚合根）

**User Story:** As a 创作者, I want to 创建和管理我的小说项目, so that 我可以组织和追踪我的创作进度。

#### Acceptance Criteria

1. WHEN a user creates a new project THEN the Novel_Platform SHALL generate a unique project ID and initialize default metadata including title, genre, and creation phase
2. WHEN a user requests project list THEN the Novel_Platform SHALL return all projects owned by the user sorted by last modified time descending
3. WHEN a user updates project metadata THEN the Novel_Platform SHALL validate all fields and persist changes with updated timestamp
4. WHEN a user deletes a project THEN the Novel_Platform SHALL cascade delete all associated volumes, chapters, story blocks, characters, wiki entries, and plot loops
5. WHEN a user exports a project THEN the Novel_Platform SHALL serialize all project data to JSON format preserving all relationships and metadata
6. WHEN a user imports a project from JSON THEN the Novel_Platform SHALL deserialize and reconstruct all entities with new UUIDs while preserving internal references
7. THE Novel_Platform SHALL provide a pretty-printer for project export that formats JSON with proper indentation for human readability

---

### Requirement 3: 内容结构管理（Volume/Chapter/StoryBlock聚合）

**User Story:** As a 创作者, I want to 以分卷-章节-剧情块的层级结构组织我的小说内容, so that 我可以清晰地管理长篇小说的结构。

#### Acceptance Criteria

1. WHEN a user creates a volume THEN the Novel_Platform SHALL assign an order index and associate it with the parent project
2. WHEN a user creates a chapter THEN the Novel_Platform SHALL assign an order index within the volume and initialize with empty content
3. WHEN a user creates a story block THEN the Novel_Platform SHALL assign a lexicographic rank string and set initial status to "placeholder"
4. WHEN a user reorders story blocks THEN the Novel_Platform SHALL calculate a new rank string between adjacent blocks without updating other blocks
5. WHEN a story block is inserted between two existing blocks THEN the Novel_Platform SHALL generate a rank string that sorts between the adjacent ranks in O(1) time
6. WHEN a story block content is updated THEN the Novel_Platform SHALL set the dirty flag to true and update the content hash for change detection
7. WHEN a story block status changes to "completed" THEN the Novel_Platform SHALL calculate and update the word count field
8. THE Novel_Platform SHALL use Lexorank algorithm for story block ordering to support O(1) insertion without cascading updates

---

### Requirement 4: 角色管理（Character聚合根）

**User Story:** As a 创作者, I want to 创建和管理小说中的角色及其关系, so that 我可以保持角色设定的一致性并追踪角色发展。

#### Acceptance Criteria

1. WHEN a user creates a character THEN the Novel_Platform SHALL initialize all required fields including name, role, and associate with the project
2. WHEN a user defines a character relationship THEN the Novel_Platform SHALL store the relationship type, target character ID, and description in JSONB format
3. WHEN a user queries character relationships THEN the Novel_Platform SHALL return a graph structure with nodes representing characters and edges representing relationships
4. WHEN a user updates character status THEN the Novel_Platform SHALL record the change with timestamp for evolution tracking
5. WHEN a character is referenced in a story block THEN the Novel_Platform SHALL add the character ID to the story block's context entity list
6. THE Novel_Platform SHALL support character archetype templates including: 垫脚石, 老爷爷, 欢喜冤家, 线人, 守门人, 牺牲者, 搞笑担当, 宿敌

---

### Requirement 5: 知识库管理与主动式知识图谱（WikiEntry聚合根）

**User Story:** As a 创作者, I want to 建立和维护小说的世界观知识库, so that AI可以准确引用设定并保持一致性，且当设定变更时能主动提示冲突。

#### Acceptance Criteria

1. WHEN a user creates a wiki entry THEN the Novel_Platform SHALL categorize it by type (character, location, item, event, concept) and associate with the project
2. WHEN a user adds aliases to a wiki entry THEN the Novel_Platform SHALL store all aliases and enable search by any alias
3. WHEN a wiki entry is created or updated THEN the Novel_Platform SHALL trigger asynchronous embedding generation for RAG indexing
4. WHEN a user searches wiki entries THEN the Novel_Platform SHALL support both keyword search and semantic similarity search
5. WHEN a wiki entry content changes THEN the Novel_Platform SHALL invalidate related embeddings and schedule re-indexing
6. WHEN a wiki entry is modified THEN the Novel_Platform SHALL trigger a CDC event to invoke the Consistency Check Agent
7. WHEN the Consistency Check Agent detects conflicts with existing chapters THEN the Novel_Platform SHALL proactively notify the user with specific conflict locations and suggestions
8. THE Novel_Platform SHALL support time-versioned wiki entries to distinguish settings at different story points

---

### Requirement 6: 伏笔追踪（PlotLoop聚合根）

**User Story:** As a 创作者, I want to 追踪和管理小说中的伏笔, so that 我不会忘记回收已埋下的悬念。

#### Acceptance Criteria

1. WHEN a user creates a plot loop THEN the Novel_Platform SHALL set initial status to "OPEN" and record the chapter where it was introduced
2. WHEN a plot loop remains open for more than 10 chapters THEN the Novel_Platform SHALL change status to "URGENT" and include it in AI prompts
3. WHEN a user marks a plot loop as resolved THEN the Novel_Platform SHALL change status to "CLOSED" and record the resolution chapter
4. WHEN a user abandons a plot loop THEN the Novel_Platform SHALL change status to "ABANDONED" and record the reason
5. WHEN generating content for a chapter THEN the Novel_Platform SHALL include all OPEN and URGENT plot loops in the AI context

---

### Requirement 7: AI对话服务与智能体编排（Spring AI集成）

**User Story:** As a 创作者, I want to 通过自然语言对话与AI协作创作, so that 我可以获得智能化的创作辅助。

#### Acceptance Criteria

1. WHEN a user sends a chat message THEN the Novel_Platform SHALL route the request to the appropriate AI model based on the current creation phase
2. WHEN the AI generates a response THEN the Novel_Platform SHALL stream the content using Server-Sent Events with chunked delivery
3. WHEN a chat session is established THEN the Novel_Platform SHALL maintain conversation history using ChatMemory with configurable window size
4. WHEN the AI needs to access project data THEN the Novel_Platform SHALL provide Tool functions that the AI can invoke through function calling
5. WHEN an AI request fails THEN the Novel_Platform SHALL implement retry logic with exponential backoff and provide meaningful error messages
6. THE Novel_Platform SHALL support multiple AI providers including OpenAI, DeepSeek, and local models through a unified interface
7. WHEN a complex creative task is requested THEN the Novel_Platform SHALL utilize Java 22 CompletableFuture with Virtual Threads to orchestrate multiple specialized Agents in parallel
8. WHEN multiple Agents are working THEN the Novel_Platform SHALL expose their thought processes to the user through real-time status events

---

### Requirement 8: 创作阶段编排（CreationPhase状态机）

**User Story:** As a 创作者, I want to 系统引导我完成从构思到成稿的完整创作流程, so that 我可以有条理地推进创作。

#### Acceptance Criteria

1. WHEN a new project is created THEN the Novel_Platform SHALL initialize creation phase to WELCOME
2. WHEN the creation phase transitions THEN the Novel_Platform SHALL follow the valid state machine: WELCOME → INITIALIZATION → WORLD_BUILDING → CHARACTER_CREATION → PLOTTING → DRAFTING → MAINTENANCE
3. WHEN the creation phase changes THEN the Novel_Platform SHALL adjust the available AI tools and system prompts accordingly
4. WHEN a user is in DRAFTING phase THEN the Novel_Platform SHALL enable content generation tools and RAG search capabilities
5. WHEN a user is in MAINTENANCE phase THEN the Novel_Platform SHALL enable editing tools and consistency checking capabilities

---

### Requirement 9: RAG检索增强生成

**User Story:** As a 创作者, I want to AI在生成内容时能够准确引用我的设定, so that 生成的内容与已有设定保持一致。

#### Acceptance Criteria

1. WHEN content is created or updated THEN the Novel_Platform SHALL generate vector embeddings using the configured embedding model
2. WHEN a RAG search is performed THEN the Novel_Platform SHALL use cosine similarity to find the top-K most relevant chunks
3. WHEN retrieving context for AI generation THEN the Novel_Platform SHALL implement parent-child indexing strategy: search on child chunks, return parent blocks
4. WHEN embedding generation is requested THEN the Novel_Platform SHALL check cache first and only call the embedding API on cache miss
5. WHEN the embedding model is unavailable THEN the Novel_Platform SHALL fall back to keyword-based search with BM25 algorithm
6. THE Novel_Platform SHALL support semantic chunking with cliff detection to preserve semantic boundaries
7. WHEN content is marked dirty THEN the Novel_Platform SHALL assign a new version number to the pending embedding while retaining the old version
8. WHEN RAG search is performed THEN the Novel_Platform SHALL filter results to exclude chunks with is_dirty=true or outdated version numbers
9. WHEN new embedding generation completes THEN the Novel_Platform SHALL atomically swap version numbers and delete the old embedding

---

### Requirement 10: AI工具注册与调用（Tool/DomainAdapter）

**User Story:** As a 系统, I want to 为AI提供结构化的工具调用能力, so that AI可以安全地读写项目数据。

#### Acceptance Criteria

1. WHEN the application starts THEN the Novel_Platform SHALL register all domain adapters as Spring AI Tool beans
2. WHEN the AI invokes a tool THEN the Novel_Platform SHALL validate parameters and route to the corresponding domain service
3. WHEN a tool execution completes THEN the Novel_Platform SHALL emit a ToolExecutionEvent for monitoring and logging
4. WHEN a tool execution fails THEN the Novel_Platform SHALL return a structured error response that the AI can interpret
5. WHEN tools are registered for a creation phase THEN the Novel_Platform SHALL only expose tools relevant to that phase
6. THE Novel_Platform SHALL implement a UniversalCrudTool that supports CRUD operations on all domain entities through a unified interface

---

### Requirement 11: 多模型配置与路由

**User Story:** As a 创作者, I want to 为不同创作场景配置不同的AI模型, so that 我可以根据任务特点选择最合适的模型。

#### Acceptance Criteria

1. WHEN a user configures AI providers THEN the Novel_Platform SHALL store provider credentials encrypted and validate connectivity
2. WHEN a user assigns models to scenes THEN the Novel_Platform SHALL persist the mapping between scene types (CREATIVE, STRUCTURE, WRITING, ANALYSIS) and model configurations
3. WHEN an AI request is made THEN the Novel_Platform SHALL resolve the appropriate model based on current scene type and user configuration
4. WHEN no user configuration exists THEN the Novel_Platform SHALL use system default model configurations
5. WHEN a configured model is unavailable THEN the Novel_Platform SHALL attempt fallback to alternative providers in priority order

---

### Requirement 12: 内容生成与流式响应

**User Story:** As a 创作者, I want to 实时看到AI生成的内容, so that 我可以及时干预和调整生成方向。

#### Acceptance Criteria

1. WHEN content generation is requested THEN the Novel_Platform SHALL return a Server-Sent Events stream with content chunks
2. WHEN the user cancels generation THEN the Novel_Platform SHALL immediately stop the AI request and close the SSE connection
3. WHEN generation completes THEN the Novel_Platform SHALL emit a "done" event and persist the generated content
4. WHEN generation encounters an error THEN the Novel_Platform SHALL emit an "error" event with error details and gracefully close the connection
5. WHEN tools are invoked during generation THEN the Novel_Platform SHALL emit "tool_start" and "tool_end" events for UI feedback

---

### Requirement 13: 缓存与性能优化

**User Story:** As a 系统, I want to 优化响应速度和资源使用, so that 用户获得流畅的创作体验。

#### Acceptance Criteria

1. WHEN embedding vectors are requested THEN the Novel_Platform SHALL check L1 local cache (Caffeine) first, then L2 Redis cache, before calling the embedding API
2. WHEN frequently accessed data is requested THEN the Novel_Platform SHALL serve from cache with configurable TTL
3. WHEN cache entries expire THEN the Novel_Platform SHALL lazily refresh on next access rather than proactively
4. WHEN the system starts THEN the Novel_Platform SHALL warm up caches for active projects
5. THE Novel_Platform SHALL maintain cache hit rate metrics and expose them through actuator endpoints

---

### Requirement 14: 数据持久化与迁移

**User Story:** As a 系统, I want to 安全可靠地存储和迁移数据, so that 用户数据不会丢失且系统可以平滑升级。

#### Acceptance Criteria

1. WHEN the application starts THEN the Novel_Platform SHALL execute pending Flyway migrations in order
2. WHEN a migration fails THEN the Novel_Platform SHALL rollback the transaction and prevent application startup
3. WHEN vector data is stored THEN the Novel_Platform SHALL use pgvector extension with HNSW index type for optimal recall and query performance
4. WHEN JSONB data is stored THEN the Novel_Platform SHALL create GIN indexes for efficient querying
5. THE Novel_Platform SHALL implement soft delete for critical entities with deleted_at timestamp
6. WHEN HNSW index is created THEN the Novel_Platform SHALL configure m=16 and ef_construction=64 parameters for balanced build time and recall

---

### Requirement 15: 错误处理与可观测性

**User Story:** As a 运维人员, I want to 监控系统健康状态和排查问题, so that 我可以保障系统稳定运行。

#### Acceptance Criteria

1. WHEN an unhandled exception occurs THEN the Novel_Platform SHALL log the full stack trace and return a sanitized error response to the client
2. WHEN an API request is processed THEN the Novel_Platform SHALL log request ID, duration, and status for tracing
3. WHEN AI services are called THEN the Novel_Platform SHALL record token usage, latency, and success rate metrics
4. WHEN health check is requested THEN the Novel_Platform SHALL verify database, Redis, and AI provider connectivity
5. THE Novel_Platform SHALL expose Prometheus-compatible metrics through Spring Actuator

---

### Requirement 16: 限流与安全防护

**User Story:** As a 系统, I want to 防止滥用和攻击, so that 系统资源得到合理分配和保护。

#### Acceptance Criteria

1. WHEN API requests exceed rate limit THEN the Novel_Platform SHALL return HTTP 429 status with retry-after header
2. WHEN rate limiting is applied THEN the Novel_Platform SHALL use token bucket algorithm with configurable capacity and refill rate
3. WHEN sensitive operations are performed THEN the Novel_Platform SHALL require re-authentication within the last 5 minutes
4. WHEN user input is processed THEN the Novel_Platform SHALL sanitize to prevent injection attacks
5. THE Novel_Platform SHALL implement CORS policy allowing only configured origins

---

### Requirement 17: 异步任务处理

**User Story:** As a 系统, I want to 异步处理耗时任务, so that 用户请求不会被阻塞。

#### Acceptance Criteria

1. WHEN embedding generation is triggered THEN the Novel_Platform SHALL process asynchronously using a dedicated thread pool
2. WHEN multiple story blocks need re-indexing THEN the Novel_Platform SHALL batch process with configurable batch size
3. WHEN async task fails THEN the Novel_Platform SHALL implement retry with exponential backoff up to 3 attempts
4. WHEN async task status is queried THEN the Novel_Platform SHALL return current status and progress percentage
5. THE Novel_Platform SHALL implement debouncing for rapid content updates to avoid redundant embedding generation

---

### Requirement 18: 风格学习与一致性

**User Story:** As a 创作者, I want to AI学习并模仿我的写作风格, so that 生成的内容与我的风格保持一致。

#### Acceptance Criteria

1. WHEN a user provides style samples THEN the Novel_Platform SHALL analyze and extract style features
2. WHEN content is generated THEN the Novel_Platform SHALL include style guidance in the AI prompt
3. WHEN a user edits AI-generated content THEN the Novel_Platform SHALL track edit patterns for style refinement
4. WHEN style analysis is requested THEN the Novel_Platform SHALL return metrics including sentence length, vocabulary richness, and dialogue ratio

---

### Requirement 19: 逻辑预检与一致性检查

**User Story:** As a 创作者, I want to 在发布前检查内容的逻辑一致性, so that 我可以避免设定冲突和剧情漏洞。

#### Acceptance Criteria

1. WHEN preflight check is requested THEN the Novel_Platform SHALL verify character consistency across chapters
2. WHEN preflight check is requested THEN the Novel_Platform SHALL detect timeline conflicts and impossible sequences
3. WHEN preflight check is requested THEN the Novel_Platform SHALL identify unclosed plot loops that should be resolved
4. WHEN inconsistencies are found THEN the Novel_Platform SHALL return detailed reports with specific locations and suggestions
5. THE Novel_Platform SHALL support both rule-based checks and AI-enhanced deep analysis

---

### Requirement 20: Token使用监控与成本控制

**User Story:** As a 创作者, I want to 了解和控制AI使用成本, so that 我可以在预算内高效创作。

#### Acceptance Criteria

1. WHEN an AI request completes THEN the Novel_Platform SHALL record input tokens, output tokens, model name, and calculated cost
2. WHEN a user queries usage THEN the Novel_Platform SHALL return aggregated statistics by day, model, and operation type
3. WHEN daily usage exceeds configured limit THEN the Novel_Platform SHALL warn the user and optionally block further requests
4. WHEN usage report is requested THEN the Novel_Platform SHALL generate detailed breakdown with cost projections

---

### Requirement 21: 从正文反推设定（内容逆向解析）

**User Story:** As a 创作者, I want to 将已写好的正文内容导入系统并自动提取设定, so that 我可以快速迁移老书并建立完整的知识库。

#### Acceptance Criteria

1. WHEN a user submits raw novel text THEN the Novel_Platform SHALL invoke AI to extract character entities including name, role, description, and personality traits
2. WHEN character extraction completes THEN the Novel_Platform SHALL automatically create Character records and associate them with the project
3. WHEN a user submits raw novel text THEN the Novel_Platform SHALL invoke AI to extract world-building elements and create corresponding WikiEntry records
4. WHEN multiple characters are extracted THEN the Novel_Platform SHALL analyze their interactions and generate relationship graph edges
5. WHEN extraction is performed on text exceeding 3000 characters THEN the Novel_Platform SHALL process in chunks and merge results with deduplication
6. WHEN duplicate entities are detected during extraction THEN the Novel_Platform SHALL prompt user for merge confirmation or create as separate entries
7. THE Novel_Platform SHALL provide a preview interface showing extracted entities before committing to the database

---

### Requirement 22: 状态快照存储优化（关键帧+增量策略）

**User Story:** As a 系统, I want to 高效存储角色和设定的演进历史, so that 数据库不会因大量快照而膨胀。

#### Acceptance Criteria

1. WHEN a state snapshot is created THEN the Novel_Platform SHALL determine if it should be a keyframe or delta based on chapter interval
2. WHEN the chapter count since last keyframe exceeds 10 THEN the Novel_Platform SHALL create a full keyframe snapshot
3. WHEN the chapter count since last keyframe is less than 10 THEN the Novel_Platform SHALL create a delta snapshot containing only changed fields
4. WHEN a state is queried at a specific chapter THEN the Novel_Platform SHALL reconstruct the full state by applying deltas to the nearest preceding keyframe
5. WHEN a keyframe is created THEN the Novel_Platform SHALL set is_keyframe flag to true and store complete state_data
6. WHEN a delta is created THEN the Novel_Platform SHALL store only the JSON diff between current and previous state
7. THE Novel_Platform SHALL provide a background job to compact old deltas into keyframes for query performance optimization

---

### Requirement 23: 高并发与性能（Virtual Threads）

**User Story:** As a 系统, I want to 承载百万级并发请求, so that 在晚间创作高峰期依然流畅。

#### Acceptance Criteria

1. WHEN I/O intensive tasks are executed THEN the Novel_Platform SHALL run them on Virtual Threads to support massive concurrency
2. WHEN vector search is performed THEN the Novel_Platform SHALL use PostgreSQL 18 HNSW index to achieve sub-100ms response for billion-scale vectors
3. WHEN multiple Agents work in parallel THEN the Novel_Platform SHALL use Structured Concurrency to manage their lifecycle
4. WHEN burst load occurs THEN the Novel_Platform SHALL support horizontal scaling with stateless service design
5. THE Novel_Platform SHALL maintain P99 latency under 500ms for all API endpoints under normal load

---

### Requirement 24: 多模态可视化（Multimodal）

**User Story:** As a 创作者, I want to 直观地看到我的角色和场景, so that 我能获得更多灵感。

#### Acceptance Criteria

1. WHEN a character state changes significantly THEN the Novel_Platform SHALL support generating updated character portrait via image generation model
2. WHEN a scene description is provided THEN the Novel_Platform SHALL support one-click conversion to concept art
3. WHEN voice input is received THEN the Novel_Platform SHALL transcribe to text and auto-extract settings to populate Wiki
4. THE Novel_Platform SHALL expose multimodal capabilities through Spring AI 1.1.2 Multimodal API
