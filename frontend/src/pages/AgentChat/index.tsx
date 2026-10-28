import { useEffect, useRef, useState } from 'react'
import { App as AntdApp, Button, Input, Popconfirm, Tag, Table } from 'antd'
import type { ColumnsType } from 'antd/es/table'

import { agentChatStream } from '../../api/agent'
import { PageCard } from '../../components/PageCard'
import { useAuthStore } from '../../store/useAuthStore'

/**
 * AI Agent 智能对话面板
 * - SSE 流式接收 LLM 回复（无 API Key 时自动降级为规则模式）
 * - 规则模式返回 JSON（type + data + note），自动识别并渲染为结构化表格
 * - LLM 模式返回自然语言文本，直接展示
 */

const EXAMPLES = [
  '上个月各状态的我的采购订单有多少？',
  '本月我的采购订单总额是多少？',
  '近三个月各供应商采购金额排行前5名',
  '审批中的订单有哪些？',
  '当前库存低于安全库存的物料有哪些？',
  '各供应商的应付余额是多少？',
]

interface Message {
  role: 'user' | 'assistant'
  content: string
}

interface TableData {
  columns: ColumnsType<Record<string, unknown>>
  rows: Record<string, unknown>[]
}

interface SummaryData {
  items: { label: string; value: string }[]
}

interface ParsedResponse {
  note?: string
  table?: TableData | null
  summary?: SummaryData | null
  text?: string
}

/** 字段名中英映射，避免直接展示英文字段 */
const LABELS: Record<string, string> = {
  orderNo: '订单编号',
  title: '订单标题',
  receiptNo: '收货单号',
  invoiceNo: '发票号',
  paymentNo: '付款单号',
  supplierName: '供应商',
  buyerName: '采购员',
  materialName: '物料',
  deptName: '部门',
  name: '名称',
  code: '编码',
  groupName: '分组',
  status: '状态',
  statusName: '状态名称',
  expectDate: '期望到货日期',
  orderDate: '下单日期',
  totalQty: '总数量',
  totalAmount: '总金额',
  qty: '数量',
  amount: '金额',
  orderCount: '订单数',
  count: '数量',
  stockQty: '库存数量',
  safetyStock: '安全库存',
  balance: '应付余额',
  totalCount: '订单总数',
  draftCount: '草稿',
  approvingCount: '审批中',
  approvingAmount: '审批中金额',
  approvedCount: '已审批',
  rejectedCount: '已驳回',
  canceledCount: '已取消',
  dataTime: '数据时间',
  expectedDate: '期望到货',
  unpaidAmount: '未付金额',
  unpaidInvoiceCount: '未付发票数',
  paidAmount: '已付金额',
  createTime: '创建时间'
}

/** 订单状态码转中文 */
const STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  APPROVING: '审批中',
  APPROVED: '已审批',
  REJECTED: '已驳回',
  CANCELED: '已取消',
  COMPLETED: '已完成'
}

function labelOf(key: string): string {
  return LABELS[key] ?? key
}

/** 汇总字段的展示顺序与单位处理 */
const SUMMARY_KEYS: { key: string; money?: boolean }[] = [
  { key: 'totalCount' },
  { key: 'totalAmount', money: true },
  { key: 'draftCount' },
  { key: 'approvingCount' },
  { key: 'approvingAmount', money: true },
  { key: 'approvedCount' },
  { key: 'rejectedCount' },
  { key: 'canceledCount' }
]

/** 尝试从文本中提取 JSON 对象（LLM 模式返回的格式） */
function extractJsonFromText(text: string): string | null {
  // 格式 A：```json ... ``` 或 ``` ... ``` 代码块
  const jsonBlockMatch = text.match(/```(?:json)?\s*\n?([\s\S]*?)\n?```/)
  if (jsonBlockMatch) {
    const body = jsonBlockMatch[1].trim()
    if (body.startsWith('{')) return body
  }
  // 格式 B：从 { 开始的最外层 JSON 对象（括号配对提取，避免贪婪匹配出错）
  const start = text.indexOf('{')
  if (start === -1) return null
  let depth = 0
  let inString = false
  let escaped = false
  for (let i = start; i < text.length; i++) {
    const ch = text[i]
    if (escaped) {
      escaped = false
      continue
    }
    if (ch === '\\') {
      escaped = true
      continue
    }
    if (ch === '"') {
      inString = !inString
      continue
    }
    if (inString) continue
    if (ch === '{') depth++
    else if (ch === '}') {
      depth--
      if (depth === 0) {
        const candidate = text.slice(start, i + 1)
        return candidate.includes('"data"') ? candidate : null
      }
    }
  }
  return null
}

/** 解析 AI 回复：支持纯 JSON、LLM 文本 + JSON 代码块、纯文本三种格式 */
function parseResponse(raw: string): ParsedResponse {
  const trimmed = raw.trim()

  // 格式 1：纯 JSON（规则降级模式）
  if (trimmed.startsWith('{')) {
    try {
      const parsed = JSON.parse(trimmed)
      return parseJsonObject(parsed)
    } catch {
      return { text: raw }
    }
  }

  // 格式 2：LLM 模式返回的文本 + JSON 代码块
  const jsonStr = extractJsonFromText(trimmed)
  if (jsonStr) {
    try {
      const parsed = JSON.parse(jsonStr)
      const result = parseJsonObject(parsed)
      // 保留 LLM 的自然语言作为文本
      const textWithoutJson = trimmed.replace(/```(?:json)?\s*\n?[\s\S]*?\n?```/, '').trim()
      if (textWithoutJson && textWithoutJson !== trimmed) {
        result.text = textWithoutJson
      }
      return result
    } catch {
      // JSON 解析失败，返回原文
    }
  }

  // 格式 3：纯文本
  return { text: raw }
}

/** 解析已序列化为对象的 JSON 数据 */
function parseJsonObject(parsed: Record<string, unknown>): ParsedResponse {
  const note: string | undefined = parsed.note as string | undefined

  let table: TableData | null = null
  let summary: SummaryData | null = null
  const data = parsed.data

  if (data && typeof data === 'object' && !Array.isArray(data) && Array.isArray((data as Record<string, unknown>).records)) {
    // PageResult 格式（逾期订单等）
    const records: Record<string, unknown>[] = (data as Record<string, unknown>).records as Record<string, unknown>[]
    table = { columns: buildColumnsFromRows(records), rows: records }
  } else if (Array.isArray(data)) {
    // 数组格式（库存预警 / 供应商余额）
    const rows: Record<string, unknown>[] = data as Record<string, unknown>[]
    table = { columns: buildColumnsFromRows(rows), rows }
  } else if (data && typeof data === 'object' && Array.isArray((data as Record<string, unknown>).rows)) {
    // 排行格式（RankResultVO：rows + total）
    const rows: Record<string, unknown>[] = (data as Record<string, unknown>).rows as Record<string, unknown>[]
    table = { columns: buildColumnsFromRows(rows), rows }
  } else if (data && typeof data === 'object' && !Array.isArray(data)) {
    // 汇总格式（ReportSummaryVO）
    const dataObj = data as Record<string, unknown>
    const items = SUMMARY_KEYS
      .filter(({ key }) => dataObj[key] != null)
      .map(({ key, money }) => ({
        label: labelOf(key),
        value: money
          ? `${Number(dataObj[key]).toLocaleString('zh-CN')} 元`
          : Number(dataObj[key]).toLocaleString('zh-CN')
      }))
    if (items.length > 0) {
      summary = { items }
    }
  }

  // 文本部分（note 单独渲染为 Tag，此处不重复放入）
  const textParts: string[] = []
  if (parsed.suggestion) textParts.push(parsed.suggestion as string)
  if (parsed.text) textParts.push(parsed.text as string)

  return {
    note,
    table,
    summary,
    text: textParts.length > 0 ? textParts.join('\n') : undefined
  }
}

function buildColumnsFromRows(rows: Record<string, unknown>[]): ColumnsType<Record<string, unknown>> {
  if (rows.length === 0) return []
  const keys = Object.keys(rows[0]).filter(k => k !== 'id' && k !== 'deleted' && k !== 'color')
  return keys.map(key => ({
    title: labelOf(key),
    dataIndex: key,
    width: key === 'orderNo' || key === 'receiptNo' || key === 'invoiceNo' ? 160 : undefined,
    render: (v: unknown) => {
      if (v == null) return '—'
      // 状态码翻译为中文
      if ((key === 'status' || key === 'fromStatus' || key === 'toStatus') && typeof v === 'string' && STATUS_LABELS[v]) {
        return STATUS_LABELS[v]
      }
      if (typeof v === 'number') return v.toLocaleString('zh-CN')
      return String(v)
    }
  }))
}

const STORAGE_PREFIX = 'yonerp.agentchat.history.v1'

function storageKeyFor(userId: number | null) {
  return `${STORAGE_PREFIX}.${userId ?? 'anonymous'}`
}

function loadMessages(userId: number | null) {
  if (typeof window === 'undefined') return []
  try {
    const raw = localStorage.getItem(storageKeyFor(userId))
    return raw ? JSON.parse(raw) : []
  } catch {
    return []
  }
}

export function AgentChatPage() {
  const { message: antMessage } = AntdApp.useApp()
  const userId = useAuthStore((state) => state.userId)
  const storageKey = storageKeyFor(userId)
  const [messages, setMessages] = useState<Message[]>(() => loadMessages(userId))
  const [input, setInput] = useState('')
  const [streaming, setStreaming] = useState(false)
  const [streamText, setStreamText] = useState('')
  const messagesRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    try {
      localStorage.setItem(storageKey, JSON.stringify(messages))
    } catch {
      // ignore quota errors
    }
  }, [messages, storageKey])

  useEffect(() => {
    setMessages(loadMessages(userId))
  }, [userId])

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
        {parsed.summary && (
          <div className="mt-2 grid w-full grid-cols-2 gap-2 sm:grid-cols-3">
            {parsed.summary.items.map((item) => (
              <div key={item.label} className="rounded-md border border-line bg-surface-card px-3 py-2">
                <div className="text-xs text-ink-muted">{item.label}</div>
                <div className="mt-1 text-sm font-medium text-ink">{item.value}</div>
              </div>
            ))}
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

      <PageCard
        title="对话记录"
        extra={
          messages.length > 0 ? (
            <Popconfirm
              title="确认清空对话记录？"
              okText="清空"
              cancelText="取消"
              onConfirm={() => {
                setMessages([])
                setStreamText('')
                try {
                  localStorage.removeItem(storageKey)
                } catch {
                  // ignore
                }
              }}
            >
              <Button size="small" danger>
                清空记录
              </Button>
            </Popconfirm>
          ) : null
        }
      >
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
