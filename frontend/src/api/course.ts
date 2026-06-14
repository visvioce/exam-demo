/**
 * 课程 API 模块
 *
 * 提供课程管理相关的 API 接口调用：
 * - 分页查询、列表查询
 * - 创建、更新、删除课程
 * - 学生加入/退出课程
 * - 获取课程成员
 */
import request from '@/utils/request'
import type { Course, PageRequest, PageResult, UserResponse } from '@/types'

export const courseApi = {
  /** 分页查询课程（支持按教师ID、状态、关键词筛选） */
  page(params: PageRequest & { teacherId?: number; status?: string; keyword?: string }) {
    return request.get<PageResult<Course>>('/courses/page', { params })
  },

  /** 获取所有课程 */
  list() {
    return request.get<Course[]>('/courses')
  },

  /** 获取可加入的活跃课程（学生选课用） */
  getActiveCourses() {
    return request.get<Course[]>('/courses/active')
  },

  /** 获取当前学生已加入的课程 */
  getMyCourses() {
    return request.get<Course[]>('/courses/my')
  },

  /** 获取课程详情 */
  getById(id: number) {
    return request.get<Course>(`/courses/${id}`)
  },

  /** 创建课程 */
  create(data: Partial<Course>) {
    return request.post<boolean>('/courses', data)
  },

  /** 更新课程 */
  update(id: number, data: Partial<Course>) {
    return request.put<boolean>(`/courses/${id}`, data)
  },

  /** 删除课程 */
  delete(id: number) {
    return request.delete(`/courses/${id}`)
  },

  /** 学生加入课程 */
  join(id: number) {
    return request.post(`/courses/${id}/join`)
  },

  /** 学生退出课程 */
  leave(id: number) {
    return request.post(`/courses/${id}/leave`)
  },

  /** 检查当前学生是否已加入课程（轻量级接口） */
  checkJoined(id: number) {
    return request.get<boolean>(`/courses/${id}/joined`)
  },

  /** 获取课程成员列表 */
  getMembers(id: number) {
    return request.get<UserResponse[]>(`/courses/${id}/members`)
  }
}
