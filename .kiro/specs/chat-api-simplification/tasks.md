# Implementation Plan

- [x] 1. 重构 SpringAIChatService，合并 RAG 为默认行为
  - [x] 1.1 合并 `chat()` 和 `chatWithContext()` 方法，RAG 检索作为默认步骤
    - 修改 `chat()` 方法，在生成响应前自动调用 HybridSearchService
    - `chatWithContext()` 标记为 @Deprecated，内部调用 `chat()`
    - _Requirements: 1.2_
  - [ ]* 1.2 编写属性测试验证 RAG 默认启用
    - **Property 1: RAG 上下文默认启用**
    - **Validates: Requirements 1.2**

- [x] 2. 简化 ChatController
  - [x] 2.1 重构 ChatController，只保留 `/stream` 和 `/simple` 两个端点
    - 移除 `/stream/context` 端点
    - 移除 `/test` 端点
    - 移除 `/conversations/{conversationId}` 端点
    - 移除 `/resume/{projectId}` 端点
    - _Requirements: 1.1, 1.3, 1.4_
  - [x] 2.2 更新 ChatRequest DTO，添加 @NotNull 和 @NotBlank 验证注解
    - projectId: @NotNull
    - message: @NotBlank
    - phase: 可选
    - conversationId: 可选
    - _Requirements: 3.1_
  - [x] 2.3 实现 phase 自动推断逻辑
    - 当 phase 为空时调用 PhaseInferenceService（已有实现）
    - _Requirements: 3.2_
  - [ ]* 2.4 编写属性测试验证 phase 自动推断
    - **Property 2: Phase 自动推断**
    - **Validates: Requirements 3.2**
  - [x] 2.5 实现 conversationId 自动生成逻辑
    - 当 conversationId 为空时基于 userId 和 projectId 生成
    - 新增 `resolveConversationId()` 方法
    - _Requirements: 3.3_
  - [ ]* 2.6 编写属性测试验证 conversationId 生成
    - **Property 3: ConversationId 自动生成**
    - **Validates: Requirements 3.3**

- [x] 3. 扩展 SessionController
  - [x] 3.1 添加会话恢复端点 `GET /resume/{projectId}`
    - 从 ChatController 迁移 getResumePrompt 方法
    - 注入 SessionResumeService
    - _Requirements: 2.2_
  - [x] 3.2 添加清除对话端点 `DELETE /conversations/{conversationId}`
    - 从 ChatController 迁移 clearConversation 方法
    - 调用 ChatMemoryFactory.clearMemory()
    - _Requirements: 2.1_

- [x] 4. 统一错误处理
  - [x] 4.1 添加 ChatController 的 @Valid 验证和异常处理
    - 验证失败返回 400 + 字段级错误（通过 GlobalExceptionHandler）
    - AI 服务不可用返回 503（通过 AIErrorHandler）
    - _Requirements: 4.1, 4.2, 4.3, 4.4_
  - [ ]* 4.2 编写属性测试验证错误响应格式
    - **Property 5: 错误响应格式一致性**
    - **Property 6: 验证错误返回 400**
    - **Validates: Requirements 4.1, 4.3**

- [x] 5. Checkpoint - 确保所有测试通过
  - 所有核心任务已完成，可选的属性测试任务标记为 `*`
