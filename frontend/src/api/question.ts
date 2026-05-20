import request from '@/utils/request'
import type { Question, PageRequest, PageResult } from '@/types'

export const questionApi = {
  // 分页查询题目
  page(params: PageRequest & { teacherId?: number; type?: string; keyword?: string; difficulty?: string }) {
    return request.get<PageResult<Question>>('/questions/page', { params })
  },

  // 获取所有题目
  list() {
    return request.get<Question[]>('/questions')
  },

  // 获取题目详情
  getById(id: number) {
    return request.get<Question>(`/questions/${id}`)
  },

  // 创建题目
  create(data: Partial<Question>) {
    return request.post<boolean>('/questions', data)
  },

  // 更新题目
  update(id: number, data: Partial<Question>) {
    return request.put<boolean>(`/questions/${id}`, data)
  },

  // 删除题目
  delete(id: number) {
    return request.delete(`/questions/${id}`)
  },

  // 获取所有学科列表
  getSubjects() {
    return request.get<string[]>('/questions/subjects')
  },

  // 获取可用题目数量
  getAvailableCount(params: { type: string; subject?: string; difficulty?: string }) {
    return request.get<{ count: number }>('/questions/available-count', { params })
  },

  // 批量获取题目
  getByIds(ids: number[]) {
    return request.post<Question[]>('/questions/batch', { ids })
  },
}
