# 本地模型集成指南

## 概述

本文档详细说明如何集成本地部署的 **qwen-embedding-4b** 和 **bge-reranker-v2-m3** 模型到 InkFlow RAG 系统。

## 模型部署

### 1. qwen-embedding-4b 部署

**模型信息**
- 模型名称：qwen-embedding-4b
- 参数量：4B
- 向量维度：1024（根据实际配置）
- 用途：文本向量化

**部署方式（推荐使用 vLLM 或 Xinference）**

#### 使用 Xinference 部署

```bash
# 安装 Xinference
pip install xinference

# 启动 Xinference 服务
xinference-local --host 0.0.0.0 --port 8001

# 通过 Web UI 或 CLI 加载模型
xinference launch --model-name qwen-embedding-4b --model-type embedding
```

#### 使用 vLLM 部署

```bash
# 安装 vLLM
pip install vllm

# 启动 embedding 服务
python -m vllm.entrypoints.openai.api_server \
    --model Qwen/Qwen2-Embedding-4B \
    --port 8001 \
    --host 0.0.0.0
```

**API 接口格式**

```bash
# 测试 API
curl -X POST http://localhost:8001/v1/embeddings \
  -H "Content-Type: application/json" \
  -d '{
    "model": "qwen-embedding-4b",
    "input": ["这是一个测试文本", "另一个测试文本"]
  }'
```

**响应格式**

```json
{
  "object": "list",
  "data": [
    {
      "object": "embedding",
      "embedding": [0.123, -0.456, ...],
      "index": 0
    },
    {
      "object": "embedding",
      "embedding": [0.789, -0.012, ...],
      "index": 1
    }
  ],
  "model": "qwen-embedding-4b",
  "usage": {
    "prompt_tokens": 10,
    "total_tokens": 10
  }
}
```

### 2. bge-reranker-v2-m3 部署

**模型信息**
- 模型名称：bge-reranker-v2-m3
- 用途：重排序、相似度计算、意图识别
- 输入：查询文本 + 候选文本列表
- 输出：相关性得分（0-1）

**部署方式**

#### 使用 FastAPI 自定义服务

创建 `reranker_server.py`：

```python
from fastapi import FastAPI
from pydantic import BaseModel
from typing import List
from FlagEmbedding import FlagReranker
import uvicorn

app = FastAPI()

# 加载模型
reranker = FlagReranker('BAAI/bge-reranker-v2-m3', use_fp16=True)

class RerankRequest(BaseModel):
    query: str
    documents: List[str]
    top_k: int = None

class RerankResult(BaseModel):
    index: int
    score: float
    document: str

@app.post("/v1/rerank")
async def rerank(request: RerankRequest):
    # 计算相关性得分
    scores = reranker.compute_score(
        [[request.query, doc] for doc in request.documents]
    )
    
    # 如果 scores 是单个值，转换为列表
    if not isinstance(scores, list):
        scores = [scores]
    
    # 构建结果
    results = [
        {"index": i, "score": float(score), "document": doc}
        for i, (score, doc) in enumerate(zip(scores, request.documents))
    ]
    
    # 按得分排序
    results.sort(key=lambda x: x["score"], reverse=True)
    
    # 如果指定了 top_k，只返回前 k 个
    if request.top_k:
        results = results[:request.top_k]
    
    return {
        "object": "list",
        "data": results,
        "model": "bge-reranker-v2-m3"
    }

@app.post("/v1/similarity")
async def similarity(text1: str, text2: str):
    """计算两个文本的相似度"""
    score = reranker.compute_score([[text1, text2]])
    return {"similarity": float(score)}

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8002)
```

启动服务：

```bash
# 安装依赖
pip install fastapi uvicorn FlagEmbedding

# 启动服务
python reranker_server.py
```

**API 接口格式**

```bash
# 测试重排序 API
curl -X POST http://localhost:8002/v1/rerank \
  -H "Content-Type: application/json" \
  -d '{
    "query": "修仙小说的主角设定",
    "documents": [
      "主角是一个热血少年，立志成为最强修仙者",
      "这是一个关于科幻的故事",
      "主角性格冷静，擅长谋略"
    ],
    "top_k": 2
  }'

# 测试相似度 API
curl -X POST http://localhost:8002/v1/similarity \
  -H "Content-Type: application/json" \
  -d '{
    "text1": "我想创作一个修仙小说",
    "text2": "帮我写一个仙侠故事"
  }'
```

**响应格式**

```json
{
  "object": "list",
  "data": [
    {
      "index": 0,
      "score": 0.95,
      "document": "主角是一个热血少年，立志成为最强修仙者"
    },
    {
      "index": 2,
      "score": 0.72,
      "document": "主角性格冷静，擅长谋略"
    }
  ],
  "model": "bge-reranker-v2-m3"
}
```

## Java 客户端实现

### 1. LocalEmbeddingService 实现

```java
package com.inkflow.module.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocalEmbeddingService {
    
    private final WebClient.Builder webClientBuilder;
    private final EmbeddingProperties embeddingProperties;
    
    /**
     * 生成单个文本的向量
     */
    public Mono<float[]> generateEmbedding(String text) {
        return generateEmbeddingsBatch(List.of(text))
            .map(embeddings -> embeddings.get(0));
    }
    
    /**
     * 批量生成向量
     */
    public Mono<List<float[]>> generateEmbeddingsBatch(List<String> texts) {
        WebClient client = webClientBuilder
            .baseUrl(embeddingProperties.getEndpoint())
            .build();
        
        Map<String, Object> requestBody = Map.of(
            "model", embeddingProperties.getModel(),
            "input", texts
        );
        
        return client.post()
            .uri("/v1/embeddings")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(EmbeddingResponse.class)
            .timeout(Duration.ofMillis(embeddingProperties.getTimeoutMs()))
            .retryWhen(Retry.backoff(
                embeddingProperties.getMaxRetries(),
                Duration.ofMillis(100)
            ))
            .map(response -> response.getData().stream()
                .map(EmbeddingData::getEmbedding)
                .toList())
            .doOnError(e -> log.error("Embedding generation failed: {}", e.getMessage()));
    }
    
    @lombok.Data
    private static class EmbeddingResponse {
        private List<EmbeddingData> data;
        private String model;
        private Usage usage;
    }
    
    @lombok.Data
    private static class EmbeddingData {
        private float[] embedding;
        private int index;
    }
    
    @lombok.Data
    private static class Usage {
        private int promptTokens;
        private int totalTokens;
    }
}
```

### 2. LocalRerankerService 实现

```java
package com.inkflow.module.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocalRerankerService {
    
    private final WebClient.Builder webClientBuilder;
    private final RerankerProperties rerankerProperties;
    
    /**
     * 重排序检索结果
     */
    public Mono<List<RerankResult>> rerank(String query, List<String> candidates) {
        return rerank(query, candidates, null);
    }
    
    /**
     * 重排序检索结果（指定 topK）
     */
    public Mono<List<RerankResult>> rerank(String query, List<String> candidates, Integer topK) {
        if (!rerankerProperties.isEnabled()) {
            // 如果未启用 reranker，返回原始顺序
            return Mono.just(candidates.stream()
                .map((doc, idx) -> new RerankResult(idx, 1.0, doc))
                .toList());
        }
        
        WebClient client = webClientBuilder
            .baseUrl(rerankerProperties.getEndpoint())
            .build();
        
        Map<String, Object> requestBody = Map.of(
            "query", query,
            "documents", candidates,
            "top_k", topK != null ? topK : candidates.size()
        );
        
        return client.post()
            .uri("/v1/rerank")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(RerankResponse.class)
            .timeout(Duration.ofMillis(rerankerProperties.getTimeoutMs()))
            .retryWhen(Retry.backoff(
                rerankerProperties.getMaxRetries(),
                Duration.ofMillis(100)
            ))
            .map(response -> response.getData().stream()
                .map(data -> new RerankResult(
                    data.getIndex(),
                    data.getScore(),
                    data.getDocument()
                ))
                .toList())
            .doOnError(e -> log.error("Reranking failed: {}", e.getMessage()));
    }
    
    /**
     * 计算两个文本的相似度
     */
    public Mono<Double> calculateSimilarity(String text1, String text2) {
        WebClient client = webClientBuilder
            .baseUrl(rerankerProperties.getEndpoint())
            .build();
        
        Map<String, String> requestBody = Map.of(
            "text1", text1,
            "text2", text2
        );
        
        return client.post()
            .uri("/v1/similarity")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(SimilarityResponse.class)
            .map(SimilarityResponse::getSimilarity)
            .timeout(Duration.ofMillis(rerankerProperties.getTimeoutMs()));
    }
    
    /**
     * 批量计算相邻句子的相似度
     */
    public Mono<List<Double>> calculateAdjacentSimilarities(List<String> sentences) {
        if (sentences.size() < 2) {
            return Mono.just(List.of());
        }
        
        // 构建相邻句子对
        List<Mono<Double>> similarityMonos = new ArrayList<>();
        for (int i = 0; i < sentences.size() - 1; i++) {
            similarityMonos.add(calculateSimilarity(sentences.get(i), sentences.get(i + 1)));
        }
        
        // 并行计算所有相似度
        return Mono.zip(similarityMonos, results -> 
            Arrays.stream(results)
                .map(obj -> (Double) obj)
                .toList()
        );
    }
    
    @lombok.Data
    private static class RerankResponse {
        private List<RerankData> data;
        private String model;
    }
    
    @lombok.Data
    private static class RerankData {
        private int index;
        private double score;
        private String document;
    }
    
    @lombok.Data
    private static class SimilarityResponse {
        private double similarity;
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class RerankResult {
        private int index;
        private double score;
        private String document;
    }
}
```

### 3. 配置类

```java
package com.inkflow.module.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "inkflow.rag.embedding")
public class EmbeddingProperties {
    private String provider = "local-ollama";
    private String endpoint = "http://localhost:11434";
    private String model = "qwen3-embedding";
    private int dimension = 2560;
    private int batchSize = 32;
    private long timeoutMs = 5000;
    private int maxRetries = 3;
}

@Data
@Configuration
@ConfigurationProperties(prefix = "inkflow.rag.reranker")
public class RerankerProperties {
    private String provider = "local-bge";
    private String endpoint = "http://localhost:8002";
    private String model = "bge-reranker-v2-m3";
    private boolean enabled = true;
    private int topKMultiplier = 2;
    private long timeoutMs = 3000;
    private int maxRetries = 2;
    
    private IntentEnhancement intentEnhancement = new IntentEnhancement();
    
    @Data
    public static class IntentEnhancement {
        private boolean enabled = true;
        private double confidenceThreshold = 0.6;
    }
}
```

## 性能优化建议

### 1. 批量处理

```java
// 批量生成向量以减少网络开销
List<String> texts = List.of("文本1", "文本2", "文本3", ...);
List<float[]> embeddings = embeddingService.generateEmbeddingsBatch(texts).block();
```

### 2. 缓存策略

```java
@Service
public class CachedEmbeddingService {
    
    private final LocalEmbeddingService embeddingService;
    private final Cache<String, float[]> embeddingCache;
    
    public Mono<float[]> generateEmbedding(String text) {
        // 先查缓存
        float[] cached = embeddingCache.getIfPresent(text);
        if (cached != null) {
            return Mono.just(cached);
        }
        
        // 缓存未命中，调用模型
        return embeddingService.generateEmbedding(text)
            .doOnNext(embedding -> embeddingCache.put(text, embedding));
    }
}
```

### 3. 异步并行处理

```java
// 并行处理多个切片任务
List<Mono<Void>> chunkingTasks = storyBlocks.stream()
    .map(block -> semanticChunkingService.splitIntoChildChunks(block.getContent())
        .flatMap(chunks -> saveChunks(block.getId(), chunks)))
    .toList();

Mono.when(chunkingTasks).block();
```

## 故障处理

### 1. 模型服务不可用

```java
@Service
public class ResilientEmbeddingService {
    
    public Mono<float[]> generateEmbedding(String text) {
        return localEmbeddingService.generateEmbedding(text)
            .onErrorResume(e -> {
                log.warn("Local embedding failed, falling back to cloud: {}", e.getMessage());
                return cloudEmbeddingService.generateEmbedding(text);
            });
    }
}
```

### 2. 超时处理

```java
// 设置合理的超时时间
return embeddingService.generateEmbedding(text)
    .timeout(Duration.ofSeconds(5))
    .onErrorResume(TimeoutException.class, e -> {
        log.error("Embedding generation timeout");
        return Mono.empty();
    });
```

### 3. 重试策略

```java
return embeddingService.generateEmbedding(text)
    .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
        .filter(throwable -> throwable instanceof IOException)
        .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> 
            new RuntimeException("Max retries exceeded")));
```

## 监控与日志

### 1. 性能监控

```java
@Aspect
@Component
public class EmbeddingPerformanceMonitor {
    
    @Around("execution(* LocalEmbeddingService.generateEmbedding*(..))")
    public Object monitorEmbedding(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - start;
            log.info("Embedding generation took {}ms", duration);
            return result;
        } catch (Exception e) {
            log.error("Embedding generation failed", e);
            throw e;
        }
    }
}
```

### 2. 健康检查

```java
@Component
public class ModelHealthIndicator implements HealthIndicator {
    
    private final LocalEmbeddingService embeddingService;
    private final LocalRerankerService rerankerService;
    
    @Override
    public Health health() {
        try {
            // 测试 embedding 服务
            embeddingService.generateEmbedding("test").block(Duration.ofSeconds(2));
            
            // 测试 reranker 服务
            rerankerService.calculateSimilarity("test1", "test2").block(Duration.ofSeconds(2));
            
            return Health.up()
                .withDetail("embedding", "available")
                .withDetail("reranker", "available")
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

## 测试

### 单元测试

```java
@SpringBootTest
class LocalEmbeddingServiceTest {
    
    @Autowired
    private LocalEmbeddingService embeddingService;
    
    @Test
    void testGenerateEmbedding() {
        String text = "这是一个测试文本";
        float[] embedding = embeddingService.generateEmbedding(text).block();
        
        assertNotNull(embedding);
        assertEquals(1024, embedding.length);
    }
    
    @Test
    void testBatchGeneration() {
        List<String> texts = List.of("文本1", "文本2", "文本3");
        List<float[]> embeddings = embeddingService.generateEmbeddingsBatch(texts).block();
        
        assertNotNull(embeddings);
        assertEquals(3, embeddings.size());
    }
}
```

## 总结

通过集成本地部署的 qwen-embedding-4b 和 bge-reranker-v2-m3 模型，InkFlow RAG 系统获得了：

1. **更快的响应速度**：本地推理无网络延迟
2. **更低的成本**：无需调用云端 API
3. **更好的隐私**：数据不离开本地
4. **更高的精度**：两阶段检索（召回+重排序）
5. **增强的意图识别**：利用 reranker 进行模板匹配

这些优化将显著提升 AI 引导式创作的用户体验。

## 实现细节

### 故障处理和降级系统

基于 Task 38 的实现，系统现在包含完整的故障处理和降级机制：

#### 1. ResilientEmbeddingService - 弹性向量化服务

```java
@Service
@RequiredArgsConstructor
public class ResilientEmbeddingService {
    
    private final LocalEmbeddingService localEmbeddingService;
    private final EmbeddingService cloudEmbeddingService;
    
    // 断路器配置
    private static final int FAILURE_THRESHOLD = 5;
    private static final long RECOVERY_TIMEOUT_MS = 30000;
    
    /**
     * 生成向量（带故障处理）
     */
    public Mono<float[]> generateEmbedding(String text) {
        // 检查断路器状态
        if (isCircuitBreakerOpen()) {
            return fallbackToCloudService(text);
        }
        
        return localEmbeddingService.generateEmbedding(text)
            .timeout(Duration.ofMillis(timeoutMs))
            .retryWhen(createRetrySpec())
            .onErrorResume(this::handleLocalServiceError, error -> 
                fallbackToCloudService(text));
    }
    
    /**
     * 降级到云端服务
     */
    private Mono<float[]> fallbackToCloudService(String text) {
        log.info("Falling back to cloud embedding service");
        return cloudEmbeddingService.generateEmbedding(text, "default-user")
            .timeout(Duration.ofMillis(timeoutMs * 2));
    }
}
```

#### 2. ResilientRerankerService - 弹性重排序服务

```java
@Service
@RequiredArgsConstructor
public class ResilientRerankerService {
    
    private final LocalRerankerService localRerankerService;
    
    // 断路器配置
    private static final int FAILURE_THRESHOLD = 3;
    private static final long RECOVERY_TIMEOUT_MS = 20000;
    
    /**
     * 重排序（带故障处理）
     */
    public Mono<List<RerankResult>> rerank(String query, List<String> documents, Integer topK) {
        if (isCircuitBreakerOpen()) {
            return fallbackToScoreBasedRanking(query, documents, topK);
        }
        
        return localRerankerService.rerank(query, documents, topK)
            .timeout(Duration.ofMillis(timeoutMs))
            .retryWhen(createRetrySpec())
            .onErrorResume(this::handleLocalServiceError, error -> 
                fallbackToScoreBasedRanking(query, documents, topK));
    }
    
    /**
     * 降级到基于得分的排序
     */
    private Mono<List<RerankResult>> fallbackToScoreBasedRanking(String query, List<String> documents, Integer topK) {
        return Mono.fromCallable(() -> {
            // 基于关键词匹配和文本长度的简单得分算法
            List<RerankResult> results = new ArrayList<>();
            for (int i = 0; i < documents.size(); i++) {
                double score = calculateFallbackScore(query, documents.get(i));
                results.add(new RerankResult(i, score, documents.get(i)));
            }
            
            results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
            int limit = topK != null ? Math.min(topK, results.size()) : results.size();
            return results.subList(0, limit);
        });
    }
}
```

### 缓存优化系统

基于 Task 37 的实现，系统包含双层缓存机制：

#### 1. CachedEmbeddingService - 缓存向量化服务

```java
@Service
@RequiredArgsConstructor
public class CachedEmbeddingService {
    
    private final ResilientEmbeddingService resilientEmbeddingService;
    private final Cache<String, float[]> embeddingCache;
    private final Cache<String, List<float[]>> batchCache;
    
    /**
     * 生成向量（带缓存）
     */
    public Mono<float[]> generateEmbedding(String text) {
        // 检查缓存
        float[] cached = embeddingCache.getIfPresent(text);
        if (cached != null) {
            cacheHitCounter.increment();
            return Mono.just(cached);
        }
        
        // 缓存未命中，调用弹性服务
        return resilientEmbeddingService.generateEmbedding(text)
            .doOnNext(embedding -> {
                embeddingCache.put(text, embedding);
                cacheMissCounter.increment();
            });
    }
    
    /**
     * 批量生成向量（带缓存）
     */
    public Mono<List<float[]>> generateEmbeddingsBatch(List<String> texts) {
        String cacheKey = String.join("|", texts);
        List<float[]> cached = batchCache.getIfPresent(cacheKey);
        if (cached != null) {
            return Mono.just(cached);
        }
        
        return resilientEmbeddingService.generateEmbeddingsBatch(texts)
            .doOnNext(embeddings -> batchCache.put(cacheKey, embeddings));
    }
}
```

#### 2. CachedRerankerService - 缓存重排序服务

```java
@Service
@RequiredArgsConstructor
public class CachedRerankerService {
    
    private final ResilientRerankerService resilientRerankerService;
    private final Cache<String, List<RerankResult>> rerankCache;
    private final Cache<String, Double> similarityCache;
    
    /**
     * 重排序（带缓存）
     */
    public Mono<List<RerankResult>> rerank(String query, List<String> documents, Integer topK) {
        String cacheKey = generateRerankCacheKey(query, documents, topK);
        List<RerankResult> cached = rerankCache.getIfPresent(cacheKey);
        if (cached != null) {
            return Mono.just(cached);
        }
        
        return resilientRerankerService.rerank(query, documents, topK)
            .doOnNext(results -> rerankCache.put(cacheKey, results));
    }
    
    /**
     * 相似度计算（带缓存）
     */
    public Mono<Double> calculateSimilarity(String text1, String text2) {
        String cacheKey = generateSimilarityCacheKey(text1, text2);
        Double cached = similarityCache.getIfPresent(cacheKey);
        if (cached != null) {
            return Mono.just(cached);
        }
        
        return resilientRerankerService.calculateSimilarity(text1, text2)
            .doOnNext(similarity -> similarityCache.put(cacheKey, similarity));
    }
}
```

### 监控和健康检查

#### 1. ModelHealthIndicator - 模型健康检查

```java
@Component
public class ModelHealthIndicator implements HealthIndicator {
    
    private final ResilientEmbeddingService embeddingService;
    private final ResilientRerankerService rerankerService;
    
    @Override
    public Health health() {
        try {
            // 测试 embedding 服务
            embeddingService.generateEmbedding("health-check")
                .timeout(Duration.ofSeconds(2))
                .block();
            
            // 测试 reranker 服务
            rerankerService.calculateSimilarity("test1", "test2")
                .timeout(Duration.ofSeconds(2))
                .block();
            
            return Health.up()
                .withDetail("embedding", "available")
                .withDetail("reranker", "available")
                .withDetail("embeddingCircuitBreaker", !embeddingService.isCircuitBreakerOpen())
                .withDetail("rerankerCircuitBreaker", !rerankerService.isCircuitBreakerOpen())
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

#### 2. EmbeddingPerformanceMonitor - 性能监控

```java
@Aspect
@Component
public class EmbeddingPerformanceMonitor {
    
    private final MeterRegistry meterRegistry;
    
    @Around("execution(* *EmbeddingService.generateEmbedding*(..))")
    public Object monitorEmbedding(ProceedingJoinPoint joinPoint) throws Throwable {
        Timer.Sample sample = Timer.start(meterRegistry);
        String serviceName = joinPoint.getTarget().getClass().getSimpleName();
        
        try {
            Object result = joinPoint.proceed();
            sample.stop(Timer.builder("embedding.generation.time")
                .tag("service", serviceName)
                .tag("status", "success")
                .register(meterRegistry));
            return result;
        } catch (Exception e) {
            sample.stop(Timer.builder("embedding.generation.time")
                .tag("service", serviceName)
                .tag("status", "error")
                .register(meterRegistry));
            
            Counter.builder("embedding.generation.errors")
                .tag("service", serviceName)
                .tag("error", e.getClass().getSimpleName())
                .register(meterRegistry)
                .increment();
            
            throw e;
        }
    }
}
```

### 管理和控制接口

#### 1. FaultToleranceController - 故障容错管理

```java
@RestController
@RequestMapping("/api/rag/fault-tolerance")
@RequiredArgsConstructor
public class FaultToleranceController {
    
    private final ResilientEmbeddingService resilientEmbeddingService;
    private final ResilientRerankerService resilientRerankerService;
    
    /**
     * 获取断路器状态
     */
    @GetMapping("/circuit-breaker/status")
    public ResponseEntity<Map<String, Object>> getCircuitBreakerStatus() {
        Map<String, Object> status = new HashMap<>();
        
        Map<String, Object> embeddingStatus = new HashMap<>();
        embeddingStatus.put("circuitBreakerOpen", resilientEmbeddingService.isCircuitBreakerOpen());
        embeddingStatus.put("consecutiveFailures", resilientEmbeddingService.getConsecutiveFailures());
        
        Map<String, Object> rerankerStatus = new HashMap<>();
        rerankerStatus.put("circuitBreakerOpen", resilientRerankerService.isCircuitBreakerOpen());
        rerankerStatus.put("consecutiveFailures", resilientRerankerService.getConsecutiveFailures());
        
        status.put("embedding", embeddingStatus);
        status.put("reranker", rerankerStatus);
        status.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * 重置断路器
     */
    @PostMapping("/circuit-breaker/reset-all")
    public ResponseEntity<Map<String, String>> resetAllCircuitBreakers() {
        resilientEmbeddingService.resetCircuitBreaker();
        resilientRerankerService.resetCircuitBreaker();
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "All circuit breakers reset successfully");
        
        return ResponseEntity.ok(response);
    }
}
```

#### 2. CacheMonitoringController - 缓存监控

```java
@RestController
@RequestMapping("/api/rag/cache")
@RequiredArgsConstructor
public class CacheMonitoringController {
    
    private final CachedEmbeddingService cachedEmbeddingService;
    private final CachedRerankerService cachedRerankerService;
    
    /**
     * 获取缓存统计
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getCacheStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // Embedding 缓存统计
        Map<String, Object> embeddingStats = cachedEmbeddingService.getCacheStatistics();
        stats.put("embedding", embeddingStats);
        
        // Reranker 缓存统计
        Map<String, Object> rerankerStats = cachedRerankerService.getCacheStatistics();
        stats.put("reranker", rerankerStats);
        
        stats.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * 清空缓存
     */
    @PostMapping("/clear")
    public ResponseEntity<Map<String, String>> clearAllCaches() {
        cachedEmbeddingService.clearCache();
        cachedRerankerService.clearCache();
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "All caches cleared successfully");
        
        return ResponseEntity.ok(response);
    }
}
```

### 配置管理

#### 完整的配置类

```java
@Data
@Configuration
@ConfigurationProperties(prefix = "inkflow.rag.embedding")
public class EmbeddingProperties {
    private String provider = "local-ollama";
    private String endpoint = "http://localhost:11434";
    private String model = "qwen3-embedding";
    private int dimension = 2560;
    private int batchSize = 32;
    private long timeoutMs = 5000;
    private int maxRetries = 3;
    private long retryDelayMs = 100;
    
    // 缓存配置
    private boolean enableCache = true;
    private long cacheExpirationSeconds = 3600;
    private long cacheMaxSize = 10000;
    
    // 故障处理配置
    private boolean enableFallback = true;
    private CircuitBreaker circuitBreaker = new CircuitBreaker();
    
    @Data
    public static class CircuitBreaker {
        private int failureThreshold = 5;
        private long recoveryTimeoutMs = 30000;
        private boolean enableCircuitBreaker = true;
    }
}

@Data
@Configuration
@ConfigurationProperties(prefix = "inkflow.rag.reranker")
public class RerankerProperties {
    private String provider = "local-bge";
    private String endpoint = "http://localhost:8002/v1/rerank";
    private String model = "bge-reranker-v2-m3";
    private boolean enabled = true;
    private int topKMultiplier = 2;
    private long timeoutMs = 3000;
    private int maxRetries = 2;
    private long retryDelayMs = 100;
    
    // 故障处理配置
    private boolean enableFallback = true;
    private CircuitBreaker circuitBreaker = new CircuitBreaker();
    
    // 意图识别增强配置
    private IntentEnhancement intentEnhancement = new IntentEnhancement();
    
    @Data
    public static class CircuitBreaker {
        private int failureThreshold = 3;
        private long recoveryTimeoutMs = 20000;
        private boolean enableCircuitBreaker = true;
    }
    
    @Data
    public static class IntentEnhancement {
        private boolean enabled = true;
        private double confidenceThreshold = 0.6;
        private double minMatchScore = 0.3;
        private boolean enableCache = true;
        private long cacheExpirationSeconds = 300;
    }
}
```

### 集成测试

#### 完整的集成测试示例

```java
@SpringBootTest
@TestPropertySource(properties = {
    "inkflow.rag.embedding.endpoint=http://localhost:8001/v1/embeddings",
    "inkflow.rag.reranker.endpoint=http://localhost:8002/v1/rerank"
})
class LocalModelIntegrationTest {
    
    @Autowired
    private CachedEmbeddingService embeddingService;
    
    @Autowired
    private CachedRerankerService rerankerService;
    
    @Test
    @ConditionalOnProperty(name = "test.local-models.enabled", havingValue = "true")
    void testEmbeddingGeneration() {
        String text = "这是一个测试文本";
        
        StepVerifier.create(embeddingService.generateEmbedding(text))
            .assertNext(embedding -> {
                assertNotNull(embedding);
                assertEquals(1024, embedding.length);
            })
            .verifyComplete();
    }
    
    @Test
    @ConditionalOnProperty(name = "test.local-models.enabled", havingValue = "true")
    void testReranking() {
        String query = "修仙小说";
        List<String> documents = Arrays.asList(
            "主角是一个修仙者",
            "这是科幻故事",
            "仙侠世界的设定"
        );
        
        StepVerifier.create(rerankerService.rerank(query, documents, 2))
            .assertNext(results -> {
                assertNotNull(results);
                assertEquals(2, results.size());
                assertTrue(results.get(0).getScore() >= results.get(1).getScore());
            })
            .verifyComplete();
    }
    
    @Test
    void testFaultTolerance() {
        // 模拟本地服务不可用
        // 验证降级机制是否正常工作
        // ...
    }
    
    @Test
    void testCaching() {
        String text = "缓存测试文本";
        
        // 第一次调用
        StepVerifier.create(embeddingService.generateEmbedding(text))
            .expectNextCount(1)
            .verifyComplete();
        
        // 第二次调用应该命中缓存
        StepVerifier.create(embeddingService.generateEmbedding(text))
            .expectNextCount(1)
            .verifyComplete();
        
        // 验证缓存命中率
        Map<String, Object> stats = embeddingService.getCacheStatistics();
        assertTrue((Double) stats.get("hitRate") > 0.0);
    }
}
```

## 部署最佳实践

### 1. 生产环境配置

```yaml
# application-prod.yml
inkflow:
  rag:
    embedding:
      endpoint: ${RAG_EMBEDDING_ENDPOINT:http://embedding-service:8001/v1/embeddings}
      timeout-ms: ${RAG_EMBEDDING_TIMEOUT:10000}
      max-retries: ${RAG_EMBEDDING_MAX_RETRIES:5}
      enable-cache: true
      cache-max-size: ${RAG_EMBEDDING_CACHE_SIZE:50000}
      
    reranker:
      endpoint: ${RAG_RERANKER_ENDPOINT:http://reranker-service:8002/v1/rerank}
      timeout-ms: ${RAG_RERANKER_TIMEOUT:5000}
      max-retries: ${RAG_RERANKER_MAX_RETRIES:3}
      enabled: ${RAG_RERANKER_ENABLED:true}
```

### 2. 监控配置

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
  health:
    custom:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
```

### 3. 日志配置

```yaml
logging:
  level:
    com.inkflow.module.rag: INFO
    com.inkflow.module.rag.service.LocalEmbeddingService: DEBUG
    com.inkflow.module.rag.service.LocalRerankerService: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level [%X{traceId}] %logger{36} - %msg%n"
```

## 性能调优指南

### 1. 批量处理优化

- 使用批量API减少网络开销
- 合理设置批量大小（推荐32-64）
- 并行处理多个批次

### 2. 缓存策略优化

- 根据内存容量调整缓存大小
- 设置合理的过期时间
- 监控缓存命中率并调优

### 3. 超时和重试优化

- 根据网络环境调整超时时间
- 设置合理的重试次数和间隔
- 区分可重试和不可重试错误

### 4. 断路器优化

- 根据服务稳定性调整失败阈值
- 设置合理的恢复时间
- 监控断路器状态并及时处理

通过这些实现细节和最佳实践，InkFlow RAG 系统能够稳定、高效地运行本地模型，同时具备完善的故障处理和性能优化能力。