# 设计文档

## 概述

本设计文档描述 RAG 父子索引切片系统的技术实现方案。系统采用"小块检索，大块返回"策略，将 StoryBlock 作为父块，通过语义断崖检测算法切分为子块，实现精准检索与完整上下文返回的平衡。

## 架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              用户写作流程                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────┐    ┌──────────┐    ┌──────────────┐    ┌─────────────────┐   │
│  │ 用户编辑  │───▶│ 防抖控制  │───▶│ 章节保存API  │───▶│ 异步切片任务队列 │   │
│  │ StoryBlock│    │ (3秒)    │    │ (立即返回)   │    │ (后台处理)      │   │
│  └──────────┘    └──────────┘    └──────────────┘    └────────┬────────┘   │
│                                                                │            │
└────────────────────────────────────────────────────────────────┼────────────┘
                                                                 │
                                                                 ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           异步切片处理流程                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                  │
│  │ 脏标记检查    │───▶│ 内容哈希比对  │───▶│ 语义断崖切分  │                  │
│  │ is_dirty=true│    │ 跳过未变更    │    │ 生成子块      │                  │
│  └──────────────┘    └──────────────┘    └──────┬───────┘                  │
│                                                  │                          │
│                                                  ▼                          │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                  │
│  │ 更新父块引用  │◀───│ 存储子块向量  │◀───│ 生成子块向量  │                  │
│  │ 清除脏标记    │    │ knowledge_base│    │ Embedding API│                  │
│  └──────────────┘    └──────────────┘    └──────────────┘                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                              检索流程                                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐  │
│  │ 用户查询  │───▶│ 查询向量生成  │───▶│ 子块向量检索  │───▶│ 父块内容返回  │  │
│  │          │    │              │    │ Top-K匹配    │    │ 去重合并      │  │
│  └──────────┘    └──────────────┘    └──────────────┘    └──────────────┘  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 本地模型集成

### 模型配置

系统集成以下本地部署模型：

**1. BGE-M3（向量化模型）**
- 用途：生成文本向量用于语义检索
- 端点：`http://localhost:8093/v1/embeddings`（可配置）
- 向量维度：1024（BGE-M3 输出维度）
- 存储类型：halfvec（半精度，节省 50% 空间）
- 支持批量处理以提升性能

**2. bge-reranker-v2-m3（重排序模型）**
- 用途：
  1. 检索结果重排序（提升精度）
  2. 语义断崖检测（计算句子相似度）
  3. 意图识别辅助（可选）
- 端点：`http://localhost:8002/v1/rerank`（可配置）
- 输入：查询文本 + 候选文本列表
- 输出：相关性得分（0-1）

### 配置示例

```yaml
# application.yml
inkflow:
  rag:
    embedding:
      provider: local-bge  # 使用本地 BGE-M3
      endpoint: http://localhost:8093
      model: bge-m3
      dimension: 1024      # BGE-M3 输出维度
      batch-size: 32       # 批量处理大小
      timeout-ms: 5000
    
    reranker:
      provider: local-bge  # 使用本地 bge-reranker-v2-m3
      endpoint: http://localhost:8082/rerank
      model: bge-reranker-v2-m3
      enabled: true
      top-k: 10  # 重排序前保留的候选数量
      timeout-ms: 3000
```

## 组件设计

### 1. LocalEmbeddingService（本地向量化服务）

封装 qwen-embedding-4b 模型调用。

```java
@Service
@RequiredArgsConstructor
public class LocalEmbeddingService {
    
    private final RestTemplate restTemplate;
    private final EmbeddingProperties embeddingProperties;
    
    /**
     * 生成单个文本的向量
     * @param text 输入文本
     * @return 向量数组
     */
    public Mono<float[]> generateEmbedding(String text);
    
    /**
     * 批量生成向量（性能优化）
     * @param texts 文本列表
     * @return 向量列表
     */
    public Mono<List<float[]>> generateEmbeddingsBatch(List<String> texts);
    
    /**
     * 调用 qwen-embedding-4b API
     */
    private Mono<EmbeddingResponse> callEmbeddingAPI(List<String> texts);
}
```

### 2. LocalRerankerService（本地重排序服务）

封装 bge-reranker-v2-m3 模型调用。

```java
@Service
@RequiredArgsConstructor
public class LocalRerankerService {
    
    private final RestTemplate restTemplate;
    private final RerankerProperties rerankerProperties;
    
    /**
     * 重排序检索结果
     * @param query 查询文本
     * @param candidates 候选文本列表
     * @return 重排序后的结果（带得分）
     */
    public Mono<List<RerankResult>> rerank(String query, List<String> candidates);
    
    /**
     * 计算两个文本的相似度得分
     * 用于语义断崖检测
     * @param text1 文本1
     * @param text2 文本2
     * @return 相似度得分（0-1）
     */
    public Mono<Double> calculateSimilarity(String text1, String text2);
    
    /**
     * 批量计算相邻句子的相似度
     * @param sentences 句子列表
     * @return 相似度列表（长度 = sentences.size() - 1）
     */
    public Mono<List<Double>> calculateAdjacentSimilarities(List<String> sentences);
}
```

### 3. SemanticChunkingService（语义切片服务）

负责将 StoryBlock 内容按语义断崖切分为子块，并处理父块大小控制。

```java
@Service
@RequiredArgsConstructor
public class SemanticChunkingService {
    
    private final LocalRerankerService rerankerService;
    private final ChunkingProperties chunkingProperties;
    
    // 配置参数
    private double similarityThreshold = 0.3;  // 相似度下降阈值
    private int targetChildChunkSize = 250;    // 目标子块字数 (200-300)
    private int minChildChunkSize = 100;       // 最小子块字数
    private int maxChildChunkSize = 400;       // 最大子块字数
    
    // 父块大小控制参数
    private int maxParentSize = 1500;          // 父块最大字符数
    private int minParentSize = 150;           // 父块最小字符数
    
    /**
     * 将父块内容切分为子块
     * 使用 bge-reranker-v2-m3 进行语义断崖检测
     * 
     * @param content 父块（StoryBlock）内容
     * @return 子块内容列表
     */
    public Mono<List<ChildChunk>> splitIntoChildChunks(String content);
    
    /**
     * 检查并处理父块大小
     * 如果父块过大，在语义边界处拆分为多个逻辑父块
     * 如果父块过小，标记为需要与相邻块合并
     * 
     * @param storyBlock 原始 StoryBlock
     * @return 处理后的父块列表
     */
    public Mono<List<LogicalParentChunk>> processParentChunkSize(StoryBlock storyBlock);
    
    /**
     * 合并相邻的小 StoryBlock
     * 
     * @param storyBlocks 章节内按顺序排列的 StoryBlock 列表
     * @return 合并后的逻辑父块列表
     */
    public Mono<List<LogicalParentChunk>> mergeSmallParentChunks(List<StoryBlock> storyBlocks);
    
    /**
     * 按句号和换行符拆分为句子
     */
    private List<String> splitIntoSentences(String content);
    
    /**
     * 使用 bge-reranker 计算相邻句子的相似度
     * 替代传统的余弦相似度计算
     */
    private Mono<List<Double>> calculateAdjacentSimilarities(List<String> sentences) {
        return rerankerService.calculateAdjacentSimilarities(sentences);
    }
    
    /**
     * 检测语义断崖位置（相似度急剧下降点）
     * 使用动态阈值检测
     */
    private List<Integer> detectSemanticCliffs(List<Double> similarities);
    
    /**
     * 合并句子形成子块，确保字数在目标范围内
     */
    private List<ChildChunk> mergeSentencesIntoChunks(
        List<String> sentences, 
        List<Integer> cliffPositions
    );
}
```

### 2. ParentChildChunkService（父子块管理服务）

管理父块（StoryBlock）与子块的关联关系。

```java
@Service
public class ParentChildChunkService {
    
    /**
     * 处理 StoryBlock 的切片任务
     * 1. 检查脏标记和内容哈希
     * 2. 调用语义切片服务生成子块
     * 3. 为子块生成向量并存储
     * 4. 更新父块引用，清除脏标记
     */
    @Async("embeddingTaskExecutor")
    public CompletableFuture<Void> processStoryBlockChunking(
        UUID userId, 
        StoryBlock storyBlock
    );
    
    /**
     * 批量处理章节内所有脏 StoryBlock
     */
    @Async("embeddingTaskExecutor")
    public CompletableFuture<Void> processChapterChunking(
        UUID userId, 
        UUID chapterId
    );
    
    /**
     * 删除 StoryBlock 对应的所有子块
     */
    public void deleteChildChunks(UUID storyBlockId);
    
    /**
     * 根据子块检索结果获取父块内容
     * 实现去重逻辑
     */
    public List<StoryBlock> getParentBlocksFromChildResults(
        List<KnowledgeBase> childChunkResults
    );
}
```

### 3. AsyncChunkingTriggerService（异步切片触发服务）

处理切片任务的触发时机和防抖逻辑。

```java
@Service
public class AsyncChunkingTriggerService {
    
    private final Map<UUID, ScheduledFuture<?>> pendingTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private static final long DEBOUNCE_DELAY_MS = 3000; // 3秒防抖
    
    /**
     * 触发 StoryBlock 切片（带防抖）
     * 用户停止输入3秒后才执行
     */
    public void triggerChunkingWithDebounce(UUID userId, UUID storyBlockId);
    
    /**
     * 立即触发切片（用户点击保存按钮时）
     */
    public void triggerChunkingImmediate(UUID userId, UUID storyBlockId);
    
    /**
     * 标记 StoryBlock 为脏
     */
    public void markDirty(UUID storyBlockId);
    
    /**
     * 取消待处理的防抖任务
     */
    public void cancelPendingTask(UUID storyBlockId);
}
```

### 4. ParentChildSearchService（父子检索服务）

实现"小块检索，大块返回 + 重排序"的检索策略。

```java
@Service
@RequiredArgsConstructor
public class ParentChildSearchService {
    
    private final LocalEmbeddingService embeddingService;
    private final LocalRerankerService rerankerService;
    private final EmbeddingRepository embeddingRepository;
    private final RerankerProperties rerankerProperties;
    
    /**
     * 语义检索（父子索引策略 + 重排序）
     * 
     * 流程：
     * 1. 使用 qwen-embedding 生成查询向量
     * 2. 在子块中检索 Top-K*2 结果（召回阶段）
     * 3. 使用 bge-reranker 重排序（精排阶段）
     * 4. 根据子块的 story_block_id 查找父块
     * 5. 去重并返回完整的 StoryBlock 内容
     * 
     * @param userId 用户ID
     * @param projectId 项目ID
     * @param query 查询文本
     * @param topK 返回结果数量
     * @return 父块结果列表
     */
    public Mono<List<ParentChunkResult>> searchWithParentReturn(
        UUID userId,
        UUID projectId,
        String query,
        int topK
    );
    
    /**
     * 两阶段检索：向量召回 + 重排序精排
     * 
     * @param query 查询文本
     * @param projectId 项目ID
     * @param topK 最终返回数量
     * @return 重排序后的子块结果
     */
    private Mono<List<KnowledgeBase>> twoStageRetrieval(
        String query,
        UUID projectId,
        int topK
    ) {
        // 阶段1：向量召回（召回 topK*2 个候选）
        return embeddingService.generateEmbedding(query)
            .flatMap(queryVector -> 
                embeddingRepository.findSimilarChildChunks(
                    projectId, 
                    queryVector, 
                    topK * 2  // 召回更多候选用于重排序
                )
            )
            // 阶段2：重排序精排
            .flatMap(candidates -> {
                if (!rerankerProperties.isEnabled() || candidates.size() <= topK) {
                    return Mono.just(candidates.subList(0, Math.min(topK, candidates.size())));
                }
                
                List<String> candidateTexts = candidates.stream()
                    .map(KnowledgeBase::getContent)
                    .collect(Collectors.toList());
                
                return rerankerService.rerank(query, candidateTexts)
                    .map(rerankResults -> {
                        // 按重排序得分排序并取 topK
                        return rerankResults.stream()
                            .limit(topK)
                            .map(result -> candidates.get(result.getIndex()))
                            .collect(Collectors.toList());
                    });
            });
    }
    
    /**
     * 构建 AI 生成上下文
     * 按章节顺序和块顺序排列结果
     * 应用上下文窗口限制和相关性得分归一化
     */
    public Mono<String> buildContextForGeneration(
        UUID userId,
        UUID projectId,
        String query,
        int maxChunks
    );
    
    /**
     * 应用相关性得分归一化
     * 根据父块大小调整检索得分以确保公平性
     * 
     * @param results 原始检索结果
     * @return 归一化后的结果
     */
    private List<ParentChunkResult> normalizeRelevanceScores(List<ParentChunkResult> results);
    
    /**
     * 提取上下文窗口
     * 从大父块中提取匹配子块周围的相关部分
     * 
     * @param parentContent 完整父块内容
     * @param matchedChildChunk 匹配的子块
     * @param contextWindowSize 上下文窗口大小
     * @return 提取的上下文片段
     */
    private String extractContextWindow(
        String parentContent, 
        ChildChunk matchedChildChunk, 
        int contextWindowSize
    );
}
```

## 数据模型

### StoryBlock 表扩展

在现有 `story_blocks` 表中添加字段：

```sql
ALTER TABLE story_blocks ADD COLUMN IF NOT EXISTS is_dirty BOOLEAN DEFAULT true;
ALTER TABLE story_blocks ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);
ALTER TABLE story_blocks ADD COLUMN IF NOT EXISTS last_chunked_at TIMESTAMP;

CREATE INDEX idx_story_blocks_dirty ON story_blocks(chapter_id, is_dirty) WHERE is_dirty = true;
```

### KnowledgeBase 表扩展

在现有 `knowledge_base` 表中添加字段以支持父子索引：

```sql
-- 添加子块相关字段
ALTER TABLE knowledge_base ADD COLUMN IF NOT EXISTS chunk_level VARCHAR(20) DEFAULT 'document';
ALTER TABLE knowledge_base ADD COLUMN IF NOT EXISTS story_block_id UUID;
ALTER TABLE knowledge_base ADD COLUMN IF NOT EXISTS chunk_order INTEGER;

-- 添加外键约束
ALTER TABLE knowledge_base 
ADD CONSTRAINT fk_kb_story_block 
FOREIGN KEY (story_block_id) REFERENCES story_blocks(id) ON DELETE CASCADE;

-- 添加索引
CREATE INDEX idx_kb_story_block ON knowledge_base(story_block_id) WHERE story_block_id IS NOT NULL;
CREATE INDEX idx_kb_chunk_level ON knowledge_base(project_id, chunk_level);

-- chunk_level 可选值: 'document'(原有整体嵌入), 'child'(子块)
```

### ChildChunk DTO

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

### ParentChunkResult DTO

```java
@Data
@Builder
public class ParentChunkResult {
    private UUID storyBlockId;        // 父块ID
    private String title;             // 剧情块标题
    private String content;           // 完整内容（可能是提取的上下文窗口）
    private UUID chapterId;           // 所属章节ID
    private Integer chapterOrder;     // 章节顺序
    private Integer blockOrder;       // 块顺序
    private double relevanceScore;    // 相关性得分（归一化后）
    private double rawRelevanceScore; // 原始相关性得分
    private int parentChunkSize;      // 父块大小（用于归一化）
    private boolean isContextWindow;  // 是否为上下文窗口提取
}
```

### LogicalParentChunk DTO

```java
@Data
@Builder
public class LogicalParentChunk {
    private String content;           // 逻辑父块内容
    private List<UUID> storyBlockIds; // 包含的 StoryBlock ID 列表
    private UUID chapterId;           // 所属章节ID
    private int startOrder;           // 起始块顺序
    private int endOrder;             // 结束块顺序
    private boolean isSplit;          // 是否为拆分的大块
    private boolean isMerged;         // 是否为合并的小块
}
```

### 5. IntentRecognitionEnhancementService（意图识别增强服务）

利用 bge-reranker-v2-m3 增强对话编排系统的意图识别能力。

```java
@Service
@RequiredArgsConstructor
public class IntentRecognitionEnhancementService {
    
    private final LocalRerankerService rerankerService;
    
    // 预定义的意图模板（用于相似度匹配）
    private static final Map<UserIntent, List<String>> INTENT_TEMPLATES = Map.of(
        UserIntent.INITIALIZE_PROJECT, List.of(
            "我想创作一个小说",
            "开始写一个新故事",
            "帮我创建一个项目"
        ),
        UserIntent.GENERATE_OUTLINE, List.of(
            "生成故事大纲",
            "帮我规划章节结构",
            "创建剧情框架"
        ),
        UserIntent.GENERATE_CHAPTER, List.of(
            "生成第一章内容",
            "写这一章的正文",
            "继续写下一章"
        ),
        UserIntent.ASK_QUESTION, List.of(
            "这个设定合理吗？",
            "我应该怎么写？",
            "有什么建议？"
        )
        // ... 其他意图模板
    );
    
    /**
     * 使用 bge-reranker 增强意图识别
     * 当规则识别置信度较低时，使用模板匹配
     * 
     * @param userInput 用户输入
     * @param ruleBasedResult 规则识别结果
     * @return 增强后的意图结果
     */
    public Mono<IntentResult> enhanceIntentRecognition(
        String userInput,
        IntentResult ruleBasedResult
    ) {
        // 如果规则识别置信度已经很高，直接返回
        if (ruleBasedResult.getConfidence() >= 0.8) {
            return Mono.just(ruleBasedResult);
        }
        
        // 使用 reranker 进行模板匹配
        return findBestMatchingIntent(userInput)
            .map(matchResult -> {
                // 如果模板匹配得分更高，使用模板匹配结果
                if (matchResult.getScore() > ruleBasedResult.getConfidence()) {
                    return IntentResult.of(
                        matchResult.getIntent(),
                        matchResult.getScore(),
                        null
                    );
                }
                return ruleBasedResult;
            });
    }
    
    /**
     * 找到最匹配的意图模板
     */
    private Mono<IntentMatchResult> findBestMatchingIntent(String userInput) {
        // 收集所有意图模板
        List<String> allTemplates = new ArrayList<>();
        List<UserIntent> templateIntents = new ArrayList<>();
        
        INTENT_TEMPLATES.forEach((intent, templates) -> {
            templates.forEach(template -> {
                allTemplates.add(template);
                templateIntents.add(intent);
            });
        });
        
        // 使用 reranker 计算相似度
        return rerankerService.rerank(userInput, allTemplates)
            .map(results -> {
                if (results.isEmpty()) {
                    return new IntentMatchResult(
                        UserIntent.GENERAL_CHAT, 
                        0.0
                    );
                }
                
                // 返回得分最高的意图
                RerankResult best = results.get(0);
                return new IntentMatchResult(
                    templateIntents.get(best.getIndex()),
                    best.getScore()
                );
            });
    }
    
    @Data
    @AllArgsConstructor
    private static class IntentMatchResult {
        private UserIntent intent;
        private double score;
    }
}
```

## 配置参数

```yaml
# application.yml
inkflow:
  rag:
    # 本地 Embedding 模型配置（BGE-M3）
    embedding:
      provider: local-bge
      endpoint: http://localhost:8093
      model: bge-m3
      dimension: 1024              # BGE-M3 向量维度
      batch-size: 32               # 批量处理大小
      timeout-ms: 5000             # 超时时间
      max-retries: 3               # 最大重试次数
      
    # 本地 Reranker 模型配置（bge-reranker-v2-m3）
    reranker:
      provider: local-bge
      endpoint: http://localhost:8082
      model: bge-reranker-v2-m3
      enabled: true                # 是否启用重排序
      top-k-multiplier: 2          # 召回倍数（召回 topK * 2 用于重排序）
      timeout-ms: 3000
      max-retries: 2
      
      # 意图识别增强配置
      intent-enhancement:
        enabled: true              # 是否启用意图识别增强
        confidence-threshold: 0.6  # 使用模板匹配的置信度阈值
    
    # 语义切片配置
    chunking:
      # 语义断崖检测
      similarity-threshold: 0.3    # 相似度下降阈值
      use-reranker: true           # 使用 reranker 计算相似度（更准确）
      
      # 子块大小控制
      target-child-size: 250       # 目标子块字数
      min-child-size: 100          # 最小子块字数
      max-child-size: 400          # 最大子块字数
      
      # 父块大小控制
      max-parent-size: 1500        # 父块最大字符数
      min-parent-size: 150         # 父块最小字符数
      enable-parent-splitting: true # 启用父块拆分
      enable-parent-merging: true  # 启用父块合并
      
      # 防抖配置
      debounce-delay-ms: 3000      # 防抖延迟（毫秒）
      
    # 检索配置
    search:
      default-top-k: 10            # 默认检索数量
      use-two-stage: true          # 使用两阶段检索（召回+重排序）
      recall-multiplier: 2         # 召回倍数
      
      # 相关性得分归一化
      enable-score-normalization: true    # 启用得分归一化
      normalization-factor: 1.0           # 归一化因子
      
      # 上下文窗口配置
      context-window-size: 1000           # 上下文窗口大小（字符）
      context-overlap-size: 100           # 上下文重叠大小（字符）
```

## 正确性属性

### 切片正确性

1. **P1: 章节边界保护** - 切片不跨越章节边界
   - 验证: 每个子块的 story_block_id 对应的 StoryBlock 属于同一章节

2. **P2: 父子关系完整性** - 每个子块必须有有效的父块引用
   - 验证: knowledge_base.story_block_id 必须存在于 story_blocks 表

3. **P3: 子块顺序连续性** - 同一父块的子块顺序连续
   - 验证: 同一 story_block_id 的子块 chunk_order 从 1 开始连续递增

4. **P4: 内容完整性** - 所有子块内容合并后等于父块内容
   - 验证: 按 chunk_order 合并子块内容应与 StoryBlock.content 一致

### 检索正确性

5. **P5: 去重正确性** - 同一父块只返回一次
   - 验证: 检索结果中不存在重复的 storyBlockId

6. **P6: 顺序正确性** - 结果按章节和块顺序排列
   - 验证: 结果按 chapterOrder, blockOrder 升序排列

### 性能正确性

7. **P7: 幂等性** - 相同内容不重复计算向量
   - 验证: content_hash 相同时跳过向量生成

8. **P8: 防抖有效性** - 快速连续保存只处理最后一次
   - 验证: 3秒内多次保存只触发一次切片任务

9. **P9: 异步非阻塞** - 切片任务不阻塞保存响应
   - 验证: 保存 API 响应时间 < 100ms

### 父块大小控制正确性

10. **P10: 父块大小边界** - 父块大小在合理范围内
    - 验证: 拆分后的逻辑父块 ≤ maxParentSize，合并后的逻辑父块 ≥ minParentSize

11. **P11: 相关性得分公平性** - 大小归一化消除偏向
    - 验证: 大父块的归一化得分不会系统性高于小父块

12. **P12: 上下文窗口完整性** - 提取的上下文包含匹配子块
    - 验证: 上下文窗口必须包含触发匹配的子块内容

## 错误处理

### 切片失败处理

```java
@Async("embeddingTaskExecutor")
public CompletableFuture<Void> processStoryBlockChunking(...) {
    try {
        // 切片逻辑
    } catch (EmbeddingGenerationException e) {
        log.error("向量生成失败，保留脏标记待重试: {}", storyBlockId);
        // 不清除 is_dirty，下次保存时重试
    } catch (Exception e) {
        log.error("切片任务异常: {}", e.getMessage());
        // 记录错误，不影响用户操作
    }
}
```

### 检索降级处理

```java
public Mono<List<ParentChunkResult>> searchWithParentReturn(...) {
    return childChunkSearch(...)
        .onErrorResume(e -> {
            log.warn("子块检索失败，降级到原有检索: {}", e.getMessage());
            return fallbackToDocumentSearch(...);
        });
}
```

## 与现有系统集成

### ChapterService 集成点

```java
// 在 ChapterService.update() 中
if (request.storyBlocks() != null) {
    for (StoryBlockDto block : request.storyBlocks()) {
        // 标记脏块
        asyncChunkingTriggerService.markDirty(block.getId());
    }
    // 触发章节级切片（处理所有脏块）
    parentChildChunkService.processChapterChunking(userId, chapterId);
}
```

### EmbeddingService 集成点

```java
// 在 EmbeddingService.getRelevantContext() 中
// 优先使用父子检索策略
return parentChildSearchService.buildContextForGeneration(...)
    .switchIfEmpty(Mono.defer(() -> 
        // 降级到原有检索
        this.getRelevantContextLegacy(...)
    ));
```
