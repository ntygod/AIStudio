# InkFlow V2 架构讨论记录

## 2024-12-19 讨论：CreationPhase 重构

### 问题背景
后端大量使用 `CreationPhase` 枚举（灵感收集、世界构建、角色设计、大纲规划、正式写作、修订完善），但这个概念存在问题。

### 问题分析
1. **创作是非线性的** - 用户可能在写正文时突然想到新角色、新设定
2. **强制分阶段会打断创作心流** - 用户不应该被限制在某个"阶段"
3. **阶段概念对 AI 模型路由没有意义** - 用户在任何时候都可能需要执行任何类型的 AI 任务

### 决定
1. **移除 `CreationPhase` 概念** - 不再强制项目处于某个阶段
2. **进度追踪改为纯统计数据**：
   - 总字数、章节数、人物数、Wiki 条目数
   - 伏笔数（已闭合/未闭合）
   - 今日/本周字数
   - 连续创作天数
3. **AI 模型路由改为基于 `TaskType`**（任务类型）：
   - CONTENT_GENERATION - 内容生成
   - OUTLINE_PLANNING - 大纲规划
   - CHARACTER_DESIGN - 角色设计
   - WORLDBUILDING - 世界观构建
   - CONSISTENCY_CHECK - 一致性检查
   - STYLE_ANALYSIS - 风格分析
   - BRAINSTORM - 头脑风暴
   - REVISION - 修订润色

### 需要移除的后端代码
- `CreationPhase` 枚举
- `Project.creationPhase` 字段
- `PhaseTransition` 实体（阶段转换历史）
- `PhaseTransitionRepository`
- `PhaseTransitionService`
- `ProgressController` 中的阶段相关 API
- `ProjectService.updateCreationPhase()` 方法

### 需要保留/重构的代码
- `ProgressSnapshot` - 保留，用于统计快照
- `CreationProgress` - 重命名为 `ProjectStatistics`，移除 phase 相关字段
- 统计相关的 API 保留

---

## 2024-12-19 讨论：路由架构简化

### 问题 1：普通聊天 vs 场景创作分类

**现状：** `ChatRequestDto` 区分"普通聊天"和"场景创作"（通过 `isSceneCreation()` 判断）

**问题：**
- `sceneType`、`chapterId`、`characterIds` 混在一起
- 把"上下文信息"和"请求类型"混淆了

**结论：**
- 移除 `sceneType` 字段和 `isSceneCreation()` 判断
- `chapterId`、`characterIds` 作为**上下文信息**保留，用于 RAG 检索和提示词增强
- 具体执行什么任务由路由层决定，不由前端指定

### 问题 2：Fast Path 和 ThinkingAgent 的触发机制

**现状：**
- Fast Path 触发条件：`intentHint` 参数 或 命令前缀（`/write`、`/plan` 等）
- 否则走 ThinkingAgent 分析

**结论：**
- 这个设计是合理的
- `intentHint` 用于前端快捷操作（选中文字后弹出的菜单）
- 建议参数名从 `intentHint` 改为 `taskType` 更直观
- 命令前缀保留给高级用户

**前端使用场景：**
```
选中文字 → 弹出菜单 → 点击"续写" → 传 taskType: WRITE_CONTENT → Fast Path
普通聊天 → 不传 taskType → ThinkingAgent 分析意图
```

---

## 2024-12-19 讨论：移除 CreationPhase 后的路由分类方案

### 问题
当前 `AgentRouter.applyPhasePriorityForIntent()` 根据 `CreationPhase` 调整意图：
- IDEA/WORLDBUILDING 阶段 → 优先 PLAN_WORLD
- CHARACTER 阶段 → 优先 PLAN_CHARACTER
- WRITING 阶段 → 优先 WRITE_CONTENT
- 等等...

移除 `CreationPhase` 后，这个调整逻辑怎么办？

### 结论：用上下文信息替代阶段信息

**移除：**
- `CreationPhase` 对路由的影响
- `applyPhasePriorityForIntent()` 方法

**新增：基于上下文的智能猜测**

| 上下文条件 | 默认意图 | 说明 |
|-----------|---------|------|
| 有 `chapterId` | WRITE_CONTENT | 用户在编辑章节，大概率想写内容 |
| 有 `characterIds` | PLAN_CHARACTER | 用户在关注角色 |
| 都没有 | GENERAL_CHAT | 走通用对话 |

### 前端传参方案

```typescript
// 前端自动根据 UI 状态填充上下文
const request = {
  projectId: currentProject.id,
  message: userInput,
  
  // 上下文信息 - 自动填充
  chapterId: editorState.currentChapterId || undefined,  // 当前编辑的章节
  characterIds: getSelectedCharacterIds() || undefined,  // 选中文字中的角色
  
  // 快捷操作 - 用户点击按钮时传
  taskType: undefined,  // 普通聊天不传，Fast Path 时传
};
```

### 优势
1. 比 `CreationPhase` 更准确 - 基于实际操作上下文，而非用户声明的阶段
2. 用户无感知 - 不需要手动切换"阶段"
3. Fast Path 不受影响 - 有 `taskType` 直接路由
4. 减少 LLM 调用 - 上下文信息可以提高规则引擎置信度

---

## 2024-12-19 讨论：完整数据流分析

### 数据流概览

```
前端请求 (ChatRequestDto)
    ↓
AgentController.chat()
    ↓
RequestAdapterService.adapt() → ChatRequest
    ↓
┌─────────────────────────────────────────────────────────┐
│ 分支判断: isSceneCreation()?                              │
│   ├─ YES → WorkflowExecutor.execute(WRITE_CONTENT)      │
│   └─ NO  → AgentRouter.route()                          │
└─────────────────────────────────────────────────────────┘
    ↓
AgentRouter.route()
    ├─ 1. FastPathFilter.tryFastPath()
    │      ├─ 有 intentHint → 直接返回 Intent
    │      └─ 有命令前缀 (/write, /plan) → 解析 Intent
    │
    ├─ 2. 无法 Fast Path → ThinkingAgent.analyze()
    │      ├─ RuleBasedClassifier (规则引擎, <10ms)
    │      │    └─ 置信度 >= 0.9 → 直接返回
    │      └─ 置信度 < 0.9 → LLM 分析 (~500ms)
    │
    └─ 3. applyPhasePriorityForIntent() ← ⚠️ 依赖 CreationPhase
           └─ 根据阶段调整意图优先级
    ↓
WorkflowExecutor.execute(intent, request)
    ├─ selectWorkflow(intent) → 选择工作流
    │      Intent → WorkflowType → Workflow 实现
    │
    └─ workflow.execute(request)
           ├─ CONTENT_GENERATION → ContentGenerationWorkflow
           ├─ CREATIVE_DESIGN → CreativeDesignWorkflow
           ├─ PLANNING → PlanningWorkflow
           ├─ QUALITY_CHECK → QualityCheckWorkflow
           ├─ SIMPLE_AGENT → SimpleAgentWorkflow
           └─ 链式工作流 → BrainstormExpandWorkflow 等
    ↓
具体工作流执行 (以 ContentGenerationWorkflow 为例)
    ├─ 1. preprocess() - 并行预处理
    │      ├─ PreflightService.preflight() - 预检
    │      ├─ HybridSearchService.search() - RAG 检索
    │      ├─ getCharacterStates() - 角色状态
    │      └─ buildContextForGeneration() - 风格样本
    │
    ├─ 2. PromptInjector.inject() - Skill 注入
    │
    ├─ 3. WriterAgent.stream() - Agent 执行
    │      └─ ChatClient.stream() → LLM 调用
    │
    └─ 4. postprocess() - 后处理
           └─ ConsistencyAgent.execute() - 一致性检查
    ↓
SSE 流式响应返回前端
```

### 发现的问题

#### 问题 1: ⚠️ CreationPhase 深度耦合

**影响范围：**
- `AgentController` - 返回 phase 信息
- `AgentRouter.applyPhasePriorityForIntent()` - 根据 phase 调整意图
- `AgentRouter.getPhasePreferredIntent()` - phase → intent 映射
- `ThinkingAgent.analyze()` - 接收 phase 参数
- `RuleBasedClassifier.classify()` - 接收 phase 参数
- `RequestAdapterService.resolvePhase()` - 推断 phase
- `PhaseInferenceService.inferPhase()` - 推断 phase
- `ChatRequest.currentPhase` - 携带 phase
- `ChatResponseDto` - 返回 phase

**结论：** 需要全面移除 CreationPhase，用上下文信息替代

#### 问题 2: ⚠️ isSceneCreation() 判断逻辑混乱

**现状：**
```java
public boolean isSceneCreation() {
    return sceneType != null || chapterId != null || characterIds != null;
}
```

**问题：**
- `chapterId` 和 `characterIds` 是**上下文信息**，不应该用来判断"是否场景创作"
- 用户在编辑章节时发普通聊天，也会被误判为"场景创作"

**结论：** 移除 `isSceneCreation()` 判断，统一走 AgentRouter

#### 问题 3: ⚠️ Fast Path 参数名不直观

**现状：**
- `ChatRequest.intentHint` - 用于 Fast Path
- `ChatRequestDto` 没有对应字段

**问题：**
- 前端无法直接传 `intentHint`
- 只能通过命令前缀触发 Fast Path

**结论：** 
- `ChatRequestDto` 添加 `taskType` 字段
- `RequestAdapterService` 将 `taskType` 转换为 `intentHint`

#### 问题 4: ⚠️ 上下文信息传递不完整

**现状：**
- `chapterId`、`characterIds` 放在 metadata 中
- 工作流需要从 metadata 中手动提取

**问题：**
- 类型不安全
- 容易遗漏

**结论：** 
- `ChatRequest` 添加 `chapterId`、`characterIds` 字段
- 或创建 `ChatContext` 对象封装上下文

#### 问题 5: ✅ 工作流选择逻辑合理

**现状：**
- `Intent` → `WorkflowType` → `Workflow` 映射清晰
- 降级机制完善（找不到就用 SimpleAgentWorkflow）

**结论：** 保持现状

#### 问题 6: ✅ Agent 编排器设计合理

**现状：**
- 支持并行执行、链式执行、竞争执行
- 使用 Virtual Threads
- 有重试和超时机制

**结论：** 保持现状

#### 问题 7: ⚠️ ThinkingAgent 依赖 CreationPhase

**现状：**
```java
public IntentResult analyze(String message, CreationPhase phase) {
    RuleBasedClassifier.ClassificationResult ruleResult = 
        ruleClassifier.classify(message, phase);
    // ...
}
```

**问题：**
- 规则引擎使用 phase 提高置信度
- 移除 phase 后，规则引擎准确率可能下降

**结论：** 
- 用上下文信息（chapterId、characterIds）替代 phase
- 规则引擎改为基于上下文的分类

### 重构方案

#### 1. ChatRequestDto 重构

```java
public record ChatRequestDto(
    UUID projectId,
    String message,
    String sessionId,
    
    // 上下文信息（自动填充）
    UUID chapterId,           // 当前编辑的章节
    List<UUID> characterIds,  // 相关角色
    List<UUID> wikiEntryIds,  // 相关 Wiki 条目
    
    // 快捷操作（用户点击按钮时传）
    String taskType,          // 替代 intentHint，如 "WRITE_CONTENT"
    
    // 选项
    Boolean consistency,
    Boolean ragEnabled
) {
    // 移除 isSceneCreation()
    // 移除 sceneType
    // 移除 phase
}
```

#### 2. ChatRequest 重构

```java
public record ChatRequest(
    String message,
    UUID projectId,
    String sessionId,
    
    // 上下文信息
    UUID chapterId,
    List<UUID> characterIds,
    List<UUID> wikiEntryIds,
    
    // Fast Path 提示
    Intent intentHint,        // 从 taskType 转换
    
    // 元数据
    Map<String, Object> metadata
) {
    // 移除 currentPhase
}
```

#### 3. AgentRouter 重构

```java
public Flux<ServerSentEvent<String>> route(ChatRequest request) {
    // 1. Fast Path
    Optional<FastPathResult> fastPath = fastPathFilter.tryFastPath(request);
    if (fastPath.isPresent()) {
        return workflowExecutor.execute(fastPath.get().intent(), request);
    }
    
    // 2. ThinkingAgent 分析（不再传 phase）
    IntentResult intentResult = thinkingAgent.analyze(
        request.message(), 
        request.chapterId(),      // 新增：上下文信息
        request.characterIds()    // 新增：上下文信息
    );
    
    // 3. 基于上下文的意图调整（替代 applyPhasePriorityForIntent）
    Intent finalIntent = applyContextPriority(intentResult, request);
    
    return workflowExecutor.execute(finalIntent, request);
}

private Intent applyContextPriority(IntentResult result, ChatRequest request) {
    if (result.confidence() >= 0.9) {
        return result.intent();
    }
    
    // 基于上下文推断
    if (request.chapterId() != null) {
        return Intent.WRITE_CONTENT;  // 在编辑章节，大概率想写内容
    }
    if (request.characterIds() != null && !request.characterIds().isEmpty()) {
        return Intent.PLAN_CHARACTER; // 关注角色
    }
    
    return result.intent();
}
```

#### 4. AgentController 重构

```java
@PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> chat(
        @AuthenticationPrincipal User user,
        @Valid @RequestBody ChatRequestDto request) {
    
    ChatRequest agentRequest = requestAdapterService.adapt(request, user.getId());
    
    // 统一走 AgentRouter，不再区分 isSceneCreation
    return agentRouter.route(agentRequest);
}
```

### 需要移除的代码

1. **CreationPhase 相关：**
   - `CreationPhase` 枚举
   - `Project.creationPhase` 字段
   - `PhaseTransition` 实体
   - `PhaseTransitionRepository`
   - `PhaseTransitionService`
   - `PhaseInferenceService`
   - `ProgressController` 中的阶段 API
   - `AgentRouter.applyPhasePriorityForIntent()`
   - `AgentRouter.getPhasePreferredIntent()`
   - `ThinkingAgent` 中的 phase 参数
   - `RuleBasedClassifier` 中的 phase 参数

2. **场景创作相关：**
   - `ChatRequestDto.isSceneCreation()`
   - `ChatRequestDto.sceneType`
   - `AgentController` 中的 `isSceneCreation` 分支

### 需要新增的代码

1. **上下文信息：**
   - `ChatRequestDto.taskType` 字段
   - `ChatRequest.chapterId` 字段
   - `ChatRequest.characterIds` 字段
   - `ChatRequest.wikiEntryIds` 字段

2. **路由逻辑：**
   - `AgentRouter.applyContextPriority()` 方法
   - `ThinkingAgent.analyze(message, chapterId, characterIds)` 重载

---

## 2024-12-19 讨论：待讨论问题解决

### 问题 1：上下文信息是否需要封装成 `ChatContext` 对象？

**结论：封装**

单独添加 `chapterId`、`characterIds` 等字段容易遗漏，不利于扩展。

```java
/**
 * 聊天上下文
 * 封装所有与当前操作相关的上下文信息
 */
public record ChatContext(
    UUID chapterId,           // 当前编辑的章节
    List<UUID> characterIds,  // 相关角色
    List<UUID> wikiEntryIds,  // 相关 Wiki 条目
    List<UUID> plotLoopIds,   // 相关伏笔
    String selectedText,      // 选中的文本（用于续写等）
    Integer cursorPosition    // 光标位置（用于插入）
) {
    public static ChatContext empty() {
        return new ChatContext(null, List.of(), List.of(), List.of(), null, null);
    }
    
    public boolean hasChapter() {
        return chapterId != null;
    }
    
    public boolean hasCharacters() {
        return characterIds != null && !characterIds.isEmpty();
    }
}
```

**ChatRequest 重构：**
```java
public record ChatRequest(
    String message,
    UUID projectId,
    String sessionId,
    ChatContext context,      // 封装的上下文
    Intent intentHint,        // Fast Path 提示
    Map<String, Object> metadata
) {}
```

### 问题 2：规则引擎如何基于上下文提高置信度？

**结论：规则引擎不依赖上下文**

规则引擎只依赖输入内容（message），不依赖上下文信息。原因：
- 上下文信息用于"辅助推断"，不是"强制覆盖"
- 规则引擎应该保持简单，只做关键词匹配
- 上下文推断放在 `AgentRouter.applyContextPriority()` 中

**增强规则引擎的方式：**
1. 扩展关键词库
2. 支持正则匹配
3. 支持多关键词组合（AND/OR）
4. 支持否定关键词（NOT）

```java
// 增强的规则引擎示例
public class EnhancedRuleClassifier {
    
    // 规则定义
    private static final List<Rule> RULES = List.of(
        // 高置信度规则（关键词组合）
        Rule.of(Intent.WRITE_CONTENT, 0.95, 
            keywords("续写", "扩写", "写一段"), 
            not("大纲", "规划")),
        
        Rule.of(Intent.PLAN_OUTLINE, 0.95,
            keywords("大纲", "章节规划", "结构"),
            not("写", "生成内容")),
        
        // 中置信度规则
        Rule.of(Intent.PLAN_CHARACTER, 0.8,
            keywords("角色", "人物", "性格")),
        
        // 低置信度规则（单关键词）
        Rule.of(Intent.GENERAL_CHAT, 0.6,
            keywords("你好", "帮我", "请问"))
    );
    
    public ClassificationResult classify(String message) {
        for (Rule rule : RULES) {
            if (rule.matches(message)) {
                return new ClassificationResult(rule.intent(), rule.confidence());
            }
        }
        return new ClassificationResult(Intent.GENERAL_CHAT, 0.3);
    }
}
```

### 问题 3：是否需要保留 `sceneType` 用于提示词增强？

**分析：存在两个不同的 `sceneType` 概念**

| 概念 | 位置 | 用途 | 值示例 |
|------|------|------|--------|
| ChatRequestDto.sceneType | 聊天请求 | 提示词增强 | "对话"、"动作"、"描写" |
| SceneType 枚举 | AI 配置 | 模型路由 | CREATIVE, WRITING, ANALYSIS |

**这是两个完全不同的东西！**

**ChatRequestDto.sceneType 的作用：**
```java
// RequestAdapterService.buildEnhancedPrompt()
if (dto.sceneType() != null) {
    builder.insert(0, "【场景类型: " + dto.sceneType() + "】\n");
}
```

只是在提示词前面加了一行 `【场景类型: 对话】`，告诉 AI 要写什么类型的内容。

**结论：移除 ChatRequestDto.sceneType**

**理由：**
1. 这个信息可以直接写在 message 里，不需要单独字段
2. 用户说"帮我写一段对话"，AI 自然知道要写对话
3. 如果需要强调，可以用 `taskType` + 提示词模板

**替代方案：**
- 用户直接在 message 中说明："帮我写一段**对话**场景"
- 或者前端在发送时自动拼接："【续写对话】" + 用户输入
- 或者使用 Skill 注入机制，根据 Intent 自动注入场景提示

**SceneType 枚举保留：**
- 这是用于 AI 模型配置的，与聊天请求无关
- 用于"不同场景使用不同模型"的功能
- 保持不变

---

## 最终重构方案总结

### 1. 新增 ChatContext

```java
public record ChatContext(
    UUID chapterId,
    List<UUID> characterIds,
    List<UUID> wikiEntryIds,
    List<UUID> plotLoopIds,
    String selectedText,
    Integer cursorPosition
) {}
```

### 2. ChatRequestDto 重构

```java
public record ChatRequestDto(
    UUID projectId,
    String message,
    String sessionId,
    
    // 上下文（封装）
    ChatContextDto context,
    
    // 快捷操作
    String taskType,
    
    // 选项
    Boolean consistency,
    Boolean ragEnabled,
    Integer targetWordCount
) {
    // 移除: phase, sceneType, isSceneCreation()
    // 移除: chapterId, characterIds (移入 context)
}

public record ChatContextDto(
    UUID chapterId,
    List<UUID> characterIds,
    List<UUID> wikiEntryIds,
    String selectedText
) {}
```

### 3. ChatRequest 重构

```java
public record ChatRequest(
    String message,
    UUID projectId,
    String sessionId,
    ChatContext context,
    Intent intentHint,
    Map<String, Object> metadata
) {
    // 移除: currentPhase
}
```

### 4. 规则引擎增强

- 只依赖 message 内容
- 支持关键词组合
- 支持否定关键词
- 不依赖上下文

### 5. 上下文推断

在 `AgentRouter.applyContextPriority()` 中：
- 置信度 >= 0.9 → 直接使用规则引擎结果
- 置信度 < 0.9 且有 chapterId → 倾向 WRITE_CONTENT
- 置信度 < 0.9 且有 characterIds → 倾向 PLAN_CHARACTER

---

## 2024-12-19 讨论：Tool 使用问题分析

### 发现的问题

**问题：Tool 定义了但没有被 LLM 调用！**

项目中有两套 Tool 注册机制：
1. `ToolRegistry` - 自动发现 `@Tool` 注解的方法
2. `SceneToolRegistry` - 按 CreationPhase 分类注册工具

但是，**这些 Tool 从未被传递给 ChatClient！**

### 代码分析

**Tool 定义（正确）：**
```java
// RAGSearchTool.java
@Tool(description = "搜索小说设定和知识库，返回与查询相关的内容")
public String searchKnowledge(
    @ToolParam(description = "项目ID") String projectId,
    @ToolParam(description = "搜索查询词") String query,
    @ToolParam(description = "返回结果数量") Integer topK) {
    // ...
}
```

**Tool 注册（正确）：**
```java
// SceneToolRegistry.java
registerTool("ragSearch", ragSearchTool, EnumSet.of(
    CreationPhase.WORLDBUILDING,
    CreationPhase.CHARACTER,
    // ...
));
```

**Tool 使用（问题所在）：**
```java
// WriterAgent.java - 直接调用 Tool 方法，而不是让 LLM 调用
private String retrieveContext(String projectId, String query) {
    return ragSearchTool.searchKnowledge(projectId, query, 5);  // 直接调用！
}
```

**正确的 Spring AI Tool 使用方式应该是：**
```java
// 应该这样使用
ChatClient client = ChatClient.builder(model)
    .defaultSystem(systemPrompt)
    .defaultTools(ragSearchTool, styleRetrieveTool)  // 传递 Tool 给 LLM
    .build();

// 然后 LLM 会自动决定是否调用 Tool
client.prompt()
    .user(userPrompt)
    .call();
```

### 当前架构的问题

| 组件 | 设计意图 | 实际情况 |
|------|---------|---------|
| `@Tool` 注解 | 让 LLM 自动调用 | ❌ 从未传给 LLM |
| `ToolRegistry` | 管理 Tool 生命周期 | ⚠️ 只用于 API 展示 |
| `SceneToolRegistry` | 按阶段分配 Tool | ❌ 从未使用 |
| `WriterAgent` | 使用 Tool 增强生成 | ⚠️ 直接调用方法，不是 LLM 调用 |

### 两种架构选择

#### 方案 A：保持当前架构（Agent 直接调用 Tool）

**优点：**
- 可控性强，Agent 决定何时调用什么 Tool
- 不依赖 LLM 的 Function Calling 能力
- 延迟可预测

**缺点：**
- Tool 的 `@Tool` 注解没有意义
- 无法利用 LLM 的智能决策能力
- Agent 代码复杂，需要手动编排

**当前实际架构：**
```
用户请求 → Workflow 预处理（调用 Tool）→ Agent（使用预处理结果）→ LLM 生成
```

#### 方案 B：让 LLM 调用 Tool（真正的 Function Calling）

**优点：**
- LLM 自动决定调用哪些 Tool
- Agent 代码简化
- 更灵活，适应性强

**缺点：**
- 依赖 LLM 的 Function Calling 能力
- 延迟不可控（LLM 可能多次调用 Tool）
- 成本更高（多轮对话）

**理想架构：**
```
用户请求 → Agent（带 Tool）→ LLM 决定调用 Tool → Tool 执行 → LLM 生成
```

### 结论

**当前架构是"伪 Tool"架构** - Tool 只是普通的 Service 方法，`@Tool` 注解没有实际作用。

**建议：**
1. **短期**：保持当前架构，但移除 `@Tool` 注解的误导性
   - 将 Tool 类重命名为 Service（如 `RAGSearchService`）
   - 移除 `ToolRegistry` 和 `SceneToolRegistry`
   - 明确 Workflow 负责调用这些 Service

2. **长期**：如果需要真正的 Function Calling
   - 为特定场景（如通用聊天）启用 LLM Tool 调用
   - 保持内容生成等核心流程使用 Workflow 预处理

### 需要讨论

1. 是否需要真正的 LLM Function Calling？
2. 如果不需要，是否移除 `@Tool` 注解避免误导？
3. `ToolRegistry` 和 `SceneToolRegistry` 是否还有存在价值？

---

## 待讨论：Tool 架构选择

### 背景

当前 Tool（如 `UniversalCrudTool`、`RAGSearchTool`）定义了 `@Tool` 注解，但从未被传递给 LLM 进行 Function Calling。实际上是 Workflow/Agent 直接调用这些方法。

### 两种架构对比

| 方案 | 描述 | 性能 | 灵活性 |
|------|------|------|--------|
| **方案 A** | Workflow 预处理 + Agent 生成 | ✅ 高（1次LLM） | ⚠️ 中 |
| **方案 B** | LLM Function Calling | ⚠️ 中（多次LLM） | ✅ 高 |

### 核心问题：创作任务中的 CRUD

用户说"帮我创建一个角色叫张三"，当前架构无法自动保存到数据库。

**场景分类：**
| 场景 | 示例 | 当前支持 |
|------|------|---------|
| 纯生成 | "帮我续写这段" | ✅ |
| 生成+保存 | "帮我创建一个角色" | ❌ |
| 查询 | "主角叫什么名字" | ⚠️ 只能 RAG |
| 修改 | "把主角年龄改成25岁" | ❌ |

### 建议方案：混合架构

```java
switch (intent.getCategory()) {
    case CREATIVE:
        // 生成类 → Workflow 预处理（性能优先）
        return workflowExecutor.execute(intent, request);
        
    case CRUD:
        // CRUD 类 → Function Calling（灵活性优先）
        return crudAgentWithTools.stream(request);
        
    case QUERY:
        // 查询类 → Function Calling
        return queryAgentWithTools.stream(request);
}
```

### Intent 分类扩展

```java
public enum IntentCategory {
    CREATIVE,    // 创作类：续写、扩写、生成大纲
    CRUD,        // CRUD类：创建角色、修改设定
    QUERY,       // 查询类：问答、检索
    ANALYSIS,    // 分析类：一致性检查
    GENERAL      // 通用：闲聊
}
```

### 待决定

1. 是否采用混合架构？
2. 如何区分"设计角色"（生成）和"创建角色"（CRUD）？
3. 是否需要确认机制（生成后询问是否保存）？

---

## 待实现

以上讨论已完成，可以开始更新 spec 文件并规划实现任务。

**已确定的重构项：**
1. 移除 `CreationPhase` 概念
2. 新增 `ChatContext` 封装上下文
3. 简化 `ChatRequestDto`（移除 sceneType、isSceneCreation）
4. 增强规则引擎（关键词组合、否定词）
5. 添加 `taskType` 字段用于 Fast Path

**待讨论的重构项：**
1. Tool 架构选择（混合架构 vs 保持现状）
2. CRUD 操作的处理方式
3. 生成内容中的实体处理（见下方）

---

## 待讨论：生成内容中的实体处理

### 问题背景

用户创建了主要角色后进行正文生成，AI 在写文时会自然产生一些配角、地点、物品等实体。这些实体应该如何处理？

### 当前架构分析

项目中已有实体抽取相关组件：

| 组件 | 功能 | 状态 |
|------|------|------|
| `ContentExtractionService` | 从正文提取实体和关系 | ✅ 已实现 |
| `EntityDeduplicationService` | 实体去重和合并 | ✅ 已实现 |
| `RelationshipInferenceService` | 推断实体间关系 | ✅ 已实现 |
| `ExtractionController` | 提供 API 接口 | ✅ 已实现 |
| `ExtractionAgent` | Agent 形式的抽取 | ✅ 已实现 |

**但是：抽取后的实体只是返回给前端，没有自动保存到数据库！**

### 实体类型分析

| 实体类型 | 示例 | 是否需要保存 | 保存位置 |
|---------|------|-------------|---------|
| 主要角色 | 主角、重要配角 | ✅ 用户主动创建 | Character 表 |
| 临时配角 | 路人、店小二 | ⚠️ 可选 | Character 表（标记为 minor） |
| 地点 | 城市、酒楼 | ✅ 应该保存 | WikiEntry 表 |
| 物品 | 武器、法宝 | ✅ 应该保存 | WikiEntry 表 |
| 组织 | 门派、势力 | ✅ 应该保存 | WikiEntry 表 |

### 处理方案

#### 方案 1：后处理自动抽取 + 用户确认

```
正文生成完成
    ↓
后处理: ContentExtractionService.extractFromContent()
    ↓
返回给前端: 生成的内容 + 抽取的实体列表
    ↓
前端展示: "发现以下新实体，是否添加到知识库？"
    ├─ [张三] 角色 - 酒楼掌柜 [添加] [忽略]
    ├─ [醉仙楼] 地点 - 城中酒楼 [添加] [忽略]
    └─ [青锋剑] 物品 - 主角佩剑 [添加] [忽略]
    ↓
用户选择后调用 API 保存
```

**优点：** 用户有控制权，不会产生垃圾数据
**缺点：** 每次生成后都要确认，打断创作流程

#### 方案 2：自动抽取 + 智能分类保存

```
正文生成完成
    ↓
后处理: ContentExtractionService.extractFromContent()
    ↓
智能分类:
    ├─ 高置信度实体（>0.8）→ 自动保存
    ├─ 中置信度实体（0.5-0.8）→ 保存为"待确认"状态
    └─ 低置信度实体（<0.5）→ 不保存
    ↓
前端侧边栏显示: "本章新增实体" 列表
```

**优点：** 自动化程度高，不打断创作
**缺点：** 可能产生错误数据

#### 方案 3：懒抽取 + 按需保存

```
正文生成完成 → 不做任何抽取
    ↓
用户点击"整理本章实体"按钮
    ↓
触发抽取 + 展示 + 用户选择保存
```

**优点：** 用户完全控制，不浪费资源
**缺点：** 用户可能忘记整理，导致知识库不完整

#### 方案 4：混合方案（推荐）

```
正文生成完成
    ↓
后处理: 轻量级实体识别（只识别名称，不做详细抽取）
    ↓
与已有实体匹配:
    ├─ 已存在的实体 → 更新出现章节
    └─ 新实体 → 标记为"待确认"，显示在侧边栏
    ↓
用户可以:
    ├─ 点击"快速添加" → 自动填充基本信息
    └─ 点击"详细编辑" → 打开完整编辑界面
```

### 数据模型扩展

```java
// Character 表新增字段
public class Character {
    // ... 现有字段
    
    @Enumerated(EnumType.STRING)
    private CharacterSource source;  // USER_CREATED, AI_EXTRACTED, AI_SUGGESTED
    
    @Enumerated(EnumType.STRING)
    private ConfirmationStatus confirmationStatus;  // CONFIRMED, PENDING, REJECTED
    
    private Double extractionConfidence;  // 抽取置信度
    
    private UUID sourceChapterId;  // 首次出现的章节
}

public enum CharacterSource {
    USER_CREATED,    // 用户主动创建
    AI_EXTRACTED,    // AI 从正文抽取
    AI_SUGGESTED     // AI 建议（用户确认后变为 USER_CREATED）
}
```

### 前端展示方案

```
左侧边栏 - 本章相关
├─ 角色
│   ├─ [主角] ← 用户创建，已确认
│   ├─ [配角A] ← 用户创建，已确认
│   └─ [店小二] 🆕 ← AI 抽取，待确认
│
├─ 地点
│   ├─ [京城] ← 已确认
│   └─ [醉仙楼] 🆕 ← AI 抽取，待确认
│
└─ 物品
    └─ [青锋剑] 🆕 ← AI 抽取，待确认
```

### 待决定

1. 选择哪种处理方案？（推荐方案 4）
2. 抽取时机：生成后立即抽取 vs 用户触发？
3. 低置信度实体如何处理？
4. 是否需要"批量确认"功能？
