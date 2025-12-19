# Design Document: Consistency & Evolution Module Integration

## Overview

本设计文档描述如何将 InkFlow 中已实现的 `consistency` 和 `evolution` 模块真正集成到业务流程中。主要包括：

1. **数据库层**: 创建 PostgreSQL 触发器实现 CDC
2. **事件监听层**: 添加 Character/PlotLoop 变更监听器
3. **业务流程层**: 集成到 ContentGenerationWorkflow 和 ConsistencyAgent
4. **前端展示层**: 一致性警告面板和演进时间线

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Frontend (React)                                │
├─────────────────────────────────────────────────────────────────────────────┤
│  ConsistencyWarningPanel  │  EvolutionTimeline  │  SSE Event Handler        │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              REST API Layer                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│  ConsistencyController    │  EvolutionController  │  AgentController        │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Business Logic Layer                               │
├─────────────────────────────────────────────────────────────────────────────┤
│  ContentGenerationWorkflow ──► PreflightService                             │
│           │                         │                                        │
│           ▼                         ▼                                        │
│  ConsistencyAgent ──────► ProactiveConsistencyService                       │
│           │                         │                                        │
│           ▼                         ▼                                        │
│  EvolutionAnalysisService ◄── RuleCheckerService                            │
│           │                                                                  │
│           ▼                                                                  │
│  StateSnapshotService ──► ConsistencyWarningService                         │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Event Listener Layer                               │
├─────────────────────────────────────────────────────────────────────────────┤
│  CharacterChangeListener  │  PlotLoopChangeListener  │  ConsistencyCDCListener│
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Database Layer (PostgreSQL)                        │
├─────────────────────────────────────────────────────────────────────────────┤
│  TRIGGER: notify_character_change    │  TRIGGER: notify_wiki_change         │
│  TRIGGER: notify_plotloop_change     │  TRIGGER: notify_relationship_change │
│                                      │                                       │
│  CHANNEL: entity_changes             │                                       │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### 1. Database Triggers (PostgreSQL)

```sql
-- 通用通知函数
CREATE OR REPLACE FUNCTION notify_entity_change() RETURNS TRIGGER AS $$
BEGIN
  PERFORM pg_notify('entity_changes', json_build_object(
    'table', TG_TABLE_NAME,
    'operation', TG_OP,
    'id', COALESCE(NEW.id, OLD.id),
    'project_id', COALESCE(NEW.project_id, OLD.project_id)
  )::text);
  RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

-- 各实体触发器
CREATE TRIGGER trigger_character_change
AFTER INSERT OR UPDATE OR DELETE ON characters
FOR EACH ROW EXECUTE FUNCTION notify_entity_change();

CREATE TRIGGER trigger_wiki_entry_change
AFTER INSERT OR UPDATE OR DELETE ON wiki_entries
FOR EACH ROW EXECUTE FUNCTION notify_entity_change();

CREATE TRIGGER trigger_plot_loop_change
AFTER INSERT OR UPDATE OR DELETE ON plot_loops
FOR EACH ROW EXECUTE FUNCTION notify_entity_change();

CREATE TRIGGER trigger_character_relationship_change
AFTER INSERT OR UPDATE OR DELETE ON character_relationships
FOR EACH ROW EXECUTE FUNCTION notify_entity_change();
```

### 2. CharacterChangeListener

```java
@Component
public class CharacterChangeListener {
    private final ProactiveConsistencyService consistencyService;
    private final EvolutionAnalysisService evolutionService;
    private final ConsistencyWarningService warningService;
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCharacterChanged(CharacterChangedEvent event) {
        // 触发一致性检查
        consistencyService.triggerCheck(
            event.getProjectId(),
            event.getCharacterId(),
            EntityType.CHARACTER,
            event.getCharacterName()
        );
        
        // 创建演进快照
        if (event.getOperation() != Operation.DELETE) {
            evolutionService.createSnapshotForEntity(
                event.getProjectId(),
                event.getCharacterId(),
                EntityType.CHARACTER,
                event.getCurrentState()
            );
        } else {
            // 清理关联数据
            warningService.deleteWarningsByEntity(event.getCharacterId());
        }
    }
}
```

### 3. ContentGenerationWorkflow Integration

```java
@Component
public class ContentGenerationWorkflow extends AbstractChainWorkflow {
    private final PreflightService preflightService;
    private final ProactiveConsistencyService consistencyService;
    private final StateRetrievalService stateRetrievalService;
    
    @Override
    protected Flux<SSEEvent> executeWorkflow(WorkflowContext context) {
        return Flux.concat(
            // 1. 预检
            performPreflight(context),
            // 2. 获取角色状态
            enrichWithEvolutionState(context),
            // 3. 生成内容
            generateContent(context),
            // 4. 后检
            performPostCheck(context)
        );
    }
    
    private Flux<SSEEvent> performPreflight(WorkflowContext context) {
        if (!context.isConsistencyEnabled()) {
            return Flux.empty();
        }
        
        return Mono.fromCallable(() -> preflightService.preflight(
            new PreflightRequest(
                context.getProjectId(),
                context.getChapterId(),
                context.getCharacterIds()
            )
        ))
        .flatMapMany(result -> {
            if (result.hasWarnings()) {
                return Flux.just(SSEEvent.preflightWarning(result.getWarnings()));
            }
            return Flux.just(SSEEvent.preflightOk());
        });
    }
}
```

### 4. ConsistencyAgent Enhancement

```java
@Component
public class ConsistencyAgent extends BaseAgent {
    private final ProactiveConsistencyService consistencyService;
    private final ConsistencyWarningService warningService;
    private final RuleCheckerService ruleCheckerService;
    
    @Override
    public Flux<SSEEvent> execute(AgentContext context) {
        UUID projectId = context.getProjectId();
        List<UUID> entityIds = context.getEntityIds();
        
        return Flux.fromIterable(entityIds)
            .flatMap(entityId -> checkEntity(projectId, entityId))
            .collectList()
            .flatMapMany(warnings -> {
                // 存储警告
                warnings.forEach(w -> warningService.createWarning(toRequest(w)));
                
                // 返回结构化报告
                return Flux.just(
                    SSEEvent.thinking("一致性检查完成"),
                    SSEEvent.artifact(buildReport(warnings))
                );
            });
    }
    
    private Flux<ConsistencyWarning> checkEntity(UUID projectId, UUID entityId) {
        return Flux.fromIterable(
            ruleCheckerService.checkAllRules(projectId, EntityType.CHARACTER, entityId)
        );
    }
}
```

### 5. SSE Event Types

```java
public class SSEEventTypes {
    // 一致性相关
    public static final String PREFLIGHT_RESULT = "preflight_result";
    public static final String CONSISTENCY_WARNING = "consistency_warning";
    public static final String CONSISTENCY_CHECK_COMPLETE = "consistency_check_complete";
    
    // 演进相关
    public static final String EVOLUTION_UPDATE = "evolution_update";
    public static final String SNAPSHOT_CREATED = "snapshot_created";
}
```

## Data Models

### ConsistencyWarning (已存在，无需修改)

```java
@Entity
public class ConsistencyWarning {
    private UUID id;
    private UUID projectId;
    private UUID entityId;
    private EntityType entityType;
    private String entityName;
    private WarningType warningType;
    private Severity severity;
    private String description;
    private String suggestion;
    private boolean resolved;
    private boolean dismissed;
    private LocalDateTime resolvedAt;
    private String resolutionMethod;
}
```

### CharacterChangedEvent (新增)

```java
public record CharacterChangedEvent(
    UUID projectId,
    UUID characterId,
    String characterName,
    Operation operation,
    Map<String, Object> currentState,
    Map<String, Object> previousState
) {
    public enum Operation { CREATE, UPDATE, DELETE }
}
```

### PreflightResult (已存在，增强)

```java
public record PreflightResult(
    boolean passed,
    List<PreflightWarning> warnings,
    Map<UUID, Map<String, Object>> characterStates
) {
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }
}
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Entity changes trigger snapshot creation
*For any* entity (Character, WikiEntry, PlotLoop) that is created or updated, the system SHALL create an evolution snapshot recording the state change.
**Validates: Requirements 2.1, 2.3, 3.3, 6.1**

### Property 2: Entity changes trigger consistency checks
*For any* entity (Character, WikiEntry, PlotLoop) that is updated, the ProactiveConsistencyService SHALL be invoked with the correct project ID and entity ID.
**Validates: Requirements 2.2, 3.1, 5.1**

### Property 3: Workflow consistency check lifecycle
*For any* ContentGenerationWorkflow execution with consistency enabled, the workflow SHALL perform both preflight and post-generation consistency checks.
**Validates: Requirements 4.1, 4.3**

### Property 4: Consistency disabled skips checks
*For any* ContentGenerationWorkflow execution with consistency disabled, no consistency checks SHALL be performed.
**Validates: Requirements 4.5**

### Property 5: State snapshot round-trip consistency
*For any* entity state that is stored as a snapshot, retrieving the state at the same chapter order SHALL return an equivalent state.
**Validates: Requirements 6.2, 6.3**

### Property 6: Keyframe interval enforcement
*For any* evolution timeline, after every 10 delta snapshots, the next snapshot SHALL be a keyframe.
**Validates: Requirements 6.2, 6.4**

### Property 7: SSE event emission on system events
*For any* consistency warning detection, evolution snapshot creation, or preflight completion, the system SHALL emit a corresponding SSE event.
**Validates: Requirements 9.1, 9.2, 9.3**

### Property 8: Entity deletion cleanup
*For any* entity that is deleted, all associated consistency warnings and evolution snapshots SHALL be removed.
**Validates: Requirements 2.4**

## Error Handling

### Database Trigger Errors
- 如果触发器执行失败，不应阻塞主事务
- 使用 `pg_notify` 的异步特性确保不影响写入性能

### CDC Listener Errors
- 连接断开时自动重连
- 通知解析失败时记录日志并跳过
- 使用虚拟线程处理通知，避免阻塞轮询

### Consistency Check Errors
- AI 检查失败时降级到规则检查
- 规则检查失败时记录错误但不阻塞流程
- 所有错误都应记录到日志

### Evolution Snapshot Errors
- 快照创建失败不应影响主业务流程
- 状态重建失败时返回空状态而非抛出异常

## Testing Strategy

### Unit Tests
- CharacterChangeListener 事件处理逻辑
- RuleCheckerService 各规则检查
- StateSnapshotService 关键帧/增量逻辑

### Property-Based Tests (jqwik)
- 使用 jqwik 库进行属性测试
- 每个属性测试运行至少 100 次迭代
- 测试标注格式: `**Feature: consistency-evolution-integration, Property {number}: {property_text}**`

### Integration Tests
- CDC 触发器 → 监听器 → 服务调用链
- ContentGenerationWorkflow 完整流程
- SSE 事件发送验证

