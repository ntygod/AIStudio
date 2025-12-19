/**
 * 资产服务
 * 封装角色、Wiki、PlotLoop 相关的 API 调用
 */

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
  UpdatePlotLoopRequest
} from '@/types';
import { getApiClient } from '@/api';

export class AssetService {
  // ============ 角色相关 ============

  /**
   * 获取项目的所有角色
   */
  async getCharacters(projectId: string): Promise<Character[]> {
    const client = getApiClient();
    const response = await client.get<Character[]>(`/characters/project/${projectId}`);
    return response.data;
  }

  /**
   * 获取单个角色
   */
  async getCharacter(id: string): Promise<Character> {
    const client = getApiClient();
    const response = await client.get<Character>(`/characters/${id}`);
    return response.data;
  }

  /**
   * 创建角色
   */
  async createCharacter(request: CreateCharacterRequest): Promise<Character> {
    const client = getApiClient();
    const response = await client.post<Character>('/characters', request);
    return response.data;
  }

  /**
   * 更新角色
   */
  async updateCharacter(id: string, request: UpdateCharacterRequest): Promise<Character> {
    const client = getApiClient();
    const response = await client.put<Character>(`/characters/${id}`, request);
    return response.data;
  }

  /**
   * 删除角色
   */
  async deleteCharacter(id: string): Promise<void> {
    const client = getApiClient();
    await client.delete(`/characters/${id}`);
  }

  /**
   * 获取角色关系图
   */
  async getRelationshipGraph(projectId: string): Promise<RelationshipGraph> {
    const client = getApiClient();
    const response = await client.get<RelationshipGraph>(`/characters/project/${projectId}/graph`);
    return response.data;
  }

  /**
   * 添加角色关系
   */
  async addRelationship(characterId: string, targetId: string, type: string, description?: string): Promise<void> {
    const client = getApiClient();
    await client.post(`/characters/${characterId}/relationships`, {
      targetId,
      type,
      description,
    });
  }

  // ============ Wiki 相关 ============

  /**
   * 获取项目的所有 Wiki 条目
   */
  async getWikiEntries(projectId: string): Promise<WikiEntry[]> {
    const client = getApiClient();
    const response = await client.get<WikiEntry[]>(`/wiki/project/${projectId}`);
    return response.data;
  }

  /**
   * 搜索 Wiki 条目
   */
  async searchWikiEntries(projectId: string, keyword: string): Promise<WikiEntry[]> {
    const client = getApiClient();
    const response = await client.get<WikiEntry[]>(`/wiki/project/${projectId}/search`, { keyword });
    return response.data;
  }

  /**
   * 按类型获取 Wiki 条目
   */
  async getWikiEntriesByType(projectId: string, type: string): Promise<WikiEntry[]> {
    const client = getApiClient();
    const response = await client.get<WikiEntry[]>(`/wiki/project/${projectId}/type/${type}`);
    return response.data;
  }

  /**
   * 创建 Wiki 条目
   */
  async createWikiEntry(request: CreateWikiEntryRequest): Promise<WikiEntry> {
    const client = getApiClient();
    const response = await client.post<WikiEntry>('/wiki', request);
    return response.data;
  }

  /**
   * 更新 Wiki 条目
   */
  async updateWikiEntry(id: string, request: UpdateWikiEntryRequest): Promise<WikiEntry> {
    const client = getApiClient();
    const response = await client.put<WikiEntry>(`/wiki/${id}`, request);
    return response.data;
  }

  /**
   * 删除 Wiki 条目
   */
  async deleteWikiEntry(id: string): Promise<void> {
    const client = getApiClient();
    await client.delete(`/wiki/${id}`);
  }

  // ============ PlotLoop 相关 ============

  /**
   * 获取项目的所有伏笔
   */
  async getPlotLoops(projectId: string): Promise<PlotLoop[]> {
    const client = getApiClient();
    const response = await client.get<PlotLoop[]>(`/plotloops/project/${projectId}`);
    return response.data;
  }

  /**
   * 创建伏笔
   */
  async createPlotLoop(request: CreatePlotLoopRequest): Promise<PlotLoop> {
    const client = getApiClient();
    const response = await client.post<PlotLoop>('/plotloops', request);
    return response.data;
  }

  /**
   * 更新伏笔
   */
  async updatePlotLoop(id: string, request: UpdatePlotLoopRequest): Promise<PlotLoop> {
    const client = getApiClient();
    const response = await client.put<PlotLoop>(`/plotloops/${id}`, request);
    return response.data;
  }

  /**
   * 更新伏笔状态
   */
  async updatePlotLoopStatus(id: string, status: string, resolutionChapterId?: string, abandonReason?: string): Promise<PlotLoop> {
    const client = getApiClient();
    const response = await client.patch<PlotLoop>(`/plotloops/${id}/status`, {
      status,
      resolutionChapterId,
      abandonReason,
    });
    return response.data;
  }

  /**
   * 删除伏笔
   */
  async deletePlotLoop(id: string): Promise<void> {
    const client = getApiClient();
    await client.delete(`/plotloops/${id}`);
  }

  /**
   * 关联伏笔到章节
   */
  async linkPlotLoopToChapter(plotLoopId: string, chapterId: string): Promise<void> {
    const client = getApiClient();
    await client.post(`/plotloops/${plotLoopId}/chapters/${chapterId}`, {});
  }
}

// 单例实例
export const assetService = new AssetService();
