import { useRef, useState } from 'react'
import { App as AntdApp, Button, Input, Tag, Table } from 'antd'
import type { ColumnsType } from 'antd/es/table'

import { agentChatStream } from '../../api/agent'
import { PageCard } from '../../components/PageCard'

/**
 * AI Agent 智能对话面板
 * - SSE 流式接收 LLM 回复（无 API Key 时自动降级为规则模式）
 * - 规则模式返回 JSON（type + data + note），自动识别并渲染为结构化表格
 * - LLM 模式返回自然语言文本，直接展示
 */

const EXAMPLES = [
  '哪些订单逾期了？',
  '当前库存低于安全库存的物料有哪些？',
  '各供应商的应付余额是多少？',
  '上月各供应商采购金额排行',
  '本月采购订单汇总（总额/数量/状态分布）',
]

interface Message {
  role: 'user' | 'assistant'
  content: string
}

/** 解析 AI 回复：若为规则模式 JSON 则提取 data，否则原样展示 */
function parseResponse(raw: string): { note?: string; table?: { columns: ColumnsType<Record<string, unknown>>; rows: Record<string, unknown>[] } | null; text?: string } {
  const trimmed = raw.trim()
  if (!trimmed.startsWith('{')) return { text: raw }

  try {
    const parsed = JSON.parse(trimmed)
    const note: string | undefined = parsed.note

    // 提取表格数据
    let table: { columns: ColumnsType<Record<string, unknown>>; rows: Record<string, unknown>[] } | null = null
    const data = parsed.data

    if (data && typeof data === 'object' && !Array.isArray(data) && Array.isArray(data.records)) {
      // PageResult 格式（逾期订单等）
      const records: Record<string, unknown>[] = data.records
      table = {
        columns: buildColumnsFromRows(records),
        rows: records
      }
    } else if (Array.isArray(data)) {
      // 数组格式（库存预警 / 供应商余额 / 排行）
      const rows: Record<string, unknown>[] = data
      table = {
        columns: buildColumnsFromRows(rows),
        rows
      }
    }

    // 文本部分（LLM 模式或 note）
    const textParts: string[] = []
    if (note) textParts.push(note)
    if (parsed.suggestion) textParts.push(parsed.suggestion)
    if (parsed.text) textParts.push(parsed.text)

    return {
      note,
      table,
      text: textParts.length > 0 ? textParts.join('\n') : undefined
    }
  } catch {
    return { text: raw }
  }
}

function buildColumnsFromRows(rows: Record<string, unknown>[]): ColumnsType<Record<string, unknown>> {
  if (rows.length === 0) return []
  const keys = Object.keys(rows[0]).filter(k => k !== 'id' && k !== 'deleted')
  return keys.map(key => ({
    title: key,
    dataIndex: key,
    width: key === 'orderNo' || key === 'receiptNo' || key === 'invoiceNo' ? 160 : undefined,
    render: (v: unknown) => {
      if (v == null) return '—'
      if (typeof v === 'number') return v.toLocaleString('zh-CN')
      return String(v)
    }
  }))
}

export function AgentChatPage() {
  const { message: antMessage } = AntdApp.useApp()
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [streaming, setStreaming] = useState(false)
  const [streamText, setStreamText] = useState('')
  const messagesRef = useRef<HTMLDivElement>(null)

  const send = (msg: string) => {
    if (!msg.trim() || streaming) return

    const history = [...messages, { role: 'user' as const, content: msg }]
    setMessages(history)
    setInput('')
    setStreaming(true)
    setStreamText('')

    let accumulated = ''

    agentChatStream(
      msg,
      history.slice(0, -1).map((m) => ({ role: m.role, content: m.content })),
      (token) => {
        accumulated += token
        setStreamText(accumulated)
        requestAnimationFrame(() => {
          messagesRef.current?.scrollTo({ top: messagesRef.current.scrollHeight, behavior: 'smooth' })
        })
      },
      (full) => {
        setMessages((prev) => [...prev, { role: 'assistant', content: full }])
        setStreaming(false)
        setStreamText('')
        requestAnimationFrame(() => {
          messagesRef.current?.scrollTo({ top: messagesRef.current.scrollHeight })
        })
      },
      (error) => {
        antMessage.error(error)
        setStreaming(false)
        setStreamText('')
      }
    )
  }

  /** 渲染 assistant 消息内容（自动识别 JSON 并展示表格） */
  const renderContent = (content: string) => {
    const parsed = parseResponse(content)

    return (
      <>
        {parsed.text && <p className="whitespace-pre-wrap text-sm text-ink">{parsed.text}</p>}
        {parsed.note && <Tag color="blue" className="mb-2">{parsed.note}</Tag>}
        {parsed.table && parsed.table.rows.length > 0 && (
          <div className="mt-2 w-full">
            <Table
              columns={parsed.table.columns}
              dataSource={parsed.table.rows}
              rowKey={(_, idx) => String(idx)}
              size="small"
              pagination={{ pageSize: 5, showTotal: (t) => `共 ${t} 条` }}
            />
          </div>
        )}
        {parsed.table && parsed.table.rows.length === 0 && (
          <p className="text-xs text-ink-muted">暂无相关数据</p>
        )}
      </>
    )
  }

  return (
    <div className="flex flex-col gap-5">
      <PageCard title="AI 智能助手">
        <div className="flex flex-col gap-2">
          <div className="text-xs text-ink-muted">
            当前为演示模式。配置 <code>DASHSCOPE_API_KEY</code> 环境变量后重启后端，即可启用通义千问 LLM 智能对话；
            未配置时自动降级为规则模式（关键词匹配 + 数据查询）。
          </div>
          <div className="flex flex-wrap gap-2">
            {EXAMPLES.map((example) => (
              <Tag
                key={example}
                className="cursor-pointer transition-160 hover:!text-primary"
                onClick={() => send(example)}
              >
                {example}
              </Tag>
            ))}
          </div>
        </div>
      </PageCard>

      <PageCard title="对话记录">
        <div
          ref={messagesRef}
          className="flex max-h-[600px] flex-col gap-4 overflow-y-auto rounded-md border border-line p-4"
        >
          {messages.length === 0 && !streaming && (
            <div className="text-center text-sm text-ink-muted">
              暂无对话记录，请从上方示例问题开始提问，或在下方输入框输入你的问题。
            </div>
          )}

          {messages.map((msg, idx) => (
            <div
              key={idx}
              className={`flex flex-col gap-1 ${
                msg.role === 'user' ? 'items-end' : 'items-start'
              }`}
            >
              <span className="text-xs text-ink-muted">
                {msg.role === 'user' ? '我' : 'AI 助手'}
              </span>
              <div
                className={`max-w-full rounded-lg px-4 py-2.5 text-sm ${
                  msg.role === 'user'
                    ? 'max-w-[70%] bg-primary text-white'
                    : 'w-full border border-line bg-surface-card text-ink'
                }`}
              >
                {msg.role === 'user' ? msg.content : renderContent(msg.content)}
              </div>
            </div>
          ))}

          {streaming && (
            <div className="flex flex-col gap-1 items-start">
              <span className="text-xs text-ink-muted">AI 助手（生成中…）</span>
              <div className="max-w-[80%] whitespace-pre-wrap rounded-lg border border-primary/30 bg-primary/5 px-4 py-2.5 text-sm text-ink">
                {streamText}
                <span className="inline-block h-4 w-0.5 animate-pulse bg-primary ml-1" />
              </div>
            </div>
          )}
        </div>

        <div className="mt-4 flex gap-3">
          <Input.TextArea
            autoSize={{ minRows: 2, maxRows: 4 }}
            placeholder="输入问题，例如：哪些订单逾期了？"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onPressEnter={(e) => {
              if (!e.shiftKey) {
                e.preventDefault()
                send(input)
              }
            }}
            disabled={streaming}
          />
          <Button
            type="primary"
            disabled={streaming || !input.trim()}
            onClick={() => send(input)}
          >
            发送
          </Button>
        </div>
      </PageCard>
    </div>
  )
}
