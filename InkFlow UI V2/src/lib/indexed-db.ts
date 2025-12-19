/**
 * IndexedDB Wrapper for Offline Storage
 * Provides typed access to IndexedDB for caching editor content and pending changes
 * Requirements: 15.2
 */

const DB_NAME = 'inkflow-offline';
const DB_VERSION = 1;

// Store names
export const STORES = {
  EDITOR_CONTENT: 'editor-content',
  PENDING_CHANGES: 'pending-changes',
  SYNC_METADATA: 'sync-metadata',
} as const;

export type StoreName = typeof STORES[keyof typeof STORES];

// Types for stored data
export interface CachedEditorContent {
  id: string; // chapterId
  projectId: string;
  content: string;
  localVersion: number;
  serverVersion: number | null;
  lastModified: number;
  isDirty: boolean;
}

export interface PendingChange {
  id: string;
  type: 'CONTENT_SAVE' | 'CHAPTER_CREATE' | 'CHAPTER_UPDATE' | 'CHAPTER_DELETE';
  resourceType: 'chapter' | 'volume' | 'project';
  resourceId: string;
  projectId: string;
  payload: unknown;
  timestamp: number;
  retryCount: number;
  status: 'pending' | 'syncing' | 'failed';
  errorMessage?: string;
}

export interface SyncMetadata {
  id: string; // 'global' or specific resource id
  lastSyncAt: number | null;
  syncStatus: 'synced' | 'syncing' | 'pending' | 'conflict';
  conflictData?: {
    localContent: string;
    serverContent: string;
    localVersion: number;
    serverVersion: number;
  };
}

let dbInstance: IDBDatabase | null = null;
let dbInitPromise: Promise<IDBDatabase> | null = null;

/**
 * Initialize IndexedDB
 */
export function initDB(): Promise<IDBDatabase> {
  if (dbInstance) {
    return Promise.resolve(dbInstance);
  }

  if (dbInitPromise) {
    return dbInitPromise;
  }

  dbInitPromise = new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') {
      reject(new Error('IndexedDB is not supported'));
      return;
    }

    const request = indexedDB.open(DB_NAME, DB_VERSION);

    request.onerror = () => {
      console.error('Failed to open IndexedDB:', request.error);
      reject(request.error);
    };

    request.onsuccess = () => {
      dbInstance = request.result;
      resolve(dbInstance);
    };

    request.onupgradeneeded = (event) => {
      const db = (event.target as IDBOpenDBRequest).result;

      // Editor content store
      if (!db.objectStoreNames.contains(STORES.EDITOR_CONTENT)) {
        const contentStore = db.createObjectStore(STORES.EDITOR_CONTENT, { keyPath: 'id' });
        contentStore.createIndex('projectId', 'projectId', { unique: false });
        contentStore.createIndex('isDirty', 'isDirty', { unique: false });
        contentStore.createIndex('lastModified', 'lastModified', { unique: false });
      }

      // Pending changes store
      if (!db.objectStoreNames.contains(STORES.PENDING_CHANGES)) {
        const changesStore = db.createObjectStore(STORES.PENDING_CHANGES, { keyPath: 'id' });
        changesStore.createIndex('projectId', 'projectId', { unique: false });
        changesStore.createIndex('timestamp', 'timestamp', { unique: false });
        changesStore.createIndex('status', 'status', { unique: false });
      }

      // Sync metadata store
      if (!db.objectStoreNames.contains(STORES.SYNC_METADATA)) {
        db.createObjectStore(STORES.SYNC_METADATA, { keyPath: 'id' });
      }
    };
  });

  return dbInitPromise;
}

/**
 * Get a transaction for the specified stores
 */
async function getTransaction(
  storeNames: StoreName | StoreName[],
  mode: IDBTransactionMode = 'readonly'
): Promise<IDBTransaction> {
  const db = await initDB();
  return db.transaction(storeNames, mode);
}

/**
 * Generic get operation
 */
export async function dbGet<T>(storeName: StoreName, key: string): Promise<T | undefined> {
  const tx = await getTransaction(storeName, 'readonly');
  const store = tx.objectStore(storeName);

  return new Promise((resolve, reject) => {
    const request = store.get(key);
    request.onsuccess = () => resolve(request.result as T | undefined);
    request.onerror = () => reject(request.error);
  });
}

/**
 * Generic put operation
 */
export async function dbPut<T>(storeName: StoreName, value: T): Promise<void> {
  const tx = await getTransaction(storeName, 'readwrite');
  const store = tx.objectStore(storeName);

  return new Promise((resolve, reject) => {
    const request = store.put(value);
    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error);
  });
}

/**
 * Generic delete operation
 */
export async function dbDelete(storeName: StoreName, key: string): Promise<void> {
  const tx = await getTransaction(storeName, 'readwrite');
  const store = tx.objectStore(storeName);

  return new Promise((resolve, reject) => {
    const request = store.delete(key);
    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error);
  });
}

/**
 * Get all items from a store
 */
export async function dbGetAll<T>(storeName: StoreName): Promise<T[]> {
  const tx = await getTransaction(storeName, 'readonly');
  const store = tx.objectStore(storeName);

  return new Promise((resolve, reject) => {
    const request = store.getAll();
    request.onsuccess = () => resolve(request.result as T[]);
    request.onerror = () => reject(request.error);
  });
}

/**
 * Get items by index
 */
export async function dbGetByIndex<T>(
  storeName: StoreName,
  indexName: string,
  value: IDBValidKey
): Promise<T[]> {
  const tx = await getTransaction(storeName, 'readonly');
  const store = tx.objectStore(storeName);
  const index = store.index(indexName);

  return new Promise((resolve, reject) => {
    const request = index.getAll(value);
    request.onsuccess = () => resolve(request.result as T[]);
    request.onerror = () => reject(request.error);
  });
}

/**
 * Clear all items from a store
 */
export async function dbClear(storeName: StoreName): Promise<void> {
  const tx = await getTransaction(storeName, 'readwrite');
  const store = tx.objectStore(storeName);

  return new Promise((resolve, reject) => {
    const request = store.clear();
    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error);
  });
}

/**
 * Count items in a store
 */
export async function dbCount(storeName: StoreName): Promise<number> {
  const tx = await getTransaction(storeName, 'readonly');
  const store = tx.objectStore(storeName);

  return new Promise((resolve, reject) => {
    const request = store.count();
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

// ============ Editor Content Operations ============

/**
 * Cache editor content locally
 */
export async function cacheEditorContent(
  chapterId: string,
  projectId: string,
  content: string,
  serverVersion: number | null = null
): Promise<void> {
  const existing = await dbGet<CachedEditorContent>(STORES.EDITOR_CONTENT, chapterId);
  
  const cached: CachedEditorContent = {
    id: chapterId,
    projectId,
    content,
    localVersion: (existing?.localVersion ?? 0) + 1,
    serverVersion: serverVersion ?? existing?.serverVersion ?? null,
    lastModified: Date.now(),
    isDirty: true,
  };

  await dbPut(STORES.EDITOR_CONTENT, cached);
}

/**
 * Get cached editor content
 */
export async function getCachedContent(chapterId: string): Promise<CachedEditorContent | undefined> {
  return dbGet<CachedEditorContent>(STORES.EDITOR_CONTENT, chapterId);
}

/**
 * Mark content as synced
 */
export async function markContentSynced(chapterId: string, serverVersion: number): Promise<void> {
  const cached = await getCachedContent(chapterId);
  if (cached) {
    cached.isDirty = false;
    cached.serverVersion = serverVersion;
    await dbPut(STORES.EDITOR_CONTENT, cached);
  }
}

/**
 * Get all dirty (unsaved) content
 */
export async function getDirtyContent(): Promise<CachedEditorContent[]> {
  return dbGetByIndex<CachedEditorContent>(STORES.EDITOR_CONTENT, 'isDirty', 1);
}

// ============ Pending Changes Operations ============

/**
 * Add a pending change
 */
export async function addPendingChange(
  change: Omit<PendingChange, 'id' | 'timestamp' | 'retryCount' | 'status'>
): Promise<string> {
  const id = `change_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  
  const pendingChange: PendingChange = {
    ...change,
    id,
    timestamp: Date.now(),
    retryCount: 0,
    status: 'pending',
  };

  await dbPut(STORES.PENDING_CHANGES, pendingChange);
  return id;
}

/**
 * Get all pending changes
 */
export async function getPendingChanges(): Promise<PendingChange[]> {
  const changes = await dbGetAll<PendingChange>(STORES.PENDING_CHANGES);
  return changes.sort((a, b) => a.timestamp - b.timestamp);
}

/**
 * Update pending change status
 */
export async function updatePendingChangeStatus(
  id: string,
  status: PendingChange['status'],
  errorMessage?: string
): Promise<void> {
  const change = await dbGet<PendingChange>(STORES.PENDING_CHANGES, id);
  if (change) {
    change.status = status;
    if (errorMessage) {
      change.errorMessage = errorMessage;
    }
    if (status === 'failed') {
      change.retryCount += 1;
    }
    await dbPut(STORES.PENDING_CHANGES, change);
  }
}

/**
 * Remove a pending change
 */
export async function removePendingChange(id: string): Promise<void> {
  await dbDelete(STORES.PENDING_CHANGES, id);
}

// ============ Sync Metadata Operations ============

/**
 * Get sync metadata
 */
export async function getSyncMetadata(id: string = 'global'): Promise<SyncMetadata | undefined> {
  return dbGet<SyncMetadata>(STORES.SYNC_METADATA, id);
}

/**
 * Update sync metadata
 */
export async function updateSyncMetadata(metadata: Partial<SyncMetadata> & { id: string }): Promise<void> {
  const existing = await getSyncMetadata(metadata.id);
  const updated: SyncMetadata = {
    id: metadata.id,
    lastSyncAt: metadata.lastSyncAt ?? existing?.lastSyncAt ?? null,
    syncStatus: metadata.syncStatus ?? existing?.syncStatus ?? 'synced',
    conflictData: metadata.conflictData ?? existing?.conflictData,
  };
  await dbPut(STORES.SYNC_METADATA, updated);
}

/**
 * Set conflict data
 */
export async function setConflictData(
  resourceId: string,
  localContent: string,
  serverContent: string,
  localVersion: number,
  serverVersion: number
): Promise<void> {
  await updateSyncMetadata({
    id: resourceId,
    syncStatus: 'conflict',
    conflictData: {
      localContent,
      serverContent,
      localVersion,
      serverVersion,
    },
  });
}

/**
 * Clear conflict data
 */
export async function clearConflictData(resourceId: string): Promise<void> {
  const metadata = await getSyncMetadata(resourceId);
  if (metadata) {
    metadata.syncStatus = 'synced';
    metadata.conflictData = undefined;
    await dbPut(STORES.SYNC_METADATA, metadata);
  }
}
