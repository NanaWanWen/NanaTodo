import { useState } from 'react'
import { Modal, Table, Button, Space, Tag, Input, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apApi } from '../api/ap'
import type { ApSettlementListItem } from '../types'

interface ManualSettleModalProps {
  open: boolean
  onClose: () => void
  settlement: ApSettlementListItem | null
}

export function ManualSettleModal({ open, onClose, settlement }: ManualSettleModalProps) {
  const queryClient = useQueryClient()
  const [remark, setRemark] = useState('')

  const manualSettleMutation = useMutation({
    mutationFn: () =>
      apApi.manualSettle({
        orderId: settlement!.orderId,
        receiptId: settlement!.receiptId,
        invoiceId: settlement!.invoiceId,
        remark
      }),
    onSuccess: () => {
      message.success('手工结算已完成')
      queryClient.invalidateQueries({ queryKey: ['ap'] })
      setRemark('')
      onClose()
    },
    onError: (error: Error) => message.error(error.message)
  })

  if (!settlement) return null

  const handleOk = () => {
    manualSettleMutation.mutate()
  }

  return (
    <Modal
      title="手工结算"
      open={open}
      onCancel={onClose}
      width={800}
      footer={[
        <Button key="cancel" onClick={onClose}>
          取消
        </Button>,
        <Button
          key="submit"
          type="primary"
          loading={manualSettleMutation.isPending}
          onClick={handleOk}
        >
          提交结算
        </Button>
      ]}
    >
      <div className="flex flex-col gap-4">
        <div className="grid grid-cols-2 gap-4 p-4 bg-gray-50 rounded">
          <div>
            <div className="text-xs text-ink-muted mb-1">结算单号</div>
            <div className="font-medium">{settlement.matchNo}</div>
          </div>
          <div>
            <div className="text-xs text-ink-muted mb-1">订单号</div>
            <div className="font-medium">{settlement.orderNo}</div>
          </div>
          <div>
            <div className="text-xs text-ink-muted mb-1">供应商</div>
            <div>{settlement.supplierName}</div>
          </div>
          <div>
            <div className="text-xs text-ink-muted mb-1">当前状态</div>
            <Tag color={settlement.matchStatus === 'MATCHED' ? 'green' : 'orange'}>
              {settlement.matchStatus}
            </Tag>
          </div>
        </div>

        <div>
          <div className="font-medium mb-2">金额对比</div>
          <div className="grid grid-cols-3 gap-4">
            <div className="p-3 bg-blue-50 rounded">
              <div className="text-xs text-ink-muted mb-1">订单金额</div>
              <div className="font-medium text-lg">
                ¥{settlement.orderAmount.toLocaleString('zh-CN', { minimumFractionDigits: 2 })}
              </div>
            </div>
            <div className="p-3 bg-green-50 rounded">
              <div className="text-xs text-ink-muted mb-1">收货金额</div>
              <div className="font-medium text-lg">
                ¥{settlement.receiptAmount.toLocaleString('zh-CN', { minimumFractionDigits: 2 })}
              </div>
            </div>
            <div className="p-3 bg-orange-50 rounded">
              <div className="text-xs text-ink-muted mb-1">发票金额</div>
              <div className="font-medium text-lg">
                ¥{settlement.invoiceAmount.toLocaleString('zh-CN', { minimumFractionDigits: 2 })}
              </div>
            </div>
          </div>
        </div>

        {settlement.diffAmount > 0 && (
          <div className="p-3 bg-red-50 rounded border border-red-200">
            <div className="flex items-center gap-2 mb-2">
              <span className="text-red-600 font-medium">存在差异</span>
              <Tag color="red">差异金额: ¥{settlement.diffAmount.toFixed(2)}</Tag>
            </div>
            <div className="text-sm text-ink-muted">
              手工结算将确认此差异并标记为已处理。请确保已核实差异原因。
            </div>
          </div>
        )}

        <div>
          <div className="font-medium mb-2">备注</div>
          <Input.TextArea
            rows={3}
            placeholder="请输入手工结算原因或备注..."
            value={remark}
            onChange={(e) => setRemark(e.target.value)}
          />
        </div>
      </div>
    </Modal>
  )
}
