# Design Document: AI Bridge Thread Safety Refactoring

## Overview

本设计文档描述了 `ai_bridge/chat` 模块的线程安全重构方案。核心目标是消除全局状态导致的并发问题，建立可靠的上下文传递机制，并恢复 DeepSeek R1 推理模型的正确使用。

### 问题分析

当前实现存在以下严重问题：

1. **全局状态竞争**: `globalToolContext` 使用 `AtomicReference` 存储上下文，在并发场景下会导致用户 A 的 Tool 读取到用户 B 的 projectId
2. **ThreadLocal 在 WebFlux 中失效**: 响应式流会在不同线程间切换，ThreadLocal 无法正确传递
3. **R1 模型被错误降级**: `isThinkingModeModel()` 检查导致 DeepThinkingTool 无法使用 R1

### 解决方案概述

1. **从方法参数提取上下文**: Tool 方法签名中已包含 userId 和 projectId，直接从 AOP 参数读取
2. **使用 Spring Event + 请求 ID 关联**: 发布带有 requestId 的事件，SSE 流按 requestId 过滤
3. **区分模型使用场景**: 主对话模型降级，DeepThinkingTool 专用方法不降级

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        ChatController                            │
│  - 生成 requestId                                                │
│  - 调用 SpringAIChatService                                      │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     SpringAIChatService                          │
│  - 创建 SSE 流                                                   │
│  - 订阅 ToolExecutionEvent (按 requestId 过滤)                   │
│  - 调用 ChatClient                                               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     ToolExecutionAspect                          │
│  - 拦截 @Tool 方法                                               │
│  - 从方法参数提取 userId, projectId                              │
│  - 发布 ToolExecutionEvent (包含 requestId)                      │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   ToolInvocationLogger                           │
│  - 接收来自 Aspect 的上下文                                      │
│  - 记录 Tool 调用日志                                            │
└─────────────────────────────────────────────────────────────────┘
```

### 请求 ID 传递流程

```
1. ChatController 生成 requestId (UUID)
2. requestId 通过 Reactor Context 传递到 ChatClient
3. Tool 方法被调用时，Aspect 从 Reactor Context 获取 requestId
4. Aspect 发布带 requestId 的 ToolExecutionEvent
5. SSE 流订阅事件，按 requestId 过滤只接收当前请求的事件
```

## Components and Interfaces

### 1. ToolExecutionAspect (重构)

```java
@Aspect
@Component
public class ToolExecutionAspect {
    
    private final ApplicationEventPublisher eventPublisher;
    
    // 移除所有静态变量和 ThreadLocal
    // 移除 ToolExecutionListener 接口
    
    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    public Object aroundToolExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        // 1. 从方法参数提取 userId 和 projectId
        ToolContext ctx = extractContextFromArgs(joinPoint);
        
        // 2. 从 Reactor Context 获取 requestId (如果可用)
        String requestId = getRequestIdFromReactorContext();
        
        // 3. 设置 Logger 上下文
        if (ctx != null) {
            ToolInvocationLogger.setContext(ctx.userId(), ctx.projectId());
        }
        
        // 4. 发布开始事件
        eventPublisher.publishEvent(new ToolExecutionEvent(
            toolName, Phase.START, true, ctx, requestId));
        
        try {
            Object result = joinPoint.proceed();
            eventPublisher.publishEvent(new ToolExecutionEvent(
                toolName, Phase.END, true, ctx, requestId));
            return result;
        } catch (Throwable e) {
            eventPublisher.publishEvent(new ToolExecutionEvent(
                toolName, Phase.END, false, ctx, requestId));
            throw e;
        } finally {
            ToolInvocationLogger.clearContext();
        }
    }
    
    private ToolContext extractContextFromArgs(ProceedingJoinPoint joinPoint) {
        // 遍历参数，查找 userId 和 projectId
    }
}
```

### 2. ToolExecutionEvent (增强)

```java
public class ToolExecutionEvent extends ApplicationEvent {
    private final String toolName;
    private final Phase phase;
    private final boolean success;
    private final UUID userId;      // 新增
    private final UUID projectId;   // 新增
    private final String requestId; // 新增：用于关联 SSE 流
    
    public enum Phase { START, END }
}
```

### 3. SpringAIChatService (重构)

```java
@Service
public class SpringAIChatService {
    
    public Flux<ServerSentEvent<String>> chatWithStatus(
            UUID userId, UUID projectId, String message, 
            CreationPhase phase, String requestId) {
        
        // 1. 创建 Tool 事件流 (按 requestId 过滤)
        Flux<ServerSentEvent<String>> toolEventFlux = createToolEventFlux(requestId);
        
        // 2. 创建内容流 (通过 Reactor Context 传递 requestId)
        Flux<ServerSentEvent<String>> contentFlux = createContentFlux(
            userId, projectId, message, phase, requestId);
        
        // 3. 合并流
        return Flux.merge(toolEventFlux, contentFlux)
            .concatWith(doneEvent());
    }
    
    private Flux<ServerSentEvent<String>> createToolEventFlux(String requestId) {
        // 使用 ApplicationListener 或 Sinks 订阅 ToolExecutionEvent
        // 按 requestId 过滤事件
    }
}
```

### 4. DynamicChatModelFactory (重构)

```java
@Component
public class DynamicChatModelFactory {
    
    /**
     * 获取聊天模型 (用于主对话，会降级 R1)
     */
    public ChatModel getChatModel(UUID userId, SceneType scene) {
        // 现有逻辑，R1 会降级到 deepseek-chat
    }
    
    /**
     * 获取推理模型 (用于 DeepThinkingTool，不降级)
     * 已存在，确保被正确调用
     */
    public ChatModel getReasoningModel(UUID userId) {
        // 直接返回 deepseek-reasoner，不降级
    }
}
```

### 5. DeepReasoningTool (确认调用正确方法)

```java
@Component
public class DeepReasoningTool {
    
    private final DynamicChatModelFactory modelFactory;
    
    @Tool(description = "深度思考工具")
    public String deepThinking(UUID userId, UUID projectId, String question) {
        // 使用 getReasoningModel 而非 getChatModel
        ChatModel reasoningModel = modelFactory.getReasoningModel(userId);
        if (reasoningModel == null) {
            return "深度思考功能需要配置 DeepSeek 服务商";
        }
        // 调用推理模型
    }
}
```

## Data Models

### ToolExecutionEvent

| Field | Type | Description |
|-------|------|-------------|
| toolName | String | Tool 方法名称 |
| phase | Phase | START 或 END |
| success | boolean | 执行是否成功 |
| userId | UUID | 用户 ID (可为 null) |
| projectId | UUID | 项目 ID (可为 null) |
| requestId | String | 请求关联 ID |
| timestamp | Instant | 事件时间戳 |

### ToolContext (简化)

```java
public record ToolContext(UUID userId, UUID projectId) {}
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Concurrent Context Isolation

*For any* set of concurrent chat requests with different userIds, when Tools are invoked, each Tool execution SHALL receive the userId and projectId from its originating request, not from any other concurrent request.

**Validates: Requirements 1.1**

### Property 2: Parameter-Based Context Extraction

*For any* Tool method invocation with userId and projectId parameters, the ToolExecutionAspect SHALL extract these values from the method arguments and include them in the published ToolExecutionEvent.

**Validates: Requirements 1.2, 4.1, 4.2**

### Property 3: Event Filtering by RequestId

*For any* SSE stream subscribed with a specific requestId, the stream SHALL only receive ToolExecutionEvents that have a matching requestId, filtering out events from other requests.

**Validates: Requirements 2.2, 5.3**

### Property 4: Event Structure Completeness

*For any* ToolExecutionEvent published by the system, the event SHALL contain non-null values for toolName, phase, and requestId (userId and projectId may be null if not available from parameters).

**Validates: Requirements 2.4**

### Property 5: Reasoning Model Non-Downgrade

*For any* call to `getReasoningModel()`, the returned ChatModel SHALL be configured with `deepseek-reasoner` model, not downgraded to `deepseek-chat`.

**Validates: Requirements 3.1, 3.3**

### Property 6: Chat Model Conditional Downgrade

*For any* call to `getChatModel()` where the configured model is a thinking-mode model (R1, reasoner), the returned ChatModel SHALL be configured with `deepseek-chat` to support Tool Calling.

**Validates: Requirements 3.2**

## Error Handling

### 缺失参数处理

当 Tool 方法没有 userId/projectId 参数时：
1. 记录 WARN 级别日志
2. 发布事件时 userId/projectId 设为 null
3. 继续正常执行 Tool 方法

### 推理模型不可用

当用户未配置 DeepSeek 时：
1. `getReasoningModel()` 返回 null
2. DeepThinkingTool 返回友好提示信息
3. 不抛出异常

### 事件发布失败

当事件发布失败时：
1. 记录 ERROR 级别日志
2. 不影响 Tool 方法执行
3. SSE 流继续工作，只是缺少该事件

## Testing Strategy

### 单元测试

1. **ToolExecutionAspect 参数提取测试**
   - 测试从不同签名的方法中提取 userId/projectId
   - 测试缺失参数时的处理

2. **DynamicChatModelFactory 模型选择测试**
   - 测试 getChatModel 的降级逻辑
   - 测试 getReasoningModel 不降级

### 属性测试

使用 jqwik 进行属性测试：

1. **并发隔离属性测试**
   - 生成随机的并发请求
   - 验证每个请求的 Tool 调用收到正确的上下文

2. **事件过滤属性测试**
   - 生成随机的 requestId 和事件
   - 验证过滤逻辑正确

### 测试框架

- 单元测试: JUnit 5
- 属性测试: jqwik
- Mock: Mockito

