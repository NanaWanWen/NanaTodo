import { useState } from 'react'
import { App as AntdApp, Button, Drawer, Input, InputNumber, Space, Table, Tag } from 'antd'
import type { TableProps } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { baseApi } from '../../api/base'
import type { MaterialItem } from '../../types'

interface Props {
  readOnly: boolean
}

/**
 * 物料维护（仅管理员可写）
 */
export function MaterialPanel({ readOnly }: Props) {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()

  const [keyword, setKeyword] = useState('')
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<MaterialItem | null>(null)
  const [form, setForm] = useState<Partial<MaterialItem>>({})

  const { data = [], isLoading } = useQuery({
    queryKey: ['materials', keyword],
    queryFn: () => baseApi.materials(keyword || undefined)
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['materials'] })
    queryClient.invalidateQueries({ queryKey: ['order-detail'] })
  }

  const saveMutation = useMutation({
    mutationFn: () =>
      editing ? baseApi.updateMaterial(editing.id, form) : baseApi.createMaterial(form),
    onSuccess: () => {
      message.success('保存成功')
      setOpen(false)
      invalidate()
    },
    onError: (error: Error) => message.error(error.message)
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => baseApi.deleteMaterial(id),
    onSuccess: () => {
      message.success('已删除')
      invalidate()
    },
    onError: (error: Error) => message.error(error.message)
  })

  const openCreate = () => {
    setEditing(null)
    setForm({ status: 1, taxRate: 13, unit: '个' })
    setOpen(true)
  }

  const openEdit = (record: MaterialItem) => {
    setEditing(record)
    setForm({ ...record })
    setOpen(true)
  }

  const columns: TableProps<MaterialItem>['columns'] = [
    { title: '编码', dataIndex: 'materialCode', width: 110 },
    { title: '名称', dataIndex: 'materialName', width: 180 },
    { title: '规格', dataIndex: 'spec', width: 180, ellipsis: true },
    { title: '单位', dataIndex: 'unit', width: 70 },
    { title: '分类', dataIndex: 'category', width: 110 },
    {
      title: '参考单价',
      dataIndex: 'refPrice',
      width: 110,
      align: 'right' as const,
      render: (value: number) => Number(value ?? 0).toFixed(4)
    },
    {
      title: '税率(%)',
      dataIndex: 'taxRate',
      width: 90,
      align: 'right' as const,
      render: (value: number) => Number(value ?? 0).toFixed(2)
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: number) => (value === 1 ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>)
    },
    ...(readOnly
      ? []
      : [
          {
            title: '操作',
            key: 'action',
            width: 120,
            render: (_: unknown, record: MaterialItem) => (
              <Space size={4}>
                <Button type="link" size="small" onClick={() => openEdit(record)}>
                  编辑
                </Button>
                <Button type="link" size="small" danger onClick={() => deleteMutation.mutate(record.id)}>
                  删除
                </Button>
              </Space>
            )
          }
        ])
  ]

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <Input.Search allowClear className="w-72" placeholder="编码或名称" onSearch={setKeyword} />
        {!readOnly && (
          <Button type="primary" onClick={openCreate}>
            新增物料
          </Button>
        )}
      </div>

      <Table
        rowKey="id"
        size="small"
        loading={isLoading}
        columns={columns}
        dataSource={data}
        pagination={{ pageSize: 10 }}
      />

      <Drawer
        title={editing ? '编辑物料' : '新增物料'}
        open={open}
        width={420}
        onClose={() => setOpen(false)}
        footer={
          <div className="flex justify-end gap-2">
            <Button onClick={() => setOpen(false)}>取消</Button>
            <Button type="primary" loading={saveMutation.isPending} onClick={() => saveMutation.mutate()}>
              保存
            </Button>
          </div>
        }
      >
        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-1">
            <span className="text-sm text-ink-muted">物料编码</span>
            <Input
              value={form.materialCode ?? ''}
              onChange={(event) => setForm({ ...form, materialCode: event.target.value })}
            />
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-sm text-ink-muted">物料名称</span>
            <Input
              value={form.materialName ?? ''}
              onChange={(event) => setForm({ ...form, materialName: event.target.value })}
            />
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-sm text-ink-muted">规格型号</span>
            <Input
              value={form.spec ?? ''}
              onChange={(event) => setForm({ ...form, spec: event.target.value })}
            />
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-sm text-ink-muted">计量单位</span>
            <Input
              value={form.unit ?? ''}
              onChange={(event) => setForm({ ...form, unit: event.target.value })}
            />
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-sm text-ink-muted">分类</span>
            <Input
              value={form.category ?? ''}
              onChange={(event) => setForm({ ...form, category: event.target.value })}
            />
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-sm text-ink-muted">参考单价</span>
            <InputNumber
              className="w-full"
              min={0}
              precision={4}
              value={form.refPrice ?? 0}
              onChange={(value) => setForm({ ...form, refPrice: Number(value ?? 0) })}
            />
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-sm text-ink-muted">默认税率(%)</span>
            <InputNumber
              className="w-full"
              min={0}
              max={100}
              precision={2}
              value={form.taxRate ?? 13}
              onChange={(value) => setForm({ ...form, taxRate: Number(value ?? 0) })}
            />
          </div>
        </div>
      </Drawer>
    </div>
  )
}
