package com.inkflow.module.content.service;

import com.inkflow.common.exception.ResourceNotFoundException;
import com.inkflow.module.content.dto.*;
import com.inkflow.module.content.entity.Volume;
import com.inkflow.module.content.repository.ChapterRepository;
import com.inkflow.module.content.repository.VolumeRepository;
import com.inkflow.module.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 分卷服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VolumeService {

    private final VolumeRepository volumeRepository;
    private final ChapterRepository chapterRepository;
    private final ProjectRepository projectRepository;

    /**
     * 获取项目的所有分卷
     */
    @Transactional(readOnly = true)
    public List<VolumeDto> list(UUID projectId, UUID userId) {
        verifyProjectAccess(projectId, userId);
        return volumeRepository.findByProjectIdAndDeletedFalseOrderByOrderIndexAsc(projectId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * 获取单个分卷
     */
    @Transactional(readOnly = true)
    public VolumeDto getById(UUID projectId, UUID volumeId, UUID userId) {
        verifyProjectAccess(projectId, userId);
        Volume volume = volumeRepository.findByIdAndProjectIdAndDeletedFalse(volumeId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("分卷不存在"));
        return toDto(volume);
    }

    /**
     * 创建分卷
     */
    @Transactional
    public VolumeDto create(UUID projectId, UUID userId, CreateVolumeRequest request) {
        verifyProjectAccess(projectId, userId);
        
        int maxOrder = volumeRepository.findMaxOrderIndex(projectId);
        
        Volume volume = new Volume();
        volume.setProjectId(projectId);
        volume.setTitle(request.title());
        volume.setDescription(request.description());
        volume.setOrderIndex(maxOrder + 1);
        
        volume = volumeRepository.save(volume);
        log.info("创建分卷: projectId={}, volumeId={}, title={}", projectId, volume.getId(), request.title());
        
        return toDto(volume);
    }

    /**
     * 更新分卷
     */
    @Transactional
    public VolumeDto update(UUID projectId, UUID volumeId, UUID userId, UpdateVolumeRequest request) {
        verifyProjectAccess(projectId, userId);
        
        Volume volume = volumeRepository.findByIdAndProjectIdAndDeletedFalse(volumeId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("分卷不存在"));
        
        if (request.title() != null) {
            volume.setTitle(request.title());
        }
        if (request.description() != null) {
            volume.setDescription(request.description());
        }
        
        volume = volumeRepository.save(volume);
        log.info("更新分卷: volumeId={}", volumeId);
        
        return toDto(volume);
    }

    /**
     * 删除分卷（软删除）
     */
    @Transactional
    public void delete(UUID projectId, UUID volumeId, UUID userId) {
        verifyProjectAccess(projectId, userId);
        
        Volume volume = volumeRepository.findByIdAndProjectIdAndDeletedFalse(volumeId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("分卷不存在"));
        
        volume.setDeleted(true);
        volumeRepository.save(volume);
        log.info("删除分卷: volumeId={}", volumeId);
    }

    /**
     * 重排序分卷
     */
    @Transactional
    public void reorder(UUID projectId, UUID userId, List<ReorderRequest> requests) {
        verifyProjectAccess(projectId, userId);
        
        for (ReorderRequest request : requests) {
            Volume volume = volumeRepository.findByIdAndProjectIdAndDeletedFalse(request.id(), projectId)
                    .orElseThrow(() -> new ResourceNotFoundException("分卷不存在: " + request.id()));
            volume.setOrderIndex(request.newOrder());
            volumeRepository.save(volume);
        }
        log.info("重排序分卷: projectId={}, count={}", projectId, requests.size());
    }

    private void verifyProjectAccess(UUID projectId, UUID userId) {
        boolean exists = projectRepository.existsByIdAndUserIdAndDeletedFalse(projectId, userId);
        if (!exists) {
            throw new ResourceNotFoundException("项目不存在或无权访问");
        }
    }

    private VolumeDto toDto(Volume volume) {
        int chapterCount = (int) chapterRepository.countByVolumeIdAndDeletedFalse(volume.getId());
        Long wordCount = chapterRepository.sumWordCountByVolumeId(volume.getId());
        return new VolumeDto(
                volume.getId(),
                volume.getProjectId(),
                volume.getTitle(),
                volume.getDescription(),
                volume.getOrderIndex(),
                chapterCount,
                wordCount,
                volume.getCreatedAt(),
                volume.getUpdatedAt()
        );
    }
}
