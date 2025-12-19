package com.inkflow.module.consistency.service;

import com.inkflow.module.consistency.dto.ConsistencyWarningDto;
import com.inkflow.module.consistency.dto.CreateWarningRequest;
import com.inkflow.module.consistency.entity.ConsistencyWarning;
import com.inkflow.module.consistency.entity.ConsistencyWarning.Severity;
import com.inkflow.module.consistency.entity.ConsistencyWarning.WarningType;
import com.inkflow.module.consistency.repository.ConsistencyWarningRepository;
import com.inkflow.module.evolution.entity.EntityType;
import com.inkflow.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 一致性警告服务
 * 实现警告的创建、解决、忽略和批量操作
 *
 * @author zsg
 * @date 2025/12/17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsistencyWarningService {

    private final ConsistencyWarningRepository warningRepository;

    /**
     * 创建警告
     * 
     * Requirements: 8.1 - 存储警告，包含严重程度、受影响实体和建议解决方案
     */
    @Transactional
    public ConsistencyWarningDto createWarning(CreateWarningRequest request) {
        // 检查是否已存在相同的未解决警告（避免重复）
        if (request.entityId() != null && 
            warningRepository.existsByProjectIdAndEntityIdAndWarningTypeAndResolvedFalse(
                    request.projectId(), request.entityId(), request.warningType())) {
            log.debug("相同警告已存在，跳过创建: projectId={}, entityId={}, type={}", 
                    request.projectId(), request.entityId(), request.warningType());
            // 返回已存在的警告
            return warningRepository.findByProjectIdAndResolvedFalseOrderByCreatedAtDesc(request.projectId())
                    .stream()
                    .filter(w -> w.getEntityId() != null && w.getEntityId().equals(request.entityId()) 
                            && w.getWarningType() == request.warningType())
                    .findFirst()
                    .map(ConsistencyWarningDto::fromEntity)
                    .orElse(null);
        }
        
        ConsistencyWarning warning = ConsistencyWarning.builder()
                .projectId(request.projectId())
                .entityId(request.entityId())
                .entityType(request.entityType())
                .entityName(request.entityName())
                .warningType(request.warningType())
                .severity(request.severity())
                .description(request.description())
                .suggestion(request.suggestion())
                .fieldPath(request.fieldPath())
                .expectedValue(request.expectedValue())
                .actualValue(request.actualValue())
                .relatedEntityIds(request.relatedEntityIds())
                .suggestedResolution(request.suggestedResolution())
                .resolved(false)
                .dismissed(false)
                .build();
        
        ConsistencyWarning saved = warningRepository.save(warning);
        log.info("创建一致性警告: id={}, projectId={}, type={}, severity={}", 
                saved.getId(), saved.getProjectId(), saved.getWarningType(), saved.getSeverity());
        
        return ConsistencyWarningDto.fromEntity(saved);
    }

    /**
     * 获取警告详情
     */
    @Transactional(readOnly = true)
    public Optional<ConsistencyWarningDto> getWarning(UUID warningId) {
        return warningRepository.findById(warningId)
                .map(ConsistencyWarningDto::fromEntity);
    }

    /**
     * 获取项目的未解决警告
     */
    @Transactional(readOnly = true)
    public List<ConsistencyWarningDto> getUnresolvedWarnings(UUID projectId) {
        return warningRepository.findByProjectIdAndResolvedFalseOrderByCreatedAtDesc(projectId)
                .stream()
                .filter(w -> !w.isDismissed()) // 排除已忽略的
                .map(ConsistencyWarningDto::fromEntity)
                .toList();
    }

    /**
     * 获取项目的未解决警告数量
     */
    @Transactional(readOnly = true)
    public long getUnresolvedCount(UUID projectId) {
        return warningRepository.countByProjectIdAndResolvedFalse(projectId);
    }
    
    /**
     * 按严重程度获取未解决警告数量
     */
    @Transactional(readOnly = true)
    public long getUnresolvedCountBySeverity(UUID projectId, Severity severity) {
        return warningRepository.countByProjectIdAndSeverityAndResolvedFalse(projectId, severity);
    }

    /**
     * 解决警告
     */
    @Transactional
    public ConsistencyWarningDto resolveWarning(UUID warningId, String resolutionMethod) {
        ConsistencyWarning warning = warningRepository.findById(warningId)
                .orElseThrow(() -> new ResourceNotFoundException("警告不存在: " + warningId));
        
        if (warning.isResolved()) {
            log.debug("警告已解决，跳过: {}", warningId);
            return ConsistencyWarningDto.fromEntity(warning);
        }
        
        warning.setResolved(true);
        warning.setResolutionMethod(resolutionMethod);
        warning.setResolvedAt(LocalDateTime.now());
        
        ConsistencyWarning saved = warningRepository.save(warning);
        log.info("解决警告: id={}, method={}", warningId, resolutionMethod);
        
        return ConsistencyWarningDto.fromEntity(saved);
    }

    /**
     * 忽略警告
     */
    @Transactional
    public ConsistencyWarningDto dismissWarning(UUID warningId) {
        ConsistencyWarning warning = warningRepository.findById(warningId)
                .orElseThrow(() -> new ResourceNotFoundException("警告不存在: " + warningId));
        
        if (warning.isDismissed()) {
            log.debug("警告已忽略，跳过: {}", warningId);
            return ConsistencyWarningDto.fromEntity(warning);
        }
        
        warning.setDismissed(true);
        
        ConsistencyWarning saved = warningRepository.save(warning);
        log.info("忽略警告: id={}", warningId);
        
        return ConsistencyWarningDto.fromEntity(saved);
    }

    /**
     * 批量解决警告
     */
    @Transactional
    public int bulkResolve(List<UUID> warningIds, String resolutionMethod) {
        if (warningIds == null || warningIds.isEmpty()) {
            return 0;
        }
        
        int count = 0;
        for (UUID warningId : warningIds) {
            try {
                resolveWarning(warningId, resolutionMethod);
                count++;
            } catch (ResourceNotFoundException e) {
                log.warn("批量解决时警告不存在: {}", warningId);
            }
        }
        
        log.info("批量解决警告: count={}, method={}", count, resolutionMethod);
        return count;
    }

    /**
     * 批量忽略警告
     */
    @Transactional
    public int bulkDismiss(List<UUID> warningIds) {
        if (warningIds == null || warningIds.isEmpty()) {
            return 0;
        }
        
        int count = 0;
        for (UUID warningId : warningIds) {
            try {
                dismissWarning(warningId);
                count++;
            } catch (ResourceNotFoundException e) {
                log.warn("批量忽略时警告不存在: {}", warningId);
            }
        }
        
        log.info("批量忽略警告: count={}", count);
        return count;
    }

    /**
     * 按实体类型获取未解决警告
     */
    @Transactional(readOnly = true)
    public List<ConsistencyWarningDto> getUnresolvedWarningsByEntityType(UUID projectId, EntityType entityType) {
        return warningRepository.findByProjectIdAndEntityTypeAndResolvedFalse(projectId, entityType)
                .stream()
                .filter(w -> !w.isDismissed())
                .map(ConsistencyWarningDto::fromEntity)
                .toList();
    }

    /**
     * 按警告类型获取未解决警告
     */
    @Transactional(readOnly = true)
    public List<ConsistencyWarningDto> getUnresolvedWarningsByWarningType(UUID projectId, WarningType warningType) {
        return warningRepository.findByProjectIdAndWarningTypeAndResolvedFalse(projectId, warningType)
                .stream()
                .filter(w -> !w.isDismissed())
                .map(ConsistencyWarningDto::fromEntity)
                .toList();
    }

    /**
     * 获取实体的所有警告
     */
    @Transactional(readOnly = true)
    public List<ConsistencyWarningDto> getWarningsByEntity(UUID entityId) {
        return warningRepository.findByEntityIdOrderByCreatedAtDesc(entityId)
                .stream()
                .map(ConsistencyWarningDto::fromEntity)
                .toList();
    }

    /**
     * 解决实体的所有警告
     */
    @Transactional
    public int resolveWarningsByEntity(UUID entityId, String resolutionMethod) {
        int count = warningRepository.resolveByEntityId(entityId, LocalDateTime.now());
        log.info("解决实体的所有警告: entityId={}, count={}", entityId, count);
        return count;
    }

    /**
     * 删除实体的所有警告
     */
    @Transactional
    public void deleteWarningsByEntity(UUID entityId) {
        warningRepository.deleteByEntityId(entityId);
        log.info("删除实体的所有警告: entityId={}", entityId);
    }

    /**
     * 删除项目的所有警告
     */
    @Transactional
    public void deleteWarningsByProject(UUID projectId) {
        warningRepository.deleteByProjectId(projectId);
        log.info("删除项目的所有警告: projectId={}", projectId);
    }

    /**
     * 清理已解决的旧警告
     */
    @Transactional
    public int cleanupResolvedWarnings(int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int count = warningRepository.deleteResolvedOlderThan(cutoff);
        log.info("清理已解决的旧警告: retentionDays={}, count={}", retentionDays, count);
        return count;
    }
}
