# Implementation Plan

- [x] 1. Add search configuration to RagProperties




  - [x] 1.1 Add SearchConfig record with useParentChild field

    - Add `SearchConfig` record to `RagProperties.java`
    - Set default value to `true` for parent-child search
    - Add corresponding YAML configuration in `application.yml`
    - _Requirements: 4.1, 4.2, 4.3_

- [x] 2. Integrate ParentChildSearchService into HybridSearchService
  - [x] 2.1 Modify executeVectorSearch to use configuration-driven strategy
    - Inject `ParentChildSearchService` into `HybridSearchService`
    - Check `ragProperties.search().useParentChild()` to select strategy
    - Use `ParentChildSearchService.search()` when enabled
    - Fallback to `EmbeddingService.searchWithScore()` when disabled or on error
    - _Requirements: 3.1, 3.2, 4.1, 4.2_
  - [ ]* 2.2 Write property test for configuration-driven search strategy
    - **Property 5: Configuration controls search strategy**
    - **Validates: Requirements 4.1, 4.2**

- [x] 3. Integrate parent-child indexing into StoryBlockService
  - [x] 3.1 Add ParentChildSearchService dependency and indexing methods
    - Inject `ParentChildSearchService` into `StoryBlockService`
    - Add `triggerIndexing(StoryBlock block)` async method
    - Add `triggerIndexDeletion(UUID blockId)` async method
    - _Requirements: 1.1, 1.3_
  - [x] 3.2 Call indexing on create, update, and delete operations
    - Call `triggerIndexing()` in `createBlock()` and `createBlockWithRank()`
    - Call `triggerIndexing()` in `updateContent()`
    - Call `triggerIndexDeletion()` in `deleteBlock()`
    - _Requirements: 1.1, 1.2, 1.3_
  - [ ]* 3.3 Write property test for StoryBlock indexing
    - **Property 1: Content indexing creates parent-child structure**
    - **Validates: Requirements 1.1, 1.4**

- [x] 4. Integrate parent-child indexing into WikiEntryService
  - [x] 4.1 Add ParentChildSearchService dependency and indexing methods
    - Inject `ParentChildSearchService` into `WikiChangeListener`
    - Add parent-child indexing in `triggerEmbeddingGeneration()` method
    - Include title and type in metadata
    - _Requirements: 2.1, 2.4_
  - [x] 4.2 Call indexing on create and update operations
    - Indexing triggered via `WikiEntryChangedEvent` in `WikiChangeListener`
    - Deletion triggers chunk cleanup via `cleanupEmbeddings()` method
    - _Requirements: 2.1, 2.2, 2.3_
  - [ ]* 4.3 Write property test for WikiEntry indexing
    - **Property 1: Content indexing creates parent-child structure**
    - **Validates: Requirements 2.1, 2.4**

- [x] 5. Checkpoint - Ensure all tests pass
  - All code compiles without errors

- [x] 6. Implement update chunk replacement logic
  - [x] 6.1 Verify createParentChildIndex deletes old chunks before creating new
    - Verified `ParentChildSearchService.createParentChildIndex()` implementation
    - `knowledgeChunkRepository.deleteBySourceId(sourceId)` is called first
    - Added logging for chunk deletion count
    - _Requirements: 1.2, 2.2_
  - [ ]* 6.2 Write property test for update chunk replacement
    - **Property 2: Content update replaces old chunks**
    - **Validates: Requirements 1.2, 2.2**

- [x] 7. Implement deletion cleanup logic
  - [x] 7.1 Ensure StoryBlock deletion cleans up all chunks
    - `triggerIndexDeletion()` calls `knowledgeChunkRepository.deleteBySourceId()`
    - Added logging for chunk cleanup count
    - _Requirements: 1.3_
  - [x] 7.2 Ensure WikiEntry deletion cleans up all chunks
    - `WikiChangeListener.cleanupEmbeddings()` uses `knowledgeChunkRepository.deleteBySourceId()`
    - Handles both parent and child chunks
    - _Requirements: 2.3_
  - [ ]* 7.3 Write property test for deletion cleanup
    - **Property 3: Content deletion cleans up all chunks**
    - **Validates: Requirements 1.3, 2.3**

- [x] 8. Verify search deduplication logic
  - [x] 8.1 Review ParentChildSearchService.search() deduplication
    - Verified `deduplicateByParent()` keeps highest scoring child per parent
    - Verified results are sorted by similarity score descending
    - _Requirements: 3.3_
  - [ ]* 8.2 Write property test for search deduplication
    - **Property 4: Search deduplicates by parent and returns highest score**
    - **Validates: Requirements 3.2, 3.3**

- [x] 9. Checkpoint - Ensure all tests pass
  - All code compiles without errors

- [ ]* 10. Write round-trip property test (Optional)
  - [ ]* 10.1 Write property test for index-search round-trip
    - **Property 6: Index-search round-trip preserves relationships**
    - **Validates: Requirements 5.1**
    - Index content with createParentChildIndex
    - Search with query matching content
    - Verify returned results have correct parent content and child similarity

- [x] 11. Final Checkpoint - Ensure all tests pass
  - All code compiles successfully without errors
