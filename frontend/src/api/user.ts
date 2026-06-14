/**
 * 用户管理 API 模块
 *
 * 提供用户管理相关的 API 接口调用（仅管理员可访问）：
 * - 分页查询、列表查询
 * - 创建、更新、删除用户
 * - 批量删除用户
 */
import request from '@/utils/request'
import type { User, UserResponse, PageRequest, PageResult } from '@/types'

export const userApi = {
  /** 分页查询用户（支持按角色、状态、关键词筛选） */
  page(params: PageRequest & { role?: string; status?: string; keyword?: string }) {
    return request.get<PageResult<UserResponse>>('/users/page', { params })
  },

  /** 获取所有用户 */
  list() {
    return request.get<UserResponse[]>('/users')
  },

  /** 获取用户详情 */
  getById(id: number) {
    return request.get<UserResponse>(`/users/${id}`)
  },

  /** 创建用户（管理员创建用户时需要设置初始密码） */
  create(data: Partial<User> & { password?: string }) {
    return request.post<boolean>('/users', data)
  },

  /** 更新用户 */
  update(id: number, data: Partial<User>) {
    return request.put<boolean>(`/users/${id}`, data)
  },

  /** 删除用户 */
  delete(id: number) {
    return request.delete(`/users/${id}`)
  },

  /** 批量删除用户 */
  deleteBatch(ids: number[]) {
    return request.delete('/users/batch', { data: ids })
  }
}
