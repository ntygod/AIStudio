/**
 * 进度服务
 * 封装进度统计相关的 API 调用
 * 
 * Requirements: 7.1, 7.2, 7.3
 */

import { getApiClient } from '@/api';

// ============ 类型定义 ============

export interface CreationProgress {
  projectId: string;
  currentPhase: string;
  characterCount: number;
  wikiEntryCount: number;
  volumeCount: number;
  chapterCount: number;
  wordCount: number;
  plotLoopCount: number;
  openPlotLoops: number;
  closedPlotLoops: number;
}

export interface WordCountStatistics {
  projectId: string;
  totalWords: number;
  averageWordsPerChapter: number;
  dailyAverageWords: number;
  chapterCount: number;
}

export interface TrendDataPoint {
  period: string;
  wordCount: number;
  wordCountChange: number;
  chapterCount: number;
  characterCount: number;
}

export interface ProgressTrend {
  projectId: string;
  period: 'DAILY' | 'WEEKLY' | 'MONTHLY';
  dataPoints: TrendDataPoint[];
}

export interface ProgressStatistics {
  projectId: string;
  currentPhase: string;
  characterCount: number;
  wikiEntryCount: number;
  volumeCount: number;
  chapterCount: number;
  wordCount: number;
  plotLoopCount: number;
  openPlotLoops: number;
  closedPlotLoops: number;
}

export interface DailyWordCount {
  date: string;
  wordCount: number;
  wordCountChange: number;
}

export class ProgressService {
  /**
   * 获取当前进度
   */
  async getProgress(projectId: string): Promise<CreationProgress> {
    const client = getApiClient();
    const response = await client.get<CreationProgress>(`/projects/${projectId}/progress`);
    return response.data;
  }

  /**
   * 获取进度统计
   */
  async getStatistics(projectId: string): Promise<ProgressStatistics> {
    const client = getApiClient();
    const response = await client.get<ProgressStatistics>(`/projects/${projectId}/progress/statistics`);
    return response.data;
  }

  /**
   * 获取字数统计
   */
  async getWordCountStats(projectId: string): Promise<WordCountStatistics> {
    const client = getApiClient();
    const response = await client.get<WordCountStatistics>(`/projects/${projectId}/progress/word-count`);
    return response.data;
  }

  /**
   * 获取进度趋势
   */
  async getTrends(projectId: string, period: 'DAILY' | 'WEEKLY' | 'MONTHLY' = 'DAILY'): Promise<ProgressTrend> {
    const client = getApiClient();
    const response = await client.get<ProgressTrend>(`/projects/${projectId}/progress/trends`, {
      period,
    });
    return response.data;
  }

  /**
   * 获取周活动数据（最近7天的字数变化）
   */
  async getWeeklyActivity(projectId: string): Promise<DailyWordCount[]> {
    const trend = await this.getTrends(projectId, 'DAILY');
    return trend.dataPoints.map(dp => ({
      date: dp.period,
      wordCount: dp.wordCount,
      wordCountChange: dp.wordCountChange,
    }));
  }
}

// 单例实例
export const progressService = new ProgressService();
