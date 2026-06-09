import request from '@/utils/request'
import type { Paper, PageRequest, PageResult, AutoGeneratePaperRequest } from '@/types'

export const paperApi = {
  // 分页查询试卷
  page(
    params: PageRequest & {
      teacherId?: number
      keyword?: string
    }
  ) {
    return request.get<PageResult<Paper>>('/papers/page', { params })
  },

  // 获取所有试卷
  list() {
    return request.get<Paper[]>('/papers')
  },

  // 获取试卷详情
  getById(id: number) {
    return request.get<Paper>(`/papers/${id}`)
  },

  // 创建试卷
  create(data: Partial<Paper>) {
    return request.post<boolean>('/papers', data)
  },

  // 更新试卷
  update(id: number, data: Partial<Paper>) {
    return request.put<boolean>(`/papers/${id}`, data)
  },

  // 删除试卷
  delete(id: number) {
    return request.delete(`/papers/${id}`)
  },

  // 自动组卷
  autoGenerate(data: AutoGeneratePaperRequest) {
    return request.post<Paper>('/papers/auto-generate', data)
  }
}
