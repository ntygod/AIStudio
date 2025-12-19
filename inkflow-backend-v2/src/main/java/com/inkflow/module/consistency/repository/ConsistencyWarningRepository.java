package com.inkflow.module.consistency.repository;

import com.inkflow.module.consistency.entity.ConsistencyWarning;
import com.inkflow.module.consistency.entity.ConsistencyWarning.Severity;
import com.inkflow.module.consistency.entity.ConsistencyWarning.WarningType;
import com.inkflow.module.evolution.entity.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 一致性警告 Repository
 * 
 * Requirements: 9.4
 *
 * @author zsg
 * @date 2025/12/17
 */
@Repository
public interface ConsistencyWarningRepository extends JpaRepository<ConsistencyWarning, UUID> {

    /**
     * 按项目ID查询未解决的警告
     */
    List<ConsistencyWarning> findByProjectIdAndResolvedFalseOrderByCreatedAtDesc(UUID projectId);

    /**
     * 按项目ID查询所有警告
     */
    List<ConsistencyWarning> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    /**
     * 按实体ID查询警告
     */
    List<ConsistencyWarning> findByEntityIdOrderByCreatedAtDesc(UUID entityId);

    /**
     * 按项目ID和严重程度查询
     */
    List<ConsistencyWarning> findByProjectIdAndSeverityAndResolvedFalse(UUID projectId, Severity severity);

    /**
     * 按项目ID和警告类型查询
     */
    List<ConsistencyWarning> findByProjectIdAndWarningTypeAndResolvedFalse(UUID projectId, WarningType warningType);

    /**
     * 按项目ID和实体类型查询
     */
    List<ConsistencyWarning> findByProjectIdAndEntityTypeAndResolvedFalse(UUID projectId, EntityType entityType);

    /**
     * 统计项目未解决警告数量
     */
    long countByProjectIdAndResolvedFalse(UUID projectId);

    /**
     * 按严重程度统计未解决警告
     */
    long countByProjectIdAndSeverityAndResolvedFalse(UUID projectId, Severity severity);

    /**
     * 删除已解决的旧警告
     */
    @Modifying
    @Query("DELETE FROM ConsistencyWarning w WHERE w.resolved = true AND w.resolvedAt < :before")
    int deleteResolvedOlderThan(@Param("before") LocalDateTime before);

    /**
     * 删除项目的所有警告
     */
    @Modifying
    void deleteByProjectId(UUID projectId);

    /**
     * 删除实体的所有警告
     */
    @Modifying
    void deleteByEntityId(UUID entityId);

    /**
     * 检查是否存在相同的未解决警告（避免重复）
     */
    boolean existsByProjectIdAndEntityIdAndWarningTypeAndResolvedFalse(
            UUID projectId, UUID entityId, WarningType warningType);

    /**
     * 批量标记为已解决
     */
    @Modifying
    @Query("UPDATE ConsistencyWarning w SET w.resolved = true, w.resolvedAt = :now WHERE w.id IN :ids")
    int resolveByIds(@Param("ids") List<UUID> ids, @Param("now") LocalDateTime now);

    /**
     * 标记实体的所有警告为已解决
     */
    @Modifying
    @Query("UPDATE ConsistencyWarning w SET w.resolved = true, w.resolvedAt = :now WHERE w.entityId = :entityId AND w.resolved = false")
    int resolveByEntityId(@Param("entityId") UUID entityId, @Param("now") LocalDateTime now);
}
