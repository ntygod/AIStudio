package com.inkflow.module.progress.repository;

import com.inkflow.module.progress.entity.ProgressSnapshot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProgressSnapshotRepository extends JpaRepository<ProgressSnapshot, UUID> {

    List<ProgressSnapshot> findByProjectIdOrderBySnapshotAtDesc(UUID projectId, Pageable pageable);

    @Query("SELECT p FROM ProgressSnapshot p WHERE p.projectId = :projectId " +
           "AND p.snapshotAt BETWEEN :start AND :end ORDER BY p.snapshotAt ASC")
    List<ProgressSnapshot> findByProjectIdAndSnapshotAtBetween(
            @Param("projectId") UUID projectId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    Optional<ProgressSnapshot> findFirstByProjectIdOrderBySnapshotAtDesc(UUID projectId);

    @Modifying
    @Query("DELETE FROM ProgressSnapshot p WHERE p.projectId = :projectId")
    int deleteByProjectId(@Param("projectId") UUID projectId);

    @Query("SELECT COUNT(p) FROM ProgressSnapshot p WHERE p.projectId = :projectId")
    long countByProjectId(@Param("projectId") UUID projectId);

    @Query("SELECT p FROM ProgressSnapshot p WHERE p.projectId = :projectId " +
           "AND p.snapshotAt >= :since ORDER BY p.snapshotAt ASC")
    List<ProgressSnapshot> findRecentSnapshots(
            @Param("projectId") UUID projectId,
            @Param("since") LocalDateTime since);
}
