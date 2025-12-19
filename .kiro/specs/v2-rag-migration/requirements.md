# Requirements Document

## Introduction

本规格定义了将 V1 RAG 核心算法迁移到 V2 的需求。V1 RAG 实现包含多个专业算法（RRF 混合检索、语义断崖检测、断路器模式、PostgreSQL 全文搜索），这些算法需要迁移到 V2，同时简化服务结构。

迁移目标是保留 V1 的核心算法优势，同时整合为更清晰的服务结构。

## Glossary

- **RRF (Reciprocal Rank Fusion)**: 一种融合多路检索结果的算法，公式为 Score = 1.0 / (k + rank)，k 通常取 60
- **语义断崖检测**: 通过计算相邻句子的 Embedding 相似度，检测语义突变点进行智能分块
- **断路器模式**: 当服务连续失败达到阈值时自动熔断，一段时间后尝试恢复
- **父子块检索**: "小块检索，大块返回"策略，用小块提高检索精度，返回大块保证上下文完整
- **HybridSearchService**: 混合检索服务，结合向量检索和全文检索
- **EmbeddingService**: 向量嵌入服务，负责生成和管理文本向量
- **KnowledgeChunk**: 知识块实体，存储分块后的内容和向量

## Requirements

### Requirement 1

**User Story:** As a developer, I want to migrate the RRF hybrid search algorithm from V1 to V2, so that search results are more accurate through proper fusion of vector and full-text retrieval.

#### Acceptance Criteria

1. WHEN the HybridSearchService performs a search THEN the system SHALL execute both vector retrieval and full-text retrieval in parallel
2. WHEN merging search results THEN the system SHALL apply the RRF algorithm with k=60 constant to calculate fusion scores
3. WHEN a document appears in both vector and full-text results THEN the system SHALL accumulate the RRF scores from both sources
4. WHEN returning final results THEN the system SHALL sort by combined RRF score in descending order
5. WHEN either retrieval path fails THEN the system SHALL continue with the successful path and log the failure

### Requirement 2

**User Story:** As a developer, I want to migrate the semantic cliff detection algorithm from V1 to V2, so that text chunking respects semantic boundaries.

#### Acceptance Criteria

1. WHEN chunking text content THEN the system SHALL split text into sentences while preserving quoted content
2. WHEN processing sentences THEN the system SHALL calculate embedding similarity between adjacent sentences
3. WHEN similarity drops below the 20th percentile threshold THEN the system SHALL mark that position as a semantic cliff
4. WHEN merging sentences into chunks THEN the system SHALL respect semantic cliff positions as preferred split points
5. WHEN chunk size exceeds the maximum limit THEN the system SHALL force a split regardless of semantic boundaries

### Requirement 3

**User Story:** As a developer, I want to migrate the circuit breaker pattern from V1 to V2, so that the embedding service gracefully handles failures.

#### Acceptance Criteria

1. WHEN the embedding service fails consecutively 5 times THEN the system SHALL open the circuit breaker
2. WHILE the circuit breaker is open THEN the system SHALL reject new requests immediately without calling the external service
3. WHEN 30 seconds have passed since the circuit breaker opened THEN the system SHALL allow a single test request
4. WHEN the test request succeeds THEN the system SHALL close the circuit breaker and reset the failure counter
5. WHEN the test request fails THEN the system SHALL keep the circuit breaker open and restart the recovery timer

### Requirement 4

**User Story:** As a developer, I want to migrate the PostgreSQL full-text search from V1 to V2, so that keyword-based search is available alongside vector search.

#### Acceptance Criteria

1. WHEN performing full-text search THEN the system SHALL use PostgreSQL to_tsvector and plainto_tsquery functions
2. WHEN searching THEN the system SHALL support multiple query types: plain, phrase, boolean, and exact
3. WHEN ranking results THEN the system SHALL use ts_rank_cd function with weighted fields (title weight A, content weight B)
4. WHEN the query contains Chinese characters THEN the system SHALL use the appropriate text search configuration
5. WHEN full-text search fails THEN the system SHALL return an empty result set and log the error

### Requirement 5

**User Story:** As a developer, I want to consolidate RAG configuration into a single properties class, so that configuration is easier to manage.

#### Acceptance Criteria

1. WHEN configuring RAG services THEN the system SHALL use a single RagProperties class for all settings
2. WHEN the application starts THEN the system SHALL validate all required configuration properties
3. WHEN configuration values are missing THEN the system SHALL use sensible defaults
4. WHEN configuration changes at runtime THEN the system SHALL apply changes without restart where possible

### Requirement 6

**User Story:** As a developer, I want to preserve the parent-child search strategy from V1, so that search returns contextually complete results.

#### Acceptance Criteria

1. WHEN searching for content THEN the system SHALL search in child chunks for precision
2. WHEN child chunks match THEN the system SHALL retrieve the corresponding parent StoryBlock
3. WHEN multiple child chunks match the same parent THEN the system SHALL deduplicate and use the highest scoring child
4. WHEN the parent content exceeds the context window size THEN the system SHALL extract a relevant window around the matched child
5. WHEN building context for AI generation THEN the system SHALL sort results by chapter order then block order

### Requirement 7

**User Story:** As a developer, I want the migrated RAG services to integrate with V2's Spring AI architecture, so that embedding generation uses the configured model.

#### Acceptance Criteria

1. WHEN generating embeddings THEN the system SHALL use Spring AI's EmbeddingModel interface
2. WHEN the user has configured a custom embedding provider THEN the system SHALL use that provider
3. WHEN no custom provider is configured THEN the system SHALL fall back to the default embedding model
4. WHEN embedding generation fails THEN the system SHALL trigger the circuit breaker logic
5. WHEN caching embeddings THEN the system SHALL use the existing EmbeddingCacheService

