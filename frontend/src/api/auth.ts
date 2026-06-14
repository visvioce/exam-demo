/**
 * 认证 API 模块
 *
 * 提供用户认证相关的 API 接口调用：
 * - 登录/注册
 * - 获取当前用户信息
 * - 修改密码/更新个人资料
 * - Token 续期
 */
import request from '@/utils/request'
import type { LoginRequest, LoginResponse, RegisterRequest, UserResponse } from '@/types'

export const authApi = {
  /** 用户登录，返回 JWT token 和用户信息 */
  login(data: LoginRequest) {
    return request.post<LoginResponse>('/auth/login', data)
  },

  /** 用户注册 */
  register(data: RegisterRequest) {
    return request.post<UserResponse>('/auth/register', data)
  },

  /** 获取当前登录用户信息（需要通过 token 验证） */
  getCurrentUser() {
    return request.get<UserResponse>('/auth/me')
  },

  /** 修改密码 */
  changePassword(data: { oldPassword: string; newPassword: string }) {
    return request.post('/auth/change-password', data)
  },

  /** 更新个人资料（昵称、头像） */
  updateProfile(data: { nickname: string; avatar?: string }) {
    return request.put<UserResponse>('/auth/profile', data)
  },

  /** Token 续期，在旧 token 过期前获取新 token */
  refreshToken() {
    return request.post<LoginResponse>('/auth/refresh')
  }
}
