import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntdApp, Button, Input, InputNumber, Modal, Select, Space, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { paymentApi } from '../../api/payments'
import { invoiceApi } from '../../api/invoices'
import { PageCard } from '../../components/PageCard'
import type { InvoiceListItem, PaymentListItem } from '../../types'

const STATUS_MAP: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待付款', color: 'warning' },
  PAID: { label: '已付款', color: 'success' },
  REJECTED: { label: '已驳回', color: 'error' }
}

const METHOD_LABEL: Record<string, string> = {
  BANK_TRANSFER: '银行转账',
  ALIPAY: '支付宝',
  WECHAT: '微信',
  CASH: '现金'
}

const money = (value: number) =>
  `¥${Number(value ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

/**
 * 付款单：列表 + 新建（选已核对发票 + 金额 + 方式）+ 确认/驳回
 */
export function PaymentListPage() {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<string | undefined>()
  const [pageNo, setPageNo] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [selectedInvoiceId, setSelectedInvoiceId] = useState<number | undefined>()
  const [paymentAmount, setPaymentAmount] = useState<number | undefined>()
  const [paymentMethod, setPaymentMethod] = useState('BANK_TRANSFER')

  const { data, isFetching } = useQuery({
    queryKey: ['payments', keyword, status, pageNo, pageSize],
    queryFn: () => paymentApi.page({ keyword: keyword || undefined, status, pageNo, pageSize })
  })

  const { data: invoicePage = { records: [], total: 0 } } = useQuery({
    queryKey: ['invoices-for-payment'],
    queryFn: async () => invoiceApi.page({ pageNo: 1, pageSize: 100, invoiceStatus: 'CHECKED' })
  })

  const confirmMutation = useMutation({
    mutationFn: (id: number) => paymentApi.confirm(id),
    onSuccess: () => {
      message.success('付款确认成功')
      queryClient.invalidateQueries({ queryKey: ['payments'] })
      queryClient.invalidateQueries({ queryKey: ['invoices'] })
    },
    onError: (error: Error) => message.error(error.message)
  })

  const rejectMutation = useMutation({
    mutationFn: (id: number) => paymentApi.reject(id),
    onSuccess: () => {
      message.success('付款已驳回')
      queryClient.invalidateQueries({ queryKey: ['payments'] })
    },
    onError: (error: Error) => message.error(error.message)
  })

  const createMutation = useMutation({
    mutationFn: () =>
      paymentApi.create({
        invoiceId: selectedInvoiceId!,
        paymentAmount,
        paymentMethod
      }),
    onSuccess: () => {
      message.success('付款单创建成功')
      setCreateModalOpen(false)
      setSelectedInvoiceId(undefined)
      setPaymentAmount(undefined)
      queryClient.invalidateQueries({ queryKey: ['payments'] })
    },
    onError: (error: Error) => message.error(error.message)
  })

  const columns: ColumnsType<PaymentListItem> = [
    { title: '付款单号', dataIndex: 'paymentNo', width: 160 },
    {
      title: '发票号',
      dataIndex: 'invoiceNo',
      width: 140,
      render: (value: string, row) => (
        <Button type="link" size="small" onClick={() => navigate(`/invoices`)}>
          {value}
        </Button>
      )
    },
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
    { title: '付款日期', dataIndex: 'paymentDate', width: 110 },
    {
      title: '金额',
      dataIndex: 'paymentAmount',
      width: 120,
      align: 'right',
      render: (v: number) => money(v),
      className: 'money-text'
    },
    {
      title: '方式',
      dataIndex: 'paymentMethod',
      width: 100,
      render: (v: string) => METHOD_LABEL[v] ?? v
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
          {record.status === 'PENDING' && (
            <>
              <Button type="link" size="small" onClick={() => confirmMutation.mutate(record.id)}>
                确认付款
              </Button>
              <Button
                type="link"
                size="small"
                danger
                onClick={() => rejectMutation.mutate(record.id)}
              >
                驳回
              </Button>
            </>
          )}
        </Space>
      )
    }
  ]

  const selectedInvoice: InvoiceListItem | undefined = invoicePage.records.find(
    (i) => i.id === selectedInvoiceId
  )

  return (
    <div className="flex flex-col gap-5">
      <PageCard>
        <div className="flex flex-wrap items-end gap-4">
          <div className="flex flex-col gap-1">
            <span className="text-xs text-ink-muted">付款单号</span>
            <Input
              placeholder="搜索付款单号"
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
            <Button onClick={() => queryClient.invalidateQueries({ queryKey: ['payments'] })}>
              查询
            </Button>
            <Button type="primary" onClick={() => setCreateModalOpen(true)}>
              新建付款单
            </Button>
          </Space>
        </div>
      </PageCard>

      <PageCard title={`付款单（共 ${data?.total ?? 0} 条）`}>
        <Table<PaymentListItem>
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
        title="新建付款单（选择已核对发票）"
        open={createModalOpen}
        onCancel={() => setCreateModalOpen(false)}
        onOk={() => createMutation.mutate()}
        confirmLoading={createMutation.isPending}
        okButtonProps={{ disabled: !selectedInvoiceId || !paymentAmount || paymentAmount <= 0 }}
      >
        <div className="flex flex-col gap-4">
          <div>
            <label className="text-xs text-ink-muted">选择已核对发票</label>
            <Select
              style={{ width: '100%', marginTop: 4 }}
              placeholder="请选择发票"
              value={selectedInvoiceId}
              onChange={(v) => {
                setSelectedInvoiceId(v)
                const inv = invoicePage.records.find((i) => i.id === v)
                if (inv) setPaymentAmount(inv.totalAmount)
              }}
              options={invoicePage.records.map((i: InvoiceListItem) => ({
                value: i.id,
                label: `${i.invoiceNo} · ${i.orderNo} · ${money(i.totalAmount)}`
              }))}
            />
            {selectedInvoice && (
              <div className="mt-2 text-xs text-ink-muted">
                价税合计 {money(selectedInvoice.totalAmount)}，建议付款金额不超过该值
              </div>
            )}
          </div>
          <div>
            <label className="text-xs text-ink-muted">付款金额</label>
            <InputNumber
              style={{ width: '100%', marginTop: 4 }}
              min={0.01}
              precision={2}
              value={paymentAmount}
              onChange={(value) => setPaymentAmount(value ?? undefined)}
            />
          </div>
          <div>
            <label className="text-xs text-ink-muted">付款方式</label>
            <Select
              style={{ width: '100%', marginTop: 4 }}
              value={paymentMethod}
              onChange={setPaymentMethod}
              options={Object.entries(METHOD_LABEL).map(([value, label]) => ({ value, label }))}
            />
          </div>
        </div>
      </Modal>
    </div>
  )
}
