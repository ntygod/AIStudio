/**
 * 章节快照服务
 * 处理版本历史相关的 API 调用
 * 
 * Requirements: 8.1, 8.2, 8.3, 8.4, 8.5
 */

import { getApiClient } from '@/api/client';
import type { 
  ChapterSnapshot, 
  CreateSnapshotRequest,
  SnapshotDiff 
} from '@/types';

class SnapshotService {
  private get apiClient() {
    return getApiClient();
  }

  /**
   * 获取章节的快照列表
   */
  async getSnapshots(projectId: string, chapterId: string): Promise<ChapterSnapshot[]> {
    const response = await this.apiClient.get<ChapterSnapshot[]>(
      `/projects/${projectId}/chapters/${chapterId}/snapshots`
    );
    return response.data;
  }

  /**
   * 获取单个快照详情
   */
  async getSnapshot(projectId: string, chapterId: string, snapshotId: string): Promise<ChapterSnapshot> {
    const response = await this.apiClient.get<ChapterSnapshot>(
      `/projects/${projectId}/chapters/${chapterId}/snapshots/${snapshotId}`
    );
    return response.data;
  }

  /**
   * 创建快照
   */
  async createSnapshot(
    projectId: string, 
    chapterId: string, 
    data: CreateSnapshotRequest
  ): Promise<ChapterSnapshot> {
    const response = await this.apiClient.post<ChapterSnapshot>(
      `/projects/${projectId}/chapters/${chapterId}/snapshots`,
      data
    );
    return response.data;
  }

  /**
   * 删除快照
   */
  async deleteSnapshot(projectId: string, chapterId: string, snapshotId: string): Promise<void> {
    await this.apiClient.delete(
      `/projects/${projectId}/chapters/${chapterId}/snapshots/${snapshotId}`
    );
  }

  /**
   * 获取快照与当前内容的差异
   */
  async getDiff(
    projectId: string, 
    chapterId: string, 
    snapshotId: string
  ): Promise<SnapshotDiff> {
    const response = await this.apiClient.get<SnapshotDiff>(
      `/projects/${projectId}/chapters/${chapterId}/snapshots/${snapshotId}/diff`
    );
    return response.data;
  }

  /**
   * 恢复到指定快照
   */
  async restoreSnapshot(
    projectId: string, 
    chapterId: string, 
    snapshotId: string
  ): Promise<ChapterSnapshot> {
    const response = await this.apiClient.post<ChapterSnapshot>(
      `/projects/${projectId}/chapters/${chapterId}/snapshots/${snapshotId}/restore`
    );
    return response.data;
  }

  /**
   * 比较两个快照
   */
  async compareSnapshots(
    projectId: string,
    chapterId: string,
    fromSnapshotId: string,
    toSnapshotId: string
  ): Promise<SnapshotDiff> {
    const response = await this.apiClient.get<SnapshotDiff>(
      `/projects/${projectId}/chapters/${chapterId}/snapshots/compare`,
      { from: fromSnapshotId, to: toSnapshotId }
    );
    return response.data;
  }
}

export const snapshotService = new SnapshotService();
