package com.inkflow.module.evolution.repository;

import com.inkflow.module.evolution.entity.StateSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StateSnapshotRepository extends JpaRepository<StateSnapshot, UUID> {

    List<StateSnapshot> findByTimelineIdOrderByChapterOrderAsc(UUID timelineId);

    List<StateSnapshot> findByChapterId(UUID chapterId);

    /**
     * 获取指定时间线在某章节之前（含）的最近关键帧
     */
    @Query("""
        SELECT s FROM StateSnapshot s 
        WHERE s.timelineId = :timelineId 
          AND s.chapterOrder <= :chapterOrder 
          AND s.isKeyframe = true 
        ORDER BY s.chapterOrder DESC 
        LIMIT 1
        """)
    Optional<StateSnapshot> findNearestKeyframeBefore(
            @Param("timelineId") UUID timelineId,
            @Param("chapterOrder") Integer chapterOrder);

    /**
     * 获取关键帧之后到目标章节的所有增量快照
     */
    @Query("""
        SELECT s FROM StateSnapshot s 
        WHERE s.timelineId = :timelineId 
          AND s.chapterOrder > :keyframeOrder 
          AND s.chapterOrder <= :targetOrder 
          AND s.isKeyframe = false 
        ORDER BY s.chapterOrder ASC
        """)
    List<StateSnapshot> findDeltasBetween(
            @Param("timelineId") UUID timelineId,
            @Param("keyframeOrder") Integer keyframeOrder,
            @Param("targetOrder") Integer targetOrder);

    /**
     * 获取时间线的最新快照
     */
    @Query("""
        SELECT s FROM StateSnapshot s 
        WHERE s.timelineId = :timelineId 
        ORDER BY s.chapterOrder DESC 
        LIMIT 1
        """)
    Optional<StateSnapshot> findLatestByTimelineId(@Param("timelineId") UUID timelineId);

    /**
     * 获取指定章节的快照
     */
    Optional<StateSnapshot> findByTimelineIdAndChapterId(UUID timelineId, UUID chapterId);

    /**
     * 统计时间线的快照数量
     */
    long countByTimelineId(UUID timelineId);

    /**
     * 统计关键帧数量
     */
    @Query("SELECT COUNT(s) FROM StateSnapshot s WHERE s.timelineId = :timelineId AND s.isKeyframe = true")
    long countKeyframesByTimelineId(@Param("timelineId") UUID timelineId);
}
