package com.inkflow.module.progress.service;

import com.inkflow.module.progress.dto.PhaseTransitionDto;
import com.inkflow.module.progress.entity.PhaseTransition;
import com.inkflow.module.progress.repository.PhaseTransitionRepository;
import com.inkflow.module.project.entity.CreationPhase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 阶段转换服务
 * 
 * Requirements: 6.3
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhaseTransitionService {

    private final PhaseTransitionRepository transitionRepository;

    /**
     * 记录阶段转换
     */
    @Transactional
    public PhaseTransition recordTransition(UUID projectId, CreationPhase fromPhase, 
            CreationPhase toPhase, String reason) {
        return recordTransition(projectId, fromPhase, toPhase, reason, "USER");
    }

    /**
     * 记录阶段转换（带触发者）
     */
    @Transactional
    public PhaseTransition recordTransition(UUID projectId, CreationPhase fromPhase, 
            CreationPhase toPhase, String reason, String triggeredBy) {
        PhaseTransition transition = PhaseTransition.builder()
                .projectId(projectId)
                .fromPhase(fromPhase)
                .toPhase(toPhase)
                .reason(reason)
                .triggeredBy(triggeredBy)
                .transitionedAt(LocalDateTime.now())
                .build();

        transition = transitionRepository.save(transition);
        log.info("记录阶段转换: projectId={}, {} -> {}, reason={}", 
                projectId, fromPhase, toPhase, reason);
        return transition;
    }

    /**
     * 获取转换历史（按时间倒序）
     */
    @Transactional(readOnly = true)
    public List<PhaseTransitionDto> getTransitionHistory(UUID projectId) {
        return transitionRepository.findByProjectIdOrderByTransitionedAtDesc(projectId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * 获取转换历史（按时间正序）
     */
    @Transactional(readOnly = true)
    public List<PhaseTransitionDto> getTransitionHistoryAsc(UUID projectId) {
        return transitionRepository.findByProjectIdOrderByTransitionedAtAsc(projectId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * 获取最近一次转换
     */
    @Transactional(readOnly = true)
    public PhaseTransitionDto getLatestTransition(UUID projectId) {
        return transitionRepository.findFirstByProjectIdOrderByTransitionedAtDesc(projectId)
                .map(this::toDto)
                .orElse(null);
    }

    /**
     * 获取转换次数
     */
    @Transactional(readOnly = true)
    public long countTransitions(UUID projectId) {
        return transitionRepository.countByProjectId(projectId);
    }

    /**
     * 删除项目的所有转换记录
     */
    @Transactional
    public int deleteByProjectId(UUID projectId) {
        int count = transitionRepository.deleteByProjectId(projectId);
        log.info("删除项目阶段转换记录: projectId={}, count={}", projectId, count);
        return count;
    }

    private PhaseTransitionDto toDto(PhaseTransition transition) {
        return PhaseTransitionDto.builder()
                .id(transition.getId())
                .projectId(transition.getProjectId())
                .fromPhase(transition.getFromPhase())
                .toPhase(transition.getToPhase())
                .reason(transition.getReason())
                .triggeredBy(transition.getTriggeredBy())
                .transitionedAt(transition.getTransitionedAt())
                .build();
    }
}
