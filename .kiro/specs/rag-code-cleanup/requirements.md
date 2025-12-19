# Requirements Document

## Introduction

本需求文档基于 `docs/RAG_IMPROVEMENT_DISCUSSION.md` 中的代码分析结果，定义 RAG 模块的代码清理和优化工作。目标是移除死代码、清理未使用的方法、优化代码结构，提高代码可维护性和可读性。

## Glossary

- **RAG**: Retrieval-Augmented Generation，检索增强生成
- **RagController**: RAG 模块的 HTTP API 控制器
- **EmbeddingService**: 向量嵌入生成服务
- **EmbeddingCacheService**: 向量嵌入缓存服务
- **SemanticChunkingService**: 语义分块服务
- **ParentChildSearchService**: 父子块检索服务
- **VersionedEmbeddingService**: 版本化嵌入服务（死代码）
- **HybridSearchService**: 混合检索服务
- **RagProperties**: RAG 模块配置属性类
- **Dead Code**: 未被任何代码路径调用的代码

## Requirements

### Requirement 1: 移除 VersionedEmbeddingService 死代码

**User Story:** As a developer, I want to remove unused VersionedEmbeddingService class, so that the codebase is cleaner and easier to maintain.

#### Acceptance Criteria

1. WHEN the VersionedEmbeddingService class is removed THEN the RAG_System SHALL compile without errors
2. WHEN the VersionedEmbeddingService class is removed THEN the RAG_System SHALL pass all existing tests
3. WHEN the removal is complete THEN the RAG_System SHALL not contain any references to VersionedEmbeddingService

### Requirement 2: 清理 EmbeddingService 未使用方法

**User Story:** As a developer, I want to clean up unused methods in EmbeddingService, so that the class has a clearer responsibility.

#### Acceptance Criteria

1. WHEN the `generateAndSave()` method is removed THEN the EmbeddingService SHALL compile without errors
2. WHEN the `getRelevantContext()` method is removed THEN the EmbeddingService SHALL compile without errors
3. WHEN the `recordSuccess()` and `recordFailure()` methods are changed to private THEN the EmbeddingService SHALL maintain internal circuit breaker functionality
4. WHEN the cleanup is complete THEN the EmbeddingService SHALL have only publicly used methods exposed as public

### Requirement 3: 清理 SemanticChunkingService 方法可见性

**User Story:** As a developer, I want to reduce the public API surface of SemanticChunkingService, so that internal implementation details are not exposed.

#### Acceptance Criteria

1. WHEN `splitAtSentenceBoundaries()` is changed to private THEN the SemanticChunkingService SHALL compile without errors
2. WHEN `calculateAdjacentSimilarities()` is changed to private THEN the SemanticChunkingService SHALL compile without errors
3. WHEN `detectSemanticCliffs()` is changed to private THEN the SemanticChunkingService SHALL compile without errors
4. WHEN `getMinChildSize()` and `getCliffThreshold()` are removed THEN the SemanticChunkingService SHALL compile without errors
5. WHEN the cleanup is complete THEN the SemanticChunkingService SHALL expose only `splitIntoChildChunks()`, `chunkText()`, `simpleChunk()`, and `getMaxChildSize()` as public methods

### Requirement 4: 精简 RagController 端点

**User Story:** As a developer, I want to remove redundant RAG API endpoints, so that the API is cleaner and easier to document.

#### Acceptance Criteria

1. WHEN the `/search/body` endpoint is removed THEN the RagController SHALL provide equivalent functionality through `/search` endpoint
2. WHEN the `/context` and `/context/body` endpoints are removed THEN the RagController SHALL compile without errors
3. WHEN the `/chunk` endpoint is removed THEN the RagController SHALL compile without errors
4. WHEN the `/search/parent-child` endpoint is removed THEN the RagController SHALL compile without errors
5. WHEN the `/search/vector` endpoint is removed THEN the RagController SHALL compile without errors
6. WHEN the cleanup is complete THEN the RagController SHALL retain health, cache, stats, and delete endpoints

### Requirement 5: 优化 RagProperties 默认值定义

**User Story:** As a developer, I want to consolidate default value definitions in RagProperties, so that default values are defined in one place.

#### Acceptance Criteria

1. WHEN default values are extracted to constants THEN the RagProperties SHALL define each default value exactly once
2. WHEN the compact constructor uses constants THEN the RagProperties SHALL apply defaults consistently
3. WHEN the `defaults()` factory method uses constants THEN the RagProperties SHALL return consistent default values
4. WHEN the refactoring is complete THEN the RagProperties SHALL compile without errors

### Requirement 6: 移除 EmbeddingCacheService 未使用方法

**User Story:** As a developer, I want to remove unused cache methods, so that the codebase is cleaner.

#### Acceptance Criteria

1. WHEN the `exists()` method is removed THEN the EmbeddingCacheService SHALL compile without errors
2. WHEN the `evict()` method is removed THEN the EmbeddingCacheService SHALL compile without errors
3. WHEN the removal is complete THEN the EmbeddingCacheService SHALL retain only `get()`, `put()`, `clear()`, and `getStats()` methods

### Requirement 7: 修复 SemanticChunkingService 中文处理

**User Story:** As a developer, I want to fix Chinese text handling in SemanticChunkingService, so that Chinese sentences are not incorrectly joined with spaces.

#### Acceptance Criteria

1. WHEN Chinese text is chunked THEN the SemanticChunkingService SHALL not add spaces between Chinese sentences
2. WHEN mixed Chinese and English text is chunked THEN the SemanticChunkingService SHALL add spaces only between English sentences
3. WHEN the fix is applied THEN the SemanticChunkingService SHALL pass property tests for Chinese text handling

