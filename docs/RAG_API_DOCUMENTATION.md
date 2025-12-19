# RAG 父子索引 API 文档

## 概述

本文档描述了 RAG 父子索引系统的 API 接口，包括检索行为的变更、新增的管理接口和使用示例。

## API 变更说明

### 检索行为变更

RAG 父子索引系统对现有检索 API 的行为进行了增强，但保持了向后兼容性。主要变更包括：

1. **检索策略升级**：从"文档级检索"升级为"小块检索，大块返回"
2. **结果结构增强**：返回结果包含更丰富的元数据信息
3. **相关性得分优化**：实现了基于父块大小的得分归一化
4. **上下文窗口支持**：大型父块支持上下文窗口提取

### 兼容性保证

- 现有 API 端点保持不变
- 请求参数格式保持兼容
- 响应结构向后兼容（新增字段，不删除现有字段）

## 核心 API 接口

### 1. 语义检索 API

#### 端点
```
POST /api/rag/search
```

#### 请求参数

```json
{
  "query": "查询文本",
  "projectId": "项目UUID",
  "topK": 10,
  "options": {
    "enableScoreNormalization": true,
    "contextWindowSize": 1000,
    "searchStrategy": "parent_child"
  }
}
```

**参数说明：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| query | string | 是 | - | 查询文本内容 |
| projectId | string | 是 | - | 项目UUID |
| topK | integer | 否 | 10 | 返回结果数量 |
| options.enableScoreNormalization | boolean | 否 | true | 是否启用相关性得分归一化 |
| options.contextWindowSize | integer | 否 | 1000 | 上下文窗口大小（字符数） |
| options.searchStrategy | string | 否 | "parent_child" | 检索策略："parent_child" 或 "document" |

#### 响应结果

```json
{
  "success": true,
  "data": {
    "results": [
      {
        "storyBlockId": "550e8400-e29b-41d4-a716-446655440000",
        "title": "第一章开头",
        "content": "完整的StoryBlock内容或上下文窗口提取...",
        "chapterId": "550e8400-e29b-41d4-a716-446655440001",
        "chapterOrder": 1,
        "blockOrder": 1,
        "relevanceScore": 0.85,
        "rawRelevanceScore": 0.92,
        "parentChunkSize": 1200,
        "isContextWindow": false,
        "matchedChildIds": [
          "550e8400-e29b-41d4-a716-446655440002"
        ],
        "metadata": {
          "chapterTitle": "第一章：开始",
          "lastUpdated": "2024-01-15T10:30:00Z",
          "wordCount": 1200
        }
      }
    ],
    "totalResults": 1,
    "searchMetadata": {
      "queryTime": 45,
      "strategy": "parent_child",
      "cacheHit": false,
      "childChunksMatched": 3,
      "parentBlocksReturned": 1
    }
  }
}
```

**响应字段说明：**

| 字段 | 类型 | 说明 |
|------|------|------|
| storyBlockId | string | 父块（StoryBlock）UUID |
| title | string | 剧情块标题 |
| content | string | 完整内容或上下文窗口提取 |
| chapterId | string | 所属章节UUID |
| chapterOrder | integer | 章节顺序 |
| blockOrder | integer | 块在章节中的顺序 |
| relevanceScore | number | 归一化后的相关性得分 (0-1) |
| rawRelevanceScore | number | 原始相关性得分 (0-1) |
| parentChunkSize | integer | 父块大小（字符数） |
| isContextWindow | boolean | 是否为上下文窗口提取 |
| matchedChildIds | array | 匹配的子块UUID列表 |
| metadata | object | 附加元数据信息 |

### 2. 上下文构建 API

#### 端点
```
POST /api/rag/context
```

#### 请求参数

```json
{
  "query": "查询文本",
  "projectId": "项目UUID",
  "maxChunks": 5,
  "maxContextLength": 3000,
  "includeMetadata": true
}
```

#### 响应结果

```json
{
  "success": true,
  "data": {
    "context": "按章节顺序排列的上下文文本...",
    "chunks": [
      {
        "storyBlockId": "550e8400-e29b-41d4-a716-446655440000",
        "title": "第一章开头",
        "excerpt": "提取的相关片段...",
        "relevanceScore": 0.85,
        "chapterInfo": {
          "chapterId": "550e8400-e29b-41d4-a716-446655440001",
          "chapterTitle": "第一章：开始",
          "chapterOrder": 1
        }
      }
    ],
    "metadata": {
      "totalContextLength": 2850,
      "chunksUsed": 3,
      "chaptersSpanned": 2,
      "queryTime": 38
    }
  }
}
```

### 3. 切片状态查询 API

#### 端点
```
GET /api/rag/chunking/status/{storyBlockId}
```

#### 响应结果

```json
{
  "success": true,
  "data": {
    "storyBlockId": "550e8400-e29b-41d4-a716-446655440000",
    "isDirty": false,
    "lastChunkedAt": "2024-01-15T10:30:00Z",
    "contentHash": "a1b2c3d4e5f6...",
    "childChunksCount": 5,
    "status": "completed",
    "processingTime": 1250,
    "error": null
  }
}
```

### 4. 批量切片触发 API

#### 端点
```
POST /api/rag/chunking/trigger
```

#### 请求参数

```json
{
  "chapterId": "550e8400-e29b-41d4-a716-446655440001",
  "storyBlockIds": [
    "550e8400-e29b-41d4-a716-446655440000",
    "550e8400-e29b-41d4-a716-446655440002"
  ],
  "immediate": true
}
```

#### 响应结果

```json
{
  "success": true,
  "data": {
    "tasksTriggered": 2,
    "estimatedCompletionTime": "2024-01-15T10:32:00Z",
    "taskIds": [
      "task-uuid-1",
      "task-uuid-2"
    ]
  }
}
```

## 管理 API

### 1. 系统配置 API

#### 获取配置
```
GET /api/rag/config
```

#### 响应结果

```json
{
  "success": true,
  "data": {
    "chunking": {
      "similarityThreshold": 0.3,
      "targetChildSize": 250,
      "minChildSize": 100,
      "maxChildSize": 400,
      "maxParentSize": 1500,
      "minParentSize": 150,
      "debounceDelayMs": 3000,
      "embeddingBatchSize": 32
    },
    "search": {
      "defaultTopK": 10,
      "enableScoreNormalization": true,
      "contextWindowSize": 1000
    },
    "performance": {
      "asyncTaskPoolSize": 8,
      "cacheEnabled": true,
      "cacheTtlMinutes": 60
    }
  }
}
```

#### 更新配置
```
PUT /api/rag/config
```

#### 请求参数

```json
{
  "chunking": {
    "similarityThreshold": 0.25,
    "targetChildSize": 300
  },
  "search": {
    "enableScoreNormalization": true
  }
}
```

### 2. 性能监控 API

#### 获取性能指标
```
GET /api/rag/metrics
```

#### 响应结果

```json
{
  "success": true,
  "data": {
    "chunking": {
      "totalTasksProcessed": 1250,
      "averageProcessingTime": 850,
      "successRate": 0.98,
      "currentQueueSize": 3
    },
    "search": {
      "totalSearches": 5420,
      "averageResponseTime": 45,
      "cacheHitRate": 0.75,
      "parentChildSearches": 4890,
      "fallbackSearches": 530
    },
    "embedding": {
      "totalEmbeddingsGenerated": 15600,
      "averageGenerationTime": 120,
      "batchProcessingRate": 0.85,
      "cacheHitRate": 0.68
    }
  }
}
```

### 3. 缓存管理 API

#### 清除缓存
```
DELETE /api/rag/cache
```

#### 请求参数

```json
{
  "cacheType": "embeddings",
  "projectId": "550e8400-e29b-41d4-a716-446655440001"
}
```

#### 缓存统计
```
GET /api/rag/cache/stats
```

#### 响应结果

```json
{
  "success": true,
  "data": {
    "embeddings": {
      "totalEntries": 8500,
      "hitRate": 0.68,
      "missRate": 0.32,
      "evictionCount": 120,
      "averageLoadTime": 115
    },
    "searchResults": {
      "totalEntries": 2300,
      "hitRate": 0.45,
      "missRate": 0.55,
      "evictionCount": 45,
      "averageLoadTime": 35
    }
  }
}
```

## 错误处理

### 错误响应格式

```json
{
  "success": false,
  "error": {
    "code": "RAG_EMBEDDING_FAILED",
    "message": "向量生成失败",
    "details": {
      "storyBlockId": "550e8400-e29b-41d4-a716-446655440000",
      "reason": "API调用超时",
      "retryable": true
    },
    "timestamp": "2024-01-15T10:30:00Z"
  }
}
```

### 常见错误码

| 错误码 | HTTP状态码 | 说明 | 是否可重试 |
|--------|------------|------|------------|
| RAG_EMBEDDING_FAILED | 500 | 向量生成失败 | 是 |
| RAG_SEARCH_TIMEOUT | 504 | 检索超时 | 是 |
| RAG_INVALID_QUERY | 400 | 查询参数无效 | 否 |
| RAG_PROJECT_NOT_FOUND | 404 | 项目不存在 | 否 |
| RAG_CHUNK_NOT_FOUND | 404 | 块不存在 | 否 |
| RAG_CONFIG_INVALID | 400 | 配置参数无效 | 否 |
| RAG_CACHE_ERROR | 500 | 缓存操作失败 | 是 |
| RAG_DATABASE_ERROR | 500 | 数据库操作失败 | 是 |

## 使用示例

### 1. 基础检索示例

```javascript
// 发起语义检索请求
const searchRequest = {
  query: "主角第一次遇到反派的情节",
  projectId: "550e8400-e29b-41d4-a716-446655440001",
  topK: 5,
  options: {
    enableScoreNormalization: true,
    contextWindowSize: 1000
  }
};

const response = await fetch('/api/rag/search', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ' + token
  },
  body: JSON.stringify(searchRequest)
});

const result = await response.json();

if (result.success) {
  result.data.results.forEach(chunk => {
    console.log(`章节: ${chunk.metadata.chapterTitle}`);
    console.log(`相关性: ${chunk.relevanceScore.toFixed(2)}`);
    console.log(`内容: ${chunk.content.substring(0, 100)}...`);
  });
}
```

### 2. 上下文构建示例

```javascript
// 为AI生成构建上下文
const contextRequest = {
  query: "描述城市的夜景",
  projectId: "550e8400-e29b-41d4-a716-446655440001",
  maxChunks: 3,
  maxContextLength: 2000
};

const response = await fetch('/api/rag/context', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ' + token
  },
  body: JSON.stringify(contextRequest)
});

const result = await response.json();

if (result.success) {
  // 使用构建的上下文进行AI生成
  const aiPrompt = `
    基于以下上下文信息，继续写作：
    
    ${result.data.context}
    
    请继续描述城市夜景的细节...
  `;
  
  // 调用AI生成API
  generateContent(aiPrompt);
}
```

### 3. 切片状态监控示例

```javascript
// 监控切片任务状态
async function monitorChunkingStatus(storyBlockId) {
  const response = await fetch(`/api/rag/chunking/status/${storyBlockId}`, {
    headers: {
      'Authorization': 'Bearer ' + token
    }
  });
  
  const result = await response.json();
  
  if (result.success) {
    const status = result.data;
    
    if (status.isDirty) {
      console.log('切片任务进行中...');
      // 等待一段时间后重新检查
      setTimeout(() => monitorChunkingStatus(storyBlockId), 2000);
    } else {
      console.log(`切片完成，生成了 ${status.childChunksCount} 个子块`);
    }
  }
}
```

### 4. 批量处理示例

```javascript
// 批量触发章节切片
async function processChapter(chapterId) {
  // 1. 获取章节内所有StoryBlock
  const blocks = await getChapterStoryBlocks(chapterId);
  
  // 2. 触发批量切片
  const triggerRequest = {
    chapterId: chapterId,
    storyBlockIds: blocks.map(b => b.id),
    immediate: false  // 使用防抖
  };
  
  const response = await fetch('/api/rag/chunking/trigger', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ' + token
    },
    body: JSON.stringify(triggerRequest)
  });
  
  const result = await response.json();
  
  if (result.success) {
    console.log(`已触发 ${result.data.tasksTriggered} 个切片任务`);
    console.log(`预计完成时间: ${result.data.estimatedCompletionTime}`);
  }
}
```

## 性能优化建议

### 1. 请求优化

- **批量请求**：尽量使用批量API减少网络开销
- **缓存利用**：相同查询在短时间内会命中缓存
- **分页处理**：大量结果使用分页避免超时

### 2. 参数调优

- **topK设置**：根据实际需求设置合理的topK值
- **上下文窗口**：根据AI模型的上下文限制调整窗口大小
- **得分归一化**：在结果质量和性能之间找到平衡

### 3. 错误处理

- **重试机制**：对可重试的错误实现指数退避重试
- **降级策略**：父子检索失败时自动降级到文档检索
- **超时设置**：设置合理的请求超时时间

## 迁移指南

### 从文档级检索迁移

1. **API兼容性**：现有API调用无需修改
2. **响应结构**：新增字段向后兼容，可选择性使用
3. **性能提升**：检索精度和上下文质量自动提升

### 配置迁移

```yaml
# 旧配置
inkflow:
  rag:
    topK: 10
    chunkSize: 500

# 新配置
inkflow:
  rag:
    search:
      defaultTopK: 10
      enableScoreNormalization: true
    chunking:
      targetChildSize: 250
      maxParentSize: 1500
```

### 监控迁移

- 新增切片任务监控指标
- 新增父子检索性能指标
- 保留原有文档检索指标作为对比

## 总结

RAG 父子索引 API 在保持向后兼容的基础上，提供了更精准的检索能力和更丰富的元数据信息。通过合理使用新的API特性，可以显著提升AI写作助手的上下文质量和用户体验。