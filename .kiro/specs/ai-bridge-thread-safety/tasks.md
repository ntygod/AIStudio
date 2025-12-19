# Implementation Plan

## 1. 重构 ToolExecutionEvent 增加关联字段

- [x] 1.1 增强 ToolExecutionEvent 类
  - 添加 userId, projectId, requestId 字段
  - 添加 timestamp 字段
  - 更新构造函数和工厂方法
  - _Requirements: 2.4_

- [ ]* 1.2 Write property test for event structure completeness
  - **Property 4: Event Structure Completeness**
  - **Validates: Requirements 2.4**

## 2. 重构 ToolExecutionAspect 移除全局状态

- [x] 2.1 移除全局状态变量
  - 删除 globalToolContext (AtomicReference)
  - 删除 userContextMap (ConcurrentHashMap)
  - 删除 currentListener (ThreadLocal)
  - 删除 ToolExecutionListener 接口
  - _Requirements: 1.4, 5.1, 6.1, 6.2_

- [x] 2.2 实现参数提取逻辑
  - 添加 extractContextFromArgs 方法
  - 遍历方法参数查找 userId 和 projectId
  - 支持 UUID 类型和 String 类型参数
  - _Requirements: 1.2, 4.1_

- [x] 2.3 更新 aroundToolExecution 方法
  - 使用参数提取替代全局状态读取
  - 发布增强的 ToolExecutionEvent
  - 设置 ToolInvocationLogger 上下文
  - _Requirements: 1.2, 2.1, 4.2_

- [ ]* 2.4 Write property test for parameter-based context extraction
  - **Property 2: Parameter-Based Context Extraction**
  - **Validates: Requirements 1.2, 4.1, 4.2**

## 3. Checkpoint - 确保所有测试通过
- [x] 3. Ensure all tests pass, ask the user if questions arise.

## 4. 重构 SpringAIChatService 事件流架构

- [x] 4.1 添加 requestId 参数支持
  - 修改 chatWithStatus 方法签名添加 requestId
  - 在 ChatController 中生成 requestId
  - _Requirements: 2.4_

- [x] 4.2 实现基于 requestId 的事件过滤
  - 创建 ToolEventSubscriber 组件订阅 Spring Events
  - 实现按 requestId 过滤的 Flux 创建方法
  - 移除 ThreadLocal 监听器注册逻辑
  - _Requirements: 2.2, 5.2, 5.3_

- [x] 4.3 清理冗余代码
  - 移除 ToolExecutionAspect.setCurrentListener 调用
  - 移除 ToolExecutionAspect.setToolContext 调用
  - 简化 chatWithToolObserver 方法
  - _Requirements: 6.3, 6.4_

- [ ]* 4.4 Write property test for event filtering by requestId
  - **Property 3: Event Filtering by RequestId**
  - **Validates: Requirements 2.2, 5.3**

## 5. 更新 ChatController

- [x] 5.1 生成 requestId 并传递
  - 在 chat 端点生成 UUID 作为 requestId
  - 传递给 SpringAIChatService
  - _Requirements: 2.4_

## 6. Checkpoint - 确保所有测试通过
- [x] 6. Ensure all tests pass, ask the user if questions arise.

## 7. 修复 DeepSeek R1 模型支持

- [x] 7.1 验证 getReasoningModel 方法
  - 确认 DeepReasoningTool 调用 getReasoningModel 而非 getChatModel
  - 确认 getReasoningModel 不执行降级逻辑
  - _Requirements: 3.1, 3.4_

- [x] 7.2 优化模型缓存 Key
  - 确认推理模型使用独立的缓存 Key (userId:reasoning)
  - 确认与普通聊天模型缓存隔离
  - _Requirements: 3.3_

- [ ]* 7.3 Write property test for reasoning model non-downgrade
  - **Property 5: Reasoning Model Non-Downgrade**
  - **Validates: Requirements 3.1, 3.3**

- [ ]* 7.4 Write property test for chat model conditional downgrade
  - **Property 6: Chat Model Conditional Downgrade**
  - **Validates: Requirements 3.2**

## 8. 更新 ToolInvocationLogger

- [x] 8.1 简化上下文设置
  - 确认只从 Aspect 接收上下文
  - 移除任何全局状态依赖
  - _Requirements: 4.1, 4.2_

## 9. Final Checkpoint - 确保所有测试通过
- [x] 9. Ensure all tests pass, ask the user if questions arise.

