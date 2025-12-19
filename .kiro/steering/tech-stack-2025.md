# InkFlow 2.0 技术栈指南 (2025)

本文档提供 InkFlow 2.0 项目使用的最新技术栈的关键 API 和代码示例。

## 技术栈版本

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 22 | Virtual Threads 正式发布 |
| Spring Boot | 3.5.x | 稳定生产版本 |
| Spring AI | 1.1.2 | 长期维护版本 |
| PostgreSQL | 16+ | pgvector 支持 |
| Redis Stack | 7.4+ | 向量缓存 |

> **重要说明**: Spring AI 没有 2.0 计划，1.x 是长期维护的主版本。Agent 实现应使用 `CompletableFuture` + Virtual Threads + Spring AI 1.1.2 的 `ChatClient` 来实现协同。

---

## 1. Java 22 关键特性

### 1.1 Virtual Threads (虚拟线程)

虚拟线程是 Java 21 引入的正式特性，在 Java 22 中已完全稳定。

```java
// 启用 Virtual Threads (application.yml)
spring:
  threads:
    virtual:
      enabled: true

// 手动创建虚拟线程
Thread.startVirtualThread(() -> {
    // I/O 密集型任务
    var result = httpClient.send(request, BodyHandlers.ofString());
});

// 使用 ExecutorService
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(() -> processRequest(request));
}
```

### 1.2 并行任务执行 (CompletableFuture + Virtual Threads)

Java 22 中使用 CompletableFuture 配合 Virtual Threads 实现并行任务管理。

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

// 并行执行多个任务
public SceneContext createSceneContext(SceneRequest request) {
    var executor = Executors.newVirtualThreadPerTaskExecutor();
    
    // 并行启动多个子任务
    var choreographyFuture = CompletableFuture.supplyAsync(() -> 
        choreographerAgent.designMoves(request), executor
    );
    
    var psychologyFuture = CompletableFuture.supplyAsync(() -> 
        psychologistAgent.analyzeState(request), executor
    );
    
    // 等待所有任务完成
    CompletableFuture.allOf(choreographyFuture, psychologyFuture).join();
    
    // 获取结果
    return new SceneContext(
        request,
        choreographyFuture.join(),
        psychologyFuture.join()
    );
}

// 任一成功则返回
var executor = Executors.newVirtualThreadPerTaskExecutor();
var future1 = CompletableFuture.supplyAsync(() -> fetchFromPrimaryProvider(), executor);
var future2 = CompletableFuture.supplyAsync(() -> fetchFromFallbackProvider(), executor);
return CompletableFuture.anyOf(future1, future2).join();
```

### 1.3 ThreadLocal (请求上下文)

在虚拟线程场景下继续使用 ThreadLocal 传递请求上下文。

```java
// 定义 ThreadLocal
private static final ThreadLocal<UUID> CURRENT_PROJECT_ID = new ThreadLocal<>();
private static final ThreadLocal<User> CURRENT_USER = new ThreadLocal<>();

// 绑定值并执行
public void processRequest(UUID projectId, User user) {
    try {
        CURRENT_PROJECT_ID.set(projectId);
        CURRENT_USER.set(user);
        // 在此作用域内可以访问这些值
        doBusinessLogic();
    } finally {
        CURRENT_PROJECT_ID.remove();
        CURRENT_USER.remove();
    }
}

// 读取值
public void doBusinessLogic() {
    UUID projectId = CURRENT_PROJECT_ID.get();
    User user = CURRENT_USER.get();
}
```

### 1.4 Pattern Matching 增强

```java
// Record Patterns
public String describeEntity(Object entity) {
    return switch (entity) {
        case Character(var id, var name, var role, _) -> 
            "角色: " + name + " (" + role + ")";
        case WikiEntry(var id, var title, var type, _) -> 
            "设定: " + title + " [" + type + "]";
        case PlotLoop(var id, var title, var status, _) when status == Status.URGENT -> 
            "⚠️ 紧急伏笔: " + title;
        case null -> "空实体";
        default -> entity.toString();
    };
}

// Unnamed Patterns (使用 _ 忽略不需要的值)
if (obj instanceof Point(int x, _)) {
    System.out.println("x = " + x);
}
```

---

## 2. Spring Boot 3.5.x 关键特性

### 2.1 基于 Spring Framework 6.2

Spring Boot 3.5.x 基于 Spring Framework 6.2，主要特性：

```java
// 新的 @HttpExchange 声明式 HTTP 客户端
@HttpExchange("/api/ai")
public interface AIProviderClient {
    
    @PostExchange("/chat/completions")
    Mono<ChatResponse> chat(@RequestBody ChatRequest request);
    
    @GetExchange("/models")
    List<Model> listModels();
}

// 配置 HTTP 客户端
@Bean
AIProviderClient aiProviderClient(WebClient.Builder builder) {
    WebClient webClient = builder.baseUrl("https://api.openai.com/v1").build();
    HttpServiceProxyFactory factory = HttpServiceProxyFactory
        .builderFor(WebClientAdapter.create(webClient))
        .build();
    return factory.createClient(AIProviderClient.class);
}
```

### 2.2 配置属性绑定增强

```java
// 使用 record 作为配置类
@ConfigurationProperties(prefix = "inkflow.ai")
public record AIProperties(
    String defaultProvider,
    Map<String, ProviderConfig> providers,
    RetryConfig retry
) {
    public record ProviderConfig(String apiKey, String baseUrl, String model) {}
    public record RetryConfig(int maxAttempts, Duration backoff) {}
}

// application.yml
inkflow:
  ai:
    default-provider: deepseek
    providers:
      deepseek:
        api-key: ${DEEPSEEK_API_KEY}
        base-url: https://api.deepseek.com
        model: deepseek-chat
    retry:
      max-attempts: 3
      backoff: 1s
```

### 2.3 Observability 增强

```java
// 自动追踪配置
management:
  tracing:
    sampling:
      probability: 1.0
  observations:
    annotations:
      enabled: true

// 使用 @Observed 注解
@Observed(name = "ai.chat", contextualName = "ai-chat-request")
public Flux<String> streamChat(ChatRequest request) {
    return chatClient.stream(request);
}

// 自定义 ObservationHandler
@Component
public class AIObservationHandler implements ObservationHandler<Observation.Context> {
    @Override
    public void onStart(Observation.Context context) {
        // 记录开始
    }
    
    @Override
    public void onStop(Observation.Context context) {
        // 记录结束，包括 token 使用量
    }
}
```

---

## 3. Spring AI 1.1.2 关键 API

### 3.1 统一的 ChatClient API

Spring AI 1.1.2 提供了简化的流式 API：

```java
@Service
public class SpringAIChatService {
    
    private final ChatClient chatClient;
    
    public SpringAIChatService(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem("你是一个专业的小说创作助手")
            .defaultAdvisors(
                new MessageChatMemoryAdvisor(chatMemory),
                new QuestionAnswerAdvisor(vectorStore)
            )
            .build();
    }
    
    // 简单调用
    public String chat(String userMessage) {
        return chatClient.prompt()
            .user(userMessage)
            .call()
            .content();
    }
    
    // 流式调用
    public Flux<String> streamChat(String userMessage) {
        return chatClient.prompt()
            .user(userMessage)
            .stream()
            .content();
    }
    
    // 带工具调用
    public String chatWithTools(String userMessage, Object... tools) {
        return chatClient.prompt()
            .user(userMessage)
            .tools(tools)
            .call()
            .content();
    }
}
```

### 3.2 Function Calling (工具调用)

```java
// 使用 @Tool 注解定义工具
@Component
public class RAGSearchTool {
    
    private final HybridSearchService searchService;
    
    @Tool(description = "搜索小说设定和知识库，返回相关内容")
    public List<SearchResult> searchKnowledge(
        @ToolParam(description = "搜索查询词") String query,
        @ToolParam(description = "项目ID") UUID projectId,
        @ToolParam(description = "返回结果数量", required = false) Integer topK
    ) {
        return searchService.hybridSearch(projectId, query, topK != null ? topK : 5);
    }
}

// 使用 Function 接口定义工具
@Bean
public Function<CharacterQuery, Character> getCharacter(CharacterService service) {
    return query -> service.findById(query.characterId());
}

public record CharacterQuery(
    @JsonPropertyDescription("角色ID") UUID characterId
) {}

// 注册工具到 ChatClient
chatClient.prompt()
    .user("帮我查找主角的设定")
    .tools(ragSearchTool, getCharacterFunction)
    .call();
```

### 3.3 Advisor 模式 (请求/响应拦截器)

```java
// RAG Advisor - 自动检索相关上下文
@Component
public class NovelRAGAdvisor implements RequestResponseAdvisor {
    
    private final VectorStore vectorStore;
    
    @Override
    public AdvisedRequest adviseRequest(AdvisedRequest request, Map<String, Object> context) {
        // 检索相关文档
        List<Document> docs = vectorStore.similaritySearch(
            SearchRequest.query(request.userText())
                .withTopK(5)
                .withSimilarityThreshold(0.7)
        );
        
        // 构建增强的系统提示
        String augmentedSystem = request.systemText() + "\n\n相关设定:\n" + 
            docs.stream().map(Document::getContent).collect(Collectors.joining("\n"));
        
        return AdvisedRequest.from(request)
            .withSystemText(augmentedSystem)
            .build();
    }
}

// 使用 Advisor
chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(
        new MessageChatMemoryAdvisor(chatMemory, 10),  // 保留最近10条消息
        new NovelRAGAdvisor(vectorStore),
        new SafeGuardAdvisor()  // 内容安全检查
    )
    .build();
```

### 3.4 ChatMemory (对话记忆)

```java
// 内置的 ChatMemory 实现
@Bean
public ChatMemory chatMemory() {
    // 内存实现
    return new InMemoryChatMemory();
}

// 持久化实现 (自定义)
@Component
public class PersistentChatMemory implements ChatMemory {
    
    private final ConversationHistoryRepository repository;
    
    @Override
    public void add(String conversationId, List<Message> messages) {
        messages.forEach(msg -> {
            var entity = new ConversationHistory();
            entity.setConversationId(conversationId);
            entity.setRole(msg.getMessageType().name());
            entity.setContent(msg.getContent());
            repository.save(entity);
        });
    }
    
    @Override
    public List<Message> get(String conversationId, int lastN) {
        return repository.findByConversationIdOrderByCreatedAtDesc(conversationId)
            .stream()
            .limit(lastN)
            .map(this::toMessage)
            .toList()
            .reversed();
    }
    
    @Override
    public void clear(String conversationId) {
        repository.deleteByConversationId(conversationId);
    }
}

// MessageChatMemoryAdvisor 自动管理对话历史
var advisor = new MessageChatMemoryAdvisor(chatMemory, "conversation-123", 20);
```

### 3.5 Embedding API

```java
// 使用 EmbeddingModel
@Service
public class EmbeddingService {
    
    private final EmbeddingModel embeddingModel;
    
    // 单个文本嵌入
    public float[] embed(String text) {
        return embeddingModel.embed(text);
    }
    
    // 批量嵌入
    public List<float[]> embedBatch(List<String> texts) {
        EmbeddingResponse response = embeddingModel.call(
            new EmbeddingRequest(texts, EmbeddingOptions.EMPTY)
        );
        return response.getResults().stream()
            .map(Embedding::getOutput)
            .toList();
    }
}

// 配置多个 Embedding 提供商
@Bean
@Primary
public EmbeddingModel embeddingModel(
    @Value("${inkflow.embedding.provider}") String provider
) {
    return switch (provider) {
        case "openai" -> new OpenAiEmbeddingModel(openAiApi);
        case "ollama" -> new OllamaEmbeddingModel(ollamaApi);
        default -> throw new IllegalArgumentException("Unknown provider: " + provider);
    };
}
```

### 3.6 VectorStore API

```java
// 使用 PgVectorStore
@Bean
public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
    return new PgVectorStore(jdbcTemplate, embeddingModel, 
        PgVectorStore.PgVectorStoreConfig.builder()
            .withTableName("knowledge_chunks")
            .withDimensions(1536)
            .withDistanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
            .withIndexType(PgVectorStore.PgIndexType.HNSW)
            .build()
    );
}

// 添加文档
vectorStore.add(List.of(
    new Document("角色设定内容...", Map.of("type", "character", "projectId", projectId)),
    new Document("世界观设定...", Map.of("type", "wiki", "projectId", projectId))
));

// 相似度搜索
List<Document> results = vectorStore.similaritySearch(
    SearchRequest.query("主角的性格特点")
        .withTopK(5)
        .withSimilarityThreshold(0.7)
        .withFilterExpression("projectId == '" + projectId + "'")
);
```

---

## 4. PostgreSQL 18 + pgvector

### 4.1 HNSW 索引配置

```sql
-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 创建带向量列的表
CREATE TABLE knowledge_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1536),  -- OpenAI ada-002 维度
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建 HNSW 索引 (PostgreSQL 18 增强版)
CREATE INDEX idx_knowledge_chunks_embedding ON knowledge_chunks 
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- m: 每个节点的最大连接数，影响召回率和构建速度
-- ef_construction: 构建时的搜索宽度，越大召回率越高但构建越慢
-- 推荐: m=16, ef_construction=64 平衡构建时间和召回率

-- 查询时设置 ef_search
SET hnsw.ef_search = 100;  -- 查询时的搜索宽度
```

### 4.2 向量搜索查询

```sql
-- 余弦相似度搜索 (推荐)
SELECT id, content, 1 - (embedding <=> $1::vector) AS similarity
FROM knowledge_chunks
WHERE project_id = $2
ORDER BY embedding <=> $1::vector
LIMIT 10;

-- 带过滤条件的搜索
SELECT id, content, 1 - (embedding <=> $1::vector) AS similarity
FROM knowledge_chunks
WHERE project_id = $2
  AND metadata->>'type' = 'character'
  AND 1 - (embedding <=> $1::vector) > 0.7  -- 相似度阈值
ORDER BY embedding <=> $1::vector
LIMIT 10;

-- 混合搜索 (向量 + 全文)
WITH vector_results AS (
    SELECT id, content, 1 - (embedding <=> $1::vector) AS vector_score
    FROM knowledge_chunks
    WHERE project_id = $2
    ORDER BY embedding <=> $1::vector
    LIMIT 20
),
fulltext_results AS (
    SELECT id, content, ts_rank(to_tsvector('chinese', content), plainto_tsquery('chinese', $3)) AS text_score
    FROM knowledge_chunks
    WHERE project_id = $2
      AND to_tsvector('chinese', content) @@ plainto_tsquery('chinese', $3)
    LIMIT 20
)
SELECT COALESCE(v.id, f.id) AS id,
       COALESCE(v.content, f.content) AS content,
       COALESCE(v.vector_score, 0) * 0.7 + COALESCE(f.text_score, 0) * 0.3 AS combined_score
FROM vector_results v
FULL OUTER JOIN fulltext_results f ON v.id = f.id
ORDER BY combined_score DESC
LIMIT 10;
```

### 4.3 JPA 实体映射

```java
@Entity
@Table(name = "knowledge_chunks")
public class KnowledgeChunk {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    // 使用自定义类型处理 vector
    @Column(columnDefinition = "vector(1536)")
    @Type(HalfVecType.class)
    private float[] embedding;
    
    @Column(columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;
    
    private LocalDateTime createdAt;
}

// 自定义 Hibernate 类型
public class HalfVecType implements UserType<float[]> {
    @Override
    public int getSqlType() {
        return Types.OTHER;
    }
    
    @Override
    public Class<float[]> returnedClass() {
        return float[].class;
    }
    
    // ... 其他方法实现
}
```

---

## 5. 项目配置示例

### 5.1 pom.xml 依赖

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.0</version>
</parent>

<properties>
    <java.version>22</java.version>
    <spring-ai.version>1.1.2</spring-ai.version>
</properties>

<dependencies>
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
    
    <!-- Spring AI -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-pgvector-store-spring-boot-starter</artifactId>
    </dependency>
    
    <!-- PostgreSQL -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <version>42.7.4</version>
    </dependency>
    
    <!-- pgvector Java 支持 -->
    <dependency>
        <groupId>com.pgvector</groupId>
        <artifactId>pgvector</artifactId>
        <version>0.1.6</version>
    </dependency>
    
    <!-- 属性测试 -->
    <dependency>
        <groupId>net.jqwik</groupId>
        <artifactId>jqwik</artifactId>
        <version>1.9.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 5.2 application.yml 配置

```yaml
spring:
  application:
    name: inkflow-backend
  
  # 启用虚拟线程
  threads:
    virtual:
      enabled: true
  
  # 数据库配置
  datasource:
    url: jdbc:postgresql://localhost:5432/inkflow
    username: ${DB_USERNAME:inkflow}
    password: ${DB_PASSWORD:inkflow}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
  
  # JPA 配置
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
  
  # Flyway 迁移
  flyway:
    enabled: true
    locations: classpath:db/migration
  
  # Redis 配置
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  
  # Spring AI 配置
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4-turbo
          temperature: 0.7
      embedding:
        options:
          model: text-embedding-ada-002
    
    # 或使用 DeepSeek
    # deepseek:
    #   api-key: ${DEEPSEEK_API_KEY}
    #   base-url: https://api.deepseek.com
    
    vectorstore:
      pgvector:
        index-type: HNSW
        distance-type: COSINE_DISTANCE
        dimensions: 1536

# 可观测性
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  tracing:
    sampling:
      probability: 1.0
  metrics:
    tags:
      application: ${spring.application.name}

# 自定义配置
inkflow:
  ai:
    default-provider: openai
    retry:
      max-attempts: 3
      backoff: 1s
  cache:
    embedding:
      ttl: 24h
      max-size: 10000
```

---

## 6. 常见模式和最佳实践

### 6.1 SSE 流式响应

```java
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    
    private final SpringAIChatService chatService;
    
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(@RequestBody ChatRequest request) {
        return chatService.streamChat(request.message())
            .map(content -> ServerSentEvent.<String>builder()
                .event("message")
                .data(content)
                .build())
            .concatWith(Flux.just(
                ServerSentEvent.<String>builder()
                    .event("done")
                    .data("[DONE]")
                    .build()
            ))
            .onErrorResume(e -> Flux.just(
                ServerSentEvent.<String>builder()
                    .event("error")
                    .data(e.getMessage())
                    .build()
            ));
    }
}
```

### 6.2 多 Agent 协同 (使用 Structured Concurrency)

```java
@Service
public class SceneCreationOrchestrator {
    
    private final WriterAgent writerAgent;
    private final ChoreographerAgent choreographerAgent;
    private final PsychologistAgent psychologistAgent;
    private final ApplicationEventPublisher eventPublisher;
    
    public Flux<SceneChunk> createScene(SceneRequest request) {
        // 使用结构化并发并行执行多个 Agent
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            
            var choreographyTask = scope.fork(() -> {
                eventPublisher.publishEvent(new AgentThoughtEvent("choreographer", "分析动作设定..."));
                return choreographerAgent.designMoves(request);
            });
            
            var psychologyTask = scope.fork(() -> {
                eventPublisher.publishEvent(new AgentThoughtEvent("psychologist", "分析角色心理..."));
                return psychologistAgent.analyzeState(request);
            });
            
            scope.join();
            scope.throwIfFailed();
            
            // 综合结果交给 Writer Agent
            var enrichedContext = new SceneContext(
                request,
                choreographyTask.get(),
                psychologyTask.get()
            );
            
            eventPublisher.publishEvent(new AgentThoughtEvent("writer", "开始创作..."));
            return writerAgent.streamContent(enrichedContext);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Flux.error(e);
        }
    }
}
```

### 6.3 CDC 事件监听 (主动式知识图谱)

```java
@Component
public class WikiChangeListener {
    
    private final ConsistencyCheckAgent consistencyAgent;
    private final ApplicationEventPublisher eventPublisher;
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWikiEntryChanged(WikiEntryChangedEvent event) {
        // 异步触发一致性检查
        Thread.startVirtualThread(() -> {
            var conflicts = consistencyAgent.checkConsistency(
                event.getProjectId(),
                event.getWikiEntryId()
            );
            
            if (!conflicts.isEmpty()) {
                eventPublisher.publishEvent(new InconsistencyDetectedEvent(
                    event.getProjectId(),
                    conflicts
                ));
            }
        });
    }
}
```

### 6.4 属性测试 (jqwik)

```java
@PropertyDefaults(tries = 100)
class LexorankPropertyTest {
    
    /**
     * Property 1: Lexorank中间值排序正确性
     * Validates: Requirements 3.4
     */
    @Property
    void middleRankSortsBetweenAdjacentRanks(
        @ForAll @StringLength(min = 1, max = 10) String before,
        @ForAll @StringLength(min = 1, max = 10) String after
    ) {
        Assume.that(before.compareTo(after) < 0);
        
        String middle = LexorankService.calculateMiddle(before, after);
        
        assertThat(middle.compareTo(before)).isGreaterThan(0);
        assertThat(middle.compareTo(after)).isLessThan(0);
    }
    
    /**
     * Property 6: 导出导入往返数据一致性
     * Validates: Requirements 2.5, 2.6
     */
    @Property
    void exportImportRoundTrip(@ForAll("validProjects") Project project) {
        String json = exportService.exportToJson(project.getId());
        Project imported = importService.importFromJson(json, project.getUserId());
        
        // 验证核心数据一致
        assertThat(imported.getTitle()).isEqualTo(project.getTitle());
        assertThat(imported.getVolumes()).hasSameSizeAs(project.getVolumes());
        assertThat(imported.getCharacters()).hasSameSizeAs(project.getCharacters());
    }
    
    @Provide
    Arbitrary<Project> validProjects() {
        return Arbitraries.of(testProjectGenerator.generate());
    }
}
```

---

## 7. 注意事项

1. **Virtual Threads 限制**: 避免在虚拟线程中使用 synchronized 块进行长时间阻塞，改用 ReentrantLock
2. **Structured Concurrency**: 确保所有 fork 的任务在 scope 关闭前完成
3. **Spring AI 1.1.2**: ChatClient 是线程安全的，可以作为单例使用
4. **pgvector HNSW**: 索引构建是增量的，但大批量插入后建议 REINDEX
5. **Scoped Values**: 不可变，适合传递请求上下文，不适合可变状态


---

## 8. 高级架构模式 (Advanced Architecture Patterns)

本章补充 AI 原生架构的三个关键基础设施模式。

### 8.1 PostgreSQL CDC 触发机制 (LISTEN/NOTIFY)

应用层事件 (`@TransactionalEventListener`) 无法捕获直接数据库修改或跨实例传播。使用 PostgreSQL 原生 CDC 实现真正的"数据变更即触发"。

```sql
-- 1. 创建通知函数
CREATE OR REPLACE FUNCTION notify_wiki_change() RETURNS TRIGGER AS $$
BEGIN
  PERFORM pg_notify('wiki_changes', json_build_object(
    'operation', TG_OP,
    'id', NEW.id,
    'project_id', NEW.project_id,
    'title', NEW.title,
    'updated_at', NEW.updated_at
  )::text);
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 2. 创建触发器
CREATE TRIGGER trigger_wiki_update
AFTER INSERT OR UPDATE ON wiki_entries
FOR EACH ROW EXECUTE FUNCTION notify_wiki_change();

-- 3. 角色设定变更触发器
CREATE OR REPLACE FUNCTION notify_character_change() RETURNS TRIGGER AS $$
BEGIN
  PERFORM pg_notify('character_changes', json_build_object(
    'operation', TG_OP,
    'id', NEW.id,
    'project_id', NEW.project_id,
    'name', NEW.name
  )::text);
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_character_update
AFTER INSERT OR UPDATE ON characters
FOR EACH ROW EXECUTE FUNCTION notify_character_change();
```

```java
// Spring 侧监听配置
@Configuration
@EnableScheduling
public class PgNotificationConfig {
    
    private final DataSource dataSource;
    private final ConsistencyCheckAgent consistencyAgent;
    private final ApplicationEventPublisher eventPublisher;
    
    @Scheduled(fixedDelay = 100) // 100ms 轮询
    public void listenForNotifications() {
        try (Connection conn = dataSource.getConnection()) {
            PGConnection pgConn = conn.unwrap(PGConnection.class);
            
            // 订阅频道
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("LISTEN wiki_changes");
                stmt.execute("LISTEN character_changes");
            }
            
            // 获取通知
            PGNotification[] notifications = pgConn.getNotifications(100);
            if (notifications != null) {
                for (PGNotification notification : notifications) {
                    handleNotification(notification);
                }
            }
        } catch (SQLException e) {
            log.error("PG notification error", e);
        }
    }
    
    private void handleNotification(PGNotification notification) {
        String channel = notification.getName();
        String payload = notification.getParameter();
        
        // 使用虚拟线程异步处理
        Thread.startVirtualThread(() -> {
            var change = objectMapper.readValue(payload, EntityChange.class);
            
            switch (channel) {
                case "wiki_changes" -> {
                    var conflicts = consistencyAgent.checkConsistency(
                        change.projectId(), change.id()
                    );
                    if (!conflicts.isEmpty()) {
                        eventPublisher.publishEvent(
                            new InconsistencyDetectedEvent(change.projectId(), conflicts)
                        );
                    }
                }
                case "character_changes" -> {
                    // 触发角色关系图更新
                    eventPublisher.publishEvent(
                        new CharacterGraphUpdateEvent(change.projectId(), change.id())
                    );
                }
            }
        });
    }
}

public record EntityChange(
    String operation,
    UUID id,
    UUID projectId,
    String title,
    LocalDateTime updatedAt
) {}
```

### 8.2 智能模型路由器 (Hybrid Model Router)

根据任务复杂度、隐私敏感性、Token 数量动态选择本地或云端模型。

```java
@Service
public class HybridModelRouter {
    
    private final ChatModel localModel;   // Ollama 本地模型
    private final ChatModel cloudModel;   // DeepSeek/OpenAI 云端模型
    private final ComplexityAnalyzer complexityAnalyzer;
    
    public HybridModelRouter(
        @Qualifier("ollamaChatModel") ChatModel localModel,
        @Qualifier("deepseekChatModel") ChatModel cloudModel,
        ComplexityAnalyzer complexityAnalyzer
    ) {
        this.localModel = localModel;
        this.cloudModel = cloudModel;
        this.complexityAnalyzer = complexityAnalyzer;
    }
    
    /**
     * 动态选择模型
     */
    public ChatModel selectModel(String prompt, RoutingContext context) {
        var complexity = complexityAnalyzer.analyze(prompt);
        
        // 规则 1: 隐私敏感内容 -> 强制本地
        if (context.isPrivacySensitive()) {
            log.info("路由决策: 隐私敏感 -> 本地模型");
            return localModel;
        }
        
        // 规则 2: 简单任务 (补全、格式化) -> 本地
        if (complexity.level() == ComplexityLevel.LOW) {
            log.info("路由决策: 简单任务 -> 本地模型");
            return localModel;
        }
        
        // 规则 3: 复杂推理、长文本生成 -> 云端
        if (complexity.level() == ComplexityLevel.HIGH || 
            complexity.estimatedTokens() > 2000) {
            log.info("路由决策: 复杂任务 -> 云端模型");
            return cloudModel;
        }
        
        // 规则 4: 云端不可用时降级到本地
        if (!isCloudAvailable()) {
            log.warn("路由决策: 云端不可用 -> 降级本地模型");
            return localModel;
        }
        
        // 默认: 云端
        return cloudModel;
    }
    
    /**
     * 智能聊天 - 自动路由
     */
    public String chat(String prompt, RoutingContext context) {
        ChatModel model = selectModel(prompt, context);
        return model.call(prompt);
    }
    
    /**
     * 流式聊天 - 自动路由
     */
    public Flux<String> streamChat(String prompt, RoutingContext context) {
        ChatModel model = selectModel(prompt, context);
        return model.stream(prompt);
    }
    
    private boolean isCloudAvailable() {
        // 检查云端模型健康状态
        return modelHealthIndicator.isCloudHealthy();
    }
}

@Component
public class ComplexityAnalyzer {
    
    // 复杂任务关键词
    private static final Set<String> COMPLEX_KEYWORDS = Set.of(
        "分析", "推理", "对比", "总结", "创作", "设计", "规划"
    );
    
    // 简单任务关键词
    private static final Set<String> SIMPLE_KEYWORDS = Set.of(
        "补全", "格式化", "翻译", "纠错", "提取"
    );
    
    public ComplexityResult analyze(String prompt) {
        int tokenEstimate = estimateTokens(prompt);
        ComplexityLevel level = determineLevel(prompt, tokenEstimate);
        
        return new ComplexityResult(level, tokenEstimate);
    }
    
    private ComplexityLevel determineLevel(String prompt, int tokens) {
        // Token 数量判断
        if (tokens > 3000) return ComplexityLevel.HIGH;
        if (tokens < 200) return ComplexityLevel.LOW;
        
        // 关键词判断
        String lowerPrompt = prompt.toLowerCase();
        if (COMPLEX_KEYWORDS.stream().anyMatch(lowerPrompt::contains)) {
            return ComplexityLevel.HIGH;
        }
        if (SIMPLE_KEYWORDS.stream().anyMatch(lowerPrompt::contains)) {
            return ComplexityLevel.LOW;
        }
        
        return ComplexityLevel.MEDIUM;
    }
    
    private int estimateTokens(String text) {
        // 中文约 1.5 字符/token，英文约 4 字符/token
        return (int) (text.length() / 1.5);
    }
}

public record ComplexityResult(ComplexityLevel level, int estimatedTokens) {}

public enum ComplexityLevel { LOW, MEDIUM, HIGH }

public record RoutingContext(
    UUID projectId,
    boolean isPrivacySensitive,
    String taskType
) {
    public static RoutingContext normal(UUID projectId) {
        return new RoutingContext(projectId, false, "general");
    }
    
    public static RoutingContext sensitive(UUID projectId) {
        return new RoutingContext(projectId, true, "sensitive");
    }
}
```

### 8.3 Agent 状态持久化 (Stateful Agent Runner)

长时间运行的 Agent（如大纲生成、一致性检查）需要持久化状态，防止服务重启导致"失忆"。

```java
// Agent 状态记录
public record AgentState(
    UUID executionId,
    UUID projectId,
    String agentType,           // e.g., "OUTLINE_AGENT", "CONSISTENCY_AGENT"
    String currentStep,         // e.g., "ANALYZING", "GENERATING", "WAITING_APPROVAL"
    Map<String, Object> interimResults,
    AgentStatus status,
    LocalDateTime startedAt,
    LocalDateTime updatedAt,
    int retryCount
) {}

public enum AgentStatus {
    PENDING,      // 等待执行
    RUNNING,      // 执行中
    PAUSED,       // 暂停等待用户确认
    COMPLETED,    // 完成
    FAILED,       // 失败
    CANCELLED     // 取消
}

@Service
public class StatefulAgentRunner {
    
    private final RedisTemplate<String, AgentState> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    
    private static final String KEY_PREFIX = "agent:state:";
    private static final Duration STATE_TTL = Duration.ofHours(24);
    
    /**
     * 启动 Agent 执行
     */
    public UUID startAgent(UUID projectId, String agentType, Map<String, Object> params) {
        UUID executionId = UUID.randomUUID();
        
        AgentState state = new AgentState(
            executionId,
            projectId,
            agentType,
            "INITIALIZING",
            params,
            AgentStatus.PENDING,
            LocalDateTime.now(),
            LocalDateTime.now(),
            0
        );
        
        saveState(state);
        
        // 使用虚拟线程异步执行
        Thread.startVirtualThread(() -> executeAgent(executionId));
        
        return executionId;
    }
    
    /**
     * 执行 Agent 步骤
     */
    public void runStep(UUID executionId, String stepName, Runnable stepLogic) {
        AgentState state = getState(executionId);
        if (state == null || state.status() == AgentStatus.CANCELLED) {
            return;
        }
        
        try {
            // 更新状态为运行中
            updateState(executionId, stepName, AgentStatus.RUNNING);
            
            // 执行步骤逻辑
            stepLogic.run();
            
            // 保存检查点
            saveCheckpoint(executionId, stepName);
            
        } catch (Exception e) {
            handleStepFailure(executionId, stepName, e);
        }
    }
    
    /**
     * 暂停等待用户确认
     */
    public void pauseForApproval(UUID executionId, String message) {
        updateState(executionId, "WAITING_APPROVAL", AgentStatus.PAUSED);
        
        eventPublisher.publishEvent(new AgentApprovalRequestEvent(
            executionId,
            message
        ));
    }
    
    /**
     * 用户确认后恢复执行
     */
    public void resumeAgent(UUID executionId, boolean approved) {
        AgentState state = getState(executionId);
        if (state == null || state.status() != AgentStatus.PAUSED) {
            return;
        }
        
        if (approved) {
            updateState(executionId, state.currentStep(), AgentStatus.RUNNING);
            Thread.startVirtualThread(() -> continueExecution(executionId));
        } else {
            updateState(executionId, state.currentStep(), AgentStatus.CANCELLED);
        }
    }
    
    /**
     * 从检查点恢复 (服务重启后)
     */
    @PostConstruct
    public void recoverPendingAgents() {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys == null) return;
        
        for (String key : keys) {
            AgentState state = redisTemplate.opsForValue().get(key);
            if (state != null && state.status() == AgentStatus.RUNNING) {
                log.info("恢复中断的 Agent: {}", state.executionId());
                Thread.startVirtualThread(() -> continueExecution(state.executionId()));
            }
        }
    }
    
    /**
     * 获取 Agent 状态
     */
    public AgentState getState(UUID executionId) {
        return redisTemplate.opsForValue().get(KEY_PREFIX + executionId);
    }
    
    private void saveState(AgentState state) {
        redisTemplate.opsForValue().set(
            KEY_PREFIX + state.executionId(),
            state,
            STATE_TTL
        );
    }
    
    private void updateState(UUID executionId, String step, AgentStatus status) {
        AgentState current = getState(executionId);
        if (current == null) return;
        
        AgentState updated = new AgentState(
            current.executionId(),
            current.projectId(),
            current.agentType(),
            step,
            current.interimResults(),
            status,
            current.startedAt(),
            LocalDateTime.now(),
            current.retryCount()
        );
        
        saveState(updated);
        
        // 发布状态变更事件
        eventPublisher.publishEvent(new AgentStateChangedEvent(updated));
    }
    
    private void saveCheckpoint(UUID executionId, String stepName) {
        log.debug("Agent {} 检查点保存: {}", executionId, stepName);
    }
    
    private void handleStepFailure(UUID executionId, String stepName, Exception e) {
        AgentState state = getState(executionId);
        if (state == null) return;
        
        if (state.retryCount() < 3) {
            // 重试
            AgentState retryState = new AgentState(
                state.executionId(),
                state.projectId(),
                state.agentType(),
                stepName,
                state.interimResults(),
                AgentStatus.PENDING,
                state.startedAt(),
                LocalDateTime.now(),
                state.retryCount() + 1
            );
            saveState(retryState);
            
            log.warn("Agent {} 步骤 {} 失败，准备重试 ({}/3)", 
                executionId, stepName, retryState.retryCount());
                
        } else {
            // 标记失败
            updateState(executionId, stepName, AgentStatus.FAILED);
            log.error("Agent {} 步骤 {} 最终失败", executionId, stepName, e);
            
            // 发送失败通知
            eventPublisher.publishEvent(new AgentFailedEvent(executionId, stepName, e));
        }
    }
    
    private void executeAgent(UUID executionId) {
        // 由具体 Agent 实现
    }
    
    private void continueExecution(UUID executionId) {
        // 从检查点继续执行
    }
}

// 事件定义
public record AgentStateChangedEvent(AgentState state) {}
public record AgentApprovalRequestEvent(UUID executionId, String message) {}
public record AgentFailedEvent(UUID executionId, String step, Exception error) {}
```

### 8.4 配置示例

```yaml
# application.yml 补充配置
inkflow:
  cdc:
    enabled: true
    poll-interval: 100ms
    channels:
      - wiki_changes
      - character_changes
  
  routing:
    enabled: true
    default-model: cloud
    complexity-threshold:
      low: 200      # tokens
      high: 3000    # tokens
    privacy-keywords:
      - 密码
      - 身份证
      - 银行卡
  
  agent:
    state-ttl: 24h
    max-retries: 3
    recovery-on-startup: true
```

---

## 9. 注意事项补充

6. **PG LISTEN/NOTIFY**: 连接池中的连接不会自动接收通知，需要专用连接或定时轮询
7. **模型路由**: 本地模型需要足够的 GPU 内存，建议至少 8GB VRAM
8. **Agent 状态**: Redis 需要配置持久化 (RDB/AOF)，防止数据丢失
9. **虚拟线程 + Redis**: RedisTemplate 操作是阻塞的，虚拟线程会自动 yield，无需额外处理
