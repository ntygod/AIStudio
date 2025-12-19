package com.inkflow.module.ai_bridge.repository;

import com.inkflow.module.ai_bridge.entity.ToolInvocationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Tool 调用日志 Repository
 * 
 * Requirements: 17.1, 17.4, 17.5
 *
 * @author zsg
 * @date 2025/12/17
 */
@Repository
public interface ToolInvocationLogRepository extends JpaRepository<ToolInvocationLog, UUID> {

    /**
     * 按请求ID查询所有 Tool 调用
     */
    List<ToolInvocationLog> findByRequestIdOrderByCreatedAtAsc(String requestId);

    /**
     * 按用户ID分页查询
     */
    Page<ToolInvocationLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * 按项目ID分页查询
     */
    Page<ToolInvocationLog> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);

    /**
     * 按工具名称分页查询
     */
    Page<ToolInvocationLog> findByToolNameOrderByCreatedAtDesc(String toolName, Pageable pageable);

    /**
     * 查询失败的调用
     */
    Page<ToolInvocationLog> findBySuccessFalseOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 按用户和时间范围查询
     */
    @Query("SELECT t FROM ToolInvocationLog t WHERE t.userId = :userId " +
           "AND t.createdAt BETWEEN :startTime AND :endTime ORDER BY t.createdAt DESC")
    List<ToolInvocationLog> findByUserIdAndTimeRange(
        @Param("userId") UUID userId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * 统计工具调用次数
     */
    @Query("SELECT t.toolName, COUNT(t), AVG(t.durationMs) FROM ToolInvocationLog t " +
           "WHERE t.createdAt >= :since GROUP BY t.toolName ORDER BY COUNT(t) DESC")
    List<Object[]> getToolUsageStats(@Param("since") LocalDateTime since);

    /**
     * 统计用户调用次数
     */
    @Query("SELECT COUNT(t) FROM ToolInvocationLog t WHERE t.userId = :userId AND t.createdAt >= :since")
    long countByUserIdSince(@Param("userId") UUID userId, @Param("since") LocalDateTime since);

    /**
     * 删除过期日志（用于日志清理）
     * Requirements: 17.4
     */
    @Modifying
    @Query("DELETE FROM ToolInvocationLog t WHERE t.createdAt < :cutoffTime")
    int deleteByCreatedAtBefore(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * 统计错误率
     */
    @Query("SELECT t.toolName, " +
           "COUNT(CASE WHEN t.success = false THEN 1 END) * 100.0 / COUNT(t) as errorRate " +
           "FROM ToolInvocationLog t WHERE t.createdAt >= :since " +
           "GROUP BY t.toolName HAVING COUNT(t) > 10 ORDER BY errorRate DESC")
    List<Object[]> getToolErrorRates(@Param("since") LocalDateTime since);
}
