package com.inkflow.module.evolution.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.inkflow.module.evolution.dto.StateChange;
import com.inkflow.module.evolution.entity.*;
import com.inkflow.module.evolution.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * 状态快照服务
 * 实现关键帧+增量策略
 */
@Service
public class StateSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(StateSnapshotService.class);
    private static final int KEYFRAME_INTERVAL = 10; // 每10个快照创建一个关键帧

    private final StateSnapshotRepository snapshotRepository;
    private final ChangeRecordRepository changeRecordRepository;
    private final EvolutionTimelineRepository timelineRepository;
    private final ObjectMapper objectMapper;

    public StateSnapshotService(
            StateSnapshotRepository snapshotRepository,
            ChangeRecordRepository changeRecordRepository,
            EvolutionTimelineRepository timelineRepository,
            ObjectMapper objectMapper) {
        this.snapshotRepository = snapshotRepository;
        this.changeRecordRepository = changeRecordRepository;
        this.timelineRepository = timelineRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建状态快照
     * 自动决定是关键帧还是增量帧
     */
    @Transactional
    public StateSnapshot createSnapshot(
            UUID timelineId,
            UUID chapterId,
            Integer chapterOrder,
            Map<String, Object> currentState,
            List<StateChange> changes,
            BigDecimal aiConfidence) {

        // 获取最新快照
        Optional<StateSnapshot> latestOpt = snapshotRepository.findLatestByTimelineId(timelineId);

        // 决定是否为关键帧
        boolean isKeyframe = shouldBeKeyframe(timelineId, latestOpt);

        // 构建快照数据
        Map<String, Object> stateData;
        ChangeType changeType;

        if (isKeyframe || latestOpt.isEmpty()) {
            // 关键帧：存储完整状态
            stateData = currentState;
            changeType = latestOpt.isEmpty() ? ChangeType.INITIAL : ChangeType.MAJOR_CHANGE;
        } else {
            // 增量帧：存储JSON diff
            stateData = computeDelta(latestOpt.get(), currentState);
            changeType = ChangeType.UPDATE;
        }

        // 生成变更摘要
        String changeSummary = generateChangeSummary(changes);

        // 创建快照
        StateSnapshot snapshot = StateSnapshot.builder()
                .timelineId(timelineId)
                .chapterId(chapterId)
                .chapterOrder(chapterOrder)
                .isKeyframe(isKeyframe || latestOpt.isEmpty())
                .stateData(stateData)
                .changeSummary(changeSummary)
                .changeType(changeType)
                .aiConfidence(aiConfidence)
                .build();

        snapshot = snapshotRepository.save(snapshot);

        // 保存变更记录
        if (changes != null && !changes.isEmpty()) {
            saveChangeRecords(snapshot.getId(), changes);
        }

        log.info("Created {} snapshot for timeline {} at chapter order {}",
                isKeyframe ? "keyframe" : "delta", timelineId, chapterOrder);

        return snapshot;
    }

    /**
     * 从关键帧+增量重建状态
     */
    @Transactional(readOnly = true)
    public Map<String, Object> reconstructState(UUID timelineId, Integer targetChapterOrder) {
        // 1. 找到最近的关键帧
        Optional<StateSnapshot> keyframeOpt = snapshotRepository
                .findNearestKeyframeBefore(timelineId, targetChapterOrder);

        if (keyframeOpt.isEmpty()) {
            log.warn("No keyframe found for timeline {} before chapter order {}",
                    timelineId, targetChapterOrder);
            return Collections.emptyMap();
        }

        StateSnapshot keyframe = keyframeOpt.get();
        Map<String, Object> state = new HashMap<>(keyframe.getStateData());

        // 2. 如果目标就是关键帧，直接返回
        if (keyframe.getChapterOrder().equals(targetChapterOrder)) {
            return state;
        }

        // 3. 应用增量
        List<StateSnapshot> deltas = snapshotRepository.findDeltasBetween(
                timelineId, keyframe.getChapterOrder(), targetChapterOrder);

        for (StateSnapshot delta : deltas) {
            applyDelta(state, delta.getStateData());
        }

        return state;
    }

    /**
     * 获取实体在指定章节的状态
     */
    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> getStateAtChapter(
            EntityType entityType, UUID entityId, Integer chapterOrder) {

        return timelineRepository.findByEntityTypeAndEntityId(entityType, entityId)
                .map(timeline -> reconstructState(timeline.getId(), chapterOrder));
    }

    /**
     * 判断是否应该创建关键帧
     */
    private boolean shouldBeKeyframe(UUID timelineId, Optional<StateSnapshot> latestOpt) {
        if (latestOpt.isEmpty()) {
            return true; // 第一个快照必须是关键帧
        }

        // 统计自上次关键帧以来的增量数量
        long snapshotCount = snapshotRepository.countByTimelineId(timelineId);
        long keyframeCount = snapshotRepository.countKeyframesByTimelineId(timelineId);

        // 每KEYFRAME_INTERVAL个快照创建一个关键帧
        return (snapshotCount - keyframeCount * KEYFRAME_INTERVAL) >= KEYFRAME_INTERVAL;
    }

    /**
     * 计算状态差异（JSON diff）
     */
    private Map<String, Object> computeDelta(StateSnapshot previous, Map<String, Object> current) {
        Map<String, Object> delta = new HashMap<>();
        Map<String, Object> previousState = reconstructState(
                previous.getTimelineId(), previous.getChapterOrder());

        // 简单的字段级diff
        for (Map.Entry<String, Object> entry : current.entrySet()) {
            String key = entry.getKey();
            Object newValue = entry.getValue();
            Object oldValue = previousState.get(key);

            if (!Objects.equals(oldValue, newValue)) {
                delta.put(key, newValue);
            }
        }

        // 检查删除的字段
        for (String key : previousState.keySet()) {
            if (!current.containsKey(key)) {
                delta.put(key, null); // null表示删除
            }
        }

        return delta;
    }

    /**
     * 应用增量到状态
     */
    private void applyDelta(Map<String, Object> state, Map<String, Object> delta) {
        for (Map.Entry<String, Object> entry : delta.entrySet()) {
            if (entry.getValue() == null) {
                state.remove(entry.getKey());
            } else {
                state.put(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * 生成变更摘要
     */
    private String generateChangeSummary(List<StateChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return "初始状态";
        }

        StringBuilder sb = new StringBuilder();
        for (StateChange change : changes) {
            if (!sb.isEmpty()) {
                sb.append("; ");
            }
            sb.append(change.fieldPath()).append(": ")
              .append(change.oldValue()).append(" -> ").append(change.newValue());
        }
        return sb.toString();
    }

    /**
     * 保存变更记录
     */
    private void saveChangeRecords(UUID snapshotId, List<StateChange> changes) {
        List<ChangeRecord> records = changes.stream()
                .map(change -> {
                    ChangeRecord record = new ChangeRecord(
                            snapshotId,
                            change.fieldPath(),
                            String.valueOf(change.oldValue()),
                            String.valueOf(change.newValue())
                    );
                    record.setChangeReason(change.changeReason());
                    record.setSourceText(change.sourceText());
                    return record;
                })
                .toList();

        changeRecordRepository.saveAll(records);
    }
}
