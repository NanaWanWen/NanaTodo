import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  App as AntdApp,
  Button,
  Card,
  DatePicker,
  Input,
  InputNumber,
  Select,
  Space,
  Table,
  Typography
} from 'antd'
import type { TableProps } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { orderApi } from '../../api/orders'
import { baseApi } from '../../api/base'
import { PageCard } from '../../components/PageCard'
import type { MaterialItem, OrderItem } from '../../types'
import dayjs from 'dayjs'

interface RowItem extends OrderItem {
  key: string
}

const emptyRow = (lineNo: number): RowItem => ({
  key: `${Date.now()}-${lineNo}`,
  lineNo,
  materialId: 0,
  qty: 1,
  price: 0,
  taxRate: 13
})

/**
 * 订单编辑页：表头 + 可编辑明细 + 金额合计 + 保存/提交
 */
export function OrderEditPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const isEdit = Boolean(id)

  const [title, setTitle] = useState('')
  const [supplierId, setSupplierId] = useState<number>()
  const [orderDate, setOrderDate] = useState<string>(dayjs().format('YYYY-MM-DD'))
  const [expectDate, setExpectDate] = useState<string>()
  const [currency, setCurrency] = useState('CNY')
  const [remark, setRemark] = useState('')
  const [version, setVersion] = useState<number>()
  const [rows, setRows] = useState<RowItem[]>([emptyRow(10)])

  const { data: suppliers = [] } = useQuery({
    queryKey: ['suppliers'],
    queryFn: () => baseApi.suppliers()
  })

  const { data: materials = [] } = useQuery({
    queryKey: ['materials'],
    queryFn: () => baseApi.materials()
  })

  const { data: detail } = useQuery({
    queryKey: ['order-detail', id],
    queryFn: () => orderApi.detail(Number(id)),
    enabled: isEdit
  })

  useEffect(() => {
    if (!detail) {
      return
    }
    setTitle(detail.title)
    setSupplierId(detail.supplierId)
    setOrderDate(detail.orderDate)
    setExpectDate(detail.expectDate)
    setCurrency(detail.currency ?? 'CNY')
    setRemark(detail.remark ?? '')
    setVersion(detail.version)
    setRows(
      detail.items.map((item, index) => ({
        ...item,
        key: `${item.id ?? index}`,
        lineNo: item.lineNo ?? (index + 1) * 10
      }))
    )
  }, [detail])

  const materialMap = useMemo(() => {
    const map = new Map<number, MaterialItem>()
    materials.forEach((item) => map.set(item.id, item))
    return map
  }, [materials])

  const totals = useMemo(() => {
    const amounts = rows.map((row) => calcAmount(row))
    return {
      qty: rows.reduce((sum, row) => sum + Number(row.qty ?? 0), 0),
      amount: amounts.reduce((sum, amount) => sum + amount, 0)
    }
  }, [rows])

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['orders'] })
    queryClient.invalidateQueries({ queryKey: ['order-detail'] })
    queryClient.invalidateQueries({ queryKey: ['reports'] })
  }

  const saveMutation = useMutation({
    mutationFn: async (): Promise<number> => {
      if (isEdit) {
        await orderApi.update(Number(id), buildPayload())
        return Number(id)
      }
      return orderApi.create(buildPayload())
    },
    onSuccess: (orderId) => {
      message.success(isEdit ? '订单已保存' : '订单已创建')
      invalidate()
      navigate(isEdit ? `/orders/${id}` : `/orders/${orderId}`)
    },
    onError: (error: Error) => message.error(error.message)
  })

  const submitMutation = useMutation({
    mutationFn: async () => {
      const orderId = isEdit
        ? await orderApi.update(Number(id), buildPayload()).then(() => Number(id))
        : await orderApi.create(buildPayload())
      return orderApi.action(orderId, 'submit')
    },
    onSuccess: () => {
      message.success('已提交审批')
      invalidate()
      navigate('/orders')
    },
    onError: (error: Error) => message.error(error.message)
  })

  function calcAmount(row: RowItem) {
    const rate = Number(row.taxRate ?? 0)
    return Number(row.qty ?? 0) * Number(row.price ?? 0) * (1 + rate / 100)
  }

  function buildPayload() {
    return {
      title: title.trim(),
      supplierId: supplierId!,
      currency,
      orderDate,
      expectDate: expectDate || undefined,
      remark: remark || undefined,
      version,
      items: rows
        .filter((row) => row.materialId > 0)
        .map((row, index) => ({
          id: row.id,
          lineNo: (index + 1) * 10,
          materialId: row.materialId,
          qty: Number(row.qty),
          price: Number(row.price),
          taxRate: Number(row.taxRate ?? 0),
          remark: row.remark
        }))
    }
  }

  const updateRow = (key: string, patch: Partial<RowItem>) => {
    setRows((prev) => prev.map((row) => (row.key === key ? { ...row, ...patch } : row)))
  }

  const handleMaterialChange = (key: string, materialId: number) => {
    const material = materialMap.get(materialId)
    updateRow(key, {
      materialId,
      materialName: material?.materialName,
      spec: material?.spec,
      unit: material?.unit,
      price: Number(material?.refPrice ?? 0),
      taxRate: Number(material?.taxRate ?? 13)
    })
  }

  const columns: TableProps<RowItem>['columns'] = [
    {
      title: '行号',
      dataIndex: 'lineNo',
      width: 70,
      render: (_: unknown, __: RowItem, index: number) => (index + 1) * 10
    },
    {
      title: '物料',
      dataIndex: 'materialId',
      width: 240,
      render: (value: number, row: RowItem) => (
        <Select
          className="w-full"
          showSearch
          optionFilterProp="label"
          placeholder="请选择物料"
          value={value || undefined}
          onChange={(next) => handleMaterialChange(row.key, next)}
          options={materials.map((item) => ({
            value: item.id,
            label: `${item.materialName}（${item.materialCode}）`
          }))}
        />
      )
    },
    {
      title: '规格',
      dataIndex: 'spec',
      width: 160,
      render: (value: string) => <span className="text-ink-muted">{value || '-'}</span>
    },
    {
      title: '单位',
      dataIndex: 'unit',
      width: 80
    },
    {
      title: '数量',
      dataIndex: 'qty',
      width: 120,
      render: (value: number, row: RowItem) => (
        <InputNumber
          className="w-full"
          min={0.001}
          precision={3}
          value={value}
          onChange={(next) => updateRow(row.key, { qty: Number(next ?? 0) })}
        />
      )
    },
    {
      title: '单价',
      dataIndex: 'price',
      width: 140,
      render: (value: number, row: RowItem) => (
        <InputNumber
          className="w-full"
          min={0.01}
          precision={4}
          value={value}
          onChange={(next) => updateRow(row.key, { price: Number(next ?? 0) })}
        />
      )
    },
    {
      title: '税率(%)',
      dataIndex: 'taxRate',
      width: 110,
      render: (value: number, row: RowItem) => (
        <InputNumber
          className="w-full"
          min={0}
          max={100}
          precision={2}
          value={value}
          onChange={(next) => updateRow(row.key, { taxRate: Number(next ?? 0) })}
        />
      )
    },
    {
      title: '金额',
      key: 'amount',
      width: 130,
      align: 'right' as const,
      render: (_: unknown, row: RowItem) => (
        <span className="money-text">¥{calcAmount(row).toFixed(2)}</span>
      )
    },
    {
      title: '操作',
      key: 'op',
      width: 80,
      render: (_: unknown, row: RowItem) => (
        <Button
          type="link"
          danger
          disabled={rows.length === 1}
          onClick={() => setRows((prev) => prev.filter((item) => item.key !== row.key))}
        >
          删除
        </Button>
      )
    }
  ]

  return (
    <div className="flex flex-col gap-5">
      <PageCard title={isEdit ? '编辑采购订单' : '新建采购订单'}>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
          <div className="flex flex-col gap-1">
            <span className="text-sm text-ink-muted">
              订单标题 <span className="text-functional-danger">*</span>
            </span>
            <Input
              value={title}
              maxLength={128}
              placeholder="如：2026 年 Q4 办公耗材采购"
              onChange={(event) => setTitle(event.target.value)}
            />
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-sm text-ink-muted">
              供应商 <span className="text-functional-danger">*</span>
            </span>
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="请选择供应商"
              value={supplierId}
              onChange={setSupplierId}
              options={suppliers.map((item) => ({
                value: item.id,
                label: item.supplierName
              }))}
            />
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-sm text-ink-muted">币种</span>
            <Select
              value={currency}
              onChange={setCurrency}
              options={[
                { value: 'CNY', label: '人民币 CNY' },
                { value: 'USD', label: '美元 USD' }
              ]}
            />
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-sm text-ink-muted">
              订单日期 <span className="text-functional-danger">*</span>
            </span>
            <DatePicker
              className="w-full"
              value={orderDate ? dayjs(orderDate) : null}
              onChange={(date) => setOrderDate(date ? date.format('YYYY-MM-DD') : '')}
            />
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-sm text-ink-muted">期望到货日期</span>
            <DatePicker
              className="w-full"
              value={expectDate ? dayjs(expectDate) : null}
              onChange={(date) => setExpectDate(date ? date.format('YYYY-MM-DD') : undefined)}
            />
          </div>
          <div className="flex flex-col gap-1 md:col-span-3">
            <span className="text-sm text-ink-muted">备注</span>
            <Input.TextArea
              rows={2}
              maxLength={500}
              value={remark}
              onChange={(event) => setRemark(event.target.value)}
            />
          </div>
        </div>
      </PageCard>

      <PageCard
        title="物料明细"
        extra={
          <Button onClick={() => setRows((prev) => [...prev, emptyRow((prev.length + 1) * 10)])}>
            新增行
          </Button>
        }
      >
        <Table
          rowKey="key"
          size="small"
          columns={columns}
          dataSource={rows}
          pagination={false}
          scroll={{ x: 1100 }}
        />

        <div className="mt-4 flex items-center justify-end gap-8">
          <div className="flex items-center gap-2">
            <span className="text-sm text-ink-muted">数量合计</span>
            <span className="money-text text-base font-medium">{totals.qty.toFixed(3)}</span>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-sm text-ink-muted">金额合计</span>
            <Typography.Text className="money-text text-lg font-semibold text-primary">
              ¥{totals.amount.toFixed(2)}
            </Typography.Text>
          </div>
        </div>
      </PageCard>

      <Card className="shadow-card">
        <Space>
          <Button
            type="primary"
            loading={saveMutation.isPending}
            onClick={() => saveMutation.mutate()}
          >
            保存
          </Button>
          <Button
            loading={submitMutation.isPending}
            onClick={() => submitMutation.mutate()}
          >
            保存并提交审批
          </Button>
          <Button onClick={() => navigate(-1)}>返回</Button>
        </Space>
        <span className="ml-4 text-xs text-ink-muted">
          金额由服务端按明细重算，前端仅作试算展示
        </span>
      </Card>
    </div>
  )
}
