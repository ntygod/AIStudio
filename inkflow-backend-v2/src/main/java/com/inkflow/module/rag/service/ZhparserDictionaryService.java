package com.inkflow.module.rag.service;

import com.inkflow.module.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * zhparser 词典服务
 * 
 * 提供 zhparser 分词测试和自定义词典管理功能。
 * 用于测试分词效果和加载小说特定术语（角色名、地名、武功招式等）。
 * 
 * 功能：
 * - testSegmentation(): 测试分词效果，返回分词结果列表
 * - loadCustomDictionary(): 加载自定义词典（预留接口）
 * - validateDictionaryEntry(): 验证词典条目是否有效
 * 
 * @author zsg
 * @date 2025/12/18
 * @see RagProperties.ZhparserConfig
 * 
 * Requirements: 2.3, 2.4
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZhparserDictionaryService {

    private final JdbcTemplate jdbcTemplate;
    private final RagProperties ragProperties;
    private final ZhparserHealthChecker healthChecker;

    /**
     * 测试分词效果
     * 
     * 使用 ts_debug 函数分析文本的分词结果，返回所有被识别的词素。
     * 仅当 zhparser 可用时才使用 chinese 配置，否则使用 simple 配置。
     * 
     * @param text 要测试分词的文本
     * @return 分词结果列表，每个元素是一个被识别的词素
     */
    public List<String> testSegmentation(String text) {
        if (text == null || text.isBlank()) {
            log.debug("Empty text provided for segmentation test");
            return Collections.emptyList();
        }

        String effectiveLanguage = healthChecker.getEffectiveLanguage();
        
        try {
            // 使用 ts_debug 获取详细的分词信息
            // alias 不为空表示该词素被词典识别
            String sql = """
                SELECT token FROM ts_debug(?, ?)
                WHERE alias IS NOT NULL
                """;
            
            List<String> tokens = jdbcTemplate.queryForList(sql, String.class, effectiveLanguage, text);
            
            log.debug("Segmentation test for '{}' using '{}' config: {} tokens found", 
                    truncateForLog(text), effectiveLanguage, tokens.size());
            
            return tokens;
            
        } catch (Exception e) {
            log.warn("Segmentation test failed for text '{}': {}", 
                    truncateForLog(text), e.getMessage());
            return Collections.emptyList();
        }
    }


    /**
     * 测试分词效果（带详细信息）
     * 
     * 返回更详细的分词信息，包括词素、词性和词典别名。
     * 
     * @param text 要测试分词的文本
     * @return 分词详细结果列表
     */
    public List<SegmentationDetail> testSegmentationWithDetails(String text) {
        if (text == null || text.isBlank()) {
            log.debug("Empty text provided for detailed segmentation test");
            return Collections.emptyList();
        }

        String effectiveLanguage = healthChecker.getEffectiveLanguage();
        
        try {
            String sql = """
                SELECT token, alias, description, lexemes
                FROM ts_debug(?, ?)
                WHERE alias IS NOT NULL
                """;
            
            List<SegmentationDetail> details = jdbcTemplate.query(sql, (rs, rowNum) -> 
                new SegmentationDetail(
                    rs.getString("token"),
                    rs.getString("alias"),
                    rs.getString("description"),
                    rs.getString("lexemes")
                ), effectiveLanguage, text);
            
            log.debug("Detailed segmentation test for '{}' using '{}' config: {} tokens found", 
                    truncateForLog(text), effectiveLanguage, details.size());
            
            return details;
            
        } catch (Exception e) {
            log.warn("Detailed segmentation test failed for text '{}': {}", 
                    truncateForLog(text), e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 加载自定义词典
     * 
     * 预留接口，用于加载小说特定术语的自定义词典。
     * zhparser 使用 SCWS 词典，需要通过文件系统加载。
     * 此方法提供 SQL 级别的词典验证方案。
     * 
     * 词典格式: 词语\t词频\t词性
     * 例如: 林动\t100\tn
     * 
     * 注意：实际的词典加载需要在 PostgreSQL 服务器端配置 SCWS 词典文件。
     * 此方法主要用于验证词条是否能被正确识别。
     * 
     * @param entries 词典条目列表
     * @return 成功加载的条目数量
     */
    public int loadCustomDictionary(List<DictionaryEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            log.debug("No dictionary entries provided");
            return 0;
        }

        if (!healthChecker.isZhparserAvailable()) {
            log.warn("Cannot load custom dictionary: zhparser is not available");
            return 0;
        }

        String customDictPath = ragProperties.fullText().zhparser().customDictPath();
        if (customDictPath == null || customDictPath.isBlank()) {
            log.info("Custom dictionary path not configured, validating entries only");
        }

        int successCount = 0;
        
        for (DictionaryEntry entry : entries) {
            try {
                boolean valid = validateDictionaryEntry(entry);
                if (valid) {
                    successCount++;
                    log.debug("Dictionary entry validated: {} (freq={}, pos={})", 
                            entry.word(), entry.frequency(), entry.pos());
                } else {
                    log.warn("Dictionary entry validation failed: {}", entry.word());
                }
            } catch (Exception e) {
                log.warn("Failed to validate dictionary entry '{}': {}", 
                        entry.word(), e.getMessage());
            }
        }

        log.info("Custom dictionary loading completed: {}/{} entries validated", 
                successCount, entries.size());
        
        return successCount;
    }

    /**
     * 验证词典条目是否有效
     * 
     * 使用 ts_lexize 函数验证词条是否能被词典识别。
     * 
     * @param entry 词典条目
     * @return true 如果词条有效
     */
    public boolean validateDictionaryEntry(DictionaryEntry entry) {
        if (entry == null || entry.word() == null || entry.word().isBlank()) {
            return false;
        }

        try {
            // 使用 ts_lexize 验证词条
            // 如果返回非空数组，说明词条被识别
            String sql = "SELECT ts_lexize('simple', ?)";
            String result = jdbcTemplate.queryForObject(sql, String.class, entry.word());
            
            // ts_lexize 返回 NULL 表示词条未被识别，返回 {} 或 {word} 表示被识别
            return result != null;
            
        } catch (Exception e) {
            log.debug("Dictionary entry validation error for '{}': {}", 
                    entry.word(), e.getMessage());
            return false;
        }
    }

    /**
     * 获取 tsvector 表示
     * 
     * 将文本转换为 tsvector，用于调试和验证索引效果。
     * 
     * @param text 要转换的文本
     * @return tsvector 字符串表示
     */
    public String getTsvector(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String effectiveLanguage = healthChecker.getEffectiveLanguage();
        
        try {
            String sql = "SELECT to_tsvector(?, ?)::text";
            String result = jdbcTemplate.queryForObject(sql, String.class, effectiveLanguage, text);
            
            log.debug("tsvector for '{}' using '{}': {}", 
                    truncateForLog(text), effectiveLanguage, result);
            
            return result != null ? result : "";
            
        } catch (Exception e) {
            log.warn("Failed to get tsvector for text '{}': {}", 
                    truncateForLog(text), e.getMessage());
            return "";
        }
    }

    /**
     * 获取 tsquery 表示
     * 
     * 将查询文本转换为 tsquery，用于调试和验证查询效果。
     * 
     * @param query 查询文本
     * @return tsquery 字符串表示
     */
    public String getTsquery(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }

        String effectiveLanguage = healthChecker.getEffectiveLanguage();
        
        try {
            String sql = "SELECT plainto_tsquery(?, ?)::text";
            String result = jdbcTemplate.queryForObject(sql, String.class, effectiveLanguage, query);
            
            log.debug("tsquery for '{}' using '{}': {}", 
                    truncateForLog(query), effectiveLanguage, result);
            
            return result != null ? result : "";
            
        } catch (Exception e) {
            log.warn("Failed to get tsquery for query '{}': {}", 
                    truncateForLog(query), e.getMessage());
            return "";
        }
    }

    /**
     * 检查文本是否匹配查询
     * 
     * 使用全文搜索匹配操作符 @@ 检查文本是否匹配查询。
     * 
     * @param text 要搜索的文本
     * @param query 查询文本
     * @return true 如果匹配
     */
    public boolean matches(String text, String query) {
        if (text == null || text.isBlank() || query == null || query.isBlank()) {
            return false;
        }

        String effectiveLanguage = healthChecker.getEffectiveLanguage();
        
        try {
            String sql = "SELECT to_tsvector(?, ?) @@ plainto_tsquery(?, ?)";
            Boolean result = jdbcTemplate.queryForObject(sql, Boolean.class, 
                    effectiveLanguage, text, effectiveLanguage, query);
            
            return Boolean.TRUE.equals(result);
            
        } catch (Exception e) {
            log.warn("Match check failed for text '{}' and query '{}': {}", 
                    truncateForLog(text), truncateForLog(query), e.getMessage());
            return false;
        }
    }

    /**
     * 获取当前 zhparser 配置状态
     * 
     * @return 配置状态信息
     */
    public ZhparserStatus getStatus() {
        return new ZhparserStatus(
            healthChecker.isZhparserAvailable(),
            healthChecker.isChineseConfigAvailable(),
            healthChecker.getEffectiveLanguage(),
            ragProperties.fullText().zhparser().multiShort(),
            ragProperties.fullText().zhparser().multiDuality(),
            ragProperties.fullText().zhparser().punctuationIgnore(),
            ragProperties.fullText().zhparser().customDictPath()
        );
    }

    /**
     * 截断文本用于日志输出
     */
    private String truncateForLog(String text) {
        if (text == null) {
            return "null";
        }
        if (text.length() <= 50) {
            return text;
        }
        return text.substring(0, 47) + "...";
    }

    /**
     * 词典条目记录
     * 
     * @param word 词语
     * @param frequency 词频（影响分词优先级）
     * @param pos 词性（n:名词, v:动词, a:形容词 等）
     */
    public record DictionaryEntry(String word, int frequency, String pos) {
        
        /**
         * 创建名词类型的词典条目
         */
        public static DictionaryEntry noun(String word, int frequency) {
            return new DictionaryEntry(word, frequency, "n");
        }
        
        /**
         * 创建默认词频的名词条目
         */
        public static DictionaryEntry noun(String word) {
            return new DictionaryEntry(word, 100, "n");
        }
    }

    /**
     * 分词详细信息记录
     * 
     * @param token 原始词素
     * @param alias 词典别名
     * @param description 描述
     * @param lexemes 词素列表
     */
    public record SegmentationDetail(
        String token,
        String alias,
        String description,
        String lexemes
    ) {}

    /**
     * zhparser 状态信息记录
     */
    public record ZhparserStatus(
        boolean zhparserAvailable,
        boolean chineseConfigAvailable,
        String effectiveLanguage,
        boolean multiShort,
        boolean multiDuality,
        boolean punctuationIgnore,
        String customDictPath
    ) {}
}
