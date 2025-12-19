/**
 * 内容状态管理
 * 使用 Zustand 管理卷和章节数据
 */

import { create } from 'zustand';
import type { 
  Volume, 
  Chapter,
  ChapterContent,
  CreateVolumeRequest,
  CreateChapterRequest,
  UpdateChapterRequest,
  ReorderRequest
} from '@/types';
import { contentService } from '@/services/content-service';

interface ContentState {
  volumes: Volume[];
  chapters: Map<string, Chapter[]>; // volumeId -> chapters
  currentVolume: Volume | null;
  currentChapter: Chapter | null;
  currentContent: ChapterContent | null;
  isLoading: boolean;
  isSaving: boolean;
  error: string | null;
  // 缓存时间戳
  volumesCacheTimestamp: number | null;
  chaptersCacheTimestamp: Map<string, number>;
  // 编辑器光标位置 (Requirements: 4.8)
  cursorPosition: number | null;
}

interface ContentActions {
  fetchVolumes(projectId: string, forceRefresh?: boolean): Promise<void>;
  fetchChapters(projectId: string, volumeId: string, forceRefresh?: boolean): Promise<void>;
  createVolume(projectId: string, data: CreateVolumeRequest): Promise<Volume>;
  updateVolume(projectId: string, volumeId: string, data: Partial<CreateVolumeRequest>): Promise<void>;
  deleteVolume(projectId: string, volumeId: string): Promise<void>;
  createChapter(projectId: string, data: CreateChapterRequest): Promise<Chapter>;
  updateChapter(projectId: string, chapterId: string, data: UpdateChapterRequest): Promise<void>;
  deleteChapter(projectId: string, chapterId: string): Promise<void>;
  reorderChapters(projectId: string, orders: ReorderRequest[]): Promise<void>;
  setCurrentChapter(chapter: Chapter | null, volume?: Volume | null): void;
  getCurrentBreadcrumb(): string;
  fetchChapterContent(projectId: string, chapterId: string): Promise<void>;
  saveChapterContent(projectId: string, chapterId: string, content: string): Promise<void>;
  clearError(): void;
  invalidateCache(): void;
  // 光标位置管理 (Requirements: 4.8)
  setCursorPosition(position: number | null): void;
  insertAtCursor(projectId: string, chapterId: string, text: string): Promise<void>;
}

type ContentStore = ContentState & ContentActions;

// 缓存有效期（5分钟）
const CACHE_TTL = 5 * 60 * 1000;

const isCacheValid = (timestamp: number | null | undefined): boolean => {
  if (timestamp === null || timestamp === undefined) return false;
  return Date.now() - timestamp < CACHE_TTL;
};

export const useContentStore = create<ContentStore>()((set, get) => ({
  // 初始状态
  volumes: [],
  currentVolume: null,
  chapters: new Map(),
  currentChapter: null,
  currentContent: null,
  isLoading: false,
  isSaving: false,
  error: null,
  volumesCacheTimestamp: null,
  chaptersCacheTimestamp: new Map(),
  cursorPosition: null,

  /**
   * 获取卷列表
   */
  fetchVolumes: async (projectId: string, forceRefresh: boolean = false) => {
    const state = get();

    // 检查缓存
    if (!forceRefresh && isCacheValid(state.volumesCacheTimestamp) && state.volumes.length > 0) {
      return;
    }

    set({ isLoading: true, error: null });

    try {
      const volumes = await contentService.getVolumes(projectId);

      set({
        volumes,
        isLoading: false,
        volumesCacheTimestamp: Date.now(),
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : '获取卷列表失败';
      set({ isLoading: false, error: message });
      throw error;
    }
  },

  /**
   * 获取章节列表
   */
  fetchChapters: async (projectId: string, volumeId: string, forceRefresh: boolean = false) => {
    const state = get();

    // 检查缓存
    const cacheTimestamp = state.chaptersCacheTimestamp.get(volumeId);
    if (!forceRefresh && isCacheValid(cacheTimestamp) && state.chapters.has(volumeId)) {
      return;
    }

    set({ isLoading: true, error: null });

    try {
      const chapters = await contentService.getChapters(projectId, volumeId);

      const newChapters = new Map(state.chapters);
      newChapters.set(volumeId, chapters);

      const newTimestamps = new Map(state.chaptersCacheTimestamp);
      newTimestamps.set(volumeId, Date.now());

      set({
        chapters: newChapters,
        chaptersCacheTimestamp: newTimestamps,
        isLoading: false,
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : '获取章节列表失败';
      set({ isLoading: false, error: message });
      throw error;
    }
  },

  /**
   * 创建卷
   */
  createVolume: async (projectId: string, data: CreateVolumeRequest) => {
    set({ isLoading: true, error: null });

    try {
      const volume = await contentService.createVolume(projectId, data);

      set((state) => ({
        volumes: [...state.volumes, volume],
        isLoading: false,
      }));

      return volume;
    } catch (error) {
      const message = error instanceof Error ? error.message : '创建卷失败';
      set({ isLoading: false, error: message });
      throw error;
    }
  },

  /**
   * 更新卷
   */
  updateVolume: async (projectId: string, volumeId: string, data: Partial<CreateVolumeRequest>) => {
    const state = get();
    const originalVolume = state.volumes.find(v => v.id === volumeId);

    // 乐观更新
    if (originalVolume) {
      set((state) => ({
        volumes: state.volumes.map(v => 
          v.id === volumeId ? { ...v, ...data } : v
        ),
      }));
    }

    try {
      const updatedVolume = await contentService.updateVolume(projectId, volumeId, data);

      set((state) => ({
        volumes: state.volumes.map(v => 
          v.id === volumeId ? updatedVolume : v
        ),
      }));
    } catch (error) {
      // 回滚
      if (originalVolume) {
        set((state) => ({
          volumes: state.volumes.map(v => 
            v.id === volumeId ? originalVolume : v
          ),
        }));
      }

      const message = error instanceof Error ? error.message : '更新卷失败';
      set({ error: message });
      throw error;
    }
  },

  /**
   * 删除卷
   */
  deleteVolume: async (projectId: string, volumeId: string) => {
    const state = get();
    const originalVolumes = [...state.volumes];

    // 乐观更新
    set((state) => ({
      volumes: state.volumes.filter(v => v.id !== volumeId),
    }));

    try {
      await contentService.deleteVolume(projectId, volumeId);

      // 清除该卷的章节缓存
      const newChapters = new Map(state.chapters);
      newChapters.delete(volumeId);

      set({ chapters: newChapters });
    } catch (error) {
      // 回滚
      set({ volumes: originalVolumes });

      const message = error instanceof Error ? error.message : '删除卷失败';
      set({ error: message });
      throw error;
    }
  },

  /**
   * 创建章节
   */
  createChapter: async (projectId: string, data: CreateChapterRequest) => {
    set({ isLoading: true, error: null });

    try {
      const chapter = await contentService.createChapter(projectId, data);

      set((state) => {
        const newChapters = new Map(state.chapters);
        const volumeChapters = newChapters.get(data.volumeId) || [];
        newChapters.set(data.volumeId, [...volumeChapters, chapter]);

        return {
          chapters: newChapters,
          isLoading: false,
        };
      });

      return chapter;
    } catch (error) {
      const message = error instanceof Error ? error.message : '创建章节失败';
      set({ isLoading: false, error: message });
      throw error;
    }
  },

  /**
   * 更新章节
   */
  updateChapter: async (projectId: string, chapterId: string, data: UpdateChapterRequest) => {
    const state = get();
    
    // 找到原始章节
    let originalChapter: Chapter | undefined;
    let volumeId: string | undefined;
    
    for (const [vId, chapters] of state.chapters) {
      const chapter = chapters.find(c => c.id === chapterId);
      if (chapter) {
        originalChapter = chapter;
        volumeId = vId;
        break;
      }
    }

    // 乐观更新
    if (originalChapter && volumeId) {
      set((state) => {
        const newChapters = new Map(state.chapters);
        const volumeChapters = newChapters.get(volumeId!) || [];
        newChapters.set(volumeId!, volumeChapters.map(c => 
          c.id === chapterId ? { ...c, ...data } : c
        ));

        return {
          chapters: newChapters,
          currentChapter: state.currentChapter?.id === chapterId 
            ? { ...state.currentChapter, ...data }
            : state.currentChapter,
        };
      });
    }

    try {
      const updatedChapter = await contentService.updateChapter(projectId, chapterId, data);

      if (volumeId) {
        set((state) => {
          const newChapters = new Map(state.chapters);
          const volumeChapters = newChapters.get(volumeId!) || [];
          newChapters.set(volumeId!, volumeChapters.map(c => 
            c.id === chapterId ? updatedChapter : c
          ));

          return {
            chapters: newChapters,
            currentChapter: state.currentChapter?.id === chapterId 
              ? updatedChapter
              : state.currentChapter,
          };
        });
      }
    } catch (error) {
      // 回滚
      if (originalChapter && volumeId) {
        set((state) => {
          const newChapters = new Map(state.chapters);
          const volumeChapters = newChapters.get(volumeId!) || [];
          newChapters.set(volumeId!, volumeChapters.map(c => 
            c.id === chapterId ? originalChapter! : c
          ));

          return {
            chapters: newChapters,
            currentChapter: state.currentChapter?.id === chapterId 
              ? originalChapter
              : state.currentChapter,
          };
        });
      }

      const message = error instanceof Error ? error.message : '更新章节失败';
      set({ error: message });
      throw error;
    }
  },

  /**
   * 删除章节
   */
  deleteChapter: async (projectId: string, chapterId: string) => {
    const state = get();
    
    // 找到章节所在的卷
    let volumeId: string | undefined;
    let originalChapters: Chapter[] | undefined;
    
    for (const [vId, chapters] of state.chapters) {
      if (chapters.some(c => c.id === chapterId)) {
        volumeId = vId;
        originalChapters = [...chapters];
        break;
      }
    }

    // 乐观更新
    if (volumeId) {
      set((state) => {
        const newChapters = new Map(state.chapters);
        const volumeChapters = newChapters.get(volumeId!) || [];
        newChapters.set(volumeId!, volumeChapters.filter(c => c.id !== chapterId));

        return {
          chapters: newChapters,
          currentChapter: state.currentChapter?.id === chapterId ? null : state.currentChapter,
        };
      });
    }

    try {
      await contentService.deleteChapter(projectId, chapterId);
    } catch (error) {
      // 回滚
      if (volumeId && originalChapters) {
        set((state) => {
          const newChapters = new Map(state.chapters);
          newChapters.set(volumeId!, originalChapters!);
          return { chapters: newChapters };
        });
      }

      const message = error instanceof Error ? error.message : '删除章节失败';
      set({ error: message });
      throw error;
    }
  },

  /**
   * 重排序章节
   */
  reorderChapters: async (projectId: string, orders: ReorderRequest[]) => {
    try {
      await contentService.reorderChapters(projectId, orders);
      // 重新获取章节列表
      set({ volumesCacheTimestamp: null, chaptersCacheTimestamp: new Map() });
    } catch (error) {
      const message = error instanceof Error ? error.message : '重排序失败';
      set({ error: message });
      throw error;
    }
  },

  /**
   * 设置当前章节
   * @param chapter - 要设置的章节，null 表示清除选择
   * @param volume - 可选的卷信息，如果不提供则自动查找
   */
  setCurrentChapter: (chapter: Chapter | null, volume?: Volume | null) => {
    const state = get();
    let currentVolume = volume ?? null;
    
    // 如果没有提供 volume，尝试从 volumes 中查找
    if (chapter && !currentVolume) {
      currentVolume = state.volumes.find(v => v.id === chapter.volumeId) ?? null;
    }
    
    set({ 
      currentChapter: chapter, 
      currentVolume,
      currentContent: null 
    });
  },

  /**
   * 获取当前面包屑路径
   * @returns 格式如 "第一卷 > 第一章"
   */
  getCurrentBreadcrumb: () => {
    const state = get();
    const parts: string[] = [];
    
    if (state.currentVolume) {
      parts.push(state.currentVolume.title);
    }
    
    if (state.currentChapter) {
      parts.push(state.currentChapter.title);
    }
    
    return parts.join(' > ') || '';
  },

  /**
   * 获取章节内容
   */
  fetchChapterContent: async (projectId: string, chapterId: string) => {
    set({ isLoading: true, error: null });

    try {
      const content = await contentService.getChapterContent(projectId, chapterId);
      set({ currentContent: content, isLoading: false });
    } catch (error) {
      const message = error instanceof Error ? error.message : '获取章节内容失败';
      set({ isLoading: false, error: message });
      throw error;
    }
  },

  /**
   * 保存章节内容
   */
  saveChapterContent: async (projectId: string, chapterId: string, content: string) => {
    set({ isSaving: true, error: null });

    try {
      const savedContent = await contentService.saveChapterContent(projectId, chapterId, { content });
      set({ currentContent: savedContent, isSaving: false });
    } catch (error) {
      const message = error instanceof Error ? error.message : '保存内容失败';
      set({ isSaving: false, error: message });
      throw error;
    }
  },

  /**
   * 清除错误
   */
  clearError: () => {
    set({ error: null });
  },

  /**
   * 使缓存失效
   */
  invalidateCache: () => {
    set({
      volumesCacheTimestamp: null,
      chaptersCacheTimestamp: new Map(),
    });
  },

  /**
   * 设置光标位置
   * Requirements: 4.8
   */
  setCursorPosition: (position: number | null) => {
    set({ cursorPosition: position });
  },

  /**
   * 在光标位置插入文本
   * Requirements: 4.8
   */
  insertAtCursor: async (projectId: string, chapterId: string, text: string) => {
    const state = get();
    const existingContent = state.currentContent?.content || '';
    const cursorPos = state.cursorPosition;

    let newContent: string;
    if (cursorPos !== null && cursorPos >= 0 && cursorPos <= existingContent.length) {
      // 在光标位置插入
      newContent = existingContent.slice(0, cursorPos) + text + existingContent.slice(cursorPos);
    } else {
      // 如果没有光标位置，追加到末尾
      newContent = existingContent ? `${existingContent}\n\n${text}` : text;
    }

    await get().saveChapterContent(projectId, chapterId, newContent);
  },
}));

// 导出选择器
export const selectVolumes = (state: ContentStore) => state.volumes;
export const selectChapters = (volumeId: string) => (state: ContentStore) => state.chapters.get(volumeId) || [];
export const selectCurrentVolume = (state: ContentStore) => state.currentVolume;
export const selectCurrentChapter = (state: ContentStore) => state.currentChapter;
export const selectCurrentContent = (state: ContentStore) => state.currentContent;
export const selectIsLoading = (state: ContentStore) => state.isLoading;
export const selectIsSaving = (state: ContentStore) => state.isSaving;
export const selectError = (state: ContentStore) => state.error;
export const selectCursorPosition = (state: ContentStore) => state.cursorPosition;
