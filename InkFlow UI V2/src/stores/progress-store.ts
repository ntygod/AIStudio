/**
 * 进度状态管理
 * 使用 Zustand 管理进度统计数据
 * 
 * Requirements: 7.1, 7.2, 7.3, 7.4
 */

import { create } from 'zustand';
import { 
  progressService, 
  type CreationProgress, 
  type WordCountStatistics, 
  type DailyWordCount 
} from '@/services/progress-service';

interface ProgressState {
  // 数据
  progress: CreationProgress | null;
  wordCountStats: WordCountStatistics | null;
  weeklyActivity: DailyWordCount[];
  dailyGoal: number;
  
  // 状态
  isLoading: boolean;
  error: string | null;
  
  // 庆祝动画状态
  showCelebration: boolean;
  lastCelebratedDate: string | null;
}

interface ProgressActions {
  fetchProgress(projectId: string): Promise<void>;
  fetchWordCountStats(projectId: string): Promise<void>;
  fetchWeeklyActivity(projectId: string): Promise<void>;
  fetchAll(projectId: string): Promise<void>;
  setDailyGoal(goal: number): void;
  checkAndTriggerCelebration(): void;
  dismissCelebration(): void;
  clearError(): void;
  reset(): void;
}

type ProgressStore = ProgressState & ProgressActions;

// 默认每日目标（字数）
const DEFAULT_DAILY_GOAL = 2000;

// 从 localStorage 获取每日目标
const getStoredDailyGoal = (): number => {
  try {
    const stored = localStorage.getItem('inkflow-daily-goal');
    return stored ? parseInt(stored, 10) : DEFAULT_DAILY_GOAL;
  } catch {
    return DEFAULT_DAILY_GOAL;
  }
};

// 从 localStorage 获取上次庆祝日期
const getLastCelebratedDate = (): string | null => {
  try {
    return localStorage.getItem('inkflow-last-celebrated-date');
  } catch {
    return null;
  }
};

export const useProgressStore = create<ProgressStore>()((set, get) => ({
  // 初始状态
  progress: null,
  wordCountStats: null,
  weeklyActivity: [],
  dailyGoal: getStoredDailyGoal(),
  isLoading: false,
  error: null,
  showCelebration: false,
  lastCelebratedDate: getLastCelebratedDate(),

  /**
   * 获取当前进度
   */
  fetchProgress: async (projectId: string) => {
    set({ isLoading: true, error: null });
    try {
      const progress = await progressService.getProgress(projectId);
      set({ progress, isLoading: false });
    } catch (error) {
      const message = error instanceof Error ? error.message : '获取进度失败';
      set({ isLoading: false, error: message });
    }
  },

  /**
   * 获取字数统计
   */
  fetchWordCountStats: async (projectId: string) => {
    set({ isLoading: true, error: null });
    try {
      const wordCountStats = await progressService.getWordCountStats(projectId);
      set({ wordCountStats, isLoading: false });
    } catch (error) {
      const message = error instanceof Error ? error.message : '获取字数统计失败';
      set({ isLoading: false, error: message });
    }
  },

  /**
   * 获取周活动数据
   */
  fetchWeeklyActivity: async (projectId: string) => {
    set({ isLoading: true, error: null });
    try {
      const weeklyActivity = await progressService.getWeeklyActivity(projectId);
      set({ weeklyActivity, isLoading: false });
    } catch (error) {
      const message = error instanceof Error ? error.message : '获取周活动数据失败';
      set({ isLoading: false, error: message });
    }
  },

  /**
   * 获取所有进度数据
   */
  fetchAll: async (projectId: string) => {
    set({ isLoading: true, error: null });
    try {
      const [progress, wordCountStats, weeklyActivity] = await Promise.all([
        progressService.getProgress(projectId),
        progressService.getWordCountStats(projectId),
        progressService.getWeeklyActivity(projectId),
      ]);
      set({ 
        progress, 
        wordCountStats, 
        weeklyActivity, 
        isLoading: false 
      });
      
      // 检查是否需要触发庆祝动画
      get().checkAndTriggerCelebration();
    } catch (error) {
      const message = error instanceof Error ? error.message : '获取进度数据失败';
      set({ isLoading: false, error: message });
    }
  },

  /**
   * 设置每日目标
   */
  setDailyGoal: (goal: number) => {
    try {
      localStorage.setItem('inkflow-daily-goal', String(goal));
    } catch {
      // ignore localStorage errors
    }
    set({ dailyGoal: goal });
  },

  /**
   * 检查并触发庆祝动画
   */
  checkAndTriggerCelebration: () => {
    const state = get();
    const today = new Date().toISOString().split('T')[0];
    
    // 如果今天已经庆祝过，不再触发
    if (state.lastCelebratedDate === today) {
      return;
    }
    
    // 计算今日字数
    const todayActivity = state.weeklyActivity.find(a => a.date === today);
    const todayWordCount = todayActivity?.wordCountChange || 0;
    
    // 如果达到每日目标，触发庆祝
    if (todayWordCount >= state.dailyGoal) {
      try {
        localStorage.setItem('inkflow-last-celebrated-date', today);
      } catch {
        // ignore localStorage errors
      }
      set({ showCelebration: true, lastCelebratedDate: today });
    }
  },

  /**
   * 关闭庆祝动画
   */
  dismissCelebration: () => {
    set({ showCelebration: false });
  },

  /**
   * 清除错误
   */
  clearError: () => {
    set({ error: null });
  },

  /**
   * 重置状态
   */
  reset: () => {
    set({
      progress: null,
      wordCountStats: null,
      weeklyActivity: [],
      isLoading: false,
      error: null,
      showCelebration: false,
    });
  },
}));

// 导出选择器
export const selectProgress = (state: ProgressStore) => state.progress;
export const selectWordCountStats = (state: ProgressStore) => state.wordCountStats;
export const selectWeeklyActivity = (state: ProgressStore) => state.weeklyActivity;
export const selectDailyGoal = (state: ProgressStore) => state.dailyGoal;
export const selectIsLoading = (state: ProgressStore) => state.isLoading;
export const selectShowCelebration = (state: ProgressStore) => state.showCelebration;

/**
 * 计算今日字数
 */
export const selectTodayWordCount = (state: ProgressStore): number => {
  const today = new Date().toISOString().split('T')[0];
  const todayActivity = state.weeklyActivity.find(a => a.date === today);
  return todayActivity?.wordCountChange || 0;
};

/**
 * 计算每日目标完成百分比
 */
export const selectDailyGoalProgress = (state: ProgressStore): number => {
  const todayWordCount = selectTodayWordCount(state);
  if (state.dailyGoal <= 0) return 0;
  return Math.min(100, Math.round((todayWordCount / state.dailyGoal) * 100));
};
