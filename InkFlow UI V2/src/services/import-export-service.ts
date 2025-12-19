/**
 * 项目导入导出服务
 * Requirements: 12.1, 12.2, 12.3, 12.4, 12.5
 */

import { getApiClient } from '../api/client';
import type { ExportData } from '../types/api';

// ============ 类型定义 ============

export interface ImportPreview {
  title: string;
  description?: string;
  volumeCount: number;
  chapterCount: number;
  wordCount: number;
  exportedAt: string;
  version: string;
}

export interface ImportResult {
  projectId: string;
  title: string;
}

// ============ 服务类 ============

export class ImportExportService {
  /**
   * 导出项目为 JSON
   */
  async exportProject(projectId: string): Promise<Blob> {
    const client = getApiClient();
    const response = await client.get<string>(
      `/api/projects/${projectId}/export`
    );
    
    // 创建 Blob 对象
    return new Blob([response.data], { type: 'application/json' });
  }

  /**
   * 下载导出文件
   */
  async downloadExport(projectId: string, filename?: string): Promise<void> {
    const blob = await this.exportProject(projectId);
    
    // 创建下载链接
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename || `inkflow-export-${projectId}.json`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }

  /**
   * 解析导入文件预览
   */
  async parseImportFile(file: File): Promise<ImportPreview> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      
      reader.onload = (e) => {
        try {
          const content = e.target?.result as string;
          const data: ExportData = JSON.parse(content);
          
          // 验证数据格式
          if (!data.metadata || !data.project) {
            throw new Error('无效的导入文件格式');
          }
          
          // 计算统计信息
          const volumeCount = data.project.volumes?.length || 0;
          let chapterCount = 0;
          let wordCount = 0;
          
          data.project.volumes?.forEach(volume => {
            chapterCount += volume.chapters?.length || 0;
            volume.chapters?.forEach(chapter => {
              chapter.blocks?.forEach(block => {
                wordCount += block.content?.length || 0;
              });
            });
          });
          
          resolve({
            title: data.project.title,
            description: data.project.description,
            volumeCount,
            chapterCount,
            wordCount,
            exportedAt: data.metadata.exportedAt,
            version: data.metadata.version,
          });
        } catch (error) {
          reject(new Error('解析文件失败: ' + (error instanceof Error ? error.message : '未知错误')));
        }
      };
      
      reader.onerror = () => {
        reject(new Error('读取文件失败'));
      };
      
      reader.readAsText(file);
    });
  }

  /**
   * 导入项目
   */
  async importProject(file: File): Promise<ImportResult> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      
      reader.onload = async (e) => {
        try {
          const content = e.target?.result as string;
          
          // 验证 JSON 格式
          JSON.parse(content);
          
          // 发送导入请求
          const client = getApiClient();
          const response = await client.post<ImportResult>(
            '/api/projects/import',
            JSON.parse(content)
          );
          
          resolve(response.data);
        } catch (error) {
          reject(new Error('导入失败: ' + (error instanceof Error ? error.message : '未知错误')));
        }
      };
      
      reader.onerror = () => {
        reject(new Error('读取文件失败'));
      };
      
      reader.readAsText(file);
    });
  }

  /**
   * 验证导入文件
   */
  validateImportFile(file: File): { valid: boolean; error?: string } {
    // 检查文件类型
    if (!file.name.endsWith('.json')) {
      return { valid: false, error: '请选择 JSON 文件' };
    }
    
    // 检查文件大小 (最大 50MB)
    const maxSize = 50 * 1024 * 1024;
    if (file.size > maxSize) {
      return { valid: false, error: '文件大小不能超过 50MB' };
    }
    
    return { valid: true };
  }
}

export const importExportService = new ImportExportService();
