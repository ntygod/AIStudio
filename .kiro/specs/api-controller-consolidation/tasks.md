# Implementation Plan

## 1. 增强 AgentController

- [x] 1.1 添加 ChatRequestDto 和 ChatResponseDto
  - 在 AgentController 中定义 ChatRequestDto record（projectId, message, phase, sessionId）
  - 定义 ChatResponseDto record（content, sessionId, phase）
  - 添加 @NotNull 和 @NotBlank 验证注解
  - _Requirements: 1.1, 1.2, 3.1, 3.2_

- [x] 1.2 添加 SceneRequestDto
  - 在 AgentController 中定义 SceneRequestDto record
  - 包含 projectId, prompt, chapterId, characterIds, sceneType, additionalContext, targetWordCount
  - 添加验证注解
  - _Requirements: 1.3, 3.4_

- [x] 1.3 实现请求适配逻辑
  - 添加 adaptChatRequest 方法（phase 推断、sessionId 生成）
  - 添加 adaptSceneRequest 方法（构建增强 prompt、metadata）
  - 添加 generateSessionId 方法
  - 注入 PhaseInferenceService
  - _Requirements: 1.5, 3.1, 3.2, 3.4_

- [ ]* 1.4 编写请求适配属性测试
  - **Property 1: Request adaptation preserves essential data and infers missing fields**
  - **Validates: Requirements 1.5, 3.1, 3.2**

- [ ]* 1.5 编写场景元数据属性测试
  - **Property 2: Scene request adaptation includes all metadata**
  - **Validates: Requirements 3.4**

## 2. 添加新端点

- [x] 2.1 添加 /chat/simple 非流式聊天端点
  - 实现 chatSimple 方法
  - 收集流式响应并返回完整内容
  - 返回 ChatResponseDto
  - _Requirements: 1.2_

- [x] 2.2 修改 /scene/create 端点支持 consistency 参数
  - 添加 @RequestParam(defaultValue = "true") boolean consistency 参数
  - 根据参数调用 createScene 或 createSceneWithConsistencyCheck
  - _Requirements: 1.3, 1.4_

- [ ]* 2.3 编写验证错误属性测试
  - **Property 3: Validation errors return HTTP 400**
  - **Validates: Requirements 4.1**

## 3. 删除冗余代码

- [x] 3.1 删除 ChatController
  - 删除 `ai_bridge/controller/ChatController.java`
  - _Requirements: 2.1_

- [x] 3.2 删除 SceneController
  - 删除 `ai_bridge/controller/SceneController.java`
  - _Requirements: 2.2_

- [x] 3.3 删除 ChatRequestAdapter
  - 删除 `agent/adapter/ChatRequestAdapter.java`
  - _Requirements: 2.3_

## 4. 更新测试

- [x] 4.1 更新或删除引用已删除控制器的测试
  - 检查并更新 ChatFlowIntegrationTest
  - 检查并更新 SSEEventStreamIntegrationTest
  - 删除不再需要的测试
  - _Requirements: 2.4_

## 5. Checkpoint - 确保所有测试通过
- [x] 5. Ensure all tests pass, ask the user if questions arise.
