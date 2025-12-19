# Requirements Document

## Introduction

本功能旨在将 InkFlow 中已实现但未充分使用的 `consistency` 和 `evolution` 模块真正集成到业务流程中。这两个模块提供了强大的一致性检查和角色/设定演进追踪能力，但目前处于"基础设施已建好，业务流程未接入"的状态。

通过本次集成，将实现：
1. 数据库 CDC 触发器激活，实现实体变更自动触发一致性检查
2. 角色/Wiki 变更时自动记录演进快照
3. 写作流程中集成完整的一致性检查
4. 前端展示一致性警告和演进时间线

## Glossary

- **CDC (Change Data Capture)**: 数据变更捕获，通过 PostgreSQL LISTEN/NOTIFY 机制监听数据库变更
- **Evolution Timeline**: 演进时间线，记录角色/设定在不同章节的状态变化
- **State Snapshot**: 状态快照，使用关键帧+增量策略存储实体状态
- **Consistency Warning**: 一致性警告，检测到的设定冲突或逻辑矛盾
- **Preflight Check**: 预检，在内容生成前检查潜在的一致性问题
- **ProactiveConsistencyService**: 主动一致性检查服务，实现防抖、限流和静默警告存储
- **RuleCheckerService**: 规则检查服务，执行基于规则的一致性检查

## Requirements

### Requirement 1: 数据库 CDC 触发器

**User Story:** As a system administrator, I want database triggers to automatically notify the application when entities change, so that consistency checks can be triggered without polling.

#### Acceptance Criteria

1. WHEN a Character entity is inserted or updated THEN the database SHALL send a notification to the 'entity_changes' channel with entity details
2. WHEN a WikiEntry entity is inserted or updated THEN the database SHALL send a notification to the 'entity_changes' channel with entity details
3. WHEN a PlotLoop entity is inserted or updated THEN the database SHALL send a notification to the 'entity_changes' channel with entity details
4. WHEN a CharacterRelationship entity is inserted or updated THEN the database SHALL send a notification to the 'entity_changes' channel with entity details

---

### Requirement 2: 角色变更事件监听

**User Story:** As a writer, I want the system to automatically track character changes, so that I can see how characters evolve throughout my story.

#### Acceptance Criteria

1. WHEN a Character entity is created THEN the CharacterChangeListener SHALL trigger an evolution snapshot creation
2. WHEN a Character entity is updated THEN the CharacterChangeListener SHALL trigger a consistency check via ProactiveConsistencyService
3. WHEN a Character entity is updated THEN the CharacterChangeListener SHALL create an evolution snapshot recording the state change
4. WHEN a Character entity is deleted THEN the CharacterChangeListener SHALL clean up associated warnings and snapshots

---

### Requirement 3: 伏笔变更事件监听

**User Story:** As a writer, I want the system to track plot loop (foreshadowing) changes, so that I can ensure all planted seeds are properly resolved.

#### Acceptance Criteria

1. WHEN a PlotLoop entity status changes THEN the PlotLoopChangeListener SHALL trigger a consistency check for related chapters
2. WHEN a PlotLoop entity is resolved THEN the PlotLoopChangeListener SHALL verify the resolution chapter exists and is valid
3. WHEN a PlotLoop entity is created THEN the PlotLoopChangeListener SHALL create an initial evolution snapshot

---

### Requirement 4: 写作流程一致性检查集成

**User Story:** As a writer, I want the AI writing assistant to check for consistency issues before and after generating content, so that I can avoid plot holes and character inconsistencies.

#### Acceptance Criteria

1. WHEN ContentGenerationWorkflow starts THEN the workflow SHALL perform a preflight consistency check using PreflightService
2. WHEN ContentGenerationWorkflow generates content THEN the workflow SHALL use evolution state data to inform character behavior
3. WHEN ContentGenerationWorkflow completes THEN the workflow SHALL trigger a post-generation consistency check
4. WHEN consistency issues are detected THEN the workflow SHALL include warnings in the SSE response stream
5. WHEN consistency check is disabled in request THEN the workflow SHALL skip consistency checks

---

### Requirement 5: ConsistencyAgent 集成

**User Story:** As a writer, I want a dedicated AI agent that can analyze my story for consistency issues, so that I can proactively fix problems.

#### Acceptance Criteria

1. WHEN ConsistencyAgent receives a check request THEN the agent SHALL use ProactiveConsistencyService to perform rule-based checks
2. WHEN ConsistencyAgent detects issues THEN the agent SHALL use ConsistencyWarningService to store warnings
3. WHEN ConsistencyAgent completes analysis THEN the agent SHALL return a structured report with severity levels
4. WHEN ConsistencyAgent is invoked THEN the agent SHALL check character name uniqueness, required fields, and relationship consistency

---

### Requirement 6: 演进快照自动创建

**User Story:** As a writer, I want the system to automatically create snapshots of character states at key story points, so that I can track character development.

#### Acceptance Criteria

1. WHEN a chapter is saved with character mentions THEN the EvolutionAnalysisService SHALL analyze and create state snapshots
2. WHEN creating a snapshot THEN the StateSnapshotService SHALL use keyframe+delta strategy for storage efficiency
3. WHEN retrieving character state at a chapter THEN the StateRetrievalService SHALL reconstruct state from keyframes and deltas
4. WHEN a character state changes significantly THEN the system SHALL create a keyframe snapshot

---

### Requirement 7: 前端一致性警告展示

**User Story:** As a writer, I want to see consistency warnings in the UI, so that I can address issues while writing.

#### Acceptance Criteria

1. WHEN a project is opened THEN the frontend SHALL fetch unresolved warning count from ConsistencyController
2. WHEN warnings exist THEN the frontend SHALL display a warning indicator in the sidebar
3. WHEN user clicks the warning indicator THEN the frontend SHALL show a panel with warning details
4. WHEN user resolves a warning THEN the frontend SHALL call the resolve API and update the display
5. WHEN user dismisses a warning THEN the frontend SHALL call the dismiss API and hide the warning

---

### Requirement 8: 前端演进时间线展示

**User Story:** As a writer, I want to view a character's evolution timeline, so that I can understand how they've changed throughout the story.

#### Acceptance Criteria

1. WHEN user views a character detail THEN the frontend SHALL fetch evolution snapshots from EvolutionController
2. WHEN snapshots exist THEN the frontend SHALL display a timeline visualization
3. WHEN user clicks a snapshot THEN the frontend SHALL show the character state at that point
4. WHEN user compares two snapshots THEN the frontend SHALL highlight the differences

---

### Requirement 9: SSE 事件流集成

**User Story:** As a writer, I want to receive real-time consistency warnings during AI content generation, so that I can see issues as they're detected.

#### Acceptance Criteria

1. WHEN a consistency warning is detected during generation THEN the system SHALL emit an SSE event with type 'consistency_warning'
2. WHEN an evolution snapshot is created THEN the system SHALL emit an SSE event with type 'evolution_update'
3. WHEN preflight check completes THEN the system SHALL emit an SSE event with type 'preflight_result'

