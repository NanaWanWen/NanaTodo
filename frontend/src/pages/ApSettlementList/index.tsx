import { useState, type Key } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntdApp, Button, Card, Col, Drawer, Input, Modal, Row, Select, Space, Statistic, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apApi } from '../../api/ap'
import { ManualSettleModal } from '../../components/ManualSettleModal'
import { PageCard } from '../../components/PageCard'
import { matchStatusMeta, matchTypeMeta, resolutionMeta } from '../../utils/matchMeta'
import type { ApSettlementListItem, SupplierBalanceVO } from '../../types'

const money = (value: number) =>
  `¥${Number(value ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

/**
 * 应付对账：三单匹配（用友式）
 */
export function ApSettlementListPage() {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const [orderId, setOrderId] = useState<number>()
  const [matchStatus, setMatchStatus] = useState<string>()
  const [matchType, setMatchType] = useState<string>()
  const [diffType, setDiffType] = useState<string>()
  const [supplierId, setSupplierId] = useState<number>()
  const [keyword, setKeyword] = useState<string>()
  const [selectedRowKeys, setSelectedRowKeys] = useState<number[]>([])
  const [drawerVisible, setDrawerVisible] = useState(false)
  const [selectedSettlement, setSelectedSettlement] = useState<ApSettlementListItem | null>(null)
  const [manualSettleOpen, setManualSettleOpen] = useState(false)
  const [manualSettleRow, setManualSettleRow] = useState<ApSettlementListItem | null>(null)

  const { data: settlementPage, isFetching: stLoading } = useQuery({
    queryKey: ['ap', 'settlements', orderId, matchStatus, matchType, diffType, supplierId, keyword],
    queryFn: () => apApi.settlements({ orderId, matchStatus, matchType, diffType, supplierId, keyword })
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

  const autoSettleMutation = useMutation({
    mutationFn: (orderIds: number[]) => apApi.autoSettle(orderIds),
    onSuccess: () => {
      message.success('批量自动结算已完成')
      queryClient.invalidateQueries({ queryKey: ['ap'] })
      setSelectedRowKeys([])
    },
    onError: (error: Error) => message.error(error.message)
  })

  const columns: ColumnsType<ApSettlementListItem> = [
    {
      title: '结算单号',
      dataIndex: 'matchNo',
      width: 160,
      render: (value: string, row) => (
        <Button type="link" size="small" onClick={() => {
          setSelectedSettlement(row)
          setDrawerVisible(true)
        }}>
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
    {
      title: '供应商',
      dataIndex: 'supplierName',
      width: 140
    },
    {
      title: '匹配类型',
      dataIndex: 'matchType',
      width: 100,
      render: (type: ApSettlementListItem['matchType']) => {
        const meta = matchTypeMeta(type)
        return <Tag color={meta.color}>{meta.label}</Tag>
      }
    },
    {
      title: '匹配状态',
      dataIndex: 'matchStatus',
      width: 120,
      render: (status: ApSettlementListItem['matchStatus']) => {
        const meta = matchStatusMeta(status)
        return <Tag color={meta.color}>{meta.label}</Tag>
      }
    },
    {
      title: '处理动作',
      dataIndex: 'resolution',
      width: 110,
      render: (resolution?: string) => {
        const meta = resolutionMeta(resolution)
        return resolution ? <Tag color={meta.color}>{meta.label}</Tag> : <span className="text-ink-muted">-</span>
      }
    },
    {
      title: '差异类型',
      dataIndex: 'diffType',
      width: 100,
      render: (type?: string) => type || '-'
    },
    {
      title: '差异金额',
      dataIndex: 'diffAmount',
      width: 130,
      align: 'right',
      render: (v: number) => (
        <span className={v > 0 ? 'text-red-500 font-medium' : ''}>{money(v)}</span>
      )
    },
    {
      title: '匹配人',
      dataIndex: 'matchedByName',
      width: 100,
      render: (v?: string) => v || '-'
    },
    {
      title: '匹配时间',
      dataIndex: 'matchedTime',
      width: 160,
      render: (v?: string) => v ? new Date(v).toLocaleString('zh-CN') : '-'
    },
    {
      title: '操作',
      width: 120,
      align: 'center',
      render: (_v, row) => (
        <Space>
          <Button
            type="link"
            size="small"
            onClick={() => {
              setSelectedSettlement(row)
              setDrawerVisible(true)
            }}
          >
            详情
          </Button>
          {row.matchStatus !== 'MATCHED' && row.matchStatus !== 'CLOSED' && (
            <>
              <Button
                type="link"
                size="small"
                onClick={() => {
                  setManualSettleRow(row)
                  setManualSettleOpen(true)
                }}
              >
                手工结算
              </Button>
              <Button
                type="link"
                size="small"
                loading={matchMutation.isPending}
                onClick={() => matchMutation.mutate(row.orderId)}
              >
                {row.matchStatus === 'UNMATCHED' ? '触发匹配' : '重新匹配'}
              </Button>
            </>
          )}
        </Space>
      )
    }
  ]

  const rowSelection = {
    selectedRowKeys,
    onChange: (keys: Key[]) => setSelectedRowKeys(keys as number[])
  }

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
                { value: 'PARTIAL_QTY', label: '数量差异' },
                { value: 'PARTIAL_AMOUNT', label: '金额差异' },
                { value: 'HOLD', label: '挂起待处理' },
                { value: 'WAITING_RECEIPT', label: '等待收货' },
                { value: 'WAITING_INVOICE', label: '等待新票' },
                { value: 'WAITING_ORDER', label: '等待调价' },
                { value: 'UNMATCHED', label: '未匹配' },
                { value: 'CLOSED', label: '已关闭' },
                { value: 'REVERSED', label: '已冲销' }
              ]}
              style={{ width: 140 }}
            />
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-xs text-ink-muted">匹配类型</span>
            <Select
              placeholder="全部"
              allowClear
              value={matchType}
              onChange={setMatchType}
              options={[
                { value: 'AUTO', label: '自动' },
                { value: 'MANUAL', label: '手工' },
                { value: 'FORCE', label: '强制' }
              ]}
              style={{ width: 120 }}
            />
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-xs text-ink-muted">差异类型</span>
            <Select
              placeholder="全部"
              allowClear
              value={diffType}
              onChange={setDiffType}
              options={[
                { value: 'QTY', label: '数量差' },
                { value: 'PRICE', label: '价格差' },
                { value: 'TAX', label: '税额差' },
                { value: 'EXCHANGE', label: '汇差' },
                { value: 'AMOUNT', label: '金额差' }
              ]}
              style={{ width: 120 }}
            />
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-xs text-ink-muted">关键字</span>
            <Input
              placeholder="结算单号"
              allowClear
              value={keyword}
              onChange={(e) => setKeyword(e.target.value || undefined)}
              style={{ width: 160 }}
            />
          </div>
          <Button type="primary" onClick={() => queryClient.invalidateQueries({ queryKey: ['ap'] })}>
            查询
          </Button>
        </div>
      </PageCard>

      <Row gutter={16}>
        <Col span={6}>
          <Card>
            <Statistic title="三单匹配记录数" value={settlements.length} suffix="条" />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="已匹配"
              value={settlements.filter(s => s.matchStatus === 'MATCHED').length}
              suffix="条"
              valueStyle={{ color: '#52C41A' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="挂起待处理"
              value={settlements.filter(s => s.matchStatus === 'HOLD').length}
              suffix="条"
              valueStyle={{ color: '#FF4D4F' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="供应商数量"
              value={balances.length}
              suffix="家"
              valueStyle={{ color: '#1677FF' }}
            />
          </Card>
        </Col>
      </Row>

      <PageCard title="三单匹配（订单 · 收货 · 发票）">
        <div className="mb-4">
          <Space>
            <Button
              type="primary"
              disabled={selectedRowKeys.length === 0}
              loading={autoSettleMutation.isPending}
              onClick={() => {
                const orderIds = settlements
                  .filter(s => selectedRowKeys.includes(s.id))
                  .map(s => s.orderId)
                autoSettleMutation.mutate(orderIds)
              }}
            >
              批量自动结算 {selectedRowKeys.length > 0 && `(${selectedRowKeys.length})`}
            </Button>
          </Space>
        </div>
        <Table<ApSettlementListItem>
          rowKey="id"
          columns={columns}
          dataSource={settlements}
          loading={stLoading}
          size="middle"
          pagination={false}
          scroll={{ x: 1400 }}
          rowSelection={rowSelection}
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

      <Drawer
        title="结算单详情"
        placement="right"
        width={800}
        open={drawerVisible}
        onClose={() => setDrawerVisible(false)}
      >
        {selectedSettlement && (
          <div className="flex flex-col gap-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <div className="text-xs text-ink-muted mb-1">结算单号</div>
                <div className="font-medium">{selectedSettlement.matchNo}</div>
              </div>
              <div>
                <div className="text-xs text-ink-muted mb-1">订单号</div>
                <div className="font-medium">{selectedSettlement.orderNo}</div>
              </div>
              <div>
                <div className="text-xs text-ink-muted mb-1">供应商</div>
                <div>{selectedSettlement.supplierName}</div>
              </div>
              <div>
                <div className="text-xs text-ink-muted mb-1">匹配类型</div>
                <Tag color={matchTypeMeta(selectedSettlement.matchType).color}>
                  {matchTypeMeta(selectedSettlement.matchType).label}
                </Tag>
              </div>
              <div>
                <div className="text-xs text-ink-muted mb-1">匹配状态</div>
                <Tag color={matchStatusMeta(selectedSettlement.matchStatus).color}>
                  {matchStatusMeta(selectedSettlement.matchStatus).label}
                </Tag>
              </div>
              <div>
                <div className="text-xs text-ink-muted mb-1">匹配规则</div>
                <div>{selectedSettlement.matchRuleCode || '-'}</div>
              </div>
            </div>
            <div className="border-t pt-4">
              <div className="font-medium mb-3">金额信息</div>
              <div className="grid grid-cols-3 gap-4">
                <div>
                  <div className="text-xs text-ink-muted mb-1">订单金额</div>
                  <div className="font-medium">{money(selectedSettlement.orderAmount)}</div>
                </div>
                <div>
                  <div className="text-xs text-ink-muted mb-1">收货金额</div>
                  <div className="font-medium">{money(selectedSettlement.receiptAmount)}</div>
                </div>
                <div>
                  <div className="text-xs text-ink-muted mb-1">发票金额</div>
                  <div className="font-medium">{money(selectedSettlement.invoiceAmount)}</div>
                </div>
              </div>
            </div>
            <div className="border-t pt-4">
              <div className="font-medium mb-3">差异信息</div>
              <div className="grid grid-cols-3 gap-4">
                <div>
                  <div className="text-xs text-ink-muted mb-1">差异金额</div>
                  <div className={`font-medium ${selectedSettlement.diffAmount > 0 ? 'text-red-500' : ''}`}>
                    {money(selectedSettlement.diffAmount)}
                  </div>
                </div>
                <div>
                  <div className="text-xs text-ink-muted mb-1">数量差异</div>
                  <div>{selectedSettlement.qtyDiff}</div>
                </div>
                <div>
                  <div className="text-xs text-ink-muted mb-1">差异类型</div>
                  <div>{selectedSettlement.diffType || '-'}</div>
                </div>
              </div>
            </div>
            {selectedSettlement.holdReason && (
              <div className="border-t pt-4">
                <div className="font-medium mb-3">挂起原因</div>
                <div className="text-sm">{selectedSettlement.holdReason}</div>
              </div>
            )}
            {selectedSettlement.resolution && (
              <div className="border-t pt-4">
                <div className="font-medium mb-3">处理方式</div>
                <div className="text-sm">{selectedSettlement.resolution}</div>
              </div>
            )}
          </div>
        )}
      </Drawer>

      <ManualSettleModal
        open={manualSettleOpen}
        settlement={manualSettleRow}
        onClose={() => {
          setManualSettleOpen(false)
          setManualSettleRow(null)
        }}
      />
    </div>
  )
}
