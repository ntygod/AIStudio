/**
 * 项目服务
 * 封装项目相关的 API 调用
 */

import type { 
  Project, 
  CreateProjectRequest, 
  UpdateProjectRequest,
  CreationPhase,
  PagedResponse,
  ExportData
} from '@/types';
import { getApiClient } from '@/api';

export class ProjectService {
  /**
   * 获取项目列表
   */
  async getProjects(page: number = 0, size: number = 20): Promise<PagedResponse<Project>> {
    const client = getApiClient();
    const response = await client.get<PagedResponse<Project>>('/projects', {
      page: String(page),
      size: String(size),
    });
    return response.data;
  }

  /**
   * 获取单个项目
   */
  async getProject(id: string): Promise<Project> {
    const client = getApiClient();
    const response = await client.get<Project>(`/projects/${id}`);
    return response.data;
  }

  /**
   * 创建项目
   */
  async createProject(request: CreateProjectRequest): Promise<Project> {
    const client = getApiClient();
    const response = await client.post<Project>('/projects', request);
    return response.data;
  }

  /**
   * 更新项目
   */
  async updateProject(id: string, request: UpdateProjectRequest): Promise<Project> {
    const client = getApiClient();
    const response = await client.put<Project>(`/projects/${id}`, request);
    return response.data;
  }

  /**
   * 删除项目
   */
  async deleteProject(id: string): Promise<void> {
    const client = getApiClient();
    await client.delete(`/projects/${id}`);
  }

  /**
   * 更新项目阶段
   * 后端 API: PATCH /api/projects/{id}/phase?phase=XXX
   */
  async updatePhase(id: string, phase: CreationPhase): Promise<Project> {
    const client = getApiClient();
    // 后端使用 query param 而非 body
    const response = await client.patch<Project>(`/projects/${id}/phase?phase=${phase}`, {});
    return response.data;
  }

  /**
   * 导出项目
   */
  async exportProject(id: string): Promise<void> {
    const client = getApiClient();
    await client.download(`/projects/${id}/export`, `project-${id}.json`);
  }

  /**
   * 导入项目
   */
  async importProject(file: File): Promise<Project> {
    const client = getApiClient();
    const response = await client.upload<Project>('/projects/import', file);
    return response.data;
  }

  /**
   * 验证导出数据格式
   */
  validateExportData(data: unknown): data is ExportData {
    if (!data || typeof data !== 'object') return false;
    
    const obj = data as Record<string, unknown>;
    
    // 检查必要字段
    if (!obj.metadata || typeof obj.metadata !== 'object') return false;
    if (!obj.project || typeof obj.project !== 'object') return false;
    
    const metadata = obj.metadata as Record<string, unknown>;
    if (typeof metadata.version !== 'string') return false;
    if (typeof metadata.exportedAt !== 'string') return false;
    
    const project = obj.project as Record<string, unknown>;
    if (typeof project.title !== 'string') return false;
    
    return true;
  }
}

// 单例实例
export const projectService = new ProjectService();
