import request from '@/utils/request'

export interface StatsData {
  userCount: number
  courseCount: number
  questionCount: number
  examCount: number
}

export const statsApi = {
  getStats() {
    return request.get<StatsData>('/stats')
  }
}