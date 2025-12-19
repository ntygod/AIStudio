package com.inkflow.module.progress.repository;

import com.inkflow.module.progress.entity.PhaseTransition;
import com.inkflow.module.project.entity.CreationPhase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PhaseTransitionRepository extends JpaRepository<PhaseTransition, UUID> {

    List<PhaseTransition> findByProjectIdOrderByTransitionedAtDesc(UUID projectId);

    List<PhaseTransition> findByProjectIdOrderByTransitionedAtAsc(UUID projectId);

    Optional<PhaseTransition> findFirstByProjectIdOrderByTransitionedAtDesc(UUID projectId);

    @Query("SELECT COUNT(p) FROM PhaseTransition p WHERE p.projectId = :projectId")
    long countByProjectId(@Param("projectId") UUID projectId);

    @Query("SELECT p FROM PhaseTransition p WHERE p.projectId = :projectId AND p.toPhase = :phase")
    List<PhaseTransition> findByProjectIdAndToPhase(
            @Param("projectId") UUID projectId,
            @Param("phase") CreationPhase phase);

    @Modifying
    @Query("DELETE FROM PhaseTransition p WHERE p.projectId = :projectId")
    int deleteByProjectId(@Param("projectId") UUID projectId);
}
