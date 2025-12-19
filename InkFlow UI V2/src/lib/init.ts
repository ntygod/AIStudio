/**
 * 应用初始化
 * 配置 API 客户端和其他全局设置
 */

import { initApiClient, initSSEClient, tokenManager } from '@/api';
import { authService } from '@/services';
import { useAuthStore } from '@/stores';

/**
 * 初始化应用
 */
export async function initializeApp(): Promise<void> {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api';
  const timeout = parseInt(import.meta.env.VITE_SSE_TIMEOUT || '30000', 10);

  // 初始化 API 客户端
  initApiClient({
    baseUrl,
    timeout,
    onUnauthorized: () => {
      // 清除认证状态并重定向到登录页
      useAuthStore.getState().logout();
      window.location.href = '/login';
    },
  });

  // 初始化 SSE 客户端
  initSSEClient(baseUrl, timeout);

  // 设置 token 刷新回调
  tokenManager.setRefreshCallback(authService.refreshToken);

  // 验证现有会话
  if (tokenManager.hasValidToken()) {
    try {
      await useAuthStore.getState().validateSession();
    } catch {
      // 会话验证失败，静默处理
      console.warn('Session validation failed');
    }
  }
}

/**
 * 获取环境配置
 */
export function getConfig() {
  return {
    apiBaseUrl: import.meta.env.VITE_API_BASE_URL || '/api',
    appName: import.meta.env.VITE_APP_NAME || 'InkFlow',
    appVersion: import.meta.env.VITE_APP_VERSION || '2.0.0',
    tokenRefreshThreshold: parseInt(import.meta.env.VITE_TOKEN_REFRESH_THRESHOLD || '300000', 10),
    sseTimeout: parseInt(import.meta.env.VITE_SSE_TIMEOUT || '30000', 10),
    cacheTTL: parseInt(import.meta.env.VITE_CACHE_TTL || '300000', 10),
  };
}
