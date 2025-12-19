/**
 * 一致性警告服务
 * 封装一致性检查相关的 API 调用
 * 
 * Requirements: 7.1, 7.4, 7.5
 */

import { getApiClient } from '@/api';

// ============ 类型定义 ============

export type EntityType = 'CHARACTER' | 'WIKI_ENTRY' | 'PLOT_LOOP' | 'CHAPTER';
export type WarningType = 'NAME_CONFLICT' | 'MISSING_FIELD' | 'RELATIONSHIP_INCONSISTENCY' | 'TIMELINE_CONFLICT' | 'PLOT_HOLE' | 'CHARACTER_INCONSISTENCY';
export type Severity = 'ERROR' | 'WARNING' | 'INFO';

export interface ConsistencyWarning {
  id: string;
  projectId: string;
  entityId: string;
  entityType: EntityType;
  entityName: string;
  warningType: WarningType;
  severity: Severity;
  description: string;
  suggestion?: string;
  fieldPath?: string;
  expectedValue?: string;
  actualValue?: string;
  relatedEntityIds?: string[];
  suggestedResolution?: string;
  resolved: boolean;
  dismissed: boolean;
  resolutionMethod?: string;
  resolvedAt?: string;
  createdAt: string;
}

export interface WarningCount {
  total: number;
  error: number;
  warning: number;
  info: number;
}

export interface ResolveWarningRequest {
  resolutionMethod: string;
}

export interface BulkResolveRequest {
  warningIds: string[];
  resolutionMethod: string;
}

// ============ 服务类 ============

export class ConsistencyService {
  /**
   * 获取项目的未解决警告数量
   * Requirements: 7.1
   */
  async getWarningCount(projectId: string): Promise<WarningCount> {
    const client = getApiClient();
    const response = await client.get<WarningCount>(`/consistency/projects/${projectId}/warnings/count`);
    return response.data;
  }

  /**
   * 获取项目的未解决警告列表
   * Requirements: 7.1
   */
  async getWarnings(
    projectId: string,
    entityType?: EntityType,
    warningType?: WarningType
  ): Promise<ConsistencyWarning[]> {
    const client = getApiClient();
    const params: Record<string, string> = {};
    
    if (entityType) {
      params.entityType = entityType;
    }
    if (warningType) {
      params.warningType = warningType;
    }
    
    const response = await client.get<ConsistencyWarning[]>(
      `/consistency/projects/${projectId}/warnings`,
      Object.keys(params).length > 0 ? params : undefined
    );
    return response.data;
  }

  /**
   * 获取警告详情
   */
  async getWarningDetails(warningId: string): Promise<ConsistencyWarning> {
    const client = getApiClient();
    const response = await client.get<ConsistencyWarning>(`/consistency/warnings/${warningId}`);
    return response.data;
  }

  /**
   * 解决警告
   * Requirements: 7.4
   */
  async resolveWarning(warningId: string, resolutionMethod: string): Promise<ConsistencyWarning> {
    const client = getApiClient();
    const response = await client.post<ConsistencyWarning>(
      `/consistency/warnings/${warningId}/resolve`,
      { resolutionMethod }
    );
    return response.data;
  }

  /**
   * 忽略警告
   * Requirements: 7.5
   */
  async dismissWarning(warningId: string): Promise<ConsistencyWarning> {
    const client = getApiClient();
    const response = await client.post<ConsistencyWarning>(
      `/consistency/warnings/${warningId}/dismiss`
    );
    return response.data;
  }

  /**
   * 批量解决警告
   * Requirements: 7.4
   */
  async bulkResolve(warningIds: string[], resolutionMethod: string): Promise<number> {
    const client = getApiClient();
    const response = await client.post<{ resolved: number }>(
      '/consistency/warnings/bulk-resolve',
      { warningIds, resolutionMethod }
    );
    return response.data.resolved;
  }

  /**
   * 批量忽略警告
   * Requirements: 7.5
   */
  async bulkDismiss(warningIds: string[]): Promise<number> {
    const client = getApiClient();
    const response = await client.post<{ dismissed: number }>(
      '/consistency/warnings/bulk-dismiss',
      warningIds
    );
    return response.data.dismissed;
  }

  /**
   * 获取实体的所有警告
   */
  async getWarningsByEntity(entityId: string): Promise<ConsistencyWarning[]> {
    const client = getApiClient();
    const response = await client.get<ConsistencyWarning[]>(
      `/consistency/entities/${entityId}/warnings`
    );
    return response.data;
  }

  /**
   * 解决实体的所有警告
   */
  async resolveWarningsByEntity(entityId: string, resolutionMethod: string): Promise<number> {
    const client = getApiClient();
    const response = await client.post<{ resolved: number }>(
      `/consistency/entities/${entityId}/warnings/resolve`,
      { resolutionMethod }
    );
    return response.data.resolved;
  }
}

// 单例实例
export const consistencyService = new ConsistencyService();
