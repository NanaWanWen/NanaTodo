import { useAuthStore } from '../store/useAuthStore'
import type { AgentAlertVO, AlertActionVO, SuggestionVO } from '../types'

function authHeaders(): Record<string, string> {
  const { userId, currentRole } = useAuthStore.getState()
  const headers: Record<string, string> = {}
  if (userId) headers['X-User-Id'] = String(userId)
  if (currentRole) headers['X-Role-Code'] = currentRole
  return headers
}

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
  const headers: Record<string, string> = { 'Content-Type': 'application/json', ...authHeaders() }

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
    let errorMessage = `HTTP ${response.status}`
    try {
      const result = await response.clone().json() as { code?: number; message?: string }
      if (result.code === 3001) {
        errorMessage = result.message ?? '登录状态已失效，请检查当前用户登录状态'
      } else if (result.message) {
        errorMessage = response.status === 401
          ? `AI 服务返回 401：${result.message}`
          : result.message
      }
    } catch {
      // 非 JSON 错误（例如上游模型服务 401）只展示错误，不清除本地登录态
    }
    onError(errorMessage)
    return
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let currentEvent = ''
  let eventData: string[] = []
  let done = false

  const dispatchEvent = () => {
    if (currentEvent && eventData.length > 0) {
      const data = eventData.join('\n')
      if (currentEvent === 'token') {
        onToken(data)
      } else if (currentEvent === 'done') {
        onDone(data)
        done = true
      }
    }
    currentEvent = ''
    eventData = []
  }

  const processLine = (line: string) => {
    const normalized = line.endsWith('\r') ? line.slice(0, -1) : line
    if (normalized === '') {
      dispatchEvent()
    } else if (normalized.startsWith(':')) {
      return
    } else if (normalized.startsWith('event:')) {
      currentEvent = normalized.slice(6).trim()
    } else if (normalized.startsWith('data:')) {
      eventData.push(normalized.slice(5).replace(/^ /, ''))
    }
  }

  while (!done) {
    const { value, done: streamDone } = await reader.read()
    if (streamDone) break
    buffer += decoder.decode(value, { stream: true })

    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''
    for (const line of lines) {
      processLine(line)
      if (done) break
    }
  }

  buffer += decoder.decode()
  if (buffer) processLine(buffer)
  if (!done && (currentEvent || eventData.length > 0)) dispatchEvent()
  if (!done) onError('流已中断')
}

/**
 * 获取预警列表（持久化，支持查看已处理/已忽略）
 */
export async function agentAlerts(handled = false, ignored = false): Promise<AgentAlertVO[]> {
  const params = new URLSearchParams({
    handled: String(handled),
    ignored: String(ignored)
  })
  const res = await fetch(`/api/agent/alerts?${params.toString()}`, { headers: authHeaders() })
  const json = await res.json()
  return json.data ?? []
}

/**
 * 标记预警已读
 */
export async function agentReadAlert(alertId: string): Promise<void> {
  await fetch(`/api/agent/alerts/${alertId}/read`, {
    method: 'POST',
    headers: authHeaders()
  })
}

/**
 * 标记预警已处理
 */
export async function agentHandleAlert(alertId: string, note?: string): Promise<void> {
  const url = note
    ? `/api/agent/alerts/${alertId}/handle?note=${encodeURIComponent(note)}`
    : `/api/agent/alerts/${alertId}/handle`
  await fetch(url, {
    method: 'POST',
    headers: authHeaders()
  })
}

/**
 * 处理预警（标记已处理 + 处理意见）
 */
export async function agentProcessAlert(alertId: string, note?: string): Promise<void> {
  const url = note
    ? `/api/agent/alerts/${alertId}/process?note=${encodeURIComponent(note)}`
    : `/api/agent/alerts/${alertId}/process`
  await fetch(url, {
    method: 'POST',
    headers: authHeaders()
  })
}

/**
 * 忽略预警（不再显示，保留历史）
 */
export async function agentIgnoreAlert(alertId: string): Promise<void> {
  await fetch(`/api/agent/alerts/${alertId}/ignore`, {
    method: 'POST',
    headers: authHeaders()
  })
}

/**
 * 恢复已忽略的预警
 */
export async function agentRestoreAlert(alertId: string): Promise<void> {
  await fetch(`/api/agent/alerts/${alertId}/restore`, {
    method: 'POST',
    headers: authHeaders()
  })
}

/**
 * 指派预警
 */
export async function agentAssignAlert(alertId: string, assigneeId?: number, assignee?: string, note?: string): Promise<void> {
  const body = new URLSearchParams()
  if (assigneeId != null) body.set('assigneeId', String(assigneeId))
  if (assignee) body.set('assignee', assignee)
  if (note) body.set('note', note)
  await fetch(`/api/agent/alerts/${alertId}/assign`, {
    method: 'POST',
    headers: { ...authHeaders(), 'Content-Type': 'application/x-www-form-urlencoded' },
    body: body.toString()
  })
}

/**
 * 转办预警
 */
export async function agentTransferAlert(alertId: string, assigneeId?: number, assignee?: string, note?: string): Promise<void> {
  const body = new URLSearchParams()
  if (assigneeId != null) body.set('assigneeId', String(assigneeId))
  if (assignee) body.set('assignee', assignee)
  if (note) body.set('note', note)
  await fetch(`/api/agent/alerts/${alertId}/transfer`, {
    method: 'POST',
    headers: { ...authHeaders(), 'Content-Type': 'application/x-www-form-urlencoded' },
    body: body.toString()
  })
}

/**
 * 获取预警处理流水（审计时间线）
 */
export async function agentAlertActions(alertId: string): Promise<AlertActionVO[]> {
  const res = await fetch(`/api/agent/alerts/${alertId}/actions`, { headers: authHeaders() })
  const json = await res.json()
  return json.data ?? []
}

/**
 * 手动触发一次预警扫描，返回新生成的预警数量
 */
export async function agentScanAlerts(): Promise<number> {
  const res = await fetch('/api/agent/alerts/scan', { method: 'POST', headers: authHeaders() })
  const json = await res.json()
  return json.data ?? 0
}

/**
 * 获取未读预警计数
 */
export async function agentUnreadCount(): Promise<number> {
  const res = await fetch('/api/agent/alerts/unread-count', { headers: authHeaders() })
  const json = await res.json()
  return json.data ?? 0
}

/**
 * 获取审批建议
 */
export async function agentSuggestApproval(orderId: number, context?: string): Promise<SuggestionVO> {
  const url = context
    ? `/api/agent/suggest/approval/${orderId}?context=${encodeURIComponent(context)}`
    : `/api/agent/suggest/approval/${orderId}`
  const res = await fetch(url, { method: 'POST', headers: authHeaders() })
  const json = await res.json()
  return json.data
}

/**
 * 获取发票校验建议
 */
export async function agentSuggestInvoiceCheck(invoiceId: number, context?: string): Promise<SuggestionVO> {
  const url = context
    ? `/api/agent/suggest/invoice/${invoiceId}?context=${encodeURIComponent(context)}`
    : `/api/agent/suggest/invoice/${invoiceId}`
  const res = await fetch(url, { method: 'POST', headers: authHeaders() })
  const json = await res.json()
  return json.data
}

/**
 * 获取付款建议
 */
export async function agentSuggestPayment(invoiceId: number, context?: string): Promise<SuggestionVO> {
  const url = context
    ? `/api/agent/suggest/payment/${invoiceId}?context=${encodeURIComponent(context)}`
    : `/api/agent/suggest/payment/${invoiceId}`
  const res = await fetch(url, { method: 'POST', headers: authHeaders() })
  const json = await res.json()
  return json.data
}
