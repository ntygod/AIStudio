# Implementation Plan

## 1. 基础架构搭建

- [x] 1.1 创建 Agent 核心接口和抽象类
  - 定义 `Agent<I, O>` 接口，包含 execute(), executeAsync(), stream() 方法
  - 实现 `AbstractAgent` 抽象类，封装通用逻辑
  - 定义 `AgentCapability` record 用于能力声明
  - 定义 `ExecutionMode` 和 `AgentCategory` 枚举
  - _Requirements: 19.1, 19.2_

- [ ]* 1.2 编写 Agent 接口属性测试
  - **Property 1: Intent-based routing correctness**
  - **Validates: Requirements 1.1-1.9**

- [x] 1.3 创建 Intent 枚举和路由数据模型
  - 定义 `Intent` 枚举，包含所有意图类型和关键词
  - 定义 `IntentResult` record
  - 定义 `ChatRequest` 和 `ChatResponse` DTO
  - _Requirements: 1.1-1.9_

- [x] 1.4 创建 SSE 事件类型和流式响应基础设施
  - 定义 `SSEEventType` 枚举
  - 实现 `AgentThoughtEvent` 事件类
  - 实现 `ToolStatusEvent` 事件类
  - _Requirements: 15.1-15.5_

## 2. Fast Path 和路由层

- [x] 2.1 实现 FastPathFilter
  - 实现 `tryFastPath()` 方法，检查 intentHint 参数
  - 实现命令前缀识别（/write, /plan 等）
  - 添加 Fast Path 使用日志
  - _Requirements: 2.1-2.5_

- [ ]* 2.2 编写 Fast Path 属性测试
  - **Property 2: Fast Path skip behavior**
  - **Validates: Requirements 2.1**

- [x] 2.3 实现 ThinkingAgent
  - 实现 `RuleBasedClassifier` 规则引擎
  - 集成轻量级 LLM 模型用于不确定情况
  - 实现置信度阈值判断逻辑
  - _Requirements: 3.1-3.6_

- [ ]* 2.4 编写 ThinkingAgent 属性测试
  - **Property 3: Rule engine priority**
  - **Validates: Requirements 3.1-3.2**

- [x] 2.5 实现 AgentRouter
  - 实现 `route()` 方法，整合 FastPath 和 ThinkingAgent
  - 实现 `getCapabilities()` 方法
  - 实现阶段感知路由优先级
  - _Requirements: 1.1-1.9, 18.1-18.6_

## 3. Checkpoint - 确保路由层测试通过
- [x] 3. Ensure all tests pass, ask the user if questions arise.
  - Fixed ToolStatusEvent.java: renamed getTimestamp() to getEventTime() to avoid conflict with ApplicationEvent.getTimestamp()
  - All 61 tests passed (0 failures, 0 errors)
  - BUILD SUCCESS

## 4. Context Bus 和上下文管理

- [x] 4.1 实现 ContextBus 接口和 Redis 实现
  - 定义 `ContextBus` 接口
  - 实现 `RedisContextBus` 使用 Redis 存储
  - 实现 `InMemoryContextBus` 用于测试
  - _Requirements: 16.1-16.5_

- [ ]* 4.2 编写 Context Bus 属性测试
  - **Property 5: Session isolation**
  - **Validates: Requirements 16.4**

- [x] 4.3 实现 SessionContext 和 ContextEvent
  - 定义 `SessionContext` record
  - 定义 `ContextEvent` record
  - 定义 `RecentEntity` record
  - 实现 session 过期清理逻辑 (ContextBusConfig.SessionCleanupTask)
  - 添加 context-bus 配置到 application.yml
  - _Requirements: 16.1-16.5_

## 5. Skill Slots 架构

- [x] 5.1 创建 SkillSlot 接口和 SkillContext
  - 定义 `SkillSlot` 接口
  - 定义 `SkillContext` record
  - 定义 `ConfigurableSkillSlot` 可配置技能接口
  - 定义 `AbstractSkillSlot` 抽象基类
  - 定义 `SkillConfiguration` 配置类
  - _Requirements: 17.1-17.5_

- [x] 5.2 实现 SkillRegistry
  - 实现 `@PostConstruct` 自动发现
  - 实现 `getSkillsFor()` 方法
  - 实现 `getTriggeredSkills()` 方法
  - 实现运行时动态注册
  - _Requirements: 17.1, 17.5_

- [x] 5.3 实现 PromptInjector
  - 实现 `buildEnhancedSystemPrompt()` 方法
  - 实现 `autoSelectSkills()` 关键词匹配
  - 实现 `buildTriggeredPrompt()` 方法
  - 确保按优先级顺序注入
  - _Requirements: 17.2-17.4_

- [ ]* 5.4 编写 Skill Slots 属性测试
  - **Property 4: Skill injection by phase**
  - **Property 6: Skill priority ordering**
  - **Validates: Requirements 4.3, 17.3**

- [x] 5.5 实现内置 Skills
  - 实现 `DialogueSkill` - 对话生成 (优先级: 80)
  - 实现 `ActionSkill` - 动作场景 (优先级: 75)
  - 实现 `PsychologySkill` - 心理描写 (优先级: 70)
  - 实现 `DescriptionSkill` - 环境描写 (优先级: 65)
  - 实现 `PlotLoopReminderSkill` - 伏笔提醒 (优先级: 85)
  - 实现 `PolishSkill` - 文本润色 (优先级: 60)
  - _Requirements: 4.3_

## 6. Checkpoint - 确保基础架构测试通过
- [x] 6. Ensure all tests pass, ask the user if questions arise.
  - All tests passed (0 failures, 0 errors)
  - BUILD SUCCESS
  - Skill Slots 架构实现完成：
    - SkillSlot 接口和 SkillContext
    - SkillRegistry 自动发现和动态注册
    - PromptInjector 提示词注入
    - 6 个内置 Skills (Dialogue, Action, Psychology, Description, PlotLoopReminder, Polish)

## 7. 核心 Agent 实现

- [x] 7.1 实现 ChatAgent
  - 继承 BaseAgent
  - 实现通用对话逻辑
  - 实现专业意图检测和推荐
  - 集成 ContextBus 进行上下文管理
  - _Requirements: 9.1-9.5_

- [x] 7.2 实现 WriterAgent
  - 继承 BaseAgent
  - 集成 RAGSearchTool 和 StyleRetrieveTool
  - 实现流式内容生成
  - 支持上下文感知的内容生成
  - _Requirements: 4.1-4.6_

- [x] 7.3 实现 WorldBuilderAgent
  - 继承 BaseAgent
  - 实现灵感激发功能（原 IdeaAgent）
  - 实现世界观设计功能
  - 实现结构化 JSON 输出
  - _Requirements: 5.1-5.5_

- [x] 7.4 实现 CharacterAgent
  - 继承 BaseAgent
  - 实现角色设计功能
  - 实现关系网络设计（原 RelationshipAgent）
  - 实现原型匹配（原 ArchetypeAgent）
  - 实现结构化 JSON 输出
  - _Requirements: 6.1-6.5_

- [x] 7.5 实现 PlannerAgent
  - 继承 BaseAgent
  - 实现大纲规划功能
  - 实现伏笔管理（原 PlotLoopAgent）
  - 实现节奏分析（原 PacingAgent）
  - 实现结构化 JSON 输出
  - _Requirements: 7.1-7.6_

- [x] 7.6 实现 ConsistencyAgent
  - 继承 BaseAgent
  - 实现一致性检查功能
  - 实现文风分析（原 StyleAgent）
  - 集成 RAGSearchTool 和 PreflightTool
  - 实现警告列表和修正建议输出
  - _Requirements: 8.1-8.6_

## 8. Checkpoint - 确保核心 Agent 测试通过
- [x] 8. Ensure all tests pass, ask the user if questions arise.
  - All 61 tests passed (0 failures, 0 errors)
  - BUILD SUCCESS
  - Implemented 6 core Agents: ChatAgent, WriterAgent, WorldBuilderAgent, CharacterAgent, PlannerAgent, ConsistencyAgent

## 9. 工具层 Agent 实现（懒执行）

- [x] 9.1 实现 LazyExecutionManager
  - 定义懒执行 Agent 集合
  - 实现 `shouldExecute()` 判断逻辑
  - 实现手动触发接口
  - 实现 Agent 注册/注销机制
  - _Requirements: 19.5_

- [x] 9.2 实现 NameGeneratorAgent
  - 继承 BaseAgent
  - 实现名称生成逻辑
  - 支持角色名、地名、物品名、技能名、组织名
  - 自动注册到 LazyExecutionManager
  - _Requirements: 10.1-10.5_

- [x] 9.3 实现 SummaryAgent
  - 继承 BaseAgent
  - 实现摘要生成逻辑
  - 支持简要/标准/详细三种详细程度
  - 支持章节/角色/情节/世界观摘要类型
  - _Requirements: 11.1-11.5_

- [x] 9.4 实现 ExtractionAgent
  - 继承 BaseAgent
  - 实现实体抽取逻辑（角色、地点、物品、关系、事件）
  - 输出结构化 JSON 格式
  - 触发 RAG 索引更新事件
  - _Requirements: 12.1-12.5_

## 10. Agent 编排层

- [x] 10.1 实现 AgentOrchestrator
  - 实现并行执行（Virtual Threads + CompletableFuture）
  - 实现结果聚合和部分失败处理
  - 实现顺序链式执行
  - 实现竞争执行（任一成功即返回）
  - 实现重试逻辑（指数退避）
  - 实现完成/失败事件发布
  - _Requirements: 13.1-13.5_

- [x] 10.2 实现 SceneCreationOrchestrator
  - 编排 WriterAgent 和 ConsistencyAgent
  - 实现场景创作流程（流式）
  - 支持异步一致性检查触发
  - 支持内联一致性检查
  - _Requirements: 13.1-13.5_

## 11. Tool 注册和集成

- [x] 11.1 实现 ToolRegistry
  - 实现 @Tool 自动发现（@PostConstruct 扫描）
  - 实现按 CreationPhase 过滤
  - 实现调用日志记录
  - 实现工具统计功能
  - _Requirements: 14.1-14.5_

- [x] 11.2 集成现有 Tools
  - 自动发现并注册 RAGSearchTool
  - 自动发现并注册 UniversalCrudTool
  - 自动发现并注册 StyleRetrieveTool
  - 自动发现并注册 PreflightTool
  - 根据工具名称和描述自动推断适用阶段
  - _Requirements: 14.1-14.5_

## 12. API 层重构

- [x] 12.1 实现 AgentController
  - 创建 /api/v2/agent/chat 端点（SSE 流式响应）
  - 创建 /api/v2/agent/scene/create 端点（场景创作）
  - 创建 /api/v2/agent/capabilities 端点（能力查询）
  - 创建 /api/v2/agent/lazy-agents 端点（懒执行 Agent 管理）
  - 创建 /api/v2/agent/tools 端点（工具查询）
  - 集成 AgentOrchestrator 和 SceneCreationOrchestrator
  - _Requirements: 20.1-20.5_

- [x] 12.2 保持向后兼容
  - 保留原有 ChatController（/api/v2/chat）
  - 新 API 使用 /api/v2/agent 前缀
  - 两套 API 可并行使用
  - _Requirements: 20.5_

## 13. 配置和文档

- [x] 13.1 添加 Agent 配置
  - Fast Path 配置已在 FastPathFilter 中实现
  - ThinkingAgent 配置已在 RuleBasedClassifier 中实现
  - Lazy Execution 配置已在 LazyExecutionManager 中实现
  - _Requirements: 2.1, 3.1, 17.1_

- [x] 13.2 更新 API 文档
  - AgentController 使用 OpenAPI 注解
  - 所有端点都有 @Operation 描述
  - _Requirements: 20.1_

## 14. Final Checkpoint - 确保所有测试通过
- [x] 14. Ensure all tests pass, ask the user if questions arise.
  - All 61 tests passed (0 failures, 0 errors)
  - BUILD SUCCESS
  - 实现了完整的统一 Agent 架构
