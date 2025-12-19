/**
 * API 客户端
 * 封装 HTTP 请求，自动处理认证和错误
 */

import type { ApiResponse, ApiError } from '@/types';
import { tokenManager } from './token-manager';

export enum ErrorCode {
  NETWORK_ERROR = 'NETWORK_ERROR',
  UNAUTHORIZED = 'UNAUTHORIZED',
  FORBIDDEN = 'FORBIDDEN',
  NOT_FOUND = 'NOT_FOUND',
  VALIDATION_ERROR = 'VALIDATION_ERROR',
  SERVER_ERROR = 'SERVER_ERROR',
  TIMEOUT = 'TIMEOUT',
}

export interface ApiClientConfig {
  baseUrl: string;
  timeout: number;
  onUnauthorized: () => void;
}

export class ApiClient {
  private baseUrl: string;
  private timeout: number;
  private onUnauthorized: () => void;

  constructor(config: ApiClientConfig) {
    this.baseUrl = config.baseUrl;
    this.timeout = config.timeout;
    this.onUnauthorized = config.onUnauthorized;
  }

  /**
   * 构建请求 headers
   */
  buildHeaders(additionalHeaders?: Record<string, string>): Headers {
    const headers = new Headers({
      'Content-Type': 'application/json',
      ...additionalHeaders,
    });

    const token = tokenManager.getAccessToken();
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }

    return headers;
  }

  /**
   * 构建完整的请求对象（用于测试）
   */
  buildRequest(
    method: string,
    path: string,
    body?: unknown,
    additionalHeaders?: Record<string, string>
  ): Request {
    const url = `${this.baseUrl}${path}`;
    const headers = this.buildHeaders(additionalHeaders);

    return new Request(url, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined,
    });
  }

  /**
   * 发送请求
   */
  private async request<T>(
    method: string,
    path: string,
    body?: unknown,
    params?: Record<string, string>
  ): Promise<ApiResponse<T>> {
    // 如果 token 即将过期，先刷新
    await tokenManager.refreshIfNeeded();

    // 构建 URL
    let url = `${this.baseUrl}${path}`;
    if (params) {
      const searchParams = new URLSearchParams(params);
      url += `?${searchParams.toString()}`;
    }

    // 创建 AbortController 用于超时
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), this.timeout);

    try {
      const response = await fetch(url, {
        method,
        headers: this.buildHeaders(),
        body: body ? JSON.stringify(body) : undefined,
        signal: controller.signal,
      });

      clearTimeout(timeoutId);

      // 处理 401 错误
      if (response.status === 401) {
        // 尝试刷新 token
        const newToken = await tokenManager.refresh();
        if (newToken) {
          // 重试请求
          return this.request<T>(method, path, body, params);
        }
        // 刷新失败，触发未授权回调
        this.onUnauthorized();
        throw this.createApiError(response);
      }

      // 处理其他错误
      if (!response.ok) {
        throw await this.handleErrorResponse(response);
      }

      // 解析响应
      const data = await this.parseResponse<T>(response);

      return {
        data,
        status: response.status,
        headers: response.headers,
      };
    } catch (error) {
      clearTimeout(timeoutId);

      if (error instanceof DOMException && error.name === 'AbortError') {
        throw {
          code: ErrorCode.TIMEOUT,
          message: '请求超时',
        } as ApiError;
      }

      if (this.isApiError(error)) {
        throw error;
      }

      throw {
        code: ErrorCode.NETWORK_ERROR,
        message: '网络连接失败',
      } as ApiError;
    }
  }

  /**
   * 解析响应体
   */
  private async parseResponse<T>(response: Response): Promise<T> {
    const contentType = response.headers.get('Content-Type');
    
    if (contentType?.includes('application/json')) {
      return response.json();
    }
    
    // 对于非 JSON 响应，返回文本
    const text = await response.text();
    return text as unknown as T;
  }

  /**
   * 处理错误响应
   */
  private async handleErrorResponse(response: Response): Promise<ApiError> {
    try {
      const body = await response.json();
      return {
        code: this.mapStatusToErrorCode(response.status),
        message: body.message || this.getDefaultErrorMessage(response.status),
        details: body.details,
      };
    } catch {
      return this.createApiError(response);
    }
  }

  /**
   * 创建 API 错误
   */
  private createApiError(response: Response): ApiError {
    return {
      code: this.mapStatusToErrorCode(response.status),
      message: this.getDefaultErrorMessage(response.status),
    };
  }

  /**
   * 映射 HTTP 状态码到错误码
   */
  mapStatusToErrorCode(status: number): ErrorCode {
    switch (status) {
      case 401:
        return ErrorCode.UNAUTHORIZED;
      case 403:
        return ErrorCode.FORBIDDEN;
      case 404:
        return ErrorCode.NOT_FOUND;
      case 422:
        return ErrorCode.VALIDATION_ERROR;
      default:
        return ErrorCode.SERVER_ERROR;
    }
  }

  /**
   * 获取默认错误消息
   */
  private getDefaultErrorMessage(status: number): string {
    switch (status) {
      case 401:
        return '请重新登录';
      case 403:
        return '无权限访问';
      case 404:
        return '资源不存在';
      case 422:
        return '数据验证失败';
      default:
        return '服务器错误';
    }
  }

  /**
   * 检查是否为 ApiError
   */
  private isApiError(error: unknown): error is ApiError {
    return (
      typeof error === 'object' &&
      error !== null &&
      'code' in error &&
      'message' in error
    );
  }

  // HTTP 方法

  async get<T>(path: string, params?: Record<string, string>): Promise<ApiResponse<T>> {
    return this.request<T>('GET', path, undefined, params);
  }

  async post<T>(path: string, body?: unknown): Promise<ApiResponse<T>> {
    return this.request<T>('POST', path, body);
  }

  async put<T>(path: string, body?: unknown): Promise<ApiResponse<T>> {
    return this.request<T>('PUT', path, body);
  }

  async patch<T>(path: string, body?: unknown): Promise<ApiResponse<T>> {
    return this.request<T>('PATCH', path, body);
  }

  async delete<T>(path: string): Promise<ApiResponse<T>> {
    return this.request<T>('DELETE', path);
  }

  /**
   * 下载文件
   */
  async download(path: string, filename: string): Promise<void> {
    await tokenManager.refreshIfNeeded();

    const response = await fetch(`${this.baseUrl}${path}`, {
      method: 'GET',
      headers: this.buildHeaders(),
    });

    if (!response.ok) {
      throw await this.handleErrorResponse(response);
    }

    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(url);
    document.body.removeChild(a);
  }

  /**
   * 上传文件（用于导入）
   */
  async upload<T>(path: string, file: File): Promise<ApiResponse<T>> {
    await tokenManager.refreshIfNeeded();

    const text = await file.text();
    return this.post<T>(path, JSON.parse(text));
  }
}

// 创建默认实例
let apiClientInstance: ApiClient | null = null;

export function initApiClient(config: ApiClientConfig): ApiClient {
  apiClientInstance = new ApiClient(config);
  return apiClientInstance;
}

export function getApiClient(): ApiClient {
  if (!apiClientInstance) {
    throw new Error('ApiClient not initialized. Call initApiClient first.');
  }
  return apiClientInstance;
}
