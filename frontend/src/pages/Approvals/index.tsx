import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntdApp, Button, Empty, Input, Modal, Space, Table, Tag } from 'antd'
import type { TableProps } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { approvalApi } from '../../api/approvals'
import { PageCard } from '../../components/PageCard'
import { MoneyText } from '../../components/MoneyText'
import type { ApprovalTodoItem } from '../../types'

/**
 * 我的待办：按当前角色过滤审批节点
 */
export function ApprovalsPage() {
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()

  const [keyword, setKeyword] = useState('')
  const [pageNo, setPageNo] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [opinion, setOpinion] = useState('')
  const [rejectTask, setRejectTask] = useState<ApprovalTodoItem | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['todo', keyword, pageNo, pageSize],
    queryFn: () => approvalApi.todo(keyword || undefined, pageNo, pageSize)
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['todo'] })
    queryClient.invalidateQueries({ queryKey: ['todo-count'] })
    queryClient.invalidateQueries({ queryKey: ['orders'] })
    queryClient.invalidateQueries({ queryKey: ['reports'] })
  }

  const approveMutation = useMutation({
    mutationFn: (taskId: number) => approvalApi.approve(taskId, opinion || '同意'),
    onSuccess: () => {
      message.success('审批通过')
      setOpinion('')
      invalidate()
    },
    onError: (error: Error) => message.error(error.message)
  })

  const rejectMutation = useMutation({
    mutationFn: (taskId: number) => approvalApi.reject(taskId, opinion),
    onSuccess: () => {
      message.success('已驳回')
      setOpinion('')
      setRejectTask(null)
      invalidate()
    },
    onError: (error: Error) => message.error(error.message)
  })

  const columns: TableProps<ApprovalTodoItem>['columns'] = [
    { title: '订单编号', dataIndex: 'orderNo', width: 160 },
    { title: '标题', dataIndex: 'title', ellipsis: true },
    { title: '采购员', dataIndex: 'buyerName', width: 100 },
    {
      title: '金额',
      dataIndex: 'totalAmount',
      width: 140,
      align: 'right' as const,
      render: (value: number) => <MoneyText value={value} />
    },
    {
      title: '节点',
      dataIndex: 'nodeName',
      width: 130,
      render: (value: string) => <Tag color="blue">{value}</Tag>
    },
    { title: '提交时间', dataIndex: 'submitTime', width: 170 },
    {
      title: '超时',
      dataIndex: 'overtime',
      width: 80,
      render: (value: boolean) =>
        value ? <Tag color="red">已超时</Tag> : <span className="text-ink-muted">-</span>
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      fixed: 'right' as const,
      render: (_: unknown, record: ApprovalTodoItem) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => navigate(`/orders/${record.orderId}`)}>
            查看
          </Button>
          <Button
            type="link"
            size="small"
            loading={approveMutation.isPending}
            onClick={() => approveMutation.mutate(record.taskId)}
          >
            同意
          </Button>
          <Button
            type="link"
            size="small"
            danger
            onClick={() => setRejectTask(record)}
          >
            驳回
          </Button>
        </Space>
      )
    }
  ]

  const records = data?.records ?? []

  return (
    <div className="flex flex-col gap-5">
      <PageCard>
        <div className="flex flex-wrap items-center gap-4">
          <Input.Search
            allowClear
            className="w-72"
            placeholder="订单编号 / 标题"
            onSearch={(value) => {
              setKeyword(value)
              setPageNo(1)
            }}
          />
          <span className="text-sm text-ink-muted">
            共 {data?.total ?? 0} 条待办，审批意见可在下方统一填写
          </span>
        </div>
      </PageCard>

      <PageCard title="待我审批">
        {records.length === 0 && !isLoading ? (
          <Empty description="暂无待审批单据" />
        ) : (
          <Table
            rowKey="taskId"
            loading={isLoading}
            columns={columns}
            dataSource={records}
            scroll={{ x: 1100 }}
            pagination={{
              current: pageNo,
              pageSize,
              total: data?.total ?? 0,
              showSizeChanger: true,
              onChange: (page, size) => {
                setPageNo(page)
                setPageSize(size)
              }
            }}
          />
        )}
      </PageCard>

      <Modal
        title="驳回确认"
        open={Boolean(rejectTask)}
        okText="确认驳回"
        cancelText="取消"
        okButtonProps={{ danger: true }}
        onCancel={() => setRejectTask(null)}
        onOk={() => {
          if (!opinion.trim()) {
            message.warning('驳回意见不能为空')
            return
          }
          if (rejectTask) {
            rejectMutation.mutate(rejectTask.taskId)
          }
        }}
      >
        <p className="mb-2 text-sm text-ink-muted">
          单据：{rejectTask?.orderNo} {rejectTask?.title}
        </p>
        <Input.TextArea
          rows={3}
          maxLength={500}
          placeholder="驳回意见（必填）"
          value={opinion}
          onChange={(event) => setOpinion(event.target.value)}
        />
      </Modal>
    </div>
  )
}
