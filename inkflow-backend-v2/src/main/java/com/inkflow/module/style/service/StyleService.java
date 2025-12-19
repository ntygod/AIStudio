package com.inkflow.module.style.service;

import com.inkflow.module.rag.service.EmbeddingService;
import com.inkflow.module.style.dto.StyleStats;
import com.inkflow.module.style.entity.StyleSample;
import com.inkflow.module.style.repository.StyleSampleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

/**
 * 风格学习服务
 * 分析用户对 AI 生成内容的修改，学习用户写作风格
 */
@Service
@Transactional(readOnly = true)
public class StyleService {

    private static final Logger log = LoggerFactory.getLogger(StyleService.class);

    private final StyleSampleRepository styleSampleRepository;
    private final EmbeddingService embeddingService;

    private static final double MIN_EDIT_RATIO = 0.3;
    private static final int MIN_TEXT_LENGTH = 100;
    private static final int NGRAM_SIZE = 3;

    public StyleService(StyleSampleRepository styleSampleRepository, EmbeddingService embeddingService) {
        this.styleSampleRepository = styleSampleRepository;
        this.embeddingService = embeddingService;
    }

    /**
     * 计算编辑比例（使用 n-gram 算法）
     */
    public double calculateEditRatio(String original, String modified) {
        if (original == null || modified == null) {
            return 0.0;
        }
        if (original.equals(modified)) {
            return 0.0;
        }

        Set<String> originalNgrams = generateNgrams(original, NGRAM_SIZE);
        Set<String> modifiedNgrams = generateNgrams(modified, NGRAM_SIZE);

        if (originalNgrams.isEmpty() && modifiedNgrams.isEmpty()) {
            return 0.0;
        }

        Set<String> intersection = new HashSet<>(originalNgrams);
        intersection.retainAll(modifiedNgrams);

        Set<String> union = new HashSet<>(originalNgrams);
        union.addAll(modifiedNgrams);

        double jaccardSimilarity = union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
        return 1.0 - jaccardSimilarity;
    }

    /**
     * 判断是否应保存为风格样本
     */
    public boolean shouldSaveAsStyleSample(String original, String modified) {
        if (original == null || modified == null) {
            return false;
        }
        if (original.length() < MIN_TEXT_LENGTH || modified.length() < MIN_TEXT_LENGTH) {
            return false;
        }
        double editRatio = calculateEditRatio(original, modified);
        return editRatio >= MIN_EDIT_RATIO;
    }

    /**
     * 保存风格样本
     */
    @Transactional
    public Mono<StyleSample> saveStyleSample(UUID projectId, UUID chapterId, String originalAI, String userFinal) {
        if (!shouldSaveAsStyleSample(originalAI, userFinal)) {
            log.debug("风格样本不满足保存条件: chapterId={}", chapterId);
            return Mono.empty();
        }

        double editRatio = calculateEditRatio(originalAI, userFinal);
        int wordCount = userFinal.length();

        return embeddingService.generateEmbedding(userFinal)
            .publishOn(Schedulers.boundedElastic())
            .map(vector -> {
                StyleSample sample = StyleSample.builder()
                    .projectId(projectId)
                    .chapterId(chapterId)
                    .originalAI(originalAI)
                    .userFinal(userFinal)
                    .editRatio(editRatio)
                    .vector(vector)
                    .wordCount(wordCount)
                    .build();

                StyleSample saved = styleSampleRepository.save(sample);
                log.info("保存风格样本: id={}, editRatio={}, wordCount={}", saved.getId(), editRatio, wordCount);
                return saved;
            })
            .doOnError(e -> log.error("保存风格样本失败: chapterId={}, error={}", chapterId, e.getMessage()));
    }

    /**
     * 检索相似风格样本
     */
    public Mono<List<StyleSample>> retrieveSimilarStyleSamples(UUID projectId, String context, int limit) {
        if (context == null || context.isBlank()) {
            return Mono.just(Collections.emptyList());
        }

        return embeddingService.generateEmbedding(context)
            .publishOn(Schedulers.boundedElastic())
            .map(queryVector -> styleSampleRepository.findSimilarByProjectId(
                projectId, Arrays.toString(queryVector), limit))
            .doOnSuccess(samples -> log.debug("检索到 {} 个相似风格样本", samples.size()))
            .doOnError(e -> log.error("检索风格样本失败: projectId={}, error={}", projectId, e.getMessage()));
    }

    /**
     * 构建风格提示词片段
     */
    public String buildStylePromptSection(List<StyleSample> samples) {
        if (samples == null || samples.isEmpty()) {
            return "";
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("【用户写作风格参考】\n");
        prompt.append("以下是用户之前对 AI 生成内容的修改示例，请学习用户的写作风格：\n\n");

        for (int i = 0; i < samples.size(); i++) {
            StyleSample sample = samples.get(i);
            prompt.append(String.format("示例 %d (编辑比例: %.2f):\n", i + 1, sample.getEditRatio()));
            prompt.append("AI 原文: ").append(truncate(sample.getOriginalAI(), 200)).append("\n");
            prompt.append("用户修改: ").append(truncate(sample.getUserFinal(), 200)).append("\n\n");
        }

        prompt.append("请参考以上示例，模仿用户的写作风格、用词习惯和表达方式。\n");
        return prompt.toString();
    }

    /**
     * 获取风格学习统计
     */
    public StyleStats getStyleStats(UUID projectId) {
        long sampleCount = styleSampleRepository.countByProjectId(projectId);
        Double avgEditRatio = styleSampleRepository.getAverageEditRatio(projectId);
        Long totalWordCount = styleSampleRepository.getTotalWordCount(projectId);

        return StyleStats.builder()
            .sampleCount(sampleCount)
            .averageEditRatio(avgEditRatio != null ? avgEditRatio : 0.0)
            .totalWordCount(totalWordCount != null ? totalWordCount : 0L)
            .build();
    }

    /**
     * 获取项目的所有风格样本
     */
    public List<StyleSample> getStyleSamples(UUID projectId) {
        return styleSampleRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    /**
     * 删除风格样本
     */
    @Transactional
    public void deleteStyleSample(UUID id) {
        styleSampleRepository.deleteById(id);
        log.info("删除风格样本: id={}", id);
    }

    // ==================== 辅助方法 ====================

    private Set<String> generateNgrams(String text, int n) {
        if (text == null || text.length() < n) {
            return Collections.emptySet();
        }
        Set<String> ngrams = new HashSet<>();
        for (int i = 0; i <= text.length() - n; i++) {
            ngrams.add(text.substring(i, i + n));
        }
        return ngrams;
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
