import { useState } from 'react'
import { App as AntdApp, Button, Drawer, Input, Space, Table, Tag } from 'antd'
import type { TableProps } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { baseApi } from '../../api/base'
import type { SupplierItem } from '../../types'

interface Props {
  readOnly: boolean
}

/**
 * 供应商维护（仅管理员可写，写操作触发后端缓存失效）
 */
export function SupplierPanel({ readOnly }: Props) {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()

  const [keyword, setKeyword] = useState('')
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<SupplierItem | null>(null)
  const [form, setForm] = useState<Partial<SupplierItem>>({})

  const { data = [], isLoading } = useQuery({
    queryKey: ['suppliers', keyword],
    queryFn: () => baseApi.suppliers(keyword || undefined)
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['suppliers'] })
    queryClient.invalidateQueries({ queryKey: ['orders'] })
  }

  const saveMutation = useMutation({
    mutationFn: () =>
      editing
        ? baseApi.updateSupplier(editing.id, form)
        : baseApi.createSupplier(form),
    onSuccess: () => {
      message.success('保存成功')
      setOpen(false)
      invalidate()
    },
    onError: (error: Error) => message.error(error.message)
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => baseApi.deleteSupplier(id),
    onSuccess: () => {
      message.success('已删除')
      invalidate()
    },
    onError: (error: Error) => message.error(error.message)
  })

  const openCreate = () => {
    setEditing(null)
    setForm({ status: 1 })
    setOpen(true)
  }

  const openEdit = (record: SupplierItem) => {
    setEditing(record)
    setForm({ ...record })
    setOpen(true)
  }

  const columns: TableProps<SupplierItem>['columns'] = [
    { title: '编码', dataIndex: 'supplierCode', width: 120 },
    { title: '名称', dataIndex: 'supplierName', width: 220 },
    { title: '联系人', dataIndex: 'contactName', width: 100 },
    { title: '联系电话', dataIndex: 'contactPhone', width: 130 },
    { title: '地址', dataIndex: 'address', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: number) =>
        value === 1 ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>
    },
    ...(readOnly
      ? []
      : [
          {
            title: '操作',
            key: 'action',
            width: 120,
            render: (_: unknown, record: SupplierItem) => (
              <Space size={4}>
                <Button type="link" size="small" onClick={() => openEdit(record)}>
                  编辑
                </Button>
                <Button
                  type="link"
                  size="small"
                  danger
                  onClick={() => deleteMutation.mutate(record.id)}
                >
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
        <Input.Search
          allowClear
          className="w-72"
          placeholder="编码或名称"
          onSearch={setKeyword}
        />
        {!readOnly && (
          <Button type="primary" onClick={openCreate}>
            新增供应商
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
        title={editing ? '编辑供应商' : '新增供应商'}
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
            <span className="text-sm text-ink-muted">供应商编码</span>
            <Input
              value={form.supplierCode ?? ''}
              onChange={(event) => setForm({ ...form, supplierCode: event.target.value })}
            />
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-sm text-ink-muted">供应商名称</span>
            <Input
              value={form.supplierName ?? ''}
              onChange={(event) => setForm({ ...form, supplierName: event.target.value })}
            />
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-sm text-ink-muted">联系人</span>
            <Input
              value={form.contactName ?? ''}
              onChange={(event) => setForm({ ...form, contactName: event.target.value })}
            />
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-sm text-ink-muted">联系电话</span>
            <Input
              value={form.contactPhone ?? ''}
              onChange={(event) => setForm({ ...form, contactPhone: event.target.value })}
            />
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-sm text-ink-muted">地址</span>
            <Input.TextArea
              rows={2}
              value={form.address ?? ''}
              onChange={(event) => setForm({ ...form, address: event.target.value })}
            />
          </div>
        </div>
      </Drawer>
    </div>
  )
}
