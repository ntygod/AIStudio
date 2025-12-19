package com.inkflow.module.snapshot.repository;

import com.inkflow.module.snapshot.entity.ChapterSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChapterSnapshotRepository extends JpaRepository<ChapterSnapshot, UUID> {

    List<ChapterSnapshot> findByChapterIdOrderByCreatedAtDesc(UUID chapterId);

    long countByChapterId(UUID chapterId);

    void deleteByChapterId(UUID chapterId);

    /**
     * 删除旧快照，保留最新的 N 个
     */
    @Modifying
    @Query(value = """
        DELETE FROM chapter_snapshots 
        WHERE chapter_id = :chapterId 
        AND id NOT IN (
            SELECT id FROM chapter_snapshots 
            WHERE chapter_id = :chapterId 
            ORDER BY created_at DESC 
            LIMIT :keepCount
        )
        """, nativeQuery = true)
    void deleteOldSnapshots(UUID chapterId, int keepCount);
}
