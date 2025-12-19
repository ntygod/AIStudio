# Implementation Plan

## V2 RAG Migration Tasks

- [x] 1. Set up RAG module structure and configuration





  - [x] 1.1 Create RagProperties configuration class with all settings


    - Create unified configuration for hybrid search, embedding, chunking, full-text, and reranker
    - Include sensible defaults for all properties
    - _Requirements: 5.1, 5.2, 5.3_
  - [ ]* 1.2 Write property test for configuration defaults
    - **Property 11: Configuration Defaults**
    - **Validates: Requirements 5.3**

  - [x] 1.3 Create base DTOs: SearchResult, ChildChunk, RerankResult

    - Define all data transfer objects needed by RAG services
    - _Requirements: 1.2, 2.1, 6.1_

- [x] 2. Implement EmbeddingService with circuit breaker





  - [x] 2.1 Create EmbeddingService with Spring AI EmbeddingModel integration


    - Implement generateEmbedding() and generateEmbeddingsBatch()
    - Integrate with Spring AI's EmbeddingModel interface
    - _Requirements: 7.1, 7.2, 7.3_

  - [x] 2.2 Implement circuit breaker pattern in EmbeddingService

    - Add failure threshold (5), recovery timeout (30s), state transitions
    - Implement recordSuccess() and recordFailure() methods
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_
  - [ ]* 2.3 Write property test for circuit breaker state transitions
    - **Property 8: Circuit Breaker State Transitions**
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**

  - [x] 2.4 Implement searchWithScore() for vector similarity search

    - Use KnowledgeChunkRepository for pgvector queries
    - _Requirements: 1.1, 7.1_
  - [ ]* 2.5 Write property test for embedding provider selection
    - **Property 14: Embedding Provider Selection**
    - **Validates: Requirements 7.2, 7.3**
  - [ ]* 2.6 Write property test for embedding failure triggers circuit breaker
    - **Property 15: Embedding Failure Triggers Circuit Breaker**
    - **Validates: Requirements 7.4**

- [x] 3. Checkpoint - Ensure all tests pass





  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Implement FullTextSearchService





  - [x] 4.1 Create FullTextSearchService with PostgreSQL full-text search


    - Implement search() with to_tsvector and plainto_tsquery
    - Support plain, phrase, boolean, exact query types
    - _Requirements: 4.1, 4.2_

  - [x] 4.2 Implement weighted ranking with ts_rank_cd

    - Add title weight A, content weight B
    - Support Chinese text search configuration
    - _Requirements: 4.3, 4.4_

  - [x] 4.3 Implement error handling for full-text search

    - Return empty result set on failure, log error
    - _Requirements: 4.5_
  - [ ]* 4.4 Write property test for query type support
    - **Property 9: Full-Text Search Query Type Support**
    - **Validates: Requirements 4.2**
  - [ ]* 4.5 Write property test for error handling
    - **Property 10: Full-Text Search Error Handling**
    - **Validates: Requirements 4.5**

- [x] 5. Implement RerankerService

  - [x] 5.1 Create RerankerService with local BGE model integration
    - Implement rerank() method calling local bge-reranker-v2-m3
    - Implement calculateSimilarity() and calculateAdjacentSimilarities()
    - _Requirements: 1.2, 2.2_

  - [x] 5.2 Implement circuit breaker pattern in RerankerService
    - Add failure threshold (3), recovery timeout (20s)
    - Implement fallback to score-based ranking
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

  - [x] 5.3 Implement caching for reranker results

    - Add in-memory cache with expiration (5 minutes)
    - Implement cache statistics
    - _Requirements: 5.3_
  - [ ]* 5.4 Write property test for reranker score ordering
    - **Property 16: Reranker Score Ordering**
    - **Validates: Requirements 1.2**
  - [ ]* 5.5 Write property test for reranker circuit breaker
    - **Property 17: Reranker Circuit Breaker State Transitions**
    - **Validates: Requirements 3.1-3.5**
  - [ ]* 5.6 Write property test for reranker cache consistency
    - **Property 18: Reranker Cache Consistency**
    - **Validates: Requirements 5.3**
  - [ ]* 5.7 Write property test for reranker fallback
    - **Property 19: Reranker Fallback Produces Valid Results**
    - **Validates: Requirements 1.5**
  - [ ]* 5.8 Write property test for adjacent similarity count
    - **Property 20: Adjacent Similarity Count for Reranker**
    - **Validates: Requirements 2.2**

- [ ] 6. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Implement HybridSearchService with RRF fusion





  - [x] 7.1 Create HybridSearchService with parallel retrieval


    - Execute vector and full-text search in parallel using Mono.zip
    - _Requirements: 1.1_

  - [x] 7.2 Implement RRF (Reciprocal Rank Fusion) algorithm

    - Apply formula: Score = 1.0 / (k + rank) with k=60
    - Accumulate scores for documents in both result sets
    - _Requirements: 1.2, 1.3_

  - [x] 7.3 Implement result sorting and deduplication

    - Sort by combined RRF score descending
    - Deduplicate by source ID
    - _Requirements: 1.4_

  - [x] 7.4 Implement graceful degradation on retrieval failure

    - Continue with successful path if one fails
    - Log failure and return partial results
    - _Requirements: 1.5_
  - [x] 7.5 Integrate RerankerService for optional reranking


    - Apply reranking after RRF fusion if enabled
    - _Requirements: 1.2_
  - [ ]* 7.6 Write property test for RRF score calculation
    - **Property 1: RRF Score Calculation Correctness**
    - **Validates: Requirements 1.2, 1.3**
  - [ ]* 7.7 Write property test for RRF result ordering
    - **Property 2: RRF Result Ordering**
    - **Validates: Requirements 1.4**
  - [ ]* 7.8 Write property test for graceful degradation
    - **Property 3: Graceful Degradation on Retrieval Failure**
    - **Validates: Requirements 1.5**

- [ ] 8. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Implement SemanticChunkingService





  - [x] 9.1 Create SemanticChunkingService with sentence splitting


    - Split text into sentences preserving quoted content
    - Handle Chinese and English punctuation
    - _Requirements: 2.1_

  - [x] 9.2 Implement semantic cliff detection

    - Calculate embedding similarity between adjacent sentences
    - Mark positions below 20th percentile as cliffs
    - _Requirements: 2.2, 2.3_

  - [x] 9.3 Implement chunk merging with cliff respect

    - Merge sentences into chunks respecting cliff positions
    - Force split when chunk exceeds max size
    - _Requirements: 2.4, 2.5_
  - [ ]* 9.4 Write property test for quoted content preservation
    - **Property 4: Sentence Splitting Preserves Quoted Content**
    - **Validates: Requirements 2.1**
  - [ ]* 9.5 Write property test for adjacent similarity count
    - **Property 5: Adjacent Similarity Count**
    - **Validates: Requirements 2.2**
  - [ ]* 9.6 Write property test for cliff detection threshold
    - **Property 6: Semantic Cliff Detection Threshold**
    - **Validates: Requirements 2.3**
  - [ ]* 9.7 Write property test for chunk size constraint
    - **Property 7: Chunk Size Constraint**
    - **Validates: Requirements 2.5**

- [ ] 10. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. Implement ParentChildSearchService





  - [x] 11.1 Create ParentChildSearchService with child chunk search


    - Search in child chunks for precision
    - Retrieve corresponding parent StoryBlock
    - _Requirements: 6.1, 6.2_
  - [x] 11.2 Implement deduplication by parent


    - Keep only highest scoring child per parent
    - _Requirements: 6.3_
  - [x] 11.3 Implement context window extraction


    - Extract relevant window when parent exceeds context size
    - _Requirements: 6.4_
  - [x] 11.4 Implement context building for AI generation


    - Sort results by chapter order then block order
    - Format context with source type grouping
    - _Requirements: 6.5_
  - [ ]* 11.5 Write property test for parent deduplication
    - **Property 12: Parent-Child Search Deduplication**
    - **Validates: Requirements 6.3**
  - [ ]* 11.6 Write property test for context sort order
    - **Property 13: Context Building Sort Order**
    - **Validates: Requirements 6.5**

- [x] 12. Implement RagController





  - [x] 12.1 Create RagController with search endpoints


    - POST /api/rag/search - hybrid search
    - POST /api/rag/search/parent-child - parent-child search
    - POST /api/rag/context - build AI context
    - _Requirements: 1.1, 6.1, 6.5_

  - [x] 12.2 Add chunking endpoints

    - POST /api/rag/chunk - semantic chunking
    - _Requirements: 2.1_

  - [x] 12.3 Add monitoring endpoints

    - GET /api/rag/health - circuit breaker status
    - GET /api/rag/cache/stats - cache statistics
    - _Requirements: 3.1, 5.3_

- [x] 13. Clean up V2 RAG module





  - [x] 13.1 Remove unused V2 code


    - Delete any placeholder or incomplete implementations
    - Remove unused imports and dependencies

  - [x] 13.2 Update application.yml with RAG configuration

    - Add inkflow.rag.* properties with defaults
    - _Requirements: 5.1, 5.2, 5.3_

  - [x] 13.3 Verify all services are properly wired

    - Check Spring dependency injection
    - Verify all beans are created correctly

- [x] 14. Final Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.
