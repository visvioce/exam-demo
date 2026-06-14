/**
 * 认证状态管理 (Pinia Store)
 *
 * 管理用户登录状态、Token 和用户信息：
 * - token: JWT 令牌，存储在 localStorage 中持久化
 * - user: 当前登录用户信息（角色、昵称等）
 *
 * 提供方法：
 * - login() - 登录，保存 token 和用户信息
 * - register() - 注册新用户
 * - getCurrentUser() - 获取当前用户信息（用于刷新后恢复状态）
 * - logout() - 登出，清除 token 和用户信息
 * - changePassword() - 修改密码
 * - refreshToken() - Token 续期
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { authApi } from '@/api/auth'
import type { User, LoginRequest, UserResponse, RegisterRequest } from '@/types'
import { ElMessage } from 'element-plus'
import { resetLoggingOut, setLoggingOut } from '@/utils/request'

export const useAuthStore = defineStore('auth', () => {
  // JWT 令牌，从 localStorage 恢复（支持页面刷新后保持登录状态）
  const token = ref<string>(localStorage.getItem('token') || '')
  // 当前登录用户信息
  const user = ref<User | null>(null)

  /**
   * 用户登录
   * 调用后端登录接口，成功后保存 token 到 localStorage 和内存中
   * @param loginData 登录表单数据（用户名、密码）
   * @returns 是否登录成功
   */
  async function login(loginData: LoginRequest) {
    try {
      const res = await authApi.login(loginData)
      token.value = res.data.token
      // 将 UserResponse 转换为 User 类型（适配前端模型）
      const userResponse: UserResponse = res.data.user
      user.value = {
        id: userResponse.id,
        username: userResponse.username,
        nickname: userResponse.nickname,
        avatar: userResponse.avatar,
        role: userResponse.role as User['role'],
        status: userResponse.status as User['status'],
        createdAt: userResponse.createdAt
      }
      localStorage.setItem('token', res.data.token)
      resetLoggingOut() // 重置登出标记，防止上次登出状态影响本次登录
      ElMessage.success('登录成功')
      return true
    } catch (error) {
      return false
    }
  }

  /**
   * 用户注册
   * 注册成功后不自动登录，需要跳转到登录页
   * @param data 注册表单数据
   * @returns 是否注册成功
   */
  async function register(data: RegisterRequest) {
    try {
      await authApi.register(data)
      ElMessage.success('注册成功，请登录')
      return true
    } catch (error) {
      return false
    }
  }

  /**
   * 获取当前用户信息
   * 用于页面刷新后恢复用户状态，也用于验证 token 是否有效
   * token 失效（401/403）时自动登出
   * @returns 是否获取成功
   */
  async function getCurrentUser() {
    try {
      const res = await authApi.getCurrentUser()
      const userData = res.data
      user.value = {
        id: userData.id,
        username: userData.username,
        nickname: userData.nickname,
        avatar: userData.avatar,
        role: userData.role as User['role'],
        status: userData.status as User['status'],
        createdAt: userData.createdAt
      }
      return true
    } catch (error: unknown) {
      const status = (error as { response?: { status?: number } })?.response?.status
      if (status === 401 || status === 403) {
        logout() // token 失效，自动登出
      }
      return false
    }
  }

  /**
   * 登出
   * 清除 token、用户信息，并设置登出标记防止重复跳转
   */
  function logout() {
    setLoggingOut(true) // 设置登出标记，防止请求拦截器重复跳转登录页
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
  }

  /**
   * 修改密码
   * @param data 旧密码和新密码
   * @returns 是否修改成功
   */
  async function changePassword(data: { oldPassword: string; newPassword: string }) {
    try {
      await authApi.changePassword(data)
      ElMessage.success('密码修改成功')
      return true
    } catch (error) {
      return false
    }
  }

  /**
   * Token 续期
   * 在 token 即将过期时调用，获取新的 token
   * 续期失败时自动登出
   * @returns 是否续期成功
   */
  async function refreshToken() {
    try {
      const res = await authApi.refreshToken()
      token.value = res.data.token
      localStorage.setItem('token', res.data.token)
      return true
    } catch {
      logout()
      return false
    }
  }

  return {
    token,
    user,
    login,
    register,
    getCurrentUser,
    logout,
    changePassword,
    refreshToken
  }
})
