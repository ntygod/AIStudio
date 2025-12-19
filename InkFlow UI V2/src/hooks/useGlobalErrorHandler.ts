/**
 * Global Error Handler Hook
 * Provides centralized error handling for API calls and async operations
 */

import { useEffect, useCallback } from 'react';
import { showApiError, showError, showWarning } from '@/lib/toast';
import { tokenManager } from '@/api';
import { useAuthStore } from '@/stores/auth-store';

// Error codes that should trigger specific actions
const AUTH_ERROR_CODES = ['UNAUTHORIZED', 'TOKEN_EXPIRED', 'INVALID_TOKEN'];
const NETWORK_ERROR_CODES = ['NETWORK_ERROR', 'TIMEOUT'];

interface GlobalErrorHandlerOptions {
  onAuthError?: () => void;
  onNetworkError?: () => void;
}

/**
 * Hook to set up global error handling
 */
export function useGlobalErrorHandler(options?: GlobalErrorHandlerOptions) {
  const logout = useAuthStore((state) => state.logout);

  const handleError = useCallback(
    (error: unknown) => {
      // Extract error details
      const errorObj = normalizeError(error);

      // Handle authentication errors
      if (AUTH_ERROR_CODES.includes(errorObj.code)) {
        showError('登录已过期，请重新登录');
        tokenManager.clear();
        logout();
        options?.onAuthError?.();
        return;
      }

      // Handle network errors
      if (NETWORK_ERROR_CODES.includes(errorObj.code)) {
        showWarning('网络连接不稳定，请检查网络后重试');
        options?.onNetworkError?.();
        return;
      }

      // Show generic error toast
      showApiError(error);
    },
    [logout, options]
  );

  // Set up global unhandled rejection handler
  useEffect(() => {
    const handleUnhandledRejection = (event: PromiseRejectionEvent) => {
      console.error('Unhandled promise rejection:', event.reason);
      handleError(event.reason);
      event.preventDefault();
    };

    window.addEventListener('unhandledrejection', handleUnhandledRejection);

    return () => {
      window.removeEventListener('unhandledrejection', handleUnhandledRejection);
    };
  }, [handleError]);

  return { handleError };
}

/**
 * Normalize various error types into a consistent format
 */
function normalizeError(error: unknown): { code: string; message: string } {
  if (error instanceof Error) {
    // Check for specific error types
    if (error.name === 'TypeError' && error.message.includes('fetch')) {
      return { code: 'NETWORK_ERROR', message: '网络连接失败' };
    }
    if (error.name === 'AbortError') {
      return { code: 'ABORTED', message: '请求已取消' };
    }
    return { code: 'UNKNOWN', message: error.message };
  }

  if (typeof error === 'object' && error !== null) {
    const err = error as { code?: string; message?: string; status?: number };
    
    // Handle HTTP status codes
    if (err.status === 401) {
      return { code: 'UNAUTHORIZED', message: '未授权访问' };
    }
    if (err.status === 403) {
      return { code: 'FORBIDDEN', message: '无权限访问' };
    }
    if (err.status === 404) {
      return { code: 'NOT_FOUND', message: '资源不存在' };
    }
    if (err.status === 500) {
      return { code: 'SERVER_ERROR', message: '服务器错误' };
    }

    return {
      code: err.code || 'UNKNOWN',
      message: err.message || '未知错误',
    };
  }

  return { code: 'UNKNOWN', message: String(error) };
}

/**
 * Wrapper for async operations with error handling
 */
export function withErrorHandling<T>(
  operation: () => Promise<T>,
  onError?: (error: unknown) => void
): Promise<T | undefined> {
  return operation().catch((error) => {
    console.error('Operation failed:', error);
    onError?.(error);
    showApiError(error);
    return undefined;
  });
}

/**
 * Create a safe async handler that catches errors
 */
export function createSafeHandler<T extends unknown[]>(
  handler: (...args: T) => Promise<void>,
  onError?: (error: unknown) => void
) {
  return async (...args: T) => {
    try {
      await handler(...args);
    } catch (error) {
      console.error('Handler error:', error);
      onError?.(error);
      showApiError(error);
    }
  };
}
