/**
 * Offline Manager
 * Handles network state detection, operation queuing, and sync for offline support
 * Requirements: 15.1, 15.2, 15.3, 15.4, 15.5
 */

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { showWarning, showSuccess, showError, showInfo } from './toast';
import {
  initDB,
  getPendingChanges,
  addPendingChange,
  removePendingChange,
  updatePendingChangeStatus,
  cacheEditorContent,
  getCachedContent,
  markContentSynced,
  getDirtyContent,
  getSyncMetadata,
  setConflictData,
  clearConflictData,
  type PendingChange,
  type CachedEditorContent,
  type SyncMetadata,
} from './indexed-db';

// Re-export types for convenience
export type { PendingChange, CachedEditorContent, SyncMetadata };

// Queued mutation types (kept for backward compatibility)
export interface QueuedMutation {
  id: string;
  type: 'CREATE' | 'UPDATE' | 'DELETE';
  resource: string;
  endpoint: string;
  method: 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  payload?: unknown;
  timestamp: number;
  retryCount: number;
}

// Sync status type
export type SyncStatus = 'synced' | 'syncing' | 'pending' | 'conflict';

// Conflict resolution strategy
export type ConflictResolution = 'keep-local' | 'keep-server' | 'merge';

// Conflict data for UI
export interface ConflictInfo {
  resourceId: string;
  resourceType: string;
  localContent: string;
  serverContent: string;
  localVersion: number;
  serverVersion: number;
  timestamp: number;
}

interface OfflineState {
  isOnline: boolean;
  pendingMutations: QueuedMutation[];
  isReplaying: boolean;
  lastOnlineAt: number | null;
  // Enhanced sync state
  syncStatus: SyncStatus;
  pendingChangesCount: number;
  conflicts: ConflictInfo[];
  isSyncing: boolean;
  lastSyncAt: number | null;
  syncError: string | null;
}

interface OfflineActions {
  setOnline: (online: boolean) => void;
  queueMutation: (mutation: Omit<QueuedMutation, 'id' | 'timestamp' | 'retryCount'>) => string;
  removeMutation: (id: string) => void;
  clearQueue: () => void;
  replayMutations: (executor: (mutation: QueuedMutation) => Promise<void>) => Promise<void>;
  incrementRetry: (id: string) => void;
  // Enhanced sync actions
  initializeOfflineSupport: () => Promise<void>;
  cacheContent: (chapterId: string, projectId: string, content: string) => Promise<void>;
  getCachedContent: (chapterId: string) => Promise<CachedEditorContent | undefined>;
  queueContentSave: (chapterId: string, projectId: string, content: string) => Promise<string>;
  syncPendingChanges: (executor: (change: PendingChange) => Promise<{ success: boolean; serverVersion?: number; serverContent?: string }>) => Promise<void>;
  resolveConflict: (resourceId: string, resolution: ConflictResolution, mergedContent?: string) => Promise<void>;
  dismissConflict: (resourceId: string) => void;
  refreshPendingCount: () => Promise<void>;
  setSyncStatus: (status: SyncStatus) => void;
  setSyncError: (error: string | null) => void;
}

type OfflineStore = OfflineState & OfflineActions;

const MAX_RETRIES = 3;

/**
 * Offline state store
 * Requirements: 15.1, 15.2, 15.3, 15.4, 15.5
 */
export const useOfflineStore = create<OfflineStore>()(
  persist(
    (set, get) => ({
      isOnline: typeof navigator !== 'undefined' ? navigator.onLine : true,
      pendingMutations: [],
      isReplaying: false,
      lastOnlineAt: null,
      // Enhanced sync state
      syncStatus: 'synced' as SyncStatus,
      pendingChangesCount: 0,
      conflicts: [],
      isSyncing: false,
      lastSyncAt: null,
      syncError: null,

      setOnline: (online: boolean) => {
        const wasOffline = !get().isOnline;
        set({
          isOnline: online,
          lastOnlineAt: online ? Date.now() : get().lastOnlineAt,
        });

        // Show notification on status change
        if (online && wasOffline) {
          showSuccess('网络已恢复');
          // Refresh pending count and trigger sync
          get().refreshPendingCount();
          const { pendingChangesCount } = get();
          if (pendingChangesCount > 0) {
            showInfo(`有 ${pendingChangesCount} 个待同步操作`);
            set({ syncStatus: 'pending' });
          }
        } else if (!online) {
          showWarning('网络已断开，操作将在恢复后同步');
        }
      },

      queueMutation: (mutation) => {
        const id = `mutation_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
        const queuedMutation: QueuedMutation = {
          ...mutation,
          id,
          timestamp: Date.now(),
          retryCount: 0,
        };

        set((state) => ({
          pendingMutations: [...state.pendingMutations, queuedMutation],
          syncStatus: 'pending',
        }));

        return id;
      },

      removeMutation: (id: string) => {
        set((state) => ({
          pendingMutations: state.pendingMutations.filter((m) => m.id !== id),
        }));
      },

      clearQueue: () => {
        set({ pendingMutations: [], syncStatus: 'synced' });
      },

      incrementRetry: (id: string) => {
        set((state) => ({
          pendingMutations: state.pendingMutations.map((m) =>
            m.id === id ? { ...m, retryCount: m.retryCount + 1 } : m
          ),
        }));
      },

      replayMutations: async (executor) => {
        const { pendingMutations, isReplaying, isOnline } = get();

        if (isReplaying || !isOnline || pendingMutations.length === 0) {
          return;
        }

        set({ isReplaying: true, syncStatus: 'syncing' });

        const failedMutations: QueuedMutation[] = [];

        for (const mutation of pendingMutations) {
          try {
            await executor(mutation);
            get().removeMutation(mutation.id);
          } catch (error) {
            console.error('Failed to replay mutation:', mutation.id, error);
            get().incrementRetry(mutation.id);

            if (mutation.retryCount + 1 >= MAX_RETRIES) {
              showError(`操作 "${mutation.resource}" 同步失败，已达最大重试次数`);
              get().removeMutation(mutation.id);
            } else {
              failedMutations.push(mutation);
            }
          }
        }

        set({ 
          isReplaying: false,
          syncStatus: failedMutations.length > 0 ? 'pending' : 'synced',
          lastSyncAt: Date.now(),
        });

        if (failedMutations.length > 0) {
          showWarning(`${failedMutations.length} 个操作同步失败，将稍后重试`);
        } else if (pendingMutations.length > 0) {
          showSuccess('所有操作已同步');
        }
      },

      // ============ Enhanced Sync Actions ============

      initializeOfflineSupport: async () => {
        try {
          await initDB();
          await get().refreshPendingCount();
          
          // Check for existing conflicts
          const metadata = await getSyncMetadata('global');
          if (metadata?.syncStatus === 'conflict') {
            set({ syncStatus: 'conflict' });
          }
        } catch (error) {
          console.error('Failed to initialize offline support:', error);
        }
      },

      cacheContent: async (chapterId: string, projectId: string, content: string) => {
        try {
          await cacheEditorContent(chapterId, projectId, content);
          await get().refreshPendingCount();
        } catch (error) {
          console.error('Failed to cache content:', error);
        }
      },

      getCachedContent: async (chapterId: string) => {
        try {
          return await getCachedContent(chapterId);
        } catch (error) {
          console.error('Failed to get cached content:', error);
          return undefined;
        }
      },

      queueContentSave: async (chapterId: string, projectId: string, content: string) => {
        try {
          // Cache content locally first
          await cacheEditorContent(chapterId, projectId, content);
          
          // Add to pending changes queue
          const changeId = await addPendingChange({
            type: 'CONTENT_SAVE',
            resourceType: 'chapter',
            resourceId: chapterId,
            projectId,
            payload: { content },
          });

          await get().refreshPendingCount();
          set({ syncStatus: 'pending' });
          
          return changeId;
        } catch (error) {
          console.error('Failed to queue content save:', error);
          throw error;
        }
      },

      syncPendingChanges: async (executor) => {
        const { isOnline, isSyncing } = get();

        if (!isOnline || isSyncing) {
          return;
        }

        set({ isSyncing: true, syncStatus: 'syncing', syncError: null });

        try {
          const pendingChanges = await getPendingChanges();
          
          if (pendingChanges.length === 0) {
            set({ isSyncing: false, syncStatus: 'synced', lastSyncAt: Date.now() });
            return;
          }

          let hasConflicts = false;
          let successCount = 0;
          let failCount = 0;

          for (const change of pendingChanges) {
            if (change.status === 'failed' && change.retryCount >= MAX_RETRIES) {
              continue; // Skip permanently failed changes
            }

            try {
              await updatePendingChangeStatus(change.id, 'syncing');
              
              const result = await executor(change);
              
              if (result.success) {
                // Mark as synced
                await removePendingChange(change.id);
                
                // Update cached content if it was a content save
                if (change.type === 'CONTENT_SAVE' && result.serverVersion) {
                  await markContentSynced(change.resourceId, result.serverVersion);
                }
                
                successCount++;
              } else if (result.serverContent !== undefined) {
                // Conflict detected
                const cached = await getCachedContent(change.resourceId);
                if (cached) {
                  await setConflictData(
                    change.resourceId,
                    cached.content,
                    result.serverContent,
                    cached.localVersion,
                    result.serverVersion ?? 0
                  );
                  
                  set((state) => ({
                    conflicts: [
                      ...state.conflicts,
                      {
                        resourceId: change.resourceId,
                        resourceType: change.resourceType,
                        localContent: cached.content,
                        serverContent: result.serverContent!,
                        localVersion: cached.localVersion,
                        serverVersion: result.serverVersion ?? 0,
                        timestamp: Date.now(),
                      },
                    ],
                  }));
                  
                  hasConflicts = true;
                }
              }
            } catch (error) {
              console.error('Failed to sync change:', change.id, error);
              await updatePendingChangeStatus(
                change.id,
                'failed',
                error instanceof Error ? error.message : 'Unknown error'
              );
              failCount++;
            }
          }

          await get().refreshPendingCount();

          const newStatus: SyncStatus = hasConflicts 
            ? 'conflict' 
            : (failCount > 0 ? 'pending' : 'synced');

          set({ 
            isSyncing: false, 
            syncStatus: newStatus,
            lastSyncAt: Date.now(),
          });

          if (successCount > 0 && failCount === 0 && !hasConflicts) {
            showSuccess(`已同步 ${successCount} 个更改`);
          } else if (failCount > 0) {
            showWarning(`${failCount} 个更改同步失败`);
          }
          
          if (hasConflicts) {
            showWarning('检测到内容冲突，请手动解决');
          }
        } catch (error) {
          console.error('Sync failed:', error);
          set({ 
            isSyncing: false, 
            syncStatus: 'pending',
            syncError: error instanceof Error ? error.message : 'Sync failed',
          });
          showError('同步失败，请稍后重试');
        }
      },

      resolveConflict: async (resourceId: string, resolution: ConflictResolution, mergedContent?: string) => {
        const conflict = get().conflicts.find(c => c.resourceId === resourceId);
        if (!conflict) return;

        try {
          let finalContent: string;
          
          switch (resolution) {
            case 'keep-local':
              finalContent = conflict.localContent;
              break;
            case 'keep-server':
              finalContent = conflict.serverContent;
              break;
            case 'merge':
              if (!mergedContent) {
                throw new Error('Merged content is required for merge resolution');
              }
              finalContent = mergedContent;
              break;
          }

          // Update cached content with resolved version
          await cacheEditorContent(
            resourceId,
            '', // projectId will be preserved from existing cache
            finalContent,
            conflict.serverVersion
          );

          // Clear conflict data
          await clearConflictData(resourceId);

          // Remove from conflicts list
          set((state) => ({
            conflicts: state.conflicts.filter(c => c.resourceId !== resourceId),
            syncStatus: state.conflicts.length <= 1 ? 'pending' : 'conflict',
          }));

          showSuccess('冲突已解决');
        } catch (error) {
          console.error('Failed to resolve conflict:', error);
          showError('解决冲突失败');
        }
      },

      dismissConflict: (resourceId: string) => {
        set((state) => ({
          conflicts: state.conflicts.filter(c => c.resourceId !== resourceId),
          syncStatus: state.conflicts.length <= 1 ? 'pending' : 'conflict',
        }));
      },

      refreshPendingCount: async () => {
        try {
          const pendingChanges = await getPendingChanges();
          const dirtyContent = await getDirtyContent();
          const totalPending = pendingChanges.length + dirtyContent.length;
          
          set({ 
            pendingChangesCount: totalPending,
            syncStatus: totalPending > 0 ? 'pending' : get().syncStatus,
          });
        } catch (error) {
          console.error('Failed to refresh pending count:', error);
        }
      },

      setSyncStatus: (status: SyncStatus) => {
        set({ syncStatus: status });
      },

      setSyncError: (error: string | null) => {
        set({ syncError: error });
      },
    }),
    {
      name: 'inkflow-offline',
      partialize: (state) => ({
        pendingMutations: state.pendingMutations,
        lastOnlineAt: state.lastOnlineAt,
        lastSyncAt: state.lastSyncAt,
      }),
    }
  )
);

/**
 * Initialize offline detection and IndexedDB
 * Call this once at app startup
 * Requirements: 15.1
 */
export function initializeOfflineDetection() {
  if (typeof window === 'undefined') return;

  const store = useOfflineStore.getState();

  // Initialize IndexedDB and offline support
  store.initializeOfflineSupport();

  // Set initial state
  store.setOnline(navigator.onLine);

  // Listen for online/offline events
  const handleOnline = () => {
    store.setOnline(true);
    // Auto-sync when coming back online
    setTimeout(() => {
      const { pendingChangesCount } = useOfflineStore.getState();
      if (pendingChangesCount > 0) {
        showInfo('正在同步离线更改...');
      }
    }, 1000);
  };
  
  const handleOffline = () => store.setOnline(false);

  window.addEventListener('online', handleOnline);
  window.addEventListener('offline', handleOffline);

  // Periodic sync check (every 30 seconds when online)
  const syncInterval = setInterval(() => {
    const { isOnline, pendingChangesCount, isSyncing } = useOfflineStore.getState();
    if (isOnline && pendingChangesCount > 0 && !isSyncing) {
      store.refreshPendingCount();
    }
  }, 30000);

  // Return cleanup function
  return () => {
    window.removeEventListener('online', handleOnline);
    window.removeEventListener('offline', handleOffline);
    clearInterval(syncInterval);
  };
}

/**
 * Hook to use offline state
 * Requirements: 15.1, 15.5
 */
export function useOffline() {
  const isOnline = useOfflineStore((state) => state.isOnline);
  const pendingCount = useOfflineStore((state) => state.pendingMutations.length);
  const isReplaying = useOfflineStore((state) => state.isReplaying);
  const queueMutation = useOfflineStore((state) => state.queueMutation);
  const replayMutations = useOfflineStore((state) => state.replayMutations);
  // Enhanced sync state
  const syncStatus = useOfflineStore((state) => state.syncStatus);
  const pendingChangesCount = useOfflineStore((state) => state.pendingChangesCount);
  const conflicts = useOfflineStore((state) => state.conflicts);
  const isSyncing = useOfflineStore((state) => state.isSyncing);
  const lastSyncAt = useOfflineStore((state) => state.lastSyncAt);
  const syncError = useOfflineStore((state) => state.syncError);
  // Enhanced actions
  const cacheContent = useOfflineStore((state) => state.cacheContent);
  const getCachedContent = useOfflineStore((state) => state.getCachedContent);
  const queueContentSave = useOfflineStore((state) => state.queueContentSave);
  const syncPendingChanges = useOfflineStore((state) => state.syncPendingChanges);
  const resolveConflict = useOfflineStore((state) => state.resolveConflict);
  const dismissConflict = useOfflineStore((state) => state.dismissConflict);

  return {
    // Basic state
    isOnline,
    isOffline: !isOnline,
    pendingCount,
    isReplaying,
    queueMutation,
    replayMutations,
    // Enhanced sync state
    syncStatus,
    pendingChangesCount,
    conflicts,
    isSyncing,
    lastSyncAt,
    syncError,
    hasConflicts: conflicts.length > 0,
    // Enhanced actions
    cacheContent,
    getCachedContent,
    queueContentSave,
    syncPendingChanges,
    resolveConflict,
    dismissConflict,
  };
}

/**
 * Hook for sync status display
 * Requirements: 15.5
 */
export function useSyncStatus() {
  const syncStatus = useOfflineStore((state) => state.syncStatus);
  const pendingChangesCount = useOfflineStore((state) => state.pendingChangesCount);
  const isSyncing = useOfflineStore((state) => state.isSyncing);
  const lastSyncAt = useOfflineStore((state) => state.lastSyncAt);
  const conflicts = useOfflineStore((state) => state.conflicts);
  const isOnline = useOfflineStore((state) => state.isOnline);

  const statusText = (() => {
    if (!isOnline) return '离线';
    if (isSyncing) return '同步中...';
    if (conflicts.length > 0) return `${conflicts.length} 个冲突`;
    if (pendingChangesCount > 0) return `${pendingChangesCount} 待同步`;
    return '已同步';
  })();

  const statusColor = (() => {
    if (!isOnline) return 'destructive';
    if (conflicts.length > 0) return 'warning';
    if (isSyncing) return 'info';
    if (pendingChangesCount > 0) return 'warning';
    return 'success';
  })();

  return {
    syncStatus,
    statusText,
    statusColor,
    pendingChangesCount,
    isSyncing,
    lastSyncAt,
    hasConflicts: conflicts.length > 0,
    conflictCount: conflicts.length,
    isOnline,
  };
}

/**
 * Wrapper for API calls that queues mutations when offline
 */
export async function withOfflineSupport<T>(
  operation: () => Promise<T>,
  mutation: Omit<QueuedMutation, 'id' | 'timestamp' | 'retryCount'>,
  options?: {
    optimisticUpdate?: () => void;
    rollback?: () => void;
  }
): Promise<T | undefined> {
  const { isOnline, queueMutation } = useOfflineStore.getState();

  // Apply optimistic update
  options?.optimisticUpdate?.();

  if (!isOnline) {
    // Queue for later
    queueMutation(mutation);
    return undefined;
  }

  try {
    return await operation();
  } catch (error) {
    // Check if it's a network error
    if (error instanceof TypeError && error.message.includes('fetch')) {
      // Queue for later
      queueMutation(mutation);
      return undefined;
    }

    // Rollback optimistic update
    options?.rollback?.();
    throw error;
  }
}

/**
 * Wrapper for content saves with offline support
 * Requirements: 15.2
 */
export async function saveContentWithOfflineSupport(
  chapterId: string,
  projectId: string,
  content: string,
  saveOperation: () => Promise<{ version: number }>
): Promise<{ success: boolean; queued: boolean; version?: number }> {
  const store = useOfflineStore.getState();
  const { isOnline } = store;

  // Always cache locally first
  await store.cacheContent(chapterId, projectId, content);

  if (!isOnline) {
    // Queue for later sync
    await store.queueContentSave(chapterId, projectId, content);
    return { success: true, queued: true };
  }

  try {
    const result = await saveOperation();
    // Mark as synced in IndexedDB
    await markContentSynced(chapterId, result.version);
    return { success: true, queued: false, version: result.version };
  } catch (error) {
    // Check if it's a network error
    if (error instanceof TypeError && error.message.includes('fetch')) {
      // Queue for later sync
      await store.queueContentSave(chapterId, projectId, content);
      return { success: true, queued: true };
    }
    throw error;
  }
}
