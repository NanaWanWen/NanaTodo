import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntdApp, Button, Input, Modal, Popconfirm, Select, Space, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { invoiceApi } from '../../api/invoices'
import { receiptApi } from '../../api/receipts'
import { PageCard } from '../../components/PageCard'
import type { InvoiceListItem, ReceiptListItem } from '../../types'

const STATUS_MAP: Record<string, { label: string; color: string }> = {
  UNCHECKED: { label: '未核对', color: 'warning' },
  CHECKED: { label: '已核对', color: 'success' },
  PAID: { label: '已付款', color: 'default' },
  RED_FLUSHED: { label: '已红冲', color: 'red' },
  VOIDED: { label: '已作废', color: 'default' }
}

const INVOICE_TYPE_MAP: Record<string, { label: string; color: string }> = {
  BLUE: { label: '蓝字', color: 'blue' },
  RED: { label: '红字', color: 'red' }
}

const money = (value: number) =>
  `¥${Number(value ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

const moneyCell = (value: number) => (
  <span style={Number(value ?? 0) < 0 ? { color: '#cf1322', fontWeight: 600 } : undefined}>{money(value)}</span>
)

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
  const [createInvoiceAmount, setCreateInvoiceAmount] = useState<string>('')
  const [createTaxAmount, setCreateTaxAmount] = useState<string>('')
  const [receiptSearch, setReceiptSearch] = useState('')

  const [editModalOpen, setEditModalOpen] = useState(false)
  const [editingInvoice, setEditingInvoice] = useState<InvoiceListItem | null>(null)
  const [editInvoiceAmount, setEditInvoiceAmount] = useState<string>('')
  const [editTaxAmount, setEditTaxAmount] = useState<string>('')
  const [editInvoiceDate, setEditInvoiceDate] = useState<string>('')
  const [editRemark, setEditRemark] = useState<string>('')

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
      invoiceApi.create({
        receiptId: selectedReceiptId!,
        invoiceDate: invoiceDate || undefined,
        invoiceAmount: createInvoiceAmount ? Number(createInvoiceAmount) : undefined,
        taxAmount: createTaxAmount ? Number(createTaxAmount) : undefined
      }),
    onSuccess: () => {
      message.success('发票创建成功')
      setCreateModalOpen(false)
      setSelectedReceiptId(undefined)
      setInvoiceDate(undefined)
      setCreateInvoiceAmount('')
      setCreateTaxAmount('')
      setReceiptSearch('')
      queryClient.invalidateQueries({ queryKey: ['invoices'] })
    },
    onError: (error: Error) => message.error(error.message)
  })

  const updateMutation = useMutation({
    mutationFn: () =>
      invoiceApi.update(editingInvoice!.id, {
        invoiceAmount: Number(editInvoiceAmount),
        taxAmount: Number(editTaxAmount),
        invoiceDate: editInvoiceDate || undefined,
        remark: editRemark || undefined
      }),
    onSuccess: () => {
      message.success('发票金额修改成功')
      setEditModalOpen(false)
      setEditingInvoice(null)
      setEditInvoiceAmount('')
      setEditTaxAmount('')
      setEditInvoiceDate('')
      setEditRemark('')
      queryClient.invalidateQueries({ queryKey: ['invoices'] })
    },
    onError: (error: Error) => message.error(error.message)
  })

  const redFlushMutation = useMutation({
    mutationFn: (id: number) => invoiceApi.redFlush(id, {}),
    onSuccess: () => {
      message.success('红冲成功：已生成红字发票并自动核对，原票置「已红冲」')
      queryClient.invalidateQueries({ queryKey: ['invoices'] })
    },
    onError: (error: Error) => message.error(error.message)
  })

  const voidMutation = useMutation({
    mutationFn: (id: number) => invoiceApi.voidInvoice(id),
    onSuccess: () => {
      message.success('草稿发票已作废')
      queryClient.invalidateQueries({ queryKey: ['invoices'] })
    },
    onError: (error: Error) => message.error(error.message)
  })

  // 重开：红冲后按同一收货单新建一张正确蓝票
  const openReissue = (record: InvoiceListItem) => {
    setSelectedReceiptId(record.receiptId)
    setInvoiceDate(undefined)
    setCreateInvoiceAmount('')
    setCreateTaxAmount('')
    setReceiptSearch('')
    setCreateModalOpen(true)
  }

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
      render: (v: number) => moneyCell(v),
      className: 'money-text'
    },
    {
      title: '税额',
      dataIndex: 'taxAmount',
      width: 100,
      align: 'right',
      render: (v: number) => moneyCell(v)
    },
    {
      title: '价税合计',
      dataIndex: 'totalAmount',
      width: 120,
      align: 'right',
      render: (v: number) => moneyCell(v),
      className: 'money-text'
    },
    {
      title: '状态',
      dataIndex: 'invoiceStatus',
      width: 90,
      render: (v: string) => <Tag color={STATUS_MAP[v]?.color}>{STATUS_MAP[v]?.label ?? v}</Tag>
    },
    {
      title: '类型',
      dataIndex: 'invoiceType',
      width: 70,
      render: (v: string, row) => (
        <Space size={2} direction="vertical">
          <Tag color={INVOICE_TYPE_MAP[v]?.color ?? 'default'}>
            {INVOICE_TYPE_MAP[v]?.label ?? (v || '蓝字')}
          </Tag>
          {row.relatedInvoiceNo && (
            <span className="text-xs text-ink-muted">冲 {row.relatedInvoiceNo}</span>
          )}
        </Space>
      )
    },
    {
      title: '操作',
      width: 160,
      render: (_v, record) => (
        <Space size="small">
          {record.invoiceStatus === 'UNCHECKED' && (
            <>
              <Button type="link" size="small" onClick={() => checkMutation.mutate(record.id)}>
                核对
              </Button>
              {record.invoiceType !== 'RED' && (
                <>
                  <Button
                    type="link"
                    size="small"
                    onClick={() => {
                      setEditingInvoice(record)
                      setEditInvoiceAmount(String(record.invoiceAmount))
                      setEditTaxAmount(String(record.taxAmount))
                      setEditInvoiceDate(record.invoiceDate)
                      setEditRemark('')
                      setEditModalOpen(true)
                    }}
                  >
                    修改金额
                  </Button>
                  <Popconfirm
                    title="确认作废该草稿发票？"
                    description="作废后不参与三单匹配，不可恢复"
                    okText="作废"
                    cancelText="取消"
                    onConfirm={() => voidMutation.mutate(record.id)}
                  >
                    <Button type="link" size="small" danger>
                      作废
                    </Button>
                  </Popconfirm>
                </>
              )}
            </>
          )}
          {record.invoiceStatus === 'CHECKED' && (
            <>
              {record.invoiceType !== 'RED' && (
                <>
                  <Button type="link" size="small" onClick={() => uncheckMutation.mutate(record.id)}>
                    撤销核对
                  </Button>
                  <Popconfirm
                    title="确认红冲该发票？"
                    description="将生成红字发票抵消原票，原票置「已红冲」，红票自动核对生效"
                    okText="红冲"
                    cancelText="取消"
                    onConfirm={() => redFlushMutation.mutate(record.id)}
                  >
                    <Button type="link" size="small" danger>
                      红冲
                    </Button>
                  </Popconfirm>
                </>
              )}
            </>
          )}
          {record.invoiceStatus === 'RED_FLUSHED' && (
            <Button type="link" size="small" onClick={() => openReissue(record)}>
              重开
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
            <label className="text-xs text-ink-muted">搜索收货单</label>
            <Input
              style={{ width: '100%', marginTop: 4 }}
              placeholder="输入收货单号 / 订单号 / 供应商"
              allowClear
              value={receiptSearch}
              onChange={(e) => setReceiptSearch(e.target.value)}
            />
          </div>
          <div>
            <label className="text-xs text-ink-muted">选择已确认收货单</label>
            <Select
              style={{ width: '100%', marginTop: 4 }}
              placeholder="请选择收货单"
              value={selectedReceiptId}
              onChange={setSelectedReceiptId}
              showSearch
              optionFilterProp="label"
              options={receiptPage.records
                .filter((r: ReceiptListItem) => {
                  const kw = receiptSearch.trim().toLowerCase()
                  if (!kw) return true
                  const text = `${r.receiptNo} ${r.orderNo} ${r.supplierName}`.toLowerCase()
                  return text.includes(kw)
                })
                .map((r: ReceiptListItem) => ({
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
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs text-ink-muted">不含税金额（留空自动计算）</label>
              <Input
                type="number"
                step="0.01"
                style={{ width: '100%', marginTop: 4 }}
                placeholder="0.00"
                value={createInvoiceAmount}
                onChange={(e) => setCreateInvoiceAmount(e.target.value)}
              />
            </div>
            <div>
              <label className="text-xs text-ink-muted">税额（留空自动计算）</label>
              <Input
                type="number"
                step="0.01"
                style={{ width: '100%', marginTop: 4 }}
                placeholder="0.00"
                value={createTaxAmount}
                onChange={(e) => setCreateTaxAmount(e.target.value)}
              />
            </div>
          </div>
        </div>
      </Modal>

      <Modal
        title={`修改发票金额 · ${editingInvoice?.invoiceNo ?? ''}`}
        open={editModalOpen}
        onCancel={() => {
          setEditModalOpen(false)
          setEditingInvoice(null)
          setEditInvoiceAmount('')
          setEditTaxAmount('')
          setEditInvoiceDate('')
          setEditRemark('')
        }}
        onOk={() => updateMutation.mutate()}
        confirmLoading={updateMutation.isPending}
        okButtonProps={{
          disabled:
            editInvoiceAmount === '' ||
            editTaxAmount === '' ||
            Number.isNaN(Number(editInvoiceAmount)) ||
            Number.isNaN(Number(editTaxAmount))
        }}
      >
        <div className="flex flex-col gap-4">
          <Typography.Paragraph type="secondary">
            仅「未核对」发票可修改金额。修改后点击「核对」将按新金额重新匹配。
          </Typography.Paragraph>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs text-ink-muted">不含税金额</label>
              <Input
                type="number"
                step="0.01"
                style={{ width: '100%', marginTop: 4 }}
                placeholder="0.00"
                value={editInvoiceAmount}
                onChange={(e) => setEditInvoiceAmount(e.target.value)}
              />
            </div>
            <div>
              <label className="text-xs text-ink-muted">税额</label>
              <Input
                type="number"
                step="0.01"
                style={{ width: '100%', marginTop: 4 }}
                placeholder="0.00"
                value={editTaxAmount}
                onChange={(e) => setEditTaxAmount(e.target.value)}
              />
            </div>
          </div>
          <div>
            <label className="text-xs text-ink-muted">开票日期</label>
            <Input
              type="date"
              style={{ width: '100%', marginTop: 4 }}
              value={editInvoiceDate}
              onChange={(e) => setEditInvoiceDate(e.target.value)}
            />
          </div>
          <div>
            <label className="text-xs text-ink-muted">备注</label>
            <Input.TextArea
              style={{ width: '100%', marginTop: 4 }}
              rows={3}
              placeholder="退票重开原因等"
              value={editRemark}
              onChange={(e) => setEditRemark(e.target.value)}
            />
          </div>
        </div>
      </Modal>
    </div>
  )
}
