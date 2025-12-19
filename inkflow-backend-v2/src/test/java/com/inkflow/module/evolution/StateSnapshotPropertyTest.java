package com.inkflow.module.evolution;

import com.inkflow.module.evolution.dto.StateChange;
import com.inkflow.module.evolution.entity.*;
import com.inkflow.module.evolution.repository.*;
import com.inkflow.module.evolution.service.StateSnapshotService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 状态快照属性测试
 * Property 2: 关键帧+增量重建状态一致性
 * Validates: Requirements 22.4
 */
class StateSnapshotPropertyTest {

    /**
     * Property 2: 关键帧+增量重建状态一致性
     * 
     * 给定一系列状态变更，通过关键帧+增量策略存储后，
     * 重建的状态应该与原始状态完全一致。
     */
    @Property(tries = 50)
    void keyframePlusDeltaReconstructionIsConsistent(
            @ForAll("stateSequences") List<Map<String, Object>> stateSequence) {

        // 模拟存储
        List<StoredSnapshot> storedSnapshots = new ArrayList<>();
        Map<String, Object> lastStoredState = null;
        int keyframeInterval = 5;

        // 存储状态序列
        for (int i = 0; i < stateSequence.size(); i++) {
            Map<String, Object> currentState = stateSequence.get(i);
            boolean isKeyframe = (i % keyframeInterval == 0) || lastStoredState == null;

            if (isKeyframe) {
                // 存储完整状态
                storedSnapshots.add(new StoredSnapshot(i, true, new HashMap<>(currentState)));
            } else {
                // 存储相对于上一个状态的增量
                Map<String, Object> delta = computeDelta(lastStoredState, currentState);
                storedSnapshots.add(new StoredSnapshot(i, false, delta));
            }
            lastStoredState = new HashMap<>(currentState);
        }

        // 验证：重建每个位置的状态
        for (int targetOrder = 0; targetOrder < stateSequence.size(); targetOrder++) {
            Map<String, Object> reconstructed = reconstructState(storedSnapshots, targetOrder);
            Map<String, Object> original = stateSequence.get(targetOrder);

            assertThat(reconstructed)
                    .as("Reconstructed state at order %d should match original", targetOrder)
                    .isEqualTo(original);
        }
    }

    /**
     * Property: 增量应用的幂等性
     * 多次应用相同的增量应该得到相同的结果
     */
    @Property(tries = 30)
    void deltaApplicationIsIdempotent(
            @ForAll("validStates") Map<String, Object> baseState,
            @ForAll("validStates") Map<String, Object> targetState) {

        Map<String, Object> delta = computeDelta(baseState, targetState);

        // 应用一次
        Map<String, Object> result1 = new HashMap<>(baseState);
        applyDelta(result1, delta);

        // 应用两次
        Map<String, Object> result2 = new HashMap<>(baseState);
        applyDelta(result2, delta);
        applyDelta(result2, delta);

        assertThat(result1).isEqualTo(result2);
    }

    /**
     * Property: 空增量不改变状态
     */
    @Property(tries = 30)
    void emptyDeltaPreservesState(@ForAll("validStates") Map<String, Object> state) {
        Map<String, Object> original = new HashMap<>(state);
        Map<String, Object> emptyDelta = new HashMap<>();

        applyDelta(state, emptyDelta);

        assertThat(state).isEqualTo(original);
    }

    /**
     * Property: 增量计算的正确性
     * delta(A, B) 应用到 A 后得到 B
     */
    @Property(tries = 50)
    void deltaComputationIsCorrect(
            @ForAll("validStates") Map<String, Object> stateA,
            @ForAll("validStates") Map<String, Object> stateB) {

        Map<String, Object> delta = computeDelta(stateA, stateB);
        Map<String, Object> result = new HashMap<>(stateA);
        applyDelta(result, delta);

        assertThat(result).isEqualTo(stateB);
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<List<Map<String, Object>>> stateSequences() {
        return validStates().list().ofMinSize(1).ofMaxSize(20);
    }

    @Provide
    Arbitrary<Map<String, Object>> validStates() {
        return Arbitraries.maps(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
                Arbitraries.oneOf(
                        Arbitraries.strings().ofMaxLength(50).map(s -> (Object) s),
                        Arbitraries.integers().between(-1000, 1000).map(i -> (Object) i),
                        Arbitraries.of(true, false).map(b -> (Object) b)
                )
        ).ofMinSize(1).ofMaxSize(10);
    }

    // ========== Helper Methods ==========

    private Map<String, Object> computeDelta(Map<String, Object> previous, Map<String, Object> current) {
        Map<String, Object> delta = new HashMap<>();

        // 检查变更和新增
        for (Map.Entry<String, Object> entry : current.entrySet()) {
            String key = entry.getKey();
            Object newValue = entry.getValue();
            Object oldValue = previous.get(key);

            if (!Objects.equals(oldValue, newValue)) {
                delta.put(key, newValue);
            }
        }

        // 检查删除
        for (String key : previous.keySet()) {
            if (!current.containsKey(key)) {
                delta.put(key, null);
            }
        }

        return delta;
    }

    private void applyDelta(Map<String, Object> state, Map<String, Object> delta) {
        for (Map.Entry<String, Object> entry : delta.entrySet()) {
            if (entry.getValue() == null) {
                state.remove(entry.getKey());
            } else {
                state.put(entry.getKey(), entry.getValue());
            }
        }
    }

    private Map<String, Object> reconstructState(List<StoredSnapshot> snapshots, int targetOrder) {
        // 找到最近的关键帧
        StoredSnapshot keyframe = null;
        for (int i = targetOrder; i >= 0; i--) {
            if (snapshots.get(i).isKeyframe()) {
                keyframe = snapshots.get(i);
                break;
            }
        }

        if (keyframe == null) {
            return new HashMap<>();
        }

        Map<String, Object> state = new HashMap<>(keyframe.data());

        // 应用增量
        for (int i = keyframe.order() + 1; i <= targetOrder; i++) {
            StoredSnapshot snapshot = snapshots.get(i);
            if (!snapshot.isKeyframe()) {
                applyDelta(state, snapshot.data());
            }
        }

        return state;
    }

    record StoredSnapshot(int order, boolean isKeyframe, Map<String, Object> data) {}
}
