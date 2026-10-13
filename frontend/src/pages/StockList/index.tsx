import { useState } from 'react'
import { App as AntdApp, Button, InputNumber, Modal, Space, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { inventoryApi } from '../../api/inventory'
import { PageCard } from '../../components/PageCard'
import type { InventoryItem } from '../../types'

/**
 * 库存管理：物料级库存列表 + 低库存预警高亮 + 安全库存编辑
 */
export function StockListPage() {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()

  const [editing, setEditing] = useState<InventoryItem | null>(null)
  const [safetyStock, setSafetyStock] = useState<number | undefined>()

  const { data: stockList = [], isFetching } = useQuery({
    queryKey: ['inventory'],
    queryFn: inventoryApi.listAll
  })

  const { data: alerts = [] } = useQuery({
    queryKey: ['inventory-alerts'],
    queryFn: inventoryApi.alerts
  })

  const lowStockIds = new Set(alerts.map((a: InventoryItem) => a.materialId))

  const saveMutation = useMutation({
    mutationFn: () => inventoryApi.updateSafetyStock(editing!.id, safetyStock!),
    onSuccess: () => {
      message.success('安全库存已更新')
      setEditing(null)
      setSafetyStock(undefined)
      queryClient.invalidateQueries({ queryKey: ['inventory'] })
    },
    onError: (error: Error) => message.error(error.message)
  })

  const columns: ColumnsType<InventoryItem> = [
    { title: '物料编码', dataIndex: 'materialCode', width: 140 },
    { title: '物料名称', dataIndex: 'materialName', width: 180 },
    { title: '仓库', dataIndex: 'warehouse', width: 100 },
    {
      title: '当前库存',
      dataIndex: 'quantity',
      width: 110,
      align: 'right',
      render: (v: number, row) => (
        <span className={row.quantity <= row.safetyStock ? 'font-medium text-[#FF4D4F]' : ''}>
          {Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 0 })}
        </span>
      )
    },
    {
      title: '安全库存',
      dataIndex: 'safetyStock',
      width: 110,
      align: 'right',
      render: (v: number) => Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 0 })
    },
    {
      title: '状态',
      width: 110,
      render: (_v, record) =>
        record.quantity <= record.safetyStock ? (
          <Tag color="error">库存预警</Tag>
        ) : (
          <Tag color="success">正常</Tag>
        )
    },
    { title: '最后更新', dataIndex: 'lastUpdateTime', width: 160 },
    {
      title: '操作',
      width: 100,
      render: (_v, record) => (
        <Button
          type="link"
          size="small"
          onClick={() => {
            setEditing(record)
            setSafetyStock(record.safetyStock)
          }}
        >
          编辑安全库存
        </Button>
      )
    }
  ]

  return (
    <div className="flex flex-col gap-5">
      <PageCard>
        <div className="flex items-center justify-between">
          <div>
            <span className="text-xs text-ink-muted">库存管理 · 共 {stockList.length} 个物料</span>
          </div>
          <Space>
            {alerts.length > 0 && (
              <Tag color="warning">
                {alerts.length} 个物料低于安全水位
              </Tag>
            )}
            <Button onClick={() => queryClient.invalidateQueries({ queryKey: ['inventory'] })}>
              刷新
            </Button>
          </Space>
        </div>

        {alerts.length > 0 && (
          <div className="mt-4 rounded-md border border-[#FAAD14]/40 bg-[#FAAD14]/10 p-3 text-sm">
            <div className="mb-1 font-medium text-[#FAAD14]">库存预警</div>
            <div className="text-ink-muted">
              {alerts
                .map((a) => `${a.materialName}（当前 ${a.quantity} / 安全 ${a.safetyStock}）`)
                .join('；')}
            </div>
          </div>
        )}
      </PageCard>

      <PageCard title="物料库存">
        <Table<InventoryItem>
          rowKey="id"
          columns={columns}
          dataSource={stockList}
          loading={isFetching}
          size="middle"
          pagination={false}
          scroll={{ x: 900 }}
        />
      </PageCard>

      <Modal
        title={`编辑安全库存 · ${editing?.materialName ?? ''}`}
        open={!!editing}
        onCancel={() => {
          setEditing(null)
          setSafetyStock(undefined)
        }}
        onOk={() => saveMutation.mutate()}
        confirmLoading={saveMutation.isPending}
        okButtonProps={{ disabled: safetyStock === undefined || safetyStock < 0 }}
      >
        <div className="flex flex-col gap-4">
          <div className="text-xs text-ink-muted">
            当前库存 {editing?.quantity ?? 0}，修改安全库存将触发预警刷新
          </div>
          <div>
            <label className="text-xs text-ink-muted">安全库存数量</label>
            <InputNumber
              style={{ width: '100%', marginTop: 4 }}
              min={0}
              value={safetyStock}
              onChange={(value) => setSafetyStock(value ?? undefined)}
            />
          </div>
        </div>
      </Modal>
    </div>
  )
}
