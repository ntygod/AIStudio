package com.inkflow.module.agent.routing;

import com.inkflow.module.agent.core.Intent;
import com.inkflow.module.project.entity.CreationPhase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 基于规则的意图分类器
 * 使用关键词匹配和阶段上下文进行快速分类
 * 设计目标：延迟 < 10ms
 *
 */
@Slf4j
@Component
public class RuleBasedClassifier {
    
    /**
     * 阶段优先意图映射
     */
    private static final Map<CreationPhase, List<Intent>> PHASE_PRIORITY_INTENTS = Map.of(
        CreationPhase.IDEA, List.of(Intent.BRAINSTORM_IDEA, Intent.PLAN_WORLD),
        CreationPhase.WORLDBUILDING, List.of(Intent.PLAN_WORLD, Intent.BRAINSTORM_IDEA),
        CreationPhase.CHARACTER, List.of(Intent.PLAN_CHARACTER, Intent.DESIGN_RELATIONSHIP, Intent.MATCH_ARCHETYPE),
        CreationPhase.OUTLINE, List.of(Intent.PLAN_OUTLINE, Intent.MANAGE_PLOTLOOP, Intent.ANALYZE_PACING),
        CreationPhase.WRITING, List.of(Intent.WRITE_CONTENT),
        CreationPhase.REVISION, List.of(Intent.CHECK_CONSISTENCY, Intent.ANALYZE_STYLE)
    );
    
    /**
     * 分类用户消息
     * 
     * @param message 用户消息
     * @param phase 当前创作阶段（可选）
     * @return 分类结果
     */
    public ClassificationResult classify(String message, CreationPhase phase) {
        long startTime = System.nanoTime();
        
        if (message == null || message.isBlank()) {
            return ClassificationResult.lowConfidence(Intent.GENERAL_CHAT, 0.3);
        }
        
        String normalizedMessage = message.toLowerCase().trim();
        
        // 1. 精确关键词匹配
        Map<Intent, Double> scores = new HashMap<>();
        for (Intent intent : Intent.values()) {
            double score = calculateKeywordScore(normalizedMessage, intent);
            if (score > 0) {
                scores.put(intent, score);
            }
        }
        
        // 2. 应用阶段优先级加成
        if (phase != null && PHASE_PRIORITY_INTENTS.containsKey(phase)) {
            List<Intent> priorityIntents = PHASE_PRIORITY_INTENTS.get(phase);
            for (Intent intent : priorityIntents) {
                if (scores.containsKey(intent)) {
                    // 阶段匹配的意图获得 20% 加成
                    scores.put(intent, scores.get(intent) * 1.2);
                }
            }
        }
        
        // 3. 选择最高分意图
        if (scores.isEmpty()) {
            return ClassificationResult.lowConfidence(Intent.GENERAL_CHAT, 0.4);
        }
        
        Intent bestIntent = Collections.max(scores.entrySet(), Map.Entry.comparingByValue()).getKey();
        double bestScore = scores.get(bestIntent);
        
        // 4. 计算置信度
        double confidence = calculateConfidence(bestScore, scores.size());
        
        // 5. 收集备选意图
        List<Intent> alternatives = scores.entrySet().stream()
            .filter(e -> !e.getKey().equals(bestIntent))
            .sorted(Map.Entry.<Intent, Double>comparingByValue().reversed())
            .limit(3)
            .map(Map.Entry::getKey)
            .toList();
        
        long elapsedNanos = System.nanoTime() - startTime;
        log.debug("[RuleClassifier] 分类完成: intent={}, confidence={:.2f}, 耗时={}μs", 
                bestIntent, confidence, elapsedNanos / 1000);
        
        return new ClassificationResult(bestIntent, confidence, alternatives);
    }
    
    /**
     * 计算关键词匹配分数
     */
    private double calculateKeywordScore(String message, Intent intent) {
        double score = 0.0;
        String[] keywords = intent.getKeywords();
        
        for (String keyword : keywords) {
            String lowerKeyword = keyword.toLowerCase();
            if (message.contains(lowerKeyword)) {
                // 基础分数
                double keywordScore = 1.0;
                
                // 关键词越长，权重越高
                keywordScore += keyword.length() * 0.1;
                
                // 如果关键词在消息开头，权重更高
                if (message.startsWith(lowerKeyword)) {
                    keywordScore *= 1.5;
                }
                
                score += keywordScore;
            }
        }
        
        return score;
    }
    
    /**
     * 计算置信度
     */
    private double calculateConfidence(double bestScore, int matchCount) {
        // 基础置信度基于最高分
        double baseConfidence = Math.min(bestScore / 3.0, 1.0);
        
        // 如果只有一个匹配，置信度更高
        if (matchCount == 1) {
            baseConfidence = Math.min(baseConfidence * 1.2, 1.0);
        }
        // 如果有多个匹配，置信度略降
        else if (matchCount > 3) {
            baseConfidence *= 0.9;
        }
        
        return Math.round(baseConfidence * 100) / 100.0;
    }
    
    /**
     * 分类结果
     */
    public record ClassificationResult(
        Intent intent,
        double confidence,
        List<Intent> alternatives
    ) {
        public static ClassificationResult lowConfidence(Intent intent, double confidence) {
            return new ClassificationResult(intent, confidence, List.of());
        }
        
        public boolean isHighConfidence() {
            return confidence >= 0.9;
        }
        
        public boolean needsLLMAnalysis() {
            return confidence < 0.9;
        }
    }
}
