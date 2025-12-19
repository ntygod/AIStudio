# Requirements Document

## Introduction

本需求文档针对 `ai_bridge/chat` 模块中的线程安全问题和架构优化进行规范。当前实现存在严重的并发安全隐患，在高并发场景下可能导致用户数据混淆。本次优化将重构 Tool 执行上下文传递机制、事件流架构，并恢复 DeepSeek R1 推理模型的正确使用。

## Glossary

- **ToolExecutionAspect**: AOP 切面，拦截所有 `@Tool` 注解的方法，发布执行事件
- **globalToolContext**: 当前使用的全局静态变量，存储 userId 和 projectId，存在线程安全问题
- **ThreadLocal**: Java 线程本地存储，在 WebFlux 响应式环境中可能失效
- **Reactor Context**: Project Reactor 提供的上下文传递机制，支持响应式流中的跨线程上下文传递
- **SSE (Server-Sent Events)**: 服务器推送事件，用于实时推送 Tool 状态和聊天内容
- **DeepSeek R1**: DeepSeek 推理模型，用于深度思考场景
- **DeepSeek V3 (deepseek-chat)**: DeepSeek 对话模型，支持 Tool Calling
- **Spring Event**: Spring 框架的事件发布/订阅机制

## Requirements

### Requirement 1: 消除全局状态的线程安全问题

**User Story:** As a system administrator, I want the Tool execution context to be thread-safe, so that concurrent user requests do not interfere with each other's data.

#### Acceptance Criteria

1. WHEN multiple users send chat requests concurrently THEN the system SHALL ensure each Tool execution receives the correct userId and projectId for its originating request
2. WHEN a Tool method is invoked THEN the system SHALL extract userId and projectId from the method parameters instead of global state
3. IF a Tool method does not have userId/projectId parameters THEN the system SHALL log a warning and continue without context injection
4. WHEN the ToolExecutionAspect intercepts a Tool call THEN the system SHALL NOT use AtomicReference or static global variables for context storage

### Requirement 2: 重构事件流架构

**User Story:** As a developer, I want the Tool status event flow to be decoupled from ThreadLocal, so that events are reliably delivered in the WebFlux reactive environment.

#### Acceptance Criteria

1. WHEN a Tool execution starts or ends THEN the system SHALL publish events through Spring ApplicationEventPublisher only
2. WHEN the SSE stream needs Tool status events THEN the system SHALL filter events by userId and projectId to match the current session
3. WHEN the chat stream is cancelled THEN the system SHALL properly clean up event subscriptions without resource leaks
4. WHEN Tool events are published THEN the system SHALL include userId, projectId, and a unique requestId for correlation

### Requirement 3: 恢复 DeepSeek R1 推理模型支持

**User Story:** As a user, I want to use DeepSeek R1 for deep reasoning tasks, so that I can get more thorough analysis for complex creative decisions.

#### Acceptance Criteria

1. WHEN the DeepThinkingTool requests a reasoning model THEN the system SHALL return DeepSeek R1 without automatic downgrade
2. WHEN a user configures R1 as the main chat model THEN the system SHALL downgrade to deepseek-chat only for Tool Calling scenarios
3. WHEN the system creates a reasoning model THEN the system SHALL cache it separately from chat models using a distinct cache key
4. WHEN R1 is used for reasoning THEN the system SHALL NOT attempt Tool Calling with that model instance

### Requirement 4: 优化 ToolInvocationLogger 上下文传递

**User Story:** As an operations engineer, I want Tool invocation logs to correctly record the userId and projectId, so that I can trace and debug issues accurately.

#### Acceptance Criteria

1. WHEN a Tool is invoked THEN the ToolInvocationLogger SHALL receive context from method parameters, not global state
2. WHEN logging Tool invocations THEN the system SHALL include the correct userId and projectId in every log entry
3. IF context cannot be determined from parameters THEN the system SHALL log with null context and a warning message

### Requirement 5: 移除冗余的监听器机制

**User Story:** As a developer, I want a simplified event architecture, so that the codebase is easier to maintain and less prone to threading bugs.

#### Acceptance Criteria

1. WHEN the ToolExecutionAspect is refactored THEN the system SHALL remove the ThreadLocal-based ToolExecutionListener mechanism
2. WHEN Tool status needs to be communicated THEN the system SHALL use Spring Events exclusively
3. WHEN the SSE stream is created THEN the system SHALL subscribe to Spring Events filtered by request correlation ID

### Requirement 6: 清理冗余代码

**User Story:** As a developer, I want all unused and redundant code removed, so that the codebase is clean and maintainable.

#### Acceptance Criteria

1. WHEN refactoring is complete THEN the system SHALL have removed all unused static variables (globalToolContext, userContextMap)
2. WHEN refactoring is complete THEN the system SHALL have removed the ToolExecutionListener interface and related ThreadLocal
3. WHEN refactoring is complete THEN the system SHALL have removed any compatibility shims or fallback code paths that are no longer needed
4. WHEN refactoring is complete THEN the system SHALL have consolidated duplicate event handling logic

