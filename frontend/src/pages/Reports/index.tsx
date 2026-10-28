import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from 'recharts'
import {
  App as AntdApp,
  Button,
  Card as AntCard,
  Collapse,
  DatePicker,
  Progress,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { reportApi } from '../../api/reports'
import { apApi } from '../../api/ap'
import { paymentApi } from '../../api/payments'
import { PageCard } from '../../components/PageCard'
import { matchStatusMeta } from '../../utils/matchMeta'
import dayjs from 'dayjs'
import type {
  ApSettlementListItem,
  PaymentListItem,
  RankGroupBy,
  RankStat,
  SupplierBalanceVO
} from '../../types'

const { RangePicker } = DatePicker

const GROUP_OPTIONS = [
  { value: 'supplier', label: '按供应商' },
  { value: 'buyer', label: '按采购员' },
  { value: 'material', label: '按物料' },
  { value: 'dept', label: '按部门' }
]

const DIM_LABEL: Record<RankGroupBy, string> = {
  supplier: '供应商',
  buyer: '采购员',
  material: '物料',
  dept: '部门'
}

const GROUP_PARAM: Record<RankGroupBy, string> = {
  supplier: 'supplierId',
  buyer: 'buyerId',
  material: 'materialId',
  dept: 'deptId'
}

const STATUS_OPTIONS = [
  { value: '', label: '生效订单（审批中 + 已审批）' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'APPROVING', label: '审批中' },
  { value: 'APPROVED', label: '已审批' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'CANCELED', label: '已取消' }
]

const TOP_OPTIONS = [10, 20, 50]

const download = (blob: Blob, filename: string) => {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

const money = (value: number) =>
  `¥${Number(value ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

const num = (value: number) =>
  Number(value ?? 0).toLocaleString('zh-CN', { maximumFractionDigits: 3 })

const PAYMENT_STATUS: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待付款', color: 'orange' },
  PAID: { label: '已付款', color: 'green' },
  REJECTED: { label: '已驳回', color: 'red' }
}

/**
 * 报表统计：采购报表 + 应付对账 + 现金日记账
 */
export function ReportsPage() {
  const [activeTab, setActiveTab] = useState<'purchase' | 'ap' | 'cash'>('purchase')

  return (
    <div className="flex flex-col gap-5">
      <Tabs
        activeKey={activeTab}
        onChange={(k) => setActiveTab(k as typeof activeTab)}
        items={[
          { key: 'purchase', label: '采购报表' },
          { key: 'ap', label: '应付对账' },
          { key: 'cash', label: '现金日记账' }
        ]}
      />
      {activeTab === 'purchase' && <PurchaseReportsPanel />}
      {activeTab === 'ap' && <ApReconciliationPanel />}
      {activeTab === 'cash' && <CashJournalPanel />}
    </div>
  )
}

/** 采购报表（原有逻辑） */
function PurchaseReportsPanel() {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const [granularity, setGranularity] = useState<'month' | 'day'>('month')
  const [range, setRange] = useState<[string, string] | null>(null)
  const [groupBy, setGroupBy] = useState<RankGroupBy>('supplier')
  const [status, setStatus] = useState<string>('')
  const [topN, setTopN] = useState<number>(10)

  const startDate = range?.[0]
  const endDate = range?.[1]

  const { data: summary } = useQuery({
    queryKey: ['reports', 'summary', startDate, endDate],
    queryFn: () => reportApi.summary(startDate, endDate)
  })

  const { data: statusStats = [] } = useQuery({
    queryKey: ['reports', 'status', startDate, endDate],
    queryFn: () => reportApi.byStatus(startDate, endDate)
  })

  const { data: timeStat } = useQuery({
    queryKey: ['reports', 'time', granularity, startDate, endDate],
    queryFn: () => reportApi.byTime(granularity, startDate, endDate)
  })

  const { data: rank, isFetching: rankLoading } = useQuery({
    queryKey: ['reports', 'rank', groupBy, startDate, endDate, status, topN],
    queryFn: () =>
      reportApi.rank({
        groupBy,
        startDate,
        endDate,
        status: status || undefined,
        topN
      })
  })

  const refreshMutation = useMutation({
    mutationFn: () => reportApi.refresh(),
    onSuccess: () => {
      message.success('报表缓存已刷新')
      queryClient.invalidateQueries({ queryKey: ['reports'] })
    },
    onError: (error: Error) => message.error(error.message)
  })

  const exportRankMutation = useMutation({
    mutationFn: () =>
      reportApi.exportRank({ groupBy, startDate, endDate, status: status || undefined }),
    onSuccess: (blob) => {
      download(blob, `采购统计表_${DIM_LABEL[groupBy]}_${dayjs().format('YYYYMMDD')}.xlsx`)
      message.success('统计表已导出')
    },
    onError: (error: Error) => message.error(error.message)
  })

  const exportOrdersMutation = useMutation({
    mutationFn: () => reportApi.exportOrders({ startDate, endDate, status: status || undefined }),
    onSuccess: (blob) => {
      download(blob, `采购订单明细_${dayjs().format('YYYYMMDD')}.xlsx`)
      message.success('订单明细已导出')
    },
    onError: (error: Error) => message.error(error.message)
  })

  const rows = rank?.rows ?? []
  const total = rank?.total

  const goOrders = (row?: RankStat) => {
    const params = new URLSearchParams()
    if (row) {
      params.set(GROUP_PARAM[groupBy], row.linkValue)
    }
    if (startDate) params.set('startDate', startDate)
    if (endDate) params.set('endDate', endDate)
    if (status) params.set('status', status)
    navigate(`/orders?${params.toString()}`)
  }

  const columns: ColumnsType<RankStat> = [
    { title: '排名', width: 60, render: (_v, _r, i) => i + 1 },
    { title: '编码', dataIndex: 'code', width: 120 },
    { title: DIM_LABEL[groupBy], dataIndex: 'name' },
    {
      title: '订单数',
      dataIndex: 'orderCount',
      width: 100,
      align: 'right',
      sorter: (a, b) => a.orderCount - b.orderCount
    },
    {
      title: '数量',
      dataIndex: 'qty',
      width: 120,
      align: 'right',
      render: (v: number) => num(v),
      sorter: (a, b) => a.qty - b.qty
    },
    {
      title: '含税金额',
      dataIndex: 'amount',
      width: 150,
      align: 'right',
      className: 'money-text',
      render: (v: number) => money(v),
      sorter: (a, b) => a.amount - b.amount,
      defaultSortOrder: 'descend'
    },
    {
      title: '金额占比',
      dataIndex: 'percent',
      width: 180,
      render: (v: number) => (
        <Progress percent={Number(v ?? 0)} size="small" format={(p) => `${p}%`} />
      ),
      sorter: (a, b) => a.percent - b.percent
    },
    {
      title: '操作',
      width: 90,
      align: 'center',
      render: (_v, row) => (
        <Button type="link" size="small" onClick={() => goOrders(row)}>
          联查明细
        </Button>
      )
    }
  ]

  return (
    <div className="flex flex-col gap-5">
      <PageCard>
        <div className="flex flex-wrap items-end gap-4">
          <div className="flex flex-col gap-1">
            <span className="text-xs text-ink-muted">订单日期</span>
            <RangePicker
              value={range ? [dayjs(range[0]), dayjs(range[1])] : null}
              onChange={(dates) =>
                setRange(
                  dates?.[0] && dates?.[1]
                    ? [dates[0].format('YYYY-MM-DD'), dates[1].format('YYYY-MM-DD')]
                    : null
                )
              }
            />
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-xs text-ink-muted">统计维度</span>
            <Select
              value={groupBy}
              options={GROUP_OPTIONS}
              onChange={setGroupBy}
              style={{ width: 140 }}
            />
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-xs text-ink-muted">订单状态</span>
            <Select
              value={status}
              options={STATUS_OPTIONS}
              onChange={setStatus}
              style={{ width: 210 }}
            />
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-xs text-ink-muted">显示前 N</span>
            <Select
              value={topN}
              options={TOP_OPTIONS.map((n) => ({ value: n, label: `TOP ${n}` }))}
              onChange={setTopN}
              style={{ width: 100 }}
            />
          </div>
          <Space>
            <Button onClick={() => queryClient.invalidateQueries({ queryKey: ['reports'] })}>
              查询
            </Button>
            <Button
              onClick={() => {
                setRange(null)
                setGroupBy('supplier')
                setStatus('')
                setTopN(10)
              }}
            >
              重置
            </Button>
          </Space>
          <div className="ml-auto flex items-center gap-3">
            <span className="text-xs text-ink-muted">
              数据更新时间：{rank?.dataTime ?? '-'}（缓存 5 分钟）
            </span>
            <Button loading={refreshMutation.isPending} onClick={() => refreshMutation.mutate()}>
              刷新缓存
            </Button>
          </div>
        </div>
      </PageCard>

      <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-4">
        <AntCard className="shadow-card">
          <Statistic
            title="订单总数（全部口径）"
            value={summary?.totalCount ?? 0}
            suffix="单"
            valueStyle={{ color: '#1C2B45' }}
          />
        </AntCard>
        <AntCard className="shadow-card">
          <Statistic
            title="订单总金额（全部口径）"
            value={summary?.totalAmount ?? 0}
            precision={2}
            prefix="¥"
            valueStyle={{ color: '#1677FF' }}
          />
        </AntCard>
        <AntCard className="shadow-card">
          <Statistic
            title="审批中"
            value={summary?.approvingCount ?? 0}
            suffix="单"
            valueStyle={{ color: '#FAAD14' }}
          />
        </AntCard>
        <AntCard className="shadow-card">
          <Statistic
            title="已审批"
            value={summary?.approvedCount ?? 0}
            suffix="单"
            valueStyle={{ color: '#52C41A' }}
          />
        </AntCard>
      </div>

      <div className="rounded-lg border border-blue-100 bg-blue-50/60 px-4 py-3 text-xs leading-relaxed text-ink-muted">
        <b className="text-ink">口径说明：</b>
        上方四张卡片为<b>全部订单</b>口径（含草稿 / 审批中 / 已审批 / 已驳回 / 已取消）；
        下方各统计表默认只统计<b>生效订单（审批中 + 已审批）</b>，
        因此表内合计会小于卡片总数——这是筛选口径差异，不是数据错误。
        需要对齐时，把「订单状态」切换为对应状态即可（如选「已审批」即与「已审批」卡片一致）。
      </div>

      <PageCard
        title={`按${DIM_LABEL[groupBy]}统计（${status ? '指定状态' : '生效口径'}，TOP ${topN}）`}
        extra={
          <Space>
            <Button size="small" loading={exportRankMutation.isPending} onClick={() => exportRankMutation.mutate()}>
              导出统计表
            </Button>
            <Button size="small" loading={exportOrdersMutation.isPending} onClick={() => exportOrdersMutation.mutate()}>
              导出订单明细
            </Button>
            <Button type="link" size="small" onClick={() => goOrders()}>
              查看全部相关订单
            </Button>
          </Space>
        }
      >
        <Table<RankStat>
          rowKey={(row) => `${row.code}-${row.linkValue}`}
          columns={columns}
          dataSource={rows}
          loading={rankLoading}
          size="middle"
          pagination={false}
          scroll={{ x: 900 }}
          summary={() => (
            <Table.Summary fixed>
              <Table.Summary.Row>
                <Table.Summary.Cell index={0}>
                  <span className="font-medium">合计</span>
                </Table.Summary.Cell>
                <Table.Summary.Cell index={1}>—</Table.Summary.Cell>
                <Table.Summary.Cell index={2}>
                  <span className="text-xs text-ink-muted">全口径（不受 TOP N 限制）</span>
                </Table.Summary.Cell>
                <Table.Summary.Cell index={3} align="right">
                  <span className="font-medium">{total?.orderCount ?? 0}</span>
                </Table.Summary.Cell>
                <Table.Summary.Cell index={4} align="right">
                  <span className="font-medium">{num(total?.qty ?? 0)}</span>
                </Table.Summary.Cell>
                <Table.Summary.Cell index={5} align="right">
                  <span className="money-text font-medium">{money(total?.amount ?? 0)}</span>
                </Table.Summary.Cell>
                <Table.Summary.Cell index={6} align="right">
                  100%
                </Table.Summary.Cell>
                <Table.Summary.Cell index={7} />
              </Table.Summary.Row>
            </Table.Summary>
          )}
        />
      </PageCard>

      <Collapse
        items={[
          {
            key: 'charts',
            label: '图表视图',
            children: (
              <div className="grid grid-cols-1 gap-5 xl:grid-cols-2">
                <div>
                  <div className="mb-2 text-sm text-ink-muted">TOP {topN} 金额排行</div>
                  <div className="h-80">
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={rows} layout="vertical" margin={{ left: 16 }}>
                        <CartesianGrid strokeDasharray="3 3" horizontal={false} />
                        <XAxis type="number" tick={{ fontSize: 12 }} />
                        <YAxis type="category" dataKey="name" width={130} tick={{ fontSize: 12 }} />
                        <Tooltip formatter={(value: number) => money(value)} />
                        <Bar dataKey="amount" name="含税金额" fill="#1677FF" radius={[0, 4, 4, 0]} />
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                </div>
                <div>
                  <div className="mb-2 text-sm text-ink-muted">按状态统计</div>
                  <div className="h-80">
                    {statusStats.length === 0 ? (
                      <div className="flex h-full items-center justify-center text-ink-muted">
                        暂无数据
                      </div>
                    ) : (
                      <ResponsiveContainer width="100%" height="100%">
                        <PieChart>
                          <Pie
                            data={statusStats}
                            dataKey="count"
                            nameKey="statusName"
                            outerRadius={110}
                            label={(entry: any) => `${entry.statusName} ${entry.count}`}
                          >
                            {statusStats.map((item) => (
                              <Cell key={item.status} fill={item.color || '#1677FF'} />
                            ))}
                          </Pie>
                          <Tooltip formatter={(value: number) => `${value} 单`} />
                          <Legend />
                        </PieChart>
                      </ResponsiveContainer>
                    )}
                  </div>
                </div>
                <div className="xl:col-span-2">
                  <div className="mb-2 flex items-center justify-between text-sm text-ink-muted">
                    <span>按时间统计</span>
                    <Select
                      value={granularity}
                      options={[
                        { value: 'month', label: '按月' },
                        { value: 'day', label: '按日' }
                      ]}
                      onChange={setGranularity}
                      style={{ width: 100 }}
                      size="small"
                    />
                  </div>
                  <div className="h-72">
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={timeStat?.points ?? []}>
                        <CartesianGrid strokeDasharray="3 3" vertical={false} />
                        <XAxis dataKey="period" tick={{ fontSize: 12 }} />
                        <YAxis tick={{ fontSize: 12 }} />
                        <Tooltip
                          formatter={(value: number, name: string) =>
                            name === 'amount' ? money(value) : `${value} 单`
                          }
                        />
                        <Legend />
                        <Bar dataKey="amount" name="金额" fill="#1677FF" radius={[4, 4, 0, 0]} />
                        <Bar dataKey="count" name="单量" fill="#52C41A" radius={[4, 4, 0, 0]} />
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                </div>
              </div>
            )
          }
        ]}
      />
    </div>
  )
}

/** 应付对账面板：供应商余额 + 三单匹配概览 */
function ApReconciliationPanel() {
  const { data: balances = [] } = useQuery({
    queryKey: ['ap', 'balances'],
    queryFn: () => apApi.supplierBalances()
  })

  const { data: settlementPage, isFetching } = useQuery({
    queryKey: ['ap', 'settlements'],
    queryFn: () => apApi.settlements({ pageNo: 1, pageSize: 50 })
  })
  const settlements = settlementPage?.records ?? []

  const totalUnpaid = balances.reduce((s, b) => s + Number(b.unpaidAmount || 0), 0)
  const totalPaid = balances.reduce((s, b) => s + Number(b.paidAmount || 0), 0)

  const balanceColumns: ColumnsType<SupplierBalanceVO> = [
    { title: '供应商', dataIndex: 'supplierName', width: 200 },
    {
      title: '未付款金额',
      dataIndex: 'unpaidAmount',
      width: 140,
      align: 'right',
      render: (v: number) => money(v),
      className: 'money-text'
    },
    {
      title: '未付款发票数',
      dataIndex: 'unpaidInvoiceCount',
      width: 130,
      align: 'right'
    },
    {
      title: '已付款',
      dataIndex: 'paidAmount',
      width: 140,
      align: 'right',
      render: (v: number) => money(v)
    },
    {
      title: '应付余额',
      dataIndex: 'balance',
      width: 140,
      align: 'right',
      render: (v: number) => money(v),
      className: 'money-text'
    }
  ]

  const settlementColumns: ColumnsType<ApSettlementListItem> = [
    { title: '订单号', dataIndex: 'orderNo', width: 140 },
    {
      title: '订单金额',
      dataIndex: 'orderAmount',
      width: 120,
      align: 'right',
      render: (v: number) => money(v)
    },
    {
      title: '收货金额',
      dataIndex: 'receiptAmount',
      width: 120,
      align: 'right',
      render: (v: number) => money(v)
    },
    {
      title: '发票金额',
      dataIndex: 'invoiceAmount',
      width: 120,
      align: 'right',
      render: (v: number) => money(v)
    },
    {
      title: '差异',
      dataIndex: 'diffAmount',
      width: 100,
      align: 'right',
      render: (v: number) => money(v)
    },
    {
      title: '匹配状态',
      dataIndex: 'matchStatus',
      width: 110,
      render: (v: string) => {
        const meta = matchStatusMeta(v)
        return <Tag color={meta.color}>{meta.label}</Tag>
      }
    }
  ]

  return (
    <div className="flex flex-col gap-5">
      <div className="grid grid-cols-1 gap-5 md:grid-cols-3">
        <AntCard className="shadow-card">
          <Statistic
            title="应付总额（未付款）"
            value={totalUnpaid}
            precision={2}
            prefix="¥"
            valueStyle={{ color: '#FF4D4F' }}
          />
        </AntCard>
        <AntCard className="shadow-card">
          <Statistic
            title="已付款合计"
            value={totalPaid}
            precision={2}
            prefix="¥"
            valueStyle={{ color: '#52C41A' }}
          />
        </AntCard>
        <AntCard className="shadow-card">
          <Statistic
            title="匹配记录"
            value={settlements.length}
            suffix="条"
            valueStyle={{ color: '#1677FF' }}
          />
        </AntCard>
      </div>

      <PageCard title="供应商应付余额">
        <Table<SupplierBalanceVO>
          rowKey="supplierId"
          columns={balanceColumns}
          dataSource={balances}
          size="middle"
          pagination={false}
          scroll={{ x: 800 }}
        />
      </PageCard>

      <PageCard title={`三单匹配概览（最近 ${settlements.length} 条）`}>
        <Table<ApSettlementListItem>
          rowKey="id"
          columns={settlementColumns}
          dataSource={settlements}
          loading={isFetching}
          size="middle"
          pagination={false}
          scroll={{ x: 800 }}
        />
      </PageCard>
    </div>
  )
}

/** 现金日记账面板：付款流水 + 按日汇总 */
function CashJournalPanel() {
  const { data: paymentPage, isFetching } = useQuery({
    queryKey: ['payments', 'cash-journal'],
    queryFn: () => paymentApi.page({ pageNo: 1, pageSize: 100 })
  })
  const payments = paymentPage?.records ?? []

  const totalPaid = payments
    .filter((p) => p.status === 'PAID')
    .reduce((s, p) => s + Number(p.paymentAmount || 0), 0)

  const totalPending = payments
    .filter((p) => p.status === 'PENDING')
    .reduce((s, p) => s + Number(p.paymentAmount || 0), 0)

  /** 按日期分组统计 */
  const byDay = payments.reduce<Record<string, number>>((acc, p) => {
    if (p.status !== 'PAID') return acc
    const day = p.paymentDate || '未知'
    acc[day] = (acc[day] || 0) + Number(p.paymentAmount || 0)
    return acc
  }, {})
  const chartData = Object.entries(byDay)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([day, amount]) => ({ day, amount }))

  const columns: ColumnsType<PaymentListItem> = [
    { title: '付款单号', dataIndex: 'paymentNo', width: 160 },
    { title: '发票号', dataIndex: 'invoiceNo', width: 140 },
    { title: '供应商', dataIndex: 'supplierName', width: 160 },
    { title: '付款日期', dataIndex: 'paymentDate', width: 110 },
    {
      title: '金额',
      dataIndex: 'paymentAmount',
      width: 130,
      align: 'right',
      render: (v: number) => money(v),
      className: 'money-text'
    },
    { title: '付款方式', dataIndex: 'paymentMethod', width: 100 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => (
        <Tag color={PAYMENT_STATUS[v]?.color}>{PAYMENT_STATUS[v]?.label ?? v}</Tag>
      )
    }
  ]

  return (
    <div className="flex flex-col gap-5">
      <div className="grid grid-cols-1 gap-5 md:grid-cols-3">
        <AntCard className="shadow-card">
          <Statistic
            title="已付款合计"
            value={totalPaid}
            precision={2}
            prefix="¥"
            valueStyle={{ color: '#52C41A' }}
          />
        </AntCard>
        <AntCard className="shadow-card">
          <Statistic
            title="待付款合计"
            value={totalPending}
            precision={2}
            prefix="¥"
            valueStyle={{ color: '#FAAD14' }}
          />
        </AntCard>
        <AntCard className="shadow-card">
          <Statistic
            title="付款笔数"
            value={payments.length}
            suffix="笔"
            valueStyle={{ color: '#1677FF' }}
          />
        </AntCard>
      </div>

      <PageCard title="付款流水（最近 100 笔）">
        <Table<PaymentListItem>
          rowKey="id"
          columns={columns}
          dataSource={payments}
          loading={isFetching}
          size="middle"
          pagination={false}
          scroll={{ x: 900 }}
        />
      </PageCard>

      {chartData.length > 0 && (
        <PageCard title="按日付款金额">
          <div className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} />
                <XAxis dataKey="day" tick={{ fontSize: 12 }} />
                <YAxis tick={{ fontSize: 12 }} />
                <Tooltip formatter={(v: number) => money(v)} />
                <Bar dataKey="amount" name="付款金额" fill="#1677FF" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </PageCard>
      )}
    </div>
  )
}
