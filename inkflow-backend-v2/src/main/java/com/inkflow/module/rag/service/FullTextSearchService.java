package com.inkflow.module.rag.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.module.rag.config.RagProperties;
import com.inkflow.module.rag.dto.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * PostgreSQL全文搜索服务
 * 支持多种查询类型：plain, phrase, boolean, exact
 * 使用ts_rank_cd进行加权排名，支持中文搜索配置（zhparser）
 * 
 * 功能特性：
 * - 支持 zhparser 中文分词扩展
 * - 自动检测 zhparser 可用性并优雅降级
 * - 支持 plainto_tsquery, phraseto_tsquery, to_tsquery 多种查询模式
 * 
 * @author zsg
 * @date 2025/12/17
 * @see ZhparserHealthChecker
 * 
 * Requirements: 5.1, 5.2, 5.3
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FullTextSearchService {

    private final JdbcTemplate jdbcTemplate;
    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;
    
    /**
     * zhparser 健康检查组件
     * 用于检测 zhparser 扩展可用性并提供降级支持
     * Requirements: 5.3
     */
    private final ZhparserHealthChecker zhparserHealthChecker;

    // 中文字符模式（CJK Unified Ideographs）
    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]+");
    
    // 英文字符模式（用于混合语言检测）
    private static final Pattern ENGLISH_PATTERN = Pattern.compile("[a-zA-Z]+");
    
    // 混合语言模式：同时包含中文和英文
    private static final Pattern MIXED_LANGUAGE_PATTERN = Pattern.compile(
        "(?=.*[\\u4e00-\\u9fa5])(?=.*[a-zA-Z])"
    );
    
    // 布尔查询关键词
    private static final Set<String> BOOLEAN_KEYWORDS = Set.of("AND", "OR", "NOT", "&", "|", "!");
    
    // 支持的语言配置白名单（新增 chinese 支持 zhparser）
    // Requirements: 5.1
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("simple", "english", "chinese");

    /**
     * 执行全文搜索
     * 错误处理策略：
     * - 数据库错误：返回空结果集，记录错误日志
     * - 无效查询：返回空结果集
     * - 超时：返回空结果集，记录警告日志
     * 
     * @param projectId 项目ID
     * @param query 查询文本
     * @param sourceType 来源类型（可选）
     * @param limit 结果数量限制
     * @return 搜索结果列表，失败时返回空列表
     */
    public List<SearchResult> search(UUID projectId, String query, String sourceType, int limit) {
        // 参数验证
        if (query == null || query.trim().isEmpty()) {
            log.debug("Full-text search skipped: empty query");
            return Collections.emptyList();
        }
        
        if (projectId == null) {
            log.warn("Full-text search skipped: null projectId");
            return Collections.emptyList();
        }
        
        if (limit <= 0) {
            limit = ragProperties.hybridSearch().defaultTopK();
        }

        try {
            // 先检测查询类型（在预处理之前，以便正确识别混合语言）
            String queryType = detectQueryType(query);
            
            // 根据查询类型选择预处理方法
            // Requirements: 4.2
            String processedQuery;
            if ("mixed".equals(queryType)) {
                processedQuery = preprocessMixedLanguageQuery(query);
            } else {
                processedQuery = preprocessQuery(query);
            }
            
            // 验证处理后的查询
            if (processedQuery.isEmpty()) {
                log.debug("Full-text search skipped: query empty after preprocessing");
                return Collections.emptyList();
            }
            
            // 预处理后重新检测查询类型（因为预处理可能改变查询特征）
            String finalQueryType = detectQueryType(processedQuery);
            
            log.debug("Full-text search: projectId={}, query='{}', originalType={}, finalType={}, limit={}", 
                    projectId, processedQuery, queryType, finalQueryType, limit);

            return executeSearchWithErrorHandling(projectId, processedQuery, sourceType, limit, finalQueryType);
        } catch (Exception e) {
            // 记录错误并返回空结果集
            logSearchError(projectId, query, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 带错误处理的搜索执行
     *
     */
    private List<SearchResult> executeSearchWithErrorHandling(UUID projectId, String query, 
                                                               String sourceType, int limit, 
                                                               String queryType) {
        try {
            return executeSearch(projectId, query, sourceType, limit, queryType);
        } catch (org.springframework.dao.QueryTimeoutException e) {
            // 查询超时 (必须在DataAccessException之前捕获，因为它是子类)
            log.warn("Full-text search timeout: projectId={}, query='{}', timeout exceeded", 
                    projectId, query);
            return Collections.emptyList();
        } catch (org.springframework.dao.DataAccessException e) {
            // 数据库访问错误
            log.error("Database error during full-text search: projectId={}, query='{}', error={}", 
                    projectId, query, e.getMessage());
            return Collections.emptyList();
        } catch (IllegalArgumentException e) {
            // 无效参数（如无效的查询语法）
            log.warn("Invalid query syntax for full-text search: projectId={}, query='{}', error={}", 
                    projectId, query, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 记录搜索错误
     *
     */
    private void logSearchError(UUID projectId, String query, Exception e) {
        if (e instanceof org.springframework.dao.DataAccessException) {
            log.error("Full-text search database error: projectId={}, query='{}', error={}", 
                    projectId, query, e.getMessage(), e);
        } else if (e instanceof IllegalArgumentException) {
            log.warn("Full-text search invalid query: projectId={}, query='{}', error={}", 
                    projectId, query, e.getMessage());
        } else {
            log.error("Full-text search unexpected error: projectId={}, query='{}', error={}", 
                    projectId, query, e.getMessage(), e);
        }
    }

    /**
     * 检测查询类型
     * 
     * 支持以下查询类型：
     * - plain: 普通查询，使用 plainto_tsquery
     * - phrase: 短语查询，使用 phraseto_tsquery
     * - boolean: 布尔查询，使用 to_tsquery
     * - exact: 精确匹配查询
     * - mixed: 混合语言查询（中英文混合），使用 plain 模式但保留英文处理
     * 
     * Requirements: 4.2
     * 
     * @param query 查询文本
     * @return 查询类型: plain, phrase, boolean, exact, mixed
     */
    public String detectQueryType(String query) {
        if (query == null || query.trim().isEmpty()) {
            return "plain";
        }

        String upperQuery = query.toUpperCase();
        
        // 检查布尔关键词（排除混合语言中的普通英文单词）
        // 只有当布尔关键词作为独立词出现时才识别为布尔查询
        for (String keyword : BOOLEAN_KEYWORDS) {
            // 对于单字符操作符直接检查
            if (keyword.length() == 1 && query.contains(keyword)) {
                return "boolean";
            }
            // 对于多字符关键词，检查是否作为独立词出现
            if (keyword.length() > 1) {
                String pattern = "\\b" + keyword + "\\b";
                if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(query).find()) {
                    return "boolean";
                }
            }
        }

        // 引号包围的精确匹配
        if ((query.startsWith("\"") && query.endsWith("\"")) ||
            (query.startsWith("\u201C") && query.endsWith("\u201D"))) {
            return "exact";
        }

        // 检测混合语言查询（同时包含中文和英文）
        // Requirements: 4.2
        if (isMixedLanguageQuery(query)) {
            log.debug("Detected mixed-language query: {}", query);
            return "mixed";
        }

        // 长中文查询或无空格的长查询使用短语搜索
        if (CHINESE_PATTERN.matcher(query).find() && query.length() > 6) {
            return "phrase";
        }
        if (query.length() > 10 && !query.contains(" ")) {
            return "phrase";
        }

        return "plain";
    }
    
    /**
     * 检测是否为混合语言查询（同时包含中文和英文）
     * 
     * Requirements: 4.2
     * 
     * @param query 查询文本
     * @return true 如果查询同时包含中文和英文字符
     */
    public boolean isMixedLanguageQuery(String query) {
        if (query == null || query.isEmpty()) {
            return false;
        }
        
        boolean hasChinese = CHINESE_PATTERN.matcher(query).find();
        boolean hasEnglish = ENGLISH_PATTERN.matcher(query).find();
        
        return hasChinese && hasEnglish;
    }

    /**
     * 预处理查询文本
     * 
     * 处理策略：
     * 1. 去除首尾空白
     * 2. 合并多个空白字符为单个空格
     * 3. 移除引号（中英文）
     * 4. 移除中文标点符号
     * 5. 保留英文字符、数字和基本标点（用于混合语言查询）
     * 
     * Requirements: 4.2
     * 
     * @param query 原始查询文本
     * @return 预处理后的查询文本
     */
    private String preprocessQuery(String query) {
        if (query == null) {
            return "";
        }
        
        String processed = query.trim()
                // 合并多个空白字符为单个空格
                .replaceAll("\\s+", " ")
                // 移除中英文引号
                .replaceAll("[\"'\\u201C\\u201D\\u2018\\u2019]", "")
                // 移除中文标点符号（保留英文标点以支持混合语言）
                // 使用 Unicode 转义避免编码问题
                .replaceAll("[\\uff01\\uff1f\\u3002\\uff0c\\uff1b\\uff1a\\u3001\\u201c\\u201d\\u2018\\u2019\\u3010\\u3011\\u300a\\u300b\\uff08\\uff09]", " ")
                // 再次合并可能产生的多个空格
                .replaceAll("\\s+", " ")
                .trim();
        
        return processed;
    }
    
    /**
     * 预处理混合语言查询
     * 
     * 对于混合语言查询，需要特殊处理以确保中英文都能被正确匹配：
     * 1. 保留英文单词的完整性
     * 2. 在中英文之间添加空格以便分词
     * 3. 移除不必要的标点符号
     * 
     * Requirements: 4.2
     * 
     * @param query 原始查询文本
     * @return 预处理后的混合语言查询文本
     */
    public String preprocessMixedLanguageQuery(String query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        
        // 先进行基本预处理
        String processed = preprocessQuery(query);
        
        // 在中文和英文之间添加空格，以便分词器正确处理
        // 例如: "林动Harry" -> "林动 Harry"
        processed = processed
                // 在中文字符后面紧跟英文字符时添加空格
                .replaceAll("([\\u4e00-\\u9fa5])([a-zA-Z])", "$1 $2")
                // 在英文字符后面紧跟中文字符时添加空格
                .replaceAll("([a-zA-Z])([\\u4e00-\\u9fa5])", "$1 $2")
                // 在中文字符后面紧跟数字时添加空格
                .replaceAll("([\\u4e00-\\u9fa5])([0-9])", "$1 $2")
                // 在数字后面紧跟中文字符时添加空格
                .replaceAll("([0-9])([\\u4e00-\\u9fa5])", "$1 $2")
                // 合并多个空格
                .replaceAll("\\s+", " ")
                .trim();
        
        return processed;
    }

    /**
     * 执行搜索
     */
    private List<SearchResult> executeSearch(UUID projectId, String query, String sourceType, 
                                              int limit, String queryType) {
        String sql = buildSearchQuery(queryType);
        Object[] params = buildQueryParams(query, projectId, sourceType, limit, queryType);
        
        return jdbcTemplate.query(sql, params, new FullTextSearchResultRowMapper(objectMapper));
    }

    /**
     * 构建搜索SQL
     * 
     * 使用 getEffectiveLanguage() 获取有效的语言配置，
     * 确保 plainto_tsquery, phraseto_tsquery, to_tsquery 都使用正确的配置
     * 
     * Requirements: 4.2, 5.1, 5.2
     */
    private String buildSearchQuery(String queryType) {
        String lang = getEffectiveLanguage();
        String titleWeight = getTitleWeight();
        String contentWeight = getContentWeight();
        
        log.debug("Building search query with language: {}, queryType: {}", lang, queryType);

        return switch (queryType) {
            case "phrase" -> buildPhraseSearchQuery(lang);
            case "boolean" -> buildBooleanSearchQuery(lang);
            case "exact" -> buildExactSearchQuery(lang);
            case "mixed" -> buildMixedLanguageSearchQuery(lang, titleWeight, contentWeight);
            default -> buildWeightedFullTextQuery(lang, titleWeight, contentWeight);
        };
    }

    /**
     * 构建加权全文搜索查询
     * 使用ts_rank_cd进行排名，标题权重A，内容权重B
     *
     */
    private String buildWeightedFullTextQuery(String lang, String titleWeight, String contentWeight) {
        return String.format("""
            SELECT 
                id, source_type, source_id, content, metadata, chunk_level, parent_id,
                ts_rank_cd(
                    setweight(to_tsvector('%s', COALESCE(metadata->>'title', '')), '%s') ||
                    setweight(to_tsvector('%s', content), '%s'),
                    plainto_tsquery('%s', ?)
                ) as fulltext_score
            FROM knowledge_chunks 
            WHERE project_id = ?
              AND is_active = true
              AND is_dirty = false
              AND (? IS NULL OR source_type = ?)
              AND (
                setweight(to_tsvector('%s', COALESCE(metadata->>'title', '')), '%s') ||
                setweight(to_tsvector('%s', content), '%s')
              ) @@ plainto_tsquery('%s', ?)
            ORDER BY fulltext_score DESC
            LIMIT ?
            """, lang, titleWeight, lang, contentWeight, lang, 
                 lang, titleWeight, lang, contentWeight, lang);
    }

    /**
     * 构建短语搜索查询
     */
    private String buildPhraseSearchQuery(String lang) {
        return String.format("""
            SELECT 
                id, source_type, source_id, content, metadata, chunk_level, parent_id,
                ts_rank_cd(
                    to_tsvector('%s', content),
                    phraseto_tsquery('%s', ?)
                ) as fulltext_score
            FROM knowledge_chunks 
            WHERE project_id = ?
              AND is_active = true
              AND is_dirty = false
              AND (? IS NULL OR source_type = ?)
              AND to_tsvector('%s', content) @@ phraseto_tsquery('%s', ?)
            ORDER BY fulltext_score DESC
            LIMIT ?
            """, lang, lang, lang, lang);
    }

    /**
     * 构建布尔搜索查询
     */
    private String buildBooleanSearchQuery(String lang) {
        return String.format("""
            SELECT 
                id, source_type, source_id, content, metadata, chunk_level, parent_id,
                ts_rank_cd(
                    to_tsvector('%s', content),
                    to_tsquery('%s', ?)
                ) as fulltext_score
            FROM knowledge_chunks 
            WHERE project_id = ?
              AND is_active = true
              AND is_dirty = false
              AND (? IS NULL OR source_type = ?)
              AND to_tsvector('%s', content) @@ to_tsquery('%s', ?)
            ORDER BY fulltext_score DESC
            LIMIT ?
            """, lang, lang, lang, lang);
    }

    /**
     * 构建精确搜索查询
     */
    private String buildExactSearchQuery(String lang) {
        return String.format("""
            SELECT 
                id, source_type, source_id, content, metadata, chunk_level, parent_id,
                ts_rank_cd(
                    to_tsvector('%s', content),
                    phraseto_tsquery('%s', ?)
                ) as fulltext_score
            FROM knowledge_chunks 
            WHERE project_id = ?
              AND is_active = true
              AND is_dirty = false
              AND (? IS NULL OR source_type = ?)
              AND to_tsvector('%s', content) @@ phraseto_tsquery('%s', ?)
              AND (
                content ILIKE '%%%%' || ? || '%%%%' OR 
                metadata->>'title' ILIKE '%%%%' || ? || '%%%%'
              )
            ORDER BY fulltext_score DESC
            LIMIT ?
            """, lang, lang, lang, lang);
    }
    
    /**
     * 构建混合语言搜索查询
     * 
     * 对于中英混合查询，使用 plainto_tsquery 进行全文搜索，
     * 同时结合 ILIKE 进行英文部分的模糊匹配，以确保英文词汇能被正确匹配。
     * 
     * 策略：
     * 1. 使用 chinese 配置的 plainto_tsquery 处理中文分词
     * 2. 使用 ILIKE 确保英文部分也能被匹配（因为 zhparser 可能不会正确处理英文）
     * 3. 综合两种匹配方式的结果
     * 
     * Requirements: 4.2
     * 
     * @param lang 语言配置
     * @param titleWeight 标题权重
     * @param contentWeight 内容权重
     * @return SQL 查询字符串
     */
    private String buildMixedLanguageSearchQuery(String lang, String titleWeight, String contentWeight) {
        return String.format("""
            SELECT 
                id, source_type, source_id, content, metadata, chunk_level, parent_id,
                ts_rank_cd(
                    setweight(to_tsvector('%s', COALESCE(metadata->>'title', '')), '%s') ||
                    setweight(to_tsvector('%s', content), '%s'),
                    plainto_tsquery('%s', ?)
                ) as fulltext_score
            FROM knowledge_chunks 
            WHERE project_id = ?
              AND is_active = true
              AND is_dirty = false
              AND (? IS NULL OR source_type = ?)
              AND (
                -- 全文搜索匹配（处理中文分词）
                (
                    setweight(to_tsvector('%s', COALESCE(metadata->>'title', '')), '%s') ||
                    setweight(to_tsvector('%s', content), '%s')
                ) @@ plainto_tsquery('%s', ?)
                OR
                -- ILIKE 模糊匹配（确保英文部分也能匹配）
                content ILIKE '%%%%' || ? || '%%%%'
                OR
                metadata->>'title' ILIKE '%%%%' || ? || '%%%%'
              )
            ORDER BY fulltext_score DESC
            LIMIT ?
            """, lang, titleWeight, lang, contentWeight, lang,
                 lang, titleWeight, lang, contentWeight, lang);
    }

    /**
     * 构建查询参数
     * 
     * Requirements: 4.2
     */
    private Object[] buildQueryParams(String query, UUID projectId, String sourceType, 
                                       int limit, String queryType) {
        return switch (queryType) {
            case "exact" -> new Object[]{query, projectId, sourceType, sourceType, query, query, query, limit};
            case "mixed" -> {
                // 混合语言查询需要额外的参数用于 ILIKE 匹配
                // 提取英文部分用于 ILIKE 匹配
                String englishPart = extractEnglishPart(query);
                yield new Object[]{query, projectId, sourceType, sourceType, query, englishPart, englishPart, limit};
            }
            default -> new Object[]{query, projectId, sourceType, sourceType, query, limit};
        };
    }
    
    /**
     * 从混合语言查询中提取英文部分
     * 
     * Requirements: 4.2
     * 
     * @param query 混合语言查询
     * @return 提取的英文部分，如果没有则返回原查询
     */
    private String extractEnglishPart(String query) {
        if (query == null || query.isEmpty()) {
            return query;
        }
        
        StringBuilder englishParts = new StringBuilder();
        java.util.regex.Matcher matcher = ENGLISH_PATTERN.matcher(query);
        
        while (matcher.find()) {
            if (englishParts.length() > 0) {
                englishParts.append(" ");
            }
            englishParts.append(matcher.group());
        }
        
        // 如果没有提取到英文部分，返回原查询
        return englishParts.length() > 0 ? englishParts.toString() : query;
    }

    /**
     * 获取有效的搜索语言配置
     * 
     * 根据配置和 zhparser 可用性返回实际使用的语言：
     * - 如果配置为 "chinese" 且 zhparser 可用，返回 "chinese"
     * - 如果配置为 "chinese" 但 zhparser 不可用，降级返回 "simple"
     * - 其他配置直接返回配置值（需在白名单中）
     * 
     * 使用白名单防止SQL注入
     * 
     * Requirements: 1.4, 5.1
     * 
     * @return 有效的语言配置字符串
     */
    private String getEffectiveLanguage() {
        String configuredLang = ragProperties.fullText().language();
        
        // 处理 chinese 配置 - 需要检查 zhparser 可用性
        if ("chinese".equalsIgnoreCase(configuredLang)) {
            if (zhparserHealthChecker.isZhparserAvailable() && 
                zhparserHealthChecker.isChineseConfigAvailable()) {
                return "chinese";
            }
            log.warn("Chinese language configured but zhparser unavailable, using simple");
            return "simple";
        }
        
        // 其他语言配置 - 验证白名单
        if (configuredLang != null && SUPPORTED_LANGUAGES.contains(configuredLang.toLowerCase())) {
            return configuredLang.toLowerCase();
        }
        
        return "simple";
    }
    
    /**
     * 获取搜索语言配置（已废弃，请使用 getEffectiveLanguage）
     * 保留此方法以保持向后兼容
     * 
     * @deprecated 使用 {@link #getEffectiveLanguage()} 替代
     */
    @Deprecated(since = "2.0", forRemoval = true)
    private String getSearchLanguage() {
        return getEffectiveLanguage();
    }

    /**
     * 获取标题权重
     */
    private String getTitleWeight() {
        String weight = ragProperties.fullText().titleWeight();
        if (weight != null && weight.matches("[A-D]")) {
            return weight.toUpperCase();
        }
        return "A";
    }

    /**
     * 获取内容权重
     */
    private String getContentWeight() {
        String weight = ragProperties.fullText().contentWeight();
        if (weight != null && weight.matches("[A-D]")) {
            return weight.toUpperCase();
        }
        return "B";
    }

    /**
     * 结果行映射器
     */
    @RequiredArgsConstructor
    private static class FullTextSearchResultRowMapper implements RowMapper<SearchResult> {
        private final ObjectMapper objectMapper;

        @Override
        public SearchResult mapRow(ResultSet rs, int rowNum) throws SQLException {
            return SearchResult.builder()
                    .id(UUID.fromString(rs.getString("id")))
                    .sourceType(rs.getString("source_type"))
                    .sourceId(UUID.fromString(rs.getString("source_id")))
                    .content(rs.getString("content"))
                    .metadata(parseMetadata(rs.getString("metadata")))
                    .chunkLevel(rs.getString("chunk_level"))
                    .parentId(parseUUID(rs.getString("parent_id")))
                    .fullTextScore(rs.getDouble("fulltext_score"))
                    .build();
        }

        private Map<String, Object> parseMetadata(String json) {
            if (json == null || json.isBlank()) {
                return new HashMap<>();
            }
            try {
                return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                return new HashMap<>();
            }
        }

        private UUID parseUUID(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return UUID.fromString(value);
            } catch (Exception e) {
                return null;
            }
        }
    }
}
