import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import {
  App as AntdApp,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Input,
  Popconfirm,
  Select,
  Space,
  Switch,
  Tag,
  Timeline,
  Typography
} from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { alertRuleApi } from '../../api/alertRules'
import {
  agentAlerts,
  agentReadAlert,
  agentIgnoreAlert,
  agentRestoreAlert,
  agentAssignAlert,
  agentTransferAlert,
  agentProcessAlert,
  agentAlertActions,
  agentScanAlerts,
  agentUnreadCount
} from '../../api/agent'
import { authApi } from '../../api/auth'
import { PageCard } from '../../components/PageCard'
import type { AgentAlertVO, AlertActionVO } from '../../types'

const LEVEL_COLOR: Record<string, string> = {
  CRITICAL: 'error',
  WARNING: 'warning',
  INFO: 'default'
}

const TYPE_LABEL: Record<string, string> = {
  OVERDUE_ORDER: '逾期订单',
  LOW_STOCK: '库存预警',
  PRICE_ABNORMAL: '价格异常',
  SYSTEM: '系统预警'
}

const DOMAIN_LABEL: Record<string, { label: string; color: string }> = {
  PURCHASE: { label: '采购', color: 'blue' },
  INVENTORY: { label: '库存', color: 'purple' },
  FINANCE: { label: '财务', color: 'gold' }
}

const ACTION_LABEL: Record<string, string> = {
  CREATE: '创建预警',
  READ: '标记已读',
  ASSIGN: '指派',
  TRANSFER: '转办',
  HANDLE: '处理完成',
  IGNORE: '忽略',
  RESTORE: '恢复',
  ESCALATE: '超时升级'
}

/**
 * AI Agent 预警中心
 * - 规则配置化：业务角色自助维护阈值/级别/启停/通知对象
 * - 三通道触达：站内未读红点、邮件汇总、SSE 实时推送
 * - 闭环处理：指派/转办/处理/忽略/恢复 + 完整审计时间线
 */
export function AgentAlertsPage() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const [alerts, setAlerts] = useState<AgentAlertVO[]>([])
  const [loading, setLoading] = useState(false)
  const [showHandled, setShowHandled] = useState(false)
  const [unreadCount, setUnreadCount] = useState(0)
  const [domainFilter, setDomainFilter] = useState<string | undefined>()
  // 规则筛选：支持从「预警规则配置」页点击某规则的预警数跳转过来
  const [ruleFilter, setRuleFilter] = useState<string | undefined>(
    searchParams.get('rule') || undefined
  )

  // 预警规则（用于把预警与「制定预警」中的规则对应起来）
  const { data: rules = [] } = useQuery({
    queryKey: ['alert-rules'],
    queryFn: () => alertRuleApi.list()
  })
  const ruleNameMap = useMemo(() => {
    const map: Record<string, string> = {}
    rules.forEach((r) => {
      if (r.ruleCode) map[r.ruleCode] = r.ruleName ?? r.ruleCode
    })
    return map
  }, [rules])

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [currentAlert, setCurrentAlert] = useState<AgentAlertVO | null>(null)
  const [actions, setActions] = useState<AlertActionVO[]>([])
  const [actionNote, setActionNote] = useState('')
  const [assigneeId, setAssigneeId] = useState<number | null>(null)
  const { data: users = [] } = useQuery({
    queryKey: ['users'],
    queryFn: () => authApi.users()
  })
  const assigneeName = users.find((u) => u.id === assigneeId)?.realName ?? ''
  const [actionLoading, setActionLoading] = useState(false)

  // SSE 连接状态：connected / reconnecting / disconnected
  const [sseState, setSseState] = useState<'connected' | 'reconnecting' | 'disconnected'>('disconnected')
  const eventSourceRef = useRef<EventSource | null>(null)
  // 用 ref 持有最新的拉取函数与筛选条件，避免切换条件时重建 SSE 连接
  // 不能在此处直接引用下方用 const 声明的 fetchAlerts（暂时性死区会导致渲染崩溃），
  // 先给空实现，随后由同步 effect 赋值为真实函数
  const fetchRef = useRef<(handled: boolean) => Promise<void>>(async () => {})
  const showHandledRef = useRef(showHandled)
  const retryRef = useRef(0)
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // 搜索关键字（防抖后生效）
  const [searchText, setSearchText] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(searchText.trim().toLowerCase()), 300)
    return () => clearTimeout(timer)
  }, [searchText])

  const fetchAlerts = useCallback(async (handled: boolean) => {
    setLoading(true)
    try {
      const [list, count] = await Promise.all([agentAlerts(handled, false), agentUnreadCount()])
      // 后端已按 createTime DESC 返回，前端直接按返回顺序展示（时间倒序，最新在前）
      setAlerts(list)
      setUnreadCount(count)
    } catch {
      console.error('获取预警列表失败')
    } finally {
      setLoading(false)
    }
  }, [])

  const handleScan = async () => {
    try {
      const generated = await agentScanAlerts()
      message.success(`扫描完成，新增 ${generated} 条预警`)
      await fetchAlerts(showHandled)
    } catch {
      message.error('手动扫描失败')
    }
  }

  const handleManualRefresh = async () => {
    await fetchAlerts(showHandled)
    message.success('已刷新')
  }

  // 首次加载 + 切换筛选时拉取；SSE 负责实时更新
  useEffect(() => {
    fetchAlerts(showHandled)
  }, [fetchAlerts, showHandled])

  // 同步最新的拉取函数与筛选条件到 ref，供 SSE 回调使用（避免重建连接）
  useEffect(() => {
    fetchRef.current = fetchAlerts
    showHandledRef.current = showHandled
  }, [fetchAlerts, showHandled])

  // 建立 SSE 连接；断线时由 error 事件驱动指数退避重连，重连成功后补偿拉取
  const connectSse = useCallback((userId: number) => {
    const es = new EventSource(`/api/agent/alerts/stream?userId=${userId}`)
    eventSourceRef.current = es

    const scheduleReconnect = () => {
      if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current)
      // 指数退避：1s → 2s → 4s → … → 上限 30s
      const delay = Math.min(30_000, 1_000 * 2 ** retryRef.current)
      retryRef.current += 1
      setSseState('reconnecting')
      reconnectTimerRef.current = setTimeout(() => connectSse(userId), delay)
    }

    es.addEventListener('open', () => {
      setSseState('connected')
      retryRef.current = 0
      // 重连成功后立即补偿拉取，防止断连期间的预警被吞
      fetchRef.current(showHandledRef.current)
    })
    es.addEventListener('unread-count', (evt: MessageEvent) => {
      const n = Number((evt as MessageEvent).data)
      if (!Number.isNaN(n)) setUnreadCount(n)
    })
    es.addEventListener('alert', () => {
      fetchRef.current(showHandledRef.current)
    })
    es.addEventListener('error', () => {
      // CLOSED 表示浏览器已放弃自动重连，此时才手动重连，避免与其冲突
      if (es.readyState === EventSource.CLOSED) {
        eventSourceRef.current = null
        scheduleReconnect()
      } else {
        setSseState('reconnecting')
      }
    })

    return es
  }, [])

  // SSE 实时推送：仅在连接函数变化时建立，不因筛选条件变化而中断
  useEffect(() => {
    const { userId } = (window as unknown as { __auth?: { userId?: number } }).__auth ?? {}
    if (!userId) return
    const es = connectSse(userId)
    return () => {
      if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current)
      es.close()
      eventSourceRef.current = null
      setSseState('disconnected')
    }
  }, [connectSse])

  // 页面回到前台时立即刷新，补齐后台期间可能未及时接收的预警
  useEffect(() => {
    const onVisible = () => {
      if (document.visibilityState === 'visible') {
        fetchAlerts(showHandled)
      }
    }
    document.addEventListener('visibilitychange', onVisible)
    return () => document.removeEventListener('visibilitychange', onVisible)
  }, [fetchAlerts, showHandled])

  const openDrawer = async (alert: AgentAlertVO) => {
    setCurrentAlert(alert)
    setDrawerOpen(true)
    setActionNote('')
    setAssigneeId(alert.assigneeId ?? null)
    // 打开即标记已读
    if (!alert.readFlag) {
      try {
        await agentReadAlert(alert.alertId)
        setUnreadCount((c) => Math.max(0, c - 1))
      } catch {
        // 忽略已读上报失败
      }
    }
    try {
      const list = await agentAlertActions(alert.alertId)
      setActions(list)
    } catch {
      setActions([])
    }
  }

  const refreshDrawer = async (alertId: string) => {
    const [list, latest] = await Promise.all([
      agentAlertActions(alertId),
      agentAlerts(showHandled, false)
    ])
    setActions(list)
    setAlerts(latest)
    const fresh = latest.find((a) => a.alertId === alertId)
    if (fresh) setCurrentAlert(fresh)
  }

  const runAction = async (fn: () => Promise<void>, successText: string) => {
    if (!currentAlert) return
    setActionLoading(true)
    try {
      await fn()
      message.success(successText)
      await refreshDrawer(currentAlert.alertId)
      setUnreadCount(await agentUnreadCount())
      setActionNote('')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '操作失败')
    } finally {
      setActionLoading(false)
    }
  }

  const handleMarkProcessed = async (alertId: string) => {
    try {
      await agentProcessAlert(alertId)
      message.success('已标记为已处理')
      fetchAlerts(showHandled)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '操作失败')
    }
  }

  const handleIgnore = async (alertId: string) => {
    try {
      await agentIgnoreAlert(alertId)
      message.success('已忽略，不再显示')
      setUnreadCount(await agentUnreadCount())
      fetchAlerts(showHandled)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '操作失败')
    }
  }

  const handleRestore = async (alertId: string) => {
    try {
      await agentRestoreAlert(alertId)
      message.success('已恢复显示')
      fetchAlerts(showHandled)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '操作失败')
    }
  }

  const visibleAlerts = alerts
    .filter((a) => (domainFilter ? a.bizDomain === domainFilter : true))
    .filter((a) => (ruleFilter ? (a.ruleCode ?? a.alertType) === ruleFilter : true))
    .filter((a) => {
      if (!debouncedSearch) return true
      const text = `${a.title ?? ''} ${a.refNo ?? ''} ${a.content ?? ''}`.toLowerCase()
      return text.includes(debouncedSearch)
    })

  return (
    <div className="flex flex-col gap-5">
      <PageCard
        title="AI 预警中心"
        extra={
          <Space wrap>
            <Tag
              color={
                sseState === 'connected' ? 'success' : sseState === 'reconnecting' ? 'warning' : 'default'
              }
            >
              {sseState === 'connected'
                ? '实时推送已连接'
                : sseState === 'reconnecting'
                  ? '实时推送重连中…'
                  : 'SSE 未连接'}
            </Tag>
            <span className="text-xs text-ink-muted">未读 {unreadCount}</span>
            <span className="text-xs text-ink-muted">显示已处理</span>
            <Switch checked={showHandled} onChange={setShowHandled} size="small" />
            <Button onClick={handleScan}>立即扫描</Button>
            <Button
              icon={<ReloadOutlined />}
              onClick={handleManualRefresh}
              loading={loading}
              title="手动刷新"
            />
          </Space>
        }
      >
        <div className="text-xs text-ink-muted mb-4">
          预警覆盖 <b>采购 / 库存 / 财务</b> 三个业务域，规则与阈值可在「预警规则配置」中自助维护。
          处理闭环：<b>指派 / 转办</b> 给责任人，处理后标记 <b>已处理</b> 并保留处理意见，
          全部操作写入审计时间线。
        </div>

        <div className="flex flex-col gap-3 mb-4">
          <div className="flex flex-col gap-2">
            <span className="text-xs text-ink-muted">搜索预警（标题 / 单据编号 / 内容关键字）</span>
            <Input.Search
              placeholder="输入关键字搜索..."
              allowClear
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              onSearch={() => setDebouncedSearch(searchText.trim().toLowerCase())}
            />
          </div>
          <Space className="mt-1" wrap>
            <Tag
              color={!domainFilter ? 'processing' : 'default'}
              className="cursor-pointer"
              onClick={() => setDomainFilter(undefined)}
            >
              全部
            </Tag>
            {Object.entries(DOMAIN_LABEL).map(([key, cfg]) => (
              <Tag
                key={key}
                color={domainFilter === key ? 'processing' : cfg.color}
                className="cursor-pointer"
                onClick={() => setDomainFilter(key)}
              >
                {cfg.label}
              </Tag>
            ))}
            <Select
              allowClear
              size="small"
              placeholder="按预警规则筛选"
              style={{ width: 220 }}
              value={ruleFilter}
              onChange={(v) => {
                setRuleFilter(v)
                const next = new URLSearchParams(searchParams)
                if (v) next.set('rule', v)
                else next.delete('rule')
                setSearchParams(next, { replace: true })
              }}
              options={rules.map((r) => ({
                label: `${r.ruleName ?? r.ruleCode}${r.enabled === 1 ? '' : '（已停用）'}`,
                value: r.ruleCode ?? ''
              }))}
            />
          </Space>
        </div>

        {visibleAlerts.length === 0 && !loading && (
          <Empty description={showHandled ? '暂无已处理预警' : '当前无未处理预警'} className="py-8" />
        )}

        <div className="flex flex-col gap-4">
          {visibleAlerts.map((alert) => (
            <Card
              key={alert.alertId}
              size="small"
              className={`transition-160 hover:shadow-md ${
                alert.handled ? 'opacity-70 ' : ''
              }${
                alert.level === 'CRITICAL'
                  ? 'border-l-4 border-l-red-500'
                  : alert.level === 'WARNING'
                  ? 'border-l-4 border-l-amber-400'
                  : 'border-l-4 border-l-blue-400'
              }`}
            >
              <div className="flex items-start justify-between gap-4">
                <div className="flex flex-col gap-2 flex-1">
                  <div className="flex items-center gap-2 flex-wrap">
                    {alert.bizDomain && (
                      <Tag color={DOMAIN_LABEL[alert.bizDomain]?.color}>
                        {DOMAIN_LABEL[alert.bizDomain]?.label ?? alert.bizDomain}
                      </Tag>
                    )}
                    <Tag color={LEVEL_COLOR[alert.level]}>
                      {alert.ruleCode
                        ? ruleNameMap[alert.ruleCode] ?? alert.ruleCode
                        : TYPE_LABEL[alert.alertType] ?? alert.alertType}
                    </Tag>
                    {alert.ruleCode && <span className="text-xs text-ink-muted">{alert.ruleCode}</span>}
                    <span className="text-sm font-medium text-ink">{alert.title}</span>
                    {alert.refNo && <Tag className="text-xs">{alert.refNo}</Tag>}
                    {alert.handled && <Tag color="success">已处理</Tag>}
                    {!alert.handled && !alert.readFlag && <Tag color="red">未读</Tag>}
                    {alert.assignee && <Tag color="blue">责任人：{alert.assignee}</Tag>}
                  </div>
                  <Typography.Text className="text-xs text-ink-muted whitespace-pre-wrap">
                    {alert.content}
                  </Typography.Text>
                  {alert.actionPath && (
                    <Button
                      type="link"
                      size="small"
                      className="self-start !px-0"
                      onClick={() => navigate(alert.actionPath!)}
                    >
                      打开关联单据
                    </Button>
                  )}
                  <span className="text-xs text-ink-muted/60">
                    生成时间：{alert.createTime?.replace('T', ' ')}
                    {alert.handleTime && ` · 处理时间：${alert.handleTime.replace('T', ' ')}`}
                  </span>
                </div>
                <Space direction="vertical" size={8}>
                  <Button size="small" onClick={() => openDrawer(alert)}>
                    处理详情
                  </Button>
                  {!alert.handled && (
                    <Popconfirm
                      title="确认标记为已处理？"
                      okText="确认"
                      cancelText="取消"
                      onConfirm={() => handleMarkProcessed(alert.alertId)}
                    >
                      <Button type="primary" size="small">
                        标记已处理
                      </Button>
                    </Popconfirm>
                  )}
                  {alert.ignored ? (
                    <Button size="small" onClick={() => handleRestore(alert.alertId)}>
                      恢复
                    </Button>
                  ) : (
                    <Popconfirm
                      title="忽略后不再显示，历史记录仍会保留，确认？"
                      okText="忽略"
                      cancelText="取消"
                      onConfirm={() => handleIgnore(alert.alertId)}
                    >
                      <Button size="small" danger>
                        忽略
                      </Button>
                    </Popconfirm>
                  )}
                </Space>
              </div>
            </Card>
          ))}
        </div>
      </PageCard>

      <Drawer
        title="预警处理详情"
        width={560}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        destroyOnClose
      >
        {currentAlert && (
          <div className="flex flex-col gap-5">
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="业务域">
                {DOMAIN_LABEL[currentAlert.bizDomain ?? '']?.label ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="预警类型">
                {TYPE_LABEL[currentAlert.alertType] ?? currentAlert.alertType}
              </Descriptions.Item>
              <Descriptions.Item label="级别">
                <Tag color={LEVEL_COLOR[currentAlert.level]}>{currentAlert.level}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="关联单据">{currentAlert.refNo || '-'}</Descriptions.Item>
              <Descriptions.Item label="命中规则">{currentAlert.ruleCode || '-'}</Descriptions.Item>
              <Descriptions.Item label="当前责任人">{currentAlert.assignee || '未指派'}</Descriptions.Item>
              <Descriptions.Item label="状态">
                {currentAlert.handled ? (
                  <Tag color="success">已处理</Tag>
                ) : currentAlert.ignored ? (
                  <Tag>已忽略</Tag>
                ) : (
                  <Tag color="processing">待处理</Tag>
                )}
              </Descriptions.Item>
              <Descriptions.Item label="内容">
                <span className="whitespace-pre-wrap">{currentAlert.content}</span>
              </Descriptions.Item>
            </Descriptions>

            <div className="flex flex-col gap-2">
              <span className="text-xs text-ink-muted">处理意见</span>
              <Input.TextArea
                rows={2}
                value={actionNote}
                onChange={(e) => setActionNote(e.target.value)}
                placeholder="填写处理说明，将写入审计记录"
              />
            </div>

            <div className="flex flex-col gap-2">
              <span className="text-xs text-ink-muted">指派 / 转办给</span>
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
                  loading={actionLoading}
                  disabled={!assigneeId}
                  onClick={() =>
                    runAction(
                      () => agentAssignAlert(currentAlert.alertId, assigneeId!, assigneeName, actionNote),
                      '已指派'
                    )
                  }
                >
                  指派
                </Button>
                <Button
                  loading={actionLoading}
                  disabled={!assigneeId}
                  onClick={() =>
                    runAction(
                      () => agentTransferAlert(currentAlert.alertId, assigneeId!, assigneeName, actionNote),
                      '已转办'
                    )
                  }
                >
                  转办
                </Button>
              </Space>
            </div>

            <Space>
              {!currentAlert.handled && (
                <Button
                  type="primary"
                  loading={actionLoading}
                  onClick={() =>
                    runAction(
                      () => agentProcessAlert(currentAlert.alertId, actionNote),
                      '已标记为已处理'
                    )
                  }
                >
                  标记已处理
                </Button>
              )}
              {currentAlert.ignored ? (
                <Button
                  loading={actionLoading}
                  onClick={() =>
                    runAction(() => agentRestoreAlert(currentAlert.alertId), '已恢复显示')
                  }
                >
                  恢复显示
                </Button>
              ) : (
                <Button
                  danger
                  loading={actionLoading}
                  onClick={() => runAction(() => agentIgnoreAlert(currentAlert.alertId), '已忽略')}
                >
                  忽略
                </Button>
              )}
            </Space>

            <div className="flex flex-col gap-3 mt-2">
              <span className="text-sm font-medium">审计时间线</span>
              {actions.length === 0 ? (
                <Empty description="暂无处理记录" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              ) : (
                <Timeline
                  items={actions.map((a) => ({
                    children: (
                      <div className="flex flex-col gap-0.5">
                        <span className="text-sm">
                          <b>{ACTION_LABEL[a.action] ?? a.action}</b>
                          {a.operator && ` · ${a.operator}`}
                          {a.toUser && ` → ${a.toUser}`}
                        </span>
                        {a.note && <span className="text-xs text-ink-muted">{a.note}</span>}
                        <span className="text-xs text-ink-muted/60">
                          {a.createTime?.replace('T', ' ')}
                        </span>
                      </div>
                    )
                  }))}
                />
              )}
            </div>
          </div>
        )}
      </Drawer>
    </div>
  )
}
