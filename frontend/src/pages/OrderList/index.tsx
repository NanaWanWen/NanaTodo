import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { App as AntdApp, Button, DatePicker, Input, Select, Space, Table, Tag } from 'antd'
import type { TableProps } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { orderApi } from '../../api/orders'
import { baseApi } from '../../api/base'
import { useAuthStore } from '../../store/useAuthStore'
import { PageCard } from '../../components/PageCard'
import { StatusTag, statusOptions } from '../../components/StatusTag'
import { MoneyText } from '../../components/MoneyText'
import type { OrderListItem } from '../../types'
import dayjs from 'dayjs'

const { RangePicker } = DatePicker

/**
 * 订单列表页：筛选 + 表格 + 分页 + 行操作
 */
export function OrderListPage() {
  const navigate = useNavigate()
  const { message, modal } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const currentRole = useAuthStore((state) => state.currentRole)
  const [searchParams, setSearchParams] = useSearchParams()

  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<string>()
  const [supplierId, setSupplierId] = useState<number>()
  const [buyerId, setBuyerId] = useState<number>()
  const [deptId, setDeptId] = useState<number>()
  const [materialId, setMaterialId] = useState<number>()
  const [range, setRange] = useState<[string, string] | null>(null)
  const [pageNo, setPageNo] = useState(1)
  const [pageSize, setPageSize] = useState(20)

  // 报表穿透联查：URL 参数 → 筛选条件（如 /orders?supplierId=1002&startDate=...）
  useEffect(() => {
    const num = (key: string) => {
      const value = searchParams.get(key)
      const parsed = Number(value)
      return value && Number.isFinite(parsed) ? parsed : undefined
    }
    const start = searchParams.get('startDate')
    const end = searchParams.get('endDate')
    setStatus(searchParams.get('status') ?? undefined)
    setSupplierId(num('supplierId'))
    setBuyerId(num('buyerId'))
    setDeptId(num('deptId'))
    setMaterialId(num('materialId'))
    setRange(start && end ? [start, end] : null)
    setPageNo(1)
  }, [searchParams])

  const canEdit = currentRole === 'BUYER' || currentRole === 'ADMIN'

  const { data, isLoading } = useQuery({
    queryKey: ['orders', keyword, status, supplierId, buyerId, deptId, materialId, range, pageNo, pageSize],
    queryFn: () =>
      orderApi.page({
        keyword: keyword || undefined,
        status,
        supplierId,
        buyerId,
        deptId,
        materialId,
        startDate: range?.[0],
        endDate: range?.[1],
        pageNo,
        pageSize
      })
  })

  const { data: suppliers = [] } = useQuery({
    queryKey: ['suppliers'],
    queryFn: () => baseApi.suppliers()
  })

  const { data: materials = [] } = useQuery({
    queryKey: ['materials'],
    queryFn: () => baseApi.materials(),
    enabled: materialId !== undefined
  })

  const { data: users = [] } = useQuery({
    queryKey: ['users'],
    queryFn: () => baseApi.users(),
    enabled: buyerId !== undefined
  })

  const { data: deptDict = [] } = useQuery({
    queryKey: ['dict', 'dept'],
    queryFn: () => baseApi.dict('dept'),
    enabled: deptId !== undefined
  })

  /** 移除某个来自报表的筛选条件 */
  const clearDim = (key: 'buyerId' | 'deptId' | 'materialId' | 'supplierId') => {
    const next = new URLSearchParams(searchParams)
    next.delete(key)
    setSearchParams(next, { replace: true })
    if (key === 'buyerId') {
      setBuyerId(undefined)
    }
    if (key === 'deptId') {
      setDeptId(undefined)
    }
    if (key === 'materialId') {
      setMaterialId(undefined)
    }
    if (key === 'supplierId') {
      setSupplierId(undefined)
    }
    setPageNo(1)
  }

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['orders'] })
    queryClient.invalidateQueries({ queryKey: ['reports'] })
  }

  const submitMutation = useMutation({
    mutationFn: (id: number) => orderApi.action(id, 'submit'),
    onSuccess: () => {
      message.success('已提交审批')
      invalidate()
    },
    onError: (error: Error) => message.error(error.message)
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => orderApi.remove(id),
    onSuccess: () => {
      message.success('订单已删除')
      invalidate()
    },
    onError: (error: Error) => message.error(error.message)
  })

  const supplierOptions = useMemo(
    () => suppliers.map((item) => ({ value: item.id, label: item.supplierName })),
    [suppliers]
  )

  const dimTags = [
    supplierId
      ? {
          key: 'supplierId',
          text: `供应商：${suppliers.find((item) => item.id === supplierId)?.supplierName ?? supplierId}`
        }
      : null,
    buyerId
      ? { key: 'buyerId', text: `采购员：${users.find((item) => item.id === buyerId)?.realName ?? buyerId}` }
      : null,
    deptId
      ? {
          key: 'deptId',
          text: `部门：${deptDict.find((item) => item.dictCode === String(deptId))?.dictName ?? deptId}`
        }
      : null,
    materialId
      ? {
          key: 'materialId',
          text: `物料：${materials.find((item) => item.id === materialId)?.materialName ?? materialId}`
        }
      : null,
    range ? { key: 'range', text: `订单日期：${range[0]} ~ ${range[1]}` } : null
  ].filter(Boolean) as { key: string; text: string }[]

  const handleDelete = (record: OrderListItem) => {
    modal.confirm({
      title: '确认删除订单？',
      content: `${record.orderNo} ${record.title}，删除后不可恢复`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => deleteMutation.mutate(record.id)
    })
  }

  const columns: TableProps<OrderListItem>['columns'] = [
    {
      title: '订单编号',
      dataIndex: 'orderNo',
      width: 160,
      render: (value: string, record: OrderListItem) => (
        <button
          className="cursor-pointer text-primary hover:text-primary-deep"
          onClick={() => navigate(`/orders/${record.id}`)}
        >
          {value}
        </button>
      )
    },
    { title: '标题', dataIndex: 'title', ellipsis: true },
    { title: '供应商', dataIndex: 'supplierName', width: 180, ellipsis: true },
    { title: '采购员', dataIndex: 'buyerName', width: 100 },
    { title: '订单日期', dataIndex: 'orderDate', width: 120 },
    {
      title: '数量',
      dataIndex: 'totalQty',
      width: 100,
      align: 'right' as const,
      render: (value: number) => Number(value ?? 0).toFixed(3)
    },
    {
      title: '金额',
      dataIndex: 'totalAmount',
      width: 140,
      align: 'right' as const,
      render: (value: number) => <MoneyText value={value} />
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (value: OrderListItem['status']) => <StatusTag status={value} />
    },
    {
      title: '当前节点',
      dataIndex: 'currentNode',
      width: 110,
      render: (value: number, record: OrderListItem) =>
        record.status === 'APPROVING' ? (
          <Tag color="blue">{value === 1 ? '采购经理' : '总监'}</Tag>
        ) : (
          <span className="text-ink-muted">-</span>
        )
    },
    {
      title: '操作',
      key: 'action',
      width: 170,
      fixed: 'right' as const,
      render: (_: unknown, record: OrderListItem) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => navigate(`/orders/${record.id}`)}>
            查看
          </Button>
          {canEdit && (record.status === 'DRAFT' || record.status === 'REJECTED') && (
            <Button type="link" size="small" onClick={() => navigate(`/orders/${record.id}/edit`)}>
              编辑
            </Button>
          )}
          {canEdit && (record.status === 'DRAFT' || record.status === 'REJECTED') && (
            <Button
              type="link"
              size="small"
              loading={submitMutation.isPending}
              onClick={() => submitMutation.mutate(record.id)}
            >
              提交
            </Button>
          )}
          {canEdit && record.status === 'DRAFT' && (
            <Button type="link" size="small" danger onClick={() => handleDelete(record)}>
              删除
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
            <span className="text-sm text-ink-muted">关键字</span>
            <Input
              allowClear
              className="w-56"
              placeholder="订单编号 / 标题"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
            />
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-sm text-ink-muted">状态</span>
            <Select
              allowClear
              className="w-40"
              placeholder="全部"
              value={status}
              onChange={(value) => setStatus(value)}
              options={statusOptions}
            />
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-sm text-ink-muted">供应商</span>
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              className="w-56"
              placeholder="全部"
              value={supplierId}
              onChange={(value) => setSupplierId(value)}
              options={supplierOptions}
            />
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-sm text-ink-muted">订单日期</span>
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
          <Button
            onClick={() => {
              setKeyword('')
              setStatus(undefined)
              setSupplierId(undefined)
              setBuyerId(undefined)
              setDeptId(undefined)
              setMaterialId(undefined)
              setRange(null)
              setPageNo(1)
              setSearchParams(new URLSearchParams(), { replace: true })
            }}
          >
            重置
          </Button>
        </div>
      </PageCard>

      <PageCard>
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-base font-medium text-ink">采购订单</h3>
          {canEdit && (
            <Button type="primary" onClick={() => navigate('/orders/create')}>
              新建订单
            </Button>
          )}
        </div>
        {dimTags.length > 0 && (
          <div className="mb-4 flex flex-wrap items-center gap-2 rounded-lg bg-surface-page px-4 py-2 text-sm">
            <span className="text-ink-muted">报表筛选：</span>
            {dimTags.map((tag) => (
              <Tag
                key={tag.key}
                closable
                onClose={() => {
                  if (tag.key === 'range') {
                    setRange(null)
                    setPageNo(1)
                    return
                  }
                  clearDim(tag.key as 'buyerId' | 'deptId' | 'materialId' | 'supplierId')
                }}
              >
                {tag.text}
              </Tag>
            ))}
          </div>
        )}
        <Table
          rowKey="id"
          loading={isLoading}
          columns={columns}
          dataSource={data?.records ?? []}
          scroll={{ x: 1200 }}
          pagination={{
            current: pageNo,
            pageSize,
            total: data?.total ?? 0,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`,
            onChange: (page, size) => {
              setPageNo(page)
              setPageSize(size)
            }
          }}
        />
      </PageCard>
    </div>
  )
}
