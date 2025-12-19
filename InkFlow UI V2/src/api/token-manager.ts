/**
 * Token 管理器
 * 负责 JWT token 的存储、过期检测和刷新
 */

import type { TokenPair, TokenResponse } from '@/types';

const STORAGE_KEY = 'inkflow_tokens';

export class TokenManager {
  private tokens: TokenPair | null = null;
  private refreshPromise: Promise<TokenPair> | null = null;
  private refreshCallback: ((refreshToken: string) => Promise<TokenResponse>) | null = null;

  constructor() {
    this.loadFromStorage();
  }

  /**
   * 设置刷新回调函数
   */
  setRefreshCallback(callback: (refreshToken: string) => Promise<TokenResponse>): void {
    this.refreshCallback = callback;
  }

  /**
   * 从 TokenResponse 设置 tokens
   */
  setTokensFromResponse(response: TokenResponse): void {
    const expiresAt = Date.now() + response.expiresIn * 1000;
    this.setTokens({
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
      expiresAt,
    });
  }

  /**
   * 设置 tokens
   */
  setTokens(tokens: TokenPair): void {
    this.tokens = tokens;
    this.saveToStorage();
  }

  /**
   * 获取 access token
   */
  getAccessToken(): string | null {
    return this.tokens?.accessToken ?? null;
  }

  /**
   * 获取 refresh token
   */
  getRefreshToken(): string | null {
    return this.tokens?.refreshToken ?? null;
  }

  /**
   * 获取完整的 token 对
   */
  getTokens(): TokenPair | null {
    return this.tokens;
  }

  /**
   * 检查 token 是否即将过期
   * @param thresholdMs 过期阈值（毫秒），默认 5 分钟
   */
  isExpiringSoon(thresholdMs: number = 5 * 60 * 1000): boolean {
    if (!this.tokens) return true;
    return Date.now() + thresholdMs >= this.tokens.expiresAt;
  }

  /**
   * 检查 token 是否已过期
   */
  isExpired(): boolean {
    if (!this.tokens) return true;
    return Date.now() >= this.tokens.expiresAt;
  }

  /**
   * 检查是否有有效的 token
   */
  hasValidToken(): boolean {
    return this.tokens !== null && !this.isExpired();
  }

  /**
   * 清除 tokens
   */
  clear(): void {
    this.tokens = null;
    this.refreshPromise = null;
    localStorage.removeItem(STORAGE_KEY);
  }

  /**
   * 如果需要则刷新 token
   * 返回有效的 access token 或 null
   */
  async refreshIfNeeded(): Promise<string | null> {
    // 如果没有 token，返回 null
    if (!this.tokens) {
      return null;
    }

    // 如果 token 未过期且不即将过期，直接返回
    if (!this.isExpiringSoon()) {
      return this.tokens.accessToken;
    }

    // 如果已经在刷新中，等待刷新完成
    if (this.refreshPromise) {
      try {
        const newTokens = await this.refreshPromise;
        return newTokens.accessToken;
      } catch {
        return null;
      }
    }

    // 开始刷新
    return this.refresh();
  }

  /**
   * 强制刷新 token
   */
  async refresh(): Promise<string | null> {
    if (!this.tokens?.refreshToken || !this.refreshCallback) {
      return null;
    }

    // 创建刷新 Promise
    this.refreshPromise = (async () => {
      try {
        const response = await this.refreshCallback!(this.tokens!.refreshToken);
        const newTokens: TokenPair = {
          accessToken: response.accessToken,
          refreshToken: response.refreshToken,
          expiresAt: Date.now() + response.expiresIn * 1000,
        };
        this.setTokens(newTokens);
        return newTokens;
      } finally {
        this.refreshPromise = null;
      }
    })();

    try {
      const newTokens = await this.refreshPromise;
      return newTokens.accessToken;
    } catch {
      this.clear();
      return null;
    }
  }

  /**
   * 从 localStorage 加载 tokens
   */
  private loadFromStorage(): void {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored) {
        const parsed = JSON.parse(stored) as TokenPair;
        // 验证数据结构
        if (parsed.accessToken && parsed.refreshToken && parsed.expiresAt) {
          this.tokens = parsed;
        }
      }
    } catch {
      // 忽略解析错误
      localStorage.removeItem(STORAGE_KEY);
    }
  }

  /**
   * 保存 tokens 到 localStorage
   */
  private saveToStorage(): void {
    if (this.tokens) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.tokens));
    }
  }
}

// 单例实例
export const tokenManager = new TokenManager();
