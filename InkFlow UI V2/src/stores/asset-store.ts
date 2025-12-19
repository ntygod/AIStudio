/**
 * 资产状态管理
 * 使用 Zustand 管理角色、Wiki、PlotLoop 数据
 */

import { create } from 'zustand';
import type { 
  Character,
  CreateCharacterRequest,
  UpdateCharacterRequest,
  RelationshipGraph,
  WikiEntry,
  CreateWikiEntryRequest,
  UpdateWikiEntryRequest,
  PlotLoop,
  CreatePlotLoopRequest,
  PlotLoopStatus
} from '@/types';
import { assetService } from '@/services/asset-service';

interface AssetState {
  characters: Character[];
  wikiEntries: WikiEntry[];
  plotLoops: PlotLoop[];
  relationshipGraph: RelationshipGraph | null;
  isLoading: boolean;
  error: string | null;
  // 缓存时间戳
  charactersCacheTimestamp: number | null;
  wikiCacheTimestamp: number | null;
  plotLoopsCacheTimestamp: number | null;
}

interface AssetActions {
  // 角色
  fetchCharacters(projectId: string, forceRefresh?: boolean): Promise<void>;
  createCharacter(data: CreateCharacterRequest): Promise<Character>;
  updateCharacter(id: string, data: UpdateCharacterRequest): Promise<void>;
  deleteCharacter(id: string): Promise<void>;
  fetchRelationshipGraph(projectId: string): Promise<void>;
  addRelationship(characterId: string, targetId: string, type: string, description?: string): Promise<void>;
  // Wiki
  fetchWikiEntries(projectId: string, forceRefresh?: boolean): Promise<void>;
  searchWikiEntries(projectId: string, keyword: string): Promise<WikiEntry[]>;
  createWikiEntry(data: CreateWikiEntryRequest): Promise<WikiEntry>;
  updateWikiEntry(id: string, data: UpdateWikiEntryRequest): Promise<void>;
  deleteWikiEntry(id: string): Promise<void>;
  // PlotLoop
  fetchPlotLoops(projectId: string, forceRefresh?: boolean): Promise<void>;
  createPlotLoop(data: CreatePlotLoopRequest): Promise<PlotLoop>;
  resolvePlotLoop(id: string, resolutionChapterId?: string): Promise<void>;
  abandonPlotLoop(id: string, reason: string): Promise<void>;
  deletePlotLoop(id: string): Promise<void>;
  // 通用
  clearError(): void;
  invalidateCache(): void;
}

type AssetStore = AssetState & AssetActions;

// 缓存有效期（5分钟）
const CACHE_TTL = 5 * 60 * 1000;

const isCacheValid = (timestamp: number | null): boolean => {
  if (!timestamp) return false;
  return Date.now() - timestamp < CACHE_TTL;
};

export const useAssetStore = create<AssetStore>()((set, get) => ({
  // 初始状态
  characters: [],
  wikiEntries: [],
  plotLoops: [],
  relationshipGraph: null,
  isLoading: false,
  error: null,
  charactersCacheTimestamp: null,
  wikiCacheTimestamp: null,
  plotLoopsCacheTimestamp: null,

  // ============ 角色相关 ============

  fetchCharacters: async (projectId: string, forceRefresh: boolean = false) => {
    const state = get();

    if (!forceRefresh && isCacheValid(state.charactersCacheTimestamp) && state.characters.length > 0) {
      return;
    }

    set({ isLoading: true, error: null });

    try {
      const characters = await assetService.getCharacters(projectId);
      set({
        characters,
        isLoading: false,
        charactersCacheTimestamp: Date.now(),
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : '获取角色列表失败';
      set({ isLoading: false, error: message });
      throw error;
    }
  },

  createCharacter: async (data: CreateCharacterRequest) => {
    set({ isLoading: true, error: null });

    try {
      const character = await assetService.createCharacter(data);
      set((state) => ({
        characters: [...state.characters, character],
        isLoading: false,
      }));
      return character;
    } catch (error) {
      const message = error instanceof Error ? error.message : '创建角色失败';
      set({ isLoading: false, error: message });
      throw error;
    }
  },

  updateCharacter: async (id: string, data: UpdateCharacterRequest) => {
    const state = get();
    const original = state.characters.find(c => c.id === id);

    // 乐观更新
    if (original) {
      set((state) => ({
        characters: state.characters.map(c => c.id === id ? { ...c, ...data } : c),
      }));
    }

    try {
      const updated = await assetService.updateCharacter(id, data);
      set((state) => ({
        characters: state.characters.map(c => c.id === id ? updated : c),
      }));
    } catch (error) {
      // 回滚
      if (original) {
        set((state) => ({
          characters: state.characters.map(c => c.id === id ? original : c),
        }));
      }
      const message = error instanceof Error ? error.message : '更新角色失败';
      set({ error: message });
      throw error;
    }
  },

  deleteCharacter: async (id: string) => {
    const state = get();
    const original = [...state.characters];

    // 乐观更新
    set((state) => ({
      characters: state.characters.filter(c => c.id !== id),
    }));

    try {
      await assetService.deleteCharacter(id);
    } catch (error) {
      // 回滚
      set({ characters: original });
      const message = error instanceof Error ? error.message : '删除角色失败';
      set({ error: message });
      throw error;
    }
  },

  fetchRelationshipGraph: async (projectId: string) => {
    set({ isLoading: true, error: null });

    try {
      const graph = await assetService.getRelationshipGraph(projectId);
      set({ relationshipGraph: graph, isLoading: false });
    } catch (error) {
      const message = error instanceof Error ? error.message : '获取关系图失败';
      set({ isLoading: false, error: message });
      throw error;
    }
  },

  addRelationship: async (characterId: string, targetId: string, type: string, description?: string) => {
    try {
      await assetService.addRelationship(characterId, targetId, type, description);
      // 刷新关系图
      set({ relationshipGraph: null });
    } catch (error) {
      const message = error instanceof Error ? error.message : '添加关系失败';
      set({ error: message });
      throw error;
    }
  },

  // ============ Wiki 相关 ============

  fetchWikiEntries: async (projectId: string, forceRefresh: boolean = false) => {
    const state = get();

    if (!forceRefresh && isCacheValid(state.wikiCacheTimestamp) && state.wikiEntries.length > 0) {
      return;
    }

    set({ isLoading: true, error: null });

    try {
      const entries = await assetService.getWikiEntries(projectId);
      set({
        wikiEntries: entries,
        isLoading: false,
        wikiCacheTimestamp: Date.now(),
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : '获取Wiki列表失败';
      set({ isLoading: false, error: message });
      throw error;
    }
  },

  searchWikiEntries: async (projectId: string, keyword: string) => {
    try {
      return await assetService.searchWikiEntries(projectId, keyword);
    } catch (error) {
      const message = error instanceof Error ? error.message : '搜索Wiki失败';
      set({ error: message });
      throw error;
    }
  },

  createWikiEntry: async (data: CreateWikiEntryRequest) => {
    set({ isLoading: true, error: null });

    try {
      const entry = await assetService.createWikiEntry(data);
      set((state) => ({
        wikiEntries: [...state.wikiEntries, entry],
        isLoading: false,
      }));
      return entry;
    } catch (error) {
      const message = error instanceof Error ? error.message : '创建Wiki条目失败';
      set({ isLoading: false, error: message });
      throw error;
    }
  },

  updateWikiEntry: async (id: string, data: UpdateWikiEntryRequest) => {
    const state = get();
    const original = state.wikiEntries.find(e => e.id === id);

    // 乐观更新
    if (original) {
      set((state) => ({
        wikiEntries: state.wikiEntries.map(e => e.id === id ? { ...e, ...data } : e),
      }));
    }

    try {
      const updated = await assetService.updateWikiEntry(id, data);
      set((state) => ({
        wikiEntries: state.wikiEntries.map(e => e.id === id ? updated : e),
      }));
    } catch (error) {
      // 回滚
      if (original) {
        set((state) => ({
          wikiEntries: state.wikiEntries.map(e => e.id === id ? original : e),
        }));
      }
      const message = error instanceof Error ? error.message : '更新Wiki条目失败';
      set({ error: message });
      throw error;
    }
  },

  deleteWikiEntry: async (id: string) => {
    const state = get();
    const original = [...state.wikiEntries];

    // 乐观更新
    set((state) => ({
      wikiEntries: state.wikiEntries.filter(e => e.id !== id),
    }));

    try {
      await assetService.deleteWikiEntry(id);
    } catch (error) {
      // 回滚
      set({ wikiEntries: original });
      const message = error instanceof Error ? error.message : '删除Wiki条目失败';
      set({ error: message });
      throw error;
    }
  },

  // ============ PlotLoop 相关 ============

  fetchPlotLoops: async (projectId: string, forceRefresh: boolean = false) => {
    const state = get();

    if (!forceRefresh && isCacheValid(state.plotLoopsCacheTimestamp) && state.plotLoops.length > 0) {
      return;
    }

    set({ isLoading: true, error: null });

    try {
      const loops = await assetService.getPlotLoops(projectId);
      set({
        plotLoops: loops,
        isLoading: false,
        plotLoopsCacheTimestamp: Date.now(),
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : '获取伏笔列表失败';
      set({ isLoading: false, error: message });
      throw error;
    }
  },

  createPlotLoop: async (data: CreatePlotLoopRequest) => {
    set({ isLoading: true, error: null });

    try {
      const loop = await assetService.createPlotLoop(data);
      set((state) => ({
        plotLoops: [...state.plotLoops, loop],
        isLoading: false,
      }));
      return loop;
    } catch (error) {
      const message = error instanceof Error ? error.message : '创建伏笔失败';
      set({ isLoading: false, error: message });
      throw error;
    }
  },

  resolvePlotLoop: async (id: string, resolutionChapterId?: string) => {
    const state = get();
    const original = state.plotLoops.find(l => l.id === id);

    // 乐观更新
    if (original) {
      set((state) => ({
        plotLoops: state.plotLoops.map(l => 
          l.id === id ? { ...l, status: 'RESOLVED' as PlotLoopStatus, resolutionChapterId } : l
        ),
      }));
    }

    try {
      const updated = await assetService.updatePlotLoopStatus(id, 'RESOLVED', resolutionChapterId);
      set((state) => ({
        plotLoops: state.plotLoops.map(l => l.id === id ? updated : l),
      }));
    } catch (error) {
      // 回滚
      if (original) {
        set((state) => ({
          plotLoops: state.plotLoops.map(l => l.id === id ? original : l),
        }));
      }
      const message = error instanceof Error ? error.message : '解决伏笔失败';
      set({ error: message });
      throw error;
    }
  },

  abandonPlotLoop: async (id: string, reason: string) => {
    const state = get();
    const original = state.plotLoops.find(l => l.id === id);

    // 乐观更新
    if (original) {
      set((state) => ({
        plotLoops: state.plotLoops.map(l => 
          l.id === id ? { ...l, status: 'ABANDONED' as PlotLoopStatus, abandonReason: reason } : l
        ),
      }));
    }

    try {
      const updated = await assetService.updatePlotLoopStatus(id, 'ABANDONED', undefined, reason);
      set((state) => ({
        plotLoops: state.plotLoops.map(l => l.id === id ? updated : l),
      }));
    } catch (error) {
      // 回滚
      if (original) {
        set((state) => ({
          plotLoops: state.plotLoops.map(l => l.id === id ? original : l),
        }));
      }
      const message = error instanceof Error ? error.message : '放弃伏笔失败';
      set({ error: message });
      throw error;
    }
  },

  deletePlotLoop: async (id: string) => {
    const state = get();
    const original = [...state.plotLoops];

    // 乐观更新
    set((state) => ({
      plotLoops: state.plotLoops.filter(l => l.id !== id),
    }));

    try {
      await assetService.deletePlotLoop(id);
    } catch (error) {
      // 回滚
      set({ plotLoops: original });
      const message = error instanceof Error ? error.message : '删除伏笔失败';
      set({ error: message });
      throw error;
    }
  },

  // ============ 通用 ============

  clearError: () => {
    set({ error: null });
  },

  invalidateCache: () => {
    set({
      charactersCacheTimestamp: null,
      wikiCacheTimestamp: null,
      plotLoopsCacheTimestamp: null,
    });
  },
}));

// 导出选择器
export const selectCharacters = (state: AssetStore) => state.characters;
export const selectWikiEntries = (state: AssetStore) => state.wikiEntries;
export const selectPlotLoops = (state: AssetStore) => state.plotLoops;
export const selectRelationshipGraph = (state: AssetStore) => state.relationshipGraph;
export const selectIsLoading = (state: AssetStore) => state.isLoading;
export const selectError = (state: AssetStore) => state.error;
