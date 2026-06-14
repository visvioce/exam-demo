import request from '@/utils/request'
import { useAuthStore } from '@/stores/auth'
import type { AiConfig } from '@/types'
import { fetchEventSource } from '@microsoft/fetch-event-source'

export interface GenerateQuestionRequest {
  subject: string
  type: string
  difficulty: string
  count: number
  requirements?: string
}

export interface GeneratedQuestion {
  content: string
  type: string
  options?: { id: string; text: string }[]
  correctAnswer: string | string[]
  explanation?: string
  difficulty?: string
  scoringCriteria?: ScoringCriterion[]
}

export interface ScoringCriterion {
  point: string
  score: number
}

export interface GeneratedQuestionResponse {
  questions: GeneratedQuestion[]
}

export const aiApi = {
  // 获取当前用户的AI配置列表
  getConfigs() {
    return request.get<AiConfig[]>('/ai-configs/my')
  },

  // 获取当前用户的激活配置
  getActiveConfig() {
    return request.get<AiConfig>('/ai-configs/my/active')
  },

  // AI生成题目（设置60秒超时，与后端AI调用超时一致）
  generateQuestions(data: GenerateQuestionRequest) {
    return request.post<GeneratedQuestionResponse>('/ai/generate-questions', data, {
      timeout: 60000  // 60秒超时
    })
  },

  /**
   * 流式生成题目（SSE）
   * 使用 @microsoft/fetch-event-source 处理 SSE 连接、重连和错误
   * @param data 生成请求参数
   * @param callbacks 回调函数
   * @returns 返回一个关闭连接的函数
   */
  generateQuestionsStream(
    data: GenerateQuestionRequest,
    callbacks: {
      onChunk?: (content: string) => void
      onComplete?: () => void
      onError?: (error: string) => void
    }
  ): () => void {
    const authStore = useAuthStore()
    const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
    const ctrl = new AbortController()
    let completed = false

    const timeoutId = setTimeout(() => {
      if (!completed) {
        completed = true
        ctrl.abort()
        callbacks.onError?.('AI响应超时，请稍后重试')
      }
    }, 5 * 60 * 1000)

    function finishStream() {
      if (completed) return
      completed = true
      clearTimeout(timeoutId)
      callbacks.onComplete?.()
    }

    fetchEventSource(`${baseUrl}/ai/generate-questions-stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${authStore.token}`
      },
      body: JSON.stringify(data),
      signal: ctrl.signal,
      async onopen(response) {
        if (!response.ok) {
          if (response.status === 401) {
            authStore.logout()
            callbacks.onError?.('登录已过期，请重新登录')
          } else {
            const errorData = await response.json().catch(() => ({ message: '请求失败' }))
            callbacks.onError?.(errorData.message || '请求失败')
          }
        }
      },
      onmessage(msg) {
        if (msg.event === 'done') {
          finishStream()
          ctrl.abort()
        } else if (msg.event === 'error') {
          if (!completed) {
            completed = true
            clearTimeout(timeoutId)
            callbacks.onError?.(msg.data)
          }
          ctrl.abort()
        } else {
          callbacks.onChunk?.(msg.data)
        }
      },
      onerror(err) {
        if (!completed) {
          completed = true
          clearTimeout(timeoutId)
          callbacks.onError?.(err.message || '连接失败')
        }
        throw err // 阻止自动重连
      },
      onclose() {
        finishStream()
      }
    })

    return () => {
      clearTimeout(timeoutId)
      ctrl.abort()
    }
  }
}
