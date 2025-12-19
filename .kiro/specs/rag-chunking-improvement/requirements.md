# 需求文档

## 简介

本功能旨在改进 RAG 系统的切片算法，采用父子索引 + 语义断崖切分策略。通过将 StoryBlock（剧情块）作为父块，使用语义断崖检测算法将其切分为子块，实现"小块检索，大块返回"的检索策略，提升检索精度和上下文完整性。

## 术语表

- **RAG_System**：检索增强生成系统，用于为 AI 生成提供相关上下文
- **StoryBlock**：剧情块，章节内容的最小组织单位，由用户或 AI 创建
- **Parent_Chunk**：父块，即 StoryBlock，不存储向量，检索时返回完整内容
- **Child_Chunk**：子块，父块的语义切片，存储向量用于检索
- **Semantic_Cliff**：语义断崖，相邻句子余弦相似度急剧下降的位置
- **Cosine_Similarity**：余弦相似度，衡量两个向量方向相似程度的指标
- **Local_Embedding_Model**：本地部署的 Embedding 模型，用于生成文本向量
- **Debounce**：防抖，用户停止输入一段时间后才触发处理，避免频繁调用
- **Dirty_Flag**：脏标记，标识内容已修改需要重新处理的标志位
- **Content_Hash**：内容哈希，用于检测内容是否变化的 SHA-256 摘要
- **Parent_Size_Threshold**：父块大小阈值，用于控制父块的最大和最小字符数
- **Relevance_Score_Normalization**：相关性得分归一化，根据父块大小调整检索得分以确保公平性
- **Context_Window**：上下文窗口，返回给 AI 的最大字符数限制

## 需求

### 需求 1

**用户故事：** 作为系统，我希望将 StoryBlock 作为父块进行管理，以便复用现有的语义边界结构。

#### 验收标准

1. WHEN StoryBlock 内容被创建或更新 THEN RAG_System SHALL 触发异步切片任务处理该 StoryBlock
2. WHEN 切片任务执行时 THEN RAG_System SHALL 将 StoryBlock 标记为父块并记录其元数据
3. WHEN StoryBlock 被删除 THEN RAG_System SHALL 删除该 StoryBlock 对应的所有子块记录

### 需求 2

**用户故事：** 作为系统，我希望使用语义断崖检测算法切分父块，以便在话题转折处精准切分。

#### 验收标准

1. WHEN 切片算法处理父块内容 THEN RAG_System SHALL 按句号和换行符将内容拆分为句子列表
2. WHEN 句子列表生成后 THEN RAG_System SHALL 调用 Local_Embedding_Model 为每个句子生成向量
3. WHEN 句子向量生成后 THEN RAG_System SHALL 计算相邻句子之间的 Cosine_Similarity
4. WHEN 相邻句子的 Cosine_Similarity 下降幅度超过配置阈值（默认 0.3） THEN RAG_System SHALL 在该位置标记为切分点
5. WHEN 切分点确定后 THEN RAG_System SHALL 合并相邻句子形成子块，目标字数为 200-300 字符

### 需求 3

**用户故事：** 作为系统，我希望只为子块存储向量，以便优化存储空间和检索效率。

#### 验收标准

1. WHEN 子块生成完成 THEN RAG_System SHALL 为每个子块调用 Local_Embedding_Model 生成向量
2. WHEN 子块向量生成后 THEN RAG_System SHALL 将子块内容、向量和父块引用存储到 knowledge_base 表
3. WHEN 存储子块时 THEN RAG_System SHALL 记录 chunk_level 为 child、chunk_order 为子块在父块中的顺序、story_block_id 为父块 ID

### 需求 4

**用户故事：** 作为系统，我希望实现"小块检索，大块返回"的检索策略，以便提供精准检索和完整上下文。

#### 验收标准

1. WHEN 用户发起语义检索请求 THEN RAG_System SHALL 使用查询向量在子块中检索 Top-K 结果
2. WHEN 子块检索结果返回后 THEN RAG_System SHALL 根据子块的 story_block_id 查找对应的 StoryBlock
3. WHEN 返回检索结果时 THEN RAG_System SHALL 返回完整的 StoryBlock 内容而非子块内容
4. WHEN 多个子块属于同一个 StoryBlock THEN RAG_System SHALL 对该 StoryBlock 进行去重，只返回一次

### 需求 5

**用户故事：** 作为系统，我希望切片过程在后端静默执行，以便前端用户无感知。

#### 验收标准

1. WHEN StoryBlock 内容变更 THEN RAG_System SHALL 通过异步任务队列处理切片，主线程立即返回
2. WHEN 切片任务执行失败 THEN RAG_System SHALL 记录错误日志并支持重试机制
3. WHEN 切片任务执行中 THEN RAG_System SHALL 保持现有检索功能正常工作，使用旧数据响应查询

### 需求 6

**用户故事：** 作为系统，我希望支持配置切片参数，以便根据不同场景调整切片策略。

#### 验收标准

1. WHEN 系统启动时 THEN RAG_System SHALL 从配置文件加载切片参数（相似度阈值、目标子块字数范围）
2. WHEN 配置参数缺失 THEN RAG_System SHALL 使用默认值（相似度阈值 0.3、子块字数 200-300）
3. WHERE 管理员修改配置参数 THEN RAG_System SHALL 在下次切片任务中使用新参数

### 需求 7: 切片触发时机

**用户故事：** 作为系统，我需要在合适的时机触发切片处理，以确保 RAG 索引与内容保持同步，同时不影响用户的写作体验。

#### 验收标准

1. WHEN a chapter is saved (create or update) THEN RAG_System SHALL trigger chunk processing asynchronously
2. WHEN triggering chunk processing THEN RAG_System SHALL fetch all StoryBlocks for the chapter ordered by sequence
3. WHEN chapter content has not changed (same content hash) THEN RAG_System SHALL skip chunk processing
4. WHEN chunk processing is triggered THEN RAG_System SHALL NOT block the chapter save response
5. WHEN a chapter is deleted THEN RAG_System SHALL cascade delete all associated child chunks
6. WHEN multiple rapid saves occur THEN RAG_System SHALL use the latest content for processing (idempotent processing)

### 需求 8: 章节边界保护

**用户故事：** 作为系统，我需要确保切片不会跨越章节边界，以保持上下文的完整性。

#### 验收标准

1. WHEN processing StoryBlocks for chunking THEN RAG_System SHALL NOT merge blocks across chapter boundaries
2. WHEN building context for AI generation THEN RAG_System SHALL order results by chapter sequence and block order
3. WHEN a chapter is updated THEN RAG_System SHALL regenerate chunks for that chapter only

### 需求 9: 性能优化 - 防抖与增量更新

**用户故事：** 作为系统，我需要避免频繁触发向量计算，以优化性能和减少 API 调用成本。

#### 验收标准

1. WHEN user is actively typing THEN RAG_System SHALL NOT trigger embedding updates until user stops typing for 3 seconds or clicks save button
2. WHEN StoryBlock content changes THEN RAG_System SHALL mark the block as dirty (is_dirty = true) instead of immediately processing
3. WHEN chapter is saved THEN RAG_System SHALL process only dirty StoryBlocks, skipping unchanged ones
4. WHEN content hash matches existing embedding THEN RAG_System SHALL skip vector regeneration (idempotent processing)
5. WHEN only minor changes occur (typo fixes without semantic change) THEN RAG_System MAY skip vector update based on content hash comparison

### 需求 10: 异步处理与用户体验

**用户故事：** 作为用户，我希望保存操作立即响应，向量计算不应阻塞我的写作流程。

#### 验收标准

1. WHEN user clicks save THEN RAG_System SHALL immediately persist text to database and return success response
2. WHEN text is persisted THEN RAG_System SHALL queue embedding task for async processing in background thread
3. WHEN async embedding task is running THEN RAG_System SHALL continue serving search queries using existing (stale) data
4. WHEN async embedding task fails THEN RAG_System SHALL log error and support retry mechanism without affecting user
5. WHEN multiple save operations occur rapidly THEN RAG_System SHALL coalesce them and process only the latest content

### 需求 11: 父块大小控制与检索平衡

**用户故事：** 作为系统，我需要处理不同大小的 StoryBlock，以确保检索结果的公平性和上下文的相关性。

#### 验收标准

1. WHEN a StoryBlock exceeds maximum parent size threshold (default 1500 characters) THEN RAG_System SHALL split it into multiple logical parent chunks at semantic boundaries before child chunking
2. WHEN a StoryBlock is below minimum parent size threshold (default 150 characters) THEN RAG_System SHALL consider merging it with adjacent StoryBlocks in the same chapter for chunking purposes
3. WHEN returning search results THEN RAG_System SHALL apply relevance scoring that normalizes for parent chunk size to prevent bias toward larger chunks
4. WHEN building context for AI generation THEN RAG_System SHALL limit each returned parent chunk to a maximum context window (default 1000 characters) by extracting the most relevant portion around the matched child chunk
5. WHERE parent chunk size normalization is enabled THEN RAG_System SHALL calculate relevance score as (raw_similarity_score * sqrt(1.0 / parent_chunk_size_ratio)) to balance retrieval fairness

