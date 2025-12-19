# Agent 精简方案讨论记录

## 背景

2024-12-16 对 unified-agent-architecture 规范进行审视，发现原规范定义了 18+ 个 Agent，实现和维护成本过高，需要精简。

## 原规范 Agent 列表（18 个）

| 类别 | Agent | 职责 | 使用频率 |
|------|-------|------|----------|
| **路由层** | ThinkingAgent | 意图分析 | 每次请求 |
| | ChatAgent | 通用对话/兜底 | 高 |
| **创意构思** | IdeaAgent | 灵感激发 | 低（仅 IDEA 阶段） |
| | WorldBuilderAgent | 世界观设计 | 低（仅 WORLDBUILDING） |
| | NameGeneratorAgent | 名称生成 | 中 |
| **角色设计** | CharacterDesignerAgent | 角色设计 | 中 |
| | RelationshipAgent | 关系网络 | 低 |
| | ArchetypeAgent | 原型匹配 | 低 |
| **结构规划** | PlannerAgent | 大纲规划 | 中 |
| | PlotLoopAgent | 伏笔管理 | 中 |
| | PacingAgent | 节奏控制 | 低 |
| **内容创作** | WriterAgent | 内容生成 | 高 |
| **质量保障** | ConsistencyAgent | 一致性检查 | 高 |
| | StyleAgent | 文风分析 | 中 |
| | PolishAgent | 文本润色 | 低 |
| | CriticAgent | 内容评审 | 低 |
| **辅助工具** | SummaryAgent | 摘要生成 | 中 |
| | ExtractionAgent | 实体抽取 | 中 |
| | TranslationAgent | 多语言翻译 | 极低 |

## 精简方案对比

### 方案 A：激进精简（8 个 Agent）

```
核心 Agent（必须）:
1. ThinkingAgent - 意图分析与路由
2. ChatAgent - 通用对话/兜底
3. WriterAgent - 内容生成（+ Skill Slots 动态注入）
4. ConsistencyAgent - 一致性检查

合并 Agent:
5. CreativeAgent - 合并 IdeaAgent + WorldBuilderAgent + NameGeneratorAgent
6. CharacterAgent - 合并 CharacterDesignerAgent + RelationshipAgent + ArchetypeAgent
7. PlannerAgent - 合并 PlannerAgent + PlotLoopAgent + PacingAgent
8. UtilityAgent - 合并 SummaryAgent + ExtractionAgent

移除:
- StyleAgent → 合并到 WriterAgent 的 Skill Slots
- PolishAgent → 合并到 WriterAgent 的 Skill Slots
- CriticAgent → 合并到 ConsistencyAgent
- TranslationAgent → 移除（需求极低）
```

**优点**: 实现成本最低，维护简单
**缺点**: 合并后的 Agent 职责较重，Prompt 可能过长

### 方案 B：适度精简（12 个 Agent）

```
路由层（2 个）:
1. ThinkingAgent - 意图分析
2. ChatAgent - 通用对话

创作层（4 个）:
3. CreativeAgent - 合并 IdeaAgent + WorldBuilderAgent
4. CharacterAgent - 合并 CharacterDesignerAgent + RelationshipAgent + ArchetypeAgent
5. PlannerAgent - 大纲规划 + 伏笔管理
6. WriterAgent - 内容生成（+ Skill Slots）

质量层（3 个）:
7. ConsistencyAgent - 一致性检查
8. StyleAgent - 文风分析与统一
9. RevisionAgent - 合并 PolishAgent + CriticAgent

工具层（3 个）:
10. NameGeneratorAgent - 保留独立（高频使用）
11. SummaryAgent - 摘要生成
12. ExtractionAgent - 实体抽取

移除:
- PacingAgent → 合并到 PlannerAgent
- TranslationAgent → 移除
```

**优点**: 平衡了功能完整性和实现复杂度
**缺点**: 仍有 12 个 Agent 需要实现

### 方案 C：功能导向精简（10 个 Agent）- 推荐

```
核心流程 Agent（6 个）:
1. ThinkingAgent - 意图分析与路由决策
2. ChatAgent - 通用对话/兜底/帮助
3. DesignAgent - 合并所有设计类（世界观、角色、关系、原型）
4. PlannerAgent - 大纲规划 + 伏笔 + 节奏
5. WriterAgent - 内容生成（+ Skill Slots 动态注入）
6. ConsistencyAgent - 一致性检查 + 文风分析

按需 Agent（4 个，懒执行）:
7. NameGeneratorAgent - 名称生成（独立，高频）
8. SummaryAgent - 摘要生成
9. ExtractionAgent - 实体抽取
10. RevisionAgent - 润色 + 评审

移除:
- IdeaAgent → 合并到 DesignAgent
- WorldBuilderAgent → 合并到 DesignAgent
- CharacterDesignerAgent → 合并到 DesignAgent
- RelationshipAgent → 合并到 DesignAgent
- ArchetypeAgent → 合并到 DesignAgent
- PlotLoopAgent → 合并到 PlannerAgent
- PacingAgent → 合并到 PlannerAgent
- StyleAgent → 合并到 ConsistencyAgent
- PolishAgent → 合并到 RevisionAgent
- CriticAgent → 合并到 RevisionAgent
- TranslationAgent → 移除
```

**优点**: 按用户工作流程组织，符合创作阶段
**缺点**: DesignAgent 职责较重

## Agent 与 Skill Slots 的分工

```
WriterAgent 的 Skill Slots:
- DialogueSkill - 对话生成
- ActionSkill - 动作场景
- PsychologySkill - 心理描写
- DescriptionSkill - 环境描写
- PlotLoopReminderSkill - 伏笔提醒

DesignAgent 的 Skill Slots:
- WorldBuildingSkill - 世界观设计
- CharacterDesignSkill - 角色设计
- RelationshipSkill - 关系网络
- ArchetypeSkill - 原型匹配

PlannerAgent 的 Skill Slots:
- OutlineSkill - 大纲生成
- PlotLoopSkill - 伏笔管理
- PacingSkill - 节奏控制
```

## 关键设计决策

1. **Fast Path 机制** - 前端传递 intentHint 可跳过 ThinkingAgent
2. **Skill Slots 架构** - 通过 Prompt Injection 动态注入技能
3. **Context Bus** - Session 级别上下文共享
4. **懒执行模式** - SummaryAgent、ExtractionAgent、RevisionAgent 按需触发
5. **移除 SpringAIChatService** - 新项目无需兼容，直接使用 AgentRouter

## 最终推荐：混合方案（10 个 Agent）

基于方案 C 的进一步优化，拆分 DesignAgent 为更专业的 WorldBuilderAgent 和 CharacterAgent：

```
自动路由层（2 个）:
1. ThinkingAgent - 意图分析与路由决策（规则引擎 + 小模型）
2. ChatAgent - 通用对话/兜底/帮助

专业创作层（4 个）:
3. WorldBuilderAgent - 世界观设计（力量体系、地理、历史、文化）
4. CharacterAgent - 角色设计 + 关系网络 + 原型匹配
5. PlannerAgent - 大纲规划 + 伏笔管理 + 节奏控制
6. WriterAgent - 内容生成（+ Skill Slots 动态注入）

质量保障（1 个）:
7. ConsistencyAgent - 一致性检查 + 文风分析

工具层（3 个，懒执行）:
8. NameGeneratorAgent - 名称生成（独立，高频使用）
9. SummaryAgent - 摘要生成
10. ExtractionAgent - 实体抽取

移除的 Agent（8 个）:
- IdeaAgent → 合并到 WorldBuilderAgent（灵感激发作为其子功能）
- RelationshipAgent → 合并到 CharacterAgent
- ArchetypeAgent → 合并到 CharacterAgent
- PlotLoopAgent → 合并到 PlannerAgent
- PacingAgent → 合并到 PlannerAgent
- StyleAgent → 合并到 ConsistencyAgent
- PolishAgent → 移除（WriterAgent 的 Skill Slots 可实现）
- CriticAgent → 移除（ConsistencyAgent 可覆盖）
- RevisionAgent → 移除（WriterAgent + ConsistencyAgent 组合可实现）
- TranslationAgent → 移除（需求极低，可后续按需添加）
```

### 混合方案优势

1. **专业性保留**: WorldBuilderAgent 和 CharacterAgent 分离，保持专业深度
2. **用户体验**: 用户可以明确知道"设计世界观"和"设计角色"是不同的专家
3. **Prompt 可控**: 每个 Agent 职责清晰，Prompt 不会过长
4. **扩展性**: Skill Slots 机制允许动态扩展能力
5. **成本可控**: 懒执行模式避免不必要的 Token 消耗

### Agent 与创作阶段映射

| 创作阶段 | 优先 Agent | 说明 |
|---------|-----------|------|
| IDEA | WorldBuilderAgent, ChatAgent | 灵感激发、概念探索 |
| WORLDBUILDING | WorldBuilderAgent | 世界观、力量体系设计 |
| CHARACTER | CharacterAgent | 角色设计、关系网络 |
| OUTLINE | PlannerAgent | 大纲、伏笔、节奏 |
| WRITING | WriterAgent | 内容生成 |
| REVISION | ConsistencyAgent, WriterAgent | 一致性检查、润色 |

### Skill Slots 分配

```
WriterAgent 的 Skill Slots:
- DialogueSkill - 对话生成
- ActionSkill - 动作场景
- PsychologySkill - 心理描写
- DescriptionSkill - 环境描写
- PlotLoopReminderSkill - 伏笔提醒
- PolishSkill - 文本润色（替代 PolishAgent）

CharacterAgent 的 Skill Slots:
- RelationshipSkill - 关系网络设计
- ArchetypeSkill - 原型匹配
- BackstorySkill - 背景故事生成

PlannerAgent 的 Skill Slots:
- OutlineSkill - 大纲生成
- PlotLoopSkill - 伏笔管理
- PacingSkill - 节奏控制
```

## 待确认

请确认是否采用此混合方案（10 个 Agent）？确认后将更新 requirements.md 和 design.md。
