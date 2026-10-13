import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntdApp, Button, Card, Col, Input, Row, Select, Statistic, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apApi } from '../../api/ap'
import { PageCard } from '../../components/PageCard'
import type { ApSettlementListItem, SupplierBalanceVO } from '../../types'

const MATCH_META: Record<ApSettlementListItem['matchStatus'], { label: string; color: string }> = {
  MATCHED: { label: '已匹配', color: 'green' },
  PARTIAL: { label: '部分匹配', color: 'orange' },
  UNMATCHED: { label: '未匹配', color: 'default' }
}

const money = (value: number) =>
  `¥${Number(value ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

/**
 * 应付对账：三单匹配（订单-收货-发票金额校验）+ 供应商应付余额
 */
export function ApSettlementListPage() {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const [orderId, setOrderId] = useState<number>()
  const [matchStatus, setMatchStatus] = useState<string>()

  const { data: settlementPage, isFetching: stLoading } = useQuery({
    queryKey: ['ap', 'settlements', orderId, matchStatus],
    queryFn: () => apApi.settlements({ orderId, matchStatus })
  })
  const settlements = settlementPage?.records ?? []

  const { data: balances = [] } = useQuery({
    queryKey: ['ap', 'balances'],
    queryFn: () => apApi.supplierBalances()
  })

  const matchMutation = useMutation({
    mutationFn: (orderId: number) => apApi.match(orderId),
    onSuccess: () => {
      message.success('三单匹配已完成')
      queryClient.invalidateQueries({ queryKey: ['ap'] })
    },
    onError: (error: Error) => message.error(error.message)
  })

  const columns: ColumnsType<ApSettlementListItem> = [
    {
      title: '订单号',
      dataIndex: 'orderNo',
      width: 160,
      render: (value: string, row) => (
        <Button type="link" size="small" onClick={() => navigate(`/orders/${row.orderId}`)}>
          {value}
        </Button>
      )
    },
    {
      title: '订单金额',
      dataIndex: 'orderAmount',
      width: 130,
      align: 'right',
      render: (v: number) => money(v),
      className: 'money-text'
    },
    {
      title: '收货金额',
      dataIndex: 'receiptAmount',
      width: 130,
      align: 'right',
      render: (v: number) => money(v),
      className: 'money-text'
    },
    {
      title: '发票金额',
      dataIndex: 'invoiceAmount',
      width: 130,
      align: 'right',
      render: (v: number) => money(v),
      className: 'money-text'
    },
    {
      title: '差异金额',
      dataIndex: 'diffAmount',
      width: 130,
      align: 'right',
      render: (v: number) => money(v)
    },
    {
      title: '容差',
      dataIndex: 'tolerance',
      width: 110,
      align: 'right',
      render: (v: number) => money(v)
    },
    {
      title: '匹配状态',
      dataIndex: 'matchStatus',
      width: 120,
      render: (status: ApSettlementListItem['matchStatus']) => (
        <Tag color={MATCH_META[status].color}>{MATCH_META[status].label}</Tag>
      )
    },
    {
      title: '操作',
      width: 100,
      align: 'center',
      render: (_v, row) =>
        row.matchStatus !== 'MATCHED' ? (
          <Button
            type="link"
            size="small"
            loading={matchMutation.isPending}
            onClick={() => matchMutation.mutate(row.orderId)}
          >
            {row.matchStatus === 'UNMATCHED' ? '触发匹配' : '重新匹配'}
          </Button>
        ) : (
          <span className="text-xs text-ink-muted">已匹配</span>
        )
    }
  ]

  return (
    <div className="flex flex-col gap-5">
      <PageCard>
        <div className="flex flex-wrap items-end gap-4">
          <div className="flex flex-col gap-1">
            <span className="text-xs text-ink-muted">订单 ID</span>
            <Input
              placeholder="按订单 ID 筛选"
              allowClear
              value={orderId}
              onChange={(e) => setOrderId(e.target.value ? Number(e.target.value) : undefined)}
              style={{ width: 160 }}
            />
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-xs text-ink-muted">匹配状态</span>
            <Select
              placeholder="全部"
              allowClear
              value={matchStatus}
              onChange={setMatchStatus}
              options={[
                { value: 'MATCHED', label: '已匹配' },
                { value: 'PARTIAL', label: '部分匹配' },
                { value: 'UNMATCHED', label: '未匹配' }
              ]}
              style={{ width: 140 }}
            />
          </div>
          <Button type="primary" onClick={() => queryClient.invalidateQueries({ queryKey: ['ap'] })}>
            查询
          </Button>
        </div>
      </PageCard>

      <Row gutter={16}>
        <Col span={12}>
          <Card>
            <Statistic title="三单匹配记录数" value={settlements.length} suffix="条" />
          </Card>
        </Col>
        <Col span={12}>
          <Card>
            <Statistic
              title="供应商数量（含应付余额）"
              value={balances.length}
              suffix="家"
              valueStyle={{ color: '#1677FF' }}
            />
          </Card>
        </Col>
      </Row>

      <PageCard title="三单匹配（订单 · 收货 · 发票）">
        <Table<ApSettlementListItem>
          rowKey="id"
          columns={columns}
          dataSource={settlements}
          loading={stLoading}
          size="middle"
          pagination={false}
          scroll={{ x: 1000 }}
        />
      </PageCard>

      <PageCard title="供应商应付余额">
        <Table<SupplierBalanceVO>
          rowKey="supplierId"
          loading={false}
          columns={[
            { title: '供应商', dataIndex: 'supplierName' },
            {
              title: '未付款金额',
              dataIndex: 'unpaidAmount',
              align: 'right',
              width: 140,
              render: (v: number) => money(v),
              className: 'money-text'
            },
            {
              title: '未付款发票数',
              dataIndex: 'unpaidInvoiceCount',
              align: 'right',
              width: 140
            },
            {
              title: '已付款',
              dataIndex: 'paidAmount',
              align: 'right',
              width: 140,
              render: (v: number) => money(v)
            },
            {
              title: '应付余额',
              dataIndex: 'balance',
              align: 'right',
              width: 140,
              render: (v: number) => money(v),
              className: 'money-text'
            }
          ]}
          dataSource={balances}
          pagination={false}
          size="middle"
        />
      </PageCard>
    </div>
  )
}
