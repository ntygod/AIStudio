# Requirements Document

## Introduction

本文档定义 ChatController API 简化重构的需求。当前 v2 版本的 ChatController 存在接口冗余、职责不清的问题，需要重构为简洁、清晰的 API 设计。

## Glossary

- **ChatController**: AI 聊天控制器，提供用户与 AI 助手交互的 HTTP 端点
- **SSE (Server-Sent Events)**: 服务器推送事件，用于流式响应
- **RAG (Retrieval-Augmented Generation)**: 检索增强生成，自动检索相关设定作为上下文
- **Phase**: 创作阶段，如 IDEA、WORLDBUILDING、CHARACTER 等
- **SessionController**: 会话管理控制器，负责会话恢复、清除等功能

## 当前问题分析

v2 ChatController 当前有以下端点：
1. `POST /stream` - 流式聊天
2. `POST /simple` - 非流式聊天
3. `POST /stream/context` - 带 RAG 上下文的流式聊天（与 /stream 功能重叠）
4. `DELETE /conversations/{conversationId}` - 清除对话历史
5. `GET /resume/{projectId}` - 获取会话恢复提示
6. `GET /test` - 测试接口

问题：
- `/stream` 和 `/stream/context` 功能重叠，RAG 上下文应该是默认行为
- 会话管理功能（清除、恢复）不应该放在 ChatController
- 测试接口不应该暴露在生产 API 中

## Requirements

### Requirement 1: 简化聊天 API 入口

**User Story:** As a 前端开发者, I want 只有两个核心聊天接口, so that API 调用更简单、文档更清晰。

#### Acceptance Criteria

1. THE ChatController SHALL provide exactly two chat endpoints: `/stream` for SSE streaming and `/simple` for non-streaming responses
2. WHEN a user sends a chat request THEN the ChatController SHALL automatically include RAG context retrieval without requiring a separate endpoint
3. THE ChatController SHALL remove the `/stream/context` endpoint completely
4. THE ChatController SHALL remove the `/test` endpoint from production API

### Requirement 2: 分离会话管理职责

**User Story:** As a 系统架构师, I want 会话管理功能独立到 SessionController, so that 职责分离更清晰、代码更易维护。

#### Acceptance Criteria

1. WHEN a user needs to clear conversation history THEN the SessionController SHALL handle the request at `DELETE /api/v1/sessions/{conversationId}`
2. WHEN a user needs session resume prompt THEN the SessionController SHALL handle the request at `GET /api/v1/sessions/resume/{projectId}`
3. THE ChatController SHALL only contain chat-related endpoints, with no conversation management functionality

### Requirement 3: 统一请求/响应格式

**User Story:** As a 前端开发者, I want 统一的请求和响应格式, so that 客户端代码更简洁。

#### Acceptance Criteria

1. THE ChatRequest DTO SHALL include: projectId (required), message (required), phase (optional), conversationId (optional)
2. WHEN phase is not provided THEN the ChatController SHALL automatically infer the phase based on project state and message content
3. WHEN conversationId is not provided THEN the ChatController SHALL generate one based on userId and projectId
4. THE streaming response SHALL use consistent SSE event types: `content` for message chunks, `tool` for tool execution status, `done` for completion, `error` for errors

### Requirement 4: 错误处理标准化

**User Story:** As a 前端开发者, I want 标准化的错误响应格式, so that 错误处理逻辑更统一。

#### Acceptance Criteria

1. WHEN an error occurs during chat THEN the ChatController SHALL return a standardized error response with code, message, and optional details
2. WHEN AI service is unavailable THEN the ChatController SHALL return HTTP 503 with appropriate error message
3. WHEN request validation fails THEN the ChatController SHALL return HTTP 400 with field-level error details
4. WHEN authentication fails THEN the ChatController SHALL return HTTP 401 with clear error message
