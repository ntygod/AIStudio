# Requirements Document

## Introduction

本需求文档旨在系统性地完善 InkFlow V2 后端中发现的未实现代码、TODO标记、简化实现和功能缺失。这些缺陷已在 `docs/V2_UNIMPLEMENTED_CODE_ANALYSIS.md` 中详细记录。本文档将这些缺陷转化为可执行的需求，按优先级分阶段实施。

## Glossary

- **InkFlow_V2_Backend**: InkFlow 小说创作平台的第二版后端系统
- **DynamicChatModelFactory**: 动态AI聊天模型工厂，负责根据配置创建和管理AI模型实例
- **ProactiveConsistencyService**: 主动一致性检查服务，用于检测小说数据中的逻辑冲突
- **VersionedEmbeddingService**: 版本化嵌入服务，管理知识块的向量嵌入版本
- **AgentOrchestrator**: Agent编排器，负责协调多个AI Agent的执行
- **ImportService**: 项目导入服务，负责从JSON格式导入小说项目数据
- **ExportService**: 项目导出服务，负责将小说项目数据导出为JSON格式
- **ConsistencyWarning**: 一致性警告实体，记录检测到的数据冲突
- **EntityUpdate**: 实体更新事件，包含实体类型和变更内容

## Requirements

### Requirement 1: 用户级AI模型配置

**User Story:** As a 小说作者, I want to 配置我个人偏好的AI提供商和模型, so that 系统能够使用我选择的AI服务进行创作辅助。

#### Acceptance Criteria

1. WHEN a user requests an AI chat model THEN the InkFlow_V2_Backend SHALL retrieve the user's preferred provider configuration from the database
2. WHEN a user has no configured provider preference THEN the InkFlow_V2_Backend SHALL return the system default model
3. WHEN a user's configured provider is unavailable THEN the InkFlow_V2_Backend SHALL fall back to the default model and log a warning
4. WHEN retrieving user configuration THEN the InkFlow_V2_Backend SHALL cache the configuration for 5 minutes to reduce database queries

---

### Requirement 2: 一致性规则检查实现

**User Story:** As a 小说作者, I want the system to 自动检测我的小说数据中的逻辑冲突, so that 我能够保持故事的一致性。

#### Acceptance Criteria

1. WHEN a Character entity is updated THEN the ProactiveConsistencyService SHALL check for name uniqueness within the project
2. WHEN a Character entity is created or updated THEN the ProactiveConsistencyService SHALL validate that required fields (name, role) are not empty
3. WHEN a WikiEntry entity is updated THEN the ProactiveConsistencyService SHALL check for title uniqueness within the project
4. WHEN a WikiEntry entity references other entities THEN the ProactiveConsistencyService SHALL verify that referenced entities exist
5. WHEN a CharacterRelationship is created THEN the ProactiveConsistencyService SHALL verify bidirectional consistency
6. IF AI consistency check is enabled in configuration THEN the ProactiveConsistencyService SHALL perform AI-enhanced analysis after rule-based checks

---

### Requirement 3: 版本化嵌入清理

**User Story:** As a 系统管理员, I want the system to 自动清理旧版本的向量嵌入数据, so that 存储空间能够被有效利用。

#### Acceptance Criteria

1. WHEN cleanupOldVersions is called with a sourceId and keepVersions parameter THEN the VersionedEmbeddingService SHALL delete all embedding versions older than the cutoff version
2. WHEN deleting old versions THEN the VersionedEmbeddingService SHALL only delete inactive (non-current) embedding records
3. WHEN cleanup is performed THEN the VersionedEmbeddingService SHALL log the number of deleted records
4. WHEN the current version count is less than or equal to keepVersions THEN the VersionedEmbeddingService SHALL skip the cleanup operation

---

### Requirement 4: Agent链式执行输出传递

**User Story:** As a 开发者, I want Agent链式执行时 前一个Agent的输出能够传递给下一个Agent, so that 复杂的多步骤AI任务能够正确执行。

#### Acceptance Criteria

1. WHEN executing agents in chain mode THEN the AgentOrchestrator SHALL collect the output from each agent
2. WHEN an agent completes execution in chain mode THEN the AgentOrchestrator SHALL construct the next agent's input using the previous agent's output
3. WHEN the final agent in the chain completes THEN the AgentOrchestrator SHALL return the final output to the caller
4. IF an agent in the chain fails THEN the AgentOrchestrator SHALL stop execution and return an error with the failed agent's information

---

### Requirement 5: 完整项目导出功能

**User Story:** As a 小说作者, I want to 导出我的完整项目数据包括角色、设定和伏笔, so that 我能够备份或迁移我的创作内容。

#### Acceptance Criteria

1. WHEN exporting a project THEN the ExportService SHALL include all Character entities with their complete attributes
2. WHEN exporting a project THEN the ExportService SHALL include all WikiEntry entities with their content and metadata
3. WHEN exporting a project THEN the ExportService SHALL include all PlotLoop entities with their status and references
4. WHEN exporting a project THEN the ExportService SHALL include all CharacterRelationship entities
5. WHEN exporting a project THEN the ExportService SHALL include all CharacterArchetype entities
6. WHEN export completes THEN the ExportService SHALL return a complete JSON structure that can be imported without data loss

---

### Requirement 6: 完整项目导入功能

**User Story:** As a 小说作者, I want to 导入包含角色、设定和伏笔的完整项目数据, so that 我能够恢复或迁移我的创作内容。

#### Acceptance Criteria

1. WHEN importing a project THEN the ImportService SHALL create all Character entities from the import data
2. WHEN importing a project THEN the ImportService SHALL create all WikiEntry entities from the import data
3. WHEN importing a project THEN the ImportService SHALL create all PlotLoop entities from the import data
4. WHEN importing a project THEN the ImportService SHALL create all CharacterRelationship entities from the import data
5. WHEN importing a project THEN the ImportService SHALL create all CharacterArchetype entities from the import data
6. WHEN import completes THEN the ImportService SHALL return the newly created project with all associated entities
7. IF import data contains invalid references THEN the ImportService SHALL skip the invalid entity and log a warning

---

### Requirement 7: 空返回值安全处理

**User Story:** As a 开发者, I want all methods that may return null to 使用Optional或提供默认值, so that 系统不会因为NullPointerException而崩溃。

#### Acceptance Criteria

1. WHEN DynamicChatModelFactory.getReasoningModel() has no available provider THEN the method SHALL return Optional.empty() instead of null
2. WHEN ThinkingAgent.extractThinking() regex does not match THEN the method SHALL return an empty string instead of null
3. WHEN ThinkingAgent.extractAnswer() regex does not match THEN the method SHALL return the original content instead of null
4. WHEN any Agent's retrieveContext() fails THEN the method SHALL return an empty context string instead of null
5. WHEN WriterAgent.retrieveStyle() fails THEN the method SHALL return a default style configuration instead of null
6. WHEN DeepReasoningTool.getReasoningModel() fails THEN the method SHALL throw a descriptive exception instead of returning null

---

### Requirement 8: 外部服务配置验证

**User Story:** As a 系统管理员, I want the system to 在启动时验证外部服务配置, so that 配置错误能够被及早发现。

#### Acceptance Criteria

1. WHEN the application starts THEN the RerankerService SHALL validate that the configured endpoint is reachable
2. WHEN the Reranker endpoint is unreachable THEN the RerankerService SHALL log an error and disable reranking functionality
3. WHEN the application starts THEN the EmbeddingService SHALL verify that the EmbeddingModel bean is available
4. WHEN the EmbeddingModel bean is unavailable THEN the EmbeddingService SHALL throw a descriptive startup exception

---

### Requirement 9: 关键服务测试覆盖

**User Story:** As a 开发者, I want 关键服务有完整的测试覆盖, so that 代码变更不会引入回归问题。

#### Acceptance Criteria

1. THE AgentOrchestrator SHALL have property-based tests for parallel execution correctness
2. THE AgentOrchestrator SHALL have property-based tests for chain execution output passing
3. THE ThinkingAgent SHALL have unit tests for thinking pattern extraction
4. THE ProactiveConsistencyService SHALL have tests for debounce and rate limiting behavior
5. THE VersionedEmbeddingService SHALL have tests for version switch atomicity
6. THE RerankerService SHALL have tests for circuit breaker and fallback behavior

---

### Requirement 10: V1功能迁移 - Provider配置模块

**User Story:** As a 小说作者, I want to 配置不同功能使用不同的AI模型, so that 我能够根据任务类型优化AI使用成本和效果。

#### Acceptance Criteria

1. WHEN a user configures a provider THEN the InkFlow_V2_Backend SHALL store the provider configuration with API key encryption
2. WHEN a user configures functional model mapping THEN the InkFlow_V2_Backend SHALL allow mapping specific functions to specific models
3. WHEN configuration is updated THEN the InkFlow_V2_Backend SHALL validate configuration consistency
4. WHEN retrieving AI configuration THEN the AIConfigResolver SHALL resolve the appropriate model based on function type and user preference

---

### Requirement 11: 全文搜索监控功能

**User Story:** As a 系统管理员, I want to 监控全文搜索的性能和错误, so that 我能够及时发现和解决搜索相关问题。

#### Acceptance Criteria

1. WHEN a full-text search is executed THEN the FullTextSearchService SHALL log search metrics including query time and result count
2. WHEN a search error occurs THEN the FullTextSearchErrorHandler SHALL log the error with context information
3. WHEN search results are retrieved THEN the FullTextSearchCacheService SHALL cache results for repeated queries
4. THE FullTextSearchMonitoringController SHALL expose endpoints for viewing search statistics and recent errors

---

### Requirement 12: 移除创作进度功能

**User Story:** As a 开发者, I want to 移除不实用的创作进度计算功能, so that 代码库更加简洁且不会误导用户。

#### Acceptance Criteria

1. WHEN the CreationProgressService is reviewed THEN the InkFlow_V2_Backend SHALL remove the calculatePhaseCompletion method
2. WHEN the progress module is cleaned up THEN the InkFlow_V2_Backend SHALL remove related unused DTOs and endpoints
3. WHEN progress-related code is removed THEN the InkFlow_V2_Backend SHALL update any dependent code to not rely on progress calculations
4. WHEN the cleanup is complete THEN the InkFlow_V2_Backend SHALL ensure no compilation errors exist

---

### Requirement 13: JSON序列化异常处理

**User Story:** As a 开发者, I want JSON序列化方法在异常时 返回有意义的错误信息, so that 调试和问题排查更加容易。

#### Acceptance Criteria

1. WHEN EvolutionAnalysisService.toJson() encounters an exception THEN the method SHALL return a JSON object containing error details instead of empty "{}"
2. WHEN ConsistencyCheckService.toJson() encounters an exception THEN the method SHALL return a JSON object containing error details instead of empty "{}"
3. WHEN JSON serialization fails THEN the service SHALL log the exception with full stack trace

---

### Requirement 14: 预检详细逻辑检查

**User Story:** As a 小说作者, I want the system to 检查我的章节内容中的时间线和地点一致性问题, so that 我能够在发布前发现逻辑错误。

#### Acceptance Criteria

1. WHEN checkDetailedLogic is called THEN the PreflightService SHALL check for timeline consistency across chapters
2. WHEN checkDetailedLogic is called THEN the PreflightService SHALL check for location consistency with character positions
3. WHEN checkDetailedLogic is called THEN the PreflightService SHALL check for character state consistency (alive/dead, present/absent)
4. WHEN inconsistencies are found THEN the PreflightService SHALL return a list of detailed warning messages
5. WHEN no inconsistencies are found THEN the PreflightService SHALL return an empty list

