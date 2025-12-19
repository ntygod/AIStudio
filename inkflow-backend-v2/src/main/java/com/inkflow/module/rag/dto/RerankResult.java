package com.inkflow.module.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 重排序结果DTO
 * 用于RerankerService返回的重排序结果，包含原始索引、得分和文档内容。
 *
 * @author zsg
 * @date 2025/12/17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankResult {

    /**
     * 原始索引位置（在输入候选列表中的位置）
     */
    private int index;

    /**
     * 重排序得分 (0-1，越大越相关)
     */
    private double score;

    /**
     * 文档内容
     */
    private String document;

    /**
     * 创建RerankResult的便捷方法
     */
    public static RerankResult of(int index, double score, String document) {
        return new RerankResult(index, score, document);
    }

    /**
     * 比较两个结果的得分（用于排序）
     * 返回负数表示this得分更高，应排在前面
     */
    public int compareByScore(RerankResult other) {
        return Double.compare(other.score, this.score);
    }
}
