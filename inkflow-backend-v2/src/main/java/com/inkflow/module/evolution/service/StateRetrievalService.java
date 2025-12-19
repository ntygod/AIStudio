package com.inkflow.module.evolution.service;

import com.inkflow.module.evolution.dto.StateSnapshotDto;
import com.inkflow.module.evolution.dto.StateChange;
import com.inkflow.module.evolution.dto.ChangeRecordDto;
import com.inkflow.module.evolution.entity.*;
import com.inkflow.module.evolution.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 状态检索服务
 * 提供实体在任意时间点的状态查询
 */
@Service
public class StateRetrievalService {

    private final StateSnapshotService snapshotService;
    private final StateSnapshotRepository snapshotRepository;
    private final ChangeRecordRepository changeRecordRepository;
    private final EvolutionTimelineRepository timelineRepository;

    public StateRetrievalService(
            StateSnapshotService snapshotService,
            StateSnapshotRepository snapshotRepository,
            ChangeRecordRepository changeRecordRepository,
            EvolutionTimelineRepository timelineRepository) {
        this.snapshotService = snapshotService;
        this.snapshotRepository = snapshotRepository;
        this.changeRecordRepository = changeRecordRepository;
        this.timelineRepository = timelineRepository;
    }

    /**
     * 获取实体在指定章节时的状态
     */
    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> getStateAtChapter(
            EntityType entityType, UUID entityId, Integer chapterOrder) {
        return snapshotService.getStateAtChapter(entityType, entityId, chapterOrder);
    }

    /**
     * 获取实体的所有状态快照
     */
    @Transactional(readOnly = true)
    public List<StateSnapshotDto> getAllSnapshots(EntityType entityType, UUID entityId) {
        return timelineRepository.findByEntityTypeAndEntityId(entityType, entityId)
                .map(timeline -> {
                    List<StateSnapshot> snapshots = snapshotRepository
                            .findByTimelineIdOrderByChapterOrderAsc(timeline.getId());
                    return snapshots.stream()
                            .map(StateSnapshotDto::from)
                            .toList();
                })
                .orElse(Collections.emptyList());
    }

    /**
     * 获取快照详情（包含变更记录）
     */
    @Transactional(readOnly = true)
    public Optional<StateSnapshotDto> getSnapshotWithDetails(UUID snapshotId) {
        return snapshotRepository.findById(snapshotId)
                .map(snapshot -> {
                    List<ChangeRecordDto> records = changeRecordRepository
                            .findBySnapshotIdOrderByCreatedAtAsc(snapshot.getId())
                            .stream()
                            .map(ChangeRecordDto::from)
                            .toList();
                    return StateSnapshotDto.from(snapshot, records);
                });
    }

    /**
     * 获取实体的最新状态
     */
    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> getLatestState(EntityType entityType, UUID entityId) {
        return timelineRepository.findByEntityTypeAndEntityId(entityType, entityId)
                .flatMap(timeline -> snapshotRepository.findLatestByTimelineId(timeline.getId()))
                .map(snapshot -> {
                    if (snapshot.getIsKeyframe()) {
                        return snapshot.getStateData();
                    }
                    return snapshotService.reconstructState(
                            snapshot.getTimelineId(), snapshot.getChapterOrder());
                });
    }

    /**
     * 比较两个章节之间的状态差异
     */
    @Transactional(readOnly = true)
    public Map<String, StateChange> compareStates(
            EntityType entityType, UUID entityId,
            Integer fromChapterOrder, Integer toChapterOrder) {

        Optional<Map<String, Object>> fromStateOpt = getStateAtChapter(entityType, entityId, fromChapterOrder);
        Optional<Map<String, Object>> toStateOpt = getStateAtChapter(entityType, entityId, toChapterOrder);

        if (fromStateOpt.isEmpty() || toStateOpt.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> fromState = fromStateOpt.get();
        Map<String, Object> toState = toStateOpt.get();

        Map<String, StateChange> changes = new HashMap<>();

        // 检查变更和新增
        for (Map.Entry<String, Object> entry : toState.entrySet()) {
            String key = entry.getKey();
            Object newValue = entry.getValue();
            Object oldValue = fromState.get(key);

            if (!Objects.equals(oldValue, newValue)) {
                changes.put(key, StateChange.of(key, oldValue, newValue));
            }
        }

        // 检查删除
        for (String key : fromState.keySet()) {
            if (!toState.containsKey(key)) {
                changes.put(key, StateChange.of(key, fromState.get(key), null));
            }
        }

        return changes;
    }

    /**
     * 获取实体在章节范围内的演进轨迹
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getEvolutionTrack(
            EntityType entityType, UUID entityId,
            Integer fromChapterOrder, Integer toChapterOrder) {

        return timelineRepository.findByEntityTypeAndEntityId(entityType, entityId)
                .map(timeline -> {
                    List<StateSnapshot> snapshots = snapshotRepository
                            .findByTimelineIdOrderByChapterOrderAsc(timeline.getId())
                            .stream()
                            .filter(s -> s.getChapterOrder() >= fromChapterOrder
                                    && s.getChapterOrder() <= toChapterOrder)
                            .toList();

                    return snapshots.stream()
                            .map(snapshot -> {
                                Map<String, Object> state = snapshotService.reconstructState(
                                        timeline.getId(), snapshot.getChapterOrder());
                                state.put("_chapterOrder", snapshot.getChapterOrder());
                                state.put("_changeSummary", snapshot.getChangeSummary());
                                return state;
                            })
                            .toList();
                })
                .orElse(Collections.emptyList());
    }
}
