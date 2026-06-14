/**
 * 错误处理工具模块
 *
 * 提供统一的错误信息提取功能，从不同类型的错误对象中提取可读的错误消息
 * 支持：
 * - Axios 错误（response.data.message / error / msg）
 * - 自定义 Error 对象（message 属性）
 * - 其他未知错误类型（返回 fallback 默认消息）
 */

/** Axios 错误对象的可能结构 */
type ErrorWithResponse = {
  response?: {
    data?: {
      message?: string
      error?: string
      msg?: string
    }
  }
  message?: string
}

/**
 * 从错误对象中提取可读的错误消息
 *
 * 按优先级依次尝试提取：
 * 1. response.data.message - 后端返回的业务错误消息
 * 2. response.data.error - 后端返回的错误描述
 * 3. response.data.msg - 后端返回的简短消息
 * 4. error.message - 原生 Error 对象的消息
 * 5. fallback - 以上都未找到时使用的默认消息
 *
 * @param error 捕获的错误对象
 * @param fallback 默认错误消息（当无法提取具体消息时使用）
 * @returns 可读的错误消息字符串
 */
export function getErrorMessage(error: unknown, fallback: string): string {
  if (typeof error === 'object' && error !== null) {
    const maybeError = error as ErrorWithResponse
    if (maybeError.response?.data?.message) {
      return maybeError.response.data.message
    }
    if (maybeError.response?.data?.error) {
      return maybeError.response.data.error
    }
    if (maybeError.response?.data?.msg) {
      return maybeError.response.data.msg
    }
    if (maybeError.message) {
      return maybeError.message
    }
  }
  return fallback
}

