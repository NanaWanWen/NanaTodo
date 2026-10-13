import { useAuthStore } from '../store/useAuthStore'
import type { AgentAlertVO, SuggestionVO } from '../types'

/**
 * Agent 聊天请求（SSE 流式）
 * 后端返回 SseEmitter，事件类型：token（增量）、done（完整结果）
 */
export async function agentChatStream(
  message: string,
  history: { role: string; content: string }[],
  onToken: (token: string) => void,
  onDone: (full: string) => void,
  onError: (error: string) => void
): Promise<void> {
  const { userId, currentRole } = useAuthStore.getState()
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (userId) headers['X-User-Id'] = String(userId)
  if (currentRole) headers['X-Role-Code'] = currentRole

  let response: Response
  try {
    response = await fetch('/api/agent/chat', {
      method: 'POST',
      headers,
      body: JSON.stringify({ message, history })
    })
  } catch (e) {
    onError(e instanceof Error ? e.message : '网络错误')
    return
  }

  if (!response.ok || !response.body) {
    onError(`HTTP ${response.status}`)
    return
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let currentEvent = ''
  let done = false

  while (!done) {
    const { value, done: streamDone } = await reader.read()
    if (streamDone) break
    buffer += decoder.decode(value, { stream: true })

    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''

    for (const line of lines) {
      if (line.startsWith('event:')) {
        currentEvent = line.slice(6).trim()
      } else if (line.startsWith('data:') && currentEvent) {
        const data = line.slice(5)
        if (currentEvent === 'token') {
          onToken(data)
        } else if (currentEvent === 'done') {
          onDone(data)
          done = true
          break
        }
        currentEvent = ''
      }
    }
  }

  if (!done) {
    onError('流已中断')
  }
}

/**
 * 获取未处理预警列表
 */
export async function agentAlerts(): Promise<AgentAlertVO[]> {
  const { userId, currentRole } = useAuthStore.getState()
  const headers: Record<string, string> = {}
  if (userId) headers['X-User-Id'] = String(userId)
  if (currentRole) headers['X-Role-Code'] = currentRole

  const res = await fetch('/api/agent/alerts', { headers })
  const json = await res.json()
  return json.data ?? []
}

/**
 * 标记预警已处理
 */
export async function agentHandleAlert(alertId: string): Promise<void> {
  const { userId, currentRole } = useAuthStore.getState()
  const headers: Record<string, string> = {}
  if (userId) headers['X-User-Id'] = String(userId)
  if (currentRole) headers['X-Role-Code'] = currentRole

  await fetch(`/api/agent/alerts/${alertId}/handle`, {
    method: 'POST',
    headers
  })
}

/**
 * 获取审批建议
 */
export async function agentSuggestApproval(orderId: number, context?: string): Promise<SuggestionVO> {
  const { userId, currentRole } = useAuthStore.getState()
  const headers: Record<string, string> = {}
  if (userId) headers['X-User-Id'] = String(userId)
  if (currentRole) headers['X-Role-Code'] = currentRole

  const url = context
    ? `/api/agent/suggest/approval/${orderId}?context=${encodeURIComponent(context)}`
    : `/api/agent/suggest/approval/${orderId}`
  const res = await fetch(url, { method: 'POST', headers })
  const json = await res.json()
  return json.data
}

/**
 * 获取发票校验建议
 */
export async function agentSuggestInvoiceCheck(invoiceId: number, context?: string): Promise<SuggestionVO> {
  const { userId, currentRole } = useAuthStore.getState()
  const headers: Record<string, string> = {}
  if (userId) headers['X-User-Id'] = String(userId)
  if (currentRole) headers['X-Role-Code'] = currentRole

  const url = context
    ? `/api/agent/suggest/invoice/${invoiceId}?context=${encodeURIComponent(context)}`
    : `/api/agent/suggest/invoice/${invoiceId}`
  const res = await fetch(url, { method: 'POST', headers })
  const json = await res.json()
  return json.data
}

/**
 * 获取付款建议
 */
export async function agentSuggestPayment(invoiceId: number, context?: string): Promise<SuggestionVO> {
  const { userId, currentRole } = useAuthStore.getState()
  const headers: Record<string, string> = {}
  if (userId) headers['X-User-Id'] = String(userId)
  if (currentRole) headers['X-Role-Code'] = currentRole

  const url = context
    ? `/api/agent/suggest/payment/${invoiceId}?context=${encodeURIComponent(context)}`
    : `/api/agent/suggest/payment/${invoiceId}`
  const res = await fetch(url, { method: 'POST', headers })
  const json = await res.json()
  return json.data
}
