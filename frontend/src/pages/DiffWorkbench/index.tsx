import { useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Timeline,
  Typography
} from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apApi } from '../../api/ap'
import { authApi } from '../../api/auth'
import { agentAlerts, agentAlertActions, agentAssignAlert, agentScanAlerts, agentTransferAlert } from '../../api/agent'
import { PageCard } from '../../components/PageCard'
import { useAuthStore } from '../../store/useAuthStore'
import { matchStatusMeta, PENDING_DIFF_STATUSES, resolutionMeta, WAITING_STATUSES } from '../../utils/matchMeta'
import type { AgentAlertVO, AlertActionVO, ApSettlementListItem } from '../../types'

const DIFF_TYPE_META: Record<string, string> = {
  QTY: '数量差',
  PRICE: '价格差',
  TAX: '税额差',
  EXCHANGE: '汇差',
  AMOUNT: '金额差'
}

const ALERT_ACTION_LABEL: Record<string, string> = {
  CREATE: '生成预警',
  READ: '标记已读',
  ASSIGN: '指派',
  TRANSFER: '转办',
  HANDLE: '处理完成',
  IGNORE: '忽略',
  RESTORE: '恢复',
  ESCALATE: '超时升级'
}

/** 异步动作的下一步指引：登记只代表"已跟进"，差异要等对应单据变更后重新匹配才消失 */
const RESOLUTION_NEXT_STEP: Record<string, string> = {
  ADD_RECEIPT: '登记后请到「收货入库」补录收货单，收货确认后系统自动重新匹配',
  REDO_INVOICE: '登记后请到「采购发票」重开发票并核对，系统自动重新匹配',
  ADJUST_PRICE: '登记后请到「采购订单」调整单价并保存，再点下方「重新匹配」'
}

const money = (value: number) =>
  `¥${Number(value ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

/**
 * 差异处理工作台
 * - 复用预警中心「指派 / 转办 / 处理 / 忽略 + 审计时间线」交互
 * - 处理动作：补收货 / 退票重开 / 调价 / 合理损耗放行 / 领导审批强制匹配
 * - 强制匹配需二次审批并留痕（谁、何时、原因、原值→新值）
 */
export function DiffWorkbenchPage() {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const realName = useAuthStore((state) => state.realName)
  const userId = useAuthStore((state) => state.userId)
  const [statusFilter, setStatusFilter] = useState<string | undefined>()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [current, setCurrent] = useState<ApSettlementListItem | null>(null)
  const [assigneeId, setAssigneeId] = useState<number | null>(null)
  const { data: users = [] } = useQuery({
    queryKey: ['users'],
    queryFn: () => authApi.users()
  })
  const assigneeName = users.find((u) => u.id === assigneeId)?.realName ?? ''
  const [resolveRemark, setResolveRemark] = useState('')
  const [forceOpen, setForceOpen] = useState(false)
  const [forceForm] = Form.useForm()
  // 关联的三单匹配差异预警（指派/转办/审计时间线都落到真实预警流水上）
  const [relatedAlert, setRelatedAlert] = useState<AgentAlertVO | null>(null)
  const [alertActions, setAlertActions] = useState<AlertActionVO[]>([])

  const { data: settlementPage, isFetching } = useQuery({
    queryKey: ['ap', 'diff-workbench', statusFilter],
    queryFn: () =>
      apApi.settlements({
        matchStatus: statusFilter ?? [...PENDING_DIFF_STATUSES, ...WAITING_STATUSES].join(','),
        pageNo: 1,
        pageSize: 100
      })
  })
  // 待处理：无人跟进的差异（HOLD/PARTIAL_*）；处理中：已登记处理动作，等待新收货/新发票/调价
  const allList = settlementPage?.records ?? []
  const pendingList = allList.filter(
    (s) => (PENDING_DIFF_STATUSES as readonly string[]).includes(s.matchStatus)
  )
  const waitingList = allList.filter(
    (s) => (WAITING_STATUSES as readonly string[]).includes(s.matchStatus)
  )

  const resolveMutation = useMutation({
    mutationFn: ({
      settlementId,
      resolution,
      remark
    }: {
      settlementId: number
      resolution: string
      remark?: string
    }) => apApi.resolveDiff(settlementId, { resolution, remark }),
    onSuccess: () => {
      message.success('差异处理已完成，已转入「处理中」分组，相关预警将自动关闭')
      queryClient.invalidateQueries({ queryKey: ['ap'] })
      // 登记后立刻触发一次预警扫描：自动关闭已转入等待态的失效差异预警，避免继续刷存在感
      agentScanAlerts().catch(() => {})
      setDrawerOpen(false)
      setResolveRemark('')
    },
    onError: (error: Error) => message.error(error.message)
  })

  const forceMutation = useMutation({
    mutationFn: ({
      settlementId,
      reason,
      resolution
    }: {
      settlementId: number
      reason: string
      resolution: string
    }) => apApi.forceSettle(settlementId, { reason, resolution }),
    onSuccess: () => {
      message.success('强制匹配申请已提交，等待审批')
      queryClient.invalidateQueries({ queryKey: ['ap'] })
      setForceOpen(false)
      forceForm.resetFields()
    },
    onError: (error: Error) => message.error(error.message)
  })

  const openDrawer = async (record: ApSettlementListItem) => {
    setCurrent(record)
    setDrawerOpen(true)
    setResolveRemark('')
    // 指派/转办默认当前登录人，可手动改为他人
    setAssigneeId(userId ?? null)
    setRelatedAlert(null)
    setAlertActions([])
    // 关联该结算单的三单匹配差异预警，指派/转办写入真实预警流水
    try {
      const alerts = await agentAlerts(false, false)
      const alert = alerts.find(
        (a) => a.refId === record.id && a.ruleCode === 'INVOICE_MISMATCH'
      )
      if (alert) {
        setRelatedAlert(alert)
        setAlertActions(await agentAlertActions(alert.alertId))
      }
    } catch (e) {
      console.error('获取关联预警失败：', e)
    }
  }

  const runAlertAction = async (fn: () => Promise<void>, successText: string) => {
    if (!relatedAlert) return
    try {
      await fn()
      message.success(successText)
      setAlertActions(await agentAlertActions(relatedAlert.alertId))
      queryClient.invalidateQueries({ queryKey: ['ap'] })
    } catch (e) {
      message.error(e instanceof Error ? e.message : '操作失败')
    }
  }

  const handleResolve = (resolution: string) => {
    if (!current) return
    // 处理意见必填：异步动作的挂起原因与审计时间线都依赖它，空值没有追溯价值
    if (!resolveRemark.trim()) {
      message.warning('请先填写处理意见（差异原因与处理依据），再选择处理动作')
      return
    }
    resolveMutation.mutate({
      settlementId: current.id,
      resolution,
      remark: resolveRemark.trim()
    })
  }

  /** 重新匹配：调价/手动修正后幂等重算，等待态会被自动推回正常流程 */
  const reMatchMutation = useMutation({
    mutationFn: () => apApi.match(current!.orderId),
    onSuccess: () => {
      message.success('已重新匹配，状态按最新单据刷新')
      queryClient.invalidateQueries({ queryKey: ['ap'] })
      setDrawerOpen(false)
    },
    onError: (error: Error) => message.error(error.message)
  })

  const handleForceMatch = () => {
    if (!current) return
    forceForm.validateFields().then((values) => {
      forceMutation.mutate({
        settlementId: current.id,
        reason: values.reason,
        resolution: 'FORCE_MATCH'
      })
    })
  }

  const columns: ColumnsType<ApSettlementListItem> = [
    {
      title: '结算单号',
      dataIndex: 'matchNo',
      width: 160,
      render: (value: string, row) => (
        <Button type="link" size="small" onClick={() => openDrawer(row)}>
          {value}
        </Button>
      )
    },
    { title: '供应商', dataIndex: 'supplierName', width: 140 },
    { title: '订单号', dataIndex: 'orderNo', width: 140 },
    {
      title: '状态',
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
        return resolution ? <Tag color={meta.color}>{meta.label}</Tag> : <span className="text-ink-muted">未登记</span>
      }
    },
    {
      title: '差异类型',
      dataIndex: 'diffType',
      width: 100,
      render: (type?: string) => (type ? DIFF_TYPE_META[type] ?? type : '-')
    },
    {
      title: '差异金额',
      dataIndex: 'diffAmount',
      width: 130,
      align: 'right',
      render: (v: number) => <span className="text-red-500 font-medium">{money(v)}</span>
    },
    { title: '挂起原因', dataIndex: 'holdReason', width: 200, ellipsis: true },
    {
      title: '操作',
      width: 100,
      align: 'center',
      render: (_v, row) => (
        <Button type="link" size="small" onClick={() => openDrawer(row)}>
          处理
        </Button>
      )
    }
  ]

  return (
    <div className="flex flex-col gap-5">
      <PageCard>
        <div className="text-xs text-ink-muted mb-4">
          超容差或异常的三单匹配差异会在此工作台挂起处理，分两组看：<b>待处理差异</b>（无人跟进，超容差自动生成预警）和
          <b>处理中</b>（已登记补收货 / 退票重开 / 调价，等待新单据）。
          异步动作登记只代表“已有人在跟进”，差异要等新收货确认、新发票核对或调价后<b>重新匹配</b>才会消失，期间不重复预警、也不能付款；
          合理损耗放行与领导审批强制匹配可直接放行（后者需 DIRECTOR 审批）。全部操作写入审计时间线。
        </div>
        <div className="flex flex-wrap items-end gap-4">
          <div className="flex flex-col gap-1">
            <span className="text-xs text-ink-muted">匹配状态</span>
            <Select
              placeholder="待处理 + 处理中"
              allowClear
              value={statusFilter}
              onChange={setStatusFilter}
              options={[
                { value: 'HOLD', label: '挂起待处理' },
                { value: 'PARTIAL_QTY', label: '数量差异' },
                { value: 'PARTIAL_AMOUNT', label: '金额差异' },
                { value: 'WAITING_RECEIPT', label: '等待收货（已登记补收货）' },
                { value: 'WAITING_INVOICE', label: '等待新票（已登记退票重开）' },
                { value: 'WAITING_ORDER', label: '等待调价（已登记调价）' }
              ]}
              style={{ width: 220 }}
            />
          </div>
          <Button
            icon={<ReloadOutlined />}
            onClick={() => queryClient.invalidateQueries({ queryKey: ['ap'] })}
          >
            刷新
          </Button>
        </div>
      </PageCard>

      <PageCard
        title="待处理差异"
        extra={<span className="text-xs text-ink-muted">无人跟进或需重新决策，超容差会自动生成预警</span>}
      >
        <Table<ApSettlementListItem>
          rowKey="id"
          columns={columns}
          dataSource={pendingList}
          loading={isFetching}
          size="middle"
          pagination={false}
          scroll={{ x: 1250 }}
        />
      </PageCard>

      <PageCard
        title="处理中（已登记，等待新单据）"
        extra={<span className="text-xs text-ink-muted">新收货/新发票/调价触发重新匹配后自动回到正常流程，期间不重复预警</span>}
      >
        <Table<ApSettlementListItem>
          rowKey="id"
          columns={columns}
          dataSource={waitingList}
          loading={isFetching}
          size="middle"
          pagination={false}
          scroll={{ x: 1250 }}
          locale={{ emptyText: '暂无处理中的差异' }}
        />
      </PageCard>

      {/* 差异处理抽屉：复用预警中心交互范式 */}
      <Drawer
        title="差异处理详情"
        width={640}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        destroyOnClose
      >
        {current && (
          <div className="flex flex-col gap-5">
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="结算单号">{current.matchNo}</Descriptions.Item>
              <Descriptions.Item label="订单号">{current.orderNo}</Descriptions.Item>
              <Descriptions.Item label="供应商">{current.supplierName}</Descriptions.Item>
              <Descriptions.Item label="匹配类型">{current.matchType}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={matchStatusMeta(current.matchStatus).color}>
                  {matchStatusMeta(current.matchStatus).label}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="差异类型">
                {current.diffType ? DIFF_TYPE_META[current.diffType] ?? current.diffType : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="差异金额">
                <span className="text-red-500 font-medium">{money(current.diffAmount)}</span>
              </Descriptions.Item>
              <Descriptions.Item label="匹配规则">{current.matchRuleCode || '-'}</Descriptions.Item>
              <Descriptions.Item label="挂起原因" span={2}>
                <span className="whitespace-pre-wrap">{current.holdReason || '-'}</span>
              </Descriptions.Item>
            </Descriptions>

            <div className="flex flex-col gap-2">
              <span className="text-xs text-ink-muted">处理意见（必填，将写入审计记录）</span>
              <Input.TextArea
                rows={2}
                value={resolveRemark}
                onChange={(e) => setResolveRemark(e.target.value)}
                placeholder="填写差异原因说明与处理依据，如：供应商单价上浮 5%，已通知按合同价重开"
              />
            </div>

            {(WAITING_STATUSES as readonly string[]).includes(current.matchStatus) && (
              <div className="rounded-lg border border-amber-200 bg-amber-50/70 px-3 py-2 text-xs leading-relaxed text-amber-800">
                当前状态为<b>处理中</b>：已登记「{resolutionMeta(current.resolution).label}」。
                差异要等对应单据变更后重新匹配才会消失，期间不重复预警、也不能付款。
                {current.resolution && RESOLUTION_NEXT_STEP[current.resolution] && (
                  <>
                    <br />
                    <b>下一步：</b>
                    {RESOLUTION_NEXT_STEP[current.resolution]}
                  </>
                )}
              </div>
            )}

            <div className="flex flex-col gap-2">
              <span className="text-xs text-ink-muted">
                指派 / 转办给
                {!relatedAlert && '（暂无关联差异预警，请先在预警中心扫描生成）'}
              </span>
              <Space>
                <Select
                  showSearch
                  value={assigneeId ?? undefined}
                  onChange={(v) => setAssigneeId(v)}
                  placeholder="选择责任人（系统用户）"
                  style={{ width: 240 }}
                  optionFilterProp="label"
                  options={users.map((u) => ({
                    value: u.id,
                    label: `${u.realName} (${u.username})`
                  }))}
                />
                <Button
                  disabled={!relatedAlert || !assigneeId}
                  onClick={() =>
                    runAlertAction(
                      () => agentAssignAlert(relatedAlert!.alertId, assigneeId!, assigneeName, resolveRemark || undefined),
                      '已指派'
                    )
                  }
                >
                  指派
                </Button>
                <Button
                  disabled={!relatedAlert || !assigneeId}
                  onClick={() =>
                    runAlertAction(
                      () => agentTransferAlert(relatedAlert!.alertId, assigneeId!, assigneeName, resolveRemark || undefined),
                      '已转办'
                    )
                  }
                >
                  转办
                </Button>
              </Space>
            </div>

            <div className="flex flex-col gap-2">
              <span className="text-xs text-ink-muted">
                处理动作（异步动作只登记跟进，差异需等新单据重新匹配后消失）
              </span>
              <Space wrap>
                <Popconfirm
                  title={`确认按「补收货」处理？${RESOLUTION_NEXT_STEP.ADD_RECEIPT}`}
                  okText="确认"
                  cancelText="取消"
                  onConfirm={() => handleResolve('ADD_RECEIPT')}
                >
                  <Button>补收货</Button>
                </Popconfirm>
                <Popconfirm
                  title={`确认按「退票重开」处理？${RESOLUTION_NEXT_STEP.REDO_INVOICE}`}
                  okText="确认"
                  cancelText="取消"
                  onConfirm={() => handleResolve('REDO_INVOICE')}
                >
                  <Button>退票重开</Button>
                </Popconfirm>
                <Popconfirm
                  title={`确认按「调价」处理？${RESOLUTION_NEXT_STEP.ADJUST_PRICE}`}
                  okText="确认"
                  cancelText="取消"
                  onConfirm={() => handleResolve('ADJUST_PRICE')}
                >
                  <Button>调价</Button>
                </Popconfirm>
                <Popconfirm
                  title="确认按「合理损耗放行」处理？将按差异金额进损耗"
                  okText="确认"
                  cancelText="取消"
                  onConfirm={() => handleResolve('ALLOW_LOSS')}
                >
                  <Button>合理损耗放行</Button>
                </Popconfirm>
                <Button type="primary" danger onClick={() => setForceOpen(true)}>
                  领导审批强制匹配
                </Button>
              </Space>
              <div className="flex flex-col gap-1">
                <span className="text-xs text-ink-muted">
                  单据已更正（补录收货 / 重开发票 / 调整单价）后，点此按最新单据幂等重算：
                </span>
                <Button
                  loading={reMatchMutation.isPending}
                  onClick={() => reMatchMutation.mutate()}
                  style={{ width: 160 }}
                >
                  重新匹配
                </Button>
              </div>
            </div>

            <div className="flex flex-col gap-3 mt-2">
              <span className="text-sm font-medium">审计时间线</span>
              {alertActions.length === 0 && !current.resolution && !current.matchedTime ? (
                <Empty description="暂无处理记录" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              ) : (
                <Timeline
                  items={[
                    {
                      children: (
                        <div className="flex flex-col gap-0.5">
                          <span className="text-sm">
                            <b>生成结算单</b>
                            {current.matchedByName ? ` · ${current.matchedByName}` : ''}
                          </span>
                          {current.matchedTime && (
                            <span className="text-xs text-ink-muted/60">
                              {current.matchedTime.replace('T', ' ')}
                            </span>
                          )}
                        </div>
                      )
                    },
                    ...(current.resolution
                      ? [
                          {
                            children: (
                              <div className="flex flex-col gap-0.5">
                                <span className="text-sm">
                                  <b>登记处理：{resolutionMeta(current.resolution).label}</b>
                                  {current.matchedByName ? ` · 操作人：${current.matchedByName}` : ''}
                                </span>
                                {(WAITING_STATUSES as readonly string[]).includes(current.matchStatus) && (
                                  <span className="text-xs text-amber-700">
                                    等待单据变更后重新匹配，期间不重复预警、不能付款
                                  </span>
                                )}
                                {current.matchedTime && (
                                  <span className="text-xs text-ink-muted/60">
                                    {current.matchedTime.replace('T', ' ')}
                                  </span>
                                )}
                              </div>
                            )
                          }
                        ]
                      : []),
                    ...alertActions.map((a) => ({
                      children: (
                        <div className="flex flex-col gap-0.5">
                          <span className="text-sm">
                            <b>{ALERT_ACTION_LABEL[a.action] ?? a.action}</b>
                            {a.operator ? ` · ${a.operator}` : ''}
                            {a.toUser ? ` → ${a.toUser}` : ''}
                          </span>
                          {a.note && <span className="text-xs text-ink-muted">{a.note}</span>}
                          <span className="text-xs text-ink-muted/60">
                            {a.createTime?.replace('T', ' ')}
                          </span>
                        </div>
                      )
                    }))
                  ]}
                />
              )}
            </div>
          </div>
        )}
      </Drawer>

      {/* 强制匹配表单：二次审批 + 留痕 */}
      <Modal
        title="强制匹配申请"
        open={forceOpen}
        onCancel={() => {
          setForceOpen(false)
          forceForm.resetFields()
        }}
        onOk={handleForceMatch}
        confirmLoading={forceMutation.isPending}
        width={560}
      >
        <Typography.Paragraph type="secondary" className="!mb-4">
          强制匹配将跳过容差校验直接置为 MATCHED，必须填写原因说明并提交 DIRECTOR 审批。
          操作人与审批意见将完整留痕（谁、何时、原因、原值→新值）。
        </Typography.Paragraph>
        <Form form={forceForm} layout="vertical">
          <Form.Item
            name="reason"
            label="原因说明"
            rules={[{ required: true, message: '请填写强制匹配原因' }]}
          >
            <Input.TextArea rows={4} placeholder="例如：供应商让价 3%，经采购经理与财务确认，差异由公司承担" />
          </Form.Item>
          <Descriptions column={1} size="small" bordered>
            <Descriptions.Item label="结算单号">{current?.matchNo}</Descriptions.Item>
            <Descriptions.Item label="差异金额">
              <span className="text-red-500 font-medium">{money(current?.diffAmount ?? 0)}</span>
            </Descriptions.Item>
            <Descriptions.Item label="原状态">
              {current ? matchStatusMeta(current.matchStatus).label : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="目标状态">
              <Tag color="green">已匹配（MATCHED）</Tag>
            </Descriptions.Item>
          </Descriptions>
        </Form>
      </Modal>
    </div>
  )
}
