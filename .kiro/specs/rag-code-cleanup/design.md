# Design Document

## Overview

本设计文档描述 RAG 模块代码清理的技术实现方案。基于 `docs/RAG_IMPROVEMENT_DISCUSSION.md` 的分析结果，通过移除死代码、清理未使用方法、优化代码结构来提高代码质量。

## Architecture

清理工作不改变现有架构，仅移除未使用的代码和优化现有实现。

```
RAG Module (清理后)
├── config/
│   ├── RagProperties.java      # 优化默认值定义
│   └── RagConfig.java          # 保持不变
├── controller/
│   └── RagController.java      # 精简端点
├── service/
│   ├── HybridSearchService.java       # 保持不变
│   ├── EmbeddingService.java          # 清理未使用方法
│   ├── EmbeddingCacheService.java     # 标记废弃方法
│   ├── SemanticChunkingService.java   # 优化方法可见性
│   ├── ParentChildSearchService.java  # 保持不变
│   ├── FullTextSearchService.java     # 保持不变
│   └── RerankerService.java           # 保持不变
│   └── [删除] VersionedEmbeddingService.java
├── entity/
│   └── KnowledgeChunk.java     # 保持不变
├── repository/
│   └── KnowledgeChunkRepository.java  # 保持不变
└── dto/
    └── SearchResult.java       # 保持不变
```

## Components and Interfaces

### 1. VersionedEmbeddingService 移除

**当前状态：** 整个类未被使用，所有 10 个公共方法都没有外部调用。

**移除策略：**
1. 删除 `VersionedEmbeddingService.java` 文件
2. 确认无编译错误
3. 确认无运行时依赖

### 2. EmbeddingService 清理

**移除的方法：**
- `generateAndSave(UUID projectId, String sourceType, UUID sourceId, String content, Map<String, Object> metadata)` - 未被调用
- `getRelevantContext(UUID projectId, String query, int maxResults)` - 未被调用

**可见性调整：**
- `recordSuccess()` - public → private
- `recordFailure(Exception e)` - public → private

**保留的公共方法：**
- `generateEmbedding(String text)` - 核心方法
- `generateEmbeddingsBatch(List<String> texts)` - 核心方法
- `deleteBySourceId(UUID sourceId)` - 被 WikiChangeListener 使用
- `deleteByProjectId(UUID projectId)` - 被 RagController 使用
- `search(...)` 系列方法 - 被 HybridSearchService 使用
- `searchWithScore(...)` - 被 HybridSearchService 使用
- `getStatistics(UUID projectId)` - 运维用途
- 断路器相关方法 - 运维用途

### 3. SemanticChunkingService 优化

**可见性调整（public → private）：**
- `splitAtSentenceBoundaries(String text)` - 内部实现细节
- `calculateAdjacentSimilarities(List<String> sentences)` - 内部实现细节
- `detectSemanticCliffs(List<Double> similarities)` - 内部实现细节

**移除的方法：**
- `getMinChildSize()` - 未被调用
- `getCliffThreshold()` - 未被调用

**保留的公共方法：**
- `splitIntoChildChunks(String content)` - 被 RagController 调用
- `chunkText(String text)` - 被 ParentChildSearchService 调用
- `simpleChunk(String text, int size, int overlap)` - 被 RagController 调用
- `getMaxChildSize()` - 被 RagController 调用

**中文处理修复：**
```java
// 修改 mergeSentencesIntoChunks 方法中的句子连接逻辑
private boolean isChinese(String text) {
    return text.matches(".*[\\u4e00-\\u9fa5].*");
}

// 在连接句子时判断
if (!buffer.isEmpty() && !isChinese(sentence)) {
    buffer.append(" ");
}
buffer.append(sentence);
```

### 4. RagController 端点精简

**移除的端点：**
| 端点 | 原因 |
|------|------|
| `POST /search/body` | 与 `/search` 重复 |
| `POST /context` | AI Tool 内部使用，不需要 HTTP API |
| `POST /context/body` | AI Tool 内部使用，不需要 HTTP API |
| `POST /chunk` | 内部分块逻辑，不需要暴露 |

**移除的端点（内部实现细节）：**
| 端点 | 原因 |
|------|------|
| `POST /search/parent-child` | 内部实现细节，不应暴露给外部 |
| `POST /search/vector` | 内部实现细节，不应暴露给外部 |

**保留的端点：**
| 端点 | 用途 |
|------|------|
| `POST /search` | 统一搜索入口 |
| `GET /health` | 服务健康检查 |
| `GET /cache/stats` | 缓存统计 |
| `GET /stats/{projectId}` | 项目嵌入统计 |
| `DELETE /project/{projectId}` | 删除项目嵌入 |
| `DELETE /source/{sourceId}` | 删除来源嵌入 |
| `POST /cache/clear` | 清空缓存 |
| `POST /circuit-breaker/reset` | 重置断路器 |

### 5. RagProperties 默认值优化

**当前问题：** 默认值在 compact constructor 和 `defaults()` 方法中重复定义。

**优化方案：**
```java
public record HybridSearchConfig(
    double rrfK,
    double vectorWeight,
    double keywordWeight,
    boolean enableReranker,
    int defaultTopK,
    int recallMultiplier,
    double similarityThreshold
) {
    // 常量定义
    public static final double DEFAULT_RRF_K = 60.0;
    public static final double DEFAULT_VECTOR_WEIGHT = 0.7;
    public static final double DEFAULT_KEYWORD_WEIGHT = 0.3;
    public static final boolean DEFAULT_ENABLE_RERANKER = true;
    public static final int DEFAULT_TOP_K = 10;
    public static final int DEFAULT_RECALL_MULTIPLIER = 2;
    public static final double DEFAULT_SIMILARITY_THRESHOLD = 0.95;
    
    public HybridSearchConfig {
        if (rrfK <= 0) rrfK = DEFAULT_RRF_K;
        if (vectorWeight <= 0) vectorWeight = DEFAULT_VECTOR_WEIGHT;
        if (keywordWeight <= 0) keywordWeight = DEFAULT_KEYWORD_WEIGHT;
        if (defaultTopK <= 0) defaultTopK = DEFAULT_TOP_K;
        if (recallMultiplier <= 0) recallMultiplier = DEFAULT_RECALL_MULTIPLIER;
        if (similarityThreshold <= 0) similarityThreshold = DEFAULT_SIMILARITY_THRESHOLD;
    }
    
    public static HybridSearchConfig defaults() {
        return new HybridSearchConfig(
            DEFAULT_RRF_K,
            DEFAULT_VECTOR_WEIGHT,
            DEFAULT_KEYWORD_WEIGHT,
            DEFAULT_ENABLE_RERANKER,
            DEFAULT_TOP_K,
            DEFAULT_RECALL_MULTIPLIER,
            DEFAULT_SIMILARITY_THRESHOLD
        );
    }
}
```

### 6. EmbeddingCacheService 未使用方法移除

**移除的方法：**
- `exists(String text)` - 未被调用，直接使用 `get()` 更高效
- `evict(String text)` - 未被调用

**保留的方法：**
- `get(String text)` - 核心方法，被 EmbeddingService 使用
- `put(String text, float[] embedding)` - 核心方法，被 EmbeddingService 使用
- `clear()` - 运维用途，被 RagController 使用
- `getStats()` - 运维用途，被 RagController 使用

## Data Models

无数据模型变更。

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*



Based on the prework analysis, the following properties are testable:

Property 1: Circuit breaker functionality after visibility change
*For any* sequence of embedding generation calls with failures, when the failure count exceeds the threshold, the circuit breaker should open and subsequent calls should fail fast without calling the external service.
**Validates: Requirements 2.3**

Property 2: RagProperties default value consistency
*For any* RagProperties configuration with invalid values (null, zero, or negative), the compact constructor should apply the same default values as defined in the constants.
**Validates: Requirements 5.2**

Property 3: RagProperties defaults() factory consistency
*For any* call to `defaults()` factory method, the returned configuration should have values equal to the defined constants.
**Validates: Requirements 5.3**

Property 4: Chinese text chunking without spaces
*For any* Chinese text input, when chunked by SemanticChunkingService, the resulting chunks should not contain spaces between Chinese characters that were not in the original text.
**Validates: Requirements 7.1**

Property 5: Mixed text spacing rules
*For any* mixed Chinese and English text, when chunked by SemanticChunkingService, spaces should only be added between English sentences, not between Chinese sentences or between Chinese and English text.
**Validates: Requirements 7.2, 7.3**

## Error Handling

清理工作不改变现有错误处理逻辑。移除的代码不影响错误处理流程。

## Testing Strategy

### Unit Tests

1. **编译验证测试**
   - 移除 VersionedEmbeddingService 后编译通过
   - 移除 EmbeddingService 未使用方法后编译通过
   - 修改 SemanticChunkingService 方法可见性后编译通过
   - 移除 RagController 端点后编译通过

2. **功能回归测试**
   - 现有 RAG 模块测试全部通过
   - HybridSearchService 搜索功能正常
   - EmbeddingService 向量生成功能正常

### Property-Based Tests

使用 jqwik 框架进行属性测试：

1. **Circuit Breaker Property Test**
   - 生成随机失败序列
   - 验证断路器状态转换正确

2. **RagProperties Default Value Property Test**
   - 生成随机无效配置值
   - 验证默认值应用正确

3. **Chinese Text Chunking Property Test**
   - 生成随机中文文本
   - 验证分块结果不包含额外空格

4. **Mixed Text Spacing Property Test**
   - 生成随机中英混合文本
   - 验证空格规则正确应用

