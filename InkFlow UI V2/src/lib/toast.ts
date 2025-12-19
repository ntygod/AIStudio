/**
 * Toast Notification Utilities
 * Provides a centralized API for showing toast notifications
 */

import { toast as sonnerToast } from 'sonner';

export type ToastType = 'success' | 'error' | 'warning' | 'info' | 'loading';

interface ToastOptions {
  description?: string;
  duration?: number;
  action?: {
    label: string;
    onClick: () => void;
  };
}

/**
 * Show a success toast
 */
export function showSuccess(message: string, options?: ToastOptions) {
  return sonnerToast.success(message, {
    description: options?.description,
    duration: options?.duration ?? 3000,
    action: options?.action,
  });
}

/**
 * Show an error toast
 */
export function showError(message: string, options?: ToastOptions) {
  return sonnerToast.error(message, {
    description: options?.description,
    duration: options?.duration ?? 5000,
    action: options?.action,
  });
}

/**
 * Show a warning toast
 */
export function showWarning(message: string, options?: ToastOptions) {
  return sonnerToast.warning(message, {
    description: options?.description,
    duration: options?.duration ?? 4000,
    action: options?.action,
  });
}

/**
 * Show an info toast
 */
export function showInfo(message: string, options?: ToastOptions) {
  return sonnerToast.info(message, {
    description: options?.description,
    duration: options?.duration ?? 3000,
    action: options?.action,
  });
}

/**
 * Show a loading toast (returns dismiss function)
 */
export function showLoading(message: string) {
  return sonnerToast.loading(message);
}

/**
 * Dismiss a specific toast or all toasts
 */
export function dismissToast(toastId?: string | number) {
  if (toastId) {
    sonnerToast.dismiss(toastId);
  } else {
    sonnerToast.dismiss();
  }
}

/**
 * Show toast based on API error
 */
export function showApiError(error: unknown, fallbackMessage = '操作失败') {
  let message = fallbackMessage;
  let description: string | undefined;

  if (error instanceof Error) {
    message = error.message || fallbackMessage;
  } else if (typeof error === 'object' && error !== null) {
    const err = error as { message?: string; code?: string };
    message = err.message || fallbackMessage;
    description = err.code ? `错误代码: ${err.code}` : undefined;
  }

  return showError(message, { description });
}

/**
 * Promise-based toast for async operations
 */
export function toastPromise<T>(
  promise: Promise<T>,
  messages: {
    loading: string;
    success: string | ((data: T) => string);
    error: string | ((error: unknown) => string);
  }
) {
  return sonnerToast.promise(promise, {
    loading: messages.loading,
    success: (data) =>
      typeof messages.success === 'function'
        ? messages.success(data)
        : messages.success,
    error: (err) =>
      typeof messages.error === 'function'
        ? messages.error(err)
        : messages.error,
  });
}

// Re-export for convenience
export { sonnerToast as toast };
