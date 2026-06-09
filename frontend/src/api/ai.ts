import request from '@/utils/request'
import { useAuthStore } from '@/stores/auth'
import type { AiConfig } from '@/types'

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
   * 直接返回 EventSource，前端通过 onmessage 接收纯文本 chunk
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

    const controller = new AbortController()
    let completed = false  // 防止 onComplete/onError 重复调用

    // 安全超时：5分钟无完成信号则自动断开
    const timeoutId = setTimeout(() => {
      if (!completed) {
        completed = true
        controller.abort()
        callbacks.onError?.('AI响应超时，请稍后重试')
      }
    }, 5 * 60 * 1000)

    function finishStream() {
      if (completed) return
      completed = true
      clearTimeout(timeoutId)
      callbacks.onComplete?.()
    }

    fetch(`${baseUrl}/ai/generate-questions-stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${authStore.token}`
      },
      body: JSON.stringify(data),
      signal: controller.signal
    }).then(async response => {
      if (!response.ok) {
        if (response.status === 401) {
          const authStore = useAuthStore()
          authStore.logout()
          callbacks.onError?.('登录已过期，请重新登录')
          return
        }
        const errorData = await response.json().catch(() => ({ message: '请求失败' }))
        callbacks.onError?.(errorData.message || '请求失败')
        return
      }

      const reader = response.body?.getReader()
      if (!reader) {
        callbacks.onError?.('无法读取响应流')
        return
      }

      const decoder = new TextDecoder()
      let buffer = ''
      let currentEvent = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) {
          // 流正常关闭，处理 buffer 中剩余的数据
          if (buffer) {
            const lines = buffer.split('\n')
            for (const line of lines) {
              if (line.startsWith('event:')) {
                currentEvent = line.substring(6).trim()
              } else if (line.startsWith('data:')) {
                const eventData = line.substring(5).trim()
                if (currentEvent === 'done') {
                  finishStream()
                  return
                } else if (currentEvent === 'error') {
                  if (!completed) {
                    completed = true
                    clearTimeout(timeoutId)
                    callbacks.onError?.(eventData)
                  }
                  return
                } else {
                  callbacks.onChunk?.(eventData)
                }
              }
            }
          }
          finishStream()
          break
        }

        buffer += decoder.decode(value, { stream: true })

        // SSE 格式：event: xxx\ndata: xxx\n\n
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('event:')) {
            currentEvent = line.substring(6).trim()
          } else if (line.startsWith('data:')) {
            const eventData = line.substring(5).trim()
            if (currentEvent === 'done') {
              // 后端明确发送了完成信号
              finishStream()
              controller.abort()
              return
            } else if (currentEvent === 'error') {
              if (!completed) {
                completed = true
                clearTimeout(timeoutId)
                callbacks.onError?.(eventData)
              }
              controller.abort()
              return
            } else {
              callbacks.onChunk?.(eventData)
            }
          }
        }
      }
    }).catch(error => {
      if (error.name !== 'AbortError') {
        if (!completed) {
          completed = true
          clearTimeout(timeoutId)
          callbacks.onError?.(error.message || '连接失败')
        }
      }
    })

    // 返回关闭连接的函数
    return () => {
      clearTimeout(timeoutId)
      controller.abort()
    }
  }
}
