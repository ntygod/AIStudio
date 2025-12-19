# Requirements Document

## Introduction

统一 AI 相关 API 控制器，删除冗余的 `ChatController` 和 `SceneController`，将所有 AI 交互功能整合到 `AgentController` 中。这是一个新项目，无需考虑向后兼容性，直接删除不需要的代码。

## Glossary

- **AgentController**: 统一的 Agent API 控制器，位于 `/api/v2/agent`
- **ChatController**: 旧的聊天控制器，位于 `/api/v1/chat`（待删除）
- **SceneController**: 旧的场景创作控制器，位于 `/api/v1/scenes`（待删除）
- **AgentRouter**: Agent 路由器，负责将请求分发到合适的 Agent
- **ChatRequestAdapter**: 请求适配器，负责转换请求格式和推断参数
- **SSE**: Server-Sent Events，服务端推送事件

## Requirements

### Requirement 1

**User Story:** As a frontend developer, I want a single unified API endpoint for all AI interactions, so that I don't need to call different endpoints for chat and scene creation.

#### Acceptance Criteria

1. THE AgentController SHALL provide a `/chat` endpoint for streaming AI chat responses
2. THE AgentController SHALL provide a `/chat/simple` endpoint for non-streaming AI chat responses
3. THE AgentController SHALL provide a `/scene/create` endpoint with optional `consistency` query parameter (default: true)
4. WHEN consistency=false THEN the AgentController SHALL skip consistency check for faster scene creation
5. WHEN a chat request is received THEN the AgentController SHALL adapt the request format with phase inference and sessionId generation

### Requirement 2

**User Story:** As a developer, I want to remove redundant controller code, so that the codebase is cleaner and easier to maintain.

#### Acceptance Criteria

1. THE system SHALL delete ChatController.java from ai_bridge/controller
2. THE system SHALL delete SceneController.java from ai_bridge/controller
3. THE system SHALL delete ChatRequestAdapter from agent/adapter after integrating its logic into AgentController
4. WHEN controllers are deleted THEN the system SHALL update any tests that reference deleted controllers

### Requirement 3

**User Story:** As a user, I want the unified API to support all features from the old endpoints, so that I don't lose any functionality.

#### Acceptance Criteria

1. THE AgentController SHALL support optional phase parameter with automatic inference when not provided
2. THE AgentController SHALL support optional sessionId parameter with automatic generation when not provided
3. THE AgentController SHALL support user authentication via @AuthenticationPrincipal
4. THE AgentController SHALL support scene-specific parameters including chapterId, characterIds, sceneType, additionalContext, and targetWordCount

### Requirement 4

**User Story:** As a developer, I want consistent error handling across all AI endpoints, so that the frontend can handle errors uniformly.

#### Acceptance Criteria

1. WHEN validation fails THEN the AgentController SHALL return HTTP 400 with field-level error details
2. WHEN AI service is unavailable THEN the AgentController SHALL return HTTP 503 with appropriate error message
3. WHEN an internal error occurs THEN the AgentController SHALL return HTTP 500 with error details
4. THE AgentController SHALL use SSEEventBuilder for consistent SSE event formatting

### Requirement 5

**User Story:** As a developer, I want the API path to be clean and intuitive, so that it's easy to understand and use.

#### Acceptance Criteria

1. THE AgentController SHALL use `/api/v2/agent` as the base path
2. THE chat endpoints SHALL be at `/api/v2/agent/chat` and `/api/v2/agent/chat/simple`
3. THE scene endpoint SHALL be at `/api/v2/agent/scene/create` with optional `?consistency=true/false` parameter
4. THE capability endpoints SHALL remain at `/api/v2/agent/capabilities`
