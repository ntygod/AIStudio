/**
 * 写作风格服务
 * Requirements: 9.1, 9.2, 9.3, 9.4, 9.5
 */

import { getApiClient } from '../api/client';

// ============ 类型定义 ============

export interface StyleStats {
  sampleCount: number;
  averageEditRatio: number;
  totalWordCount: number;
}

export interface StyleSample {
  id: string;
  projectId: string;
  chapterId: string;
  originalAI: string;
  userFinal: string;
  editRatio: number;
  wordCount: number;
  createdAt: string;
}

export interface SaveStyleSampleRequest {
  chapterId: string;
  originalAI: string;
  userFinal: string;
}

export interface EditRatioResult {
  editRatio: number;
  shouldSave: boolean;
}

// ============ 服务类 ============

export class StyleService {
  /**
   * 获取风格统计
   */
  async getStyleStats(projectId: string): Promise<StyleStats> {
    const client = getApiClient();
    const response = await client.get<StyleStats>(
      `/api/projects/${projectId}/style/stats`
    );
    return response.data;
  }

  /**
   * 获取风格样本列表
   */
  async getStyleSamples(projectId: string): Promise<StyleSample[]> {
    const client = getApiClient();
    const response = await client.get<StyleSample[]>(
      `/api/projects/${projectId}/style/samples`
    );
    return response.data;
  }

  /**
   * 保存风格样本
   */
  async saveStyleSample(
    projectId: string,
    request: SaveStyleSampleRequest
  ): Promise<StyleSample | null> {
    const client = getApiClient();
    const response = await client.post<StyleSample>(
      `/api/projects/${projectId}/style/samples`,
      request
    );
    return response.data;
  }

  /**
   * 删除风格样本
   */
  async deleteStyleSample(projectId: string, sampleId: string): Promise<void> {
    const client = getApiClient();
    await client.delete(`/api/projects/${projectId}/style/samples/${sampleId}`);
  }

  /**
   * 搜索相似风格样本
   */
  async searchSimilarSamples(
    projectId: string,
    context: string,
    limit: number = 5
  ): Promise<StyleSample[]> {
    const client = getApiClient();
    const response = await client.post<StyleSample[]>(
      `/api/projects/${projectId}/style/samples/search`,
      { context, limit }
    );
    return response.data;
  }

  /**
   * 计算编辑比例
   */
  async calculateEditRatio(
    projectId: string,
    original: string,
    modified: string
  ): Promise<EditRatioResult> {
    const client = getApiClient();
    const response = await client.post<EditRatioResult>(
      `/api/projects/${projectId}/style/calculate-edit-ratio`,
      { original, modified }
    );
    return response.data;
  }
}

export const styleService = new StyleService();
