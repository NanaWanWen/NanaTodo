import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntdApp, Button, Card, Select, Tag, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { authApi } from '../../api/auth'
import { useAuthStore } from '../../store/useAuthStore'
import type { RoleCode } from '../../types'

const ROLE_NAME: Record<RoleCode, string> = {
  ADMIN: '管理员',
  BUYER: '采购员',
  PURCHASE_MANAGER: '采购经理',
  DIRECTOR: '总监'
}

const ROLE_MODULES: Record<RoleCode, string[]> = {
  ADMIN: ['采购订单（全部数据，可增删改）', '基础数据维护', '用户与角色管理', '报表统计'],
  BUYER: ['采购订单（仅本人，可新建/编辑/提交）', '基础数据（只读）', '报表统计（本人数据）'],
  PURCHASE_MANAGER: ['我的待办（一级审批）', '采购订单（只读）', '报表统计'],
  DIRECTOR: ['我的待办（二级审批）', '采购订单（只读）', '报表统计']
}

/**
 * 登录页：选择用户 + 选择角色（简化鉴权，会话写入 Redis）
 */
export function LoginPage() {
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const setUser = useAuthStore((state) => state.setUser)

  const [userId, setUserId] = useState<number | null>(null)
  const [role, setRole] = useState<RoleCode | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const { data: users = [] } = useQuery({
    queryKey: ['auth-users'],
    queryFn: () => authApi.users()
  })

  const currentUser = useMemo(
    () => users.find((item) => item.id === userId) ?? null,
    [users, userId]
  )

  useEffect(() => {
    setRole(currentUser?.roles?.[0] ?? null)
  }, [currentUser])

  const handleEnter = async () => {
    if (!userId || !role) {
      message.warning('请选择用户与角色')
      return
    }
    setSubmitting(true)
    const result = await authApi
      .login(userId)
      .then((login) => authApi.switchRole(userId, role).then((switched) => switched ?? login))
      .catch((error: Error) => {
        message.error(error.message)
        return null
      })
    setSubmitting(false)
    if (!result) {
      return
    }
    setUser({
      userId: result.userId,
      username: result.username,
      realName: result.realName,
      roles: result.roles,
      currentRole: result.currentRole
    })
    message.success(`欢迎，${result.realName}`)
    navigate('/orders', { replace: true })
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-[#1C2B45] via-[#244063] to-[#1677FF] p-8">
      <div className="grid w-full max-w-4xl gap-6 md:grid-cols-2">
        <Card className="shadow-card" styles={{ body: { padding: 28 } }}>
          <Typography.Title level={4} className="!mb-1">
            采购订单管理系统
          </Typography.Title>
          <Typography.Text type="secondary">选择演示用户与角色进入系统</Typography.Text>

          <div className="mt-6 flex flex-col gap-4">
            <div>
              <div className="mb-2 text-sm text-ink-muted">用户</div>
              <Select
                className="w-full"
                placeholder="请选择用户"
                value={userId ?? undefined}
                onChange={(value) => setUserId(value)}
                options={users.map((item) => ({
                  value: item.id,
                  label: `${item.realName}（${item.username}）`
                }))}
              />
            </div>

            <div>
              <div className="mb-2 text-sm text-ink-muted">角色</div>
              <Select
                className="w-full"
                placeholder="请先选择用户"
                value={role ?? undefined}
                onChange={(value) => setRole(value)}
                options={(currentUser?.roles ?? []).map((item) => ({
                  value: item,
                  label: ROLE_NAME[item]
                }))}
              />
            </div>

            <Button
              type="primary"
              size="large"
              loading={submitting}
              onClick={handleEnter}
              className="mt-2"
            >
              进入系统
            </Button>
          </div>
        </Card>

        <Card className="shadow-card" styles={{ body: { padding: 28 } }}>
          <Typography.Title level={5} className="!mb-4">
            当前角色可访问模块
          </Typography.Title>
          {role ? (
            <div className="flex flex-col gap-3">
              <Tag color="blue">{ROLE_NAME[role]}</Tag>
              {ROLE_MODULES[role].map((item) => (
                <div
                  key={item}
                  className="rounded-lg border border-black/5 bg-surface-page px-4 py-3 text-sm text-ink transition-160 hover:border-primary/40 hover:bg-white"
                >
                  {item}
                </div>
              ))}
            </div>
          ) : (
            <Typography.Text type="secondary">请先选择用户与角色</Typography.Text>
          )}
        </Card>
      </div>
    </div>
  )
}
