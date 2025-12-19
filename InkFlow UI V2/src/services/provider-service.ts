/**
 * AI Provider 配置服务
 * Requirements: 11.1, 11.2, 11.3, 11.4, 11.5
 */

import { getApiClient } from '../api/client';

// ============ 类型定义 ============

export type ProviderType = 'OPENAI' | 'DEEPSEEK' | 'OLLAMA' | 'GEMINI' | 'CLAUDE';

export interface ProviderConfig {
  providerType: ProviderType;
  displayName: string;
  keyHint: string | null;
  baseUrl: string | null;
  defaultModel: string | null;
  modelMapping: Record<string, string> | null;
  isDefault: boolean;
  isConfigured: boolean;
}

export interface SaveProviderConfigRequest {
  providerType: ProviderType;
  apiKey?: string;
  baseUrl?: string;
  defaultModel?: string;
  modelMapping?: Record<string, string>;
  isDefault?: boolean;
}

// Provider 显示信息
export const PROVIDER_INFO: Record<ProviderType, { 
  name: string; 
  description: string; 
  defaultBaseUrl: string;
  icon: string;
}> = {
  OPENAI: {
    name: 'OpenAI',
    description: 'GPT-4, GPT-3.5 等模型',
    defaultBaseUrl: 'https://api.openai.com/v1',
    icon: '🤖',
  },
  DEEPSEEK: {
    name: 'DeepSeek',
    description: '高性价比的国产大模型',
    defaultBaseUrl: 'https://api.deepseek.com',
    icon: '🔮',
  },
  OLLAMA: {
    name: 'Ollama',
    description: '本地运行的开源模型',
    defaultBaseUrl: 'http://localhost:11434',
    icon: '🦙',
  },
  GEMINI: {
    name: 'Google Gemini',
    description: 'Google 的多模态 AI 模型',
    defaultBaseUrl: 'https://generativelanguage.googleapis.com/v1beta',
    icon: '💎',
  },
  CLAUDE: {
    name: 'Anthropic Claude',
    description: '安全可靠的 AI 助手',
    defaultBaseUrl: 'https://api.anthropic.com/v1',
    icon: '🎭',
  },
};

// ============ 服务类 ============

export class ProviderService {
  /**
   * 获取所有服务商配置
   */
  async getAllConfigs(): Promise<ProviderConfig[]> {
    const client = getApiClient();
    const response = await client.get<ProviderConfig[]>('/ai-providers');
    return response.data;
  }

  /**
   * 获取已配置的服务商列表
   */
  async getConfiguredProviders(): Promise<ProviderType[]> {
    const client = getApiClient();
    const response = await client.get<ProviderType[]>('/ai-providers/configured');
    return response.data;
  }

  /**
   * 保存服务商配置
   */
  async saveConfig(request: SaveProviderConfigRequest): Promise<ProviderConfig> {
    const client = getApiClient();
    const response = await client.post<ProviderConfig>('/ai-providers', request);
    return response.data;
  }

  /**
   * 删除服务商配置
   */
  async deleteConfig(providerType: ProviderType): Promise<void> {
    const client = getApiClient();
    await client.delete(`/ai-providers/${providerType}`);
  }

  /**
   * 检查服务商配置
   */
  async checkConfig(providerType: ProviderType): Promise<boolean> {
    const client = getApiClient();
    const response = await client.get<boolean>(`/ai-providers/${providerType}/check`);
    return response.data;
  }

  /**
   * 验证 API Key (通过尝试保存配置)
   */
  async validateApiKey(
    providerType: ProviderType,
    apiKey: string,
    baseUrl?: string
  ): Promise<{ valid: boolean; error?: string }> {
    try {
      await this.saveConfig({
        providerType,
        apiKey,
        baseUrl,
      });
      return { valid: true };
    } catch (error) {
      return { 
        valid: false, 
        error: error instanceof Error ? error.message : '验证失败' 
      };
    }
  }
}

export const providerService = new ProviderService();
