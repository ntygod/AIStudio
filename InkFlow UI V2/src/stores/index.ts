/**
 * 状态管理导出
 */

export { useAuthStore, selectUser, selectIsAuthenticated } from './auth-store';
export { useProjectStore, selectProjects, selectCurrentProject, selectPagination } from './project-store';
export { useContentStore, selectVolumes, selectChapters, selectCurrentChapter, selectCurrentContent } from './content-store';
export { useAssetStore, selectCharacters, selectWikiEntries, selectPlotLoops, selectRelationshipGraph } from './asset-store';
export { useChatStore, selectMessages, selectIsStreaming, selectCurrentThoughts, selectAgentState, selectPendingContent } from './chat-store';
export { 
  useConsistencyStore, 
  selectWarnings, 
  selectWarningCount, 
  selectWarningsBySeverity,
  selectHasErrors,
  selectTotalWarnings,
  selectIsPanelOpen,
} from './consistency-store';
export {
  useEvolutionStore,
  selectSnapshots,
  selectSelectedSnapshot,
  selectSelectedEntity,
  selectKeyframes,
  selectSnapshotCount,
  selectCompareState,
  selectIsTimelineOpen,
} from './evolution-store';
export {
  useUIStore,
  selectLeftSidebarCollapsed,
  selectRightPanelCollapsed,
  selectZenMode,
  selectMobileActivePanel,
  selectTheme,
  selectBreakpoint,
  selectViewportWidth,
  selectIsMobile,
  selectIsTablet,
  selectIsDesktop,
  selectLayoutConfig,
  calculateBreakpoint,
  BREAKPOINTS,
  type ThemeMode,
  type MobileActivePanel,
  type BreakpointType,
} from './ui-store';
export {
  useProgressStore,
  selectProgress,
  selectWordCountStats,
  selectWeeklyActivity,
  selectDailyGoal,
  selectShowCelebration,
  selectTodayWordCount,
  selectDailyGoalProgress,
} from './progress-store';
