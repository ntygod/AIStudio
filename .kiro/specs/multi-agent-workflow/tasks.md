# Implementation Plan

## Phase 1: 核心基础设施

- [x] 1. 创建工作流核心接口和数据模型


  - [x] 1.1 创建 PreprocessingContext record


    - 定义 ragResults, characterStates, styleContext, additionalContext 字段
    - 实现 toContextString() 方法用于注入到 Prompt
    - 实现 empty() 静态工厂方法
    - _Requirements: 2.1, 11.1_
  - [x] 1.2 创建 CharacterState record


    - 定义 characterId, name, currentState, attributes 字段
    - _Requirements: 2.3_
  - [x] 1.3 创建 PostProcessingResult record


    - 定义 type, content, warnings 字段
    - 实现 toJson() 方法
    - _Requirements: 3.4, 3.5_
  - [x] 1.4 创建 Workflow 接口


    - 定义 getName(), getSupportedIntents(), execute() 方法
    - _Requirements: 1.1_
  - [x] 1.5 创建 WorkflowType 枚举


    - 定义 CONTENT_GENERATION, CREATIVE_DESIGN, PLANNING, QUALITY_CHECK, SIMPLE_AGENT, CHAIN 类型
    - 实现 fromIntent() 静态方法
    - _Requirements: 1.1_
  - [ ]* 1.6 Write property test for Intent-to-WorkflowType mapping
    - **Property 1: Intent-to-Workflow Mapping Consistency**
    - **Validates: Requirements 1.1**

- [x] 2. 创建 AbstractWorkflow 基类


  - [x] 2.1 实现 preprocess() 钩子方法


    - 默认返回 PreprocessingContext.empty()
    - 子类可覆盖实现并行预处理
    - _Requirements: 2.1_
  - [x] 2.2 实现 execute() 模板方法

    - Phase 0: 调用 preprocess()
    - Phase 1: Skill 注入（如果 needsSkillInjection() 返回 true）
    - Phase 2: Agent 执行
    - Phase 3: 同步后处理
    - _Requirements: 1.2, 3.4, 8.1_
  - [x] 2.3 实现 postprocess() 钩子方法

    - 默认返回 Mono.empty()
    - 子类可覆盖实现同步后处理
    - _Requirements: 3.4, 3.5_
  - [x] 2.4 实现 injectSkills() 方法

    - 调用 PromptInjector.autoSelectSkills()
    - 基于预处理上下文选择 Skills
    - _Requirements: 8.1, 8.2_
  - [ ]* 2.5 Write property test for event ordering
    - **Property 7: Event Ordering**
    - **Validates: Requirements 9.1-9.7**

- [x] 3. Checkpoint - Make sure all tests are passing


  - Ensure all tests pass, ask the user if questions arise.

## Phase 2: 工作流执行器

- [x] 4. 创建 WorkflowExecutor
  - [x] 4.1 实现工作流注册机制
    - 注入所有 Workflow 实现
    - 构建 Intent → Workflow 映射
    - _Requirements: 1.1_
  - [x] 4.2 实现 execute() 方法
    - 根据 Intent 选择工作流
    - 发布 thought 事件
    - 执行工作流并返回 SSE 流
    - _Requirements: 1.1, 1.2, 1.4_
  - [x] 4.3 实现错误处理和降级
    - 未找到工作流时降级到 SimpleAgentWorkflow
    - 执行失败时发送 error 事件
    - _Requirements: 1.5_
  - [ ]* 4.4 Write property test for workflow selection
    - **Property 1: Intent-to-Workflow Mapping Consistency**
    - **Validates: Requirements 1.1**

## Phase 3: 具体工作流实现

- [x] 5. 实现 ContentGenerationWorkflow
  - [x] 5.1 实现 preprocess() 并行预处理
    - 使用 Mono.zip() 并行执行
    - Task 1: HybridSearchService.search() RAG 检索
    - Task 2: getCharacterStates() 角色状态
    - Task 3: HybridSearchService.buildContextForGeneration() 风格样本
    - **关键：使用 subscribeOn(Schedulers.boundedElastic()) 避免阻塞 Netty IO 线程**
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 3.2, 15.1, 15.2, 15.3_
  - [x] 5.2 实现 postprocess() 同步一致性检查
    - 调用 ConsistencyAgent.execute()
    - 返回 PostProcessingResult
    - _Requirements: 3.4, 3.5_
  - [x] 5.3 配置 Skill 注入
    - 覆盖 needsSkillInjection() 返回 true
    - 支持 ActionSkill, PsychologySkill, DescriptionSkill
    - _Requirements: 3.3, 8.1, 8.2_
  - [ ]* 5.4 Write property test for synchronous consistency check
    - **Property 3: ContentGenerationWorkflow Triggers Synchronous Consistency Check**
    - **Validates: Requirements 3.4, 3.5**

- [x] 6. 实现 CreativeDesignWorkflow
  - [x] 6.1 实现 preprocess() 并行预处理
    - RAG 检索 + 原型库获取
    - _Requirements: 4.2_
  - [x] 6.2 实现 getMainAgent() 动态选择
    - PLAN_WORLD, BRAINSTORM_IDEA → WorldBuilderAgent
    - 其他 → CharacterAgent
    - _Requirements: 4.1_
  - [x] 6.3 实现 postprocess() 异步关系图更新
    - 异步调用 RelationshipGraphService.buildGraph()
    - _Requirements: 4.3, 4.4_
  - [ ]* 6.4 Write property test for relationship graph update
    - **Property 6: CreativeDesignWorkflow Updates Relationship Graph**
    - **Validates: Requirements 4.3**

- [x] 7. 实现 SimpleAgentWorkflow
  - [x] 7.1 实现 execute() 直接执行
    - 不调用 preprocess()
    - 直接执行对应 Agent
    - _Requirements: 7.1, 7.2, 7.3_
  - [x] 7.2 配置 Intent → Agent 映射
    - GENERAL_CHAT → ChatAgent
    - GENERATE_NAME → NameGeneratorAgent
    - SUMMARIZE → SummaryAgent
    - EXTRACT_ENTITY → ExtractionAgent
    - _Requirements: 7.1_
  - [ ]* 7.3 Write property test for direct execution
    - **Property 5: SimpleAgentWorkflow Direct Execution**
    - **Validates: Requirements 7.2, 7.3**

- [ ] 8. Checkpoint - Make sure all tests are passing
  - Ensure all tests pass, ask the user if questions arise.

## Phase 4: Agent 修改（"哑 Agent" 策略）

- [x] 9. 修改 WriterAgent


  - [x] 9.1 修改 buildUserPrompt() 优先使用预处理上下文


    - 检查 metadata.preprocessingContext
    - 如果存在，使用 context.toContextString()
    - 如果不存在，降级到直接 RAG 调用
    - **关键：禁止在实例字段存储请求状态，所有上下文通过参数传递**
    - _Requirements: 10.1, 10.2, 11.2, 11.3, 11.4, 16.1, 16.2, 16.3, 16.4_

  - [x] 9.2 修改 stream() 支持增强系统提示词

    - 检查 metadata.enhancedSystemPrompt
    - 如果存在，使用增强提示词
    - _Requirements: 11.5_
  - [ ]* 9.3 Write property test for context priority
    - **Property 2: Preprocessing Context Injection**
    - **Validates: Requirements 2.1, 10.1, 11.1-11.4**



- [x] 10. 修改 CharacterAgent

  - [x] 10.1 修改 buildUserPrompt() 优先使用预处理上下文

    - 同 WriterAgent 的修改模式
    - _Requirements: 10.1, 10.2, 11.2, 11.3, 11.4_
  - [ ]* 10.2 Write property test for fallback behavior
    - **Property 11: Agent Fallback to Direct RAG**
    - **Validates: Requirements 11.4**



- [x] 11. 修改 WorldBuilderAgent（如果有 RAG 调用）

  - [x] 11.1 修改 buildUserPrompt() 优先使用预处理上下文

    - 同 WriterAgent 的修改模式
    - _Requirements: 10.1, 10.2_


- [x] 12. Checkpoint - Make sure all tests are passing


  - Ensure all tests pass, ask the user if questions arise.

## Phase 5: 链式执行工作流

- [x] 13. 创建链式执行基础设施



  - [x] 13.1 创建 ChainStepType 枚举

    - AGENT, USER_INTERACTION
    - _Requirements: 13.1_

  - [x] 13.2 创建 ChainStep record

    - 定义 type, agent, description 字段
    - 实现 agent() 和 userInteraction() 静态工厂方法
    - _Requirements: 13.1_

  - [x] 13.3 创建 AbstractChainWorkflow 基类



    - 实现 getChainSteps() 抽象方法
    - 实现 executeFullChain() 无用户交互执行
    - 实现 executeWithUserInteraction() 带用户交互执行
    - 实现 continueChain() 用户选择后继续执行
    - **关键：在 execute() 入口检查 metadata.chainContext 自动判断是否继续链**
    - _Requirements: 13.1, 13.2, 13.5, 14.6, 14.7_
  - [ ]* 13.4 Write property test for chain context propagation
    - **Property 8: Chain Execution Context Propagation**
    - **Validates: Requirements 13.1, 13.2**
  - [ ]* 13.5 Write property test for chain abort on failure
    - **Property 9: Chain Execution Abort on Failure**
    - **Validates: Requirements 13.3**
  - [ ]* 13.6 Write property test for chain continuation
    - **Property 12: Chain Continuation from Context**
    - **Validates: Requirements 14.6, 14.7**





- [x] 14. 新增 Intent 枚举值

  - [x] 14.1 添加 BRAINSTORM_AND_EXPAND Intent

    - _Requirements: 14.1_
  - [x] 14.2 添加 OUTLINE_TO_CHAPTER Intent
    - _Requirements: 14.2_
  - [x] 14.3 添加 CHARACTER_TO_SCENE Intent
    - _Requirements: 14.3_

- [x] 15. 实现 BrainstormExpandWorkflow

  - [x] 15.1 实现 getChainSteps()

    - BrainstormAgent → UserInteraction → WriterAgent
    - _Requirements: 14.1_

  - [x] 15.2 配置用户交互
    - requiresUserInteraction() 返回 true
    - getUserInteractionAfterStep() 返回 0

    - _Requirements: 14.5_
  - [x] 15.3 实现 buildInteractionOptions()
    - 解析 BrainstormAgent 输出，提取选项
    - _Requirements: 14.5_
  - [ ]* 15.4 Write property test for user interaction pause
    - **Property 10: User Interaction Pause in Chain Workflow**
    - **Validates: Requirements 13.5, 14.5**

- [x] 16. 实现 OutlineToChapterWorkflow



  - [x] 16.1 实现 getChainSteps()
    - PlannerAgent → WriterAgent

    - _Requirements: 14.2_

- [x] 17. 实现 CharacterToSceneWorkflow

  - [x] 17.1 实现 getChainSteps()

    - CharacterAgent → WriterAgent
    - _Requirements: 14.3_

- [x] 18. Checkpoint - Make sure all tests are passing
  - Ensure all tests pass, ask the user if questions arise.

## Phase 6: 集成和清理

- [x] 19. 修改 AgentRouter 委托给 WorkflowExecutor
  - [x] 19.1 注入 WorkflowExecutor
    - _Requirements: 10.4_
  - [x] 19.2 修改 route() 方法
    - 意图识别后委托给 WorkflowExecutor.execute()
    - 保留 Fast Path 逻辑
    - _Requirements: 10.4_

- [x] 20. 删除 SceneCreationOrchestrator
  - [x] 20.1 验证 ContentGenerationWorkflow 功能完整
    - 对比 SceneCreationOrchestrator 的所有功能
    - _Requirements: 12.1, 12.2_
  - [x] 20.2 更新引用代码
    - 查找所有引用 SceneCreationOrchestrator 的代码
    - 更新为使用 ContentGenerationWorkflow
    - _Requirements: 12.4_
  - [x] 20.3 删除 SceneCreationOrchestrator 类
    - _Requirements: 12.3, 10.5_

- [x] 21. 更新 WorkflowType 枚举
  - [x] 21.1 添加链式工作流类型
    - BRAINSTORM_EXPAND, OUTLINE_TO_CHAPTER, CHARACTER_TO_SCENE
    - _Requirements: 14.1, 14.2, 14.3_

- [x] 22. 验证 Reactor 线程安全
  - [x] 22.1 验证 preprocess() 不阻塞 Netty IO 线程
    - 确保所有 Workflow 的 preprocess() 使用 subscribeOn(Schedulers.boundedElastic())
    - _Requirements: 15.1, 15.2, 15.3_
  - [ ]* 22.2 Write property test for non-blocking preprocessing
    - **Property 13: Non-Blocking Preprocessing**
    - **Validates: Requirements 15.1, 15.2, 15.3**

- [x] 23. 验证 Agent 无状态约束
  - [x] 23.1 代码审查 Agent 类
    - 确保 WriterAgent, CharacterAgent, WorldBuilderAgent 等没有请求相关的实例字段
    - 确保所有上下文通过 ChatRequest.metadata 传递
    - _Requirements: 16.1, 16.2, 16.3, 16.4_
  - [ ]* 23.2 Write property test for agent statelessness
    - **Property 14: Agent Statelessness**
    - **Validates: Requirements 16.1, 16.2, 16.3, 16.4**

- [x] 24. Final Checkpoint - Make sure all tests are passing



  - Ensure all tests pass, ask the user if questions arise.
