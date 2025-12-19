# Implementation Plan

- [x] 1. 移除 VersionedEmbeddingService 死代码






  - [x] 1.1 删除 VersionedEmbeddingService.java 文件

    - 删除 `inkflow-backend-v2/src/main/java/com/inkflow/module/rag/service/VersionedEmbeddingService.java`
    - 确认无编译错误
    - _Requirements: 1.1, 1.2, 1.3_

  - [x] 1.2 验证无残留引用

    - 搜索代码库确认无 VersionedEmbeddingService 引用
    - _Requirements: 1.3_

- [x] 2. 清理 EmbeddingService 未使用方法





  - [x] 2.1 移除 generateAndSave() 方法


    - 删除 `generateAndSave(UUID projectId, String sourceType, UUID sourceId, String content, Map<String, Object> metadata)` 方法
    - _Requirements: 2.1_

  - [x] 2.2 移除 getRelevantContext() 方法

    - 删除 `getRelevantContext(UUID projectId, String query, int maxResults)` 方法
    - _Requirements: 2.2_

  - [x] 2.3 修改 recordSuccess() 和 recordFailure() 为 private

    - 将 `recordSuccess()` 改为 private
    - 将 `recordFailure(Exception e)` 改为 private
    - _Requirements: 2.3_
  - [ ]* 2.4 编写断路器功能属性测试
    - **Property 1: Circuit breaker functionality after visibility change**
    - **Validates: Requirements 2.3**

- [x] 3. Checkpoint - 确保所有测试通过





  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. 清理 SemanticChunkingService 方法可见性





  - [x] 4.1 修改内部方法为 private


    - 将 `splitAtSentenceBoundaries(String text)` 改为 private
    - 将 `calculateAdjacentSimilarities(List<String> sentences)` 改为 private
    - 将 `detectSemanticCliffs(List<Double> similarities)` 改为 private
    - _Requirements: 3.1, 3.2, 3.3_

  - [x] 4.2 移除未使用的配置访问方法

    - 删除 `getMinChildSize()` 方法
    - 删除 `getCliffThreshold()` 方法
    - _Requirements: 3.4_

  - [x] 4.3 修复中文文本分块空格问题

    - 添加 `isChinese(String text)` 辅助方法
    - 修改 `mergeSentencesIntoChunks()` 中的句子连接逻辑
    - _Requirements: 7.1, 7.2_
  - [ ]* 4.4 编写中文分块属性测试
    - **Property 4: Chinese text chunking without spaces**
    - **Property 5: Mixed text spacing rules**
    - **Validates: Requirements 7.1, 7.2, 7.3**

- [ ] 5. Checkpoint - 确保所有测试通过
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. 移除 EmbeddingCacheService 未使用方法






  - [x] 6.1 移除 exists() 方法

    - 删除 `exists(String text)` 方法
    - _Requirements: 6.1_

  - [x] 6.2 移除 evict() 方法

    - 删除 `evict(String text)` 方法
    - _Requirements: 6.2_

- [x] 7. 精简 RagController 端点





  - [x] 7.1 移除重复的搜索端点


    - 删除 `/search/body` 端点方法
    - 删除 `/search/parent-child` 端点方法
    - 删除 `/search/vector` 端点方法
    - _Requirements: 4.1, 4.4, 4.5_

  - [x] 7.2 移除未使用的上下文端点

    - 删除 `/context` 端点方法
    - 删除 `/context/body` 端点方法
    - _Requirements: 4.2_

  - [x] 7.3 移除分块端点

    - 删除 `/chunk` 端点方法
    - _Requirements: 4.3_

- [ ] 8. Checkpoint - 确保所有测试通过
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. 优化 RagProperties 默认值定义


  - [x] 9.1 为 HybridSearchConfig 提取默认值常量


    - 添加 DEFAULT_RRF_K, DEFAULT_VECTOR_WEIGHT 等常量
    - 修改 compact constructor 使用常量
    - 修改 defaults() 方法使用常量
    - _Requirements: 5.1, 5.2, 5.3_

  - [x] 9.2 为 EmbeddingConfig 提取默认值常量
    - 添加 DEFAULT_PROVIDER, DEFAULT_ENDPOINT 等常量
    - 修改 compact constructor 使用常量
    - 修改 defaults() 方法使用常量
    - _Requirements: 5.1, 5.2, 5.3_
  - [x] 9.3 为 ChunkingConfig 提取默认值常量
    - 添加 DEFAULT_MAX_CHILD_SIZE, DEFAULT_MIN_CHILD_SIZE 等常量
    - 修改 compact constructor 使用常量
    - 修改 defaults() 方法使用常量
    - _Requirements: 5.1, 5.2, 5.3_
  - [x] 9.4 为 FullTextConfig 提取默认值常量
    - 添加 DEFAULT_LANGUAGE, DEFAULT_TITLE_WEIGHT 等常量
    - 修改 compact constructor 使用常量
    - 修改 defaults() 方法使用常量
    - _Requirements: 5.1, 5.2, 5.3_
  - [x] 9.5 为 RerankerConfig 提取默认值常量
    - 添加 DEFAULT_PROVIDER, DEFAULT_ENDPOINT 等常量
    - 修改 compact constructor 使用常量
    - 修改 defaults() 方法使用常量
    - _Requirements: 5.1, 5.2, 5.3_
  - [x] 9.6 为 CircuitBreakerConfig 和 HighlightConfig 提取默认值常量
    - 添加相应的默认值常量
    - 修改 compact constructor 和 defaults() 方法
    - _Requirements: 5.1, 5.2, 5.3_
  - [ ]* 9.7 编写 RagProperties 默认值属性测试
    - **Property 2: RagProperties default value consistency**
    - **Property 3: RagProperties defaults() factory consistency**
    - **Validates: Requirements 5.2, 5.3**

- [ ] 10. Final Checkpoint - 确保所有测试通过
  - Ensure all tests pass, ask the user if questions arise.

