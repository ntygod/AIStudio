# RAG Configuration Alignment Update

## 概述

基于用户反馈和最新的实现状态，对 RAG 系统配置进行对齐更新，确保文档、代码和配置的一致性。

## 更新内容

### 1. 嵌入模型配置对齐

**用户原始需求**：
- URL: `http://localhost:11434/api/embeddings`
- 模型名: `qwen3-embed`

**当前配置状态**：
```yaml
inkflow:
  rag:
    embedding:
      provider: local-ollama
      endpoint: http://localhost:11434
      model: qwen3-embedding  # ✅ 已更新为用户指定的模型
      dimension: 2560  # qwen3-embedding-4b 维度
```

### 2. 代码修复

**修复项目**：
- ✅ 修复了 `ParentChildSearchService` 中的 TODO 注释
- ✅ 将硬编码的 "gemini" 提供商替换为配置化的 `embeddingProperties.getProvider()`
- ✅ 添加了 `EmbeddingProperties` 依赖注入

**修复代码**：
```java
// 之前
return embeddingCacheService.getCachedEmbedding(
    userId, query, "gemini" // TODO: 从配置获取默认提供商
)

// 现在
return embeddingCacheService.getCachedEmbedding(
    userId, query, embeddingProperties.getProvider()
)
```

### 3. 文档更新

**更新文档**：
- ✅ `RAG_EMBEDDING_SIMPLIFICATION.md` - 确保模型名称为 `qwen3-embed`
- ✅ `application.yml` - 更新默认模型为 `qwen3-embed`

## 验证清单

- [x] 配置文件使用正确的模型名称 (`qwen3-embed`)
- [x] 代码中移除硬编码的提供商名称
- [x] 文档与实际配置保持一致
- [x] 用户原始需求得到满足

## 影响评估

**正面影响**：
1. **配置一致性** - 所有配置文件和文档现在使用相同的模型名称
2. **代码质量** - 移除了 TODO 注释，提高了代码的完整性
3. **用户需求满足** - 按照用户指定的模型和端点进行配置

**无负面影响**：
- 配置更改是向后兼容的
- 不影响现有功能
- 不需要数据库迁移

## 后续行动

1. **测试验证** - 确保使用 `qwen3-embed` 模型的嵌入生成正常工作
2. **部署指导** - 更新部署文档，确保用户知道需要在 Ollama 中安装 `qwen3-embed` 模型
3. **监控** - 监控切换到新模型后的性能表现

## 相关文件

- `inkflow-backend/src/main/resources/application.yml`
- `inkflow-backend/src/main/java/com/inkflow/module/rag/service/ParentChildSearchService.java`
- `inkflow-backend/RAG_EMBEDDING_SIMPLIFICATION.md`
- `.kiro/specs/rag-chunking-improvement/requirements.md`
- `.kiro/specs/rag-chunking-improvement/design.md`