import { useState } from 'react'
import { App as AntdApp, Button, Modal, Select, Space, Table, Tag } from 'antd'
import type { TableProps } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { baseApi } from '../../api/base'
import { authApi } from '../../api/auth'
import type { RoleCode, UserBrief } from '../../types'

interface Props {
  readOnly: boolean
}

const ROLE_NAME: Record<RoleCode, string> = {
  ADMIN: '管理员',
  BUYER: '采购员',
  PURCHASE_MANAGER: '采购经理',
  DIRECTOR: '总监'
}

/**
 * 用户与角色（仅管理员可分配角色）
 */
export function UserRolePanel({ readOnly }: Props) {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()

  const [editing, setEditing] = useState<UserBrief | null>(null)
  const [roleIds, setRoleIds] = useState<number[]>([])

  const { data: users = [], isLoading } = useQuery({
    queryKey: ['users'],
    queryFn: () => (readOnly ? authApi.users() : baseApi.users())
  })

  const { data: roles = [] } = useQuery({
    queryKey: ['roles'],
    queryFn: () => authApi.roles()
  })

  const assignMutation = useMutation({
    mutationFn: () => baseApi.assignRoles(editing!.id, roleIds),
    onSuccess: () => {
      message.success('角色已更新，相关会话缓存已清除')
      setEditing(null)
      queryClient.invalidateQueries({ queryKey: ['users'] })
      queryClient.invalidateQueries({ queryKey: ['auth-users'] })
    },
    onError: (error: Error) => message.error(error.message)
  })

  const openAssign = (record: UserBrief) => {
    setEditing(record)
    setRoleIds(
      roles.filter((role) => record.roles.includes(role.roleCode)).map((role) => role.id)
    )
  }

  const columns: TableProps<UserBrief>['columns'] = [
    { title: '用户名', dataIndex: 'username', width: 140 },
    { title: '姓名', dataIndex: 'realName', width: 120 },
    {
      title: '角色',
      dataIndex: 'roles',
      render: (values: RoleCode[]) => (
        <Space size={4}>
          {values.map((role) => (
            <Tag key={role} color={role === 'ADMIN' ? 'gold' : 'blue'}>
              {ROLE_NAME[role]}
            </Tag>
          ))}
        </Space>
      )
    },
    ...(readOnly
      ? []
      : [
          {
            title: '操作',
            key: 'action',
            width: 120,
            render: (_: unknown, record: UserBrief) => (
              <Button type="link" size="small" onClick={() => openAssign(record)}>
                分配角色
              </Button>
            )
          }
        ])
  ]

  return (
    <div className="flex flex-col gap-4">
      <Table rowKey="id" size="small" loading={isLoading} columns={columns} dataSource={users} pagination={false} />

      <Modal
        title={`分配角色 - ${editing?.realName ?? ''}`}
        open={Boolean(editing)}
        okText="保存"
        cancelText="取消"
        onCancel={() => setEditing(null)}
        onOk={() => assignMutation.mutate()}
      >
        <Select
          mode="multiple"
          className="w-full"
          placeholder="请选择角色"
          value={roleIds}
          onChange={setRoleIds}
          options={roles.map((role) => ({ value: role.id, label: role.roleName }))}
        />
      </Modal>
    </div>
  )
}
