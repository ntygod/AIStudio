# Design Document

## Overview

本设计文档描述了AI原生小说创作平台（InkFlow 2.0）的技术架构设计。系统采用DDD（领域驱动设计）架构思想，以Spring Boot 3.5.x + Spring AI 1.1.2为核心技术栈，构建一个真正以AI为核心驱动力的长篇小说创作系统。

### 设计原则

1. **AI原生**: AI不是附加功能，而是系统的核心驱动力
2. **领域驱动**: 以小说创作领域为中心，清晰划分限界上下文
3. **事件驱动**: 通过领域事件实现模块间松耦合
4. **可扩展性**: 支持多AI提供商、多模型配置
5. **高性能**: 多级缓存、异步处理、流式响应

### 技术栈

| 层次 | 技术选型 | 版本 | 说明 |
|------|----------|------|------|
| **Runtime** | Java | **22** | Virtual Threads (正式), Structured Concurrency (预览) |
| **Core Framework** | Spring Boot | **3.5.x** | 基于 Spring Framework 6.2，稳定生产版本 |
| **AI Framework** | Spring AI | **1.1.2** | Advisors API, Fluent ChatClient, Function Calling |
| **Database** | PostgreSQL | **16+** | pgvector (HNSW), JSONB |
| **Cache** | Redis Stack | **7.4+** | 向量缓存与 JSON 操作 |
| **Messaging** | Spring Events | - | 应用内事件驱动 |
| **API** | REST + SSE | - | 流式传输，GraphQL 可选 |
| **Build** | Maven | 3.9+ | - |
| **Test** | JUnit 5 + jqwik | 5.11+ | 属性测试框架 |

> **重要说明**: Spring AI 没有 2.0 计划，1.x 是长期维护的主版本。Agent 实现应使用 `CompletableFuture` + Virtual Threads + Spring AI 1.1.2 的 `ChatClient` 来实现协同。

### Spring AI 1.1.2 关键特性

1. **统一的ChatClient API**: 简化的流式API，支持链式调用 (`chatClient.prompt().user().call().content()`)
2. **Function Calling**: 通过 `@Tool` 注解定义工具，支持 `@ToolParam` 参数描述
3. **Advisor模式**: 可插拔的请求/响应拦截器 (`RequestResponseAdvisor`)，用于RAG、日志等
4. **ChatMemory**: 内置的对话记忆抽象 (`InMemoryChatMemory`, `MessageChatMemoryAdvisor`)
5. **模型可观测性**: 内置的Micrometer指标和追踪支持 (`@Observed` 注解)
6. **VectorStore API**: 统一的向量存储接口，支持 PgVectorStore 等多种后端

### Java 22 关键特性应用

1. **Virtual Threads (虚拟线程)**: 所有I/O密集型任务运行在虚拟线程上，支持百万级并发 (`Thread.startVirtualThread()`)
2. **CompletableFuture + Virtual Threads**: 多Agent协同场景，使用 `CompletableFuture.allOf()` + `newVirtualThreadPerTaskExecutor()` 并行启动多个AI任务
3. **ThreadLocal**: 继续使用 ThreadLocal 传递请求上下文（虚拟线程下也能正常工作）
4. **Pattern Matching**: 简化领域对象的状态处理逻辑 (`switch` 表达式增强)
5. **Record Patterns**: 用于DTO和值对象的解构

### Agent 实现范式 (Java 22 + Virtual Threads)

Agent 不再是框架黑盒，而是标准的 Java Callable 任务，运行在虚拟线程上：

```java
// Agent = ChatClient + CompletableFuture + Virtual Threads
@Service
public class SceneOrchestrator {
    private final ChatClient chatClient;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public SceneResult generateScene(String prompt) {
        // 并行启动多个子Agent
        var choreographyFuture = CompletableFuture.supplyAsync(() -> 
            callSubAgent("动作指导", "设计打斗动作", prompt), executor);
        var psychologyFuture = CompletableFuture.supplyAsync(() -> 
            callSubAgent("心理专家", "分析角色心理", prompt), executor);
        
        // 等待所有任务完成
        CompletableFuture.allOf(choreographyFuture, psychologyFuture).join();
        
        // 主笔Agent综合结果
        return finalWrite(prompt, choreographyFuture.join(), psychologyFuture.join());
    }
}
```

### PostgreSQL 16+ 关键特性

1. **HNSW索引**: pgvector 扩展支持高效向量检索
2. **JSONB**: 高效的JSON存储和查询
3. **并行查询**: 大表扫描性能提升
4. **逻辑复制**: 支持CDC场景

## Architecture

### 整体架构图：Event-Driven Agentic Mesh

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              接口层 (Interface Layer)                            │
├─────────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐ │
│  │ GraphQL     │  │ RSocket     │  │ SSE Stream  │  │ OpenAPI Docs            │ │
│  │ Subscriptions│ │ Streaming   │  │ (Legacy)    │  │ /swagger-ui             │ │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼

                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                     应用层 - Agent Orchestrator (Virtual Threads)                │
├─────────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                    Agent Orchestrator (智能体编排器)                      │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │   │
│  │  │ Writer      │  │Choreographer│  │Psychologist │  │ Consistency │     │   │
│  │  │ Agent       │  │ Agent       │  │ Agent       │  │ Agent       │     │   │
│  │  │ (执笔)      │  │ (动作指导)   │  │ (心理分析)   │  │ (一致性检查) │     │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘     │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐ │
│  │ RAGService  │  │ Extraction  │  │ Evolution   │  │ UsageAppService         │ │
│  │ (检索增强)   │  │ Service     │  │ Service     │  │ (用量统计)              │ │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              领域层 (Domain Layer)                               │
├─────────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │                    小说创作限界上下文 (Novel Context)                     │    │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────────┐   │    │
│  │  │ Project │  │ Volume  │  │ Chapter │  │ Story   │  │ PlotLoop    │   │    │
│  │  │ 聚合根   │  │ 实体    │  │ 实体    │  │ Block   │  │ 聚合根       │   │    │
│  │  └─────────┘  └─────────┘  └─────────┘  └─────────┘  └─────────────┘   │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │                    角色管理限界上下文 (Character Context)                 │    │
│  │  ┌─────────┐  ┌─────────────┐  ┌─────────────────┐                      │    │
│  │  │Character│  │ Relationship│  │ CharacterState  │  ┌─────────────┐   │    │
│  │  │ 聚合根   │  │ 值对象      │  │ 值对象           │  │ Evolution   │   │    │
│  │  └─────────┘  └─────────────┘  └─────────────────┘  │ 演进记录     │   │    │
│  │                                                      └─────────────┘   │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │                    知识库限界上下文 (Knowledge Context)                   │    │
│  │  ┌─────────┐  ┌─────────────┐  ┌─────────────────┐                      │    │
│  │  │WikiEntry│  │ Embedding   │  │ KnowledgeChunk  │                      │    │
│  │  │ 聚合根   │  │ 值对象      │  │ 实体             │                      │    │
│  │  └─────────┘  └─────────────┘  └─────────────────┘                      │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │                    AI桥接限界上下文 (AI Bridge Context)                   │    │
│  │  ┌─────────┐  ┌─────────────┐  ┌─────────────────┐  ┌───────────────┐   │    │
│  │  │ChatModel│  │ Tool        │  │ DomainAdapter   │  │ ChatMemory    │   │    │
│  │  │ Factory │  │ Registry    │  │ (领域适配器)     │  │ (对话记忆)     │   │    │
│  │  └─────────┘  └─────────────┘  └─────────────────┘  └───────────────┘   │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                     基础设施层 - Active Knowledge Graph                          │
├─────────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐ │
│  │ PostgreSQL  │  │ Redis Stack │  │ Apache      │  │ External AI Providers   │ │
│  │ 18 + HNSW   │  │ 7.4+ Cache  │  │ Pulsar      │  │ DeepSeek/GPT/Local      │ │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────────────────┘ │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐ │
│  │ CDC Adapter │  │ Flyway      │  │ Security    │  │ Micrometer Tracing      │ │
│  │ (主动触发)   │  │ Migrations  │  │ Passkey/JWT │  │ (全链路追踪)            │ │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### DDD限界上下文划分

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              限界上下文映射图                                     │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│   ┌───────────────────┐         ┌───────────────────┐                          │
│   │   用户认证上下文    │         │   项目管理上下文    │                          │
│   │   (Identity)      │◄───────►│   (Project)       │                          │
│   │                   │  ACL    │                   │                          │
│   │ • User            │         │ • Project (聚合根) │                          │
│   │ • Credential      │         │ • Volume          │                          │
│   │ • Token           │         │ • Chapter         │                          │
│   └───────────────────┘         │ • StoryBlock      │                          │
│                                 └─────────┬─────────┘                          │
│                                           │                                    │
│                              ┌────────────┼────────────┐                       │
│                              │            │            │                       │
│                              ▼            ▼            ▼                       │
│   ┌───────────────────┐  ┌───────────────────┐  ┌───────────────────┐         │
│   │   角色管理上下文    │  │   知识库上下文      │  │   伏笔追踪上下文    │         │
│   │   (Character)     │  │   (Knowledge)     │  │   (PlotLoop)      │         │
│   │                   │  │                   │  │                   │         │
│   │ • Character(聚合根)│  │ • WikiEntry(聚合根)│  │ • PlotLoop(聚合根) │         │
│   │ • Relationship    │  │ • KnowledgeChunk  │  │ • Resolution      │         │
│   │ • Archetype       │  │ • Embedding       │  │                   │         │
│   └─────────┬─────────┘  └─────────┬─────────┘  └─────────┬─────────┘         │
│             │                      │                      │                    │
│             └──────────────────────┼──────────────────────┘                    │
│                                    │                                           │
│                                    ▼                                           │
│                        ┌───────────────────┐                                   │
│                        │   AI桥接上下文     │                                   │
│                        │   (AI Bridge)     │                                   │
│                        │                   │                                   │
│                        │ • ChatService     │                                   │
│                        │ • ToolRegistry    │                                   │
│                        │ • DomainAdapter   │                                   │
│                        │ • RAGService      │                                   │
│                        │ • ChatMemory      │                                   │
│                        └───────────────────┘                                   │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### 1. 项目管理模块 (Project Bounded Context)

#### 包结构
```
com.inkflow.domain.project/
├── aggregate/
│   └── Project.java              # 聚合根
├── entity/
│   ├── Volume.java               # 分卷实体
│   ├── Chapter.java              # 章节实体
│   └── StoryBlock.java           # 剧情块实体
├── valueobject/
│   ├── ProjectMetadata.java      # 项目元数据
│   └── ContentHash.java          # 内容哈希
├── repository/
│   ├── ProjectRepository.java    # 项目仓储接口
│   └── StoryBlockRepository.java # 剧情块仓储接口
├── service/
│   ├── ProjectDomainService.java # 领域服务
│   └── ExportImportService.java  # 导入导出服务
└── event/
    ├── ProjectCreatedEvent.java  # 项目创建事件
    └── ContentUpdatedEvent.java  # 内容更新事件
```

#### 核心接口

```java
// 项目仓储接口
public interface ProjectRepository {
    Project save(Project project);
    Optional<Project> findById(UUID id);
    List<Project> findByUserId(UUID userId);
    void deleteById(UUID id);
}

// 导入导出服务接口
public interface ExportImportService {
    String exportToJson(UUID projectId);
    String prettyPrint(String json);
    Project importFromJson(String json, UUID userId);
}
```

### 2. 角色管理模块 (Character Bounded Context)

#### 包结构
```
com.inkflow.domain.character/
├── aggregate/
│   └── Character.java            # 聚合根
├── entity/
│   ├── CharacterArchetype.java   # 角色原型
│   └── CharacterEvolution.java   # 角色演进记录
├── valueobject/
│   ├── Relationship.java         # 关系值对象
│   ├── CharacterState.java       # 角色状态
│   ├── PersonalityTraits.java    # 性格特征
│   └── EvolutionSnapshot.java    # 演进快照
├── repository/
│   ├── CharacterRepository.java  # 角色仓储接口
│   └── EvolutionRepository.java  # 演进记录仓储
├── service/
│   ├── CharacterDomainService.java
│   ├── RelationshipGraphService.java
│   └── EvolutionTrackingService.java  # 演进追踪服务
└── event/
    ├── CharacterUpdatedEvent.java
    └── CharacterEvolvedEvent.java     # 角色演进事件
```

### 2.1 动态演进模块 (Evolution Bounded Context)

#### 设计理念

动态演进模块是AI原生小说创作平台的核心创新，它解决了长篇小说创作中角色和设定随剧情发展而变化的问题。传统写作工具只能记录静态设定，而本模块实现了：

1. **时间切片**: 记录角色/设定在不同章节时的状态
2. **自动追踪**: AI分析章节内容，自动提取状态变化
3. **一致性保障**: 生成内容时自动引用正确时间点的设定
4. **演进可视化**: 展示角色/设定的变化轨迹

#### 包结构
```
com.inkflow.domain.evolution/
├── aggregate/
│   └── EvolutionTimeline.java    # 演进时间线聚合根
├── entity/
│   ├── StateSnapshot.java        # 状态快照
│   ├── EvolutionEvent.java       # 演进事件
│   └── ChangeRecord.java         # 变更记录
├── valueobject/
│   ├── TimePoint.java            # 时间点（章节位置）
│   ├── StateChange.java          # 状态变更
│   └── EvolutionType.java        # 演进类型枚举
├── repository/
│   ├── EvolutionTimelineRepository.java
│   └── StateSnapshotRepository.java
├── service/
│   ├── EvolutionAnalysisService.java   # AI驱动的演进分析
│   ├── StateRetrievalService.java      # 状态检索服务
│   └── ConsistencyCheckService.java    # 一致性检查服务
└── event/
    ├── StateChangedEvent.java
    └── InconsistencyDetectedEvent.java
```

#### 核心接口

```java
// 演进分析服务接口
public interface EvolutionAnalysisService {
    /**
     * 分析章节内容，提取角色状态变化
     */
    List<StateChange> analyzeChapterForEvolution(UUID chapterId);
    
    /**
     * 获取角色在指定章节时的状态快照
     */
    CharacterState getCharacterStateAtChapter(UUID characterId, UUID chapterId);
    
    /**
     * 获取角色的完整演进时间线
     */
    EvolutionTimeline getCharacterEvolutionTimeline(UUID characterId);
    
    /**
     * 检测设定一致性问题
     */
    List<InconsistencyReport> checkConsistency(UUID projectId, UUID chapterId);
}

// 状态检索服务接口
public interface StateRetrievalService {
    /**
     * 获取实体在指定时间点的状态
     */
    <T> T getStateAtTimePoint(UUID entityId, Class<T> entityType, TimePoint timePoint);
    
    /**
     * 获取实体的所有状态快照
     */
    List<StateSnapshot> getAllSnapshots(UUID entityId);
}
```

#### 数据模型扩展

```sql
-- 演进时间线表
CREATE TABLE evolution_timelines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    entity_type VARCHAR(50) NOT NULL,  -- 'character', 'wiki_entry', 'relationship'
    entity_id UUID NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(entity_type, entity_id)
);

-- 状态快照表 (支持关键帧+增量策略，减少存储膨胀)
CREATE TABLE state_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    timeline_id UUID NOT NULL REFERENCES evolution_timelines(id) ON DELETE CASCADE,
    chapter_id UUID NOT NULL REFERENCES chapters(id),
    chapter_order INTEGER NOT NULL,  -- 用于快速排序
    is_keyframe BOOLEAN DEFAULT false,  -- 是否为关键帧（全量快照）
    state_data JSONB NOT NULL,       -- 关键帧存完整状态，增量帧存JSON diff
    change_summary TEXT,             -- 变更摘要
    change_type VARCHAR(50),         -- 'initial', 'update', 'major_change'
    ai_confidence DECIMAL(3, 2),     -- AI分析置信度
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 关键帧索引，用于快速定位最近的关键帧
CREATE INDEX idx_state_snapshots_keyframe ON state_snapshots(timeline_id, chapter_order) WHERE is_keyframe = true;

-- 变更记录表
CREATE TABLE change_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id UUID NOT NULL REFERENCES state_snapshots(id) ON DELETE CASCADE,
    field_path VARCHAR(255) NOT NULL,  -- 变更字段路径，如 'status', 'relationships[0].type'
    old_value TEXT,
    new_value TEXT,
    change_reason TEXT,                -- AI分析的变更原因
    source_text TEXT,                  -- 触发变更的原文引用
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX idx_evolution_timelines_entity ON evolution_timelines(entity_type, entity_id);
CREATE INDEX idx_state_snapshots_chapter ON state_snapshots(timeline_id, chapter_order);
CREATE INDEX idx_state_snapshots_timeline ON state_snapshots(timeline_id, created_at);
```

#### 演进分析流程

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              动态演进分析流程                                     │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐   │
│  │ 章节内容     │────►│ AI分析引擎   │────►│ 变更检测     │────►│ 快照生成     │   │
│  │ 完成/更新    │     │ (Spring AI) │     │             │     │             │   │
│  └─────────────┘     └─────────────┘     └─────────────┘     └─────────────┘   │
│                             │                   │                   │          │
│                             ▼                   ▼                   ▼          │
│                      ┌─────────────┐     ┌─────────────┐     ┌─────────────┐   │
│                      │ 提取角色状态 │     │ 对比前一快照 │     │ 存储新快照   │   │
│                      │ 提取关系变化 │     │ 识别差异字段 │     │ 记录变更历史 │   │
│                      │ 提取设定更新 │     │ 生成变更摘要 │     │ 触发事件     │   │
│                      └─────────────┘     └─────────────┘     └─────────────┘   │
│                                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                         一致性检查流程                                    │   │
│  │                                                                         │   │
│  │  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐               │   │
│  │  │ 获取当前章节 │────►│ 检索相关快照 │────►│ 对比检测     │               │   │
│  │  │ 引用的实体   │     │ (时间点匹配) │     │ 不一致项     │               │   │
│  │  └─────────────┘     └─────────────┘     └─────────────┘               │   │
│  │                                                │                        │   │
│  │                                                ▼                        │   │
│  │                                         ┌─────────────┐                │   │
│  │                                         │ 生成报告     │                │   │
│  │                                         │ 提供修复建议 │                │   │
│  │                                         └─────────────┘                │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 3. 知识库模块 (Knowledge Bounded Context)

#### 包结构
```
com.inkflow.domain.knowledge/
├── aggregate/
│   └── WikiEntry.java            # 聚合根
├── entity/
│   └── KnowledgeChunk.java       # 知识块实体
├── valueobject/
│   ├── Embedding.java            # 向量嵌入
│   ├── Alias.java                # 别名
│   └── EntryType.java            # 条目类型枚举
├── repository/
│   ├── WikiEntryRepository.java
│   └── EmbeddingRepository.java
├── service/
│   ├── WikiDomainService.java
│   └── SemanticSearchService.java
└── event/
    └── WikiEntryChangedEvent.java
```

### 4. AI桥接模块 (AI Bridge Bounded Context)

#### 包结构
```
com.inkflow.domain.aibridge/
├── chat/
│   ├── SpringAIChatService.java  # 核心聊天服务
│   ├── DynamicChatModelFactory.java
│   └── ChatMemoryFactory.java
├── orchestration/                     # 智能体编排层 (Java 22 CompletableFuture + Virtual Threads)
│   ├── AgentOrchestrator.java         # 智能体编排器
│   ├── SceneCreationOrchestrator.java # 场景创作编排
│   ├── agent/
│   │   ├── WriterAgent.java           # 执笔Agent
│   │   ├── ChoreographerAgent.java    # 动作指导Agent
│   │   ├── PsychologistAgent.java     # 心理分析Agent
│   │   └── ConsistencyAgent.java      # 一致性检查Agent
│   └── event/
│       └── AgentThoughtEvent.java     # Agent思考过程事件
├── tool/
│   ├── SceneToolRegistry.java    # 场景工具注册
│   ├── UniversalCrudTool.java    # 通用CRUD工具
│   ├── RAGSearchTool.java        # RAG搜索工具
│   ├── PreflightTool.java        # 预检工具
│   └── ContentExtractionTool.java # 内容逆向解析工具
├── adapter/
│   ├── DomainAdapter.java        # 领域适配器接口
│   ├── ProjectDomainAdapter.java
│   ├── CharacterDomainAdapter.java
│   └── WikiEntryDomainAdapter.java
├── memory/
│   ├── PersistentChatMemory.java
│   └── MessageWindowChatMemory.java
├── rag/
│   ├── SemanticChunkingService.java
│   ├── ParentChildSearchService.java
│   ├── EmbeddingCacheService.java
│   └── VersionedEmbeddingService.java  # 版本化嵌入服务
├── extraction/
│   ├── ContentExtractionService.java   # 内容逆向解析服务
│   ├── EntityDeduplicationService.java # 实体去重服务
│   └── RelationshipInferenceService.java # 关系推断服务
├── cdc/                                # CDC主动触发层
│   ├── WikiChangeListener.java         # Wiki变更监听器
│   └── ConsistencyCheckTrigger.java    # 一致性检查触发器
└── event/
    ├── ToolExecutionEvent.java
    └── ToolStatusEvent.java
```

#### 智能体编排核心设计 (Java 22 CompletableFuture + Virtual Threads)

```java
@Service
public class SceneCreationOrchestrator {
    
    private final ChoreographerAgent choreographerAgent;
    private final PsychologistAgent psychologistAgent;
    private final WriterAgent writerAgent;
    private final ApplicationEventPublisher eventPublisher;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public Flux<SceneChunk> createScene(SceneRequest request) {
        // 使用 CompletableFuture + Virtual Threads 并行启动多个Agent
        var choreographyFuture = CompletableFuture.supplyAsync(() -> {
            eventPublisher.publishEvent(new AgentThoughtEvent("choreographer", "分析动作设定..."));
            return choreographerAgent.designMoves(request);
        }, executor);
        
        var psychologyFuture = CompletableFuture.supplyAsync(() -> {
            eventPublisher.publishEvent(new AgentThoughtEvent("psychologist", "分析角色心理状态..."));
            return psychologistAgent.analyzeState(request);
        }, executor);

        // 等待所有任务完成
        CompletableFuture.allOf(choreographyFuture, psychologyFuture).join();

        // 综合结果交给Writer Agent
        var enrichedContext = new SceneContext(
            request, 
            choreographyFuture.join(), 
            psychologyFuture.join()
        );
        
        return writerAgent.streamContent(enrichedContext);
    }
}
```



#### 核心接口

```java
// 领域适配器接口
public interface DomainAdapter<T> {
    String getEntityType();
    T create(Map<String, Object> params);
    T update(UUID id, Map<String, Object> params);
    Optional<T> findById(UUID id);
    List<T> findByProjectId(UUID projectId);
    void delete(UUID id);
}

// 场景工具注册接口
public interface SceneToolRegistry {
    Object[] getToolsArrayForScene(CreationPhase phase);
    void registerTool(String name, Object tool, Set<CreationPhase> phases);
}
```

## Data Models

### 核心实体ER图

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              数据模型关系图                                       │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌─────────────┐       ┌─────────────┐       ┌─────────────┐                   │
│  │    User     │       │   Project   │       │   Volume    │                   │
│  ├─────────────┤       ├─────────────┤       ├─────────────┤                   │
│  │ id: UUID    │──1:N──│ id: UUID    │──1:N──│ id: UUID    │                   │
│  │ email       │       │ user_id     │       │ project_id  │                   │
│  │ password    │       │ title       │       │ title       │                   │
│  │ created_at  │       │ genre       │       │ order_index │                   │
│  └─────────────┘       │ phase       │       │ created_at  │                   │
│                        │ metadata    │       └──────┬──────┘                   │
│                        │ created_at  │              │                          │
│                        └──────┬──────┘              │                          │
│                               │                     │                          │
│         ┌─────────────────────┼─────────────────────┤                          │
│         │                     │                     │                          │
│         ▼                     ▼                     ▼                          │
│  ┌─────────────┐       ┌─────────────┐       ┌─────────────┐                   │
│  │  Character  │       │  WikiEntry  │       │   Chapter   │                   │
│  ├─────────────┤       ├─────────────┤       ├─────────────┤                   │
│  │ id: UUID    │       │ id: UUID    │       │ id: UUID    │                   │
│  │ project_id  │       │ project_id  │       │ volume_id   │                   │
│  │ name        │       │ title       │       │ title       │                   │
│  │ role        │       │ type        │       │ order_index │                   │
│  │ description │       │ content     │       │ summary     │                   │
│  │ personality │       │ aliases     │       │ created_at  │                   │
│  │ relationships│      │ created_at  │       └──────┬──────┘                   │
│  │ status      │       └──────┬──────┘              │                          │
│  │ is_active   │              │                     │                          │
│  └─────────────┘              │                     ▼                          │
│                               │              ┌─────────────┐                   │
│                               │              │ StoryBlock  │                   │
│                               │              ├─────────────┤                   │
│                               │              │ id: UUID    │                   │
│                               │              │ chapter_id  │                   │
│                               │              │ project_id  │                   │
│                               │              │ title       │                   │
│                               │              │ content     │                   │
│                               │              │ order_index │                   │
│                               │              │ status      │                   │
│                               │              │ word_count  │                   │
│                               │              │ is_dirty    │                   │
│                               │              │ content_hash│                   │
│                               │              └──────┬──────┘                   │
│                               │                     │                          │
│                               ▼                     ▼                          │
│                        ┌─────────────────────────────────┐                     │
│                        │        KnowledgeChunk           │                     │
│                        ├─────────────────────────────────┤                     │
│                        │ id: UUID                        │                     │
│                        │ project_id: UUID                │                     │
│                        │ source_type: VARCHAR            │                     │
│                        │ source_id: UUID                 │                     │
│                        │ parent_id: UUID (nullable)      │                     │
│                        │ content: TEXT                   │                     │
│                        │ embedding: VECTOR(1536)         │                     │
│                        │ chunk_level: VARCHAR            │                     │
│                        │ chunk_order: INTEGER            │                     │
│                        │ created_at: TIMESTAMP           │                     │
│                        └─────────────────────────────────┘                     │
│                                                                                 │
│  ┌─────────────┐       ┌─────────────────────┐       ┌─────────────────────┐   │
│  │  PlotLoop   │       │ ConversationHistory │       │   TokenUsageRecord  │   │
│  ├─────────────┤       ├─────────────────────┤       ├─────────────────────┤   │
│  │ id: UUID    │       │ id: UUID            │       │ id: UUID            │   │
│  │ project_id  │       │ user_id             │       │ user_id             │   │
│  │ title       │       │ project_id          │       │ project_id          │   │
│  │ description │       │ role                │       │ model               │   │
│  │ status      │       │ content             │       │ input_tokens        │   │
│  │ intro_chap  │       │ phase               │       │ output_tokens       │   │
│  │ resolve_chap│       │ created_at          │       │ cost                │   │
│  │ created_at  │       └─────────────────────┘       │ created_at          │   │
│  └─────────────┘                                     └─────────────────────┘   │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 数据库表定义

```sql
-- 用户表
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    encrypted_api_keys JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 项目表
CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    genre VARCHAR(100),
    description TEXT,
    creation_phase VARCHAR(50) DEFAULT 'WELCOME',
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

-- 分卷表
CREATE TABLE volumes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    order_index INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(project_id, order_index)
);

-- 章节表
CREATE TABLE chapters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    volume_id UUID NOT NULL REFERENCES volumes(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    summary TEXT,
    order_index INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(volume_id, order_index)
);

-- 剧情块表 (使用Lexorank字符串排序，支持O(1)插入)
CREATE TABLE story_blocks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chapter_id UUID NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    content TEXT DEFAULT '',
    rank VARCHAR(255) NOT NULL,  -- Lexorank排序字符串，如 '0|hzzzzz:'
    status VARCHAR(20) DEFAULT 'placeholder',
    word_count INTEGER DEFAULT 0,
    context_entity_ids JSONB DEFAULT '[]',
    is_dirty BOOLEAN DEFAULT true,
    content_hash VARCHAR(64),
    last_chunked_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Lexorank索引，支持高效排序查询
CREATE INDEX idx_story_blocks_rank ON story_blocks(chapter_id, rank);

-- 角色表
CREATE TABLE characters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    role VARCHAR(100),
    gender VARCHAR(20),
    age VARCHAR(50),
    description TEXT,
    appearance TEXT,
    background TEXT,
    personality TEXT,
    speaking_style TEXT,
    motivation TEXT,
    fears TEXT,
    narrative_function TEXT,
    relationships JSONB DEFAULT '[]',
    status VARCHAR(50),
    tags JSONB DEFAULT '[]',
    is_active BOOLEAN DEFAULT true,
    introduced_in_volume_id UUID,
    introduced_in_chapter_id UUID,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 知识条目表
CREATE TABLE wiki_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    content TEXT,
    aliases JSONB DEFAULT '[]',
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 知识块表（RAG父子索引，支持版本号防止脏数据）
CREATE TABLE knowledge_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    source_type VARCHAR(50) NOT NULL,
    source_id UUID NOT NULL,
    parent_id UUID REFERENCES knowledge_chunks(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    embedding VECTOR(1536),
    chunk_level VARCHAR(20) DEFAULT 'child',
    chunk_order INTEGER,
    version INTEGER DEFAULT 1,           -- 版本号，用于脏数据过滤
    is_active BOOLEAN DEFAULT true,      -- 是否为当前有效版本
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 活跃版本索引，RAG检索时只查询活跃版本
CREATE INDEX idx_knowledge_chunks_active ON knowledge_chunks(project_id, is_active) WHERE is_active = true;

-- 伏笔表
CREATE TABLE plot_loops (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(20) DEFAULT 'OPEN',
    introduced_in_chapter_id UUID REFERENCES chapters(id),
    resolved_in_chapter_id UUID REFERENCES chapters(id),
    abandon_reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 对话历史表
CREATE TABLE conversation_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    phase VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Token使用记录表
CREATE TABLE token_usage_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    project_id UUID REFERENCES projects(id) ON DELETE SET NULL,
    operation VARCHAR(100) NOT NULL,
    model VARCHAR(100) NOT NULL,
    input_tokens INTEGER NOT NULL,
    output_tokens INTEGER NOT NULL,
    cost DECIMAL(10, 6),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 向量索引 (使用HNSW替代IVFFlat，提供更好的召回率和查询性能)
CREATE INDEX idx_knowledge_chunks_embedding 
ON knowledge_chunks USING hnsw (embedding vector_cosine_ops) 
WITH (m = 16, ef_construction = 64);

-- 其他索引
CREATE INDEX idx_projects_user_id ON projects(user_id);
CREATE INDEX idx_story_blocks_dirty ON story_blocks(project_id, is_dirty) WHERE is_dirty = true;
CREATE INDEX idx_characters_project_id ON characters(project_id);
CREATE INDEX idx_wiki_entries_project_id ON wiki_entries(project_id);
CREATE INDEX idx_plot_loops_status ON plot_loops(project_id, status);
CREATE INDEX idx_conversation_history_lookup ON conversation_history(user_id, project_id, created_at DESC);
CREATE INDEX idx_token_usage_user_date ON token_usage_records(user_id, created_at);
```

## Key Design Decisions

### 1. 动态演进架构决策

**问题**: 长篇小说中角色和设定会随剧情发展而变化，传统系统只能记录静态设定。

**解决方案**: 引入时间切片机制，为每个实体维护演进时间线。

**实现要点**:
- 每个章节完成后触发AI分析，提取状态变化
- 使用JSONB存储完整状态快照，支持任意字段变更
- 生成内容时自动检索对应时间点的状态

### 2. RAG父子索引策略

**问题**: 传统RAG检索面临粒度困境——小块精准但上下文不完整，大块完整但精度不足。

**解决方案**: 采用"小块检索，大块返回"策略。

**实现要点**:
- 子块用于向量检索，保证精准匹配
- 父块（StoryBlock）用于返回，保证上下文完整
- 语义断崖检测算法智能切分子块

### 3. 创作阶段状态机

**问题**: 不同创作阶段需要不同的AI工具和提示词。

**解决方案**: 实现创作阶段状态机，动态调整AI行为。

**状态转换图**:
```
WELCOME → INITIALIZATION → WORLD_BUILDING → CHARACTER_CREATION → PLOTTING → DRAFTING → MAINTENANCE
                                                                              ↑______________|
```

### 4. Spring AI Tool集成

**问题**: AI需要安全地访问和修改领域数据。

**解决方案**: 通过DomainAdapter模式将领域服务暴露为Spring AI Tool。

**实现要点**:
- 每个聚合根对应一个DomainAdapter
- SceneToolRegistry根据创作阶段动态注册工具
- ToolExecutionAspect记录所有工具调用

### 5. 多级缓存架构

**问题**: 向量生成和AI调用成本高、延迟大。

**解决方案**: 实现L1(Caffeine) + L2(Redis)多级缓存。

**缓存策略**:
- L1: 本地缓存，纳秒级响应，容量有限
- L2: Redis缓存，毫秒级响应，支持分布式
- 写穿透: 更新时同时更新两级缓存
- 懒加载: 缓存未命中时才调用源API

### 6. HNSW向量索引（替代IVFFlat）

**问题**: IVFFlat索引在高维向量检索时召回率不稳定，且需要预先训练。

**解决方案**: 使用HNSW（Hierarchical Navigable Small World）索引。

**优势**:
- 更高的召回率（通常>95%）
- 无需预训练，支持增量插入
- 查询性能更稳定
- 参数配置: m=16, ef_construction=64

### 7. Lexorank排序算法（StoryBlock）

**问题**: 传统整数order_index在中间插入时需要更新后续所有记录。

**解决方案**: 使用Lexorank字符串排序算法。

**实现要点**:
- 排序字段使用VARCHAR存储字典序字符串
- 插入时计算两个相邻rank之间的中间值
- O(1)插入开销，无需更新其他记录
- 支持多人协作场景下的并发插入

**示例**:
```
原有: "0|hzzzzz:" 和 "0|i00000:"
插入: "0|hzzzzz:i" (在两者之间)
```

### 8. 状态快照关键帧+增量策略

**问题**: 每章都存储完整状态快照会导致数据库膨胀（50角色×1000章=50000条大JSON）。

**解决方案**: 采用视频编码思想的关键帧+增量策略。

**实现要点**:
- 每10章存储一个全量关键帧（is_keyframe=true）
- 中间章节只存储JSON diff（变更字段）
- 查询时从最近关键帧开始，依次应用增量
- 后台任务定期压缩旧增量为新关键帧

**存储节省**: 预计减少70-80%的存储空间

### 9. RAG脏数据版本控制

**问题**: 内容更新后，旧embedding未删除、新embedding未生成期间，检索可能返回过时内容。

**解决方案**: 引入版本号机制实现最终一致性。

**实现要点**:
- 每个chunk增加version字段和is_active标记
- 更新时：创建新版本chunk，保留旧版本
- 新embedding生成完成后：原子切换is_active
- 检索时：只查询is_active=true的记录
- 定期清理：删除非活跃的旧版本

### 10. 内容逆向解析（从正文反推设定）

**问题**: 迁移老书用户需要手动录入所有角色和设定，工作量巨大。

**解决方案**: AI驱动的内容逆向解析服务。

**实现要点**:
- 分块处理：超过3000字的文本分块处理
- 实体提取：AI提取角色、地点、物品、事件
- 关系推断：分析角色互动，生成关系图谱
- 去重合并：检测重复实体，提供合并建议
- 预览确认：展示提取结果，用户确认后入库

## Error Handling

### 错误处理策略

#### 1. 全局异常处理

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(404)
            .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }
    
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
        return ResponseEntity.status(400)
            .body(new ErrorResponse("VALIDATION_ERROR", ex.getMessage()));
    }
    
    @ExceptionHandler(AIServiceException.class)
    public ResponseEntity<ErrorResponse> handleAIError(AIServiceException ex) {
        log.error("AI服务错误: {}", ex.getMessage(), ex);
        return ResponseEntity.status(503)
            .body(new ErrorResponse("AI_SERVICE_ERROR", "AI服务暂时不可用，请稍后重试"));
    }
    
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException ex) {
        return ResponseEntity.status(429)
            .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
            .body(new ErrorResponse("RATE_LIMIT_EXCEEDED", ex.getMessage()));
    }
}
```

#### 2. AI服务错误处理

```java
@Service
public class AIErrorHandler {
    
    public Mono<String> handleWithRetry(Supplier<Mono<String>> aiCall) {
        return Mono.defer(aiCall)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                .filter(this::isRetryable)
                .onRetryExhaustedThrow((spec, signal) -> 
                    new AIServiceException("AI服务重试次数已用尽")));
    }
    
    private boolean isRetryable(Throwable t) {
        return t instanceof TimeoutException 
            || t instanceof ServiceUnavailableException;
    }
}
```

#### 3. 错误码定义

| 错误码 | HTTP状态 | 描述 |
|--------|----------|------|
| AUTH_INVALID_CREDENTIALS | 401 | 无效的认证凭据 |
| AUTH_TOKEN_EXPIRED | 401 | Token已过期 |
| AUTH_INSUFFICIENT_PERMISSION | 403 | 权限不足 |
| RESOURCE_NOT_FOUND | 404 | 资源不存在 |
| VALIDATION_ERROR | 400 | 请求参数验证失败 |
| RATE_LIMIT_EXCEEDED | 429 | 请求频率超限 |
| AI_SERVICE_ERROR | 503 | AI服务不可用 |
| AI_QUOTA_EXCEEDED | 402 | AI配额已用尽 |
| INTERNAL_ERROR | 500 | 内部服务器错误 |

## Testing Strategy

### 测试框架选择

- **单元测试**: JUnit 5
- **集成测试**: Testcontainers
- **Mock**: Mockito + MockWebServer
- **API测试**: Spring WebTestClient

### 测试策略

#### 1. 单元测试
针对核心业务逻辑编写单元测试，重点覆盖：
- 领域服务的业务规则
- 值对象的不变性
- 状态机转换逻辑
- 工具类方法

```java
// 示例：导入导出测试
@Test
void exportImportShouldPreserveProjectData() {
    // Given
    Project project = createTestProject();
    
    // When
    String json = exportService.exportToJson(project.getId());
    Project imported = exportService.importFromJson(json, project.getUserId());
    
    // Then
    assertThat(imported.getTitle()).isEqualTo(project.getTitle());
    assertThat(imported.getVolumes()).hasSize(project.getVolumes().size());
}
```

#### 2. 集成测试
使用Testcontainers进行端到端集成测试：

```java
@SpringBootTest
@Testcontainers
class ProjectIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");
    
    @Test
    void shouldCreateAndRetrieveProject() {
        // 完整的API流程测试
    }
}
```

### 测试覆盖要求

| 测试类型 | 覆盖目标 | 说明 |
|----------|----------|------|
| 单元测试 | 核心业务逻辑 | 领域服务、值对象、工具类 |
| 集成测试 | API端点 | 使用Testcontainers |
| 性能测试 | 关键路径 | 缓存命中率、响应时间 |

### 测试命名规范

```java
// 方法命名: should_期望结果_when_条件
@Test
void should_ReturnEmptyList_When_ProjectHasNoVolumes() { ... }

@Test
void should_ThrowException_When_InvalidPhaseTransition() { ... }
```


## Data Flow Analysis

### 1. 用户交互视角的数据流

#### 1.1 创作流程数据流

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         用户创作流程数据流                                        │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌─────────────┐                                                               │
│  │ 1. 新建项目  │                                                               │
│  │ 用户输入:    │                                                               │
│  │ - 标题      │                                                               │
│  │ - 类型      │                                                               │
│  │ - 简介      │                                                               │
│  └──────┬──────┘                                                               │
│         │                                                                       │
│         ▼                                                                       │
│  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐                       │
│  │ 2. 世界观   │────►│ 3. 角色创建  │────►│ 4. 大纲规划  │                       │
│  │ 构建        │     │             │     │             │                       │
│  │             │     │ 输出:       │     │ 输出:       │                       │
│  │ 输出:       │     │ - Character │     │ - Volume    │                       │
│  │ - WikiEntry │     │ - Relation  │     │ - Chapter   │                       │
│  │ (设定条目)  │     │ - Archetype │     │ - PlotLoop  │                       │
│  └─────────────┘     └─────────────┘     └──────┬──────┘                       │
│                                                 │                              │
│                                                 ▼                              │
│                                          ┌─────────────┐                       │
│                                          │ 5. 内容创作  │                       │
│                                          │             │                       │
│                                          │ 输出:       │                       │
│                                          │ - StoryBlock│                       │
│                                          │ - Evolution │                       │
│                                          │   Snapshot  │                       │
│                                          └─────────────┘                       │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

#### 1.2 界面展示所需数据结构

| 界面组件 | 所需数据 | 数据来源 | 更新频率 |
|----------|----------|----------|----------|
| 项目列表 | Project[] | ProjectRepository | 低 |
| 大纲树 | Volume[] → Chapter[] → StoryBlock[] | 级联查询 | 中 |
| 角色卡片 | Character + Relationships | CharacterRepository | 中 |
| 关系图谱 | Character[] + Relationship[][] | GraphService | 低 |
| 知识库面板 | WikiEntry[] | WikiEntryRepository | 中 |
| 伏笔追踪 | PlotLoop[] (按状态分组) | PlotLoopRepository | 中 |
| 编辑器 | StoryBlock.content | StoryBlockRepository | 高 |
| 聊天面板 | ConversationHistory[] | ConversationHistoryRepository | 高 |
| 演进时间线 | EvolutionTimeline + StateSnapshot[] | EvolutionRepository | 低 |
| Token统计 | TokenUsageRecord[] (聚合) | TokenUsageRepository | 低 |

#### 1.3 界面数据结构补充

```sql
-- 补充：项目统计视图（用于仪表盘展示）
CREATE VIEW project_statistics AS
SELECT 
    p.id AS project_id,
    p.title,
    p.creation_phase,
    COUNT(DISTINCT v.id) AS volume_count,
    COUNT(DISTINCT c.id) AS chapter_count,
    COUNT(DISTINCT sb.id) AS story_block_count,
    COALESCE(SUM(sb.word_count), 0) AS total_word_count,
    COUNT(DISTINCT ch.id) AS character_count,
    COUNT(DISTINCT we.id) AS wiki_entry_count,
    COUNT(DISTINCT pl.id) FILTER (WHERE pl.status = 'OPEN') AS open_plot_loops,
    COUNT(DISTINCT pl.id) FILTER (WHERE pl.status = 'URGENT') AS urgent_plot_loops
FROM projects p
LEFT JOIN volumes v ON v.project_id = p.id
LEFT JOIN chapters c ON c.volume_id = v.id
LEFT JOIN story_blocks sb ON sb.chapter_id = c.id
LEFT JOIN characters ch ON ch.project_id = p.id
LEFT JOIN wiki_entries we ON we.project_id = p.id
LEFT JOIN plot_loops pl ON pl.project_id = p.id
WHERE p.deleted_at IS NULL
GROUP BY p.id, p.title, p.creation_phase;

-- 补充：章节进度视图
CREATE VIEW chapter_progress AS
SELECT 
    c.id AS chapter_id,
    c.title,
    c.volume_id,
    COUNT(sb.id) AS total_blocks,
    COUNT(sb.id) FILTER (WHERE sb.status = 'completed') AS completed_blocks,
    COALESCE(SUM(sb.word_count), 0) AS word_count,
    ROUND(
        COUNT(sb.id) FILTER (WHERE sb.status = 'completed')::DECIMAL / 
        NULLIF(COUNT(sb.id), 0) * 100, 2
    ) AS completion_percentage
FROM chapters c
LEFT JOIN story_blocks sb ON sb.chapter_id = c.id
GROUP BY c.id, c.title, c.volume_id;
```

### 2. AI使用视角的数据流

#### 2.1 AI上下文构建流程

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         AI上下文构建数据流                                        │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  用户消息                                                                        │
│      │                                                                          │
│      ▼                                                                          │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                        上下文构建器                                       │   │
│  │                                                                         │   │
│  │  1. 基础上下文                                                           │   │
│  │     ├── 项目元数据 (Project.metadata)                                    │   │
│  │     ├── 当前创作阶段 (Project.creation_phase)                            │   │
│  │     └── 用户偏好设置                                                     │   │
│  │                                                                         │   │
│  │  2. RAG检索上下文                                                        │   │
│  │     ├── 相关WikiEntry (语义检索)                                         │   │
│  │     ├── 相关StoryBlock (父子索引检索)                                    │   │
│  │     └── 相关Character (实体关联)                                         │   │
│  │                                                                         │   │
│  │  3. 时间敏感上下文 (动态演进)                                             │   │
│  │     ├── 当前章节位置 (TimePoint)                                         │   │
│  │     ├── 角色当前状态 (StateSnapshot @ TimePoint)                         │   │
│  │     └── 设定当前版本 (WikiEntry @ TimePoint)                             │   │
│  │                                                                         │   │
│  │  4. 任务相关上下文                                                       │   │
│  │     ├── 待回收伏笔 (PlotLoop.status IN ['OPEN', 'URGENT'])              │   │
│  │     ├── 当前章节大纲 (Chapter.summary)                                   │   │
│  │     └── 前后文StoryBlock                                                 │   │
│  │                                                                         │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                 │
│      │                                                                          │
│      ▼                                                                          │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                        AI模型调用                                         │   │
│  │                                                                         │   │
│  │  输入:                                                                   │   │
│  │  - System Prompt (阶段相关)                                              │   │
│  │  - 构建的上下文                                                          │   │
│  │  - 用户消息                                                              │   │
│  │  - 可用Tools列表                                                         │   │
│  │                                                                         │   │
│  │  输出:                                                                   │   │
│  │  - AI响应文本                                                            │   │
│  │  - Tool调用请求                                                          │   │
│  │                                                                         │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

#### 2.2 AI Tool调用数据流

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         AI Tool调用数据流                                        │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  AI决定调用Tool                                                                  │
│      │                                                                          │
│      ▼                                                                          │
│  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐                       │
│  │ Tool请求    │────►│ 参数验证    │────►│ 权限检查    │                       │
│  │ - toolName  │     │ - 类型校验  │     │ - userId    │                       │
│  │ - arguments │     │ - 必填检查  │     │ - projectId │                       │
│  └─────────────┘     └─────────────┘     └──────┬──────┘                       │
│                                                 │                              │
│                                                 ▼                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                        DomainAdapter路由                                  │   │
│  │                                                                         │   │
│  │  UniversalCrudTool:                                                     │   │
│  │  ├── entityType: "character" → CharacterDomainAdapter                   │   │
│  │  ├── entityType: "wiki_entry" → WikiEntryDomainAdapter                  │   │
│  │  ├── entityType: "plot_loop" → PlotLoopDomainAdapter                    │   │
│  │  ├── entityType: "story_block" → StoryBlockDomainAdapter                │   │
│  │  └── entityType: "chapter" → ChapterDomainAdapter                       │   │
│  │                                                                         │   │
│  │  RAGSearchTool:                                                         │   │
│  │  └── → ParentChildSearchService                                         │   │
│  │                                                                         │   │
│  │  PreflightTool:                                                         │   │
│  │  └── → ConsistencyCheckService                                          │   │
│  │                                                                         │   │
│  │  EvolutionTool (新增):                                                   │   │
│  │  ├── getStateAtChapter → StateRetrievalService                          │   │
│  │  └── analyzeEvolution → EvolutionAnalysisService                        │   │
│  │                                                                         │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                 │
│      │                                                                          │
│      ▼                                                                          │
│  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐                       │
│  │ 执行结果    │────►│ 事件发布    │────►│ 返回AI      │                       │
│  │ - 成功/失败 │     │ - ToolExec  │     │ - 结构化    │                       │
│  │ - 数据      │     │   Event     │     │   响应      │                       │
│  └─────────────┘     └─────────────┘     └─────────────┘                       │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

#### 2.3 AI所需数据结构补充

```sql
-- 补充：AI上下文缓存表（减少重复构建）
CREATE TABLE ai_context_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    chapter_id UUID REFERENCES chapters(id) ON DELETE CASCADE,
    context_type VARCHAR(50) NOT NULL,  -- 'character_state', 'wiki_context', 'plot_context'
    context_data JSONB NOT NULL,
    valid_until TIMESTAMP NOT NULL,     -- 缓存过期时间
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ai_context_cache_lookup ON ai_context_cache(project_id, chapter_id, context_type);

-- 补充：Tool调用日志表（用于分析和调试）
CREATE TABLE tool_invocation_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    project_id UUID REFERENCES projects(id) ON DELETE SET NULL,
    request_id VARCHAR(100) NOT NULL,   -- 关联SSE流
    tool_name VARCHAR(100) NOT NULL,
    arguments JSONB,
    result JSONB,
    success BOOLEAN NOT NULL,
    error_message TEXT,
    duration_ms INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tool_logs_request ON tool_invocation_logs(request_id);
CREATE INDEX idx_tool_logs_user_time ON tool_invocation_logs(user_id, created_at DESC);
```

### 3. 数据顺序与一致性

#### 3.1 顺序约束

| 实体 | 顺序字段 | 约束规则 | 维护方式 |
|------|----------|----------|----------|
| Volume | order_index | 项目内唯一连续 | 插入时自动分配，删除时重排 |
| Chapter | order_index | 分卷内唯一连续 | 插入时自动分配，删除时重排 |
| StoryBlock | order_index | 章节内唯一连续 | 插入时自动分配，删除时重排 |
| StateSnapshot | chapter_order | 时间线内按章节顺序 | 基于chapter.order_index |
| ConversationHistory | created_at | 按时间顺序 | 自动时间戳 |
| KnowledgeChunk | chunk_order | 父块内子块顺序 | 切片时自动分配 |

#### 3.2 数据一致性保障

```java
// 顺序重排服务
@Service
@Transactional
public class OrderIndexService {
    
    /**
     * 重排子实体顺序
     * 保证：删除后无间隙，插入后连续
     */
    public void reorderAfterDelete(UUID parentId, Class<?> entityType) {
        List<?> children = repository.findByParentIdOrderByOrderIndex(parentId);
        for (int i = 0; i < children.size(); i++) {
            setOrderIndex(children.get(i), i + 1);
        }
        repository.saveAll(children);
    }
    
    /**
     * 移动实体到新位置
     * 保证：原子性操作，无重复索引
     */
    public void moveToPosition(UUID entityId, int newPosition) {
        // 使用临时负数索引避免唯一约束冲突
        // 1. 设置目标为-1
        // 2. 调整其他元素
        // 3. 设置目标为newPosition
    }
}
```

#### 3.3 演进数据时间线一致性

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         演进数据时间线一致性                                      │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  章节顺序:  Ch1 ──► Ch2 ──► Ch3 ──► Ch4 ──► Ch5 ──► Ch6                        │
│                                                                                 │
│  角色A演进:  [初始] ──────► [受伤] ──────────────► [康复]                        │
│              @Ch1          @Ch3                   @Ch6                          │
│                                                                                 │
│  查询规则:                                                                       │
│  - 查询Ch2时的状态 → 返回Ch1的快照（最近的前序快照）                              │
│  - 查询Ch4时的状态 → 返回Ch3的快照                                               │
│  - 查询Ch7时的状态 → 返回Ch6的快照（最新快照）                                   │
│                                                                                 │
│  SQL实现:                                                                        │
│  SELECT * FROM state_snapshots                                                  │
│  WHERE timeline_id = ? AND chapter_order <= ?                                   │
│  ORDER BY chapter_order DESC LIMIT 1;                                           │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 4. 数据结构完整性检查清单

#### 4.1 用户界面展示检查

- [x] 项目列表：Project表包含所有必要字段
- [x] 大纲树：Volume/Chapter/StoryBlock层级关系完整
- [x] 角色卡片：Character表包含展示所需的所有字段
- [x] 关系图谱：relationships JSONB字段支持图谱构建
- [x] 知识库：WikiEntry表支持分类和别名搜索
- [x] 伏笔追踪：PlotLoop表支持状态筛选
- [x] 编辑器：StoryBlock.content支持富文本
- [x] 聊天面板：ConversationHistory支持历史查询
- [x] 演进时间线：新增表结构支持时间切片展示
- [x] 统计仪表盘：新增视图支持聚合统计
- [x] 进度追踪：新增视图支持完成度计算

#### 4.2 AI使用检查

- [x] RAG检索：KnowledgeChunk表支持父子索引
- [x] 上下文构建：所有实体都有project_id关联
- [x] 时间敏感查询：StateSnapshot支持按章节顺序查询
- [x] Tool调用：所有聚合根都有对应的DomainAdapter
- [x] 调用日志：新增tool_invocation_logs表
- [x] 上下文缓存：新增ai_context_cache表
- [x] Token统计：TokenUsageRecord表完整

#### 4.3 数据流检查

- [x] 创建流程：Project → Volume → Chapter → StoryBlock
- [x] 演进流程：StoryBlock完成 → AI分析 → StateSnapshot
- [x] 检索流程：Query → KnowledgeChunk(child) → StoryBlock(parent)
- [x] 缓存流程：L1(Caffeine) → L2(Redis) → Source
