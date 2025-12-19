package com.inkflow.module.plotloop.event;

import com.inkflow.module.consistency.service.ConsistencyWarningService;
import com.inkflow.module.consistency.service.ProactiveConsistencyService;
import com.inkflow.module.content.repository.ChapterRepository;
import com.inkflow.module.evolution.entity.EntityType;
import com.inkflow.module.evolution.service.EvolutionAnalysisService;
import com.inkflow.module.plotloop.entity.PlotLoopStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 伏笔变更事件监听器
 * 
 * 监听 PlotLoopChangedEvent 事件，触发：
 * 1. 主动式一致性检查（带防抖和限流）
 * 2. 演进快照创建
 * 3. 解决时验证章节存在性
 * 
 * Requirements: 3.1, 3.2, 3.3
 */
@Component
@RequiredArgsConstructor
public class PlotLoopChangeListener {

    private static final Logger log = LoggerFactory.getLogger(PlotLoopChangeListener.class);

    private final ProactiveConsistencyService consistencyService;
    private final EvolutionAnalysisService evolutionService;
    private final ConsistencyWarningService warningService;
    private final ChapterRepository chapterRepository;

    /**
     * 处理伏笔变更事件
     * 
     * 在事务提交后异步执行，避免阻塞主流程
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePlotLoopChanged(PlotLoopChangedEvent event) {
        log.info("收到伏笔变更事件: plotLoopId={}, projectId={}, operation={}, status={}",
                event.getPlotLoopId(), event.getProjectId(), event.getOperation(), event.getStatus());

        switch (event.getOperation()) {
            case CREATE -> {
                // 创建演进快照
                createEvolutionSnapshot(event);
            }
            case UPDATE -> {
                // 触发一致性检查
                triggerConsistencyCheck(event);
                // 创建演进快照
                createEvolutionSnapshot(event);
            }
            case STATUS_CHANGE -> {
                // 状态变更时触发一致性检查
                triggerConsistencyCheck(event);
                // 创建演进快照
                createEvolutionSnapshot(event);
                // 如果是解决操作，验证章节存在性
                if (event.isResolved()) {
                    validateResolutionChapter(event);
                }
            }
            case DELETE -> {
                // 清理关联数据
                cleanupAssociatedData(event);
            }
        }
    }

    /**
     * 触发一致性检查
     * Requirements: 3.1
     */
    private void triggerConsistencyCheck(PlotLoopChangedEvent event) {
        log.debug("触发伏笔一致性检查: projectId={}, plotLoopId={}",
                event.getProjectId(), event.getPlotLoopId());
        try {
            // 使用 RELATIONSHIP 类型表示伏笔与章节的关联关系
            consistencyService.triggerCheck(
                    event.getProjectId(),
                    event.getPlotLoopId(),
                    EntityType.RELATIONSHIP,
                    event.getTitle()
            );
        } catch (Exception e) {
            log.error("伏笔一致性检查触发失败: plotLoopId={}", event.getPlotLoopId(), e);
        }
    }

    /**
     * 创建演进快照
     * Requirements: 3.3
     */
    private void createEvolutionSnapshot(PlotLoopChangedEvent event) {
        log.debug("创建伏笔演进快照: projectId={}, plotLoopId={}",
                event.getProjectId(), event.getPlotLoopId());
        try {
            if (event.getCurrentState() != null) {
                evolutionService.createSnapshotForEntity(
                        event.getProjectId(),
                        event.getPlotLoopId(),
                        EntityType.RELATIONSHIP, // 伏笔使用 RELATIONSHIP 类型
                        event.getCurrentState()
                );
            }
        } catch (Exception e) {
            log.error("伏笔演进快照创建失败: plotLoopId={}", event.getPlotLoopId(), e);
        }
    }

    /**
     * 验证解决章节存在性
     * Requirements: 3.2
     */
    private void validateResolutionChapter(PlotLoopChangedEvent event) {
        log.debug("验证伏笔解决章节: plotLoopId={}", event.getPlotLoopId());
        try {
            if (event.getCurrentState() == null) {
                return;
            }
            
            Object resolveChapterIdObj = event.getCurrentState().get("resolveChapterId");
            if (resolveChapterIdObj == null) {
                log.warn("伏笔已解决但未指定解决章节: plotLoopId={}", event.getPlotLoopId());
                return;
            }
            
            java.util.UUID resolveChapterId;
            if (resolveChapterIdObj instanceof java.util.UUID) {
                resolveChapterId = (java.util.UUID) resolveChapterIdObj;
            } else {
                resolveChapterId = java.util.UUID.fromString(resolveChapterIdObj.toString());
            }
            
            boolean chapterExists = chapterRepository.existsById(resolveChapterId);
            if (!chapterExists) {
                log.warn("伏笔解决章节不存在: plotLoopId={}, chapterId={}", 
                        event.getPlotLoopId(), resolveChapterId);
                // 可以在这里创建一个警告
            } else {
                log.debug("伏笔解决章节验证通过: plotLoopId={}, chapterId={}", 
                        event.getPlotLoopId(), resolveChapterId);
            }
        } catch (Exception e) {
            log.error("验证伏笔解决章节失败: plotLoopId={}", event.getPlotLoopId(), e);
        }
    }

    /**
     * 清理关联数据
     */
    private void cleanupAssociatedData(PlotLoopChangedEvent event) {
        log.debug("清理伏笔关联数据: plotLoopId={}", event.getPlotLoopId());
        
        try {
            // 删除关联的一致性警告
            warningService.deleteWarningsByEntity(event.getPlotLoopId());
            log.debug("Deleted consistency warnings for PlotLoop: {}", event.getPlotLoopId());
        } catch (Exception e) {
            log.error("Failed to delete consistency warnings for PlotLoop: {}", event.getPlotLoopId(), e);
        }
    }
}
