/**
 * 演进时间线服务
 * 封装演进追踪相关的 API 调用
 * 
 * Requirements: 8.1, 8.3, 8.4
 */

import { getApiClient } from '@/api';

// ============ 类型定义 ============

export type EvolutionEntityType = 'CHARACTER' | 'WIKI_ENTRY' | 'RELATIONSHIP';
export type ChangeType = 'INITIAL' | 'UPDATE' | 'MAJOR_CHANGE';

export interface ChangeRecord {
  id: string;
  snapshotId: string;
  fieldPath: string;
  oldValue: string | null;
  newValue: string | null;
  changeReason?: string;
  sourceText?: string;
  createdAt: string;
}

export interface StateSnapshot {
  id: string;
  timelineId: string;
  chapterId?: string;
  chapterOrder?: number;
  isKeyframe: boolean;
  stateData: Record<string, unknown>;
  changeSummary?: string;
  changeType: ChangeType;
  aiConfidence?: number;
  createdAt: string;
  changeRecords?: ChangeRecord[];
}

export interface EvolutionTimeline {
  id: string;
  projectId: string;
  entityType: EvolutionEntityType;
  entityId: string;
  createdAt: string;
  updatedAt: string;
  snapshots?: StateSnapshot[];
}

export interface StateChange {
  fieldPath: string;
  oldValue: unknown;
  newValue: unknown;
  changeReason?: string;
  sourceText?: string;
}

export interface StateCompareResult {
  [fieldPath: string]: StateChange;
}

export interface EvolutionTrackPoint {
  chapterOrder: number;
  state: Record<string, unknown>;
  changeType?: ChangeType;
  changeSummary?: string;
}

export interface CreateSnapshotRequest {
  entityType: EvolutionEntityType;
  entityId: string;
  description: string;
  chapterId?: string;
  isKeyframe?: boolean;
}

// ============ 服务类 ============

export class EvolutionService {
  /**
   * 获取实体的所有快照
   * Requirements: 8.1
   */
  async getSnapshots(
    entityType: EvolutionEntityType,
    entityId: string
  ): Promise<StateSnapshot[]> {
    const client = getApiClient();
    const response = await client.get<StateSnapshot[]>('/evolution/snapshots', {
      entityType,
      entityId,
    });
    return response.data;
  }

  /**
   * 获取快照详情
   */
  async getSnapshotDetails(snapshotId: string): Promise<StateSnapshot> {
    const client = getApiClient();
    const response = await client.get<StateSnapshot>(`/evolution/snapshots/${snapshotId}`);
    return response.data;
  }

  /**
   * 获取实体在指定章节的状态
   * Requirements: 8.3
   */
  async getStateAtChapter(
    entityType: EvolutionEntityType,
    entityId: string,
    chapterOrder: number
  ): Promise<Record<string, unknown> | null> {
    const client = getApiClient();
    try {
      const response = await client.get<Record<string, unknown>>('/evolution/state', {
        entityType,
        entityId,
        chapterOrder: String(chapterOrder),
      });
      return response.data;
    } catch {
      return null;
    }
  }

  /**
   * 获取实体的最新状态
   */
  async getLatestState(
    entityType: EvolutionEntityType,
    entityId: string
  ): Promise<Record<string, unknown> | null> {
    const client = getApiClient();
    try {
      const response = await client.get<Record<string, unknown>>('/evolution/state/latest', {
        entityType,
        entityId,
      });
      return response.data;
    } catch {
      return null;
    }
  }

  /**
   * 比较两个章节之间的状态差异
   * Requirements: 8.4
   */
  async compareStates(
    entityType: EvolutionEntityType,
    entityId: string,
    fromChapterOrder: number,
    toChapterOrder: number
  ): Promise<StateCompareResult> {
    const client = getApiClient();
    const response = await client.get<StateCompareResult>('/evolution/compare', {
      entityType,
      entityId,
      fromChapterOrder: String(fromChapterOrder),
      toChapterOrder: String(toChapterOrder),
    });
    return response.data;
  }

  /**
   * 获取实体的演进轨迹
   */
  async getEvolutionTrack(
    entityType: EvolutionEntityType,
    entityId: string,
    fromChapterOrder: number,
    toChapterOrder: number
  ): Promise<EvolutionTrackPoint[]> {
    const client = getApiClient();
    const response = await client.get<EvolutionTrackPoint[]>('/evolution/track', {
      entityType,
      entityId,
      fromChapterOrder: String(fromChapterOrder),
      toChapterOrder: String(toChapterOrder),
    });
    return response.data;
  }

  /**
   * 手动创建演进快照
   * Requirements: 6.5, 6.6
   */
  async createSnapshot(request: CreateSnapshotRequest): Promise<StateSnapshot> {
    const client = getApiClient();
    const response = await client.post<StateSnapshot>('/evolution/snapshots', request);
    return response.data;
  }
}

// 单例实例
export const evolutionService = new EvolutionService();
