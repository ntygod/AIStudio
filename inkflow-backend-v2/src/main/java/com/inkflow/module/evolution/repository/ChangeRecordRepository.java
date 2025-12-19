package com.inkflow.module.evolution.repository;

import com.inkflow.module.evolution.entity.ChangeRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChangeRecordRepository extends JpaRepository<ChangeRecord, UUID> {

    List<ChangeRecord> findBySnapshotIdOrderByCreatedAtAsc(UUID snapshotId);

    List<ChangeRecord> findBySnapshotIdIn(List<UUID> snapshotIds);

    void deleteBySnapshotId(UUID snapshotId);
}
