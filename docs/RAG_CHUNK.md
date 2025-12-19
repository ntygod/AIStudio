# RAG 父子索引切片系统实现文档

## 概述

本文档详细描述了 RAG 父子索引切片系统的实现细节，包括架构设计、核心算法、API 接口和使用指南。

## 系统架构

### 整体架构

RAG 父子索引切片系统采用"小块检索，大块返回"的策略，将 StoryBlock 作为父块，通过语义断崖检测算法切分为子块，实现精准检索与完整上下文返回的平衡。

```
用户写作 → 防抖控制 → 异步切片 → 向量存储 → 语义检索 → 父块返回
```

### 核心组件

1. **SemanticChunkingService** - 语义切片服务
2. **ParentChildChunkService** - 父子块管理服务  
3. **AsyncChunkingTriggerService** - 异步切片触发服务
4. **ParentChildSearchService** - 父子检索服务

## 核心算法

### 语义断崖检测算法

语义断崖检测是系统的核心算法，用于在语义转折点进行精准切分：

#### 算法流程

1. **句子拆分**
   ```java
   // 按句号、换行符拆分
   List<String> sentences = splitIntoSentences(content);
   ```

2. **向量生成**
   ```java
   // 为每个句子生成向量
   List<float[]> vectors = generateSentenceVectors(sentences, userId);
   ```

3. **相似度计算**
   ```java
   // 计算相邻句子的余弦相似度
   List<Double> similarities = calculateAdjacentSimilarities(vectors);
   ```

4. **断崖检测**
   ```java
   // 检测相似度下降幅度超过阈值的位置
   List<Integer> cliffs = detectSemanticCliffs(similarities, threshold);
   ```

5. **子块合并**
   ```java
   // 根据切分点合并句子形成子块
   List<ChildChunk> chunks = mergeSentencesIntoChunks(sentences, cliffs);
   ```

#### 关键参数

- **相似度阈值**: 默认 0.3，可通过配置调整
- **目标子块大小**: 200-300 字符
- **最小子块大小**: 100 字符  
- **最大子块大小**: 400 字符

### 父块大小控制算法

为了处理不同大小的 StoryBlock，系统实现了父块大小控制机制：

#### 父块拆分

当 StoryBlock 超过最大阈值（默认 1500 字符）时：

```java
public List<LogicalParentChunk> processParentChunkSize(StoryBlock storyBlock) {
    if (storyBlock.getContent().length() > maxParentSize) {
        // 在语义边界处拆分
        return splitAtSemanticBoundaries(storyBlock);
    }
    return List.of(createLogicalChunk(storyBlock));
}
```

#### 父块合并

当 StoryBlock 小于最小阈值（默认 150 字符）时：

```java
public List<LogicalParentChunk> mergeSmallParentChunks(List<StoryBlock> blocks) {
    // 按章节顺序合并相邻的小块
    // 确保不跨越章节边界
}
```

## 数据模型

### 数据库扩展

#### StoryBlock 表扩展

```sql
-- 添加切片相关字段
ALTER TABLE story_blocks ADD COLUMN is_dirty BOOLEAN DEFAULT true;
ALTER TABLE story_blocks ADD COLUMN content_hash VARCHAR(64);
ALTER TABLE story_blocks ADD COLUMN last_chunked_at TIMESTAMP;

-- 创建索引
CREATE INDEX idx_story_blocks_dirty ON story_blocks(chapter_id, is_dirty) 
WHERE is_dirty = true;
```

#### KnowledgeBase 表扩展

```sql
-- 添加父子索引字段
ALTER TABLE knowledge_base ADD COLUMN chunk_level VARCHAR(20) DEFAULT 'document';
ALTER TABLE knowledge_base ADD COLUMN story_block_id UUID;
ALTER TABLE knowledge_base ADD COLUMN chunk_order INTEGER;

-- 添加外键约束
ALTER TABLE knowledge_base 
ADD CONSTRAINT fk_kb_story_block 
FOREIGN KEY (story_block_id) REFERENCES story_blocks(id) ON DELETE CASCADE;

-- 创建索引
CREATE INDEX idx_kb_story_block ON knowledge_base(story_block_id);
CREATE INDEX idx_kb_chunk_level ON knowledge_base(project_id, chunk_level);
```

### 核心 DTO

#### ChildChunk

```java
@Data
@Builder
public class ChildChunk {
    private String content;           // 子块内容
    private int order;                // 在父块中的顺序
    private int startPosition;        // 在原文中的起始位置
    private int endPosition;          // 在原文中的结束位置
}
```

#### ParentChunkResult

```java
@Data
@Builder
public class ParentChunkResult {
    private UUID storyBlockId;        // 父块ID
    private String title;             // 剧情块标题
    private String content;           // 完整内容
    private UUID chapterId;           // 所属章节ID
    private Integer chapterOrder;     // 章节顺序
    private Integer blockOrder;       // 块顺序
    private double relevanceScore;    // 相关性得分（归一化后）
    private double rawRelevanceScore; // 原始相关性得分
    private int parentChunkSize;      // 父块大小
    private boolean isContextWindow;  // 是否为上下文窗口提取
}
```

## 异步处理机制

### 防抖逻辑

系统实现了防抖机制，避免频繁触发向量计算：

```java
@Service
public class AsyncChunkingTriggerService {
    
    private final Map<UUID, ScheduledFuture<?>> pendingTasks = new ConcurrentHashMap<>();
    private static final long DEBOUNCE_DELAY_MS = 3000; // 3秒防抖
    
    public void triggerChunkingWithDebounce(UUID userId, UUID storyBlockId) {
        // 取消之前的任务
        cancelPendingTask(storyBlockId);
        
        // 安排新的延迟任务
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            parentChildChunkService.processStoryBlockChunking(userId, storyBlockId);
            pendingTasks.remove(storyBlockId);
        }, DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS);
        
        pendingTasks.put(storyBlockId, future);
    }
}
```

### 脏标记机制

使用脏标记避免重复处理：

```java
public CompletableFuture<Void> processStoryBlockChunking(UUID userId, StoryBlock storyBlock) {
    // 检查脏标记
    if (!storyBlock.isDirty()) {
        return CompletableFuture.completedFuture(null);
    }
    
    // 检查内容哈希
    String currentHash = calculateContentHash(storyBlock.getContent());
    if (currentHash.equals(storyBlock.getContentHash())) {
        // 内容未变更，清除脏标记
        storyBlock.setDirty(false);
        storyBlockRepository.save(storyBlock);
        return CompletableFuture.completedFuture(null);
    }
    
    // 执行切片处理...
}
```

## 检索策略

### 两阶段检索

系统实现了"小块检索，大块返回"的检索策略：

#### 阶段1：子块检索

```java
public List<ParentChunkResult> searchWithParentReturn(
    UUID userId, UUID projectId, String query, int topK) {
    
    // 1. 生成查询向量
    float[] queryVector = embeddingCacheService.getEmbedding(query, userId);
    
    // 2. 在子块中检索
    List<KnowledgeBase> childResults = embeddingRepository.findSimilarChildChunks(
        projectId, queryVector, topK);
    
    // 3. 根据子块查找父块
    List<StoryBlock> parentBlocks = getParentBlocksFromChildResults(childResults);
    
    // 4. 去重并返回
    return deduplicateAndBuildResults(parentBlocks, childResults);
}
```

#### 阶段2：父块返回

```java
private List<StoryBlock> getParentBlocksFromChildResults(List<KnowledgeBase> childResults) {
    Set<UUID> storyBlockIds = childResults.stream()
        .map(KnowledgeBase::getStoryBlockId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    
    return storyBlockRepository.findAllById(storyBlockIds);
}
```

### 相关性得分归一化

为了确保检索公平性，系统对不同大小的父块进行得分归一化：

```java
private List<ParentChunkResult> normalizeRelevanceScores(List<ParentChunkResult> results) {
    if (!chunkingProperties.isEnableScoreNormalization()) {
        return results;
    }
    
    // 计算平均父块大小
    double avgSize = results.stream()
        .mapToInt(ParentChunkResult::getParentChunkSize)
        .average()
        .orElse(1000.0);
    
    return results.stream()
        .map(result -> {
            double sizeRatio = result.getParentChunkSize() / avgSize;
            double normalizedScore = result.getRawRelevanceScore() * Math.sqrt(1.0 / sizeRatio);
            result.setRelevanceScore(normalizedScore);
            return result;
        })
        .collect(Collectors.toList());
}
```

### 上下文窗口提取

对于大型父块，系统提取匹配子块周围的相关上下文：

```java
private String extractContextWindow(String parentContent, ChildChunk matchedChild, int windowSize) {
    int childStart = parentContent.indexOf(matchedChild.getContent());
    if (childStart == -1) {
        return parentContent.substring(0, Math.min(windowSize, parentContent.length()));
    }
    
    // 计算上下文窗口边界
    int contextStart = Math.max(0, childStart - windowSize / 2);
    int contextEnd = Math.min(parentContent.length(), childStart + matchedChild.getContent().length() + windowSize / 2);
    
    return parentContent.substring(contextStart, contextEnd);
}
```

## 性能优化

### 批量处理

向量生成支持批量处理以提升性能：

```java
public List<float[]> generateSentenceVectors(List<String> sentences, UUID userId) {
    List<float[]> vectors = new ArrayList<>();
    
    // 按批次处理
    for (int i = 0; i < sentences.size(); i += embeddingBatchSize) {
        int endIndex = Math.min(i + embeddingBatchSize, sentences.size());
        List<String> batch = sentences.subList(i, endIndex);
        
        List<float[]> batchVectors = embeddingCacheService.getEmbeddingsBatch(batch, userId);
        vectors.addAll(batchVectors);
    }
    
    return vectors;
}
```

### 缓存策略

系统利用现有的 EmbeddingCacheService 进行向量缓存：

```java
// 利用现有缓存机制
float[] vector = embeddingCacheService.getEmbedding(sentence, userId);
```

### 异步执行

所有切片任务都在后台异步执行，不阻塞用户操作：

```java
@Async("embeddingTaskExecutor")
public CompletableFuture<Void> processStoryBlockChunking(...) {
    // 异步执行切片逻辑
}
```

## 错误处理

### 切片失败处理

```java
@Async("embeddingTaskExecutor")
public CompletableFuture<Void> processStoryBlockChunking(...) {
    try {
        // 切片逻辑
        performChunking(storyBlock);
        
        // 成功后清除脏标记
        storyBlock.setDirty(false);
        storyBlock.setLastChunkedAt(LocalDateTime.now());
        storyBlockRepository.save(storyBlock);
        
    } catch (EmbeddingGenerationException e) {
        log.error("向量生成失败，保留脏标记待重试: storyBlockId={}", storyBlock.getId(), e);
        // 不清除 is_dirty，下次保存时重试
        
    } catch (Exception e) {
        log.error("切片任务异常: storyBlockId={}", storyBlock.getId(), e);
        // 记录错误，不影响用户操作
    }
}
```

### 检索降级处理

```java
public List<ParentChunkResult> searchWithParentReturn(...) {
    try {
        // 尝试父子检索
        return performParentChildSearch(userId, projectId, query, topK);
        
    } catch (Exception e) {
        log.warn("父子检索失败，降级到原有检索: {}", e.getMessage());
        
        // 降级到原有的文档级检索
        return fallbackToDocumentSearch(userId, projectId, query, topK);
    }
}
```

## 配置参数

### 应用配置

```yaml
# application.yml
inkflow:
  rag:
    chunking:
      # 语义断崖检测
      similarity-threshold: 0.3      # 相似度下降阈值
      
      # 子块大小控制
      target-child-size: 250         # 目标子块字数
      min-child-size: 100            # 最小子块字数
      max-child-size: 400            # 最大子块字数
      
      # 父块大小控制
      max-parent-size: 1500          # 父块最大字符数
      min-parent-size: 150           # 父块最小字符数
      
      # 防抖配置
      debounce-delay-ms: 3000        # 防抖延迟（毫秒）
      
      # 向量生成配置
      embedding-batch-size: 32       # 批量处理大小
      default-embedding-provider: "openai"  # 默认向量提供商
      
    search:
      # 检索配置
      default-top-k: 10              # 默认检索数量
      
      # 相关性得分归一化
      enable-score-normalization: true     # 启用得分归一化
      
      # 上下文窗口配置
      context-window-size: 1000            # 上下文窗口大小（字符）
```

### 配置类

```java
@ConfigurationProperties(prefix = "inkflow.rag.chunking")
@Data
public class ChunkingProperties {
    
    private double similarityThreshold = 0.3;
    private int targetChildSize = 250;
    private int minChildSize = 100;
    private int maxChildSize = 400;
    private int maxParentSize = 1500;
    private int minParentSize = 150;
    private int contextWindowSize = 1000;
    private boolean enableScoreNormalization = true;
    private long debounceDelayMs = 3000;
    private int embeddingBatchSize = 32;
    private String defaultEmbeddingProvider = "openai";
}
```

## 监控和日志

### 关键指标

系统记录以下关键指标：

- 切片任务执行时间
- 向量生成成功/失败率
- 检索响应时间
- 缓存命中率

### 日志记录

```java
// 切片开始
log.info("开始处理StoryBlock切片: storyBlockId={}, contentLength={}", 
    storyBlock.getId(), storyBlock.getContent().length());

// 切片完成
log.info("StoryBlock切片完成: storyBlockId={}, childChunks={}, duration={}ms", 
    storyBlock.getId(), childChunks.size(), duration);

// 检索请求
log.debug("父子检索请求: projectId={}, query={}, topK={}", 
    projectId, query, topK);

// 检索结果
log.debug("父子检索结果: parentBlocks={}, totalChildMatches={}", 
    results.size(), totalChildMatches);
```

## 测试策略

### 单元测试

- SemanticChunkingService 测试
- ParentChildChunkService 测试  
- AsyncChunkingTriggerService 测试
- ParentChildSearchService 测试

### 集成测试

- 端到端切片流程测试
- 检索策略集成测试
- 错误处理集成测试

### 属性测试

系统实现了12个正确性属性的属性测试：

1. **P1: 章节边界保护** - 切片不跨越章节边界
2. **P2: 父子关系完整性** - 每个子块必须有有效的父块引用
3. **P3: 子块顺序连续性** - 同一父块的子块顺序连续
4. **P4: 内容完整性** - 所有子块内容合并后等于父块内容
5. **P5: 去重正确性** - 同一父块只返回一次
6. **P7: 幂等性** - 相同内容不重复计算向量
7. **P10: 父块大小边界** - 父块大小在合理范围内
8. **P11: 相关性得分公平性** - 大小归一化消除偏向
9. **P12: 上下文窗口完整性** - 提取的上下文包含匹配子块

## 部署和运维

### 部署要求

- Java 17+
- PostgreSQL 12+
- Redis（用于缓存）
- 足够的内存用于向量计算

### 运维监控

- 监控切片任务队列长度
- 监控向量生成API调用频率
- 监控数据库存储增长
- 监控检索性能指标

### 故障排查

常见问题和解决方案：

1. **切片任务堆积** - 检查向量生成API可用性
2. **检索结果为空** - 检查子块数据完整性
3. **性能下降** - 检查缓存命中率和数据库索引

## 未来优化方向

1. **智能阈值调整** - 根据内容类型动态调整相似度阈值
2. **多模态支持** - 支持图片、表格等多模态内容切片
3. **增量更新优化** - 更精细的增量更新策略
4. **分布式处理** - 支持分布式向量计算
5. **实时监控** - 更完善的实时监控和告警机制