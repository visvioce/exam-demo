/**
 * 轮播图 API 模块
 *
 * 提供首页轮播图相关的 API 接口调用：
 * - 列表查询（全部 / 活跃）
 * - 创建、更新、删除轮播图
 */
import request from '@/utils/request'
import type { Carousel } from '@/types'

export const carouselApi = {
  /** 获取所有轮播图（含未激活的） */
  list() {
    return request.get<Carousel[]>('/carousels')
  },

  /** 获取活跃的轮播图（首页展示用） */
  getActive() {
    return request.get<Carousel[]>('/carousels/active')
  },

  /** 获取轮播图详情 */
  getById(id: number) {
    return request.get<Carousel>(`/carousels/${id}`)
  },

  /** 创建轮播图 */
  create(data: Partial<Carousel>) {
    return request.post<boolean>('/carousels', data)
  },

  /** 更新轮播图 */
  update(id: number, data: Partial<Carousel>) {
    return request.put<boolean>(`/carousels/${id}`, data)
  },

  /** 删除轮播图 */
  delete(id: number) {
    return request.delete(`/carousels/${id}`)
  }
}
