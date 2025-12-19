package com.inkflow.module.snapshot.service;

import com.inkflow.common.exception.BusinessException;
import com.inkflow.common.exception.ResourceNotFoundException;
import com.inkflow.module.content.repository.ChapterRepository;
import com.inkflow.module.snapshot.dto.ChapterSnapshotDto;
import com.inkflow.module.snapshot.dto.CreateSnapshotRequest;
import com.inkflow.module.snapshot.entity.ChapterSnapshot;
import com.inkflow.module.snapshot.repository.ChapterSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 章节快照服务
 */
@Service
@Transactional(readOnly = true)
public class ChapterSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(ChapterSnapshotService.class);

    private final ChapterSnapshotRepository snapshotRepository;
    private final ChapterRepository chapterRepository;

    private static final int MAX_SNAPSHOTS_PER_CHAPTER = 20;

    public ChapterSnapshotService(ChapterSnapshotRepository snapshotRepository, ChapterRepository chapterRepository) {
        this.snapshotRepository = snapshotRepository;
        this.chapterRepository = chapterRepository;
    }

    /**
     * 获取章节的所有快照
     */
    public List<ChapterSnapshotDto> getSnapshots(UUID chapterId, UUID userId) {
        validateChapterOwnership(chapterId, userId);
        return snapshotRepository.findByChapterIdOrderByCreatedAtDesc(chapterId)
            .stream()
            .map(ChapterSnapshotDto::from)
            .toList();
    }

    /**
     * 创建快照
     */
    @Transactional
    public ChapterSnapshotDto createSnapshot(UUID chapterId, CreateSnapshotRequest request, UUID userId) {
        validateChapterOwnership(chapterId, userId);

        int wordCount = countWords(request.content());

        ChapterSnapshot snapshot = ChapterSnapshot.builder()
            .chapterId(chapterId)
            .content(request.content())
            .wordCount(wordCount)
            .note(request.note() != null ? request.note() : "手动保存")
            .build();

        snapshot = snapshotRepository.save(snapshot);
        log.debug("Created snapshot {} for chapter {}", snapshot.getId(), chapterId);

        cleanupOldSnapshots(chapterId);

        return ChapterSnapshotDto.from(snapshot);
    }

    /**
     * 自动创建快照（用于自动保存）
     */
    @Transactional
    public ChapterSnapshotDto autoCreateSnapshot(UUID chapterId, String content, UUID userId) {
        validateChapterOwnership(chapterId, userId);

        int wordCount = countWords(content);

        ChapterSnapshot snapshot = ChapterSnapshot.builder()
            .chapterId(chapterId)
            .content(content)
            .wordCount(wordCount)
            .note("自动保存")
            .build();

        snapshot = snapshotRepository.save(snapshot);
        log.debug("Auto-created snapshot {} for chapter {}", snapshot.getId(), chapterId);

        cleanupOldSnapshots(chapterId);

        return ChapterSnapshotDto.from(snapshot);
    }

    /**
     * 获取单个快照
     */
    public ChapterSnapshotDto getSnapshot(UUID snapshotId, UUID userId) {
        ChapterSnapshot snapshot = snapshotRepository.findById(snapshotId)
            .orElseThrow(() -> new ResourceNotFoundException("快照不存在"));
        validateChapterOwnership(snapshot.getChapterId(), userId);
        return ChapterSnapshotDto.from(snapshot);
    }

    /**
     * 删除单个快照
     */
    @Transactional
    public void deleteSnapshot(UUID snapshotId, UUID userId) {
        ChapterSnapshot snapshot = snapshotRepository.findById(snapshotId)
            .orElseThrow(() -> new ResourceNotFoundException("快照不存在"));
        validateChapterOwnership(snapshot.getChapterId(), userId);
        snapshotRepository.delete(snapshot);
        log.debug("Deleted snapshot {}", snapshotId);
    }

    /**
     * 删除章节的所有快照
     */
    @Transactional
    public void deleteAllSnapshots(UUID chapterId, UUID userId) {
        validateChapterOwnership(chapterId, userId);
        snapshotRepository.deleteByChapterId(chapterId);
        log.debug("Deleted all snapshots for chapter {}", chapterId);
    }

    /**
     * 清理旧快照，保留最新的 N 个
     */
    private void cleanupOldSnapshots(UUID chapterId) {
        long count = snapshotRepository.countByChapterId(chapterId);
        if (count > MAX_SNAPSHOTS_PER_CHAPTER) {
            snapshotRepository.deleteOldSnapshots(chapterId, MAX_SNAPSHOTS_PER_CHAPTER);
            log.debug("Cleaned up old snapshots for chapter {}, kept {}", chapterId, MAX_SNAPSHOTS_PER_CHAPTER);
        }
    }

    /**
     * 验证章节所有权
     */
    private void validateChapterOwnership(UUID chapterId, UUID userId) {
        boolean exists = chapterRepository.existsByIdAndVolumeProjectUserId(chapterId, userId);
        if (!exists) {
            throw new BusinessException("章节不存在或无权访问");
        }
    }

    /**
     * 计算字数（去除 HTML 标签）
     */
    private int countWords(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        String plainText = content.replaceAll("<[^>]*>", "");
        return plainText.length();
    }
}
