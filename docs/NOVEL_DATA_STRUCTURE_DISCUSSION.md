# InkFlow 小说数据结构讨论记录

> 创建时间: 2025-12-15
> 目的: 探讨AI辅助小说创作软件应具备的核心元素，评估当前InkFlow数据结构的实用性

---

## 一、背景

当前项目分为两个创作路径：
1. **传统模式**: 用户填写各种设定，然后由AI生成小说内容
2. **AI引导模式**: 通过AI问答式引导用户逐步生成小说

不管采用哪种模式，小说的组成元素是固定的。本文档记录我们对这些元素的讨论。

---

## 二、当前InkFlow数据结构概览

### 2.1 项目级配置 (Project)

#### NovelConfig (小说配置)
| 字段 | 类型 | 说明 | 实用性评估 |
|------|------|------|-----------|
| title | string | 小说标题 | ✅ 必需 |
| genre | string | 类型（玄幻、都市等） | ✅ 影响AI生成风格 |
| subGenre | string | 子类型（高武、修仙等） | ⚠️ 可选细化 |
| worldSetting | string | 世界设定描述 | ✅ 核心上下文 |
| protagonistArchetype | string | 主角原型（穿越者/重生者） | ⚠️ 模板化辅助 |
| goldenFinger | string | 金手指/特殊能力 | ⚠️ 网文特色 |
| mainPlot | string | 核心目标/主线剧情 | ✅ 必需 |
| pacing | string | 节奏风格 | ⚠️ 影响生成节奏 |
| narrativeTone | string | 叙事语调 | ⚠️ 影响文风 |
| tags | string[] | 标签 | ⚠️ 分类用 |
| dailyTarget | int | 每日字数目标 | ❓ 用户激励，不影响生成 |

#### WorldStructure (世界观结构)
| 字段 | 类型 | 说明 | 实用性评估 |
|------|------|------|-----------|
| worldView | string | 地理/魔法体系/历史 | ✅ 核心世界观 |
| centralConflict | string | 主要冲突/反派势力 | ✅ 驱动剧情 |
| keyPlotPoints | string[] | 关键情节点 | ✅ 大纲骨架 |
| globalMemory | string | 系列圣经（永久事实） | ✅ 一致性保障 |
| factions | Faction[] | 派系列表 | ⚠️ 复杂度高 |
| regions | MapRegion[] | 地图区域 | ❓ 可视化用 |

### 2.2 内容结构层级

```
Project (项目)
  └── Volume (分卷) - 可选
        └── Chapter (章节)
              └── StoryBlock (剧情块) - AI引导模式专用
```

#### Volume (分卷)
| 字段 | 说明 | 实用性 |
|------|------|--------|
| title | 卷标题 | ✅ |
| summary | 卷摘要 | ✅ |
| coreConflict | 本卷核心冲突 | ✅ |
| orderIndex | 顺序号 | ✅ |
| volumeSummary | 完成后详细总结 | ⚠️ 回顾用 |

#### Chapter (章节)
| 字段 | 说明 | 实用性 |
|------|------|--------|
| title | 章节标题 | ✅ |
| summary | 情节要点 | ✅ 生成指导 |
| content | 实际内容 | ✅ 核心 |
| beats | 详细情节节拍 | ✅ 步骤大纲 |
| tension | 张力等级(1-10) | ⚠️ 节奏控制 |
| hooks | 本章伏笔 | ⚠️ 连贯性 |
| chapterType | 类型(普通/回忆/序章) | ⚠️ |

#### StoryBlock (剧情块) - AI引导模式
| 字段 | 说明 | 实用性 |
|------|------|--------|
| title | 场景描述 | ✅ |
| content | 块内容 | ✅ |
| status | 状态(占位/生成中/完成) | ✅ |
| contextEntityIds | 引用实体ID | ✅ 上下文注入 |

### 2.3 角色系统 (Character)

| 字段分类 | 字段 | 说明 | 实用性 |
|----------|------|------|--------|
| **基础信息** | name, role, gender, age | 基本属性 | ✅ |
| **核心设定** | description | 简短传记 | ✅ |
| | appearance | 外貌描述 | ✅ 视觉一致性 |
| | background | 背景故事 | ✅ |
| | personality | 性格特征 | ✅ |
| **AI写作指导** | speakingStyle | 对话风格 | ✅ 对话生成 |
| | motivation | 核心动机 | ✅ 行为逻辑 |
| | fears | 弱点/恐惧 | ⚠️ 冲突设计 |
| | narrativeFunction | 叙事功能 | ⚠️ 结构设计 |
| **关系网** | relationships | 角色关系列表 | ✅ 互动逻辑 |
| **动态状态** | status, isActive | 当前状态 | ⚠️ 状态追踪 |

### 2.4 辅助系统

#### WikiEntry (百科词条)
| 字段 | 说明 | 实用性 |
|------|------|--------|
| name | 条目名称 | ✅ |
| category | 分类(物品/技能/地点等) | ✅ |
| description | 描述 | ✅ |
| aliases | 别名列表 | ✅ 识别同一实体 |
| history | 历史版本(时间切片) | ⚠️ 复杂度高 |
| relationships | 与其他词条关系 | ⚠️ |

#### PlotLoop (伏笔/情节线索)
| 字段 | 说明 | 实用性 |
|------|------|--------|
| title | 伏笔标题 | ✅ |
| description | 详细描述 | ✅ |
| status | 状态(开放/紧急/已关闭) | ✅ 追踪管理 |
| importance | 重要性(1-5) | ⚠️ |
| setupChapterId | 埋下章节 | ✅ |
| targetChapterId | 计划回收章节 | ✅ |
| relatedCharacterIds | 关联角色 | ⚠️ |

#### StyleSample (风格样本)
| 字段 | 说明 | 实用性 |
|------|------|--------|
| originalAI | AI原始生成 | ✅ 风格学习 |
| userFinal | 用户修改后 | ✅ |
| editRatio | 编辑比例 | ⚠️ |
| vector | 风格向量 | ⚠️ 相似度检索 |

---

## 三、成熟AI小说辅助软件的元素拆解

### 3.1 从创作流程角度拆解

小说创作本质上是一个**从抽象到具体**的过程：

```
灵感/想法 → 世界观 → 人物 → 情节结构 → 章节大纲 → 正文内容 → 润色修改
```

每个阶段需要的数据支撑不同：

| 阶段 | 核心问题 | 需要的数据 | AI能做什么 |
|------|---------|-----------|-----------|
| 灵感 | "我想写什么？" | 类型、题材、核心卖点 | 提供灵感、细化方向 |
| 世界观 | "故事发生在哪？" | 背景设定、规则体系 | 补全设定、检查一致性 |
| 人物 | "谁来演这个故事？" | 角色档案、关系网 | 生成角色、维护关系 |
| 情节结构 | "故事怎么发展？" | 主线、支线、伏笔 | 规划节奏、追踪线索 |
| 章节大纲 | "这一章写什么？" | 场景、冲突、转折 | 生成大纲、分配节拍 |
| 正文 | "具体怎么写？" | 上下文、风格、对话 | 生成内容、保持一致 |
| 润色 | "怎么写得更好？" | 风格样本、修改历史 | 风格迁移、查错纠偏 |

### 3.2 从数据依赖角度拆解

AI生成内容时，需要的上下文可以分为三类：

**1. 静态设定（写之前就确定，很少变）**
- 世界观规则（魔法体系、科技水平、社会结构）
- 角色核心设定（性格、背景、说话方式）
- 故事基调（幽默/严肃、快节奏/慢热）

**2. 动态状态（随剧情推进而变化）**
- 角色当前状态（位置、情绪、受伤情况）
- 已发生的事件（谁知道什么、谁做了什么）
- 未解决的伏笔（埋下了什么、何时回收）

**3. 即时上下文（当前章节/场景需要）**
- 前几章的内容摘要
- 本章涉及的角色
- 本章要完成的情节目标

### 3.3 核心元素的最小化定义

基于上述分析，一个成熟的小说辅助软件**必须**具备以下核心元素：

#### 第一层：故事骨架（必需）

```typescript
interface StoryCore {
  premise: string;       // 一句话概括：这是一个关于什么的故事
  genre: string;         // 类型决定读者期待和叙事惯例
  tone: string;          // 基调决定文风和情绪
  mainConflict: string;  // 核心冲突驱动整个故事
}
```

#### 第二层：世界规则（必需）

```typescript
interface WorldRules {
  setting: string;       // 时空背景
  powerSystem: string;   // 力量体系（如有）
  constraints: string[]; // 不可违反的规则（"系列圣经"）
}
```

#### 第三层：角色系统（必需）

```typescript
interface Character {
  // 识别
  name: string;
  aliases: string[];     // 别名很重要，AI需要识别同一人
  
  // 行为指导
  personality: string;   // 决定反应方式
  speakingStyle: string; // 决定对话风格
  motivation: string;    // 决定行动逻辑
  
  // 关系
  relationships: Relationship[];
  
  // 状态（可选但推荐）
  currentState: string;  // 当前状态
}
```

#### 第四层：情节结构（必需）

```typescript
interface PlotStructure {
  // 宏观
  keyPlotPoints: string[];  // 关键转折点
  
  // 中观
  volumes: Arc[];           // 分卷/故事弧
  
  // 微观
  chapters: Chapter[];      // 章节
}

interface Chapter {
  summary: string;    // 本章要完成什么
  beats: string[];    // 具体步骤
  content: string;    // 实际内容
}
```

#### 第五层：一致性追踪（推荐）

```typescript
interface ConsistencyTracker {
  // 伏笔管理
  plotLoops: PlotLoop[];
  
  // 知识库
  wikiEntries: WikiEntry[];
  
  // 时间线（可选）
  timeline: Event[];
}
```

### 3.4 元素之间的关系

```
┌─────────────────────────────────────────────────────────┐
│                    StoryCore (故事核心)                   │
│         premise + genre + tone + mainConflict           │
└─────────────────────────────────────────────────────────┘
                            │
            ┌───────────────┼───────────────┐
            ▼               ▼               ▼
    ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
    │  WorldRules  │ │  Characters  │ │ PlotStructure│
    │   世界规则    │ │    角色系统   │ │   情节结构    │
    └──────────────┘ └──────────────┘ └──────────────┘
            │               │               │
            └───────────────┼───────────────┘
                            ▼
                ┌──────────────────────┐
                │  ConsistencyTracker  │
                │      一致性追踪       │
                │  (Wiki + PlotLoop)   │
                └──────────────────────┘
                            │
                            ▼
                ┌──────────────────────┐
                │   Content Generation │
                │       内容生成        │
                └──────────────────────┘
```

---

## 四、与当前InkFlow的对比

| 元素 | 理想状态 | InkFlow现状 | 差距分析 |
|------|---------|------------|---------|
| 故事核心 | 简洁聚焦 | NovelConfig字段过多 | 部分字段（如dailyTarget）不影响生成 |
| 世界规则 | 明确约束 | WorldStructure有globalMemory | ✅ 基本满足 |
| 角色系统 | 行为可预测 | 字段完整 | ✅ 设计良好 |
| 情节结构 | 层次清晰 | Volume→Chapter→StoryBlock | ✅ 结构合理 |
| 一致性追踪 | 自动维护 | Wiki+PlotLoop | ⚠️ 功能有但使用率存疑 |

---

## 五、双视角数据结构设计

### 5.1 设计原则

数据结构需要同时服务两个目标：
1. **用户交互友好** - 降低填写门槛，渐进式收集信息
2. **AI生成有效** - 提供足够上下文，确保生成质量和一致性

核心矛盾：用户希望少填，AI需要多知道。

解决方案：**分层设计 + 智能推断 + 渐进收集**

### 5.2 用户视角：交互层数据结构

用户直接接触的数据，按**认知负担**分级：

#### 第一级：必填项（创建项目时）
用户必须提供，否则无法开始。

```typescript
interface UserInput_Level1 {
  // 一句话描述你想写什么
  premise: string;        // "一个程序员穿越到修仙世界"
  
  // 选择类型（下拉选择）
  genre: Genre;           // 玄幻 | 都市 | 科幻 | 言情 | ...
}
```

**用户体验**：只需要2个输入，30秒内可以开始

#### 第二级：推荐填写（引导式收集）
AI可以根据第一级推断，但用户确认/修改会更准确。

```typescript
interface UserInput_Level2 {
  // AI推断后让用户确认
  tone: Tone;             // 幽默 | 严肃 | 热血 | 治愈
  pacing: Pacing;         // 快节奏 | 慢热 | 张弛有度
  
  // 核心冲突（AI可以建议）
  mainConflict: string;   // "对抗天道，追求自由"
  
  // 主角设定（可以用模板）
  protagonistType: string; // 穿越者 | 重生者 | 土著天才
}
```

**用户体验**：AI先给建议，用户选择或修改

#### 第三级：可选增强（写作过程中）
不填也能写，填了效果更好。

```typescript
interface UserInput_Level3 {
  // 世界观细节
  powerSystem?: string;    // 修仙体系描述
  worldConstraints?: string[]; // 不可违反的规则
  
  // 角色深度设定
  characterDetails?: {
    speakingStyle?: string;
    fears?: string;
    motivation?: string;
  };
  
  // 网文特色
  goldenFinger?: string;   // 金手指
  tags?: string[];         // 标签
}
```

**用户体验**：写到需要时再补充，或者AI提示补充

#### 第四级：自动生成（用户不需要填）
系统自动维护，用户可以查看和修改。

```typescript
interface AutoGenerated {
  // Wiki词条 - 从内容中自动提取
  wikiEntries: WikiEntry[];
  
  // 伏笔追踪 - AI自动识别
  plotLoops: PlotLoop[];
  
  // 章节摘要 - 自动生成
  chapterSummaries: string[];
  
  // 角色状态 - 自动追踪
  characterStates: CharacterState[];
}
```

### 5.3 AI视角：生成层数据结构

AI生成内容时需要的上下文，按**注入时机**分类：

#### 系统级上下文（每次生成都注入）

```typescript
interface AIContext_System {
  // 故事DNA - 决定整体风格
  storyDNA: {
    genre: string;
    tone: string;
    pacing: string;
    mainConflict: string;
  };
  
  // 世界规则 - 不可违反
  worldRules: {
    constraints: string[];      // 硬性规则
    powerSystem?: string;       // 力量体系
    globalMemory: string;       // 系列圣经
  };
  
  // 写作风格指导
  styleGuide?: {
    sampleTexts: string[];      // 风格样本
    avoidPatterns: string[];    // 避免的写法
  };
}
```

#### 场景级上下文（根据当前场景注入）

```typescript
interface AIContext_Scene {
  // 当前章节目标
  chapterGoal: {
    summary: string;           // 本章要完成什么
    beats: string[];           // 情节节拍
    tension: number;           // 张力等级
  };
  
  // 涉及角色（只注入相关的）
  involvedCharacters: {
    id: string;
    name: string;
    aliases: string[];         // 别名很重要！
    personality: string;
    speakingStyle: string;
    currentState: string;      // 当前状态
    relationshipsInScene: Relationship[]; // 场景内关系
  }[];
  
  // 相关Wiki（只注入相关的）
  relevantWiki: WikiEntry[];
  
  // 活跃伏笔
  activePlotLoops: PlotLoop[];
}
```

#### 连续性上下文（保持一致性）

```typescript
interface AIContext_Continuity {
  // 前文摘要（滑动窗口）
  previousSummary: string;     // 前3-5章摘要
  
  // 最近内容（精确上下文）
  recentContent: string;       // 前1000-2000字
  
  // 待回收伏笔
  pendingHooks: {
    hook: string;
    setupChapter: number;
    urgency: 'low' | 'medium' | 'high';
  }[];
  
  // 角色最近动态
  recentCharacterActions: {
    characterId: string;
    action: string;
    chapter: number;
  }[];
}
```

### 5.4 数据流转：用户输入 → AI上下文

```
┌─────────────────────────────────────────────────────────────────┐
│                        用户输入层                                │
├─────────────────────────────────────────────────────────────────┤
│  Level 1 (必填)    │  Level 2 (推荐)   │  Level 3 (可选)        │
│  premise, genre    │  tone, conflict   │  powerSystem, tags     │
└────────┬──────────┴────────┬──────────┴────────┬───────────────┘
         │                   │                   │
         ▼                   ▼                   ▼
┌─────────────────────────────────────────────────────────────────┐
│                      智能推断层                                  │
│  - 根据genre推断默认tone/pacing                                  │
│  - 根据premise提取关键词，建议worldRules                         │
│  - 根据protagonistType生成默认角色模板                           │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      存储层 (NovelState)                         │
│  config + structure + characters + chapters + wiki + plotLoops  │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      上下文组装层                                │
│  根据当前任务，从存储层提取相关数据，组装成AI上下文               │
│  - 生成章节 → System + Scene + Continuity                       │
│  - 生成角色 → System + 已有角色关系                              │
│  - 生成大纲 → System + 已有章节摘要                              │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                        AI生成层                                  │
│  接收组装好的上下文，生成内容                                    │
└─────────────────────────────────────────────────────────────────┘
```

### 5.5 字段重新评估

基于双视角，重新评估当前字段：

#### NovelConfig 字段评估

| 字段 | 用户视角 | AI视角 | 结论 |
|------|---------|--------|------|
| title | L1必填 | 低价值 | ✅ 保留，用户需要 |
| genre | L1必填 | 高价值（决定风格） | ✅ 保留，核心 |
| subGenre | L2推荐 | 中价值（细化风格） | ⚠️ 可合并到genre |
| worldSetting | L2推荐 | 高价值（世界观） | ✅ 保留，移到WorldRules |
| protagonistArchetype | L2推荐 | 中价值（角色模板） | ⚠️ 可作为角色生成辅助 |
| goldenFinger | L3可选 | 中价值（网文特色） | ⚠️ 网文专用，可选 |
| mainPlot | L2推荐 | 高价值（核心冲突） | ✅ 保留，核心 |
| pacing | L2推荐 | 高价值（节奏控制） | ✅ 保留 |
| narrativeTone | L2推荐 | 高价值（文风） | ✅ 保留 |
| tags | L3可选 | 低价值 | ⚠️ 分类用，不影响生成 |
| dailyTarget | L4自动 | 无价值 | ❌ 移出，不属于小说数据 |

#### Character 字段评估

| 字段 | 用户视角 | AI视角 | 结论 |
|------|---------|--------|------|
| name | L1必填 | 高价值 | ✅ 核心 |
| aliases | L3可选 | **极高价值** | ✅ 必须有，AI识别同一人 |
| role | L2推荐 | 中价值 | ✅ 保留 |
| personality | L2推荐 | 高价值 | ✅ 核心 |
| speakingStyle | L2推荐 | **极高价值** | ✅ 对话生成必需 |
| motivation | L2推荐 | 高价值 | ✅ 行为逻辑 |
| appearance | L3可选 | 中价值 | ⚠️ 视觉描写时需要 |
| background | L3可选 | 中价值 | ⚠️ 深度场景需要 |
| fears | L3可选 | 中价值 | ⚠️ 冲突设计用 |
| narrativeFunction | L3可选 | 中价值 | ⚠️ 结构设计用 |
| relationships | L2推荐 | 高价值 | ✅ 互动逻辑 |
| status | L4自动 | 高价值 | ✅ 自动追踪 |
| isActive | L4自动 | 中价值 | ✅ 过滤用 |

#### 新增建议字段

| 字段 | 所属 | 用户视角 | AI视角 | 说明 |
|------|------|---------|--------|------|
| storyDNA | Project | L1-L2 | 系统级 | 聚合premise+genre+tone+conflict |
| globalMemory | WorldStructure | L3可选 | **极高价值** | 系列圣经，不可违反的事实 |
| chapterSummary | Chapter | L4自动 | 高价值 | 自动生成，用于连续性 |
| sceneCharacters | Chapter | L4自动 | 高价值 | 本章涉及角色ID列表 |

### 5.6 推荐的数据结构重构

#### 核心层（必需）

```typescript
// 故事DNA - 最核心的设定
interface StoryDNA {
  premise: string;         // 一句话概括
  genre: Genre;            // 类型
  tone: Tone;              // 基调
  pacing: Pacing;          // 节奏
  mainConflict: string;    // 核心冲突
}

// 世界规则 - AI必须遵守
interface WorldRules {
  setting: string;         // 时空背景
  powerSystem?: string;    // 力量体系
  constraints: string[];   // 硬性规则（系列圣经）
}

// 角色核心 - AI生成对话/行为必需
interface CharacterCore {
  id: string;
  name: string;
  aliases: string[];       // 别名（必需！）
  personality: string;     // 性格
  speakingStyle: string;   // 说话方式
  motivation: string;      // 动机
  relationships: Relationship[];
}
```

#### 扩展层（推荐）

```typescript
// 角色扩展
interface CharacterExtended extends CharacterCore {
  role: string;
  appearance: string;
  background: string;
  fears?: string;
  narrativeFunction?: string;
  status: string;          // 当前状态（自动追踪）
  isActive: boolean;
}

// 章节扩展
interface ChapterExtended {
  id: string;
  title: string;
  summary: string;         // 情节要点
  beats: string[];         // 情节节拍
  content: string;         // 实际内容
  
  // AI辅助字段
  involvedCharacterIds: string[];  // 涉及角色
  tension: number;                  // 张力等级
  hooks: string[];                  // 本章伏笔
  
  // 自动生成
  autoSummary?: string;    // AI生成的摘要
}
```

#### 追踪层（自动维护）

```typescript
// 一致性追踪
interface ConsistencyTracker {
  // Wiki - 自动提取 + 用户补充
  wikiEntries: WikiEntry[];
  
  // 伏笔 - AI识别 + 用户管理
  plotLoops: PlotLoop[];
  
  // 角色状态时间线
  characterTimeline: {
    characterId: string;
    chapterId: string;
    stateChange: string;
  }[];
}
```

---

## 六、两种创作模式的数据流

### 6.1 传统模式（用户主导）

```
用户填写 NovelConfig
    ↓
用户填写 WorldStructure
    ↓
用户创建 Characters
    ↓
用户规划 Volumes/Chapters 大纲
    ↓
AI 根据大纲生成 Chapter 内容
    ↓
系统自动维护 Wiki/PlotLoop
```

**最小数据集**：
- 必填：title, genre, premise, mainPlot
- 推荐：worldSetting, 至少1个主角
- 可选：其他所有

### 6.2 AI引导模式（AI主导）

```
AI 问：你想写什么故事？
用户答：premise
    ↓
AI 推断 genre/tone，请用户确认
    ↓
AI 生成 WorldRules 建议，用户修改
    ↓
AI 生成 Characters 建议，用户修改
    ↓
AI 生成 Plot 大纲，用户修改
    ↓
AI 逐章生成内容
    ↓
系统自动维护 Wiki/PlotLoop
```

**最小数据集**：
- 第一轮：premise（一句话）
- 后续：AI推断 + 用户确认

### 6.3 数据收集时机对比

| 数据 | 传统模式 | AI引导模式 |
|------|---------|-----------|
| premise | 创建时填写 | 第一轮对话 |
| genre | 创建时选择 | AI推断+确认 |
| tone/pacing | 创建时选择 | AI推断+确认 |
| worldRules | 创建时填写 | AI生成+修改 |
| characters | 创建时填写 | AI生成+修改 |
| plot | 创建时规划 | AI生成+修改 |
| wiki | 写作中补充 | 自动提取 |
| plotLoops | 写作中标记 | AI识别+确认 |

---

## 七、待讨论问题

1. **StoryDNA 的聚合方式**
   - 是否需要一个独立的 StoryDNA 对象？
   - 还是保持分散在 NovelConfig 中？

2. **别名系统的实现**
   - Character.aliases 是否足够？
   - 是否需要更复杂的实体识别？

3. **自动追踪的触发时机**
   - Wiki 提取：每章完成后？实时？
   - 伏笔识别：AI主动识别还是用户标记？

4. **上下文窗口管理**
   - 长篇小说如何处理上下文长度限制？
   - 摘要策略：固定长度 vs 动态调整？

---

## 八、DDD架构重构分析

### 8.1 当前架构分析

当前采用的是**模块化分层架构**（Module-based Layered Architecture）：

```
inkflow-backend/
├── common/                    # 公共组件
│   ├── exception/
│   ├── response/
│   └── util/
├── config/                    # 配置
│   └── security/
└── module/                    # 业务模块（按功能划分）
    ├── project/               # 项目模块
    │   ├── controller/
    │   ├── dto/
    │   ├── entity/
    │   ├── repository/
    │   └── service/
    ├── character/             # 角色模块
    ├── chapter/               # 章节模块
    ├── wiki/                  # Wiki模块
    ├── plotloop/              # 伏笔模块
    ├── ai_bridge/             # AI桥接模块（特殊）
    │   ├── adapter/           # 领域适配器
    │   ├── chat/              # 聊天服务
    │   ├── embedding/         # 向量嵌入
    │   ├── tool/              # AI工具
    │   └── ...
    ├── rag/                   # RAG检索模块
    ├── conversation/          # 对话管理模块
    └── ...
```

**当前架构特点**：
- ✅ 按功能模块划分，职责清晰
- ✅ 每个模块内部分层（Controller→Service→Repository）
- ⚠️ 模块间依赖关系不够清晰
- ⚠️ 领域逻辑分散在Service层
- ⚠️ Entity是贫血模型（只有数据，没有行为）
- ⚠️ ai_bridge模块承担了太多职责

### 8.2 DDD核心概念映射

如果按DDD思想重构，需要识别以下概念：

#### 限界上下文（Bounded Context）

```
┌─────────────────────────────────────────────────────────────────┐
│                        InkFlow 系统                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────┐ │
│  │   创作上下文      │  │   生成上下文      │  │  知识上下文    │ │
│  │   (Creation)     │  │   (Generation)   │  │  (Knowledge)   │ │
│  │                  │  │                  │  │                │ │
│  │  - Project       │  │  - AIChat        │  │  - Wiki        │ │
│  │  - Character     │  │  - ContentGen    │  │  - RAG         │ │
│  │  - Chapter       │  │  - StyleLearning │  │  - Embedding   │ │
│  │  - Volume        │  │  - ContextAssembly│ │  - Search      │ │
│  │  - PlotLoop      │  │                  │  │                │ │
│  └────────┬─────────┘  └────────┬─────────┘  └───────┬────────┘ │
│           │                     │                    │          │
│           └─────────────────────┼────────────────────┘          │
│                                 │                               │
│                    ┌────────────▼────────────┐                  │
│                    │      编排上下文          │                  │
│                    │    (Orchestration)      │                  │
│                    │                         │                  │
│                    │  - CreationPhase        │                  │
│                    │  - ConversationFlow     │                  │
│                    │  - IntentRecognition    │                  │
│                    └─────────────────────────┘                  │
│                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐                     │
│  │   用户上下文      │  │   基础设施上下文   │                     │
│  │   (User)         │  │ (Infrastructure)  │                     │
│  │                  │  │                  │                     │
│  │  - Auth          │  │  - Provider      │                     │
│  │  - Usage         │  │  - Cache         │                     │
│  │  - Settings      │  │  - RateLimit     │                     │
│  └──────────────────┘  └──────────────────┘                     │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

#### 聚合根（Aggregate Root）识别

| 聚合根 | 包含的实体 | 说明 |
|--------|-----------|------|
| **Novel** | Project, NovelConfig, WorldStructure | 小说是核心聚合，包含所有设定 |
| **Character** | CharacterRelationship | 角色是独立聚合，有自己的生命周期 |
| **Volume** | Chapter, StoryBlock | 分卷包含章节，章节包含剧情块 |
| **WikiEntry** | WikiHistoryEntry, WikiRelationship | Wiki词条是独立聚合 |
| **PlotLoop** | - | 伏笔是独立聚合 |
| **Conversation** | ConversationHistory, IntentLog | 对话是独立聚合 |

#### 领域事件（Domain Event）

```java
// 创作上下文事件
ChapterCompletedEvent        // 章节完成 → 触发Wiki提取、伏笔检测
CharacterCreatedEvent        // 角色创建 → 触发RAG索引
WorldStructureUpdatedEvent   // 世界观更新 → 触发一致性检查

// 生成上下文事件
ContentGeneratedEvent        // 内容生成 → 触发风格学习
ContextAssembledEvent        // 上下文组装完成 → 开始生成

// 知识上下文事件
WikiEntryExtractedEvent      // Wiki提取 → 更新知识库
EmbeddingCompletedEvent      // 向量化完成 → 可供检索
```

### 8.3 推荐的DDD架构

#### 目录结构

```
inkflow-backend/
├── shared/                           # 共享内核
│   ├── domain/
│   │   ├── event/                    # 领域事件基类
│   │   ├── entity/                   # 实体基类
│   │   └── valueobject/              # 值对象基类
│   └── infrastructure/
│       ├── persistence/              # 持久化基础
│       └── messaging/                # 消息基础
│
├── creation/                         # 创作上下文（核心域）
│   ├── domain/
│   │   ├── model/
│   │   │   ├── novel/                # Novel聚合
│   │   │   │   ├── Novel.java        # 聚合根
│   │   │   │   ├── StoryDNA.java     # 值对象
│   │   │   │   ├── WorldRules.java   # 值对象
│   │   │   │   └── NovelId.java      # 值对象
│   │   │   ├── character/            # Character聚合
│   │   │   │   ├── Character.java    # 聚合根（充血模型）
│   │   │   │   ├── CharacterCore.java# 值对象
│   │   │   │   └── Relationship.java # 值对象
│   │   │   ├── content/              # Content聚合
│   │   │   │   ├── Volume.java       # 聚合根
│   │   │   │   ├── Chapter.java      # 实体
│   │   │   │   └── StoryBlock.java   # 实体
│   │   │   └── tracking/             # Tracking聚合
│   │   │       ├── PlotLoop.java     # 聚合根
│   │   │       └── PlotLoopStatus.java# 值对象
│   │   ├── event/                    # 领域事件
│   │   │   ├── ChapterCompletedEvent.java
│   │   │   └── CharacterCreatedEvent.java
│   │   ├── service/                  # 领域服务
│   │   │   ├── ConsistencyChecker.java
│   │   │   └── PlotLoopTracker.java
│   │   └── repository/               # 仓储接口
│   │       ├── NovelRepository.java
│   │       └── CharacterRepository.java
│   ├── application/                  # 应用层
│   │   ├── command/                  # 命令
│   │   │   ├── CreateNovelCommand.java
│   │   │   └── UpdateChapterCommand.java
│   │   ├── query/                    # 查询
│   │   │   ├── GetNovelQuery.java
│   │   │   └── ListCharactersQuery.java
│   │   └── service/                  # 应用服务
│   │       ├── NovelApplicationService.java
│   │       └── CharacterApplicationService.java
│   ├── infrastructure/               # 基础设施层
│   │   ├── persistence/
│   │   │   ├── jpa/
│   │   │   │   ├── NovelJpaRepository.java
│   │   │   │   └── NovelJpaEntity.java
│   │   │   └── mapper/
│   │   │       └── NovelMapper.java
│   │   └── event/
│   │       └── SpringEventPublisher.java
│   └── interfaces/                   # 接口层
│       ├── rest/
│       │   ├── NovelController.java
│       │   └── CharacterController.java
│       └── dto/
│           ├── NovelDto.java
│           └── CharacterDto.java
│
├── generation/                       # 生成上下文（核心域）
│   ├── domain/
│   │   ├── model/
│   │   │   ├── context/              # 上下文组装
│   │   │   │   ├── AIContext.java    # 聚合根
│   │   │   │   ├── SystemContext.java# 值对象
│   │   │   │   ├── SceneContext.java # 值对象
│   │   │   │   └── ContinuityContext.java
│   │   │   └── generation/           # 生成任务
│   │   │       ├── GenerationTask.java
│   │   │       └── GenerationResult.java
│   │   ├── service/                  # 领域服务
│   │   │   ├── ContextAssembler.java # 上下文组装器
│   │   │   └── StyleMatcher.java     # 风格匹配
│   │   └── port/                     # 端口（接口）
│   │       ├── AIModelPort.java      # AI模型端口
│   │       └── KnowledgePort.java    # 知识库端口
│   ├── application/
│   │   └── service/
│   │       ├── ContentGenerationService.java
│   │       └── ChatService.java
│   └── infrastructure/
│       └── adapter/                  # 适配器
│           ├── openai/
│           ├── deepseek/
│           └── gemini/
│
├── knowledge/                        # 知识上下文（支撑域）
│   ├── domain/
│   │   ├── model/
│   │   │   ├── wiki/
│   │   │   │   ├── WikiEntry.java
│   │   │   │   └── WikiCategory.java
│   │   │   └── embedding/
│   │   │       ├── EmbeddingRecord.java
│   │   │       └── ChunkInfo.java
│   │   └── service/
│   │       ├── WikiExtractor.java
│   │       └── SemanticSearcher.java
│   └── infrastructure/
│       ├── rag/
│       └── fulltext/
│
├── orchestration/                    # 编排上下文（支撑域）
│   ├── domain/
│   │   ├── model/
│   │   │   ├── phase/
│   │   │   │   ├── CreationPhase.java
│   │   │   │   └── PhaseTransition.java
│   │   │   └── conversation/
│   │   │       ├── Conversation.java
│   │   │       └── Intent.java
│   │   └── service/
│   │       ├── PhaseOrchestrator.java
│   │       └── IntentRecognizer.java
│   └── application/
│       └── ConversationFlowService.java
│
└── user/                             # 用户上下文（通用域）
    ├── domain/
    │   └── model/
    │       ├── User.java
    │       └── UserSettings.java
    └── application/
        └── AuthService.java
```

### 8.4 核心领域模型示例

#### Novel 聚合根（充血模型）

```java
// creation/domain/model/novel/Novel.java
public class Novel extends AggregateRoot<NovelId> {
    private NovelId id;
    private String title;
    private StoryDNA storyDNA;           // 值对象
    private WorldRules worldRules;        // 值对象
    private UserId ownerId;
    private CreationPhase currentPhase;
    private Instant createdAt;
    private Instant updatedAt;
    
    // 工厂方法
    public static Novel create(String title, String premise, Genre genre, UserId ownerId) {
        Novel novel = new Novel();
        novel.id = NovelId.generate();
        novel.title = title;
        novel.storyDNA = StoryDNA.fromPremise(premise, genre);
        novel.worldRules = WorldRules.empty();
        novel.ownerId = ownerId;
        novel.currentPhase = CreationPhase.INITIALIZATION;
        novel.createdAt = Instant.now();
        
        // 发布领域事件
        novel.registerEvent(new NovelCreatedEvent(novel.id, title, genre));
        return novel;
    }
    
    // 领域行为
    public void updateWorldRules(WorldRules newRules) {
        this.worldRules = newRules;
        this.updatedAt = Instant.now();
        registerEvent(new WorldRulesUpdatedEvent(this.id, newRules));
    }
    
    public void advancePhase() {
        CreationPhase nextPhase = this.currentPhase.next();
        if (canAdvanceTo(nextPhase)) {
            this.currentPhase = nextPhase;
            registerEvent(new PhaseAdvancedEvent(this.id, nextPhase));
        } else {
            throw new PhaseTransitionException("Cannot advance to " + nextPhase);
        }
    }
    
    private boolean canAdvanceTo(CreationPhase phase) {
        return switch (phase) {
            case WORLD_BUILDING -> storyDNA.isComplete();
            case CHARACTER_CREATION -> worldRules.hasBasicSetting();
            case PLOTTING -> true; // 可以没有角色就开始规划
            case DRAFTING -> true;
            case MAINTENANCE -> true;
            default -> false;
        };
    }
}
```

#### StoryDNA 值对象

```java
// creation/domain/model/novel/StoryDNA.java
public record StoryDNA(
    String premise,
    Genre genre,
    Tone tone,
    Pacing pacing,
    String mainConflict
) {
    // 工厂方法：从premise推断其他字段
    public static StoryDNA fromPremise(String premise, Genre genre) {
        return new StoryDNA(
            premise,
            genre,
            genre.defaultTone(),      // 根据类型推断默认基调
            genre.defaultPacing(),    // 根据类型推断默认节奏
            null                      // 核心冲突待用户补充
        );
    }
    
    public boolean isComplete() {
        return premise != null && !premise.isBlank()
            && genre != null
            && tone != null
            && pacing != null;
    }
    
    public StoryDNA withMainConflict(String conflict) {
        return new StoryDNA(premise, genre, tone, pacing, conflict);
    }
}
```

#### Character 聚合根

```java
// creation/domain/model/character/Character.java
public class Character extends AggregateRoot<CharacterId> {
    private CharacterId id;
    private NovelId novelId;
    private CharacterCore core;           // 值对象：核心设定
    private CharacterExtension extension; // 值对象：扩展设定
    private List<Relationship> relationships;
    private CharacterState currentState;  // 值对象：当前状态
    private boolean isActive;
    
    // 领域行为
    public void speak(String dialogue) {
        // 验证对话是否符合角色说话风格
        if (!core.speakingStyle().matches(dialogue)) {
            registerEvent(new StyleMismatchWarningEvent(this.id, dialogue));
        }
    }
    
    public void updateState(String newState, ChapterId chapterId) {
        CharacterState oldState = this.currentState;
        this.currentState = new CharacterState(newState, chapterId, Instant.now());
        registerEvent(new CharacterStateChangedEvent(this.id, oldState, this.currentState));
    }
    
    public void addRelationship(CharacterId targetId, String relation, String attitude) {
        Relationship rel = new Relationship(targetId, relation, attitude);
        this.relationships.add(rel);
        registerEvent(new RelationshipAddedEvent(this.id, targetId, relation));
    }
    
    // 查询方法
    public boolean canInteractWith(Character other) {
        return this.relationships.stream()
            .anyMatch(r -> r.targetId().equals(other.getId()));
    }
}
```

#### 上下文组装器（领域服务）

```java
// generation/domain/service/ContextAssembler.java
public class ContextAssembler {
    
    public AIContext assembleForChapterGeneration(
        Novel novel,
        Chapter chapter,
        List<Character> involvedCharacters,
        List<WikiEntry> relevantWiki,
        List<PlotLoop> activePlotLoops,
        String recentContent
    ) {
        // 系统级上下文
        SystemContext system = SystemContext.builder()
            .storyDNA(novel.getStoryDNA())
            .worldRules(novel.getWorldRules())
            .build();
        
        // 场景级上下文
        SceneContext scene = SceneContext.builder()
            .chapterGoal(chapter.getSummary())
            .beats(chapter.getBeats())
            .tension(chapter.getTension())
            .characters(involvedCharacters.stream()
                .map(this::toSceneCharacter)
                .toList())
            .wiki(relevantWiki)
            .plotLoops(activePlotLoops)
            .build();
        
        // 连续性上下文
        ContinuityContext continuity = ContinuityContext.builder()
            .recentContent(recentContent)
            .pendingHooks(extractPendingHooks(activePlotLoops))
            .build();
        
        return new AIContext(system, scene, continuity);
    }
    
    private SceneCharacter toSceneCharacter(Character c) {
        return new SceneCharacter(
            c.getId(),
            c.getCore().name(),
            c.getCore().aliases(),
            c.getCore().personality(),
            c.getCore().speakingStyle(),
            c.getCurrentState().description()
        );
    }
}
```

### 8.5 上下文间通信

#### 使用领域事件解耦

```java
// 创作上下文发布事件
@Service
public class ChapterApplicationService {
    
    @Transactional
    public void completeChapter(ChapterId chapterId) {
        Chapter chapter = chapterRepository.findById(chapterId);
        chapter.markCompleted();
        chapterRepository.save(chapter);
        
        // 发布领域事件
        eventPublisher.publish(new ChapterCompletedEvent(
            chapter.getId(),
            chapter.getNovelId(),
            chapter.getContent()
        ));
    }
}

// 知识上下文订阅事件
@Component
public class WikiExtractionHandler {
    
    @EventListener
    public void onChapterCompleted(ChapterCompletedEvent event) {
        // 从章节内容中提取Wiki词条
        List<WikiEntry> entries = wikiExtractor.extract(event.content());
        entries.forEach(wikiRepository::save);
        
        // 触发向量化
        embeddingService.embedChapter(event.chapterId(), event.content());
    }
}

// 生成上下文订阅事件
@Component
public class StyleLearningHandler {
    
    @EventListener
    public void onChapterCompleted(ChapterCompletedEvent event) {
        // 学习用户的写作风格
        styleLearner.learnFromContent(event.novelId(), event.content());
    }
}
```

### 8.6 与当前架构的对比

| 方面 | 当前架构 | DDD架构 |
|------|---------|---------|
| 模块划分 | 按功能（project, character, chapter） | 按限界上下文（creation, generation, knowledge） |
| 实体模型 | 贫血模型（只有getter/setter） | 充血模型（包含领域行为） |
| 业务逻辑 | 分散在Service层 | 集中在领域模型和领域服务 |
| 模块通信 | 直接依赖注入 | 通过领域事件解耦 |
| 数据访问 | Repository直接返回Entity | Repository返回聚合根 |
| 值对象 | 很少使用 | 大量使用（StoryDNA, WorldRules等） |

### 8.7 重构建议

#### 渐进式迁移策略

1. **第一阶段：引入值对象**
   - 将 NovelConfig 拆分为 StoryDNA + WorldRules
   - 将 Character 的核心字段提取为 CharacterCore
   - 不改变现有模块结构

2. **第二阶段：充血模型**
   - 将业务逻辑从 Service 移到 Entity
   - 引入领域事件
   - 保持现有API不变

3. **第三阶段：限界上下文**
   - 重新组织包结构
   - 引入防腐层（ACL）
   - 实现上下文间事件通信

4. **第四阶段：CQRS（可选）**
   - 分离命令和查询
   - 引入读模型优化查询性能

#### 优先级建议

| 改动 | 优先级 | 原因 |
|------|--------|------|
| 引入 StoryDNA 值对象 | 高 | 聚合核心设定，提升AI上下文质量 |
| Character 充血模型 | 高 | 角色行为逻辑复杂，需要封装 |
| 领域事件 | 中 | 解耦模块，但需要基础设施支持 |
| 限界上下文重组 | 低 | 改动大，可以后期进行 |

---

## 九、数据结构迁移计划

### 9.1 迁移目标

将当前分散的数据结构重构为更清晰的层次结构，同时保持向后兼容。

**核心改动**：
1. 引入 `StoryDNA` 值对象，聚合故事核心设定
2. 为 `Character` 添加 `aliases` 字段
3. 引入 `WorldRules` 值对象，从 `WorldStructure` 中提取
4. 移除 `dailyTarget` 等非小说数据字段

### 9.2 第一阶段：后端数据结构改动

#### 9.2.1 新增 StoryDNA 值对象

```java
// inkflow-backend/src/main/java/com/inkflow/module/project/entity/StoryDNA.java
package com.inkflow.module.project.entity;

import lombok.*;
import java.io.Serializable;

/**
 * 故事DNA - 聚合故事的核心设定
 * 这些设定决定了AI生成内容的整体风格和方向
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryDNA implements Serializable {
    
    /** 一句话概括：这是一个关于什么的故事 */
    private String premise;
    
    /** 类型：玄幻、都市、科幻、言情等 */
    private String genre;
    
    /** 子类型：高武、修仙、末世等 */
    private String subGenre;
    
    /** 基调：幽默、严肃、热血、治愈 */
    private String tone;
    
    /** 节奏：快节奏、慢热、张弛有度 */
    private String pacing;
    
    /** 核心冲突：驱动整个故事的主要矛盾 */
    private String mainConflict;
    
    /**
     * 检查StoryDNA是否完整（可以开始创作）
     */
    public boolean isComplete() {
        return premise != null && !premise.isBlank()
            && genre != null && !genre.isBlank();
    }
    
    /**
     * 从NovelConfig迁移数据
     */
    public static StoryDNA fromNovelConfig(NovelConfig config) {
        return StoryDNA.builder()
            .premise(config.getMainPlot())  // mainPlot 作为 premise
            .genre(config.getGenre())
            .subGenre(config.getSubGenre())
            .tone(config.getNarrativeTone())
            .pacing(config.getPacing())
            .mainConflict(config.getMainPlot())
            .build();
    }
}
```

#### 9.2.2 新增 WorldRules 值对象

```java
// inkflow-backend/src/main/java/com/inkflow/module/project/entity/WorldRules.java
package com.inkflow.module.project.entity;

import lombok.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 世界规则 - AI生成时必须遵守的约束
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorldRules implements Serializable {
    
    /** 时空背景设定 */
    private String setting;
    
    /** 力量体系（修仙、魔法、科技等） */
    private String powerSystem;
    
    /** 硬性规则列表（系列圣经） - AI绝对不能违反 */
    @Builder.Default
    private List<String> constraints = new ArrayList<>();
    
    /** 全局记忆 - 必须永远记住的事实 */
    private String globalMemory;
    
    /**
     * 检查是否有基本设定
     */
    public boolean hasBasicSetting() {
        return setting != null && !setting.isBlank();
    }
    
    /**
     * 从WorldStructure迁移数据
     */
    public static WorldRules fromWorldStructure(WorldStructure structure) {
        List<String> constraints = new ArrayList<>();
        if (structure.getGlobalMemory() != null) {
            constraints.add(structure.getGlobalMemory());
        }
        
        return WorldRules.builder()
            .setting(structure.getWorldView())
            .powerSystem(null)  // 新字段，需要用户补充
            .constraints(constraints)
            .globalMemory(structure.getGlobalMemory())
            .build();
    }
}
```

#### 9.2.3 修改 Character 实体，添加 aliases

```java
// 在 Character.java 中添加
/**
 * 角色别名列表 - AI识别同一角色的关键
 * 例如：["张三", "三哥", "老张", "那个男人"]
 */
@JdbcTypeCode(SqlTypes.JSON)
@Column(columnDefinition = "jsonb")
@Builder.Default
private List<String> aliases = new ArrayList<>();
```

#### 9.2.4 修改 NovelConfig，添加 storyDNA

```java
// 在 NovelConfig.java 中添加
/**
 * 故事DNA - 聚合核心设定（新字段）
 * 向后兼容：如果为null，从其他字段构建
 */
private StoryDNA storyDNA;

/**
 * 获取StoryDNA，如果为null则从现有字段构建
 */
public StoryDNA getEffectiveStoryDNA() {
    if (storyDNA != null) {
        return storyDNA;
    }
    return StoryDNA.fromNovelConfig(this);
}
```

#### 9.2.5 修改 WorldStructure，添加 worldRules

```java
// 在 WorldStructure.java 中添加
/**
 * 世界规则 - 聚合约束条件（新字段）
 * 向后兼容：如果为null，从其他字段构建
 */
private WorldRules worldRules;

/**
 * 获取WorldRules，如果为null则从现有字段构建
 */
public WorldRules getEffectiveWorldRules() {
    if (worldRules != null) {
        return worldRules;
    }
    return WorldRules.fromWorldStructure(this);
}
```

### 9.3 第二阶段：前端类型定义改动

#### 9.3.1 新增 TypeScript 类型

```typescript
// inkflow-frontend/types.ts 中添加

/**
 * 故事DNA - 聚合故事核心设定
 */
export interface StoryDNA {
  premise: string;        // 一句话概括
  genre: string;          // 类型
  subGenre?: string;      // 子类型
  tone: string;           // 基调
  pacing: string;         // 节奏
  mainConflict: string;   // 核心冲突
}

/**
 * 世界规则 - AI必须遵守的约束
 */
export interface WorldRules {
  setting: string;        // 时空背景
  powerSystem?: string;   // 力量体系
  constraints: string[];  // 硬性规则
  globalMemory?: string;  // 全局记忆
}

/**
 * 角色核心 - AI生成对话/行为必需的字段
 */
export interface CharacterCore {
  name: string;
  aliases: string[];      // 别名（必需！）
  personality: string;
  speakingStyle: string;
  motivation: string;
}
```

#### 9.3.2 修改 Character 接口

```typescript
export interface Character {
  // ... 现有字段 ...
  
  // 新增别名字段
  aliases?: string[];     // 别名列表
}
```

#### 9.3.3 修改 NovelConfig 接口

```typescript
export interface NovelConfig {
  // ... 现有字段 ...
  
  // 新增 StoryDNA（可选，向后兼容）
  storyDNA?: StoryDNA;
}
```

### 9.4 第三阶段：数据库迁移

```sql
-- V17__add_story_dna_and_aliases.sql

-- 1. 为 characters 表添加 aliases 列
ALTER TABLE characters 
ADD COLUMN IF NOT EXISTS aliases jsonb DEFAULT '[]'::jsonb;

-- 2. 为 projects 表的 config 添加 storyDNA 字段（JSONB内部字段）
-- 注意：JSONB字段的内部结构变更不需要DDL，只需要应用层处理

-- 3. 为 projects 表的 structure 添加 worldRules 字段（JSONB内部字段）
-- 同上

-- 4. 创建索引优化别名查询
CREATE INDEX IF NOT EXISTS idx_characters_aliases 
ON characters USING gin (aliases);

-- 5. 数据迁移：为现有角色生成默认别名（角色名本身）
UPDATE characters 
SET aliases = jsonb_build_array(name)
WHERE aliases = '[]'::jsonb OR aliases IS NULL;
```

### 9.5 迁移顺序

```
1. 后端：创建新的值对象类（StoryDNA, WorldRules）
   ↓
2. 后端：修改现有实体，添加新字段（向后兼容）
   ↓
3. 数据库：执行迁移脚本
   ↓
4. 后端：修改Service层，使用新的值对象
   ↓
5. 前端：添加新的TypeScript类型
   ↓
6. 前端：修改组件，支持新字段
   ↓
7. 测试：验证向后兼容性
   ↓
8. 清理：移除废弃字段（可选，下一版本）
```

### 9.6 向后兼容策略

1. **新字段可选**：所有新字段都是可选的，旧数据仍然有效
2. **自动迁移**：通过 `getEffectiveXxx()` 方法自动从旧字段构建新对象
3. **渐进式更新**：用户编辑时自动保存到新字段
4. **API兼容**：保持现有API不变，新字段作为扩展

### 9.7 影响范围评估

| 模块 | 影响程度 | 改动内容 |
|------|---------|---------|
| project/entity | 中 | 添加StoryDNA, WorldRules |
| character/entity | 低 | 添加aliases字段 |
| project/service | 中 | 使用新值对象 |
| ai_bridge/adapter | 中 | 上下文组装使用新结构 |
| 前端types.ts | 低 | 添加新类型 |
| 前端组件 | 低 | 支持aliases编辑 |

---

## 十、后续行动

- [x] 确定 StoryDNA 的最终结构
- [x] 设计别名系统的实现方案
- [ ] 实现后端值对象类
- [ ] 执行数据库迁移
- [ ] 修改Service层使用新结构
- [ ] 更新前端类型定义
- [ ] 修改前端组件支持新字段
- [ ] 测试向后兼容性

---

*本文档将持续更新，记录后续讨论内容*
*最后更新：2025-12-15*
