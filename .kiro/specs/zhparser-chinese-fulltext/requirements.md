# Requirements Document

## Introduction

本功能为 InkFlow 2.0 集成 zhparser 中文分词扩展，以实现高质量的中文全文搜索。当前系统使用 PostgreSQL 的 `simple` 配置进行全文搜索，无法正确处理中文分词，导致中文搜索召回率和精确度较低。zhparser 基于 SCWS（Simple Chinese Word Segmentation）分词库，能够提供专业级的中文分词能力，显著提升中文小说内容的搜索质量。

## Glossary

- **zhparser**: PostgreSQL 中文全文搜索扩展，基于 SCWS 分词库
- **SCWS**: Simple Chinese Word Segmentation，简易中文分词系统
- **tsvector**: PostgreSQL 全文搜索向量类型，存储分词后的词素
- **tsquery**: PostgreSQL 全文搜索查询类型
- **GIN Index**: Generalized Inverted Index，用于加速全文搜索的倒排索引
- **Full-Text Search (FTS)**: 全文搜索，基于词素匹配的文本检索技术
- **Hybrid Search**: 混合搜索，结合向量语义搜索和全文搜索的检索策略
- **knowledge_chunks**: 知识块表，存储 RAG 系统的文本分块和向量嵌入
- **ts_rank_cd**: PostgreSQL 全文搜索排名函数，基于覆盖密度计算相关性

## Requirements

### Requirement 1

**User Story:** As a novel author, I want the system to correctly segment Chinese text when searching, so that I can find relevant content using natural Chinese queries.

#### Acceptance Criteria

1. WHEN the system initializes the database THEN the System SHALL enable the zhparser extension and configure the chinese text search configuration
2. WHEN a user searches with Chinese keywords THEN the System SHALL use zhparser to tokenize the query and match against properly segmented content
3. WHEN Chinese text is indexed THEN the System SHALL generate tsvector using zhparser configuration instead of simple configuration
4. IF zhparser extension is not available THEN the System SHALL fall back to simple configuration and log a warning

### Requirement 2

**User Story:** As a system administrator, I want to configure zhparser segmentation parameters, so that I can optimize search quality for novel content.

#### Acceptance Criteria

1. WHEN configuring zhparser THEN the System SHALL support multi-short word segmentation mode for better recall
2. WHEN configuring zhparser THEN the System SHALL enable punctuation filtering to exclude irrelevant tokens
3. WHEN configuring zhparser THEN the System SHALL support custom dictionary loading for novel-specific terminology
4. WHERE custom dictionary is provided THEN the System SHALL load domain-specific terms (character names, location names, martial arts terms)

### Requirement 3

**User Story:** As a developer, I want the knowledge_chunks table to use zhparser for text indexing, so that RAG search can leverage high-quality Chinese segmentation.

#### Acceptance Criteria

1. WHEN creating or updating knowledge_chunks THEN the System SHALL generate text_search tsvector using zhparser chinese configuration
2. WHEN performing full-text search on knowledge_chunks THEN the System SHALL use chinese configuration for both query parsing and ranking
3. WHEN the GIN index is created THEN the System SHALL use the chinese text search configuration for optimal performance
4. WHEN migrating existing data THEN the System SHALL regenerate tsvector for all existing knowledge_chunks using zhparser

### Requirement 4

**User Story:** As a user, I want search results to be ranked by relevance considering Chinese language characteristics, so that the most relevant content appears first.

#### Acceptance Criteria

1. WHEN ranking search results THEN the System SHALL use ts_rank_cd with chinese configuration for accurate relevance scoring
2. WHEN a query contains both Chinese and English terms THEN the System SHALL handle mixed-language queries correctly
3. WHEN calculating relevance THEN the System SHALL apply title weight (A) and content weight (B) for weighted ranking
4. WHEN displaying search results THEN the System SHALL return results ordered by combined relevance score

### Requirement 5

**User Story:** As a developer, I want the FullTextSearchService to seamlessly integrate zhparser, so that existing search functionality is enhanced without breaking changes.

#### Acceptance Criteria

1. WHEN FullTextSearchService executes a search THEN the System SHALL use chinese configuration for all tsquery functions
2. WHEN building search queries THEN the System SHALL support plainto_tsquery, phraseto_tsquery, and to_tsquery with chinese configuration
3. WHEN the search language configuration is set to chinese THEN the System SHALL validate that zhparser is properly installed
4. IF a search query fails due to configuration issues THEN the System SHALL return an empty result set and log the error

### Requirement 6

**User Story:** As a system administrator, I want to monitor zhparser performance and health, so that I can ensure search quality remains high.

#### Acceptance Criteria

1. WHEN the application starts THEN the System SHALL verify zhparser extension availability and log the status
2. WHEN search performance degrades THEN the System SHALL log query execution times for analysis
3. WHEN zhparser configuration changes THEN the System SHALL provide a mechanism to reindex affected content
