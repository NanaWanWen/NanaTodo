import { Tag } from 'antd'
import type { OrderStatus } from '../types'

const STATUS_MAP: Record<OrderStatus, { label: string; color: string }> = {
  DRAFT: { label: '草稿', color: '#8C8C8C' },
  APPROVING: { label: '审批中', color: '#1677FF' },
  APPROVED: { label: '已审批', color: '#52C41A' },
  REJECTED: { label: '已驳回', color: '#FF4D4F' },
  CANCELED: { label: '已取消', color: '#BFBFBF' }
}

interface Props {
  status: OrderStatus
}

/**
 * 订单状态胶囊标签（语义色）
 */
export function StatusTag({ status }: Props) {
  const item = STATUS_MAP[status] ?? { label: status, color: '#8C8C8C' }
  return (
    <span
      className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium text-white transition-160"
      style={{ background: item.color }}
    >
      {item.label}
    </span>
  )
}

export const statusLabel = (status: OrderStatus) => STATUS_MAP[status]?.label ?? status

export const statusOptions = (Object.keys(STATUS_MAP) as OrderStatus[]).map((value) => ({
  value,
  label: STATUS_MAP[value].label
}))

export { Tag }
