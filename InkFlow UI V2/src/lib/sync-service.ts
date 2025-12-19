/**
 * Sync Service
 * Handles synchronization of offline changes with the server
 * Requirements: 15.3
 */

import { contentService } from '@/services/content-service';
import { 
  useOfflineStore, 
  type PendingChange,
  type ConflictResolution,
} from './offline-manager';
import {
  getPendingChanges,
  getCachedContent,
  markContentSynced,
  setConflictData,
  clearConflictData,
  getDirtyContent,
} from './indexed-db';
import { showSuccess, showWarning, showError, showInfo } from './toast';

export interface SyncResult {
  success: boolean;
  serverVersion?: number;
  serverContent?: string;
  error?: string;
}

/**
 * Execute a single pending change
 */
async function executePendingChange(change: PendingChange): Promise<SyncResult> {
  try {
    switch (change.type) {
      case 'CONTENT_SAVE': {
        const payload = change.payload as { content: string };
        
        // First, check if there's a conflict by fetching current server version
        try {
          const serverContent = await contentService.getChapterContent(
            change.projectId,
            change.resourceId
          );
          
          const cached = await getCachedContent(change.resourceId);
          
          // Check for conflict: server has newer version than what we based our changes on
          if (cached && serverContent.version && cached.serverVersion !== null) {
            if (serverContent.version > cached.serverVersion) {
              // Conflict detected
              return {
                success: false,
                serverVersion: serverContent.version,
                serverContent: serverContent.content,
              };
            }
          }
        } catch (error) {
          // If we can't fetch server content, proceed with save attempt
          console.warn('Could not fetch server content for conflict check:', error);
        }
        
        // Attempt to save
        const result = await contentService.saveChapterContent(
          change.projectId,
          change.resourceId,
          { content: payload.content }
        );
        
        return {
          success: true,
          serverVersion: result.version,
        };
      }
      
      case 'CHAPTER_CREATE':
      case 'CHAPTER_UPDATE':
      case 'CHAPTER_DELETE':
        // These would be handled by their respective services
        // For now, return success as placeholder
        return { success: true };
      
      default:
        return { success: false, error: `Unknown change type: ${change.type}` };
    }
  } catch (error) {
    console.error('Failed to execute pending change:', error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error',
    };
  }
}

/**
 * Sync all pending changes
 * Requirements: 15.3
 */
export async function syncAllPendingChanges(): Promise<{
  synced: number;
  failed: number;
  conflicts: number;
}> {
  const store = useOfflineStore.getState();
  
  if (!store.isOnline) {
    return { synced: 0, failed: 0, conflicts: 0 };
  }

  let synced = 0;
  let failed = 0;
  let conflicts = 0;

  await store.syncPendingChanges(async (change) => {
    const result = await executePendingChange(change);
    
    if (result.success) {
      synced++;
    } else if (result.serverContent !== undefined) {
      conflicts++;
    } else {
      failed++;
    }
    
    return result;
  });

  return { synced, failed, conflicts };
}

/**
 * Sync a specific chapter's content
 */
export async function syncChapterContent(
  chapterId: string,
  projectId: string
): Promise<SyncResult> {
  const store = useOfflineStore.getState();
  
  if (!store.isOnline) {
    return { success: false, error: 'Offline' };
  }

  const cached = await getCachedContent(chapterId);
  if (!cached || !cached.isDirty) {
    return { success: true }; // Nothing to sync
  }

  try {
    // Check for conflicts first
    const serverContent = await contentService.getChapterContent(projectId, chapterId);
    
    const serverVersion = serverContent.version ?? 0;
    if (cached.serverVersion !== null && serverVersion > cached.serverVersion) {
      // Conflict detected
      await setConflictData(
        chapterId,
        cached.content,
        serverContent.content,
        cached.localVersion,
        serverVersion
      );
      
      return {
        success: false,
        serverVersion: serverVersion,
        serverContent: serverContent.content,
      };
    }

    // No conflict, save content
    const result = await contentService.saveChapterContent(projectId, chapterId, {
      content: cached.content,
    });

    await markContentSynced(chapterId, result.version ?? 0);
    
    return {
      success: true,
      serverVersion: result.version,
    };
  } catch (error) {
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Sync failed',
    };
  }
}

/**
 * Resolve a content conflict
 * Requirements: 15.4
 */
export async function resolveContentConflict(
  chapterId: string,
  projectId: string,
  resolution: ConflictResolution,
  mergedContent?: string
): Promise<boolean> {
  const store = useOfflineStore.getState();
  
  try {
    await store.resolveConflict(chapterId, resolution, mergedContent);
    
    // If keeping local or merged, we need to save to server
    if (resolution !== 'keep-server') {
      const cached = await getCachedContent(chapterId);
      if (cached) {
        const result = await contentService.saveChapterContent(projectId, chapterId, {
          content: cached.content,
        });
        await markContentSynced(chapterId, result.version ?? 0);
      }
    } else {
      // If keeping server, update local cache with server content
      const serverContent = await contentService.getChapterContent(projectId, chapterId);
      await markContentSynced(chapterId, serverContent.version ?? 0);
    }
    
    await clearConflictData(chapterId);
    return true;
  } catch (error) {
    console.error('Failed to resolve conflict:', error);
    showError('解决冲突失败');
    return false;
  }
}

/**
 * Auto-sync on reconnection
 */
export function setupAutoSync() {
  const handleOnline = async () => {
    const store = useOfflineStore.getState();
    
    // Wait a moment for connection to stabilize
    await new Promise(resolve => setTimeout(resolve, 2000));
    
    if (!store.isOnline) return;
    
    const pendingChanges = await getPendingChanges();
    const dirtyContent = await getDirtyContent();
    const totalPending = pendingChanges.length + dirtyContent.length;
    
    if (totalPending > 0) {
      showInfo(`正在同步 ${totalPending} 个离线更改...`);
      
      const result = await syncAllPendingChanges();
      
      if (result.conflicts > 0) {
        showWarning(`检测到 ${result.conflicts} 个冲突，请手动解决`);
      } else if (result.failed > 0) {
        showWarning(`${result.failed} 个更改同步失败`);
      } else if (result.synced > 0) {
        showSuccess(`已同步 ${result.synced} 个更改`);
      }
    }
  };

  window.addEventListener('online', handleOnline);
  
  return () => {
    window.removeEventListener('online', handleOnline);
  };
}

/**
 * Get sync status summary
 */
export async function getSyncStatusSummary(): Promise<{
  pendingChanges: number;
  dirtyContent: number;
  hasConflicts: boolean;
  lastSyncAt: number | null;
}> {
  const store = useOfflineStore.getState();
  const pendingChanges = await getPendingChanges();
  const dirtyContent = await getDirtyContent();
  
  return {
    pendingChanges: pendingChanges.length,
    dirtyContent: dirtyContent.length,
    hasConflicts: store.conflicts.length > 0,
    lastSyncAt: store.lastSyncAt,
  };
}
