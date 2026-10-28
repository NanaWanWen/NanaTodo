import { useMemo, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { Empty, Progress, Spin, Tabs, Tag } from 'antd'
import {
  AuditOutlined,
  BellOutlined,
  BarChartOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
  DatabaseOutlined,
  FileAddOutlined,
  FileSearchOutlined,
  RobotOutlined,
  SafetyCertificateOutlined,
  SyncOutlined
} from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from 'recharts'
import dayjs from 'dayjs'
import { agentAlerts } from '../../api/agent'
import { reportApi } from '../../api/reports'
import { useAuthStore } from '../../store/useAuthStore'
import type { AgentAlertVO, RankStat, ReportSummary, StatusStat, TimeStat } from '../../types'

interface KpiCard {
  key: string
  label: string
  value: string
  hint: string
  icon: ReactNode
  tone: 'blue' | 'green' | 'orange' | 'violet'
  progress?: number
}

export function WorkbenchPage() {
  const navigate = useNavigate()
  const realName = useAuthStore((state) => state.realName)
  const currentRole = useAuthStore((state) => state.currentRole)

  const range = useMemo(() => {
    const start = dayjs().subtract(6, 'month').format('YYYY-MM-DD')
    const end = dayjs().format('YYYY-MM-DD')
    return { start, end }
  }, [])

  const { data: summary, isLoading: summaryLoading } = useQuery({
    queryKey: ['workbench', 'summary', range.start, range.end],
    queryFn: () => reportApi.summary(range.start, range.end),
    select: (data) => data as ReportSummary | undefined
  })

  const { data: statusStats, isLoading: statusLoading } = useQuery({
    queryKey: ['workbench', 'status', range.start, range.end],
    queryFn: () => reportApi.byStatus(range.start, range.end),
    select: (data) => data as StatusStat[] | undefined
  })

  const { data: timeStat, isLoading: timeLoading } = useQuery({
    queryKey: ['workbench', 'time', range.start, range.end],
    queryFn: () => reportApi.byTime('month', range.start, range.end),
    select: (data) => data as TimeStat | undefined
  })

  const { data: rank, isLoading: rankLoading } = useQuery({
    queryKey: ['workbench', 'rank', range.start, range.end],
    queryFn: () => reportApi.rank({ groupBy: 'supplier', startDate: range.start, endDate: range.end, topN: 8 }),
    select: (data) => data as { rows: RankStat[] } | undefined
  })

  const { data: alerts = [] as AgentAlertVO[], isLoading: alertsLoading } = useQuery({
    queryKey: ['workbench', 'alerts'],
    queryFn: () => agentAlerts(false, false)
  })

  const kpis = useMemo<KpiCard[]>(() => {
    const total = summary?.totalCount ?? 0
    const approved = summary?.approvedCount ?? 0
    const completion = total ? Math.round((approved / total) * 100) : 0
    return [
      {
        key: 'completion',
        label: '采购完成率',
        value: `${completion}%`,
        hint: `已审批 ${approved} / 全部 ${total} 单`,
        icon: <CheckCircleOutlined />,
        tone: 'green',
        progress: completion
      },
      {
        key: 'amount',
        label: '采购金额',
        value: `¥${Number(summary?.totalAmount ?? 0).toLocaleString('zh-CN')}`,
        hint: '近半年全部口径',
        icon: <BarChartOutlined />,
        tone: 'blue'
      },
      {
        key: 'abnormal',
        label: '异常订单',
        value: String((summary?.rejectedCount ?? 0) + (summary?.canceledCount ?? 0)),
        hint: '已驳回 + 已取消',
        icon: <ClockCircleOutlined />,
        tone: 'orange'
      },
      {
        key: 'replenish',
        label: '建议补货',
        value: String(alerts.filter((item) => item.alertType === 'LOW_STOCK').length),
        hint: '来自库存预警',
        icon: <SafetyCertificateOutlined />,
        tone: 'violet'
      }
    ]
  }, [alerts, summary])

  const statusRows = useMemo(() => {
    const map = new Map((statusStats ?? []).map((item) => [item.status, item]))
    const total = (statusStats ?? []).reduce((acc, item) => acc + Number(item.count || 0), 0)
    const toRow = (code: StatusStat['status'], label: string) => {
      const item = map.get(code)
      const count = item?.count ?? 0
      const rate = total ? Math.round((Number(count) / total) * 100) : 0
      return { label, count: Number(count || 0), rate, color: item?.color || '#1677FF' }
    }
    return [
      toRow('APPROVING', '审批中'),
      toRow('APPROVED', '已审批'),
      toRow('REJECTED', '已驳回'),
      toRow('CANCELED', '已取消'),
      toRow('DRAFT', '草稿')
    ]
  }, [statusStats])

  const chartData = useMemo(() => {
    const points = timeStat?.points ?? []
    return points.map((point) => ({
      ...point,
      label: point.period.slice(2)
    }))
  }, [timeStat])

  const rankRows = rank?.rows ?? []
  const loading = summaryLoading || statusLoading || timeLoading || rankLoading || alertsLoading
  const latestAlert = alerts[0]

  return (
    <div className="workspace bip-workbench">
      <div className="workspace-hero">
        <div>
          <h2>采购工作台</h2>
          <p>你好，{realName || '采购员'}。这里汇总了近半年的订单、状态、供应商与 AI 预警。</p>
        </div>
        <div className="workspace-hero-actions">
          <Tag icon={<RobotOutlined />} className="chip-soft chip-blue">AI 可问数</Tag>
          <Tag icon={<BellOutlined />} className="chip-soft chip-orange">预警 {alerts.length}</Tag>
          <Tag icon={<DatabaseOutlined />} className="chip-soft chip-green">近半年数据</Tag>
        </div>
      </div>

      <div className="workspace-tabs">
        <Tabs
          activeKey="overview"
          items={[
            { key: 'overview', label: '经营总览' },
            { key: 'purchase', label: '采购协同' },
            { key: 'supplier', label: '供应商画像' },
            { key: 'inventory', label: '库存中心' },
            { key: 'ai', label: '智能分析' }
          ]}
          onChange={(key) => {
            if (key === 'purchase') navigate('/orders')
            if (key === 'supplier') navigate('/reports')
            if (key === 'inventory') navigate('/inventory')
            if (key === 'ai') navigate('/agent-chat')
          }}
        />
      </div>

      <div className="workspace-kpis">
        {kpis.map((item) => (
          <section key={item.key} className={`bip-card kpi-card kpi-${item.tone}`}>
            <div className="kpi-top">
              <span className="kpi-label">{item.label}</span>
              <span className="kpi-icon">{item.icon}</span>
            </div>
            <div className="kpi-value">{item.value}</div>
            <div className="kpi-hint">{item.hint}</div>
            {item.progress !== undefined && <Progress percent={item.progress} showInfo={false} />}
          </section>
        ))}
      </div>

      <div className="workspace-main">
        <section className="bip-card panel-card">
          <div className="panel-header">
            <div>
              <h3>采购金额趋势</h3>
              <p>近半年按月汇总，适合观察采购节奏</p>
            </div>
            <span className="panel-extra">{dayjs(range.start).format('YYYY-MM')} ~ {dayjs(range.end).format('YYYY-MM')}</span>
          </div>
          {chartData.length ? (
            <ResponsiveContainer width="100%" height={330}>
              <AreaChart data={chartData} margin={{ top: 12, right: 18, left: 12, bottom: 0 }}>
                <defs>
                  <linearGradient id="bipAmount" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#1677FF" stopOpacity={0.35} />
                    <stop offset="100%" stopColor="#1677FF" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#E8EEFB" />
                <XAxis dataKey="label" tickLine={false} axisLine={false} />
                <YAxis tickLine={false} axisLine={false} width={88} />
                <Tooltip />
                <Area type="monotone" dataKey="amount" stroke="#1677FF" strokeWidth={3} fill="url(#bipAmount)" />
              </AreaChart>
            </ResponsiveContainer>
          ) : (
            <Empty description="暂无趋势数据" />
          )}
        </section>

        <section className="bip-card panel-card side-card">
          <div className="panel-header">
            <div>
              <h3>供应商排行</h3>
              <p>点击供应商可查看对应订单</p>
            </div>
            <span className="panel-extra">TOP {rankRows.length || 8}</span>
          </div>
          {rankRows.length ? (
            <div className="rank-list">
              {rankRows.map((row, index) => (
                <button
                  key={row.linkValue}
                  className="rank-item"
                  onClick={() => navigate(`/orders?supplierId=${row.linkValue}&startDate=${range.start}&endDate=${range.end}`)}
                >
                  <span className="rank-index">{index + 1}</span>
                  <span className="rank-name">{row.name}</span>
                  <span className="rank-count">{row.orderCount} 单</span>
                  <span className="rank-amount">¥{Number(row.amount).toLocaleString('zh-CN')}</span>
                </button>
              ))}
            </div>
          ) : (
            <Empty description="暂无供应商排行" />
          )}
        </section>

        <section className="bip-card panel-card side-card">
          <div className="panel-header">
            <div>
              <h3>状态分布</h3>
              <p>当前近半年订单状态占比</p>
            </div>
            <span className="panel-extra">{statusRows.reduce((acc, item) => acc + item.count, 0)} 单</span>
          </div>
          <div className="status-list">
            {statusRows.map((item) => (
              <div key={item.label}>
                <div className="status-row">
                  <span>{item.label}</span>
                  <span>
                    {item.count} 单 / {item.rate}%
                  </span>
                </div>
                <Progress percent={item.rate} showInfo={false} strokeColor={item.color} />
              </div>
            ))}
          </div>
        </section>

        <section className="bip-card panel-card">
          <div className="panel-header">
            <div>
              <h3>AI 预警摘要</h3>
              <p>{latestAlert ? `最近一条：${latestAlert.title}` : '暂无未处理预警，可先扫描一次'}</p>
            </div>
            <button className="bip-link" onClick={() => navigate('/agent-alerts')}>
              打开预警中心
            </button>
          </div>
          {alerts.length ? (
            <div className="alert-list">
              {alerts.slice(0, 4).map((alert) => (
                <div key={alert.alertId} className="alert-item">
                  <span className={`alert-dot alert-${alert.level.toLowerCase()}`} />
                  <div className="alert-body">
                    <div className="alert-title">{alert.title}</div>
                    <div className="alert-content">{alert.content}</div>
                  </div>
                  {alert.refNo && <Tag>{alert.refNo}</Tag>}
                </div>
              ))}
            </div>
          ) : (
            <Empty description="暂无预警" />
          )}
        </section>

        <section className="bip-card panel-card quick-panel">
          <div className="panel-header">
            <div>
              <h3>常用入口</h3>
              <p>根据现有业务模块组织，避免空功能</p>
            </div>
            <SyncOutlined />
          </div>
          <div className="quick-grid">
            <button onClick={() => navigate('/orders/create')}>
              <FileAddOutlined />
              <span>新建订单</span>
            </button>
            <button onClick={() => navigate('/approvals')}>
              <AuditOutlined />
              <span>审批待办</span>
            </button>
            <button onClick={() => navigate('/orders')}>
              <FileSearchOutlined />
              <span>订单列表</span>
            </button>
            <button onClick={() => navigate('/agent-chat')}>
              <RobotOutlined />
              <span>智能问答</span>
            </button>
          </div>
        </section>
      </div>

      <Spin spinning={loading} wrapperClassName="workspace-spin" />
    </div>
  )
}
