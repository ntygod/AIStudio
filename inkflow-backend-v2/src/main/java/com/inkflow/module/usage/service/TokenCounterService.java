package com.inkflow.module.usage.service;

import com.inkflow.module.usage.entity.TokenUsageRecord;
import com.inkflow.module.usage.repository.TokenUsageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Token计数服务
 * 记录和统计Token使用量
 */
@Service
public class TokenCounterService {

    private static final Logger log = LoggerFactory.getLogger(TokenCounterService.class);

    // 模型价格配置 (每1000 tokens的价格，单位：美元)
    private static final Map<String, ModelPricing> MODEL_PRICING = Map.of(
            "gpt-4-turbo", new ModelPricing(0.01, 0.03),
            "gpt-4", new ModelPricing(0.03, 0.06),
            "gpt-3.5-turbo", new ModelPricing(0.0005, 0.0015),
            "deepseek-chat", new ModelPricing(0.0001, 0.0002),
            "deepseek-coder", new ModelPricing(0.0001, 0.0002),
            "text-embedding-ada-002", new ModelPricing(0.0001, 0.0),
            "text-embedding-3-small", new ModelPricing(0.00002, 0.0)
    );

    private final TokenUsageRepository repository;

    public TokenCounterService(TokenUsageRepository repository) {
        this.repository = repository;
    }

    /**
     * 记录Token使用
     */
    @Transactional
    public TokenUsageRecord recordUsage(
            UUID userId,
            UUID projectId,
            String modelName,
            String provider,
            int promptTokens,
            int completionTokens,
            String operationType) {

        TokenUsageRecord record = new TokenUsageRecord();
        record.setUserId(userId);
        record.setProjectId(projectId);
        record.setModelName(modelName);
        record.setProvider(provider);
        record.setPromptTokens(promptTokens);
        record.setCompletionTokens(completionTokens);
        record.setTotalTokens(promptTokens + completionTokens);
        record.setOperationType(operationType);
        record.setCost(calculateCost(modelName, promptTokens, completionTokens));

        TokenUsageRecord saved = repository.save(record);
        log.debug("Recorded token usage: {} tokens for user {}", saved.getTotalTokens(), userId);

        return saved;
    }

    /**
     * 获取用户今日使用量
     */
    @Transactional(readOnly = true)
    public UsageSummary getTodayUsage(UUID userId) {
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        Long totalTokens = repository.sumTotalTokensByUserIdSince(userId, startOfDay);
        Double totalCost = repository.sumCostByUserIdSince(userId, startOfDay);

        return new UsageSummary(
                totalTokens != null ? totalTokens : 0L,
                totalCost != null ? totalCost : 0.0,
                startOfDay,
                LocalDateTime.now()
        );
    }

    /**
     * 获取用户本月使用量
     */
    @Transactional(readOnly = true)
    public UsageSummary getMonthlyUsage(UUID userId) {
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).toLocalDate().atStartOfDay();
        Long totalTokens = repository.sumTotalTokensByUserIdSince(userId, startOfMonth);
        Double totalCost = repository.sumCostByUserIdSince(userId, startOfMonth);

        return new UsageSummary(
                totalTokens != null ? totalTokens : 0L,
                totalCost != null ? totalCost : 0.0,
                startOfMonth,
                LocalDateTime.now()
        );
    }

    /**
     * 获取项目总使用量
     */
    @Transactional(readOnly = true)
    public Long getProjectTotalUsage(UUID projectId) {
        Long total = repository.sumTotalTokensByProjectId(projectId);
        return total != null ? total : 0L;
    }

    /**
     * 获取按模型分组的使用统计
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getUsageByModel(UUID userId, int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        List<Object[]> results = repository.sumTokensByModelForUser(userId, startDate);

        Map<String, Long> usageByModel = new HashMap<>();
        for (Object[] row : results) {
            String model = (String) row[0];
            Long tokens = (Long) row[1];
            usageByModel.put(model, tokens);
        }
        return usageByModel;
    }

    /**
     * 获取每日使用趋势
     */
    @Transactional(readOnly = true)
    public List<DailyUsage> getDailyUsageTrend(UUID userId, int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        List<Object[]> results = repository.dailyUsageByUser(userId, startDate);

        return results.stream()
                .map(row -> new DailyUsage(
                        row[0].toString(),
                        ((Number) row[1]).longValue()
                ))
                .toList();
    }

    /**
     * 计算费用
     */
    private double calculateCost(String modelName, int promptTokens, int completionTokens) {
        ModelPricing pricing = MODEL_PRICING.getOrDefault(modelName, new ModelPricing(0.001, 0.002));
        return (promptTokens * pricing.promptPrice() + completionTokens * pricing.completionPrice()) / 1000.0;
    }

    /**
     * 估算文本的Token数量（简单估算）
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        // 简单估算：中文约1.5字符/token，英文约4字符/token
        // 这里使用混合估算
        int chineseChars = 0;
        int otherChars = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        return (int) (chineseChars / 1.5 + otherChars / 4.0);
    }

    // DTOs
    public record UsageSummary(
            long totalTokens,
            double totalCost,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {}

    public record DailyUsage(String date, long tokens) {}

    public record ModelPricing(double promptPrice, double completionPrice) {}
}
