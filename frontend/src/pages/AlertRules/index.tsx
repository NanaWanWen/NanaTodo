import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntdApp, Button, Drawer, Form, Input, Modal, Select, Space, Switch, Table, Tag, Typography } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { alertRuleApi } from '../../api/alertRules'
import { agentAlerts } from '../../api/agent'
import { PageCard } from '../../components/PageCard'
import type { AgentAlertRule } from '../../types'

const DOMAIN_OPTIONS = [
  { label: '采购', value: 'PURCHASE' },
  { label: '库存', value: 'INVENTORY' },
  { label: '财务', value: 'FINANCE' }
]

const LEVEL_OPTIONS = [
  { label: '低 INFO', value: 'INFO' },
  { label: '警告 WARNING', value: 'WARNING' },
  { label: '严重 CRITICAL', value: 'CRITICAL' }
]

const THRESHOLD_UNIT_OPTIONS = [
  { label: '天 DAY', value: 'DAY' },
  { label: '小时 HOUR', value: 'HOUR' },
  { label: '百分比 PERCENT', value: 'PERCENT' },
  { label: '金额 AMOUNT', value: 'AMOUNT' },
  { label: '倍数 MULTIPLE', value: 'MULTIPLE' },
  { label: '数量 QTY', value: 'QTY' }
]

const RULE_DESC: Record<string, string> = {
  OVERDUE_ORDER: '已审批订单预计到货日期已过即预警；逾期超阈值 2 倍升级为严重。',
  APPROVAL_TIMEOUT: '审批中订单滞留超过阈值小时数即预警；超 2 倍升级为严重。',
  PRICE_ABNORMAL: '订单明细单价与物料参考价偏差超过阈值即预警；超 2 倍升级为严重。',
  LOW_STOCK: '库存低于安全库存×阈值系数即预警；库存为 0 升级为严重。',
  OVERSTOCK: '库存超过安全库存×阈值倍数视为超储，请评估周转率。',
  INVOICE_MISMATCH: '三单匹配状态非完全匹配即预警；差异超容差 2 倍升级为严重。',
  PAYMENT_OVERDUE: '待付款单滞留超过阈值天数即预警；超 2 倍升级为严重。'
}

const DOMAIN_COLOR: Record<string, string> = {
  PURCHASE: 'blue',
  INVENTORY: 'purple',
  FINANCE: 'gold'
}

export function AlertRulesPage() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [rules, setRules] = useState<AgentAlertRule[]>([])
  const [loading, setLoading] = useState(false)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editingRule, setEditingRule] = useState<AgentAlertRule | null>(null)

  // react-query v5 已移除 useQuery 的 onSuccess，需通过 effect 同步到组件状态
  const { data, refetch, isFetching } = useQuery({
    queryKey: ['alert-rules'],
    queryFn: () => alertRuleApi.list()
  })

  useEffect(() => {
    if (data) setRules(data)
  }, [data])

  useEffect(() => {
    setLoading(isFetching)
  }, [isFetching])

  // 当前各规则命中的未处理预警数：让「规则配置」与「预警中心」一一对应
  const { data: alerts = [] } = useQuery({
    queryKey: ['alert-rule-hit-count'],
    queryFn: () => agentAlerts(false, false)
  })

  const alertCountByRule = useMemo(() => {
    const map: Record<string, number> = {}
    alerts.forEach((a) => {
      const key = a.ruleCode ?? a.alertType
      if (key) map[key] = (map[key] ?? 0) + 1
    })
    return map
  }, [alerts])

  // 规则变更后同步刷新「预警中心」侧的数据（规则列表 + 命中数）
  const refreshAll = () => {
    refetch()
    qc.invalidateQueries({ queryKey: ['alert-rule-hit-count'] })
    qc.invalidateQueries({ queryKey: ['agent-alerts'] })
  }

  const createMutation = useMutation({
    mutationFn: (rule: AgentAlertRule) => alertRuleApi.create(rule),
    onSuccess: () => {
      message.success('规则已创建')
      setDrawerOpen(false)
      refreshAll()
    },
    onError: (error: Error) => message.error(error.message)
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, rule }: { id: number; rule: AgentAlertRule }) =>
      alertRuleApi.update(id, rule),
    onSuccess: () => {
      message.success('规则已更新，该规则的预警已按新规则重建')
      setDrawerOpen(false)
      setEditingRule(null)
      refreshAll()
    },
    onError: (error: Error) => message.error(error.message)
  })

  const removeMutation = useMutation({
    mutationFn: (id: number) => alertRuleApi.remove(id),
    onSuccess: () => {
      message.success('规则已删除，其名下预警已清理')
      refreshAll()
    },
    onError: (error: Error) => message.error(error.message)
  })

  const handleCreate = () => {
    setEditingRule(null)
    setDrawerOpen(true)
  }

  const handleEdit = (record: AgentAlertRule) => {
    setEditingRule(record)
    setDrawerOpen(true)
  }

  const handleDelete = (id: number) => {
    Modal.confirm({
      title: '确认删除',
      content: '删除后预警规则将停用，确定？',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => removeMutation.mutate(id)
    })
  }

  const handleSubmit = (values: AgentAlertRule) => {
    if (editingRule?.id) {
      updateMutation.mutate({ id: editingRule.id, rule: { ...editingRule, ...values } })
    } else {
      createMutation.mutate(values)
    }
  }

  const columns = [
    {
      title: '规则编码',
      dataIndex: 'ruleCode',
      key: 'ruleCode',
      width: 160
    },
    {
      title: '规则名称',
      dataIndex: 'ruleName',
      key: 'ruleName'
    },
    {
      title: '业务域',
      dataIndex: 'bizDomain',
      key: 'bizDomain',
      width: 100,
      render: (v: string) => (
        <Tag color={DOMAIN_COLOR[v] ?? 'default'}>{v ?? '-'}</Tag>
      )
    },
    {
      title: '级别',
      dataIndex: 'level',
      key: 'level',
      width: 100,
      render: (v: string) => (
        <Tag color={v === 'CRITICAL' ? 'error' : v === 'WARNING' ? 'warning' : 'default'}>
          {v}
        </Tag>
      )
    },
    {
      title: '阈值',
      key: 'threshold',
      width: 140,
      render: (_: unknown, record: AgentAlertRule) => {
        const unit = record.thresholdUnit ?? ''
        const desc = RULE_DESC[record.ruleCode ?? ''] ?? ''
        return (
          <span title={desc}>
            {record.threshold != null ? `${record.threshold} ${unit || '(缺省)'}` : '-'}
          </span>
        )
      }
    },
    {
      title: '规则说明',
      dataIndex: 'remark',
      key: 'remark',
      width: 260,
      ellipsis: true,
      render: (v: string, record: AgentAlertRule) => (
        <span title={v}>{RULE_DESC[record.ruleCode ?? ''] ?? v ?? '-'}</span>
      )
    },
    {
      title: '当前预警数',
      key: 'alertCount',
      width: 120,
      render: (_: unknown, record: AgentAlertRule) => {
        const count = alertCountByRule[record.ruleCode ?? ''] ?? 0
        return count > 0 ? (
          <Button
            type="link"
            size="small"
            className="!px-0"
            onClick={() => navigate(`/agent-alerts?rule=${record.ruleCode ?? ''}`)}
          >
            {count} 条（查看）
          </Button>
        ) : (
          <span className="text-xs text-ink-muted">0</span>
        )
      }
    },
    {
      title: '通知渠道',
      dataIndex: 'notifyChannel',
      key: 'notifyChannel',
      width: 120,
      render: (v: string) => v ?? 'INAPP'
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 80,
      render: (v: number) => (
        <Switch checked={v === 1} size="small" />
      )
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_: unknown, record: AgentAlertRule) => (
        <Space>
          <Button type="link" size="small" onClick={() => handleEdit(record)}>
            编辑
          </Button>
          <Button danger type="link" size="small" onClick={() => handleDelete(record.id!)}>
            删除
          </Button>
        </Space>
      )
    }
  ]

  return (
    <div className="flex flex-col gap-5">
      <PageCard
        title="预警规则配置"
        extra={
          <Button type="primary" onClick={handleCreate}>
            新建规则
          </Button>
        }
      >
        <div className="text-xs text-ink-muted mb-4">
          业务角色可自助调整预警规则的阈值、级别、启停与通知对象，无需修改代码。
          内置规则（逾期订单 / 审批超时 / 价格异常 / 低库存 / 超储 / 三单匹配差异 / 付款逾期）已预置，可直接编辑。
          <b>修改某条规则后，只有该规则的历史预警会被清除并按新规则重建，未修改的规则及其预警保持原样</b>；
          停用或删除规则会一并清理其名下的预警。
        </div>

        <Table<AgentAlertRule>
          rowKey="id"
          columns={columns}
          dataSource={rules}
          loading={loading}
          pagination={{ pageSize: 10 }}
          size="small"
        />
      </PageCard>

      <Drawer
        title={editingRule ? '编辑预警规则' : '新建预警规则'}
        width={520}
        open={drawerOpen}
        onClose={() => {
          setDrawerOpen(false)
          setEditingRule(null)
        }}
        destroyOnClose
      >
        <RuleForm
          initialValues={editingRule}
          onSubmit={handleSubmit}
          submitting={createMutation.isPending || updateMutation.isPending}
        />
      </Drawer>
    </div>
  )
}

interface RuleFormProps {
  initialValues?: AgentAlertRule | null
  onSubmit: (values: AgentAlertRule) => void
  submitting: boolean
}

function RuleForm({ initialValues, onSubmit, submitting }: RuleFormProps) {
  const [form] = Form.useForm<AgentAlertRule>()

  useEffect(() => {
    if (initialValues) {
      form.setFieldsValue(initialValues)
    } else {
      form.resetFields()
    }
  }, [initialValues, form])

  return (
    <Form
      form={form}
      layout="vertical"
      initialValues={{ bizDomain: 'PURCHASE', level: 'WARNING', enabled: 1, notifyChannel: 'INAPP' }}
      onFinish={onSubmit}
    >
      <Form.Item name="ruleCode" label="规则编码" rules={[{ required: true, message: '请输入规则编码' }]}>
        <Input placeholder="OVERDUE_ORDER / LOW_STOCK / PRICE_ABNORMAL / APPROVAL_TIMEOUT / OVERSTOCK / INVOICE_MISMATCH / PAYMENT_OVERDUE" disabled={!!initialValues} />
      </Form.Item>
      <Form.Item name="ruleName" label="规则名称" rules={[{ required: true, message: '请输入规则名称' }]}>
        <Input placeholder="逾期订单预警" />
      </Form.Item>
      <Form.Item name="bizDomain" label="业务域" rules={[{ required: true, message: '请选择业务域' }]}>
        <Select options={DOMAIN_OPTIONS} />
      </Form.Item>
      <Form.Item name="alertType" label="预警类型" rules={[{ required: true, message: '请输入预警类型' }]}>
        <Input placeholder="OVERDUE_ORDER / LOW_STOCK / PRICE_ABNORMAL / APPROVAL_TIMEOUT / OVERSTOCK / INVOICE_MISMATCH / PAYMENT_OVERDUE" />
      </Form.Item>
      <Form.Item name="level" label="预警级别" rules={[{ required: true, message: '请选择预警级别' }]}>
        <Select options={LEVEL_OPTIONS} />
      </Form.Item>
      <Form.Item name="remark" label="规则说明">
        <Input.TextArea rows={2} placeholder="该规则的业务含义与触发条件说明" />
      </Form.Item>
      <Space.Compact style={{ width: '100%' }}>
        <Form.Item name="threshold" label="阈值" style={{ flex: 1, marginBottom: 0 }}>
          <Input type="number" placeholder="阈值数值" />
        </Form.Item>
        <Form.Item name="thresholdUnit" label="单位" style={{ width: 160, marginBottom: 0 }}>
          <Select options={THRESHOLD_UNIT_OPTIONS} placeholder="选择单位" />
        </Form.Item>
      </Space.Compact>
      <div className="text-xs text-ink-muted mt-1 mb-2">
        单位说明：DAY（天）、HOUR（小时）、PERCENT（百分比）、AMOUNT（金额）、MULTIPLE（倍数）、QTY（数量）
      </div>
      <Form.Item name="notifyChannel" label="通知渠道">
        <Select options={[
          { label: '站内 INAPP', value: 'INAPP' },
          { label: '邮件 EMAIL', value: 'EMAIL' },
          { label: '实时推送 SSE', value: 'SSE' }
        ]} placeholder="选择通知渠道" />
      </Form.Item>
      <Form.Item name="notifyRoles" label="通知角色">
        <Input placeholder="多个角色逗号分隔，如 BUYER,PURCHASE_MANAGER" />
      </Form.Item>
      <Form.Item name="ownerRole" label="规则归属角色">
        <Input placeholder="业务角色编码，如 BUYER / PURCHASE_MANAGER" />
      </Form.Item>
      <Form.Item name="enabled" label="启用状态" valuePropName="checked">
        <Switch />
      </Form.Item>
      <Form.Item>
        <Button type="primary" htmlType="submit" loading={submitting} block>
          保存
        </Button>
      </Form.Item>
    </Form>
  )
}