/**
 * 认证相关 hooks
 */

import { useCallback } from 'react';
import { useAuthStore } from '@/stores';

/**
 * 使用认证状态
 */
export function useAuth() {
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const isLoading = useAuthStore((state) => state.isLoading);
  const error = useAuthStore((state) => state.error);
  
  const login = useAuthStore((state) => state.login);
  const register = useAuthStore((state) => state.register);
  const logout = useAuthStore((state) => state.logout);
  const clearError = useAuthStore((state) => state.clearError);

  return {
    user,
    isAuthenticated,
    isLoading,
    error,
    login,
    register,
    logout,
    clearError,
  };
}

/**
 * 需要认证的路由保护
 */
export function useRequireAuth() {
  const { isAuthenticated, isLoading } = useAuth();

  const checkAuth = useCallback(() => {
    if (!isLoading && !isAuthenticated) {
      window.location.href = '/login';
      return false;
    }
    return true;
  }, [isAuthenticated, isLoading]);

  return { isAuthenticated, isLoading, checkAuth };
}
