package com.inkflow.module.progress.service;

import com.inkflow.module.character.repository.CharacterRepository;
import com.inkflow.module.content.repository.ChapterRepository;
import com.inkflow.module.content.repository.VolumeRepository;
import com.inkflow.module.plotloop.entity.PlotLoopStatus;
import com.inkflow.module.plotloop.repository.PlotLoopRepository;
import com.inkflow.module.progress.entity.ProgressSnapshot;
import com.inkflow.module.progress.repository.ProgressSnapshotRepository;
import com.inkflow.module.project.entity.CreationPhase;
import com.inkflow.module.project.entity.Project;
import com.inkflow.module.project.repository.ProjectRepository;
import com.inkflow.module.wiki.repository.WikiEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 创作进度追踪服务
 * 追踪实体数量（已移除不实用的阶段完成度计算功能）
 * 
 * Requirements: 5.1, 5.5, 12.1-12.4
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreationProgressService {

    private final ProjectRepository projectRepository;
    private final CharacterRepository characterRepository;
    private final WikiEntryRepository wikiEntryRepository;
    private final VolumeRepository volumeRepository;
    private final ChapterRepository chapterRepository;
    private final PlotLoopRepository plotLoopRepository;
    private final ProgressSnapshotRepository snapshotRepository;
    
    // 自动保存快照的最小间隔（分钟）
    private static final int AUTO_SAVE_INTERVAL_MINUTES = 30;

    /**
     * 获取项目的创作进度
     * 注意：已移除 phaseCompletion 和 suggestedNextPhase 计算，因为这些功能不实用
     */
    @Transactional(readOnly = true)
    public CreationProgress getProgress(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在: " + projectId));

        long characterCount = characterRepository.countByProjectId(projectId);
        long wikiEntryCount = wikiEntryRepository.countByProjectId(projectId);
        long volumeCount = volumeRepository.countByProjectIdAndDeletedFalse(projectId);
        long chapterCount = chapterRepository.countByProjectIdAndDeletedFalse(projectId);
        long wordCount = chapterRepository.sumWordCountByProjectId(projectId);
        long plotLoopCount = plotLoopRepository.countByProjectId(projectId);
        long openPlotLoops = plotLoopRepository.countByProjectIdAndStatus(projectId, PlotLoopStatus.OPEN);
        long closedPlotLoops = plotLoopRepository.countByProjectIdAndStatus(projectId, PlotLoopStatus.CLOSED);

        CreationPhase currentPhase = project.getCreationPhase();

        return CreationProgress.builder()
                .projectId(projectId)
                .currentPhase(currentPhase)
                .characterCount(characterCount)
                .wikiEntryCount(wikiEntryCount)
                .volumeCount(volumeCount)
                .chapterCount(chapterCount)
                .wordCount(wordCount)
                .plotLoopCount(plotLoopCount)
                .openPlotLoops(openPlotLoops)
                .closedPlotLoops(closedPlotLoops)
                .build();
    }

    /**
     * 检查是否可以进入下一阶段
     * 简化版本：允许任意阶段转换，由用户自行决定
     */
    @Transactional(readOnly = true)
    public PhaseTransitionCheck checkPhaseTransition(UUID projectId, CreationPhase targetPhase) {
        CreationProgress progress = getProgress(projectId);
        CreationPhase currentPhase = progress.getCurrentPhase();

        // 检查是否是有效的阶段转换
        if (targetPhase.ordinal() <= currentPhase.ordinal()) {
            return PhaseTransitionCheck.builder()
                    .canTransition(true)
                    .message("可以回退到之前的阶段")
                    .build();
        }

        // 检查是否跳过了阶段
        if (targetPhase.ordinal() > currentPhase.ordinal() + 1) {
            return PhaseTransitionCheck.builder()
                    .canTransition(true)
                    .message("可以跳过阶段，但建议按顺序进行创作")
                    .build();
        }

        return PhaseTransitionCheck.builder()
                .canTransition(true)
                .message("可以进入下一阶段")
                .build();
    }

    /**
     * 更新项目阶段
     */
    @Transactional
    public void updatePhase(UUID projectId, CreationPhase newPhase) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在: " + projectId));
        
        project.setCreationPhase(newPhase);
        projectRepository.save(project);
        log.info("项目 {} 阶段更新为 {}", projectId, newPhase);
        
        // 阶段变更时自动保存快照
        autoSaveSnapshot(projectId);
    }

    /**
     * 自动保存进度快照（如果距离上次保存超过指定间隔）
     * 
     * Requirements: 5.1, 5.5
     */
    @Transactional
    public void autoSaveSnapshot(UUID projectId) {
        Optional<ProgressSnapshot> latestSnapshot = snapshotRepository
                .findFirstByProjectIdOrderBySnapshotAtDesc(projectId);
        
        boolean shouldSave = latestSnapshot
                .map(snapshot -> snapshot.getSnapshotAt()
                        .plusMinutes(AUTO_SAVE_INTERVAL_MINUTES)
                        .isBefore(LocalDateTime.now()))
                .orElse(true);
        
        if (shouldSave) {
            saveSnapshot(projectId);
        }
    }

    /**
     * 保存进度快照
     * 已简化：移除了不实用的 phaseCompletion 字段
     * 
     * Requirements: 5.1, 12.1-12.4
     */
    @Transactional
    public ProgressSnapshot saveSnapshot(UUID projectId) {
        CreationProgress progress = getProgress(projectId);
        
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
     * 在进度变更时触发自动保存
     * 可由其他服务调用以触发快照保存
     * 
     * Requirements: 5.5
     */
    @Transactional
    public void onProgressChange(UUID projectId) {
        autoSaveSnapshot(projectId);
    }
}
