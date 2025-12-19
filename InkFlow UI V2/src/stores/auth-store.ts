/**
 * 认证状态管理
 * 使用 Zustand 管理用户认证状态
 */

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { User, LoginRequest, RegisterRequest } from '@/types';
import { tokenManager } from '@/api';
import { authService } from '@/services/auth-service';

interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
}

interface AuthActions {
  login(identifier: string, password: string): Promise<void>;
  register(username: string, email: string, password: string, displayName?: string): Promise<void>;
  logout(): Promise<void>;
  validateSession(): Promise<void>;
  clearError(): void;
  setUser(user: User | null): void;
}

type AuthStore = AuthState & AuthActions;

// Token 刷新阈值（5分钟）
const TOKEN_REFRESH_THRESHOLD = 5 * 60 * 1000;

// 自动刷新定时器
let refreshTimer: ReturnType<typeof setInterval> | null = null;

/**
 * 启动自动刷新定时器
 */
function startAutoRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer);
  }

  // 每分钟检查一次是否需要刷新
  refreshTimer = setInterval(async () => {
    if (tokenManager.isExpiringSoon(TOKEN_REFRESH_THRESHOLD)) {
      await tokenManager.refreshIfNeeded();
    }
  }, 60 * 1000);
}

/**
 * 停止自动刷新定时器
 */
function stopAutoRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer);
    refreshTimer = null;
  }
}

export const useAuthStore = create<AuthStore>()(
  persist(
    (set, _get) => ({
      // 初始状态
      user: null,
      isAuthenticated: false,
      isLoading: false,
      error: null,

      /**
       * 用户登录
       */
      login: async (identifier: string, password: string) => {
        set({ isLoading: true, error: null });

        try {
          const request: LoginRequest = { identifier, password };
          const response = await authService.login(request);

          // 保存 token
          tokenManager.setTokensFromResponse(response);

          // 设置刷新回调
          tokenManager.setRefreshCallback(authService.refreshToken);

          // 启动自动刷新
          startAutoRefresh();

          set({
            user: response.user,
            isAuthenticated: true,
            isLoading: false,
            error: null,
          });
        } catch (error) {
          const message = error instanceof Error ? error.message : '登录失败';
          set({
            user: null,
            isAuthenticated: false,
            isLoading: false,
            error: message,
          });
          throw error;
        }
      },

      /**
       * 用户注册
       */
      register: async (
        username: string,
        email: string,
        password: string,
        displayName?: string
      ) => {
        set({ isLoading: true, error: null });

        try {
          const request: RegisterRequest = { username, email, password, displayName };
          const response = await authService.register(request);

          // 保存 token
          tokenManager.setTokensFromResponse(response);

          // 设置刷新回调
          tokenManager.setRefreshCallback(authService.refreshToken);

          // 启动自动刷新
          startAutoRefresh();

          set({
            user: response.user,
            isAuthenticated: true,
            isLoading: false,
            error: null,
          });
        } catch (error) {
          const message = error instanceof Error ? error.message : '注册失败';
          set({
            user: null,
            isAuthenticated: false,
            isLoading: false,
            error: message,
          });
          throw error;
        }
      },

      /**
       * 用户登出
       */
      logout: async () => {
        set({ isLoading: true });

        try {
          const refreshToken = tokenManager.getRefreshToken();
          if (refreshToken) {
            await authService.logout(refreshToken);
          }
        } catch {
          // 忽略登出错误，继续清理本地状态
        } finally {
          // 停止自动刷新
          stopAutoRefresh();

          // 清除 token
          tokenManager.clear();

          set({
            user: null,
            isAuthenticated: false,
            isLoading: false,
            error: null,
          });
        }
      },

      /**
       * 验证会话
       */
      validateSession: async () => {
        // 检查是否有有效的 token
        if (!tokenManager.hasValidToken()) {
          set({
            user: null,
            isAuthenticated: false,
            isLoading: false,
          });
          return;
        }

        set({ isLoading: true });

        try {
          // 设置刷新回调
          tokenManager.setRefreshCallback(authService.refreshToken);

          // 刷新 token（如果需要）
          await tokenManager.refreshIfNeeded();

          // 获取用户信息
          const user = await authService.getCurrentUser();

          // 启动自动刷新
          startAutoRefresh();

          set({
            user,
            isAuthenticated: true,
            isLoading: false,
            error: null,
          });
        } catch {
          // 验证失败，清除状态
          stopAutoRefresh();
          tokenManager.clear();

          set({
            user: null,
            isAuthenticated: false,
            isLoading: false,
            error: null,
          });
        }
      },

      /**
       * 清除错误
       */
      clearError: () => {
        set({ error: null });
      },

      /**
       * 设置用户（用于外部更新）
       */
      setUser: (user: User | null) => {
        set({ user, isAuthenticated: user !== null });
      },
    }),
    {
      name: 'inkflow-auth',
      partialize: (state) => ({
        // 只持久化用户信息，token 由 TokenManager 管理
        user: state.user,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
);

// 导出选择器
export const selectUser = (state: AuthStore) => state.user;
export const selectIsAuthenticated = (state: AuthStore) => state.isAuthenticated;
export const selectIsLoading = (state: AuthStore) => state.isLoading;
export const selectError = (state: AuthStore) => state.error;
