import DOMPurify from 'dompurify'

/**
 * DOMPurify 配置：允许的标签和属性
 * 与原有白名单保持一致
 */
const PURIFY_CONFIG: DOMPurify.Config = {
  ALLOWED_TAGS: [
    'p', 'br', 'span', 'div',
    'strong', 'b', 'em', 'i', 'u', 's',
    'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
    'ul', 'ol', 'li',
    'table', 'thead', 'tbody', 'tr', 'th', 'td',
    'img', 'a',
    'code', 'pre', 'blockquote'
  ],
  ALLOWED_ATTR: [
    'src', 'alt', 'title', 'width', 'height',
    'href', 'target',
    'class', 'border',
    'colspan', 'rowspan'
  ],
  ALLOW_DATA_ATTR: false,
  SANITIZE_DOM: true
}

/**
 * 净化 HTML 内容，移除危险的标签和属性
 * @param html 原始 HTML 内容
 * @returns 净化后的安全 HTML
 */
export function sanitizeHtml(html: string): string {
  if (!html) return ''
  return DOMPurify.sanitize(html, PURIFY_CONFIG)
}

/**
 * 转义 HTML 特殊字符（用于纯文本显示）
 */
export function escapeHtml(text: string): string {
  if (!text) return ''

  const escapeMap: Record<string, string> = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  }

  return text.replace(/[&<>"']/g, char => escapeMap[char] || char)
}

/**
 * 截断文本并添加省略号
 */
export function truncateText(text: string, maxLength: number): string {
  if (!text || text.length <= maxLength) return text
  return text.substring(0, maxLength) + '...'
}

/**
 * 净化并截断 HTML 内容
 */
export function sanitizeAndTruncate(html: string, maxLength: number): string {
  const sanitized = sanitizeHtml(html)
  // 移除 HTML 标签获取纯文本长度
  const tempDiv = document.createElement('div')
  tempDiv.innerHTML = sanitized
  const textContent = tempDiv.textContent || ''

  if (textContent.length <= maxLength) {
    return sanitized
  }

  // 如果需要截断，返回纯文本
  return escapeHtml(truncateText(textContent, maxLength))
}
