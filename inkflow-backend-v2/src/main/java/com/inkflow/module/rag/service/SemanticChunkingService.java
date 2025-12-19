package com.inkflow.module.rag.service;

import com.inkflow.module.rag.config.RagProperties;
import com.inkflow.module.rag.dto.ChildChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 语义分块服务
 * 实现基于语义断崖检测的智能分块，保持语义边界完整性。
 * 核心功能：
 * 1. 句子拆分（保护引号内容）
 * 2. 语义断崖检测（基于相邻句子相似度）
 * 3. 智能合并（尊重断崖位置）

 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Service
public class SemanticChunkingService {

    private final RerankerService rerankerService;
    private final EmbeddingService embeddingService;
    private final RagProperties.ChunkingConfig config;

    // 中文引号模式（用于保护引号内容）
    // 匹配: "" 「」 『』
    private static final Pattern CHINESE_QUOTE_PATTERN = Pattern.compile(
        "[\u201c\u300c\u300e]([^\u201d\u300d\u300f]*?)[\u201d\u300d\u300f]"
    );
    
    // 英文引号模式
    private static final Pattern ENGLISH_QUOTE_PATTERN = Pattern.compile(
        "\"([^\"]*?)\""
    );
    
    // 句子结束标点（中英文）
    // 匹配句号、感叹号、问号，但不匹配引号内的
    private static final Pattern SENTENCE_END_PATTERN = Pattern.compile(
        "([\u3002\uff01\uff1f.!?]+)(?![^\u201c\u300c\u300e\"]*[\u201d\u300d\u300f\"])"
    );

    public SemanticChunkingService(
            RerankerService rerankerService,
            EmbeddingService embeddingService,
            RagProperties ragProperties) {
        this.rerankerService = rerankerService;
        this.embeddingService = embeddingService;
        this.config = ragProperties.chunking();
    }

    // ==================== 公共API ====================

    /**
     * 将文本切分为语义子块
     *
     * @param content 原始文本内容
     * @return 子块列表
     */
    public Mono<List<ChildChunk>> splitIntoChildChunks(String content) {
        if (content == null || content.trim().isEmpty()) {
            return Mono.just(Collections.emptyList());
        }

        // 1. 使用带引号保护的句子拆分
        List<String> sentences = splitAtSentenceBoundaries(content);

        if (sentences.isEmpty()) {
            return Mono.just(Collections.emptyList());
        }

        if (sentences.size() == 1) {
            return Mono.just(List.of(createChildChunk(content.trim(), 0, 0, content.length())));
        }

        // 2. 计算相邻句子相似度
        return calculateAdjacentSimilarities(sentences)
                .map(similarities -> {
                    // 3. 检测语义断崖
                    List<Integer> cliffPositions = detectSemanticCliffs(similarities);
                    // 4. 合并句子为子块
                    return mergeSentencesIntoChunks(sentences, cliffPositions, content);
                })
                .onErrorResume(e -> {
                    log.warn("Semantic split failed, fallback to simple chunking: {}", e.getMessage());
                    return Mono.just(simpleChunking(content));
                });
    }

    /**
     * 对文本进行语义分块（返回字符串列表）
     * 兼容旧API
     *
     * @param text 原始文本
     * @return 分块内容列表
     */
    public Mono<List<String>> chunkText(String text) {
        return splitIntoChildChunks(text)
                .map(chunks -> chunks.stream()
                        .map(ChildChunk::getContent)
                        .collect(Collectors.toList()));
    }

    /**
     * 简单分块（不使用语义检测）
     * 用于快速处理或降级场景
     *
     * @param text 原始文本
     * @param chunkSize 块大小
     * @param overlap 重叠大小
     * @return 分块列表
     */
    public List<String> simpleChunk(String text, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());

            // 尝试在句子边界处截断
            if (end < text.length()) {
                int sentenceEnd = findSentenceEnd(text, start, end);
                if (sentenceEnd > start) {
                    end = sentenceEnd;
                }
            }

            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            start = end - overlap;
            if (start < 0) start = 0;
            if (start >= text.length()) break;
        }

        return chunks;
    }


    // ==================== 句子拆分 ====================

    /**
     * 在句子边界处拆分文本，保护引号内容
     *
     * @param text 原始文本
     * @return 句子列表
     */
    private List<String> splitAtSentenceBoundaries(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 保护引号内容（用占位符替换）
        Map<String, String> quotePlaceholders = new LinkedHashMap<>();
        String protectedText = protectQuotedContent(text, quotePlaceholders);

        // 2. 在句子边界处拆分
        List<String> sentences = splitBySentenceEndings(protectedText);

        // 3. 恢复引号内容
        return sentences.stream()
                .map(sentence -> restoreQuotedContent(sentence, quotePlaceholders))
                .filter(s -> !s.trim().isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 保护引号内容，用占位符替换
     */
    private String protectQuotedContent(String text, Map<String, String> placeholders) {
        String result = text;
        int counter = 0;

        // 保护中文引号
        Matcher chineseMatcher = CHINESE_QUOTE_PATTERN.matcher(result);
        StringBuffer sb1 = new StringBuffer();
        while (chineseMatcher.find()) {
            String placeholder = "§QUOTE_" + counter++ + "§";
            placeholders.put(placeholder, chineseMatcher.group());
            chineseMatcher.appendReplacement(sb1, Matcher.quoteReplacement(placeholder));
        }
        chineseMatcher.appendTail(sb1);
        result = sb1.toString();

        // 保护英文引号
        Matcher englishMatcher = ENGLISH_QUOTE_PATTERN.matcher(result);
        StringBuffer sb2 = new StringBuffer();
        while (englishMatcher.find()) {
            String placeholder = "§QUOTE_" + counter++ + "§";
            placeholders.put(placeholder, englishMatcher.group());
            englishMatcher.appendReplacement(sb2, Matcher.quoteReplacement(placeholder));
        }
        englishMatcher.appendTail(sb2);
        result = sb2.toString();

        return result;
    }

    /**
     * 恢复引号内容
     */
    private String restoreQuotedContent(String text, Map<String, String> placeholders) {
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * 按句子结束标点拆分
     */
    private List<String> splitBySentenceEndings(String text) {
        List<String> sentences = new ArrayList<>();
        
        // 使用正则匹配句子结束位置
        Matcher matcher = SENTENCE_END_PATTERN.matcher(text);
        int lastEnd = 0;
        
        while (matcher.find()) {
            int sentenceEnd = matcher.end();
            String sentence = text.substring(lastEnd, sentenceEnd).trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
            lastEnd = sentenceEnd;
        }
        
        // 处理最后一段（可能没有句号结尾）
        if (lastEnd < text.length()) {
            String remaining = text.substring(lastEnd).trim();
            if (!remaining.isEmpty()) {
                sentences.add(remaining);
            }
        }
        
        // 如果没有找到任何句子边界，返回整个文本
        if (sentences.isEmpty() && !text.trim().isEmpty()) {
            sentences.add(text.trim());
        }
        
        return sentences;
    }

    // ==================== 相似度计算 ====================

    /**
     * 计算相邻句子的相似度
     *
     * @param sentences 句子列表
     * @return 相似度列表（长度 = sentences.size() - 1）
     */
    private Mono<List<Double>> calculateAdjacentSimilarities(List<String> sentences) {
        if (sentences == null || sentences.size() < 2) {
            return Mono.just(Collections.emptyList());
        }

        // 优先使用Reranker计算相似度（如果配置启用）
        if (config.useReranker() && rerankerService.isServiceAvailable()) {
            return rerankerService.calculateAdjacentSimilarities(sentences)
                    .onErrorResume(e -> {
                        log.warn("Reranker similarity failed, falling back to embedding: {}", e.getMessage());
                        return calculateSimilaritiesWithEmbedding(sentences);
                    });
        }

        // 使用Embedding计算相似度
        return calculateSimilaritiesWithEmbedding(sentences);
    }

    /**
     * 使用Embedding计算相邻句子相似度
     */
    private Mono<List<Double>> calculateSimilaritiesWithEmbedding(List<String> sentences) {
        return embeddingService.generateEmbeddingsBatch(sentences)
                .map(embeddings -> {
                    List<Double> similarities = new ArrayList<>();
                    for (int i = 0; i < embeddings.size() - 1; i++) {
                        double similarity = cosineSimilarity(embeddings.get(i), embeddings.get(i + 1));
                        similarities.add(similarity);
                    }
                    return similarities;
                })
                .onErrorReturn(Collections.emptyList());
    }

    /**
     * 计算余弦相似度
     */
    private double cosineSimilarity(float[] v1, float[] v2) {
        if (v1 == null || v2 == null || v1.length == 0 || v2.length == 0 || v1.length != v2.length) {
            return 0.0;
        }
        
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dot += v1[i] * v2[i];
            normA += v1[i] * v1[i];
            normB += v2[i] * v2[i];
        }
        
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // ==================== 语义断崖检测 ====================

    /**
     * 检测语义断崖位置
     * 阈值：相似度低于第20百分位的位置标记为断崖
     *
     * @param similarities 相邻句子相似度列表
     * @return 断崖位置列表（索引从1开始，表示在第i个句子后断开）
     */
    private List<Integer> detectSemanticCliffs(List<Double> similarities) {
        List<Integer> cliffs = new ArrayList<>();
        
        if (similarities == null || similarities.isEmpty()) {
            return cliffs;
        }

        // 计算动态阈值：基于第20百分位
        double threshold = calculatePercentileThreshold(similarities, config.cliffThreshold());

        for (int i = 0; i < similarities.size(); i++) {
            if (similarities.get(i) <= threshold) {
                // 断崖位置 = i + 1（表示在第i个句子后断开）
                cliffs.add(i + 1);
            }
        }

        log.debug("Detected {} semantic cliffs with threshold {}", cliffs.size(), threshold);
        return cliffs;
    }

    /**
     * 计算百分位阈值
     * 
     * @param values 值列表
     * @param percentile 百分位（0-1）
     * @return 阈值
     */
    private double calculatePercentileThreshold(List<Double> values, double percentile) {
        if (values.isEmpty()) {
            return 0.0;
        }
        
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        
        int index = Math.max(0, (int) (sorted.size() * percentile) - 1);
        return sorted.get(index);
    }


    // ==================== 块合并 ====================

    /**
     * 检测文本是否包含中文字符
     * 用于判断句子连接时是否需要添加空格
     *
     * @param text 待检测文本
     * @return 如果包含中文字符返回true
     */
    private boolean containsChinese(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        // 匹配中文字符范围：CJK统一汉字
        return text.matches(".*[\\u4e00-\\u9fa5].*");
    }

    /**
     * 判断两个句子之间是否需要添加空格
     * 规则：
     * - 如果前一个句子以中文结尾，不添加空格
     * - 如果后一个句子以中文开头，不添加空格
     * - 其他情况（英文句子之间）添加空格
     *
     * @param previousSentence 前一个句子
     * @param nextSentence 下一个句子
     * @return 如果需要添加空格返回true
     */
    private boolean needsSpaceBetween(String previousSentence, String nextSentence) {
        if (previousSentence == null || previousSentence.isEmpty() ||
            nextSentence == null || nextSentence.isEmpty()) {
            return false;
        }
        
        // 获取前一个句子的最后一个字符
        char lastChar = previousSentence.charAt(previousSentence.length() - 1);
        // 获取下一个句子的第一个字符
        char firstChar = nextSentence.charAt(0);
        
        // 如果前一个句子以中文字符或中文标点结尾，不需要空格
        if (isCjkCharacter(lastChar)) {
            return false;
        }
        
        // 如果下一个句子以中文字符开头，不需要空格
        if (isCjkCharacter(firstChar)) {
            return false;
        }
        
        // 英文句子之间需要空格
        return true;
    }

    /**
     * 判断字符是否为CJK字符（中日韩统一表意文字或中文标点）
     *
     * @param c 待检测字符
     * @return 如果是CJK字符返回true
     */
    private boolean isCjkCharacter(char c) {
        // CJK统一汉字范围
        if (c >= '\u4e00' && c <= '\u9fa5') {
            return true;
        }
        // 中文标点符号
        if (c >= '\u3000' && c <= '\u303f') {
            return true;
        }
        // 全角标点符号
        if (c >= '\uff00' && c <= '\uffef') {
            return true;
        }
        return false;
    }

    /**
     * 将句子合并为子块，尊重断崖位置
     *
     * @param sentences 句子列表
     * @param cliffs 断崖位置列表
     * @param originalContent 原始内容（用于计算位置）
     * @return 子块列表
     */
    private List<ChildChunk> mergeSentencesIntoChunks(
            List<String> sentences, 
            List<Integer> cliffs, 
            String originalContent) {
        
        List<ChildChunk> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        int chunkOrder = 0;
        int startPos = 0;
        Set<Integer> cliffSet = new HashSet<>(cliffs);
        String previousSentence = null;

        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            
            // 根据前后句子内容判断是否需要添加空格
            if (buffer.length() > 0 && previousSentence != null) {
                if (needsSpaceBetween(previousSentence, sentence)) {
                    buffer.append(" ");
                }
            }
            
            buffer.append(sentence);
            previousSentence = sentence;
            
            // 判断是否需要切分
            boolean isCliff = cliffSet.contains(i + 1);
            boolean isTooBig = buffer.length() > config.maxChildSize();
            boolean isBigEnough = buffer.length() >= config.minChildSize();
            boolean isLastSentence = (i == sentences.size() - 1);

            // 切分条件：
            // 1. 遇到断崖且当前块足够大
            // 2. 当前块超过最大大小（强制切分）
            // 3. 最后一个句子
            if ((isCliff && isBigEnough) || isTooBig || isLastSentence) {
                String content = buffer.toString().trim();
                if (!content.isEmpty()) {
                    int endPos = findEndPosition(originalContent, startPos, content);
                    chunks.add(createChildChunk(content, chunkOrder++, startPos, endPos));
                    startPos = endPos;
                }
                buffer = new StringBuilder();
                previousSentence = null;
            }
        }

        // 处理剩余内容
        if (buffer.length() > 0) {
            String content = buffer.toString().trim();
            if (!content.isEmpty()) {
                chunks.add(createChildChunk(content, chunkOrder, startPos, originalContent.length()));
            }
        }

        return chunks;
    }

    /**
     * 查找内容在原文中的结束位置
     */
    private int findEndPosition(String original, int startPos, String content) {
        // 简单估算：起始位置 + 内容长度
        int estimatedEnd = startPos + content.length();
        return Math.min(estimatedEnd, original.length());
    }

    /**
     * 创建子块
     */
    private ChildChunk createChildChunk(String content, int order, int start, int end) {
        return ChildChunk.builder()
                .content(content)
                .order(order)
                .startPosition(start)
                .endPosition(end)
                .build();
    }

    // ==================== 降级处理 ====================

    /**
     * 简单分块降级方案
     */
    private List<ChildChunk> simpleChunking(String content) {
        List<ChildChunk> chunks = new ArrayList<>();
        List<String> textChunks = simpleChunk(content, config.maxChildSize(), config.contextOverlapSize());
        
        int startPos = 0;
        for (int i = 0; i < textChunks.size(); i++) {
            String chunkContent = textChunks.get(i);
            int endPos = Math.min(startPos + chunkContent.length(), content.length());
            chunks.add(createChildChunk(chunkContent, i, startPos, endPos));
            startPos = endPos;
        }
        
        return chunks;
    }

    /**
     * 查找句子结束位置
     */
    private int findSentenceEnd(String text, int start, int maxEnd) {
        int lastEnd = start;
        for (int i = start; i < maxEnd && i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?') {
                lastEnd = i + 1;
            }
        }
        return lastEnd > start ? lastEnd : maxEnd;
    }

    // ==================== 配置访问 ====================

    /**
     * 获取最大子块大小
     */
    public int getMaxChildSize() {
        return config.maxChildSize();
    }
}
