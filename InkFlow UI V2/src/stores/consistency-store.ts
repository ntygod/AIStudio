/**
 * 一致性警告状态管理
 * 使用 Zustand 管理一致性警告数据
 * 
 * Requirements: 7.1, 7.4, 7.5
 */

import { create } from 'zustand';
import {
  consistencyService,
  type ConsistencyWarning,
  type WarningCount,
  type EntityType,
  type WarningType,
  type Severity,
} from '@/services/consistency-service';

interface ConsistencyState {
  warnings: ConsistencyWarning[];
  warningCount: WarningCount | null;
  isLoading: boolean;
  error: string | null;
  // 面板状态
  isPanelOpen: boolean;
  // 过滤器
  filterEntityType: EntityType | null;
  filterWarningType: WarningType | null;
  filterSeverity: Severity | null;
  // 缓存时间戳
  cacheTimestamp: number | null;
}

interface ConsistencyActions {
  // 数据加载
  fetchWarningCount(projectId: string): Promise<void>;
  fetchWarnings(projectId: string, forceRefresh?: boolean): Promise<void>;
  // 警告操作
  resolveWarning(warningId: string, resolutionMethod: string): Promise<void>;
  dismissWarning(warningId: string): Promise<void>;
  bulkResolve(warningIds: string[], resolutionMethod: string): Promise<void>;
  bulkDismiss(warningIds: string[]): Promise<void>;
  // 面板控制
  openPanel(): void;
  closePanel(): void;
  togglePanel(): void;
  // 过滤器
  setFilterEntityType(type: EntityType | null): void;
  setFilterWarningType(type: WarningType | null): void;
  setFilterSeverity(severity: Severity | null): void;
  clearFilters(): void;
  // 通用
  clearError(): void;
  invalidateCache(): void;
  // SSE 事件处理
  addWarningFromSSE(warning: ConsistencyWarning): void;
  updateWarningFromSSE(warning: ConsistencyWarning): void;
}

type ConsistencyStore = ConsistencyState & ConsistencyActions;

// 缓存有效期（2分钟，警告数据需要更频繁刷新）
const CACHE_TTL = 2 * 60 * 1000;

const isCacheValid = (timestamp: number | null): boolean => {
  if (!timestamp) return false;
  return Date.now() - timestamp < CACHE_TTL;
};

export const useConsistencyStore = create<ConsistencyStore>()((set, get) => ({
  // 初始状态
  warnings: [],
  warningCount: null,
  isLoading: false,
  error: null,
  isPanelOpen: false,
  filterEntityType: null,
  filterWarningType: null,
  filterSeverity: null,
  cacheTimestamp: null,

  // ============ 数据加载 ============

  fetchWarningCount: async (projectId: string) => {
    try {
      const count = await consistencyService.getWarningCount(projectId);
      set({ warningCount: count });
    } catch (error) {
      const message = error instanceof Error ? error.message : '获取警告数量失败';
      set({ error: message });
    }
  },

  fetchWarnings: async (projectId: string, forceRefresh: boolean = false) => {
    const state = get();

    if (!forceRefresh && isCacheValid(state.cacheTimestamp) && state.warnings.length > 0) {
      return;
    }

    set({ isLoading: true, error: null });

    try {
      const warnings = await consistencyService.getWarnings(
        projectId,
        state.filterEntityType ?? undefined,
        state.filterWarningType ?? undefined
      );
      
      // 应用严重程度过滤（前端过滤）
      const filteredWarnings = state.filterSeverity
        ? warnings.filter(w => w.severity === state.filterSeverity)
        : warnings;

      set({
        warnings: filteredWarnings,
        isLoading: false,
        cacheTimestamp: Date.now(),
      });

      // 同时更新计数
      const count = await consistencyService.getWarningCount(projectId);
      set({ warningCount: count });
    } catch (error) {
      const message = error instanceof Error ? error.message : '获取警告列表失败';
      set({ isLoading: false, error: message });
      throw error;
    }
  },

  // ============ 警告操作 ============

  resolveWarning: async (warningId: string, resolutionMethod: string) => {
    const state = get();
    const original = state.warnings.find(w => w.id === warningId);

    // 乐观更新
    if (original) {
      set((state) => ({
        warnings: state.warnings.map(w =>
          w.id === warningId ? { ...w, resolved: true, resolutionMethod } : w
        ),
      }));
    }

    try {
      await consistencyService.resolveWarning(warningId, resolutionMethod);
      
      // 从列表中移除已解决的警告
      set((state) => ({
        warnings: state.warnings.filter(w => w.id !== warningId),
        warningCount: state.warningCount ? {
          ...state.warningCount,
          total: Math.max(0, state.warningCount.total - 1),
          [original?.severity.toLowerCase() ?? 'warning']: Math.max(
            0,
            (state.warningCount[original?.severity.toLowerCase() as keyof WarningCount] as number ?? 1) - 1
          ),
        } : null,
      }));
    } catch (error) {
      // 回滚
      if (original) {
        set((state) => ({
          warnings: state.warnings.map(w => w.id === warningId ? original : w),
        }));
      }
      const message = error instanceof Error ? error.message : '解决警告失败';
      set({ error: message });
      throw error;
    }
  },

  dismissWarning: async (warningId: string) => {
    const state = get();
    const original = state.warnings.find(w => w.id === warningId);

    // 乐观更新
    if (original) {
      set((state) => ({
        warnings: state.warnings.map(w =>
          w.id === warningId ? { ...w, dismissed: true } : w
        ),
      }));
    }

    try {
      await consistencyService.dismissWarning(warningId);
      
      // 从列表中移除已忽略的警告
      set((state) => ({
        warnings: state.warnings.filter(w => w.id !== warningId),
        warningCount: state.warningCount ? {
          ...state.warningCount,
          total: Math.max(0, state.warningCount.total - 1),
          [original?.severity.toLowerCase() ?? 'warning']: Math.max(
            0,
            (state.warningCount[original?.severity.toLowerCase() as keyof WarningCount] as number ?? 1) - 1
          ),
        } : null,
      }));
    } catch (error) {
      // 回滚
      if (original) {
        set((state) => ({
          warnings: state.warnings.map(w => w.id === warningId ? original : w),
        }));
      }
      const message = error instanceof Error ? error.message : '忽略警告失败';
      set({ error: message });
      throw error;
    }
  },

  bulkResolve: async (warningIds: string[], resolutionMethod: string) => {
    const state = get();
    const originalWarnings = [...state.warnings];

    // 乐观更新
    set((state) => ({
      warnings: state.warnings.filter(w => !warningIds.includes(w.id)),
    }));

    try {
      await consistencyService.bulkResolve(warningIds, resolutionMethod);
      
      // 更新计数
      set({ cacheTimestamp: null }); // 强制下次刷新
    } catch (error) {
      // 回滚
      set({ warnings: originalWarnings });
      const message = error instanceof Error ? error.message : '批量解决警告失败';
      set({ error: message });
      throw error;
    }
  },

  bulkDismiss: async (warningIds: string[]) => {
    const state = get();
    const originalWarnings = [...state.warnings];

    // 乐观更新
    set((state) => ({
      warnings: state.warnings.filter(w => !warningIds.includes(w.id)),
    }));

    try {
      await consistencyService.bulkDismiss(warningIds);
      
      // 更新计数
      set({ cacheTimestamp: null }); // 强制下次刷新
    } catch (error) {
      // 回滚
      set({ warnings: originalWarnings });
      const message = error instanceof Error ? error.message : '批量忽略警告失败';
      set({ error: message });
      throw error;
    }
  },

  // ============ 面板控制 ============

  openPanel: () => set({ isPanelOpen: true }),
  closePanel: () => set({ isPanelOpen: false }),
  togglePanel: () => set((state) => ({ isPanelOpen: !state.isPanelOpen })),

  // ============ 过滤器 ============

  setFilterEntityType: (type: EntityType | null) => {
    set({ filterEntityType: type, cacheTimestamp: null });
  },

  setFilterWarningType: (type: WarningType | null) => {
    set({ filterWarningType: type, cacheTimestamp: null });
  },

  setFilterSeverity: (severity: Severity | null) => {
    set({ filterSeverity: severity, cacheTimestamp: null });
  },

  clearFilters: () => {
    set({
      filterEntityType: null,
      filterWarningType: null,
      filterSeverity: null,
      cacheTimestamp: null,
    });
  },

  // ============ 通用 ============

  clearError: () => set({ error: null }),

  invalidateCache: () => set({ cacheTimestamp: null }),

  // ============ SSE 事件处理 ============

  addWarningFromSSE: (warning: ConsistencyWarning) => {
    set((state) => {
      // 检查是否已存在
      if (state.warnings.some(w => w.id === warning.id)) {
        return state;
      }
      
      return {
        warnings: [warning, ...state.warnings],
        warningCount: state.warningCount ? {
          ...state.warningCount,
          total: state.warningCount.total + 1,
          [warning.severity.toLowerCase()]: 
            ((state.warningCount[warning.severity.toLowerCase() as keyof WarningCount] as number) ?? 0) + 1,
        } : null,
      };
    });
  },

  updateWarningFromSSE: (warning: ConsistencyWarning) => {
    set((state) => ({
      warnings: state.warnings.map(w => w.id === warning.id ? warning : w),
    }));
  },
}));

// ============ 选择器 ============

export const selectWarnings = (state: ConsistencyStore) => state.warnings;
export const selectWarningCount = (state: ConsistencyStore) => state.warningCount;
export const selectIsLoading = (state: ConsistencyStore) => state.isLoading;
export const selectError = (state: ConsistencyStore) => state.error;
export const selectIsPanelOpen = (state: ConsistencyStore) => state.isPanelOpen;

// 按严重程度分组的警告
export const selectWarningsBySeverity = (state: ConsistencyStore) => {
  const grouped = {
    error: [] as ConsistencyWarning[],
    warning: [] as ConsistencyWarning[],
    info: [] as ConsistencyWarning[],
  };
  
  state.warnings.forEach(w => {
    const key = w.severity.toLowerCase() as keyof typeof grouped;
    if (grouped[key]) {
      grouped[key].push(w);
    }
  });
  
  return grouped;
};

// 是否有未解决的错误级别警告
export const selectHasErrors = (state: ConsistencyStore) => 
  state.warningCount?.error ? state.warningCount.error > 0 : false;

// 总警告数
export const selectTotalWarnings = (state: ConsistencyStore) => 
  state.warningCount?.total ?? 0;
