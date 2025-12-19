# Design Document: Chat API Simplification

## Overview

重构 ChatController，将当前 6 个端点简化为 2 个核心聊天端点，会话管理功能移至 SessionController。RAG 上下文检索作为默认行为集成到聊天流程中。

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Frontend                                │
└─────────────────────────┬───────────────────────────────────┘
                          │
          ┌───────────────┴───────────────┐
          ▼                               ▼
┌─────────────────────┐       ┌─────────────────────┐
│   ChatController    │       │  SessionController  │
│  /api/v1/chat       │       │  /api/v1/sessions   │
├─────────────────────┤       ├─────────────────────┤
│ POST /stream        │       │ GET /               │
│ POST /simple        │       │ GET /{sessionId}    │
└─────────┬───────────┘       │ DELETE /{sessionId} │
          │                   │ DELETE /others      │
          │                   │ GET /resume/{pid}   │
          │                   │ DELETE /conv/{cid}  │
          ▼                   └─────────────────────┘
┌─────────────────────┐
│ SpringAIChatService │
├─────────────────────┤
│ - chatWithContext() │  ◄── 默认启用 RAG
│ - chatWithStatus()  │
└─────────┬───────────┘
          │
    ┌─────┴─────┐
    ▼           ▼
┌────────┐  ┌────────────────┐
│ RAG    │  │ ChatMemory     │
│ Search │  │ (Conversation) │
└────────┘  └────────────────┘
```

## Components and Interfaces

### 1. ChatController (Simplified)

```java
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {
    
    // 聊天请求 DTO
    public record ChatRequest(
        @NotNull UUID projectId,
        @NotBlank String message,
        String phase,           // 可选，不传则自动推断
        String conversationId   // 可选，不传则自动生成
    ) {}
    
    // POST /stream - 流式聊天（SSE）
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<String>> streamChat(User user, ChatRequest request);
    
    // POST /simple - 非流式聊天
    @PostMapping("/simple")
    ChatResponse simpleChat(User user, ChatRequest request);
}
```

### 2. SessionController (Extended)

```java
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {
    
    // 现有端点保持不变...
    
    // 新增：获取会话恢复提示
    @GetMapping("/resume/{projectId}")
    SessionResumeResponse getResumePrompt(User user, UUID projectId);
    
    // 新增：清除对话历史
    @DeleteMapping("/conversations/{conversationId}")
    void clearConversation(String conversationId);
}
```

### 3. SpringAIChatService (Unified)

合并 `chat()` 和 `chatWithContext()` 为统一方法，RAG 默认启用：

```java
public Flux<String> chat(UUID userId, UUID projectId, String conversationId,
                         String message, CreationPhase phase) {
    // 1. RAG 检索相关上下文（默认启用）
    // 2. 构建增强的系统提示词
    // 3. 执行聊天
}
```

## Data Models

### ChatRequest

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| projectId | UUID | Yes | 项目ID |
| message | String | Yes | 用户消息 |
| phase | String | No | 创作阶段，不传则自动推断 |
| conversationId | String | No | 会话ID，不传则自动生成 |

### ChatResponse (非流式)

| Field | Type | Description |
|-------|------|-------------|
| content | String | AI 响应内容 |
| conversationId | String | 会话ID |
| phase | String | 使用的创作阶段 |

### SSE Event Types

| Event | Data | Description |
|-------|------|-------------|
| content | String | 消息内容片段 |
| tool | ToolStatusEvent | Tool 执行状态 |
| done | "" | 流结束标记 |
| error | ErrorResponse | 错误信息 |

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. 
Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

Property 1: RAG 上下文默认启用
*For any* chat request, the system should invoke RAG context retrieval before generating response
**Validates: Requirements 1.2**

Property 2: Phase 自动推断
*For any* chat request without explicit phase parameter, the system should call PhaseInferenceService to determine the phase
**Validates: Requirements 3.2**

Property 3: ConversationId 自动生成
*For any* chat request without explicit conversationId, the system should generate a deterministic conversationId based on userId and projectId
**Validates: Requirements 3.3**

Property 4: SSE 事件类型一致性
*For any* streaming response, all SSE events should use one of the defined event types: content, tool, done, error
**Validates: Requirements 3.4**

Property 5: 错误响应格式一致性
*For any* error during chat processing, the response should contain standardized error structure with code and message
**Validates: Requirements 4.1**

Property 6: 验证错误返回 400
*For any* request with invalid fields (null projectId, blank message), the system should return HTTP 400 with field-level error details
**Validates: Requirements 4.3**

## Error Handling

| Error Type | HTTP Status | Error Code | Description |
|------------|-------------|------------|-------------|
| Validation Error | 400 | VALIDATION_ERROR | 请求参数验证失败 |
| Authentication Error | 401 | UNAUTHORIZED | 未认证或 token 无效 |
| AI Service Unavailable | 503 | AI_SERVICE_UNAVAILABLE | AI 服务不可用 |
| Internal Error | 500 | INTERNAL_ERROR | 内部错误 |

## Testing Strategy

### Unit Tests
- ChatController 端点路由测试
- ChatRequest 验证测试
- Phase 推断逻辑测试
- ConversationId 生成逻辑测试

### Property-Based Tests (jqwik)
- Property 2: Phase 自动推断 - 验证无 phase 参数时调用推断服务
- Property 3: ConversationId 生成 - 验证生成的 ID 是确定性的
- Property 6: 验证错误处理 - 验证各种无效输入返回正确的错误响应

### Integration Tests
- 完整聊天流程测试（含 RAG）
- SSE 流式响应测试
- 错误场景测试
