import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntdApp, Button, Input, Modal, Popconfirm, Select, Space, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { receiptApi } from '../../api/receipts'
import { orderApi } from '../../api/orders'
import { optionsApi } from '../../api/options'
import { PageCard } from '../../components/PageCard'
import { SearchSelect } from '../../components/SearchSelect'
import { ImportModal } from '../../components/ImportModal'
import type { OrderItem, ReceiptCreatePayload, ReceiptListItem } from '../../types'

const STATUS_MAP: Record<string, { label: string; color: string }> = {
  DRAFT: { label: '草稿', color: 'default' },
  CONFIRMED: { label: '已确认', color: 'success' },
  CANCELED: { label: '已取消', color: 'error' }
}

const money = (value: number) =>
  `¥${Number(value ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

/**
 * 采购收货单：列表 + 新建（从已审批订单创建，输入实收数量）+ 确认/取消
 */
export function ReceiptListPage() {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<string | undefined>()
  const [pageNo, setPageNo] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [importModalOpen, setImportModalOpen] = useState(false)
  const [selectedOrderId, setSelectedOrderId] = useState<number | undefined>()
  const [receivedQtyMap, setReceivedQtyMap] = useState<Record<number, number>>({})

  const { data, isFetching } = useQuery({
    queryKey: ['receipts', keyword, status, pageNo, pageSize],
    queryFn: () => receiptApi.page({ keyword: keyword || undefined, status, pageNo, pageSize })
  })

  const { data: orderDetail } = useQuery({
    queryKey: ['order-detail-for-receipt', selectedOrderId],
    queryFn: () => orderApi.detail(selectedOrderId!),
    enabled: !!selectedOrderId
  })
  const orderItems: OrderItem[] = orderDetail?.items ?? []

  const createMutation = useMutation({
    mutationFn: () => {
      const payload: ReceiptCreatePayload = {
        orderId: selectedOrderId!,
        items: Object.entries(receivedQtyMap)
          .filter(([, qty]) => qty > 0)
          .map(([orderItemId, receivedQty]) => ({
            orderItemId: Number(orderItemId),
            receivedQty
          }))
      }
      return receiptApi.create(payload)
    },
    onSuccess: () => {
      message.success('收货单创建成功')
      setCreateModalOpen(false)
      setSelectedOrderId(undefined)
      setReceivedQtyMap({})
      queryClient.invalidateQueries({ queryKey: ['receipts'] })
    },
    onError: (error: Error) => message.error(error.message)
  })

  const confirmMutation = useMutation({
    mutationFn: (id: number) => receiptApi.confirm(id),
    onSuccess: () => {
      message.success('收货确认成功，库存已更新')
      queryClient.invalidateQueries({ queryKey: ['receipts'] })
      queryClient.invalidateQueries({ queryKey: ['inventory'] })
    },
    onError: (error: Error) => message.error(error.message)
  })

  const cancelMutation = useMutation({
    mutationFn: (id: number) => receiptApi.cancel(id),
    onSuccess: () => {
      message.success('收货单已取消（已冲回库存并触发重匹配）')
      queryClient.invalidateQueries({ queryKey: ['receipts'] })
      queryClient.invalidateQueries({ queryKey: ['inventory'] })
    },
    onError: (error: Error) => message.error(error.message)
  })

  const columns: ColumnsType<ReceiptListItem> = [
    { title: '收货单号', dataIndex: 'receiptNo', width: 160 },
    {
      title: '订单号',
      dataIndex: 'orderNo',
      width: 140,
      render: (value: string, row) => (
        <Button type="link" size="small" onClick={() => navigate(`/orders/${row.orderId}`)}>
          {value}
        </Button>
      )
    },
    { title: '供应商', dataIndex: 'supplierName', width: 140 },
    { title: '收货日期', dataIndex: 'receiptDate', width: 110 },
    { title: '数量', dataIndex: 'totalQty', width: 80, align: 'right' },
    {
      title: '金额',
      dataIndex: 'totalAmount',
      width: 120,
      align: 'right',
      render: (v: number) => money(v),
      className: 'money-text'
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => <Tag color={STATUS_MAP[v]?.color}>{STATUS_MAP[v]?.label ?? v}</Tag>
    },
    {
      title: '操作',
      width: 160,
      render: (_v, record) => (
        <Space size="small">
          {record.status === 'DRAFT' && (
            <>
              <Button type="link" size="small" onClick={() => confirmMutation.mutate(record.id)}>
                确认入库
              </Button>
              <Button
                type="link"
                size="small"
                danger
                onClick={() => cancelMutation.mutate(record.id)}
              >
                取消
              </Button>
            </>
          )}
          {record.status === 'CONFIRMED' && (
            <Popconfirm
              title="确认撤销该已确认收货单？"
              description="将冲回对应库存并触发三单匹配重算，请谨慎操作"
              okText="撤销"
              cancelText="取消"
              onConfirm={() => cancelMutation.mutate(record.id)}
            >
              <Button type="link" size="small" danger>
                取消
              </Button>
            </Popconfirm>
          )}
        </Space>
      )
    }
  ]

  return (
    <div className="flex flex-col gap-5">
      <PageCard>
        <div className="flex flex-wrap items-end gap-4">
          <div className="flex flex-col gap-1">
            <span className="text-xs text-ink-muted">收货单号</span>
            <Input
              placeholder="搜索收货单号"
              allowClear
              value={keyword}
              onChange={(e) => {
                setKeyword(e.target.value)
                setPageNo(1)
              }}
              style={{ width: 180 }}
            />
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-xs text-ink-muted">状态</span>
            <Select
              placeholder="全部"
              allowClear
              value={status}
              onChange={(v) => {
                setStatus(v)
                setPageNo(1)
              }}
              options={Object.entries(STATUS_MAP).map(([value, { label }]) => ({ value, label }))}
              style={{ width: 120 }}
            />
          </div>
          <Space>
            <Button onClick={() => queryClient.invalidateQueries({ queryKey: ['receipts'] })}>
              查询
            </Button>
            <Button onClick={() => setImportModalOpen(true)}>Excel 导入</Button>
            <Button type="primary" onClick={() => setCreateModalOpen(true)}>
              新建收货单
            </Button>
          </Space>
        </div>
      </PageCard>

      <PageCard title={`采购收货单（共 ${data?.total ?? 0} 条）`}>
        <Table<ReceiptListItem>
          rowKey="id"
          columns={columns}
          dataSource={data?.records ?? []}
          loading={isFetching}
          size="middle"
          pagination={{
            current: pageNo,
            pageSize,
            total: data?.total ?? 0,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`,
            onChange: (p, s) => {
              setPageNo(p)
              setPageSize(s)
            }
          }}
        />
      </PageCard>

      <Modal
        title="新建收货单（选择已审批订单并填写实收数量）"
        open={createModalOpen}
        onCancel={() => setCreateModalOpen(false)}
        onOk={() => createMutation.mutate()}
        confirmLoading={createMutation.isPending}
        okButtonProps={{
          disabled:
            !selectedOrderId || !Object.values(receivedQtyMap).some((qty) => qty > 0)
        }}
        width={640}
      >
        <div className="flex flex-col gap-4">
          <div>
            <label className="text-xs text-ink-muted">选择采购订单</label>
            <SearchSelect
              style={{ width: '100%', marginTop: 4 }}
              placeholder="输入订单号、标题或供应商搜索"
              fetchOptions={(keyword) => optionsApi.receivableOrders(keyword)}
              onChange={(v) => {
                setSelectedOrderId(v)
                setReceivedQtyMap({})
              }}
            />
          </div>

          {selectedOrderId && orderItems.length > 0 && (
            <div className="rounded-md border border-line p-3">
              <div className="mb-2 text-xs text-ink-muted">
                实收数量（可部分收货，留空或 0 表示未收）
              </div>
              <div className="flex flex-col gap-2">
                {orderItems.map((item) => (
                  <div key={item.id} className="flex items-center justify-between gap-3">
                    <div className="flex-1 text-sm">
                      <span className="font-medium">{item.materialName}</span>
                      {item.spec && (
                        <span className="ml-2 text-xs text-ink-muted">{item.spec}</span>
                      )}
                      <span className="ml-2 text-xs text-ink-muted">
                        订购 {item.qty} {item.unit} × {money(Number(item.price))}
                      </span>
                    </div>
                    <Input
                      type="number"
                      min={0}
                      max={item.qty}
                      value={receivedQtyMap[item.id!] ?? 0}
                      onChange={(e) =>
                        setReceivedQtyMap((prev) => ({
                          ...prev,
                          [item.id!]: Math.min(Number(e.target.value), item.qty)
                        }))
                      }
                      style={{ width: 100 }}
                      placeholder={`0~${item.qty}`}
                    />
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </Modal>

      <ImportModal
        open={importModalOpen}
        onClose={() => setImportModalOpen(false)}
        bizType="RECEIPT"
        bizLabel="收货单"
        onSuccess={() => queryClient.invalidateQueries({ queryKey: ['receipts'] })}
      />
    </div>
  )
}
