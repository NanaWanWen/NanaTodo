import { useState } from 'react'
import { App as AntdApp, Button, Form, Input, InputNumber, Modal, Select, Space, Switch, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { request, unwrap } from '../../api/request'
import type { Result } from '../../types'
import { PageCard } from '../../components/PageCard'

interface MatchRule {
  id: number
  ruleCode: string
  ruleName: string
  scopeType: 'GLOBAL' | 'SUPPLIER' | 'MATERIAL_CATEGORY' | 'CURRENCY'
  scopeValue?: string
  qtyTolerance: number
  qtyToleranceUnit: 'PERCENT' | 'ABSOLUTE'
  amountTolerance: number
  amountToleranceUnit: 'PERCENT' | 'ABSOLUTE'
  autoSettle: number
  accrualMode: 'SINGLE' | 'MONTHLY'
  allowForceMatch: number
  approvalRole?: string
  enabled: number
}

export function MatchRulesPage() {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const [modalOpen, setModalOpen] = useState(false)
  const [editingRule, setEditingRule] = useState<MatchRule | null>(null)
  const [form] = Form.useForm()

  const { data: rules = [], isFetching } = useQuery({
    queryKey: ['match-rules'],
    queryFn: () => unwrap(request.get<Result<MatchRule[]>>('/ap/match-rules'))
  })

  const createMutation = useMutation({
    mutationFn: (data: Partial<MatchRule>) =>
      unwrap(request.post<Result<MatchRule>>('/ap/match-rules', data)),
    onSuccess: () => {
      message.success('规则创建成功')
      queryClient.invalidateQueries({ queryKey: ['match-rules'] })
      setModalOpen(false)
      form.resetFields()
    },
    onError: (error: Error) => message.error(error.message)
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: Partial<MatchRule> }) =>
      unwrap(request.put<Result<MatchRule>>(`/ap/match-rules/${id}`, data)),
    onSuccess: () => {
      message.success('规则更新成功')
      queryClient.invalidateQueries({ queryKey: ['match-rules'] })
      setModalOpen(false)
      setEditingRule(null)
      form.resetFields()
    },
    onError: (error: Error) => message.error(error.message)
  })

  const toggleMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) =>
      unwrap(request.patch<Result<void>>(`/ap/match-rules/${id}/toggle`, null, {
        params: { enabled }
      })),
    onSuccess: () => {
      message.success('状态已更新')
      queryClient.invalidateQueries({ queryKey: ['match-rules'] })
    },
    onError: (error: Error) => message.error(error.message)
  })

  const handleEdit = (rule: MatchRule) => {
    setEditingRule(rule)
    form.setFieldsValue(rule)
    setModalOpen(true)
  }

  const handleSubmit = () => {
    form.validateFields().then((values) => {
      if (editingRule) {
        updateMutation.mutate({ id: editingRule.id, data: values })
      } else {
        createMutation.mutate(values)
      }
    })
  }

  const columns: ColumnsType<MatchRule> = [
    {
      title: '规则编码',
      dataIndex: 'ruleCode',
      width: 140
    },
    {
      title: '规则名称',
      dataIndex: 'ruleName',
      width: 180
    },
    {
      title: '适用范围',
      dataIndex: 'scopeType',
      width: 120,
      render: (type: string, row) => {
        const label =
          type === 'GLOBAL'
            ? '全局'
            : type === 'SUPPLIER'
            ? '供应商'
            : type === 'MATERIAL_CATEGORY'
            ? '物料分类'
            : '币种'
        return (
          <span>
            {label} {row.scopeValue && `(${row.scopeValue})`}
          </span>
        )
      }
    },
    {
      title: '数量容差',
      dataIndex: 'qtyTolerance',
      width: 120,
      align: 'right',
      render: (v: number, row) => `${v} ${row.qtyToleranceUnit === 'PERCENT' ? '%' : ''}`
    },
    {
      title: '金额容差',
      dataIndex: 'amountTolerance',
      width: 120,
      align: 'right',
      render: (v: number, row) => `${v} ${row.amountToleranceUnit === 'PERCENT' ? '%' : ''}`
    },
    {
      title: '自动结算',
      dataIndex: 'autoSettle',
      width: 100,
      render: (v: number) => <Tag color={v === 1 ? 'green' : 'default'}>{v === 1 ? '是' : '否'}</Tag>
    },
    {
      title: '暂估方式',
      dataIndex: 'accrualMode',
      width: 100,
      render: (mode: string) => (mode === 'SINGLE' ? '单到回冲' : '月初回冲')
    },
    {
      title: '允许强制匹配',
      dataIndex: 'allowForceMatch',
      width: 120,
      render: (v: number) => <Tag color={v === 1 ? 'orange' : 'default'}>{v === 1 ? '是' : '否'}</Tag>
    },
    {
      title: '启用状态',
      dataIndex: 'enabled',
      width: 100,
      render: (enabled: number, row) => (
        <Switch
          checked={enabled === 1}
          onChange={(checked) => toggleMutation.mutate({ id: row.id, enabled: checked })}
        />
      )
    },
    {
      title: '操作',
      width: 100,
      align: 'center',
      render: (_v, row) => (
        <Button type="link" size="small" onClick={() => handleEdit(row)}>
          编辑
        </Button>
      )
    }
  ]

  return (
    <div className="flex flex-col gap-5">
      <PageCard>
        <div className="flex items-center justify-between">
          <div className="font-medium text-lg">匹配规则配置</div>
          <Button
            type="primary"
            onClick={() => {
              setEditingRule(null)
              form.resetFields()
              setModalOpen(true)
            }}
          >
            新增规则
          </Button>
        </div>
      </PageCard>

      <PageCard>
        <Table<MatchRule>
          rowKey="id"
          columns={columns}
          dataSource={rules}
          loading={isFetching}
          size="middle"
          pagination={false}
          scroll={{ x: 1200 }}
        />
      </PageCard>

      <Modal
        title={editingRule ? '编辑规则' : '新增规则'}
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false)
          setEditingRule(null)
          form.resetFields()
        }}
        onOk={handleSubmit}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        width={600}
      >
        <Form form={form} layout="vertical" className="mt-4">
          <Form.Item
            name="ruleCode"
            label="规则编码"
            rules={[{ required: true, message: '请输入规则编码' }]}
          >
            <Input disabled={!!editingRule} placeholder="如：DEFAULT、SUP-1001" />
          </Form.Item>
          <Form.Item
            name="ruleName"
            label="规则名称"
            rules={[{ required: true, message: '请输入规则名称' }]}
          >
            <Input placeholder="如：全局默认规则" />
          </Form.Item>
          <Form.Item
            name="scopeType"
            label="适用范围"
            rules={[{ required: true, message: '请选择适用范围' }]}
          >
            <Select
              options={[
                { value: 'GLOBAL', label: '全局' },
                { value: 'SUPPLIER', label: '供应商' },
                { value: 'MATERIAL_CATEGORY', label: '物料分类' },
                { value: 'CURRENCY', label: '币种' }
              ]}
            />
          </Form.Item>
          <Form.Item name="scopeValue" label="适用范围值">
            <Input placeholder="供应商ID/物料分类ID/币种代码" />
          </Form.Item>
          <Space size="large">
            <Form.Item
              name="qtyTolerance"
              label="数量容差"
              rules={[{ required: true, message: '请输入数量容差' }]}
            >
              <InputNumber min={0} step={0.01} style={{ width: 120 }} />
            </Form.Item>
            <Form.Item
              name="qtyToleranceUnit"
              label="单位"
              rules={[{ required: true, message: '请选择单位' }]}
            >
              <Select
                options={[
                  { value: 'PERCENT', label: '百分比' },
                  { value: 'ABSOLUTE', label: '绝对值' }
                ]}
                style={{ width: 100 }}
              />
            </Form.Item>
          </Space>
          <Space size="large">
            <Form.Item
              name="amountTolerance"
              label="金额容差"
              rules={[{ required: true, message: '请输入金额容差' }]}
            >
              <InputNumber min={0} step={0.01} style={{ width: 120 }} />
            </Form.Item>
            <Form.Item
              name="amountToleranceUnit"
              label="单位"
              rules={[{ required: true, message: '请选择单位' }]}
            >
              <Select
                options={[
                  { value: 'PERCENT', label: '百分比' },
                  { value: 'ABSOLUTE', label: '绝对值' }
                ]}
                style={{ width: 100 }}
              />
            </Form.Item>
          </Space>
          <Form.Item name="autoSettle" label="自动结算" valuePropName="checked">
            <Switch checkedChildren="是" unCheckedChildren="否" />
          </Form.Item>
          <Form.Item name="accrualMode" label="暂估方式">
            <Select
              options={[
                { value: 'SINGLE', label: '单到回冲' },
                { value: 'MONTHLY', label: '月初回冲' }
              ]}
            />
          </Form.Item>
          <Form.Item name="allowForceMatch" label="允许强制匹配" valuePropName="checked">
            <Switch checkedChildren="是" unCheckedChildren="否" />
          </Form.Item>
          <Form.Item name="approvalRole" label="审批角色">
            <Input placeholder="如：DIRECTOR" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
