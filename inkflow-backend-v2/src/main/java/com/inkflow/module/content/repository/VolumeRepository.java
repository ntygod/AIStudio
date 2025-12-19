package com.inkflow.module.content.repository;

import com.inkflow.module.content.entity.Volume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 分卷数据访问层
 */
@Repository
public interface VolumeRepository extends JpaRepository<Volume, UUID> {
    
    /**
     * 根据项目ID查找所有分卷（按顺序）
     */
    List<Volume> findByProjectIdAndDeletedFalseOrderByOrderIndexAsc(UUID projectId);
    
    /**
     * 根据ID和项目ID查找分卷
     */
    Optional<Volume> findByIdAndProjectIdAndDeletedFalse(UUID id, UUID projectId);
    
    /**
     * 获取项目的最大排序索引
     */
    @Query("SELECT COALESCE(MAX(v.orderIndex), -1) FROM Volume v WHERE v.projectId = :projectId AND v.deleted = false")
    int findMaxOrderIndex(@Param("projectId") UUID projectId);
    
    /**
     * 统计项目的分卷数量
     */
    long countByProjectIdAndDeletedFalse(UUID projectId);
}
