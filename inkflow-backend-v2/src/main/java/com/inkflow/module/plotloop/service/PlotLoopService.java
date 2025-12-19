package com.inkflow.module.plotloop.service;

import com.inkflow.common.exception.BusinessException;
import com.inkflow.common.exception.ResourceNotFoundException;
import com.inkflow.module.plotloop.dto.*;
import com.inkflow.module.plotloop.entity.PlotLoop;
import com.inkflow.module.plotloop.entity.PlotLoopStatus;
import com.inkflow.module.plotloop.event.PlotLoopChangedEvent;
import com.inkflow.module.plotloop.repository.PlotLoopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 伏笔服务
 * 
 * 提供伏笔的CRUD操作和状态管理功能
 */
@Service
@Transactional
public class PlotLoopService {

    private static final Logger log = LoggerFactory.getLogger(PlotLoopService.class);

    private final PlotLoopRepository plotLoopRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PlotLoopService(PlotLoopRepository plotLoopRepository,
                          ApplicationEventPublisher eventPublisher) {
        this.plotLoopRepository = plotLoopRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 创建伏笔
     */
    public PlotLoopDto create(CreatePlotLoopRequest request) {
        PlotLoop plotLoop = new PlotLoop();
        plotLoop.setProjectId(request.projectId());
        plotLoop.setTitle(request.title());
        plotLoop.setDescription(request.description());
        plotLoop.setIntroChapterId(request.introChapterId());
        plotLoop.setIntroChapterOrder(request.introChapterOrder());
        plotLoop.setStatus(PlotLoopStatus.OPEN);

        PlotLoop saved = plotLoopRepository.save(plotLoop);
        log.info("创建伏笔: {} (项目: {})", saved.getTitle(), saved.getProjectId());
        
        // 发布伏笔创建事件
        eventPublisher.publishEvent(PlotLoopChangedEvent.created(
                this, saved.getProjectId(), saved.getId(), saved.getTitle(), buildStateMap(saved)));
        
        return PlotLoopDto.from(saved);
    }

    /**
     * 根据ID查询伏笔
     */
    @Transactional(readOnly = true)
    public PlotLoopDto findById(UUID id) {
        PlotLoop plotLoop = plotLoopRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("伏笔不存在: " + id));
        return PlotLoopDto.from(plotLoop);
    }

    /**
     * 根据项目ID查询所有伏笔
     */
    @Transactional(readOnly = true)
    public List<PlotLoopDto> findByProjectId(UUID projectId) {
        return plotLoopRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
            .stream()
            .map(PlotLoopDto::from)
            .toList();
    }

    /**
     * 根据项目ID和状态查询
     */
    @Transactional(readOnly = true)
    public List<PlotLoopDto> findByStatus(UUID projectId, PlotLoopStatus status) {
        return plotLoopRepository.findByProjectIdAndStatus(projectId, status)
            .stream()
            .map(PlotLoopDto::from)
            .toList();
    }

    /**
     * 查询开放和紧急的伏笔 (用于AI上下文)
     */
    @Transactional(readOnly = true)
    public List<PlotLoopDto> findOpenAndUrgent(UUID projectId) {
        return plotLoopRepository.findOpenAndUrgent(projectId)
            .stream()
            .map(PlotLoopDto::from)
            .toList();
    }

    /**
     * 搜索伏笔
     */
    @Transactional(readOnly = true)
    public List<PlotLoopDto> search(UUID projectId, String keyword) {
        return plotLoopRepository.searchByKeyword(projectId, keyword)
            .stream()
            .map(PlotLoopDto::from)
            .toList();
    }

    /**
     * 更新伏笔
     */
    public PlotLoopDto update(UUID id, String title, String description) {
        PlotLoop plotLoop = plotLoopRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("伏笔不存在: " + id));

        if (title != null) {
            plotLoop.setTitle(title);
        }
        if (description != null) {
            plotLoop.setDescription(description);
        }

        PlotLoop saved = plotLoopRepository.save(plotLoop);
        log.info("更新伏笔: {} (ID: {})", saved.getTitle(), saved.getId());
        
        return PlotLoopDto.from(saved);
    }

    /**
     * 解决伏笔
     */
    public PlotLoopDto resolve(UUID id, UUID chapterId, Integer chapterOrder) {
        PlotLoop plotLoop = plotLoopRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("伏笔不存在: " + id));

        if (plotLoop.getStatus() == PlotLoopStatus.CLOSED) {
            throw new BusinessException("伏笔已经被解决");
        }

        PlotLoopStatus previousStatus = plotLoop.getStatus();
        plotLoop.resolve(chapterId, chapterOrder);
        PlotLoop saved = plotLoopRepository.save(plotLoop);
        
        log.info("解决伏笔: {} (章节: {})", saved.getTitle(), chapterId);
        
        // 发布伏笔状态变更事件
        eventPublisher.publishEvent(PlotLoopChangedEvent.statusChanged(
                this, saved.getProjectId(), saved.getId(), saved.getTitle(),
                saved.getStatus(), previousStatus, buildStateMap(saved)));
        
        return PlotLoopDto.from(saved);
    }

    /**
     * 放弃伏笔
     */
    public PlotLoopDto abandon(UUID id, String reason) {
        PlotLoop plotLoop = plotLoopRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("伏笔不存在: " + id));

        if (plotLoop.getStatus() == PlotLoopStatus.CLOSED) {
            throw new BusinessException("已解决的伏笔不能放弃");
        }

        PlotLoopStatus previousStatus = plotLoop.getStatus();
        plotLoop.abandon(reason);
        PlotLoop saved = plotLoopRepository.save(plotLoop);
        
        log.info("放弃伏笔: {} (原因: {})", saved.getTitle(), reason);
        
        // 发布伏笔状态变更事件
        eventPublisher.publishEvent(PlotLoopChangedEvent.statusChanged(
                this, saved.getProjectId(), saved.getId(), saved.getTitle(),
                saved.getStatus(), previousStatus, buildStateMap(saved)));
        
        return PlotLoopDto.from(saved);
    }

    /**
     * 重新打开伏笔
     */
    public PlotLoopDto reopen(UUID id) {
        PlotLoop plotLoop = plotLoopRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("伏笔不存在: " + id));

        plotLoop.reopen();
        PlotLoop saved = plotLoopRepository.save(plotLoop);
        
        log.info("重新打开伏笔: {}", saved.getTitle());
        return PlotLoopDto.from(saved);
    }

    /**
     * 检查并更新紧急状态
     * 
     * 将超过10章未回收的伏笔标记为紧急
     */
    public int checkAndUpdateUrgentStatus(UUID projectId, int currentChapterOrder) {
        List<PlotLoop> shouldBeUrgent = plotLoopRepository.findShouldBeUrgent(projectId, currentChapterOrder);
        
        for (PlotLoop plotLoop : shouldBeUrgent) {
            plotLoop.markAsUrgent();
            plotLoopRepository.save(plotLoop);
            log.info("伏笔标记为紧急: {} (已超过10章)", plotLoop.getTitle());
        }

        return shouldBeUrgent.size();
    }

    /**
     * 删除伏笔 (软删除)
     */
    public void delete(UUID id) {
        PlotLoop plotLoop = plotLoopRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("伏笔不存在: " + id));

        UUID projectId = plotLoop.getProjectId();
        String title = plotLoop.getTitle();
        
        plotLoop.softDelete();
        plotLoopRepository.save(plotLoop);
        
        log.info("删除伏笔: {} (ID: {})", title, id);
        
        // 发布伏笔删除事件
        eventPublisher.publishEvent(PlotLoopChangedEvent.deleted(this, projectId, id, title));
    }

    /**
     * 获取伏笔统计
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getStatistics(UUID projectId) {
        return Map.of(
            "total", plotLoopRepository.countByProjectId(projectId),
            "open", plotLoopRepository.countByProjectIdAndStatus(projectId, PlotLoopStatus.OPEN),
            "urgent", plotLoopRepository.countByProjectIdAndStatus(projectId, PlotLoopStatus.URGENT),
            "closed", plotLoopRepository.countByProjectIdAndStatus(projectId, PlotLoopStatus.CLOSED),
            "abandoned", plotLoopRepository.countByProjectIdAndStatus(projectId, PlotLoopStatus.ABANDONED)
        );
    }

    /**
     * 生成伏笔上下文 (用于AI提示词)
     */
    @Transactional(readOnly = true)
    public String generateContextForAI(UUID projectId) {
        List<PlotLoop> openAndUrgent = plotLoopRepository.findOpenAndUrgent(projectId);
        
        if (openAndUrgent.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("【待回收伏笔】\n");

        List<PlotLoop> urgent = openAndUrgent.stream()
            .filter(p -> p.getStatus() == PlotLoopStatus.URGENT)
            .toList();
        
        List<PlotLoop> open = openAndUrgent.stream()
            .filter(p -> p.getStatus() == PlotLoopStatus.OPEN)
            .toList();

        if (!urgent.isEmpty()) {
            context.append("⚠️ 紧急伏笔（超过10章未回收）:\n");
            for (PlotLoop p : urgent) {
                context.append("- ").append(p.getTitle());
                if (p.getDescription() != null) {
                    context.append(": ").append(p.getDescription());
                }
                context.append("\n");
            }
        }

        if (!open.isEmpty()) {
            context.append("📌 开放伏笔:\n");
            for (PlotLoop p : open) {
                context.append("- ").append(p.getTitle());
                if (p.getDescription() != null) {
                    context.append(": ").append(p.getDescription());
                }
                context.append("\n");
            }
        }

        return context.toString();
    }

    /**
     * 构建伏笔状态映射（用于演进快照）
     */
    private Map<String, Object> buildStateMap(PlotLoop plotLoop) {
        Map<String, Object> state = new HashMap<>();
        state.put("title", plotLoop.getTitle());
        state.put("description", plotLoop.getDescription());
        state.put("status", plotLoop.getStatus() != null ? plotLoop.getStatus().name() : null);
        state.put("introChapterId", plotLoop.getIntroChapterId());
        state.put("introChapterOrder", plotLoop.getIntroChapterOrder());
        state.put("resolveChapterId", plotLoop.getResolutionChapterId());
        state.put("resolveChapterOrder", plotLoop.getResolutionChapterOrder());
        return state;
    }
}
