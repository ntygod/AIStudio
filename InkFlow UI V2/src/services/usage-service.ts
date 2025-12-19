/**
 * Token 使用量服务
 * 封装 Token 使用量相关的 API 调用
 * 
 * Requirements: 10.1, 10.2, 10.3, 10.4
 */

import { getApiClient } from '@/api/client';

export interface TokenUsage {
  todayUsage: number;
  dailyQuota: number;
  remainingQuota: number;
  usagePercentage: number;
}

export interface DailyUsage {
  date: string;
  usage: number;
}

export interface OperationUsage {
  operation: string;
  usage: number;
  percentage: number;
}

export interface TokenUsageDetail {
  todayUsage: number;
  dailyQuota: number;
  weeklyTrend: DailyUsage[];
  breakdown: OperationUsage[];
}

class UsageService {
  /**
   * 获取今日使用量概览
   * Requirements: 10.1, 10.2
   */
  async getTodayUsage(): Promise<TokenUsage> {
    const client = getApiClient();
    const response = await client.get<TokenUsage>('/v2/usage/today');
    return response.data;
  }

  /**
   * 获取详细使用量信息
   * Requirements: 10.3, 10.4
   */
  async getUsageDetail(): Promise<TokenUsageDetail> {
    const client = getApiClient();
    const response = await client.get<TokenUsageDetail>('/v2/usage/detail');
    return response.data;
  }

  /**
   * 获取周趋势数据
   * Requirements: 10.4
   */
  async getWeeklyTrend(): Promise<DailyUsage[]> {
    const client = getApiClient();
    const response = await client.get<DailyUsage[]>('/v2/usage/weekly');
    return response.data;
  }
}

export const usageService = new UsageService();
