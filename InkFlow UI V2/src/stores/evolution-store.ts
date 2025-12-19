/**
 * 演进时间线状态管理
 * 使用 Zustand 管理演进时间线数据
 * 
 * Requirements: 8.1
 */

import { create } from 'zustand';
import {
  evolutionService,
  type StateSnapshot,
  type StateCompareResult,
  type EvolutionEntityType,
} from '@/services/evolution-service';

interface EvolutionState {
  // 当前选中的实体
  selectedEntityId: string | null;
  selectedEntityType: EvolutionEntityType | null;
  selectedEntityName: string | null;
  // 快照数据
  snapshots: StateSnapshot[];
  // 选中的快照（用于详情查看）
  selectedSnapshot: StateSnapshot | null;
  // 比较状态
  compareFromSnapshot: StateSnapshot | null;
  compareToSnapshot: StateSnapshot | null;
  compareResult: StateCompareResult | null;
  // 加载状态
  isLoading: boolean;
  isComparing: boolean;
  isCreatingSnapshot: boolean;
  error: string | null;
  // 面板状态
  isTimelineOpen: boolean;
  isCompareDialogOpen: boolean;
  isCreateSnapshotDialogOpen: boolean;
  // 缓存
  cacheTimestamp: number | null;
}

interface EvolutionActions {
  // 实体选择
  selectEntity(entityId: string, entityType: EvolutionEntityType, entityName: string): void;
  clearSelection(): void;
  // 数据加载
  fetchSnapshots(forceRefresh?: boolean): Promise<void>;
  fetchSnapshotDetails(snapshotId: string): Promise<void>;
  // 快照选择
  selectSnapshot(snapshot: StateSnapshot | null): void;
  // 快照创建
  createSnapshot(description: string, chapterId?: string, isKeyframe?: boolean): Promise<StateSnapshot>;
  // 比较功能
  setCompareFrom(snapshot: StateSnapshot | null): void;
  setCompareTo(snapshot: StateSnapshot | null): void;
  compareSnapshots(): Promise<void>;
  clearCompare(): void;
  // 面板控制
  openTimeline(): void;
  closeTimeline(): void;
  toggleTimeline(): void;
  openCompareDialog(): void;
  closeCompareDialog(): void;
  openCreateSnapshotDialog(): void;
  closeCreateSnapshotDialog(): void;
  // 通用
  clearError(): void;
  invalidateCache(): void;
  // SSE 事件处理
  addSnapshotFromSSE(snapshot: StateSnapshot): void;
}

type EvolutionStore = EvolutionState & EvolutionActions;

// 缓存有效期（5分钟）
const CACHE_TTL = 5 * 60 * 1000;

const isCacheValid = (timestamp: number | null): boolean => {
  if (!timestamp) return false;
  return Date.now() - timestamp < CACHE_TTL;
};

export const useEvolutionStore = create<EvolutionStore>()((set, get) => ({
  // 初始状态
  selectedEntityId: null,
  selectedEntityType: null,
  selectedEntityName: null,
  snapshots: [],
  selectedSnapshot: null,
  compareFromSnapshot: null,
  compareToSnapshot: null,
  compareResult: null,
  isLoading: false,
  isComparing: false,
  isCreatingSnapshot: false,
  error: null,
  isTimelineOpen: false,
  isCompareDialogOpen: false,
  isCreateSnapshotDialogOpen: false,
  cacheTimestamp: null,

  // ============ 实体选择 ============

  selectEntity: (entityId, entityType, entityName) => {
    const state = get();
    // 如果选择了不同的实体，清除缓存
    if (state.selectedEntityId !== entityId) {
      set({
        selectedEntityId: entityId,
        selectedEntityType: entityType,
        selectedEntityName: entityName,
        snapshots: [],
        selectedSnapshot: null,
        compareFromSnapshot: null,
        compareToSnapshot: null,
        compareResult: null,
        cacheTimestamp: null,
      });
    }
  },

  clearSelection: () => {
    set({
      selectedEntityId: null,
      selectedEntityType: null,
      selectedEntityName: null,
      snapshots: [],
      selectedSnapshot: null,
      compareFromSnapshot: null,
      compareToSnapshot: null,
      compareResult: null,
      cacheTimestamp: null,
      isTimelineOpen: false,
    });
  },

  // ============ 数据加载 ============

  fetchSnapshots: async (forceRefresh = false) => {
    const state = get();

    if (!state.selectedEntityId || !state.selectedEntityType) {
      return;
    }

    if (!forceRefresh && isCacheValid(state.cacheTimestamp) && state.snapshots.length > 0) {
      return;
    }

    set({ isLoading: true, error: null });

    try {
      const snapshots = await evolutionService.getSnapshots(
        state.selectedEntityType,
        state.selectedEntityId
      );

      // 按章节顺序排序
      const sortedSnapshots = snapshots.sort((a, b) => {
        const orderA = a.chapterOrder ?? 0;
        const orderB = b.chapterOrder ?? 0;
        return orderA - orderB;
      });

      set({
        snapshots: sortedSnapshots,
        isLoading: false,
        cacheTimestamp: Date.now(),
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : '获取演进快照失败';
      set({ isLoading: false, error: message });
      throw error;
    }
  },

  fetchSnapshotDetails: async (snapshotId: string) => {
    set({ isLoading: true, error: null });

    try {
      const snapshot = await evolutionService.getSnapshotDetails(snapshotId);
      set({
        selectedSnapshot: snapshot,
        isLoading: false,
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : '获取快照详情失败';
      set({ isLoading: false, error: message });
      throw error;
    }
  },

  // ============ 快照选择 ============

  selectSnapshot: (snapshot) => {
    set({ selectedSnapshot: snapshot });
  },

  // ============ 快照创建 ============

  createSnapshot: async (description, chapterId, isKeyframe = false) => {
    const state = get();

    if (!state.selectedEntityId || !state.selectedEntityType) {
      throw new Error('请先选择一个实体');
    }

    set({ isCreatingSnapshot: true, error: null });

    try {
      const snapshot = await evolutionService.createSnapshot({
        entityType: state.selectedEntityType,
        entityId: state.selectedEntityId,
        description,
        chapterId,
        isKeyframe,
      });

      // 添加到快照列表并重新排序
      set((state) => {
        const newSnapshots = [...state.snapshots, snapshot].sort((a, b) => {
          const orderA = a.chapterOrder ?? 0;
          const orderB = b.chapterOrder ?? 0;
          return orderA - orderB;
        });

        return {
          snapshots: newSnapshots,
          isCreatingSnapshot: false,
          isCreateSnapshotDialogOpen: false,
        };
      });

      return snapshot;
    } catch (error) {
      const message = error instanceof Error ? error.message : '创建快照失败';
      set({ isCreatingSnapshot: false, error: message });
      throw error;
    }
  },

  // ============ 比较功能 ============

  setCompareFrom: (snapshot) => {
    set({ compareFromSnapshot: snapshot, compareResult: null });
  },

  setCompareTo: (snapshot) => {
    set({ compareToSnapshot: snapshot, compareResult: null });
  },

  compareSnapshots: async () => {
    const state = get();

    if (
      !state.selectedEntityId ||
      !state.selectedEntityType ||
      !state.compareFromSnapshot ||
      !state.compareToSnapshot
    ) {
      return;
    }

    const fromOrder = state.compareFromSnapshot.chapterOrder ?? 0;
    const toOrder = state.compareToSnapshot.chapterOrder ?? 0;

    set({ isComparing: true, error: null });

    try {
      const result = await evolutionService.compareStates(
        state.selectedEntityType,
        state.selectedEntityId,
        fromOrder,
        toOrder
      );

      set({
        compareResult: result,
        isComparing: false,
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : '比较状态失败';
      set({ isComparing: false, error: message });
      throw error;
    }
  },

  clearCompare: () => {
    set({
      compareFromSnapshot: null,
      compareToSnapshot: null,
      compareResult: null,
    });
  },

  // ============ 面板控制 ============

  openTimeline: () => set({ isTimelineOpen: true }),
  closeTimeline: () => set({ isTimelineOpen: false }),
  toggleTimeline: () => set((state) => ({ isTimelineOpen: !state.isTimelineOpen })),
  openCompareDialog: () => set({ isCompareDialogOpen: true }),
  closeCompareDialog: () => set({ isCompareDialogOpen: false, compareResult: null }),
  openCreateSnapshotDialog: () => set({ isCreateSnapshotDialogOpen: true }),
  closeCreateSnapshotDialog: () => set({ isCreateSnapshotDialogOpen: false }),

  // ============ 通用 ============

  clearError: () => set({ error: null }),

  invalidateCache: () => set({ cacheTimestamp: null }),

  // ============ SSE 事件处理 ============

  addSnapshotFromSSE: (snapshot: StateSnapshot) => {
    set((state) => {
      // 检查是否属于当前选中的实体
      if (state.selectedEntityId !== snapshot.timelineId) {
        return state;
      }

      // 检查是否已存在
      if (state.snapshots.some((s) => s.id === snapshot.id)) {
        return state;
      }

      // 添加并重新排序
      const newSnapshots = [...state.snapshots, snapshot].sort((a, b) => {
        const orderA = a.chapterOrder ?? 0;
        const orderB = b.chapterOrder ?? 0;
        return orderA - orderB;
      });

      return { snapshots: newSnapshots };
    });
  },
}));

// ============ 选择器 ============

export const selectSnapshots = (state: EvolutionStore) => state.snapshots;
export const selectSelectedSnapshot = (state: EvolutionStore) => state.selectedSnapshot;
export const selectIsLoading = (state: EvolutionStore) => state.isLoading;
export const selectError = (state: EvolutionStore) => state.error;
export const selectIsTimelineOpen = (state: EvolutionStore) => state.isTimelineOpen;
export const selectSelectedEntity = (state: EvolutionStore) => ({
  id: state.selectedEntityId,
  type: state.selectedEntityType,
  name: state.selectedEntityName,
});

// 获取关键帧快照
export const selectKeyframes = (state: EvolutionStore) =>
  state.snapshots.filter((s) => s.isKeyframe);

// 获取快照数量
export const selectSnapshotCount = (state: EvolutionStore) => state.snapshots.length;

// 比较相关选择器
export const selectCompareState = (state: EvolutionStore) => ({
  from: state.compareFromSnapshot,
  to: state.compareToSnapshot,
  result: state.compareResult,
  isComparing: state.isComparing,
});

// 创建快照相关选择器
export const selectCreateSnapshotState = (state: EvolutionStore) => ({
  isOpen: state.isCreateSnapshotDialogOpen,
  isCreating: state.isCreatingSnapshot,
});
