import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  App as AntdApp,
  Button,
  Descriptions,
  Input,
  Modal,
  Space,
  Table,
  Tag,
  Timeline,
  Typography
} from 'antd'
import type { TableProps } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { orderApi } from '../../api/orders'
import { approvalApi } from '../../api/approvals'
import { PageCard } from '../../components/PageCard'
import { StatusTag } from '../../components/StatusTag'
import { MoneyText } from '../../components/MoneyText'
import type { OrderItem } from '../../types'

/**
 * 订单详情页：头部信息 + 明细 + 审批时间线 + 操作区
 */
export function OrderDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { message, modal } = AntdApp.useApp()
  const queryClient = useQueryClient()

  const [opinion, setOpinion] = useState('')
  const [opinionModal, setOpinionModal] = useState<{ open: boolean; type: 'reject' | 'cancel' | null }>({
    open: false,
    type: null
  })

  const { data: detail, isLoading } = useQuery({
    queryKey: ['order-detail', id],
    queryFn: () => orderApi.detail(Number(id)),
    enabled: Boolean(id)
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['order-detail', id] })
    queryClient.invalidateQueries({ queryKey: ['orders'] })
    queryClient.invalidateQueries({ queryKey: ['todo'] })
    queryClient.invalidateQueries({ queryKey: ['todo-count'] })
    queryClient.invalidateQueries({ queryKey: ['reports'] })
  }

  const actionMutation = useMutation({
    mutationFn: (action: 'submit' | 'withdraw' | 'cancel') =>
      orderApi.action(Number(id), action, { remark: action === 'cancel' ? opinion : undefined }),
    onSuccess: () => {
      message.success('操作成功')
      setOpinion('')
      invalidate()
    },
    onError: (error: Error) => message.error(error.message)
  })

  const rejectMutation = useMutation({
    mutationFn: () => {
      const task = detail?.tasks.find((item) => item.status === 'PENDING')
      if (!task) {
        return Promise.reject(new Error('当前没有待审批节点'))
      }
      return approvalApi.reject(task.id, opinion, task.version ?? 0)
    },
    onSuccess: () => {
      message.success('已驳回')
      setOpinion('')
      invalidate()
    },
    onError: (error: Error) => message.error(error.message)
  })

  const approveMutation = useMutation({
    mutationFn: () => {
      const task = detail?.tasks.find((item) => item.status === 'PENDING')
      if (!task) {
        return Promise.reject(new Error('当前没有待审批节点'))
      }
      return approvalApi.approve(task.id, opinion, task.version ?? 0)
    },
    onSuccess: () => {
      message.success('审批通过')
      setOpinion('')
      invalidate()
    },
    onError: (error: Error) => message.error(error.message)
  })

  const actions = detail?.actions ?? []

  const confirmAction = (action: 'submit' | 'withdraw' | 'cancel') => {
    if (action === 'cancel') {
      setOpinionModal({ open: true, type: 'cancel' })
      return
    }
    modal.confirm({
      title: action === 'submit' ? '确认提交审批？' : '确认撤回订单？',
      content: '提交后将进入采购经理审批流程',
      onOk: () => actionMutation.mutate(action)
    })
  }

  const itemColumns: TableProps<OrderItem>['columns'] = [
    { title: '行号', dataIndex: 'lineNo', width: 70 },
    { title: '物料', dataIndex: 'materialName', width: 200 },
    { title: '规格', dataIndex: 'spec', width: 160 },
    { title: '单位', dataIndex: 'unit', width: 70 },
    {
      title: '数量',
      dataIndex: 'qty',
      width: 100,
      align: 'right' as const,
      render: (value: number) => Number(value ?? 0).toFixed(3)
    },
    {
      title: '单价',
      dataIndex: 'price',
      width: 120,
      align: 'right' as const,
      render: (value: number) => Number(value ?? 0).toFixed(4)
    },
    {
      title: '税率(%)',
      dataIndex: 'taxRate',
      width: 90,
      align: 'right' as const,
      render: (value: number) => Number(value ?? 0).toFixed(2)
    },
    {
      title: '金额',
      dataIndex: 'amount',
      width: 130,
      align: 'right' as const,
      render: (value: number) => <MoneyText value={value} />
    },
    { title: '备注', dataIndex: 'remark', ellipsis: true }
  ]

  return (
    <div className="flex flex-col gap-5">
      <PageCard
        title={detail ? `${detail.orderNo} ${detail.title}` : '订单详情'}
        extra={
          <Space>
            <Button onClick={() => navigate('/orders')}>返回列表</Button>
            {actions.includes('edit') && (
              <Button onClick={() => navigate(`/orders/${id}/edit`)}>编辑</Button>
            )}
            {actions.includes('submit') && (
              <Button
                type="primary"
                loading={actionMutation.isPending}
                onClick={() => confirmAction('submit')}
              >
                提交审批
              </Button>
            )}
            {actions.includes('withdraw') && (
              <Button loading={actionMutation.isPending} onClick={() => confirmAction('withdraw')}>
                撤回
              </Button>
            )}
            {actions.includes('cancel') && (
              <Button danger onClick={() => confirmAction('cancel')}>
                取消订单
              </Button>
            )}
          </Space>
        }
      >
        {detail && (
          <Descriptions
            bordered
            size="small"
            column={3}
            labelStyle={{ width: 110, color: '#5A6B85' }}
          >
            <Descriptions.Item label="订单编号">{detail.orderNo}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <StatusTag status={detail.status} />
            </Descriptions.Item>
            <Descriptions.Item label="当前节点">
              {detail.status === 'APPROVING' ? (
                <Tag color="blue">{detail.currentNode === 1 ? '采购经理审批' : '总监审批'}</Tag>
              ) : (
                '-'
              )}
            </Descriptions.Item>
            <Descriptions.Item label="供应商">{detail.supplierName}</Descriptions.Item>
            <Descriptions.Item label="采购员">{detail.buyerName}</Descriptions.Item>
            <Descriptions.Item label="币种">{detail.currency}</Descriptions.Item>
            <Descriptions.Item label="订单日期">{detail.orderDate}</Descriptions.Item>
            <Descriptions.Item label="期望到货">{detail.expectDate ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="提交时间">{detail.submitTime ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="数量合计">
              {Number(detail.totalQty ?? 0).toFixed(3)}
            </Descriptions.Item>
            <Descriptions.Item label="金额合计">
              <MoneyText value={detail.totalAmount} />
            </Descriptions.Item>
            <Descriptions.Item label="备注" span={2}>
              {detail.remark ?? '-'}
            </Descriptions.Item>
          </Descriptions>
        )}
      </PageCard>

      <PageCard title="物料明细">
        <Table
          rowKey={(record: OrderItem) => String(record.id ?? record.lineNo)}
          size="small"
          loading={isLoading}
          columns={itemColumns}
          dataSource={detail?.items ?? []}
          pagination={false}
          scroll={{ x: 1000 }}
        />
      </PageCard>

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
        <PageCard title="审批进度">
          <Timeline
            items={(detail?.tasks ?? []).map((task) => ({
              color:
                task.status === 'APPROVED'
                  ? 'green'
                  : task.status === 'REJECTED'
                    ? 'red'
                    : task.status === 'PENDING'
                      ? 'blue'
                      : 'gray',
              children: (
                <div>
                  <div className="font-medium text-ink">{task.nodeName}</div>
                  <div className="text-xs text-ink-muted">
                    {task.status === 'APPROVED'
                      ? `${task.approverName ?? ''} 于 ${task.approveTime ?? ''} 通过`
                      : task.status === 'REJECTED'
                        ? `${task.approverName ?? ''} 于 ${task.approveTime ?? ''} 驳回`
                        : task.status === 'PENDING'
                          ? '等待审批'
                          : '未开始'}
                  </div>
                  {task.opinion && (
                    <div className="mt-1 rounded bg-surface-page px-2 py-1 text-xs text-ink-muted">
                      意见：{task.opinion}
                    </div>
                  )}
                </div>
              )
            }))}
          />
        </PageCard>

        <PageCard title="审批流水">
          <Timeline
            items={(detail?.logs ?? []).map((log) => ({
              color: log.action === 'REJECT' ? 'red' : log.action === 'APPROVE' ? 'green' : 'blue',
              children: (
                <div>
                  <div className="font-medium text-ink">
                    {log.actionName}
                    {log.fromStatus && log.toStatus && (
                      <span className="ml-2 text-xs text-ink-muted">
                        {log.fromStatus} → {log.toStatus}
                      </span>
                    )}
                  </div>
                  <div className="text-xs text-ink-muted">
                    {log.operatorName} · {log.operateTime}
                  </div>
                  {log.opinion && (
                    <div className="mt-1 rounded bg-surface-page px-2 py-1 text-xs text-ink-muted">
                      {log.opinion}
                    </div>
                  )}
                </div>
              )
            }))}
          />
        </PageCard>
      </div>

      {(actions.includes('approve') || actions.includes('reject')) && (
        <PageCard title="审批操作">
          <div className="flex flex-col gap-3">
            <Input.TextArea
              rows={3}
              maxLength={500}
              placeholder="审批意见（驳回必填）"
              value={opinion}
              onChange={(event) => setOpinion(event.target.value)}
            />
            <Space>
              <Button
                type="primary"
                loading={approveMutation.isPending}
                onClick={() => approveMutation.mutate()}
              >
                同意
              </Button>
              <Button
                danger
                loading={rejectMutation.isPending}
                onClick={() => setOpinionModal({ open: true, type: 'reject' })}
              >
                驳回
              </Button>
            </Space>
            <Typography.Text type="secondary" className="text-xs">
              审批结果以服务端状态机为准，重复提交会被幂等拦截
            </Typography.Text>
          </div>
        </PageCard>
      )}

      <Modal
        title={opinionModal.type === 'cancel' ? '取消订单' : '驳回订单'}
        open={opinionModal.open}
        okText="确认"
        cancelText="取消"
        onCancel={() => setOpinionModal({ open: false, type: null })}
        onOk={() => {
          if (!opinion.trim()) {
            message.warning('请填写原因或意见')
            return
          }
          setOpinionModal({ open: false, type: null })
          if (opinionModal.type === 'cancel') {
            actionMutation.mutate('cancel')
          } else {
            rejectMutation.mutate()
          }
        }}
      >
        <Input.TextArea
          rows={3}
          maxLength={500}
          placeholder={opinionModal.type === 'cancel' ? '取消原因（必填）' : '驳回意见（必填）'}
          value={opinion}
          onChange={(event) => setOpinion(event.target.value)}
        />
      </Modal>
    </div>
  )
}
