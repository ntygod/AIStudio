# Design Document: API Controller Consolidation

## Overview

将 `ChatController` 和 `SceneController` 的功能整合到 `AgentController` 中，删除冗余代码，提供统一的 AI 交互 API。

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Frontend                                │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                   AgentController                            │
│                   /api/v2/agent                              │
├─────────────────────────────────────────────────────────────┤
│ POST /chat              - 流式聊天 (SSE)                     │
│ POST /chat/simple       - 非流式聊天                         │
│ POST /scene/create      - 场景创作 (?consistency=true/false) │
│ GET  /capabilities      - 获取 Agent 能力                    │
│ GET  /capabilities/phase/{phase}                             │
│ GET  /lazy-agents       - 获取懒执行 Agent                   │
│ POST /lazy-agents/{name}/execute                             │
│ GET  /tools             - 获取工具列表                       │
│ GET  /tools/phase/{phase}                                    │
│ GET  /tools/stats       - 获取工具统计                       │
└─────────────────────────┬───────────────────────────────────┘
                          │
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
┌─────────────────┐ ┌─────────────┐ ┌─────────────────────┐
│  AgentRouter    │ │ Orchestrator│ │ PhaseInferenceService│
└─────────────────┘ └─────────────┘ └─────────────────────┘
```

## Components and Interfaces

### 1. AgentController (Enhanced)

```java
@RestController
@RequestMapping("/api/v2/agent")
public class AgentController {
    
    // ========== Chat Endpoints ==========
    
    /**
     * 流式聊天请求 DTO
     */
    public record ChatRequestDto(
        @NotNull UUID projectId,
        @NotBlank String message,
        String phase,           // 可选，不传则自动推断
        String sessionId        // 可选，不传则自动生成
    ) {}
    
    /**
     * 非流式聊天响应 DTO
     */
    public record ChatResponseDto(
        String content,
        String sessionId,
        String phase
    ) {}
    
    // POST /chat - 流式聊天（SSE）
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<String>> chat(User user, ChatRequestDto request);
    
    // POST /chat/simple - 非流式聊天
    @PostMapping("/chat/simple")
    ChatResponseDto chatSimple(User user, ChatRequestDto request);
    
    // ========== Scene Endpoints ==========
    
    /**
     * 场景创作请求 DTO
     */
    public record SceneRequestDto(
        @NotNull UUID projectId,
        @NotBlank String prompt,
        UUID chapterId,
        List<UUID> characterIds,
        String sceneType,
        String additionalContext,
        Integer targetWordCount
    ) {}
    
    /**
     * 场景创作响应 DTO
     */
    public record SceneResponseDto(
        String content,
        Integer wordCount,
        boolean success,
        String errorMessage
    ) {}
    
    // POST /scene/create - 场景创作（通过 consistency 参数控制是否进行一致性检查）
    @PostMapping(value = "/scene/create", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<String>> createScene(
        User user, 
        SceneRequestDto request,
        @RequestParam(defaultValue = "true") boolean consistency
    );
}
```

### 2. Request Adaptation Logic (Inline)

将 `ChatRequestAdapter` 的逻辑内联到 `AgentController` 中：

```java
private ChatRequest adaptChatRequest(ChatRequestDto dto, UUID userId) {
    // Phase 推断
    CreationPhase phase = dto.phase() != null 
        ? CreationPhase.valueOf(dto.phase())
        : phaseInferenceService.inferPhase(dto.projectId());
    
    // SessionId 生成
    String sessionId = dto.sessionId() != null 
        ? dto.sessionId()
        : generateSessionId(userId, dto.projectId());
    
    return new ChatRequest(
        dto.message(),
        dto.projectId(),
        sessionId,
        phase,
        null,  // intentHint
        Map.of()
    );
}

private ChatRequest adaptSceneRequest(SceneRequestDto dto, UUID userId) {
    // 构建增强的 prompt
    StringBuilder promptBuilder = new StringBuilder(dto.prompt());
    if (dto.sceneType() != null) {
        promptBuilder.insert(0, "【场景类型: " + dto.sceneType() + "】\n");
    }
    if (dto.additionalContext() != null && !dto.additionalContext().isBlank()) {
        promptBuilder.append("\n\n【额外上下文】\n").append(dto.additionalContext());
    }
    if (dto.targetWordCount() != null && dto.targetWordCount() > 0) {
        promptBuilder.append("\n\n【目标字数: ").append(dto.targetWordCount()).append("字】");
    }
    
    // 构建 metadata
    Map<String, Object> metadata = new HashMap<>();
    if (dto.chapterId() != null) {
        metadata.put("chapterId", dto.chapterId().toString());
    }
    if (dto.characterIds() != null && !dto.characterIds().isEmpty()) {
        metadata.put("characterIds", dto.characterIds().stream()
            .map(UUID::toString).toList());
    }
    if (dto.sceneType() != null) {
        metadata.put("sceneType", dto.sceneType());
    }
    
    return new ChatRequest(
        promptBuilder.toString(),
        dto.projectId(),
        "scene_" + UUID.randomUUID().toString().substring(0, 8),
        CreationPhase.WRITING,
        Intent.WRITE_CONTENT,
        metadata
    );
}

private String generateSessionId(UUID userId, UUID projectId) {
    return "session_" + userId.toString().substring(0, 8) + "_" + projectId.toString().substring(0, 8);
}
```

## Data Models

### ChatRequestDto

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| projectId | UUID | Yes | 项目ID |
| message | String | Yes | 用户消息 |
| phase | String | No | 创作阶段，不传则自动推断 |
| sessionId | String | No | 会话ID，不传则自动生成 |

### SceneRequestDto

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| projectId | UUID | Yes | 项目ID |
| prompt | String | Yes | 场景描述/用户指令 |
| chapterId | UUID | No | 章节ID |
| characterIds | List<UUID> | No | 参与角色ID列表 |
| sceneType | String | No | 场景类型 |
| additionalContext | String | No | 额外上下文 |
| targetWordCount | Integer | No | 目标字数 |

### SSE Event Types

| Event | Data | Description |
|-------|------|-------------|
| content | String | 消息内容片段 |
| thought | String | Agent 思考过程 |
| tool_start | String | 工具开始执行 |
| tool_end | String | 工具执行完成 |
| done | TokenUsage | 流结束标记 |
| error | String | 错误信息 |

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. 
Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

Property 1: Request adaptation preserves essential data and infers missing fields
*For any* chat request without explicit phase or sessionId, the adapted request should have a valid phase (inferred from project) and a deterministic sessionId (based on userId and projectId)
**Validates: Requirements 1.5, 3.1, 3.2**

Property 2: Scene request adaptation includes all metadata
*For any* scene request with optional parameters (chapterId, characterIds, sceneType, additionalContext, targetWordCount), the adapted ChatRequest should include all provided parameters in its metadata map
**Validates: Requirements 3.4**

Property 3: Validation errors return HTTP 400
*For any* request with invalid fields (null projectId, blank message/prompt), the system should return HTTP 400 with field-level error details
**Validates: Requirements 4.1**

Property 4: SSE events use consistent format
*For any* streaming response, all SSE events should use one of the defined event types from SSEEventBuilder: content, thought, tool_start, tool_end, done, error
**Validates: Requirements 4.4**

## Error Handling

| Error Type | HTTP Status | Error Code | Description |
|------------|-------------|------------|-------------|
| Validation Error | 400 | VALIDATION_ERROR | 请求参数验证失败 |
| Authentication Error | 401 | UNAUTHORIZED | 未认证或 token 无效 |
| AI Service Unavailable | 503 | AI_SERVICE_UNAVAILABLE | AI 服务不可用 |
| Internal Error | 500 | INTERNAL_ERROR | 内部错误 |

## Files to Delete

| File | Reason |
|------|--------|
| `ai_bridge/controller/ChatController.java` | 功能已整合到 AgentController |
| `ai_bridge/controller/SceneController.java` | 功能已整合到 AgentController |
| `agent/adapter/ChatRequestAdapter.java` | 逻辑已内联到 AgentController |

## Testing Strategy

### Unit Tests
- AgentController 端点路由测试
- ChatRequestDto/SceneRequestDto 验证测试
- Phase 推断逻辑测试
- SessionId 生成逻辑测试

### Property-Based Tests (jqwik)
- Property 1: 请求适配 - 验证 phase 推断和 sessionId 生成
- Property 2: 场景元数据 - 验证所有可选参数正确传递
- Property 3: 验证错误处理 - 验证各种无效输入返回正确的错误响应
- Property 4: SSE 事件格式 - 验证事件类型一致性

### Integration Tests
- 完整聊天流程测试
- 场景创作流程测试
- 错误场景测试
