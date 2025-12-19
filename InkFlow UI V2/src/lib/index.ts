/**
 * Lib 导出
 */

export { initializeApp, getConfig } from './init';
export {
  showSuccess,
  showError,
  showWarning,
  showInfo,
  showLoading,
  dismissToast,
  showApiError,
  toastPromise,
  toast,
} from './toast';
export {
  useOfflineStore,
  useOffline,
  useSyncStatus,
  initializeOfflineDetection,
  withOfflineSupport,
  saveContentWithOfflineSupport,
  type QueuedMutation,
  type SyncStatus,
  type ConflictInfo,
  type ConflictResolution,
  type PendingChange,
  type CachedEditorContent,
  type SyncMetadata,
} from './offline-manager';
export {
  initDB,
  cacheEditorContent,
  getCachedContent,
  markContentSynced,
  getDirtyContent,
  addPendingChange,
  getPendingChanges,
  removePendingChange,
  getSyncMetadata,
  updateSyncMetadata,
  setConflictData,
  clearConflictData,
  STORES,
} from './indexed-db';
export {
  syncAllPendingChanges,
  syncChapterContent,
  resolveContentConflict,
  setupAutoSync,
  getSyncStatusSummary,
} from './sync-service';
