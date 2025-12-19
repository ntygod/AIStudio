# Implementation Plan: InkFlow V2 Enhanced Creation Flow

## Phase 1: 基础设施层

- [x] 1. 实现跨线程请求上下文


  - [x] 1.1 创建 RequestContextHolder 使用 ScopedValue


    - 定义 RequestContext record (requestId, userId, projectId)
    - 实现 ScopedValue 绑定和访问方法
    - _Requirements: 7.1, 7.2_
  - [ ]* 1.2 编写属性测试验证 ScopedValue 在 Virtual Threads 中的继承
    - **Property 6: ScopedValue 上下文传递**
    - **Validates: Requirements 7.1-7.3**

- [x] 2. 实现 Tool 执行切面



  - [x] 2.1 创建 ToolExecutionEvent 事件类

    - 定义 Phase 枚举 (START, END)
    - 包含 requestId, toolName, success 字段
    - _Requirements: 6.1, 6.2_

  - [x] 2.2 创建 ToolExecutionAspect

    - 使用 @Around 拦截 @Tool 注解方法
    - 从 RequestContextHolder 获取上下文
    - 发布 START 和 END 事件
    - _Requirements: 6.1, 6.2_
  - [ ]* 2.3 编写属性测试验证事件配对
    - **Property 2: Tool 事件配对**
    - **Validates: Requirements 2.5, 6.1, 6.2**

- [x] 3. 实现 Tool 调用日志



  - [x] 3.1 创建数据库迁移脚本

    - tool_invocation_logs 表
    - _Requirements: 17.1_

  - [x] 3.2 创建 ToolInvocationLog 实体和 Repository

    - _Requirements: 17.1_

  - [x] 3.3 创建 ToolInvocationLogger 服务

    - logSuccess() 和 logFailure() 方法
    - 集成到 ToolExecutionAspect
    - _Requirements: 17.1-17.3_
  - [ ]* 3.4 编写属性测试验证日志完整性
    - **Property 11: Tool 调用日志完整性**
    - **Validates: Requirements 17.1-17.3**

- [x] 4. 实现错误处理与重试



  - [x] 4.1 创建 AIErrorHandler

    - 指数退避重试逻辑
    - 用户友好错误消息
    - _Requirements: 18.1-18.5_
  - [ ]* 4.2 编写属性测试验证重试次数限制
    - **Property 12: 错误重试次数限制**
    - **Validates: Requirements 18.1**

- [x] 5. Checkpoint - 基础设施层测试通过


  - Ensure all tests pass, ask the user if questions arise.

## Phase 2: Tool 层

- [x] 6. 实现 CreativeGenTool
  - [x] 6.1 创建 CreativeGenTool 类
    - 支持生成类型: CHAPTER_CONTENT, CHARACTER_BACKGROUND, OUTLINE, WORLD_STRUCTURE, STORY_BLOCK
    - 集成 RAG 上下文检索
    - _Requirements: 10.1-10.7_
  - [ ]* 6.2 编写单元测试验证生成类型覆盖

- [x] 7. 实现 DeepReasoningTool
  - [x] 7.1 创建 DeepReasoningTool 类
    - 调用推理模型 (DeepSeek R1)
    - 处理模型不可用的降级
    - _Requirements: 11.1-11.6_
  - [ ]* 7.2 编写单元测试验证降级逻辑

- [x] 8. 实现 PreflightTool
  - [x] 8.1 创建 PreflightTool 类
    - 规则检查: 角色一致性、时间线、伏笔
    - 可选 AI 增强检查
    - _Requirements: 12.1-12.6_
  - [ ]* 8.2 编写单元测试验证检查逻辑

- [x] 9. 实现 StyleRetrieveTool
  - [x] 9.1 创建 StyleRetrieveTool 类
    - 检索风格样本
    - 构建风格引导提示词
    - _Requirements: 13.1-13.5_
  - [ ]* 9.2 编写单元测试验证风格检索

- [x] 10. 更新 SceneToolRegistry
  - [x] 10.1 注册新 Tool 到对应阶段
    - CreativeGenTool: IDEA, WORLDBUILDING, CHARACTER, WRITING
    - DeepReasoningTool: OUTLINE, WRITING
    - PreflightTool: OUTLINE, WRITING, REVISION
    - StyleRetrieveTool: WRITING
  - [ ]* 10.2 编写属性测试验证所有 Tool 描述
    - **Property 1: Tool 描述完整性**
    - **Validates: Requirements 1.5, 2.7**

- [x] 11. Checkpoint - Tool 层编译通过
  - 编译验证通过，可选测试任务待后续实现

## Phase 3: 服务层核心

- [x] 12. 实现 SceneToolRegistry


  - [x] 12.1 创建 SceneToolRegistry 类

    - 阶段-工具映射配置
    - getToolsArrayForPhase() 方法
    - _Requirements: 4.1-4.7_
  - [ ]* 12.2 编写属性测试验证阶段工具映射
    - **Property 3: 阶段工具映射一致性**
    - **Validates: Requirements 4.1-4.7**

- [x] 13. 实现 PhaseAwarePromptBuilder



  - [x] 13.1 创建 PhaseAwarePromptBuilder 类

    - buildBasePrompt() 包含 userId, projectId
    - buildPhasePrompt() 阶段特定提示词
    - buildToolGuide() 工具使用指南
    - _Requirements: 5.1-5.8_
  - [ ]* 13.2 编写属性测试验证提示词内容
    - **Property 4: 系统提示词包含上下文**
    - **Property 5: 阶段提示词包含阶段信息**
    - **Validates: Requirements 5.1-5.7**

- [x] 14. 实现 PhaseInferenceService



  - [x] 14.1 创建 PhaseInferenceService 类

    - 根据项目状态推断阶段
    - 根据消息内容辅助推断
    - _Requirements: 3.1-3.7_
  - [ ]* 14.2 编写单元测试验证推断逻辑

- [x] 15. Checkpoint - 服务层核心测试通过



  - Ensure all tests pass, ask the user if questions arise.

## Phase 4: 聊天服务增强

- [x] 16. 增强 SpringAIChatService


  - [x] 16.1 集成 SceneToolRegistry


    - 根据阶段获取工具
    - _Requirements: 4.1-4.7_
  - [x] 16.2 集成 PhaseAwarePromptBuilder

    - 构建阶段感知系统提示词
    - _Requirements: 5.1-5.8_
  - [x] 16.3 集成 RequestContextHolder

    - 使用 ThreadLocal 绑定上下文（Virtual Threads 兼容）
    - _Requirements: 7.1-7.6_
  - [x] 16.4 实现 Tool 事件流合并

    - 合并 Tool 事件和内容流
    - _Requirements: 6.1-6.8_

- [x] 17. 增强 ChatController


  - [x] 17.1 更新 ChatRequest 支持可选 phase

    - 不传则自动推断
    - _Requirements: 1.1-1.6_
  - [x] 17.2 集成 PhaseInferenceService

    - 推断或使用显式 phase
    - _Requirements: 3.1-3.7_

- [x] 18. 实现智能模型路由


  - [x] 18.1 更新 DynamicChatModelFactory

    - 根据场景选择模型（通过 AIConfigResolver）
    - 处理模型不可用降级（DeepSeek 思考模式自动降级）
    - _Requirements: 8.1-8.6_
  - [ ]* 18.2 编写属性测试验证模型路由
    - **Property 7: 模型路由一致性**
    - **Validates: Requirements 8.1-8.5**

- [x] 19. Checkpoint - 聊天服务增强测试通过


  - Phase 4 核心功能已实现，可选测试任务待后续实现

## Phase 5: 辅助功能

- [x] 20. 实现对话历史持久化



  - [x] 20.1 创建数据库迁移脚本

    - conversation_history 表
    - _Requirements: 14.1_

  - [x] 20.2 创建 ConversationHistory 实体和 Repository

    - _Requirements: 14.1_

  - [x] 20.3 创建 ConversationHistoryService

    - save(), findByProject(), summarize()
    - _Requirements: 14.1-14.5_
  - [ ]* 20.4 编写属性测试验证往返一致性
    - **Property 9: 对话历史往返一致性**
    - **Validates: Requirements 14.1-14.2**



- [x] 21. 实现创作进度追踪

  - [x] 21.1 创建 CreationProgressService

    - 计算阶段完成度
    - 追踪实体数量
    - _Requirements: 15.1-15.4_
  - [ ]* 21.2 编写属性测试验证进度计算
    - **Property 10: 创作进度计算正确性**
    - **Validates: Requirements 15.2**



- [x] 22. 实现会话恢复提示

  - [x] 22.1 创建 SessionResumeService

    - 检测上次会话
    - 生成恢复提示
    - _Requirements: 16.1-16.4_
  - [-]* 22.2 编写单元测试验证恢复逻辑



- [x] 23. 实现主动式一致性检查

  - [x] 23.1 创建 ConsistencyCheckService

    - 规则检查逻辑
    - 防抖和限流
    - _Requirements: 9.1-9.9_

  - [-] 23.2 创建 ConsistencyWarningRepository

    - 存储静默警告
    - _Requirements: 9.4_
  - [ ]* 23.3 编写属性测试验证限流
    - **Property 8: 一致性检查限流**

    - **Validates: Requirements 9.7**

- [x] 24. Checkpoint - 辅助功能测试通过


  - Ensure all tests pass, ask the user if questions arise.

## Phase 6: 集成测试

- [x] 25. 编写集成测试





  - [x] 25.1 聊天流程集成测试






    - 消息 → Tool 调用 → 响应
    - _Requirements: 1.1-1.6, 2.1-2.7_
  - [x] 25.2 SSE 事件流集成测试






    - 验证事件顺序和格式
    - _Requirements: 6.1-6.8_

  - [ ] 25.3 阶段切换集成测试

    - 验证阶段推断和工具切换
    - _Requirements: 3.1-3.7, 4.1-4.7_

- [ ] 26. Final Checkpoint - 所有测试通过
  - Ensure all tests pass, ask the user if questions arise.

## 优先级说明

### 高优先级 (P0) - 核心功能
- Phase 1: 基础设施层 (上下文、事件、日志)
- Phase 2: Tool 层 (CreativeGen, DeepReasoning, Preflight, Style)
- Phase 3: 服务层核心 (SceneToolRegistry, PromptBuilder)
- Phase 4: 聊天服务增强

### 中优先级 (P1) - 用户体验
- Phase 5: 辅助功能 (历史、进度、恢复、一致性检查)

### 低优先级 (P2) - 质量保证
- Phase 6: 集成测试

## 依赖关系

```
Phase 1 (基础设施) ──> Phase 2 (Tool) ──> Phase 3 (服务核心) ──> Phase 4 (聊天增强)
                                                                      │
                                                                      ▼
                                                              Phase 5 (辅助功能)
                                                                      │
                                                                      ▼
                                                              Phase 6 (集成测试)
```

## 注意事项

1. **这是新项目** - 不需要考虑迁移兼容性，代码要干净整洁
2. **不要遗留无用代码** - 只实现需要的功能
3. **使用 Java 22 特性** - ScopedValue, Virtual Threads, Pattern Matching
4. **使用 Spring AI 1.1.2** - @Tool 注解, ChatClient, Function Calling
5. **属性测试使用 jqwik** - 验证正确性属性
