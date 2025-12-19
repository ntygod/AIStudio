# Design Document: InkFlow V2 Enhanced Creation Flow

## Overview

本文档描述 InkFlow V2 增强版创作流程的技术设计方案。核心目标是构建一个 AI 原生的小说创作工具，用户只需输入自然语言，系统自动理解意图、执行任务、引导创作流程。

### 设计原则

1. **AI 原生** - 依赖 AI 模型的 Tool Calling 能力，而非硬编码的意图识别
2. **阶段感知** - 根据创作阶段动态调整工具和提示词
3. **低成本** - 简单任务使用规则检查，复杂任务才使用 AI
4. **不打扰** - 后台检查静默执行，用户主动查询时才展示

## Architecture

### 整体架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Frontend (React)                               │
│  ChatPanel → SSE Stream → Tool Status Display → Progress Panel          │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         API Layer (Controllers)                          │
│  ChatController (POST /api/v1/chat/stream)                              │
│  - Receives: message, projectId, phase (optional)                       │
│  - Returns: SSE stream (tool_start, tool_end, content, done)            │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         Service Layer                                    │
├─────────────────────────────────────────────────────────────────────────┤
│  SpringAIChatService                                                     │
│  ├── PhaseInferenceService (推断创作阶段)                                │
│  ├── SceneToolRegistry (阶段感知工具选择)                                │
│  ├── PhaseAwarePromptBuilder (阶段感知提示词)                            │
│  ├── DynamicChatModelFactory (智能模型路由)                              │
│  └── ChatMemoryFactory (对话记忆)                                        │
├─────────────────────────────────────────────────────────────────────────┤
│  CreationProgressService (创作进度追踪)                                  │
│  ConsistencyCheckService (一致性检查)                                    │
│  ConversationHistoryService (对话历史持久化)                             │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         Tool Layer                                       │
├─────────────────────────────────────────────────────────────────────────┤
│  UniversalCrudTool     - CRUD 操作 (角色、百科、伏笔、章节等)            │
│  RAGSearchTool         - RAG 检索 (设定、角色、章节内容)                 │
│  CreativeGenTool       - 创意生成 (章节、角色、大纲、世界观)             │
│  DeepReasoningTool     - 深度推理 (剧情分析、角色心理)                   │
│  PreflightTool         - 逻辑预检 (设定冲突、时间线)                     │
│  StyleRetrieveTool     - 风格检索 (写作风格样本)                         │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         Infrastructure Layer                             │
├─────────────────────────────────────────────────────────────────────────┤
│  RequestContextHolder (ScopedValue 跨线程上下文)                         │
│  ToolExecutionAspect (Tool 执行切面，发布事件)                           │
│  ToolInvocationLogger (Tool 调用日志)                                    │
│  AIErrorHandler (错误处理与重试)                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 请求处理流程

```
用户消息 → ChatController
              │
              ▼
         推断/获取 Phase
              │
              ▼
         SceneToolRegistry.getToolsForPhase(phase)
              │
              ▼
         PhaseAwarePromptBuilder.buildSystemPrompt(phase, userId, projectId)
              │
              ▼
         DynamicChatModelFactory.getChatModel(userId, sceneType)
              │
              ▼
         ChatClient.builder(model)
           .defaultTools(tools)
           .defaultSystem(systemPrompt)
           .build()
              │
              ▼
         client.prompt().user(message).stream()
              │
              ├── AI 决定调用 Tool → ToolExecutionAspect 拦截 → 发布事件 → 执行 Tool
              │
              └── AI 直接回复 → 返回内容
              │
              ▼
         SSE Stream (tool_start, tool_end, content, done)
```

## Components and Interfaces

### 1. ChatController (增强版)

```java
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {
    
    public record ChatRequest(
        String message,
        UUID projectId,
        String phase,           // 可选，不传则自动推断
        String conversationId   // 可选，用于恢复会话
    ) {}
    
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody ChatRequest request) {
        
        UUID userId = user.getId();
        String requestId = UUID.randomUUID().toString();
        
        // 推断或使用显式 phase
        CreationPhase phase = request.phase() != null 
            ? CreationPhase.valueOf(request.phase().toUpperCase())
            : phaseInferenceService.inferPhase(request.projectId(), request.message());
        
        return chatService.chatWithStatus(userId, request.projectId(), request.message(), phase, requestId);
    }
}
```

### 2. SpringAIChatService (增强版)

```java
@Service
public class SpringAIChatService {
    
    private final DynamicChatModelFactory modelFactory;
    private final SceneToolRegistry toolRegistry;
    private final PhaseAwarePromptBuilder promptBuilder;
    private final ChatMemoryFactory chatMemoryFactory;
    private final ConversationHistoryService historyService;
    
    public Flux<ServerSentEvent<String>> chatWithStatus(
            UUID userId, UUID projectId, String message, 
            CreationPhase phase, String requestId) {
        
        // 1. 绑定请求上下文 (ScopedValue)
        return ScopedValue.where(RequestContextHolder.CONTEXT, 
                new RequestContext(requestId, userId, projectId))
            .call(() -> executeChat(userId, projectId, message, phase, requestId));
    }
    
    private Flux<ServerSentEvent<String>> executeChat(...) {
        // 2. 获取阶段感知的工具
        Object[] tools = toolRegistry.getToolsArrayForPhase(phase);
        
        // 3. 构建阶段感知的系统提示词
        String systemPrompt = promptBuilder.buildSystemPrompt(phase, userId, projectId);
        
        // 4. 获取智能路由的模型
        SceneType sceneType = mapPhaseToSceneType(phase);
        ChatModel model = modelFactory.getChatModel(userId, sceneType);
        
        // 5. 构建 ChatClient
        ChatClient client = ChatClient.builder(model)
            .defaultTools(tools)
            .defaultSystem(systemPrompt)
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory)
                .conversationId(buildConversationId(userId, projectId))
                .build())
            .build();
        
        // 6. 流式执行
        return client.prompt()
            .user(message)
            .stream()
            .content()
            .map(content -> ServerSentEvent.<String>builder()
                .event("content")
                .data(content)
                .build())
            .mergeWith(toolEventFlux)  // 合并 Tool 事件流
            .concatWith(doneEvent);
    }
}
```

### 3. SceneToolRegistry (增强版)

```java
@Component
public class SceneToolRegistry {
    
    private final Map<CreationPhase, List<Object>> phaseTools = new EnumMap<>(CreationPhase.class);
    
    public SceneToolRegistry(
            UniversalCrudTool crudTool,
            RAGSearchTool ragTool,
            Optional<CreativeGenTool> genTool,
            Optional<DeepReasoningTool> reasoningTool,
            Optional<PreflightTool> preflightTool,
            Optional<StyleRetrieveTool> styleTool) {
        
        // IDEA 阶段: 基础 CRUD + 创意生成
        registerPhase(CreationPhase.IDEA, crudTool, genTool);
        
        // WORLDBUILDING 阶段: CRUD + RAG + 创意生成
        registerPhase(CreationPhase.WORLDBUILDING, crudTool, ragTool, genTool);
        
        // CHARACTER 阶段: CRUD + RAG + 创意生成
        registerPhase(CreationPhase.CHARACTER, crudTool, ragTool, genTool);
        
        // OUTLINE 阶段: CRUD + RAG + 预检 + 深度推理
        registerPhase(CreationPhase.OUTLINE, crudTool, ragTool, preflightTool, reasoningTool);
        
        // WRITING 阶段: 全部工具
        registerPhase(CreationPhase.WRITING, crudTool, ragTool, genTool, styleTool, preflightTool, reasoningTool);
        
        // REVISION 阶段: CRUD + RAG + 预检
        registerPhase(CreationPhase.REVISION, crudTool, ragTool, preflightTool);
    }
    
    public Object[] getToolsArrayForPhase(CreationPhase phase) {
        return phaseTools.getOrDefault(phase, Collections.emptyList()).toArray();
    }
}
```

### 4. PhaseAwarePromptBuilder

```java
@Component
public class PhaseAwarePromptBuilder {
    
    private final CreationProgressService progressService;
    
    public String buildSystemPrompt(CreationPhase phase, UUID userId, UUID projectId) {
        StringBuilder prompt = new StringBuilder();
        
        // 基础提示词（包含 userId 和 projectId）
        prompt.append(buildBasePrompt(userId, projectId));
        
        // 阶段特定提示词
        prompt.append(buildPhasePrompt(phase));
        
        // 创作进度提示
        CreationProgress progress = progressService.getProgress(projectId);
        prompt.append(buildProgressPrompt(progress));
        
        // 工具使用指南
        prompt.append(buildToolGuide(phase));
        
        return prompt.toString();
    }
    
    private String buildPhasePrompt(CreationPhase phase) {
        return switch (phase) {
            case IDEA -> """
                【当前阶段：灵感收集】
                帮助用户收集创意灵感，确定故事核心概念。
                - 询问用户想写什么类型的故事
                - 收集关键词和灵感
                - 建议故事的核心冲突
                完成后建议进入世界观构建阶段。
                """;
            case WORLDBUILDING -> """
                【当前阶段：世界构建】
                帮助用户设计世界观、力量体系、地理环境。
                - 使用 UniversalCrudTool 创建 wiki_entry
                - 使用 RAGSearchTool 检索已有设定
                - 使用 CreativeGenTool 生成世界观内容
                完成后建议进入角色设计阶段。
                """;
            // ... 其他阶段
        };
    }
}
```

### 5. RequestContextHolder (ScopedValue)

```java
public class RequestContextHolder {
    
    public static final ScopedValue<RequestContext> CONTEXT = ScopedValue.newInstance();
    
    public record RequestContext(
        String requestId,
        UUID userId,
        UUID projectId
    ) {}
    
    public static RequestContext current() {
        return CONTEXT.orElseThrow(() -> 
            new IllegalStateException("No request context bound"));
    }
    
    public static UUID currentUserId() {
        return current().userId();
    }
    
    public static UUID currentProjectId() {
        return current().projectId();
    }
}
```

### 6. ToolExecutionAspect (增强版)

```java
@Aspect
@Component
public class ToolExecutionAspect {
    
    private final ApplicationEventPublisher eventPublisher;
    private final ToolInvocationLogger logger;
    
    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    public Object aroundToolExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String toolName = joinPoint.getSignature().getName();
        RequestContext context = RequestContextHolder.current();
        
        // 发布开始事件
        eventPublisher.publishEvent(new ToolExecutionEvent(
            context.requestId(), toolName, ToolExecutionEvent.Phase.START, true));
        
        // 记录调用日志
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            
            // 发布完成事件
            eventPublisher.publishEvent(new ToolExecutionEvent(
                context.requestId(), toolName, ToolExecutionEvent.Phase.END, true));
            
            // 记录成功日志
            logger.logSuccess(toolName, context, System.currentTimeMillis() - startTime, result);
            
            return result;
        } catch (Exception e) {
            // 发布失败事件
            eventPublisher.publishEvent(new ToolExecutionEvent(
                context.requestId(), toolName, ToolExecutionEvent.Phase.END, false));
            
            // 记录失败日志
            logger.logFailure(toolName, context, System.currentTimeMillis() - startTime, e);
            
            throw e;
        }
    }
}
```

## Data Models

### CreationPhase 枚举

```java
public enum CreationPhase {
    IDEA("灵感收集", "收集创意灵感，确定故事核心概念"),
    WORLDBUILDING("世界构建", "设计世界观、力量体系、地理环境"),
    CHARACTER("角色设计", "创建主要角色，设定性格、背景、关系"),
    OUTLINE("大纲规划", "设计故事主线、分卷结构、章节大纲"),
    WRITING("正式写作", "按大纲进行章节创作"),
    REVISION("修订完善", "检查一致性、优化文笔、修复漏洞"),
    COMPLETED("创作完成", "作品已完结");
    
    // ... getters
}
```

### ToolInvocationLog 实体

```java
@Entity
@Table(name = "tool_invocation_logs")
public class ToolInvocationLog {
    @Id
    private UUID id;
    private UUID userId;
    private UUID projectId;
    private String requestId;
    private String toolName;
    private String parameters;  // JSON
    private boolean success;
    private Long durationMs;
    private String resultSummary;
    private String errorMessage;
    private LocalDateTime createdAt;
}
```

### ConversationHistory 实体

```java
@Entity
@Table(name = "conversation_history")
public class ConversationHistory {
    @Id
    private UUID id;
    private UUID userId;
    private UUID projectId;
    private String role;  // user, assistant, system
    private String content;
    private String phase;
    private LocalDateTime createdAt;
}
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Tool 描述完整性
*For any* registered tool, the tool description SHALL be non-empty and contain at least 10 characters.
**Validates: Requirements 1.5, 2.7**

### Property 2: Tool 事件配对
*For any* tool execution, a tool_start event SHALL be followed by exactly one tool_end event with the same tool name.
**Validates: Requirements 2.5, 6.1, 6.2**

### Property 3: 阶段工具映射一致性
*For any* creation phase, SceneToolRegistry.getToolsForPhase(phase) SHALL return a non-empty list of tools.
**Validates: Requirements 4.1-4.7**

### Property 4: 系统提示词包含上下文
*For any* system prompt built for a phase, the prompt SHALL contain userId and projectId strings.
**Validates: Requirements 5.1**

### Property 5: 阶段提示词包含阶段信息
*For any* creation phase, the system prompt SHALL contain the phase name or description.
**Validates: Requirements 5.2-5.7**

### Property 6: ScopedValue 上下文传递
*For any* request context bound via ScopedValue, spawned Virtual Threads SHALL be able to access the same context.
**Validates: Requirements 7.1-7.3**

### Property 7: 模型路由一致性
*For any* creation phase, DynamicChatModelFactory SHALL return a non-null ChatModel.
**Validates: Requirements 8.1-8.5**

### Property 8: 一致性检查限流
*For any* project, consistency checks SHALL NOT exceed 1 per 5 minutes.
**Validates: Requirements 9.7**

### Property 9: 对话历史往返一致性
*For any* conversation message, persisting then retrieving SHALL return equivalent content.
**Validates: Requirements 14.1-14.2**

### Property 10: 创作进度计算正确性
*For any* project state, progress percentage SHALL be between 0 and 100.
**Validates: Requirements 15.2**

### Property 11: Tool 调用日志完整性
*For any* tool invocation, a log entry SHALL be created with tool name, timestamp, and success status.
**Validates: Requirements 17.1-17.3**

### Property 12: 错误重试次数限制
*For any* failed AI call, retry attempts SHALL NOT exceed 3.
**Validates: Requirements 18.1**

## Error Handling

### AIErrorHandler

```java
@Component
public class AIErrorHandler {
    
    private static final int MAX_RETRIES = 3;
    private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(1);
    
    public <T> T executeWithRetry(Supplier<T> operation, String context) {
        int attempts = 0;
        Exception lastException = null;
        
        while (attempts < MAX_RETRIES) {
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;
                attempts++;
                
                if (isRetryable(e) && attempts < MAX_RETRIES) {
                    Duration backoff = INITIAL_BACKOFF.multipliedBy((long) Math.pow(2, attempts - 1));
                    Thread.sleep(backoff.toMillis());
                } else {
                    break;
                }
            }
        }
        
        throw new AIOperationException(formatUserMessage(lastException, context), lastException);
    }
    
    private boolean isRetryable(Exception e) {
        return e instanceof TimeoutException 
            || e instanceof RateLimitException
            || (e instanceof HttpClientErrorException hce && hce.getStatusCode().is5xxServerError());
    }
    
    private String formatUserMessage(Exception e, String context) {
        // 返回用户友好的错误消息，不暴露内部细节
        return "抱歉，处理您的请求时遇到问题，请稍后重试。";
    }
}
```

## Testing Strategy

### 单元测试

- 测试 SceneToolRegistry 的阶段-工具映射
- 测试 PhaseAwarePromptBuilder 的提示词生成
- 测试 RequestContextHolder 的 ScopedValue 绑定
- 测试 AIErrorHandler 的重试逻辑

### 属性测试 (jqwik)

```java
@PropertyDefaults(tries = 100)
class SceneToolRegistryPropertyTest {
    
    /**
     * Property 3: 阶段工具映射一致性
     * Validates: Requirements 4.1-4.7
     */
    @Property
    void everyPhaseHasTools(@ForAll CreationPhase phase) {
        SceneToolRegistry registry = createRegistry();
        Object[] tools = registry.getToolsArrayForPhase(phase);
        assertThat(tools).isNotEmpty();
    }
}

@PropertyDefaults(tries = 100)
class ConversationHistoryPropertyTest {
    
    /**
     * Property 9: 对话历史往返一致性
     * Validates: Requirements 14.1-14.2
     */
    @Property
    void messageRoundTrip(
        @ForAll @StringLength(min = 1, max = 1000) String content,
        @ForAll("validRoles") String role
    ) {
        UUID id = historyService.save(userId, projectId, role, content);
        ConversationHistory retrieved = historyService.findById(id);
        assertThat(retrieved.getContent()).isEqualTo(content);
        assertThat(retrieved.getRole()).isEqualTo(role);
    }
}
```

### 集成测试

- 测试完整的聊天流程（消息 → Tool 调用 → 响应）
- 测试 SSE 事件流的正确性
- 测试跨线程上下文传递

## Configuration

```yaml
inkflow:
  chat:
    # 对话历史配置
    history:
      window-size: 20
      retention-days: 90
    
    # 阶段推断配置
    phase-inference:
      enabled: true
      fallback-to-stored: true
    
    # Tool 配置
    tools:
      deep-reasoning:
        enabled: true
        model: deepseek-reasoner
      preflight:
        ai-enhanced: false  # 默认只用规则检查
      style:
        min-samples: 3
  
  # 一致性检查配置
  consistency:
    enabled: true
    debounce-ms: 5000
    rate-limit-per-project: 1/5m
    ai-check: false  # 默认不使用 AI
  
  # 错误处理配置
  error:
    max-retries: 3
    initial-backoff-ms: 1000
```
