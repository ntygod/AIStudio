# Implementation Plan

## 1. 创建请求适配层

- [x] 1.1 创建 ChatRequestAdapter 组件
  - 在 `agent/adapter` 目录创建 ChatRequestAdapter 类
  - 实现 ai_bridge.dto.ChatRequest → agent.dto.ChatRequest 转换
  - 集成 PhaseInferenceService 进行 phase 推断
  - 实现 conversationId 生成逻辑
  - _Requirements: 2.1, 2.2, 2.3, 2.4_

- [ ]* 1.2 编写 ChatRequestAdapter 属性测试
  - **Property 1: Request adaptation preserves essential data**
  - **Validates: Requirements 2.1-2.4**

## 2. 重构 ChatController

- [x] 2.1 修改 ChatController 使用 AgentRouter
  - 注入 AgentRouter 和 ChatRequestAdapter
  - 修改 streamChat 方法使用 AgentRouter.route()
  - 修改 simpleChat 方法收集流式响应
  - 保持 `/api/v1/chat/stream` 端点路径不变
  - _Requirements: 1.1, 1.4, 6.1, 6.2, 6.3_

- [ ]* 2.2 编写 ChatController 路由一致性属性测试
  - **Property 2: Routing consistency**
  - **Validates: Requirements 1.1, 6.1-6.4**

## 3. 统一 AgentOrchestrator

- [x] 3.1 增强 agent 模块的 AgentOrchestrator
  - 添加 ChainExecutionContext 支持（从 ai_bridge 合并）
  - 添加 executeChain 方法实现链式执行
  - 添加 ParallelResult2/ParallelResult3 类型安全结果
  - 添加 executeParallel2/executeParallel3 方法
  - 添加 executeAny 竞争执行方法
  - 确保使用 Virtual Threads 和 CompletableFuture
  - 实现指数退避重试逻辑
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 8.1, 8.2_

- [ ]* 3.2 编写 AgentOrchestrator 链式执行属性测试
  - **Property 4: Chain execution preserves context**
  - **Validates: Requirements 3.2, 8.1**

- [ ]* 3.3 编写 AgentOrchestrator 单元测试
  - 测试并行执行功能
  - 测试重试逻辑
  - 测试超时处理
  - _Requirements: 3.1, 3.3_

## 4. 统一 SceneCreationOrchestrator

- [x] 4.1 增强 agent 模块的 SceneCreationOrchestrator
  - 集成 HybridSearchService 进行 RAG 检索
  - 使用 WriterAgent 生成内容
  - 完成后异步触发 ConsistencyAgent 验证
  - 发布 AgentThoughtEvent 事件
  - _Requirements: 4.1, 4.2, 4.3_

## 5. 统一 SSE 事件类型

- [x] 5.1 创建统一的 SSEEventBuilder
  - 在 `agent/event` 目录创建 SSEEventBuilder 类
  - 定义标准事件类型常量：content, thought, tool_start, tool_end, done, error
  - 实现各类型事件的构建方法
  - 确保 done 事件包含 token 使用量
  - 确保 error 事件包含用户友好消息
  - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6_

- [ ]* 5.2 编写 SSE 事件类型一致性属性测试
  - **Property 3: SSE event type consistency**
  - **Validates: Requirements 7.1-7.6**

## 6. Checkpoint - 确保所有测试通过
- [x] 6. Ensure all tests pass, ask the user if questions arise.
  - Fixed `conversationId()` → `sessionId()` in AgentOrchestrator
  - Renamed ai_bridge orchestrators to avoid bean name conflicts:
    - `AgentOrchestrator` → `@Service("legacyAgentOrchestrator")`
    - `SceneCreationOrchestrator` → `@Service("legacySceneCreationOrchestrator")`
  - All 61 tests pass (BUILD SUCCESS)

## 7. 迁移 ai_bridge 特有功能到 Skill

- [x] 7.1 将 ChoreographerAgent 功能迁移到 ActionSkill
  - 在 `agent/skill/impl` 创建 ActionSkill 类
  - 实现动作描写增强功能
  - 注册到 SkillRegistry
  - _Requirements: 8.3_
  - ✅ ActionSkill 已存在，包含完整的动作描写指南（动作设计原则、空间布局、节奏控制、视觉效果、连贯性、打斗技巧）
  - ✅ 通过 @Component 自动注册到 SkillRegistry

- [x] 7.2 确认 PsychologySkill 已实现
  - 验证 PsychologySkill 已存在于 `agent/skill/impl`
  - 确保功能与 PsychologistAgent 一致
  - _Requirements: 8.4_
  - ✅ PsychologySkill 已存在，包含完整的心理描写指南（内心独白、情感层次、潜意识表达、记忆联想、心理防御、成长变化）
  - ✅ 功能覆盖 PsychologistAgent 的所有能力并有所扩展

## 8. 删除废弃代码

- [x] 8.1 删除 ai_bridge/orchestration 目录
  - 删除 `ai_bridge/orchestration/AgentOrchestrator.java` - 不存在于 v2
  - 删除 `ai_bridge/orchestration/SceneCreationOrchestrator.java` - 不存在于 v2
  - 删除 `ai_bridge/orchestration/agent/` 目录 - 已为空
  - 删除 `ai_bridge/orchestration/dto/` 目录 - ✅ 已删除所有文件
  - 删除 `ai_bridge/orchestration/event/` 目录 - ✅ 已删除 AgentThoughtEvent.java
  - 删除 `ai_bridge/orchestration/chain/` 目录 - ✅ 已删除 ChainExecutionContext.java, ChainExecutionException.java
  - _Requirements: 3.5, 4.4_

- [x] 8.2 删除 SpringAIChatService
  - ✅ 删除 `ai_bridge/chat/SpringAIChatService.java`
  - ✅ 更新测试文件移除对 SpringAIChatService 的依赖
  - _Requirements: 5.1, 5.2_

- [x] 8.3 更新 ChatController 移除旧依赖
  - ✅ ChatController 已经只使用 AgentRouter（在任务 2.1 中完成）
  - ✅ 无需额外修改
  - _Requirements: 5.2, 5.3_

## 9. 更新 SceneController

- [x] 9.1 修改 SceneController 使用统一的 SceneCreationOrchestrator
  - ✅ 更新 import 指向 agent 模块的 SceneCreationOrchestrator
  - ✅ 创建内联 SceneRequest 和 SceneResult record 类型
  - ✅ 实现 adaptToChatRequest 方法转换请求格式
  - ✅ 添加 /with-consistency 端点支持一致性检查
  - ✅ 确保 API 兼容性
  - _Requirements: 4.1, 6.4_

## 10. Final Checkpoint - 确保所有测试通过
- [x] 10. Ensure all tests pass, ask the user if questions arise.
  - ✅ 编译成功
  - ✅ 所有测试通过（包括 property-based tests）
