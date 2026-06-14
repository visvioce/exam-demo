/**
 * 试卷 API 模块
 *
 * 提供试卷管理相关的 API 接口调用：
 * - 分页查询、列表查询
 * - 创建、更新、删除试卷
 * - 自动组卷（根据题型和数量自动选题生成试卷）
 */
import request from '@/utils/request'
import type { Paper, PageRequest, PageResult, AutoGeneratePaperRequest } from '@/types'

export const paperApi = {
  /** 分页查询试卷（支持按教师ID、关键词筛选） */
  page(
    params: PageRequest & {
      teacherId?: number
      keyword?: string
    }
  ) {
    return request.get<PageResult<Paper>>('/papers/page', { params })
  },

  /** 获取所有试卷 */
  list() {
    return request.get<Paper[]>('/papers')
  },

  /** 获取试卷详情 */
  getById(id: number) {
    return request.get<Paper>(`/papers/${id}`)
  },

  /** 创建试卷 */
  create(data: Partial<Paper>) {
    return request.post<boolean>('/papers', data)
  },

  /** 更新试卷 */
  update(id: number, data: Partial<Paper>) {
    return request.put<boolean>(`/papers/${id}`, data)
  },

  /** 删除试卷 */
  delete(id: number) {
    return request.delete(`/papers/${id}`)
  },

  /** 自动组卷：根据题型和数量配置自动从题库中选题生成试卷 */
  autoGenerate(data: AutoGeneratePaperRequest) {
    return request.post<Paper>('/papers/auto-generate', data)
  }
}
