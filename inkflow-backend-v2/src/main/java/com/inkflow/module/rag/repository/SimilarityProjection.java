package com.inkflow.module.rag.repository;

import java.util.UUID;

/**
 * 相似性搜索结果投影接口
 * 用于返回带余弦距离分数的搜索结果
 *
 * @author zsg
 * @date 2025/12/17
 */
public interface SimilarityProjection {

    UUID getId();

    String getSourceType();

    UUID getSourceId();

    String getContent();

    String getChunkLevel();

    UUID getParentId();

    Integer getChunkOrder();

    /**
     * 余弦距离 (0-2之间，越小越相似)
     * 相似度 = 1 - cosineDistance
     */
    Double getCosineDistance();

    /**
     * 计算相似度分数
     * @return 相似度 (0-1之间，越大越相似)
     */
    default Double getSimilarity() {
        Double distance = getCosineDistance();
        if (distance == null) {
            return null;
        }
        return 1.0 - distance;
    }
}
