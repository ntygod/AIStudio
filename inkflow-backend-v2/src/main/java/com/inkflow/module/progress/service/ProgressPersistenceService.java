package com.inkflow.module.progress.service;

import com.inkflow.module.progress.entity.ProgressSnapshot;
import com.inkflow.module.progress.repository.ProgressSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 进度持久化服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressPersistenceService {

    private final ProgressSnapshotRepository snapshotRepository;
    private final CreationProgressService progressService;

    /**
     * 保存进度快照
     * 已简化：移除了不实用的 phaseCompletion 字段
     * Requirements: 12.1-12.4
     */
    @Transactional
    public ProgressSnapshot saveSnapshot(UUID projectId) {
        CreationProgress progress = progressService.getProgress(projectId);
        
        ProgressSnapshot snapshot = ProgressSnapshot.builder()
                .projectId(projectId)
                .phase(progress.getCurrentPhase())
                .phaseCompletion(0) // 已弃用，保留字段以兼容现有数据
                .characterCount(progress.getCharacterCount())
                .wikiEntryCount(progress.getWikiEntryCount())
                .volumeCount(progress.getVolumeCount())
                .chapterCount(progress.getChapterCount())
                .wordCount(progress.getWordCount())
                .plotLoopCount(progress.getPlotLoopCount())
                .openPlotLoops(progress.getOpenPlotLoops())
                .closedPlotLoops(progress.getClosedPlotLoops())
                .snapshotAt(LocalDateTime.now())
                .build();

        snapshot = snapshotRepository.save(snapshot);
        log.debug("保存进度快照: projectId={}, snapshotId={}", projectId, snapshot.getId());
        return snapshot;
    }

    /**
     * 获取进度历史
     */
    @Transactional(readOnly = true)
    public List<ProgressSnapshot> getHistory(UUID projectId, int limit) {
        return snapshotRepository.findByProjectIdOrderBySnapshotAtDesc(projectId, PageRequest.of(0, limit));
    }

    /**
     * 获取指定时间范围内的进度历史
     */
    @Transactional(readOnly = true)
    public List<ProgressSnapshot> getHistoryBetween(UUID projectId, LocalDateTime start, LocalDateTime end) {
        return snapshotRepository.findByProjectIdAndSnapshotAtBetween(projectId, start, end);
    }

    /**
     * 获取最新快照
     */
    @Transactional(readOnly = true)
    public Optional<ProgressSnapshot> getLatestSnapshot(UUID projectId) {
        return snapshotRepository.findFirstByProjectIdOrderBySnapshotAtDesc(projectId);
    }

    /**
     * 删除项目的所有进度记录
     */
    @Transactional
    public int deleteByProjectId(UUID projectId) {
        int count = snapshotRepository.deleteByProjectId(projectId);
        log.info("删除项目进度记录: projectId={}, count={}", projectId, count);
        return count;
    }

    /**
     * 获取快照数量
     */
    @Transactional(readOnly = true)
    public long countSnapshots(UUID projectId) {
        return snapshotRepository.countByProjectId(projectId);
    }
}
