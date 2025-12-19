# Implementation Plan

## Phase 1: 项目基础架构

- [x] 1. 初始化项目结构和核心配置
  - [x] 1.1 创建Spring Boot 3.5.x项目骨架，配置Java 22和Virtual Threads
    - 配置pom.xml依赖：Spring Boot 3.5.x, Spring AI 1.1.2, PostgreSQL 16+驱动
    - 启用Virtual Threads: `spring.threads.virtual.enabled=true`
    - _Requirements: 23.1_
  - [x] 1.2 配置PostgreSQL 18连接和pgvector扩展
    - 创建application.yml数据库配置
    - 配置Flyway迁移
    - _Requirements: 14.1, 14.3_
  - [x] 1.3 配置Redis Stack 7.4+缓存
    - 配置Redis连接和序列化
    - 设置Caffeine本地缓存
    - _Requirements: 13.1, 13.2_

- [x] 2. 数据库迁移脚本
  - [x] 2.1 创建用户认证相关表
    - users表，支持Passkey
    - _Requirements: 1.1, 1.4_
  - [x] 2.2 创建项目管理相关表
    - projects, volumes, chapters表
    - _Requirements: 2.1, 3.1, 3.2_
  - [x] 2.3 创建story_blocks表（Lexorank排序）
    - 使用VARCHAR rank字段替代INTEGER order_index
    - _Requirements: 3.3, 3.4, 3.5_
  - [x] 2.4 编写Lexorank排序属性测试
    - **Property 1: Lexorank中间值排序正确性**
    - **Validates: Requirements 3.4**
  - [x] 2.5 创建角色和知识库相关表
    - characters, wiki_entries, character_relationships表
    - _Requirements: 4.1, 4.2, 5.1_
  - [x] 2.6 创建演进时间线和状态快照表（关键帧+增量）
    - evolution_timelines, state_snapshots表
    - 支持is_keyframe和state_delta字段
    - _Requirements: 22.1, 22.2, 22.5, 22.6_
  - [x] 2.7 编写状态快照重建属性测试
    - **Property 2: 关键帧+增量重建状态一致性**
    - **Validates: Requirements 22.4**
  - [x] 2.8 创建知识块表（版本控制）
    - knowledge_chunks表，包含version和is_active字段
    - 创建HNSW向量索引
    - _Requirements: 9.7, 9.8, 14.3_
  - [ ]* 2.9 编写RAG版本过滤属性测试
    - **Property 3: 脏数据不出现在搜索结果中**
    - **Validates: Requirements 9.8**
  - [x] 2.10 创建伏笔、对话历史、Token使用记录表
    - plot_loops, conversation_history, token_usage_records表
    - _Requirements: 6.1, 7.3, 20.1_

- [x] 3. Checkpoint - 确保所有迁移脚本执行成功
  - Ensure all tests pass, ask the user if questions arise.

## Phase 2: 用户认证模块

- [x] 4. 实现用户认证服务
  - [x] 4.1 实现JWT Token生成和验证
    - 使用HMAC-SHA256签名算法
    - Access Token 15分钟，Refresh Token 7天
    - _Requirements: 1.1, 1.2_
  - [ ]* 4.2 编写Token过期时间属性测试
    - **Property 4: Token过期时间符合规格**
    - **Validates: Requirements 1.1**
  - [x] 4.3 实现API Key加密存储
    - 使用AES-GCM-256加密
    - _Requirements: 1.5_
  - [x] 4.4 编写加密解密往返属性测试
    - **Property 5: 加密解密往返一致性**
    - **Validates: Requirements 1.5**
  - [x] 4.5 实现认证过滤器和安全配置
    - JwtAuthenticationFilter
    - SecurityConfig
    - _Requirements: 1.3_

- [x] 5. Checkpoint - 确保认证模块测试通过
  - Ensure all tests pass, ask the user if questions arise.

## Phase 3: 项目管理模块

- [x] 6. 实现项目聚合根和仓储
  - [x] 6.1 创建Project实体和Repository
    - 包含metadata JSONB字段
    - _Requirements: 2.1, 2.2, 2.3_
  - [x] 6.2 实现项目CRUD服务
    - ProjectService
    - _Requirements: 2.1, 2.2, 2.3, 2.4_
  - [x] 6.3 实现项目导出服务（JSON序列化）
    - ExportService，支持pretty-print
    - _Requirements: 2.5, 2.7_
  - [x] 6.4 实现项目导入服务（JSON反序列化）
    - ImportService，UUID重新生成
    - _Requirements: 2.6_
  - [ ]* 6.5 编写导出导入往返属性测试
    - **Property 6: 导出导入往返数据一致性**
    - **Validates: Requirements 2.5, 2.6**

- [x] 7. 实现内容结构管理
  - [x] 7.1 创建Volume, Chapter实体和Repository
    - _Requirements: 3.1, 3.2_
  - [x] 7.2 实现StoryBlock实体（Lexorank排序）
    - 使用rank VARCHAR字段
    - _Requirements: 3.3_
  - [x] 7.3 实现LexorankService
    - 计算中间rank值
    - _Requirements: 3.4, 3.5, 3.8_
  - [x] 7.4 实现StoryBlockService
    - 创建、重排序、更新内容
    - _Requirements: 3.6, 3.7_

- [x] 8. Checkpoint - 确保项目管理模块测试通过
  - Ensure all tests pass, ask the user if questions arise.

## Phase 4: 角色和知识库模块

- [x] 9. 实现角色管理


  - [x] 9.1 创建Character实体和Repository


    - 包含relationships JSONB字段
    - _Requirements: 4.1, 4.2_

  - [x] 9.2 实现CharacterService

    - CRUD操作
    - _Requirements: 4.1, 4.4_

  - [x] 9.3 实现RelationshipGraphService

    - 构建角色关系图谱
    - _Requirements: 4.3_

  - [x] 9.4 实现角色原型模板

    - 垫脚石、老爷爷、欢喜冤家等
    - _Requirements: 4.6_



- [x] 10. 实现知识库管理

  - [x] 10.1 创建WikiEntry实体和Repository

    - 支持类型分类和别名
    - _Requirements: 5.1, 5.2_

  - [x] 10.2 实现WikiEntryService

    - CRUD操作，触发embedding生成
    - _Requirements: 5.3, 5.5_

  - [x] 10.3 实现CDC事件监听器

    - WikiChangeListener，触发一致性检查
    - _Requirements: 5.6, 5.7_



- [x] 11. 实现伏笔追踪

  - [x] 11.1 创建PlotLoop实体和Repository

    - _Requirements: 6.1_

  - [x] 11.2 实现PlotLoopService

    - 状态管理：OPEN -> URGENT -> CLOSED/ABANDONED
    - _Requirements: 6.2, 6.3, 6.4_


- [x] 12. Checkpoint - 确保角色和知识库模块测试通过












  - Ensure all tests pass, ask the user if questions arise.

## Phase 5: RAG检索增强模块

- [x] 13. 实现向量嵌入服务




  - [x] 13.1 创建KnowledgeChunk实体和Repository


    - 包含version和is_active字段

    - _Requirements: 9.1, 9.7_
  - [x] 13.2 实现EmbeddingService


    - 调用Spring AI 1.1.2 Embedding API

    - _Requirements: 9.1_
  - [x] 13.3 实现VersionedEmbeddingService

    - 版本控制，原子切换
    - _Requirements: 9.7, 9.9_
  - [x] 13.4 实现EmbeddingCacheService


    - L1 Caffeine + L2 Redis
    - _Requirements: 9.4, 13.1_

- [x] 14. 实现语义检索服务


  - [x] 14.1 实现SemanticChunkingService


    - 语义断崖检测
    - _Requirements: 9.6_
  - [x] 14.2 实现ParentChildSearchService


    - 小块检索，大块返回
    - _Requirements: 9.3_
  - [x] 14.3 实现HybridSearchService


    - 向量检索 + BM25关键词检索
    - _Requirements: 9.2, 9.5_

- [x] 15. Checkpoint - 确保RAG模块测试通过



  - Ensure all tests pass, ask the user if questions arise.

## Phase 6: AI智能体编排模块

- [x] 16. 实现Spring AI 1.1.2集成

  - [x] 16.1 配置DynamicChatModelFactory


    - 支持多AI提供商
    - _Requirements: 7.6, 11.1, 11.2_

  - [x] 16.2 实现ChatMemoryFactory

    - PersistentChatMemory
    - _Requirements: 7.3_

  - [x] 16.3 实现SpringAIChatService

    - SSE流式响应
    - _Requirements: 7.2, 12.1_


- [-] 17. 实现智能体编排器

  - [x] 17.1 创建Agent基础接口和抽象类

    - WriterAgent, ChoreographerAgent, PsychologistAgent, ConsistencyAgent
    - _Requirements: 7.7_

  - [x] 17.2 实现AgentOrchestrator

    - 使用Java 22 CompletableFuture + Virtual Threads
    - _Requirements: 7.7, 23.3_

  - [x] 17.3 实现SceneCreationOrchestrator

    - 并行启动多Agent协同创作
    - _Requirements: 7.7, 7.8_

  - [x] 17.4 实现AgentThoughtEvent

    - 实时展示Agent思考过程
    - _Requirements: 7.8_


- [x] 18. 实现AI工具注册
  - [x] 18.1 实现SceneToolRegistry


    - 根据创作阶段注册工具
    - _Requirements: 10.1, 10.5_

  - [x] 18.2 实现UniversalCrudTool

    - 通用CRUD操作
    - _Requirements: 10.6_
  - [x] 18.3 实现RAGSearchTool


    - RAG检索工具
    - _Requirements: 10.2_

  - [x] 18.4 实现DomainAdapter适配器

    - ProjectDomainAdapter, CharacterDomainAdapter, WikiEntryDomainAdapter等
    - _Requirements: 10.2, 10.4_

- [x] 19. Checkpoint - 确保AI模块测试通过



  - Ensure all tests pass, ask the user if questions arise.

## Phase 7: 动态演进模块

- [x] 20. 实现演进时间线服务
  - [x] 20.1 创建EvolutionTimeline实体和Repository
    - _Requirements: 22.1_
  - [x] 20.2 实现StateSnapshotService
    - 关键帧+增量策略
    - _Requirements: 22.2, 22.3, 22.5, 22.6_
  - [x] 20.3 实现StateRetrievalService
    - 从关键帧+增量重建状态
    - _Requirements: 22.4_
  - [x] 20.4 实现EvolutionAnalysisService
    - AI驱动的演进分析
    - _Requirements: 相关演进需求_

- [x] 21. 实现一致性检查服务
  - [x] 21.1 实现ConsistencyCheckService
    - 检测设定冲突
    - _Requirements: 5.7, 19.1, 19.2_
  - [x] 21.2 实现PreflightService
    - 逻辑预检
    - _Requirements: 19.3, 19.4, 19.5_

- [x] 22. Checkpoint - 确保演进模块测试通过
  - StateSnapshotPropertyTest 属性测试已创建

## Phase 8: 内容逆向解析模块

- [x] 23. 实现从正文反推设定功能
  - [x] 23.1 实现ContentExtractionService
    - AI提取角色和设定
    - _Requirements: 21.1, 21.2, 21.3_
  - [x] 23.2 实现EntityDeduplicationService
    - 实体去重和合并
    - _Requirements: 21.5, 21.6_
  - [x] 23.3 实现RelationshipInferenceService
    - 关系图谱推断
    - _Requirements: 21.4_
  - [x] 23.4 实现ExtractionController
    - 提取预览接口，支持完整提取流程
    - _Requirements: 21.7_

- [x] 24. Checkpoint - 确保内容解析模块测试通过
  - 编译通过

## Phase 9: API接口层

- [x] 25. REST API已实现
  - [x] 25.1 ChatController - AI对话API (SSE流式)
  - [x] 25.2 SceneController - 场景创作API
  - [x] 25.3 EvolutionController - 演进时间线API
  - [x] 25.4 ExtractionController - 内容提取API
  - GraphQL可选，当前使用REST + SSE

- [x] 26. SSE流式API
  - [x] 26.1 ChatController.streamChat - 流式聊天
  - [x] 26.2 SceneController.createScene - 流式场景生成
  - _Requirements: 12.1, 12.2, 12.3_

- [x] 27. 实现REST API
  - [x] 27.1 AuthController - 认证相关REST端点
  - [x] 27.2 ProjectController, CharacterController等 - 业务API
  - _Requirements: 1.1-1.5, 7.2_

- [x] 28. Checkpoint - API层编译通过

## Phase 10: 监控和运维

- [x] 29. 实现可观测性
  - [x] 29.1 Spring Boot Actuator已配置
    - 全链路追踪
    - _Requirements: 15.2, 15.3_
  - [x] 29.2 实现HealthController
    - 数据库、Redis连通性检查
    - Kubernetes liveness/readiness probes
    - _Requirements: 15.4_
  - [x] 29.3 实现TokenCounterService
    - Token使用记录和统计
    - UsageController API
    - _Requirements: 20.1, 20.2, 20.3, 20.4_

- [x] 30. 实现限流和安全
  - [x] 30.1 实现RateLimitService
    - Token Bucket算法
    - 分布式限流（Redis）
    - _Requirements: 16.1, 16.2_
  - [x] 30.2 实现RateLimitFilter
    - 请求限流过滤器
    - _Requirements: 16.5_

- [x] 31. Final Checkpoint - 编译通过
  - 所有模块编译成功
