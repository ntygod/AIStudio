package com.inkflow.module.extraction.service;

import com.inkflow.module.extraction.dto.ExtractedEntity;
import com.inkflow.module.extraction.dto.ExtractedEntity.EntityCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 实体去重服务
 * 合并重复的实体
 */
@Service
public class EntityDeduplicationService {

    private static final Logger log = LoggerFactory.getLogger(EntityDeduplicationService.class);
    private static final double SIMILARITY_THRESHOLD = 0.8;

    /**
     * 去重并合并实体
     */
    public List<ExtractedEntity> deduplicateAndMerge(List<ExtractedEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }

        // 按类别分组
        Map<EntityCategory, List<ExtractedEntity>> byCategory = entities.stream()
                .collect(Collectors.groupingBy(ExtractedEntity::category));

        List<ExtractedEntity> result = new ArrayList<>();

        // 对每个类别进行去重
        for (Map.Entry<EntityCategory, List<ExtractedEntity>> entry : byCategory.entrySet()) {
            List<ExtractedEntity> deduped = deduplicateCategory(entry.getValue());
            result.addAll(deduped);
        }

        return result;
    }

    /**
     * 对同一类别的实体去重
     */
    private List<ExtractedEntity> deduplicateCategory(List<ExtractedEntity> entities) {
        List<ExtractedEntity> result = new ArrayList<>();
        Set<Integer> merged = new HashSet<>();

        for (int i = 0; i < entities.size(); i++) {
            if (merged.contains(i)) continue;

            ExtractedEntity current = entities.get(i);
            List<ExtractedEntity> duplicates = new ArrayList<>();
            duplicates.add(current);

            // 查找重复项
            for (int j = i + 1; j < entities.size(); j++) {
                if (merged.contains(j)) continue;

                ExtractedEntity other = entities.get(j);
                if (areDuplicates(current, other)) {
                    duplicates.add(other);
                    merged.add(j);
                }
            }

            // 合并重复项
            ExtractedEntity mergedEntity = mergeEntities(duplicates);
            result.add(mergedEntity);
            merged.add(i);
        }

        return result;
    }

    /**
     * 判断两个实体是否重复
     */
    private boolean areDuplicates(ExtractedEntity a, ExtractedEntity b) {
        // 名称完全相同
        if (a.name().equalsIgnoreCase(b.name())) {
            return true;
        }

        // 名称相似度检查
        double nameSimilarity = calculateSimilarity(a.name(), b.name());
        if (nameSimilarity >= SIMILARITY_THRESHOLD) {
            return true;
        }

        // 别名匹配
        if (a.aliases() != null && b.aliases() != null) {
            for (String aliasA : a.aliases()) {
                if (b.name().equalsIgnoreCase(aliasA)) return true;
                for (String aliasB : b.aliases()) {
                    if (aliasA.equalsIgnoreCase(aliasB)) return true;
                }
            }
        }

        // 检查一方的名称是否在另一方的别名中
        if (a.aliases() != null && a.aliases().stream()
                .anyMatch(alias -> alias.equalsIgnoreCase(b.name()))) {
            return true;
        }
        if (b.aliases() != null && b.aliases().stream()
                .anyMatch(alias -> alias.equalsIgnoreCase(a.name()))) {
            return true;
        }

        return false;
    }

    /**
     * 合并多个实体
     */
    private ExtractedEntity mergeEntities(List<ExtractedEntity> entities) {
        if (entities.size() == 1) {
            return entities.get(0);
        }

        // 选择置信度最高的作为主实体
        ExtractedEntity primary = entities.stream()
                .max(Comparator.comparingDouble(ExtractedEntity::confidence))
                .orElse(entities.get(0));

        // 合并别名
        Set<String> allAliases = new HashSet<>();
        if (primary.aliases() != null) {
            allAliases.addAll(primary.aliases());
        }
        for (ExtractedEntity entity : entities) {
            if (!entity.name().equalsIgnoreCase(primary.name())) {
                allAliases.add(entity.name());
            }
            if (entity.aliases() != null) {
                allAliases.addAll(entity.aliases());
            }
        }
        allAliases.remove(primary.name());

        // 合并属性
        Map<String, Object> mergedAttributes = new HashMap<>();
        for (ExtractedEntity entity : entities) {
            if (entity.attributes() != null) {
                for (Map.Entry<String, Object> attr : entity.attributes().entrySet()) {
                    // 保留非空值，优先保留置信度高的实体的属性
                    if (attr.getValue() != null) {
                        mergedAttributes.putIfAbsent(attr.getKey(), attr.getValue());
                    }
                }
            }
        }

        // 合并源文本
        String mergedSourceText = entities.stream()
                .map(ExtractedEntity::sourceText)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(" | "));

        // 计算平均置信度
        double avgConfidence = entities.stream()
                .mapToDouble(ExtractedEntity::confidence)
                .average()
                .orElse(0.5);

        return ExtractedEntity.builder()
                .name(primary.name())
                .category(primary.category())
                .attributes(mergedAttributes)
                .aliases(new ArrayList<>(allAliases))
                .sourceText(mergedSourceText)
                .confidence(avgConfidence)
                .build();
    }

    /**
     * 计算字符串相似度（Jaccard相似度）
     */
    private double calculateSimilarity(String a, String b) {
        if (a == null || b == null) return 0.0;

        String lowerA = a.toLowerCase();
        String lowerB = b.toLowerCase();

        // 简单的字符级Jaccard相似度
        Set<Character> setA = lowerA.chars().mapToObj(c -> (char) c).collect(Collectors.toSet());
        Set<Character> setB = lowerB.chars().mapToObj(c -> (char) c).collect(Collectors.toSet());

        Set<Character> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);

        Set<Character> union = new HashSet<>(setA);
        union.addAll(setB);

        if (union.isEmpty()) return 0.0;
        return (double) intersection.size() / union.size();
    }
}
