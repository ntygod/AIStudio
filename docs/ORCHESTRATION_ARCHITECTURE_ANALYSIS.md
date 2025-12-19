# Orchestration 架构分析报告

> 分析日期: 2025-12-17
> 分析范围: inkflow-backend-v2 中 `agent` 模块和 `ai_bridge` 模块的 orchestration 对比

## 1. 模块概览

### 1.1 两个 orchestration 模块的位置

```
inkflow-backend-v2/src/main/java/com/inkflow/module/
├── agent/orchestration/           # 新架构
│   ├── AgentOrchestrator.java
│   └── SceneCreationOrchestrator.java
│
└── ai_bridge/orchestration/       # 旧架构
    ├── AgentOrchestrator.java
    ├── SceneCreationOrchestrator.java
    ├── agent/
    │   ├── Agent.java
    │   ├── AbstractAgent.java
    │   ├── WriterAgent.java
    │   ├── ChoreographerAgent.java
    │   ├── PsychologistAgent.java
    │   └── ConsistencyAgent.java
    ├── chain/
    │   ├── ChainExecutionContext.java
    │   └── ChainExecutionException.java
    ├── dto/
    │   ├── SceneRequest.java
    │   ├── SceneResult.java
    │   ├── SceneContext.java
    │   └── AgentOutput.java
    └── event/
        └── AgentThoughtEvent.java
```

---

## 2. 详细对比分析

### 2.1 AgentOrchestrator 对比

| 特性 | agent 模块 | ai_bridge 模块 |
|------|-----------|---------------|
| 包路径 | `module.agent.orchestration` | `module.ai_bridge.orchestration` |
| Agent 类型 | `CapableAgent<I, O>` | `Agent<I, O>` |
| 路由集成 | ✅ 集成 `AgentRouter` | ❌ 无路由 |
| 并行执行 | `executeParallel(List<Agent>)` | `executeParallel(Supplier...)` |
| 链式执行 | 简单实现 | 完整实现 + `ChainExecutionContext` |
| 竞争执行 | `executeAny()` | `executeAny()` |
| 重试机制 | ✅ 指数退避重试 | ❌ 无重试 |
| 超时控制 | ✅ 30s 默认超时 | ❌ 无超时 |
| 事件发布 | `ContextBus` + `ApplicationEventPublisher` | 仅 `ApplicationEventPublisher` |
| 类型安全结果 | 泛型 `CompletableFuture<List<T>>` | `ParallelResult2<T1,T2>` / `ParallelResult3<T1,T2,T3>` |

### 2.2 SceneCreationOrchestrator 对比

| 特性 | agent 模块 | ai_bridge 模块 |
|------|-----------|---------------|
| 编排的 Agent | `WriterAgent` + `ConsistencyAgent` | `ChoreographerAgent` + `PsychologistAgent` + `WriterAgent` |
| 执行模式 | 串行（生成后检查） | 并行分析 → 串行生成 |
| RAG 集成 | ❌ 无 | ✅ `HybridSearchService` |
| 一致性检查 | ✅ 异步触发 | ❌ 无 |
| 流式支持 | ✅ `Flux<String>` | ✅ `Flux<String>` |
| 快速模式 | ❌ 无 | ✅ `createSceneQuick()` |

### 2.3 Agent 基类对比

| 特性 | agent/core/BaseAgent | ai_bridge/orchestration/agent/AbstractAgent |
|------|---------------------|---------------------------------------------|
| 接口 | `CapableAgent<I, O>` | `Agent<I, O>` |
| 能力声明 | ✅ `AgentCapability` | ❌ 无 |
| 流式执行 | ✅ `stream(I input)` | 部分支持 |
| 异步执行 | ✅ `executeAsync()` | ✅ `executeAsync()` |
| 思考事件 | ✅ `publishThought()` | ✅ `publishThought()` |
| ChatModel | `DynamicChatModelFactory` | `DynamicChatModelFactory` |

---

## 3. 重复代码识别

### 3.1 高度重复

1. **AgentOrchestrator** - 核心编排逻辑重复
   - Virtual Thread 执行器
   - `executeParallel()` 方法
   - `executeAny()` 方法
   - 思考事件发布

2. **Agent 基类** - 执行逻辑重复
   - `execute()` 同步执行
   - `executeAsync()` 异步执行
   - `publishThought()` 事件发布

### 3.2 部分重复

1. **SceneCreationOrchestrator** - 场景创作流程
   - 都使用 Virtual Threads
   - 都支持流式输出
   - 但编排的 Agent 不同

### 3.3 独有功能

**agent 模块独有：**
- `AgentRouter` 意图识别和路由
- `FastPathFilter` 快速路由
- `ThinkingAgent` LLM 意图分析
- `AgentCapability` 能力声明
- `ContextBus` 事件总线
- 重试机制和超时控制
- `ToolRegistry` 工具注册
- `SkillRegistry` 技能注册

**ai_bridge 模块独有：**
- `ChainExecutionContext` 完整链式执行上下文
- `ChainExecutionException` 链式执行异常
- `ParallelResult2/3` 类型安全的并行结果
- `ChoreographerAgent` 动作指导
- `PsychologistAgent` 心理分析
- RAG 集成到场景创作

---

## 4. ChatController 执行逻辑分析

### 4.1 当前实现

```java
// ai_bridge/ChatController.java
@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> streamChat(...) {
    // 直接调用 SpringAIChatService，绕过 AgentRouter
    return chatService.chatWithStatus(userId, projectId, message, phase, requestId);
}
```

### 4.2 问题

1. **没有使用 AgentRouter** - 无法进行意图识别和多 Agent 路由
2. **没有利用 Skill 系统** - 无法动态注入技能
3. **没有利用 Tool 系统** - 工具调用与 Agent 分离
4. **与 agent 模块割裂** - 两套独立的执行路径

### 4.3 对比：AgentController 的正确实现

```java
// agent/AgentController.java
@PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> chat(@Valid @RequestBody ChatRequest request) {
    // 通过 AgentOrchestrator 路由到正确的 Agent
    return agentOrchestrator.execute(request);
}
```

---

## 5. 建议的整合方案

### 5.1 目标架构

```
┌─────────────────────────────────────────────────────────────┐
│                    统一入口层                                │
│  ChatController (/api/v1/chat) + AgentController (/api/v2)  │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    路由层 (agent 模块)                       │
│  AgentRouter → FastPath / ThinkingAgent → 目标 Agent        │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    编排层 (合并后)                           │
│  AgentOrchestrator (并行/链式/竞争执行)                      │
│  + ChainExecutionContext (从 ai_bridge 合并)                │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    Agent 执行层                              │
│  BaseAgent + Skill 注入 + Tool 调用 + RAG 检索              │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 整合步骤建议

1. **合并 AgentOrchestrator**
   - 保留 `agent` 模块的版本作为主实现
   - 将 `ai_bridge` 的 `ChainExecutionContext` 合并进来
   - 保留 `ParallelResult2/3` 类型安全结果

2. **合并 SceneCreationOrchestrator**
   - 保留 `agent` 模块的版本
   - 将 `ChoreographerAgent` 和 `PsychologistAgent` 迁移为 Skill
   - 集成 RAG 检索

3. **重构 ChatController**
   - 注入 `AgentRouter`
   - 将请求转换为 `ChatRequest` 并路由

4. **废弃 ai_bridge/orchestration**
   - 标记为 `@Deprecated`
   - 逐步迁移依赖

### 5.3 ChatController 重构示例

```java
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {
    
    private final AgentRouter agentRouter;
    private final PhaseInferenceService phaseInferenceService;
    
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChatRequest request) {
        
        // 推断阶段
        CreationPhase phase = resolvePhase(request.projectId(), request.message(), request.phase());
        
        // 构建 Agent 请求
        com.inkflow.module.agent.dto.ChatRequest agentRequest = new com.inkflow.module.agent.dto.ChatRequest(
            request.message(),
            request.projectId(),
            request.conversationId(),
            phase,
            null,  // intentHint - 可选
            Map.of("userId", user.getId().toString())
        );
        
        // 通过 AgentRouter 路由
        return agentRouter.route(agentRequest);
    }
}
```

---

## 6. 待讨论问题

1. **API 兼容性**：是否需要保持 `/api/v1/chat` 和 `/api/v2/agent/chat` 两个端点？
2. **Skill 迁移**：`ChoreographerAgent` 和 `PsychologistAgent` 是否应该迁移为 Skill？
3. **RAG 集成位置**：RAG 检索应该在 Agent 内部还是作为 Tool？
4. **链式执行**：是否需要保留完整的 `ChainExecutionContext`？
5. **废弃策略**：`ai_bridge/orchestration` 的废弃时间表？

---

## 7. 相关文件清单

### agent 模块
- `agent/orchestration/AgentOrchestrator.java`
- `agent/orchestration/SceneCreationOrchestrator.java`
- `agent/core/BaseAgent.java`
- `agent/core/CapableAgent.java`
- `agent/routing/AgentRouter.java`
- `agent/routing/ThinkingAgent.java`
- `agent/controller/AgentController.java`

### ai_bridge 模块
- `ai_bridge/orchestration/AgentOrchestrator.java`
- `ai_bridge/orchestration/SceneCreationOrchestrator.java`
- `ai_bridge/orchestration/agent/AbstractAgent.java`
- `ai_bridge/orchestration/chain/ChainExecutionContext.java`
- `ai_bridge/controller/ChatController.java`
- `ai_bridge/chat/SpringAIChatService.java`
