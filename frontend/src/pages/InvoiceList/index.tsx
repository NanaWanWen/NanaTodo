import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntdApp, Button, Input, Modal, Select, Space, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { invoiceApi } from '../../api/invoices'
import { receiptApi } from '../../api/receipts'
import { PageCard } from '../../components/PageCard'
import type { InvoiceListItem, ReceiptListItem } from '../../types'

const STATUS_MAP: Record<string, { label: string; color: string }> = {
  UNCHECKED: { label: '未核对', color: 'warning' },
  CHECKED: { label: '已核对', color: 'success' },
  PAID: { label: '已付款', color: 'default' }
}

const money = (value: number) =>
  `¥${Number(value ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

/**
 * 采购发票：列表 + 新建（选已确认收货单）+ 核对/撤销核对
 */
export function InvoiceListPage() {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<string | undefined>()
  const [pageNo, setPageNo] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [selectedReceiptId, setSelectedReceiptId] = useState<number | undefined>()
  const [invoiceDate, setInvoiceDate] = useState<string>()

  const { data, isFetching } = useQuery({
    queryKey: ['invoices', keyword, status, pageNo, pageSize],
    queryFn: () =>
      invoiceApi.page({ keyword: keyword || undefined, invoiceStatus: status, pageNo, pageSize })
  })

  const { data: receiptPage = { records: [], total: 0 } } = useQuery({
    queryKey: ['receipts-for-invoice'],
    queryFn: async () => receiptApi.page({ pageNo: 1, pageSize: 100, status: 'CONFIRMED' })
  })

  const checkMutation = useMutation({
    mutationFn: (id: number) => invoiceApi.check(id),
    onSuccess: () => {
      message.success('发票核对成功')
      queryClient.invalidateQueries({ queryKey: ['invoices'] })
    },
    onError: (error: Error) => message.error(error.message)
  })

  const uncheckMutation = useMutation({
    mutationFn: (id: number) => invoiceApi.uncheck(id),
    onSuccess: () => {
      message.success('已撤销核对')
      queryClient.invalidateQueries({ queryKey: ['invoices'] })
    },
    onError: (error: Error) => message.error(error.message)
  })

  const createMutation = useMutation({
    mutationFn: () =>
      invoiceApi.create({ receiptId: selectedReceiptId!, invoiceDate: invoiceDate || undefined }),
    onSuccess: () => {
      message.success('发票创建成功')
      setCreateModalOpen(false)
      setSelectedReceiptId(undefined)
      setInvoiceDate(undefined)
      queryClient.invalidateQueries({ queryKey: ['invoices'] })
    },
    onError: (error: Error) => message.error(error.message)
  })

  const columns: ColumnsType<InvoiceListItem> = [
    { title: '发票号', dataIndex: 'invoiceNo', width: 160 },
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
    { title: '开票日期', dataIndex: 'invoiceDate', width: 110 },
    {
      title: '不含税金额',
      dataIndex: 'invoiceAmount',
      width: 120,
      align: 'right',
      render: (v: number) => money(v),
      className: 'money-text'
    },
    {
      title: '税额',
      dataIndex: 'taxAmount',
      width: 100,
      align: 'right',
      render: (v: number) => money(v)
    },
    {
      title: '价税合计',
      dataIndex: 'totalAmount',
      width: 120,
      align: 'right',
      render: (v: number) => money(v),
      className: 'money-text'
    },
    {
      title: '状态',
      dataIndex: 'invoiceStatus',
      width: 90,
      render: (v: string) => <Tag color={STATUS_MAP[v]?.color}>{STATUS_MAP[v]?.label ?? v}</Tag>
    },
    {
      title: '操作',
      width: 140,
      render: (_v, record) => (
        <Space size="small">
          {record.invoiceStatus === 'UNCHECKED' && (
            <Button type="link" size="small" onClick={() => checkMutation.mutate(record.id)}>
              核对
            </Button>
          )}
          {record.invoiceStatus === 'CHECKED' && (
            <Button type="link" size="small" onClick={() => uncheckMutation.mutate(record.id)}>
              撤销核对
            </Button>
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
            <span className="text-xs text-ink-muted">发票号</span>
            <Input
              placeholder="搜索发票号"
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
            <Button onClick={() => queryClient.invalidateQueries({ queryKey: ['invoices'] })}>
              查询
            </Button>
            <Button type="primary" onClick={() => setCreateModalOpen(true)}>
              新建发票
            </Button>
          </Space>
        </div>
      </PageCard>

      <PageCard title={`采购发票（共 ${data?.total ?? 0} 条）`}>
        <Table<InvoiceListItem>
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
        title="新建发票（选择已确认收货单）"
        open={createModalOpen}
        onCancel={() => setCreateModalOpen(false)}
        onOk={() => createMutation.mutate()}
        confirmLoading={createMutation.isPending}
        okButtonProps={{ disabled: !selectedReceiptId }}
      >
        <div className="flex flex-col gap-4">
          <div>
            <label className="text-xs text-ink-muted">选择已确认收货单</label>
            <Select
              style={{ width: '100%', marginTop: 4 }}
              placeholder="请选择收货单"
              value={selectedReceiptId}
              onChange={setSelectedReceiptId}
              options={receiptPage.records.map((r: ReceiptListItem) => ({
                value: r.id,
                label: `${r.receiptNo} · ${r.orderNo} · ${r.supplierName} · ${money(
                  r.totalAmount
                )}`
              }))}
            />
          </div>
          <div>
            <label className="text-xs text-ink-muted">开票日期（可选）</label>
            <Input
              type="date"
              style={{ width: '100%', marginTop: 4 }}
              value={invoiceDate}
              onChange={(e) => setInvoiceDate(e.target.value)}
            />
          </div>
        </div>
      </Modal>
    </div>
  )
}
