/**
 * 三单匹配状态/类型的展示元数据（用友式）
 *
 * 所有页面统一从这里取标签与颜色，并对未知枚举值做兜底，
 * 避免存量数据中出现映射表之外的值时渲染崩溃（白屏）。
 */

export interface MetaItem {
  label: string
  color: string
}

const MATCH_STATUS_META: Record<string, MetaItem> = {
  MATCHED: { label: '已匹配', color: 'green' },
  PARTIAL_QTY: { label: '数量差异', color: 'orange' },
  PARTIAL_AMOUNT: { label: '金额差异', color: 'orange' },
  HOLD: { label: '挂起待处理', color: 'red' },
  WAITING_RECEIPT: { label: '等待收货', color: 'gold' },
  WAITING_INVOICE: { label: '等待新票', color: 'gold' },
  WAITING_ORDER: { label: '等待调价', color: 'gold' },
  UNMATCHED: { label: '未匹配', color: 'default' },
  CLOSED: { label: '已关闭', color: 'default' },
  REVERSED: { label: '已冲销', color: 'default' }
}

/** 等待态（差异已登记处理动作，等待后续单据变更；新单据触发重匹配后自动推回正常流程） */
export const WAITING_STATUSES = ['WAITING_RECEIPT', 'WAITING_INVOICE', 'WAITING_ORDER'] as const

/** 待处理态（无人跟进或需重新决策的差异） */
export const PENDING_DIFF_STATUSES = ['HOLD', 'PARTIAL_QTY', 'PARTIAL_AMOUNT'] as const

const MATCH_TYPE_META: Record<string, MetaItem> = {
  AUTO: { label: '自动', color: 'blue' },
  MANUAL: { label: '手工', color: 'green' },
  FORCE: { label: '强制', color: 'orange' }
}

/** 匹配状态展示（未知值原样展示，不抛异常） */
export function matchStatusMeta(status?: string | null): MetaItem {
  if (!status) return { label: '-', color: 'default' }
  return MATCH_STATUS_META[status] ?? { label: status, color: 'default' }
}

/** 匹配类型展示（未知值原样展示，不抛异常） */
export function matchTypeMeta(type?: string | null): MetaItem {
  if (!type) return { label: '-', color: 'default' }
  return MATCH_TYPE_META[type] ?? { label: type, color: 'default' }
}

const RESOLUTION_META: Record<string, MetaItem> = {
  ADD_RECEIPT: { label: '补收货', color: 'blue' },
  REDO_INVOICE: { label: '退票重开', color: 'orange' },
  ADJUST_PRICE: { label: '调价', color: 'purple' },
  ALLOW_LOSS: { label: '合理损耗放行', color: 'green' },
  FORCE_MATCH: { label: '领导审批强制匹配', color: 'red' }
}

/** 差异处理动作展示（未知值原样展示，不抛异常） */
export function resolutionMeta(resolution?: string | null): MetaItem {
  if (!resolution) return { label: '未登记', color: 'default' }
  return RESOLUTION_META[resolution] ?? { label: resolution, color: 'default' }
}
