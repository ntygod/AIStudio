package com.inkflow.module.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG模块统一配置类
 * 整合混合检索、向量嵌入、语义分块、全文搜索和重排序的所有配置。
 * 
 * @author zsg
 * @date 2025/12/17
 */
@ConfigurationProperties(prefix = "inkflow.rag")
public record RagProperties(
    HybridSearchConfig hybridSearch,
    EmbeddingConfig embedding,
    ChunkingConfig chunking,
    FullTextConfig fullText,
    RerankerConfig reranker,
    SearchConfig search
) {
    
    /**
     * 检索策略配置
     * 控制是否使用父子块检索策略
     */
    public record SearchConfig(
        /** 是否使用父子块检索策略（小块检索，大块返回） */
        boolean useParentChild
    ) {
        // Default value constants
        public static final boolean DEFAULT_USE_PARENT_CHILD = true;
        
        public SearchConfig {
            // useParentChild 是 boolean，无需特殊处理，默认值在 defaults() 中设置
        }
        
        public static SearchConfig defaults() {
            return new SearchConfig(DEFAULT_USE_PARENT_CHILD);
        }
    }
    
    /**
     * 混合检索配置
     */
    public record HybridSearchConfig(
        /** RRF常数，默认60 */
        double rrfK,
        /** 向量权重，默认0.7 */
        double vectorWeight,
        /** 关键词权重，默认0.3 */
        double keywordWeight,
        /** 是否启用重排序 */
        boolean enableReranker,
        /** 默认返回结果数量 */
        int defaultTopK,
        /** 召回倍数（用于两阶段检索） */
        int recallMultiplier,
        /** 相似度阈值（用于去重） */
        double similarityThreshold
    ) {
        // Default value constants
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


    /**
     * 向量嵌入服务配置
     */
    public record EmbeddingConfig(
        /** 提供商类型 */
        String provider,
        /** 服务端点 */
        String endpoint,
        /** API路径 */
        String apiPath,
        /** 模型名称 */
        String model,
        /** 向量维度 */
        int dimension,
        /** 批量大小 */
        int batchSize,
        /** 超时时间（毫秒） */
        int timeoutMs,
        /** 最大重试次数 */
        int maxRetries,
        /** 重试延迟（毫秒） */
        int retryDelayMs,
        /** 是否启用缓存 */
        boolean enableCache,
        /** 缓存过期时间（秒） */
        int cacheExpirationSeconds,
        /** 缓存最大条目数 */
        int cacheMaxSize,
        /** 是否启用降级 */
        boolean enableFallback,
        /** 断路器配置 */
        CircuitBreakerConfig circuitBreaker
    ) {
        // Default value constants
        public static final String DEFAULT_PROVIDER = "local-bge";
        public static final String DEFAULT_ENDPOINT = "http://localhost:8093";
        public static final String DEFAULT_API_PATH = "v1/embeddings";
        public static final String DEFAULT_MODEL = "bge-m3";
        public static final int DEFAULT_DIMENSION = 1024;
        public static final int DEFAULT_BATCH_SIZE = 32;
        public static final int DEFAULT_TIMEOUT_MS = 5000;
        public static final int DEFAULT_MAX_RETRIES = 3;
        public static final int DEFAULT_RETRY_DELAY_MS = 100;
        public static final boolean DEFAULT_ENABLE_CACHE = true;
        public static final int DEFAULT_CACHE_EXPIRATION_SECONDS = 3600;
        public static final int DEFAULT_CACHE_MAX_SIZE = 10000;
        public static final boolean DEFAULT_ENABLE_FALLBACK = true;
        
        public EmbeddingConfig {
            if (provider == null || provider.isBlank()) provider = DEFAULT_PROVIDER;
            if (endpoint == null || endpoint.isBlank()) endpoint = DEFAULT_ENDPOINT;
            if (apiPath == null || apiPath.isBlank()) apiPath = DEFAULT_API_PATH;
            if (model == null || model.isBlank()) model = DEFAULT_MODEL;
            if (dimension <= 0) dimension = DEFAULT_DIMENSION;
            if (batchSize <= 0) batchSize = DEFAULT_BATCH_SIZE;
            if (timeoutMs <= 0) timeoutMs = DEFAULT_TIMEOUT_MS;
            if (maxRetries <= 0) maxRetries = DEFAULT_MAX_RETRIES;
            if (retryDelayMs <= 0) retryDelayMs = DEFAULT_RETRY_DELAY_MS;
            if (cacheExpirationSeconds <= 0) cacheExpirationSeconds = DEFAULT_CACHE_EXPIRATION_SECONDS;
            if (cacheMaxSize <= 0) cacheMaxSize = DEFAULT_CACHE_MAX_SIZE;
            if (circuitBreaker == null) circuitBreaker = CircuitBreakerConfig.embeddingDefaults();
        }
        
        public static EmbeddingConfig defaults() {
            return new EmbeddingConfig(
                DEFAULT_PROVIDER,
                DEFAULT_ENDPOINT,
                DEFAULT_API_PATH,
                DEFAULT_MODEL,
                DEFAULT_DIMENSION,
                DEFAULT_BATCH_SIZE,
                DEFAULT_TIMEOUT_MS,
                DEFAULT_MAX_RETRIES,
                DEFAULT_RETRY_DELAY_MS,
                DEFAULT_ENABLE_CACHE,
                DEFAULT_CACHE_EXPIRATION_SECONDS,
                DEFAULT_CACHE_MAX_SIZE,
                DEFAULT_ENABLE_FALLBACK,
                CircuitBreakerConfig.embeddingDefaults()
            );
        }
    }

    
    /**
     * 语义分块配置
     */
    public record ChunkingConfig(
        /** 子块最大大小 */
        int maxChildSize,
        /** 子块最小大小 */
        int minChildSize,
        /** 目标子块大小 */
        int targetChildSize,
        /** 父块最大大小 */
        int maxParentSize,
        /** 父块最小大小 */
        int minParentSize,
        /** 断崖阈值百分位（0-1） */
        double cliffThreshold,
        /** 相似度阈值 */
        double similarityThreshold,
        /** 是否使用重排序计算相似度 */
        boolean useReranker,
        /** 上下文窗口大小 */
        int contextWindowSize,
        /** 上下文重叠大小 */
        int contextOverlapSize
    ) {
        // Default value constants
        public static final int DEFAULT_MAX_CHILD_SIZE = 400;
        public static final int DEFAULT_MIN_CHILD_SIZE = 100;
        public static final int DEFAULT_TARGET_CHILD_SIZE = 250;
        public static final int DEFAULT_MAX_PARENT_SIZE = 1500;
        public static final int DEFAULT_MIN_PARENT_SIZE = 150;
        public static final double DEFAULT_CLIFF_THRESHOLD = 0.2;
        public static final double DEFAULT_SIMILARITY_THRESHOLD = 0.3;
        public static final boolean DEFAULT_USE_RERANKER = true;
        public static final int DEFAULT_CONTEXT_WINDOW_SIZE = 1000;
        public static final int DEFAULT_CONTEXT_OVERLAP_SIZE = 100;
        
        public ChunkingConfig {
            if (maxChildSize <= 0) maxChildSize = DEFAULT_MAX_CHILD_SIZE;
            if (minChildSize <= 0) minChildSize = DEFAULT_MIN_CHILD_SIZE;
            if (targetChildSize <= 0) targetChildSize = DEFAULT_TARGET_CHILD_SIZE;
            if (maxParentSize <= 0) maxParentSize = DEFAULT_MAX_PARENT_SIZE;
            if (minParentSize <= 0) minParentSize = DEFAULT_MIN_PARENT_SIZE;
            if (cliffThreshold <= 0 || cliffThreshold > 1) cliffThreshold = DEFAULT_CLIFF_THRESHOLD;
            if (similarityThreshold <= 0) similarityThreshold = DEFAULT_SIMILARITY_THRESHOLD;
            if (contextWindowSize <= 0) contextWindowSize = DEFAULT_CONTEXT_WINDOW_SIZE;
            if (contextOverlapSize <= 0) contextOverlapSize = DEFAULT_CONTEXT_OVERLAP_SIZE;
        }
        
        public static ChunkingConfig defaults() {
            return new ChunkingConfig(
                DEFAULT_MAX_CHILD_SIZE,
                DEFAULT_MIN_CHILD_SIZE,
                DEFAULT_TARGET_CHILD_SIZE,
                DEFAULT_MAX_PARENT_SIZE,
                DEFAULT_MIN_PARENT_SIZE,
                DEFAULT_CLIFF_THRESHOLD,
                DEFAULT_SIMILARITY_THRESHOLD,
                DEFAULT_USE_RERANKER,
                DEFAULT_CONTEXT_WINDOW_SIZE,
                DEFAULT_CONTEXT_OVERLAP_SIZE
            );
        }
    }


    
    /**
     * 全文搜索配置
     */
    public record FullTextConfig(
        /** 是否启用全文搜索 */
        boolean enabled,
        /** 语言配置 - 默认使用 chinese (zhparser) */
        String language,
        /** 标题权重 */
        String titleWeight,
        /** 内容权重 */
        String contentWeight,
        /** 超时时间（毫秒） */
        int timeoutMs,
        /** 最大重试次数 */
        int maxRetries,
        /** 重试延迟（毫秒） */
        int retryDelayMs,
        /** 失败时是否降级到关键词匹配 */
        boolean fallbackToKeyword,
        /** 高亮配置 */
        HighlightConfig highlight,
        /** zhparser 中文分词配置 */
        ZhparserConfig zhparser
    ) {
        // Default value constants
        public static final boolean DEFAULT_ENABLED = false;
        /** 默认语言改为 chinese，使用 zhparser 中文分词 */
        public static final String DEFAULT_LANGUAGE = "chinese";
        public static final String DEFAULT_TITLE_WEIGHT = "A";
        public static final String DEFAULT_CONTENT_WEIGHT = "B";
        public static final int DEFAULT_TIMEOUT_MS = 5000;
        public static final int DEFAULT_MAX_RETRIES = 2;
        public static final int DEFAULT_RETRY_DELAY_MS = 100;
        public static final boolean DEFAULT_FALLBACK_TO_KEYWORD = true;
        
        public FullTextConfig {
            if (language == null || language.isBlank()) language = DEFAULT_LANGUAGE;
            if (titleWeight == null || titleWeight.isBlank()) titleWeight = DEFAULT_TITLE_WEIGHT;
            if (contentWeight == null || contentWeight.isBlank()) contentWeight = DEFAULT_CONTENT_WEIGHT;
            if (timeoutMs <= 0) timeoutMs = DEFAULT_TIMEOUT_MS;
            if (maxRetries <= 0) maxRetries = DEFAULT_MAX_RETRIES;
            if (retryDelayMs <= 0) retryDelayMs = DEFAULT_RETRY_DELAY_MS;
            if (highlight == null) highlight = HighlightConfig.defaults();
            if (zhparser == null) zhparser = ZhparserConfig.defaults();
        }
        
        public static FullTextConfig defaults() {
            return new FullTextConfig(
                DEFAULT_ENABLED,
                DEFAULT_LANGUAGE,
                DEFAULT_TITLE_WEIGHT,
                DEFAULT_CONTENT_WEIGHT,
                DEFAULT_TIMEOUT_MS,
                DEFAULT_MAX_RETRIES,
                DEFAULT_RETRY_DELAY_MS,
                DEFAULT_FALLBACK_TO_KEYWORD,
                HighlightConfig.defaults(),
                ZhparserConfig.defaults()
            );
        }
    }
    
    /**
     * 高亮显示配置
     */
    public record HighlightConfig(
        /** 是否启用高亮 */
        boolean enabled,
        /** 最大词数 */
        int maxWords,
        /** 最小词数 */
        int minWords,
        /** 短词长度阈值 */
        int shortWord,
        /** 是否高亮所有匹配 */
        boolean highlightAll
    ) {
        // Default value constants
        public static final boolean DEFAULT_ENABLED = true;
        public static final int DEFAULT_MAX_WORDS = 35;
        public static final int DEFAULT_MIN_WORDS = 15;
        public static final int DEFAULT_SHORT_WORD = 3;
        public static final boolean DEFAULT_HIGHLIGHT_ALL = false;
        
        public HighlightConfig {
            if (maxWords <= 0) maxWords = DEFAULT_MAX_WORDS;
            if (minWords <= 0) minWords = DEFAULT_MIN_WORDS;
            if (shortWord <= 0) shortWord = DEFAULT_SHORT_WORD;
        }
        
        public static HighlightConfig defaults() {
            return new HighlightConfig(
                DEFAULT_ENABLED,
                DEFAULT_MAX_WORDS,
                DEFAULT_MIN_WORDS,
                DEFAULT_SHORT_WORD,
                DEFAULT_HIGHLIGHT_ALL
            );
        }
    }
    
    /**
     * zhparser 中文分词配置
     * 用于配置 PostgreSQL zhparser 扩展的分词参数
     * 
     * @param multiShort 短词复合模式，启用后提高召回率
     * @param multiDuality 散字二元复合模式
     * @param punctuationIgnore 是否忽略标点符号
     * @param customDictPath 自定义词典路径（用于小说特定术语：角色名、地名、武功招式等）
     */
    public record ZhparserConfig(
        /** 短词复合模式 - 启用后提高召回率 */
        boolean multiShort,
        /** 散字二元复合模式 */
        boolean multiDuality,
        /** 是否忽略标点符号 */
        boolean punctuationIgnore,
        /** 自定义词典路径（可选） */
        String customDictPath
    ) {
        // Default value constants
        public static final boolean DEFAULT_MULTI_SHORT = true;
        public static final boolean DEFAULT_MULTI_DUALITY = false;
        public static final boolean DEFAULT_PUNCTUATION_IGNORE = true;
        public static final String DEFAULT_CUSTOM_DICT_PATH = null;
        
        public ZhparserConfig {
            // customDictPath 可以为 null，不需要特殊处理
        }
        
        /**
         * 创建带有所有默认值的 ZhparserConfig 实例
         */
        public static ZhparserConfig defaults() {
            return new ZhparserConfig(
                DEFAULT_MULTI_SHORT,
                DEFAULT_MULTI_DUALITY,
                DEFAULT_PUNCTUATION_IGNORE,
                DEFAULT_CUSTOM_DICT_PATH
            );
        }
    }

    
    /**
     * 重排序服务配置
     */
    public record RerankerConfig(
        /** 是否启用重排序 */
        boolean enabled,
        /** 提供商类型 */
        String provider,
        /** 服务端点 */
        String endpoint,
        /** API路径 */
        String apiPath,
        /** 模型名称 */
        String model,
        /** 超时时间（毫秒） */
        int timeoutMs,
        /** 最大重试次数 */
        int maxRetries,
        /** 重试延迟（毫秒） */
        int retryDelayMs,
        /** TopK倍数 */
        int topKMultiplier,
        /** 是否启用降级 */
        boolean enableFallback,
        /** 是否启用缓存 */
        boolean enableCache,
        /** 缓存最大条目数 */
        int cacheMaxSize,
        /** 缓存过期时间（毫秒） */
        long cacheExpirationMs,
        /** 断路器配置 */
        CircuitBreakerConfig circuitBreaker
    ) {
        // Default value constants
        public static final boolean DEFAULT_ENABLED = true;
        public static final String DEFAULT_PROVIDER = "local-bge";
        public static final String DEFAULT_ENDPOINT = "http://localhost:8082";
        public static final String DEFAULT_API_PATH = "/rerank";
        public static final String DEFAULT_MODEL = "bge-reranker-v2-m3";
        public static final int DEFAULT_TIMEOUT_MS = 3000;
        public static final int DEFAULT_MAX_RETRIES = 2;
        public static final int DEFAULT_RETRY_DELAY_MS = 100;
        public static final int DEFAULT_TOP_K_MULTIPLIER = 2;
        public static final boolean DEFAULT_ENABLE_FALLBACK = true;
        public static final boolean DEFAULT_ENABLE_CACHE = true;
        public static final int DEFAULT_CACHE_MAX_SIZE = 1000;
        public static final long DEFAULT_CACHE_EXPIRATION_MS = 300000L; // 5 minutes
        
        public RerankerConfig {
            if (provider == null || provider.isBlank()) provider = DEFAULT_PROVIDER;
            if (endpoint == null || endpoint.isBlank()) endpoint = DEFAULT_ENDPOINT;
            if (apiPath == null || apiPath.isBlank()) apiPath = DEFAULT_API_PATH;
            if (model == null || model.isBlank()) model = DEFAULT_MODEL;
            if (timeoutMs <= 0) timeoutMs = DEFAULT_TIMEOUT_MS;
            if (maxRetries <= 0) maxRetries = DEFAULT_MAX_RETRIES;
            if (retryDelayMs <= 0) retryDelayMs = DEFAULT_RETRY_DELAY_MS;
            if (topKMultiplier <= 0) topKMultiplier = DEFAULT_TOP_K_MULTIPLIER;
            if (cacheMaxSize <= 0) cacheMaxSize = DEFAULT_CACHE_MAX_SIZE;
            if (cacheExpirationMs <= 0) cacheExpirationMs = DEFAULT_CACHE_EXPIRATION_MS;
            if (circuitBreaker == null) circuitBreaker = CircuitBreakerConfig.rerankerDefaults();
        }
        
        public static RerankerConfig defaults() {
            return new RerankerConfig(
                DEFAULT_ENABLED,
                DEFAULT_PROVIDER,
                DEFAULT_ENDPOINT,
                DEFAULT_API_PATH,
                DEFAULT_MODEL,
                DEFAULT_TIMEOUT_MS,
                DEFAULT_MAX_RETRIES,
                DEFAULT_RETRY_DELAY_MS,
                DEFAULT_TOP_K_MULTIPLIER,
                DEFAULT_ENABLE_FALLBACK,
                DEFAULT_ENABLE_CACHE,
                DEFAULT_CACHE_MAX_SIZE,
                DEFAULT_CACHE_EXPIRATION_MS,
                CircuitBreakerConfig.rerankerDefaults()
            );
        }
    }


    
    /**
     * 断路器配置
     */
    public record CircuitBreakerConfig(
        /** 是否启用断路器 */
        boolean enabled,
        /** 失败阈值 */
        int failureThreshold,
        /** 恢复超时时间（毫秒） */
        long recoveryTimeoutMs
    ) {
        // Default value constants
        public static final boolean DEFAULT_ENABLED = true;
        public static final int DEFAULT_FAILURE_THRESHOLD = 5;
        public static final long DEFAULT_RECOVERY_TIMEOUT_MS = 30000L;
        
        // Embedding-specific defaults
        public static final int EMBEDDING_FAILURE_THRESHOLD = 5;
        public static final long EMBEDDING_RECOVERY_TIMEOUT_MS = 30000L;
        
        // Reranker-specific defaults
        public static final int RERANKER_FAILURE_THRESHOLD = 3;
        public static final long RERANKER_RECOVERY_TIMEOUT_MS = 20000L;
        
        public CircuitBreakerConfig {
            if (failureThreshold <= 0) failureThreshold = DEFAULT_FAILURE_THRESHOLD;
            if (recoveryTimeoutMs <= 0) recoveryTimeoutMs = DEFAULT_RECOVERY_TIMEOUT_MS;
        }
        
        /** Embedding服务默认断路器配置 */
        public static CircuitBreakerConfig embeddingDefaults() {
            return new CircuitBreakerConfig(
                DEFAULT_ENABLED,
                EMBEDDING_FAILURE_THRESHOLD,
                EMBEDDING_RECOVERY_TIMEOUT_MS
            );
        }
        
        /** Reranker服务默认断路器配置 */
        public static CircuitBreakerConfig rerankerDefaults() {
            return new CircuitBreakerConfig(
                DEFAULT_ENABLED,
                RERANKER_FAILURE_THRESHOLD,
                RERANKER_RECOVERY_TIMEOUT_MS
            );
        }
    }
    
    /**
     * 使用默认值创建RagProperties实例
     */
    public RagProperties {
        if (hybridSearch == null) hybridSearch = HybridSearchConfig.defaults();
        if (embedding == null) embedding = EmbeddingConfig.defaults();
        if (chunking == null) chunking = ChunkingConfig.defaults();
        if (fullText == null) fullText = FullTextConfig.defaults();
        if (reranker == null) reranker = RerankerConfig.defaults();
        if (search == null) search = SearchConfig.defaults();
    }
    
    /**
     * 创建带有所有默认值的RagProperties实例
     */
    public static RagProperties defaults() {
        return new RagProperties(
            HybridSearchConfig.defaults(),
            EmbeddingConfig.defaults(),
            ChunkingConfig.defaults(),
            FullTextConfig.defaults(),
            RerankerConfig.defaults(),
            SearchConfig.defaults()
        );
    }
}
