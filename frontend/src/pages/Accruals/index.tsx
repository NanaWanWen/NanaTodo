import { useState } from 'react'
import { App as AntdApp, Button, DatePicker, Input, Modal, Select, Space, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { request, unwrap } from '../../api/request'
import type { AccrualItem, PageResult, Result } from '../../types'
import { PageCard } from '../../components/PageCard'

const money = (value: number) =>
  `¥${Number(value ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

export function AccrualsPage() {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const [period, setPeriod] = useState<string>()
  const [status, setStatus] = useState<string>()

  const { data: accrualPage, isFetching } = useQuery({
    queryKey: ['accruals', period, status],
    queryFn: () =>
      unwrap(
        request.get<Result<PageResult<AccrualItem>>>('/ap/accruals', {
          params: { period, status }
        })
      )
  })
  const accruals = accrualPage?.records ?? []

  const monthlyAccrueMutation = useMutation({
    mutationFn: (period: string) =>
      unwrap(request.post<Result<void>>('/ap/accruals/monthly-accrue', null, { params: { period } })),
    onSuccess: () => {
      message.success('月末批量暂估已完成')
      queryClient.invalidateQueries({ queryKey: ['accruals'] })
    },
    onError: (error: Error) => message.error(error.message)
  })

  const monthlyReverseMutation = useMutation({
    mutationFn: (period: string) =>
      unwrap(request.post<Result<void>>('/ap/accruals/monthly-reverse', null, { params: { period } })),
    onSuccess: () => {
      message.success('月初批量回冲已完成')
      queryClient.invalidateQueries({ queryKey: ['accruals'] })
    },
    onError: (error: Error) => message.error(error.message)
  })

  const handleMonthlyAccrue = () => {
    if (!period) {
      message.warning('请选择暂估期间')
      return
    }
    Modal.confirm({
      title: '月末批量暂估',
      content: `将为 ${period} 所有货到票未到的收货单生成暂估，是否继续？`,
      onOk: () => monthlyAccrueMutation.mutate(period)
    })
  }

  const handleMonthlyReverse = () => {
    if (!period) {
      message.warning('请选择回冲期间')
      return
    }
    Modal.confirm({
      title: '月初批量回冲',
      content: `将回冲 ${period} 所有暂估单，是否继续？`,
      onOk: () => monthlyReverseMutation.mutate(period)
    })
  }

  const columns: ColumnsType<AccrualItem> = [
    {
      title: '暂估单号',
      dataIndex: 'accrualNo',
      width: 170
    },
    {
      title: '供应商',
      dataIndex: 'supplierName',
      width: 130,
      render: (v?: string) => v || '-'
    },
    {
      title: '订单号',
      dataIndex: 'orderNo',
      width: 150,
      render: (v?: string) => v || '-'
    },
    {
      title: '物料',
      dataIndex: 'materialName',
      width: 110,
      render: (v: string, row) => v || row.materialCode || '-'
    },
    {
      title: '暂估期间',
      dataIndex: 'accrualPeriod',
      width: 100
    },
    {
      title: '暂估日期',
      dataIndex: 'accrualDate',
      width: 120,
      render: (v: string) => new Date(v).toLocaleDateString('zh-CN')
    },
    {
      title: '暂估数量',
      dataIndex: 'accrualQty',
      width: 100,
      align: 'right'
    },
    {
      title: '暂估单价',
      dataIndex: 'accrualPrice',
      width: 120,
      align: 'right',
      render: (v: number) => money(v)
    },
    {
      title: '暂估金额',
      dataIndex: 'accrualAmount',
      width: 130,
      align: 'right',
      render: (v: number) => money(v)
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status: string) => (
        <Tag color={status === 'ACCRUED' ? 'orange' : 'green'}>
          {status === 'ACCRUED' ? '已暂估' : '已回冲'}
        </Tag>
      )
    },
    {
      title: '回冲日期',
      dataIndex: 'reverseDate',
      width: 120,
      render: (v?: string) => (v ? new Date(v).toLocaleDateString('zh-CN') : '-')
    },
    {
      title: '回冲金额',
      dataIndex: 'reverseAmount',
      width: 130,
      align: 'right',
      render: (v?: number) => (v ? money(v) : '-')
    },
    {
      title: '差额',
      dataIndex: 'diffAmount',
      width: 120,
      align: 'right',
      render: (v?: number) => (
        <span className={v && v > 0 ? 'text-red-500' : ''}>{v ? money(v) : '-'}</span>
      )
    },
    {
      title: '回冲方式',
      dataIndex: 'accrualMode',
      width: 100,
      render: (mode: string) => (mode === 'SINGLE' ? '单到回冲' : '月初回冲')
    }
  ]

  return (
    <div className="flex flex-col gap-5">
      <PageCard>
        <div className="flex flex-wrap items-end gap-4">
          <div className="flex flex-col gap-1">
            <span className="text-xs text-ink-muted">暂估期间</span>
            <DatePicker
              picker="month"
              onChange={(date) => setPeriod(date ? date.format('YYYY-MM') : undefined)}
              style={{ width: 160 }}
            />
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-xs text-ink-muted">状态</span>
            <Select
              placeholder="全部"
              allowClear
              value={status}
              onChange={setStatus}
              options={[
                { value: 'ACCRUED', label: '已暂估' },
                { value: 'REVERSED', label: '已回冲' }
              ]}
              style={{ width: 120 }}
            />
          </div>
          <Space>
            <Button
              type="primary"
              loading={monthlyAccrueMutation.isPending}
              onClick={handleMonthlyAccrue}
            >
              月末批量暂估
            </Button>
            <Button
              loading={monthlyReverseMutation.isPending}
              onClick={handleMonthlyReverse}
            >
              月初批量回冲
            </Button>
          </Space>
        </div>
      </PageCard>

      <PageCard title="暂估应付列表">
        <Table<AccrualItem>
          rowKey="id"
          columns={columns}
          dataSource={accruals}
          loading={isFetching}
          size="middle"
          pagination={false}
          scroll={{ x: 1700 }}
        />
      </PageCard>
    </div>
  )
}
