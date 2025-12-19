/**
 * 认证服务
 * 封装认证相关的 API 调用
 */

import type { 
  LoginRequest, 
  RegisterRequest, 
  TokenResponse, 
  User 
} from '@/types';
import { getApiClient } from '@/api';

export class AuthService {
  /**
   * 用户登录
   */
  async login(request: LoginRequest): Promise<TokenResponse> {
    const client = getApiClient();
    const response = await client.post<TokenResponse>('/auth/login', request);
    return response.data;
  }

  /**
   * 用户注册
   */
  async register(request: RegisterRequest): Promise<TokenResponse> {
    const client = getApiClient();
    const response = await client.post<TokenResponse>('/auth/register', request);
    return response.data;
  }

  /**
   * 用户登出
   */
  async logout(refreshToken: string): Promise<void> {
    const client = getApiClient();
    await client.post('/auth/logout', { refreshToken });
  }

  /**
   * 刷新 token
   */
  async refreshToken(refreshToken: string): Promise<TokenResponse> {
    const client = getApiClient();
    const response = await client.post<TokenResponse>('/auth/refresh', { refreshToken });
    return response.data;
  }

  /**
   * 获取当前用户信息
   */
  async getCurrentUser(): Promise<User> {
    const client = getApiClient();
    const response = await client.get<User>('/auth/me');
    return response.data;
  }

  /**
   * 更新用户资料
   */
  async updateProfile(request: UpdateProfileRequest): Promise<User> {
    const client = getApiClient();
    const response = await client.put<User>('/auth/profile', request);
    return response.data;
  }
}

export interface UpdateProfileRequest {
  displayName?: string;
  bio?: string;
  avatarUrl?: string;
}

// 单例实例
export const authService = new AuthService();
