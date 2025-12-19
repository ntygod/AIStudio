package com.inkflow.module.style.repository;

import com.inkflow.module.style.entity.StyleSample;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StyleSampleRepository extends JpaRepository<StyleSample, UUID> {

    List<StyleSample> findByProjectId(UUID projectId);

    List<StyleSample> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    long countByProjectId(UUID projectId);

    @Query("SELECT AVG(s.editRatio) FROM StyleSample s WHERE s.projectId = :projectId")
    Double getAverageEditRatio(UUID projectId);

    @Query("SELECT SUM(s.wordCount) FROM StyleSample s WHERE s.projectId = :projectId")
    Long getTotalWordCount(UUID projectId);

    /**
     * 向量相似度搜索
     */
    @Query(value = """
        SELECT * FROM style_samples 
        WHERE project_id = :projectId 
        ORDER BY vector <=> :queryVector::vector 
        LIMIT :limit
        """, nativeQuery = true)
    List<StyleSample> findSimilarByProjectId(UUID projectId, String queryVector, int limit);

    void deleteByProjectId(UUID projectId);
}
