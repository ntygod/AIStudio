/**
 * 内容服务
 * 封装卷、章节相关的 API 调用
 */

import type { 
  Volume, 
  Chapter,
  ChapterContent,
  CreateVolumeRequest,
  CreateChapterRequest,
  UpdateChapterRequest,
  SaveContentRequest,
  ReorderRequest
} from '@/types';
import { getApiClient } from '@/api';

export class ContentService {
  // ============ 卷相关 ============

  /**
   * 获取项目的所有卷
   */
  async getVolumes(projectId: string): Promise<Volume[]> {
    const client = getApiClient();
    const response = await client.get<Volume[]>(`/projects/${projectId}/volumes`);
    return response.data;
  }

  /**
   * 创建卷
   */
  async createVolume(projectId: string, request: CreateVolumeRequest): Promise<Volume> {
    const client = getApiClient();
    const response = await client.post<Volume>(`/projects/${projectId}/volumes`, request);
    return response.data;
  }

  /**
   * 更新卷
   */
  async updateVolume(projectId: string, volumeId: string, request: Partial<CreateVolumeRequest>): Promise<Volume> {
    const client = getApiClient();
    const response = await client.put<Volume>(`/projects/${projectId}/volumes/${volumeId}`, request);
    return response.data;
  }

  /**
   * 删除卷
   */
  async deleteVolume(projectId: string, volumeId: string): Promise<void> {
    const client = getApiClient();
    await client.delete(`/projects/${projectId}/volumes/${volumeId}`);
  }

  // ============ 章节相关 ============

  /**
   * 获取卷下的所有章节
   */
  async getChapters(projectId: string, volumeId: string): Promise<Chapter[]> {
    const client = getApiClient();
    const response = await client.get<Chapter[]>(`/projects/${projectId}/chapters/volume/${volumeId}`);
    return response.data;
  }

  /**
   * 获取单个章节
   */
  async getChapter(projectId: string, chapterId: string): Promise<Chapter> {
    const client = getApiClient();
    const response = await client.get<Chapter>(`/projects/${projectId}/chapters/${chapterId}`);
    return response.data;
  }

  /**
   * 创建章节
   */
  async createChapter(projectId: string, request: CreateChapterRequest): Promise<Chapter> {
    const client = getApiClient();
    const response = await client.post<Chapter>(`/projects/${projectId}/chapters`, request);
    return response.data;
  }

  /**
   * 更新章节
   */
  async updateChapter(projectId: string, chapterId: string, request: UpdateChapterRequest): Promise<Chapter> {
    const client = getApiClient();
    const response = await client.put<Chapter>(`/projects/${projectId}/chapters/${chapterId}`, request);
    return response.data;
  }

  /**
   * 删除章节
   */
  async deleteChapter(projectId: string, chapterId: string): Promise<void> {
    const client = getApiClient();
    await client.delete(`/projects/${projectId}/chapters/${chapterId}`);
  }

  /**
   * 重排序章节
   */
  async reorderChapters(projectId: string, orders: ReorderRequest[]): Promise<void> {
    const client = getApiClient();
    await client.put(`/projects/${projectId}/chapters/reorder`, orders);
  }

  // ============ 章节内容相关 ============

  /**
   * 获取章节内容
   */
  async getChapterContent(projectId: string, chapterId: string): Promise<ChapterContent> {
    const client = getApiClient();
    const response = await client.get<ChapterContent>(`/projects/${projectId}/chapters/${chapterId}/content`);
    return response.data;
  }

  /**
   * 保存章节内容
   */
  async saveChapterContent(projectId: string, chapterId: string, request: SaveContentRequest): Promise<ChapterContent> {
    const client = getApiClient();
    const response = await client.put<ChapterContent>(`/projects/${projectId}/chapters/${chapterId}/content`, request);
    return response.data;
  }
}

// 单例实例
export const contentService = new ContentService();
