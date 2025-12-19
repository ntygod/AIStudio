package com.inkflow.module.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * RAG搜索结果DTO
 * 支持混合检索（向量+全文）和RRF融合算法。
 *
 * @author zsg
 * @date 2025/12/17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {

    /**
     * 知识块ID
     */
    private UUID id;

    /**
     * 来源类型 (character, wiki, chapter, etc.)
     */
    private String sourceType;

    /**
     * 来源实体ID
     */
    private UUID sourceId;

    /**
     * 内容
     */
    private String content;

    /**
     * 向量相似度分数 (0-1，越大越相似)
     */
    private Double similarity;

    /**
     * 余弦距离 (0-2，越小越相似)
     */
    private Double cosineDistance;

    /**
     * 全文搜索分数
     */
    private Double fullTextScore;

    /**
     * RRF融合分数
     * 公式: Score = 1.0 / (k + rank)
     */
    private Double rrfScore;

    /**
     * 重排序分数
     */
    private Double rerankerScore;

    /**
     * 元数据
     */
    private Map<String, Object> metadata;

    /**
     * 块级别 (parent, child)
     */
    private String chunkLevel;

    /**
     * 父块ID（用于父子块检索）
     */
    private UUID parentId;

    /**
     * 章节顺序（用于上下文排序）
     */
    private Integer chapterOrder;

    /**
     * 块顺序（用于上下文排序）
     */
    private Integer blockOrder;

    /**
     * 设置余弦距离并计算相似度
     */
    public void setCosineDistanceAndCalculateSimilarity(Double cosineDistance) {
        this.cosineDistance = cosineDistance;
        if (cosineDistance != null) {
            this.similarity = 1.0 - cosineDistance;
        }
    }

    /**
     * 获取最终分数（优先使用RRF分数，其次是重排序分数，最后是相似度）
     */
    public Double getFinalScore() {
        if (rrfScore != null) {
            return rrfScore;
        }
        if (rerankerScore != null) {
            return rerankerScore;
        }
        return similarity;
    }
}
