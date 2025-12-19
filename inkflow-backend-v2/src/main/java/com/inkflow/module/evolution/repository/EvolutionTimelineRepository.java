package com.inkflow.module.evolution.repository;

import com.inkflow.module.evolution.entity.EntityType;
import com.inkflow.module.evolution.entity.EvolutionTimeline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EvolutionTimelineRepository extends JpaRepository<EvolutionTimeline, UUID> {

    Optional<EvolutionTimeline> findByEntityTypeAndEntityId(EntityType entityType, UUID entityId);

    List<EvolutionTimeline> findByProjectId(UUID projectId);

    List<EvolutionTimeline> findByProjectIdAndEntityType(UUID projectId, EntityType entityType);

    boolean existsByEntityTypeAndEntityId(EntityType entityType, UUID entityId);
}
