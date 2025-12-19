# Requirements Document

## Introduction

本规范旨在解决 ParentChildSearchService 未被正确集成到 RAG 检索流程中的问题。当前系统存在以下核心问题：

1. **索引创建未被调用**：`createParentChildIndex()` 方法没有被任何内容服务调用，导致父子块结构未被创建
2. **检索流程未集成**：`HybridSearchService` 直接调用 `EmbeddingService.searchWithScore()` 进行向量检索，没有使用父子块检索策略
3. **"小块检索，大块返回"策略未生效**：当前混合检索返回的是原始 chunk，而不是父块内容

本规范将实现父子块检索策略的完整集成，使 RAG 系统能够：
- 在子块（小块）中搜索以获得精确匹配
- 返回对应的父块（大块）内容以提供完整上下文
- 避免返回过于碎片化的内容

## Glossary

- **ParentChildSearchService**: 父子块检索服务，实现"小块检索，大块返回"策略
- **HybridSearchService**: 混合检索服务，结合向量检索和全文检索，使用 RRF 算法融合结果
- **Parent Chunk (父块)**: 存储完整内容的知识块，不生成 embedding，用于返回完整上下文
- **Child Chunk (子块)**: 语义分块后的小块，生成 embedding 用于精确匹配检索
- **StoryBlock**: 剧情块实体，小说内容的基本单元
- **WikiEntry**: 百科条目实体，世界设定的基本单元
- **RRF (Reciprocal Rank Fusion)**: 倒数排名融合算法，用于合并多个检索结果
- **SemanticChunkingService**: 语义分块服务，将长文本切分为语义完整的子块

## Requirements

### Requirement 1

**User Story:** As a content author, I want my story blocks to be automatically indexed with parent-child structure, so that RAG retrieval can find precise matches while returning complete context.

#### Acceptance Criteria

1. WHEN a StoryBlock is created THEN the System SHALL call ParentChildSearchService.createParentChildIndex() to create parent and child chunks
2. WHEN a StoryBlock content is updated THEN the System SHALL delete old chunks and recreate parent-child index with new content
3. WHEN a StoryBlock is deleted THEN the System SHALL delete all associated parent and child chunks
4. WHEN createParentChildIndex() is called THEN the System SHALL create one parent chunk without embedding and multiple child chunks with embeddings

### Requirement 2

**User Story:** As a content author, I want my wiki entries to be automatically indexed with parent-child structure, so that world-building settings can be retrieved with full context.

#### Acceptance Criteria

1. WHEN a WikiEntry is created THEN the System SHALL call ParentChildSearchService.createParentChildIndex() to create parent and child chunks
2. WHEN a WikiEntry content is updated THEN the System SHALL delete old chunks and recreate parent-child index with new content
3. WHEN a WikiEntry is deleted THEN the System SHALL delete all associated parent and child chunks
4. WHEN indexing WikiEntry THEN the System SHALL include entry title and type in chunk metadata

### Requirement 3

**User Story:** As an AI assistant, I want to use parent-child search strategy for content retrieval, so that I can find precise matches and return complete context to users.

#### Acceptance Criteria

1. WHEN HybridSearchService performs vector search THEN the System SHALL use ParentChildSearchService.search() instead of direct EmbeddingService.searchWithScore()
2. WHEN parent-child search returns results THEN the System SHALL return parent chunk content with child chunk similarity score
3. WHEN multiple child chunks match the same parent THEN the System SHALL deduplicate by parent and keep the highest scoring child
4. WHEN parent content exceeds context window size THEN the System SHALL extract relevant context window around matched child position

### Requirement 4

**User Story:** As a system administrator, I want to configure the search strategy, so that I can choose between parent-child search and direct search based on use case.

#### Acceptance Criteria

1. WHEN configuration property `inkflow.rag.search.use-parent-child` is true THEN the System SHALL use ParentChildSearchService for vector search
2. WHEN configuration property `inkflow.rag.search.use-parent-child` is false THEN the System SHALL use direct EmbeddingService.searchWithScore() for vector search
3. WHEN configuration is not specified THEN the System SHALL default to using parent-child search strategy
4. WHEN search strategy is changed THEN the System SHALL apply new strategy without requiring restart

### Requirement 5

**User Story:** As a developer, I want comprehensive property-based tests for the parent-child search integration, so that correctness is verified across all valid inputs.

#### Acceptance Criteria

1. WHEN testing createParentChildIndex() THEN the System SHALL verify parent chunk has no embedding and child chunks have embeddings
2. WHEN testing search() THEN the System SHALL verify results are deduplicated by parent and sorted by similarity
3. WHEN testing content update THEN the System SHALL verify old chunks are deleted before new chunks are created
4. WHEN testing round-trip THEN the System SHALL verify indexed content can be retrieved with correct parent-child relationships
