# RAG 系统性能调优指南

## 概述

本文档提供 InkFlow RAG 系统的性能调优指南，涵盖本地模型部署、缓存优化、并发处理、数据库优化等方面。

## 本地模型性能优化

### 1. 硬件配置优化

#### CPU 优化
```yaml
# 推荐配置
CPU: 16核心以上
内存: 32GB+ RAM
存储: NVMe SSD

# Docker 资源限制
services:
  qwen-embedding:
    deploy:
      resources:
        limits:
          cpus: '8.0'
          memory: 16G
        reservations:
          cpus: '4.0'
          memory: 8G
```

#### GPU 加速配置
```yaml
# Docker Compose GPU 配置
services:
  qwen-embedding:
    runtime: nvidia
    environment:
      - NVIDIA_VISIBLE_DEVICES=0
      - CUDA_VISIBLE_DEVICES=0
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: 1
              capabilities: [gpu]
```

### 2. 模型服务优化

#### qwen-embedding-4b 优化
```python
# 启动参数优化
python -m vllm.entrypoints.openai.api_server \
    --model Qwen/Qwen2-Embedding-4B \
    --port 8001 \
    --host 0.0.0.0 \
    --max-model-len 2048 \
    --max-num-batched-tokens 8192 \
    --max-num-seqs 256 \
    --tensor-parallel-size 1 \
    --gpu-memory-utilization 0.8 \
    --enable-prefix-caching \
    --disable-log-stats
```

#### bge-reranker-v2-m3 优化
```python
# reranker_server.py 优化配置
from FlagEmbedding import FlagReranker
import torch

# 模型加载优化
reranker = FlagReranker(
    'BAAI/bge-reranker-v2-m3',
    use_fp16=True,  # 使用半精度浮点数
    device='cuda' if torch.cuda.is_available() else 'cpu',
    batch_size=32,  # 批量处理大小
    max_length=512  # 最大序列长度
)

# 批量处理优化
@app.post("/v1/rerank/batch")
async def rerank_batch(requests: List[RerankRequest]):
    # 合并所有请求进行批量处理
    all_pairs = []
    request_boundaries = []
    
    for request in requests:
        start_idx = len(all_pairs)
        pairs = [[request.query, doc] for doc in request.documents]
        all_pairs.extend(pairs)
        request_boundaries.append((start_idx, len(all_pairs)))
    
    # 批量计算所有得分
    scores = reranker.compute_score(all_pairs)
    
    # 分割结果
    results = []
    for i, (start, end) in enumerate(request_boundaries):
        request_scores = scores[start:end]
        # 构建结果...
    
    return {"batch_results": results}
```

### 3. 连接池优化

#### WebClient 配置优化
```java
@Configuration
public class WebClientConfig {
    
    @Bean
    public WebClient.Builder webClientBuilder() {
        // 连接池配置
        ConnectionProvider connectionProvider = ConnectionProvider.builder("custom")
            .maxConnections(100)  // 最大连接数
            .maxIdleTime(Duration.ofSeconds(30))  // 空闲超时
            .maxLifeTime(Duration.ofMinutes(5))   // 连接生命周期
            .pendingAcquireTimeout(Duration.ofSeconds(10))  // 获取连接超时
            .evictInBackground(Duration.ofSeconds(120))     // 后台清理间隔
            .build();
        
        // HTTP 客户端配置
        HttpClient httpClient = HttpClient.create(connectionProvider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .option(ChannelOption.TCP_NODELAY, true)
            .responseTimeout(Duration.ofSeconds(30))
            .doOnConnected(conn -> 
                conn.addHandlerLast(new ReadTimeoutHandler(30))
                    .addHandlerLast(new WriteTimeoutHandler(30)));
        
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024));
    }
}
```

## 缓存系统优化

### 1. 多级缓存架构

#### L1 缓存 - 本地内存缓存
```java
@Configuration
public class CacheConfig {
    
    @Bean
    public Cache<String, float[]> embeddingL1Cache() {
        return Caffeine.newBuilder()
            .maximumSize(10_000)  // 最大条目数
            .expireAfterWrite(Duration.ofHours(1))  // 写入后过期
            .expireAfterAccess(Duration.ofMinutes(30))  // 访问后过期
            .recordStats()  // 启用统计
            .build();
    }
    
    @Bean
    public Cache<String, List<RerankResult>> rerankL1Cache() {
        return Caffeine.newBuilder()
            .maximumSize(5_000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .recordStats()
            .build();
    }
}
```

#### L2 缓存 - Redis 分布式缓存
```yaml
# application.yml
spring:
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
    database: 0
    timeout: 2000ms
    lettuce:
      pool:
        max-active: 20
        max-idle: 10
        min-idle: 5
        max-wait: 2000ms

inkflow:
  rag:
    cache:
      redis:
        enabled: true
        key-prefix: "inkflow:rag:"
        embedding-ttl: 3600  # 1小时
        rerank-ttl: 1800     # 30分钟
```

```java
@Service
public class L2CacheService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    public Mono<float[]> getEmbedding(String text) {
        String key = "embedding:" + DigestUtils.md5Hex(text);
        return Mono.fromCallable(() -> 
            (float[]) redisTemplate.opsForValue().get(key)
        ).subscribeOn(Schedulers.boundedElastic());
    }
    
    public Mono<Void> putEmbedding(String text, float[] embedding) {
        String key = "embedding:" + DigestUtils.md5Hex(text);
        return Mono.fromRunnable(() -> 
            redisTemplate.opsForValue().set(key, embedding, Duration.ofHours(1))
        ).subscribeOn(Schedulers.boundedElastic()).then();
    }
}
```

### 2. 缓存预热策略

#### 启动时预热
```java
@Component
public class CacheWarmupService {
    
    @EventListener(ApplicationReadyEvent.class)
    public void warmupCache() {
        log.info("Starting cache warmup...");
        
        // 预热常用文本的向量
        List<String> commonTexts = loadCommonTexts();
        embeddingService.generateEmbeddingsBatch(commonTexts)
            .doOnNext(embeddings -> log.info("Warmed up {} embeddings", embeddings.size()))
            .subscribe();
        
        // 预热常用查询的重排序结果
        warmupCommonQueries();
    }
    
    private void warmupCommonQueries() {
        Map<String, List<String>> commonQueries = loadCommonQueries();
        
        commonQueries.forEach((query, documents) -> 
            rerankerService.rerank(query, documents)
                .doOnNext(results -> log.info("Warmed up rerank for query: {}", query))
                .subscribe()
        );
    }
}
```

#### 智能预热
```java
@Service
public class IntelligentCacheWarmup {
    
    @Scheduled(fixedRate = 300000) // 每5分钟执行
    public void intelligentWarmup() {
        // 分析最近的查询模式
        List<String> frequentQueries = analyzeQueryPatterns();
        
        // 预测可能的查询
        List<String> predictedQueries = predictFutureQueries(frequentQueries);
        
        // 预热预测的查询
        predictedQueries.forEach(query -> 
            preloadQueryResults(query).subscribe()
        );
    }
    
    private Mono<Void> preloadQueryResults(String query) {
        return searchService.searchWithParentReturn(query, 10, "system")
            .doOnNext(results -> log.debug("Preloaded results for query: {}", query))
            .then();
    }
}
```

### 3. 缓存淘汰策略

#### LRU + 时间衰减
```java
public class TimeDecayLRUCache<K, V> {
    
    private final Map<K, CacheEntry<V>> cache = new ConcurrentHashMap<>();
    private final int maxSize;
    private final Duration maxAge;
    
    public Optional<V> get(K key) {
        CacheEntry<V> entry = cache.get(key);
        if (entry == null || entry.isExpired()) {
            cache.remove(key);
            return Optional.empty();
        }
        
        // 更新访问时间和权重
        entry.updateAccess();
        return Optional.of(entry.getValue());
    }
    
    public void put(K key, V value) {
        // 检查是否需要淘汰
        if (cache.size() >= maxSize) {
            evictLeastValuable();
        }
        
        cache.put(key, new CacheEntry<>(value));
    }
    
    private void evictLeastValuable() {
        // 基于访问频率和时间衰减计算价值
        cache.entrySet().stream()
            .min(Comparator.comparing(entry -> entry.getValue().calculateValue()))
            .ifPresent(entry -> cache.remove(entry.getKey()));
    }
    
    private static class CacheEntry<V> {
        private final V value;
        private final long createTime;
        private volatile long lastAccessTime;
        private volatile int accessCount;
        
        // 计算缓存条目的价值（访问频率 / 时间衰减）
        public double calculateValue() {
            long age = System.currentTimeMillis() - createTime;
            double timeDecay = Math.exp(-age / 3600000.0); // 1小时衰减因子
            return accessCount * timeDecay;
        }
    }
}
```

## 并发处理优化

### 1. 响应式编程优化

#### 背压处理
```java
@Service
public class BackpressureOptimizedService {
    
    private final Scheduler embeddingScheduler = Schedulers.newBoundedElastic(
        50,  // 最大线程数
        1000, // 队列大小
        "embedding-scheduler"
    );
    
    public Flux<float[]> generateEmbeddingsWithBackpressure(Flux<String> texts) {
        return texts
            .buffer(32)  // 批量处理
            .flatMap(batch -> 
                embeddingService.generateEmbeddingsBatch(batch)
                    .subscribeOn(embeddingScheduler)
                    .onBackpressureBuffer(100)  // 背压缓冲
                    .timeout(Duration.ofSeconds(30))
                    .retry(2)
            , 4)  // 并发度限制
            .flatMapIterable(Function.identity());
    }
}
```

#### 流量控制
```java
@Component
public class RateLimitedEmbeddingService {
    
    private final RateLimiter rateLimiter = RateLimiter.create(100.0); // 每秒100个请求
    
    public Mono<float[]> generateEmbeddingWithRateLimit(String text) {
        return Mono.fromCallable(() -> rateLimiter.acquire())
            .then(embeddingService.generateEmbedding(text))
            .timeout(Duration.ofSeconds(10));
    }
}
```

### 2. 线程池优化

#### 自定义线程池配置
```java
@Configuration
public class ThreadPoolConfig {
    
    @Bean("embeddingTaskExecutor")
    public TaskExecutor embeddingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("embedding-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
    
    @Bean("rerankTaskExecutor")
    public TaskExecutor rerankTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("rerank-");
        executor.initialize();
        return executor;
    }
}
```

#### 异步处理优化
```java
@Service
public class AsyncChunkingOptimizedService {
    
    @Async("embeddingTaskExecutor")
    public CompletableFuture<Void> processChunkingAsync(UUID storyBlockId) {
        return CompletableFuture.runAsync(() -> {
            try {
                processStoryBlockChunking(storyBlockId);
            } catch (Exception e) {
                log.error("Async chunking failed for block: {}", storyBlockId, e);
                // 记录失败，保持 isDirty 标记以便重试
            }
        });
    }
    
    @Async("embeddingTaskExecutor")
    public CompletableFuture<List<Void>> processBatchChunkingAsync(List<UUID> storyBlockIds) {
        List<CompletableFuture<Void>> futures = storyBlockIds.stream()
            .map(this::processChunkingAsync)
            .toList();
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .toList());
    }
}
```

## 数据库优化

### 1. 索引优化

#### 向量检索索引
```sql
-- 为向量检索创建复合索引
CREATE INDEX CONCURRENTLY idx_knowledge_base_vector_search 
ON knowledge_base (chunk_level, project_id) 
WHERE chunk_level = 'child';

-- 为父子关系查询创建索引
CREATE INDEX CONCURRENTLY idx_knowledge_base_story_block 
ON knowledge_base (story_block_id, chunk_order) 
WHERE story_block_id IS NOT NULL;

-- 为脏块查询创建部分索引
CREATE INDEX CONCURRENTLY idx_story_blocks_dirty_processing 
ON story_blocks (project_id, chapter_id, order_index) 
WHERE is_dirty = true;
```

#### 查询优化
```sql
-- 优化的向量检索查询
EXPLAIN (ANALYZE, BUFFERS) 
SELECT kb.id, kb.content, kb.story_block_id, kb.chunk_order,
       (kb.embedding <=> $1::vector) as distance
FROM knowledge_base kb
WHERE kb.chunk_level = 'child' 
  AND kb.project_id = $2
  AND (kb.embedding <=> $1::vector) < 0.8
ORDER BY kb.embedding <=> $1::vector
LIMIT 20;
```

### 2. 连接池优化

#### HikariCP 配置
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
      max-lifetime: 1200000
      connection-timeout: 20000
      validation-timeout: 3000
      leak-detection-threshold: 60000
      connection-test-query: SELECT 1
      pool-name: InkFlowHikariCP
```

#### 读写分离
```java
@Configuration
public class DatabaseConfig {
    
    @Bean
    @Primary
    public DataSource primaryDataSource() {
        return DataSourceBuilder.create()
            .url("jdbc:postgresql://master-db:5432/inkflow")
            .build();
    }
    
    @Bean
    public DataSource readOnlyDataSource() {
        return DataSourceBuilder.create()
            .url("jdbc:postgresql://replica-db:5432/inkflow")
            .build();
    }
    
    @Bean
    public DataSource routingDataSource() {
        RoutingDataSource routingDataSource = new RoutingDataSource();
        Map<Object, Object> dataSourceMap = new HashMap<>();
        dataSourceMap.put("write", primaryDataSource());
        dataSourceMap.put("read", readOnlyDataSource());
        routingDataSource.setTargetDataSources(dataSourceMap);
        routingDataSource.setDefaultTargetDataSource(primaryDataSource());
        return routingDataSource;
    }
}
```

### 3. 批量操作优化

#### 批量插入优化
```java
@Repository
public class OptimizedKnowledgeBaseRepository {
    
    @Modifying
    @Query(value = """
        INSERT INTO knowledge_base (id, content, embedding, chunk_level, story_block_id, chunk_order, project_id, created_at)
        VALUES (:#{#chunks.![id]}, :#{#chunks.![content]}, :#{#chunks.![embedding]}, 
                :#{#chunks.![chunkLevel]}, :#{#chunks.![storyBlockId]}, :#{#chunks.![chunkOrder]}, 
                :#{#chunks.![projectId]}, :#{#chunks.![createdAt]})
        ON CONFLICT (id) DO UPDATE SET
            content = EXCLUDED.content,
            embedding = EXCLUDED.embedding,
            updated_at = CURRENT_TIMESTAMP
        """, nativeQuery = true)
    void batchUpsertChunks(@Param("chunks") List<KnowledgeBase> chunks);
    
    @Modifying
    @Query("DELETE FROM KnowledgeBase kb WHERE kb.storyBlockId IN :storyBlockIds")
    void batchDeleteByStoryBlockIds(@Param("storyBlockIds") List<UUID> storyBlockIds);
}
```

## 监控和性能分析

### 1. 性能指标收集

#### Micrometer 指标
```java
@Component
public class RAGPerformanceMetrics {
    
    private final MeterRegistry meterRegistry;
    private final Timer embeddingTimer;
    private final Timer rerankTimer;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    
    public RAGPerformanceMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.embeddingTimer = Timer.builder("rag.embedding.duration")
            .description("Time taken for embedding generation")
            .register(meterRegistry);
        this.rerankTimer = Timer.builder("rag.rerank.duration")
            .description("Time taken for reranking")
            .register(meterRegistry);
        this.cacheHitCounter = Counter.builder("rag.cache.hits")
            .description("Cache hit count")
            .register(meterRegistry);
        this.cacheMissCounter = Counter.builder("rag.cache.misses")
            .description("Cache miss count")
            .register(meterRegistry);
    }
    
    public void recordEmbeddingTime(Duration duration) {
        embeddingTimer.record(duration);
    }
    
    public void recordCacheHit() {
        cacheHitCounter.increment();
    }
}
```

#### JVM 性能监控
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,threaddump,heapdump
  endpoint:
    metrics:
      enabled: true
    prometheus:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
    distribution:
      percentiles-histogram:
        http.server.requests: true
        rag.embedding.duration: true
        rag.rerank.duration: true
```

### 2. 性能分析工具

#### 自定义性能分析器
```java
@Component
public class RAGPerformanceProfiler {
    
    private final Map<String, PerformanceStats> stats = new ConcurrentHashMap<>();
    
    public void startProfiling(String operation) {
        stats.put(operation, new PerformanceStats(System.nanoTime()));
    }
    
    public void endProfiling(String operation) {
        PerformanceStats stat = stats.get(operation);
        if (stat != null) {
            stat.recordEnd(System.nanoTime());
            logPerformance(operation, stat);
        }
    }
    
    @Scheduled(fixedRate = 60000) // 每分钟输出统计
    public void reportPerformanceStats() {
        stats.forEach((operation, stat) -> {
            log.info("Performance Stats - {}: avg={}ms, max={}ms, count={}", 
                operation, stat.getAverageMs(), stat.getMaxMs(), stat.getCount());
        });
    }
    
    private static class PerformanceStats {
        private final long startTime;
        private volatile long totalTime = 0;
        private volatile long maxTime = 0;
        private volatile int count = 0;
        
        // 统计方法实现...
    }
}
```

### 3. 性能告警

#### 性能阈值监控
```java
@Component
public class PerformanceAlertService {
    
    @EventListener
    public void handleSlowEmbedding(EmbeddingCompletedEvent event) {
        if (event.getDuration().toMillis() > 5000) { // 超过5秒
            sendAlert("Slow embedding generation", 
                "Embedding took " + event.getDuration().toMillis() + "ms");
        }
    }
    
    @EventListener
    public void handleHighCacheMissRate(CacheStatsEvent event) {
        double missRate = event.getMissCount() / (double) event.getTotalRequests();
        if (missRate > 0.5) { // 缓存命中率低于50%
            sendAlert("High cache miss rate", 
                "Cache miss rate: " + String.format("%.2f%%", missRate * 100));
        }
    }
    
    private void sendAlert(String title, String message) {
        // 发送告警到监控系统
        log.warn("PERFORMANCE ALERT - {}: {}", title, message);
    }
}
```

## 配置调优建议

### 1. 生产环境配置

#### 应用配置
```yaml
# application-prod.yml
server:
  tomcat:
    threads:
      max: 200
      min-spare: 10
    connection-timeout: 20000
    max-connections: 8192

spring:
  webflux:
    multipart:
      max-in-memory-size: 10MB
      max-disk-usage-per-part: 100MB

inkflow:
  rag:
    embedding:
      batch-size: 64
      timeout-ms: 10000
      max-retries: 5
      enable-cache: true
      cache-max-size: 50000
      
    reranker:
      timeout-ms: 5000
      max-retries: 3
      top-k-multiplier: 3
      
    chunking:
      debounce-delay-ms: 5000
      max-parent-size: 2000
      target-child-size: 300
      
    search:
      use-two-stage: true
      recall-multiplier: 3
```

#### JVM 调优
```bash
# 启动参数
JAVA_OPTS="-Xms4g -Xmx8g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UseStringDeduplication \
  -XX:+OptimizeStringConcat \
  -XX:+UseCompressedOops \
  -XX:+UseCompressedClassPointers \
  -Djava.awt.headless=true \
  -Dspring.profiles.active=prod"
```

### 2. 容器化部署优化

#### Docker 配置
```dockerfile
# 多阶段构建优化
FROM openjdk:17-jdk-slim as builder
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests

FROM openjdk:17-jre-slim
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

# 性能优化参数
ENV JAVA_OPTS="-Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

#### Kubernetes 资源配置
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: inkflow-backend
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: inkflow-backend
        image: inkflow/backend:latest
        resources:
          requests:
            memory: "2Gi"
            cpu: "1000m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
        env:
        - name: JAVA_OPTS
          value: "-Xms2g -Xmx3g -XX:+UseG1GC"
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
```

## 性能测试

### 1. 负载测试

#### JMeter 测试计划
```xml
<!-- embedding-load-test.jmx -->
<TestPlan>
  <ThreadGroup>
    <stringProp name="ThreadGroup.num_threads">50</stringProp>
    <stringProp name="ThreadGroup.ramp_time">30</stringProp>
    <stringProp name="ThreadGroup.duration">300</stringProp>
    
    <HTTPSamplerProxy>
      <stringProp name="HTTPSampler.domain">localhost</stringProp>
      <stringProp name="HTTPSampler.port">8080</stringProp>
      <stringProp name="HTTPSampler.path">/api/rag/search</stringProp>
      <stringProp name="HTTPSampler.method">POST</stringProp>
      <stringProp name="HTTPSampler.postBodyRaw">
        {
          "query": "修仙小说的主角设定",
          "topK": 10
        }
      </stringProp>
    </HTTPSamplerProxy>
  </ThreadGroup>
</TestPlan>
```

#### 性能基准测试
```java
@SpringBootTest
@TestPropertySource(properties = {
    "inkflow.rag.embedding.endpoint=http://localhost:8001/v1/embeddings",
    "inkflow.rag.reranker.endpoint=http://localhost:8002/v1/rerank"
})
class PerformanceBenchmarkTest {
    
    @Test
    void benchmarkEmbeddingGeneration() {
        List<String> testTexts = generateTestTexts(1000);
        
        long startTime = System.currentTimeMillis();
        
        List<CompletableFuture<float[]>> futures = testTexts.stream()
            .map(text -> embeddingService.generateEmbedding(text).toFuture())
            .toList();
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        long duration = System.currentTimeMillis() - startTime;
        double throughput = testTexts.size() / (duration / 1000.0);
        
        log.info("Embedding throughput: {} texts/second", throughput);
        assertThat(throughput).isGreaterThan(10.0); // 至少10个/秒
    }
    
    @Test
    void benchmarkSearchPerformance() {
        String query = "修仙小说的主角设定";
        int iterations = 100;
        
        List<Long> durations = new ArrayList<>();
        
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            searchService.searchWithParentReturn(query, 10, "test-user").block();
            long duration = System.nanoTime() - start;
            durations.add(duration / 1_000_000); // 转换为毫秒
        }
        
        double avgDuration = durations.stream().mapToLong(Long::longValue).average().orElse(0);
        double p95Duration = durations.stream().sorted().skip((long)(iterations * 0.95)).findFirst().orElse(0L);
        
        log.info("Search performance - Avg: {}ms, P95: {}ms", avgDuration, p95Duration);
        assertThat(avgDuration).isLessThan(500); // 平均响应时间小于500ms
        assertThat(p95Duration).isLessThan(1000); // P95响应时间小于1秒
    }
}
```

### 2. 压力测试

#### Gatling 压力测试
```scala
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class RAGStressTest extends Simulation {
  
  val httpProtocol = http
    .baseUrl("http://localhost:8080")
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
  
  val searchScenario = scenario("RAG Search Stress Test")
    .exec(
      http("search_request")
        .post("/api/rag/search")
        .body(StringBody("""{"query": "修仙小说", "topK": 10}"""))
        .check(status.is(200))
        .check(responseTimeInMillis.lt(2000))
    )
  
  setUp(
    searchScenario.inject(
      rampUsers(100) during (30 seconds),
      constantUsers(200) during (5 minutes),
      rampUsers(500) during (60 seconds)
    )
  ).protocols(httpProtocol)
   .assertions(
     global.responseTime.percentile3.lt(1000),
     global.successfulRequests.percent.gt(95)
   )
}
```

## 总结

通过以上性能调优措施，InkFlow RAG 系统可以实现：

1. **高吞吐量**: 支持每秒处理数百个向量生成和检索请求
2. **低延迟**: 平均响应时间控制在500ms以内
3. **高可用性**: 通过缓存、降级和故障处理确保系统稳定性
4. **可扩展性**: 支持水平扩展和负载均衡
5. **资源效率**: 通过优化配置和缓存策略降低资源消耗

定期进行性能测试和监控，根据实际负载情况调整配置参数，确保系统始终保持最佳性能状态。