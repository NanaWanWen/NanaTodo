import { useMemo, useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Avatar, Badge, Dropdown, Layout, Menu, Typography } from 'antd'
import type { MenuProps } from 'antd'
import {
  FileTextOutlined,
  CheckSquareOutlined,
  DatabaseOutlined,
  BarChartOutlined,
  InboxOutlined,
  FileProtectOutlined,
  PayCircleOutlined,
  SafetyOutlined,
  AuditOutlined,
  RobotOutlined,
  AlertOutlined
} from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { approvalApi } from '../api/approvals'
import { authApi } from '../api/auth'
import { useAuthStore } from '../store/useAuthStore'
import type { RoleCode } from '../types'

const { Header, Sider, Content } = Layout

const ROLE_NAME: Record<RoleCode, string> = {
  ADMIN: '管理员',
  BUYER: '采购员',
  PURCHASE_MANAGER: '采购经理',
  DIRECTOR: '总监'
}

/**
 * 主框架：固定顶栏 + 可折叠侧边菜单 + 内容区
 */
export function MainLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const [collapsed, setCollapsed] = useState(false)

  const { realName, username, roles, currentRole, userId, setRole, clear } = useAuthStore()

  const isApprover = currentRole === 'PURCHASE_MANAGER' || currentRole === 'DIRECTOR'
  const isBuyerOrAdmin = currentRole === 'BUYER' || currentRole === 'ADMIN'

  const { data: todoCount = 0 } = useQuery({
    queryKey: ['todo-count', currentRole],
    queryFn: () => approvalApi.todoCount(),
    enabled: isApprover
  })

  const menuItems = useMemo(() => {
    const items: MenuProps['items'] = [
      { key: '/orders', icon: <FileTextOutlined />, label: '采购订单' }
    ]
    if (isApprover) {
      items.push({
        key: '/approvals',
        icon: <CheckSquareOutlined />,
        label: (
          <span className="flex items-center gap-2">
            我的待办
            {todoCount > 0 && <Badge count={todoCount} size="small" />}
          </span>
        )
      })
    }
    if (isBuyerOrAdmin) {
      items.push(
        { key: '/receipts', icon: <InboxOutlined />, label: '收货入库' },
        { key: '/invoices', icon: <FileProtectOutlined />, label: '采购发票' },
        { key: '/payments', icon: <PayCircleOutlined />, label: '付款单' },
        { key: '/inventory', icon: <SafetyOutlined />, label: '库存管理' },
        { key: '/ap-settlements', icon: <AuditOutlined />, label: '应付对账' }
      )
    }
    // AI Agent 入口（所有角色可用：只读报表 + 预警）
    items.push({
      type: 'group',
      label: 'AI Agent',
      key: 'agent-group',
      children: [
        { key: '/agent-chat', icon: <RobotOutlined />, label: '智能对话' },
        { key: '/agent-alerts', icon: <AlertOutlined />, label: '预警中心' }
      ]
    })
    items.push({ key: '/base', icon: <DatabaseOutlined />, label: '基础数据' })
    items.push({ key: '/reports', icon: <BarChartOutlined />, label: '报表统计' })
    return items
  }, [isApprover, isBuyerOrAdmin, todoCount])

  const selectedKey = '/' + (location.pathname.split('/')[1] ?? '')

  const handleSwitchRole = async (role: RoleCode) => {
    if (!userId || role === currentRole) {
      return
    }
    const result = await authApi.switchRole(userId, role)
    setRole(result.currentRole)
    navigate('/orders')
  }

  const handleLogout = async () => {
    if (userId) {
      await authApi.logout(userId)
    }
    clear()
    navigate('/login')
  }

  return (
    <Layout className="min-h-screen">
      <Header className="app-header fixed inset-x-0 top-0 z-20 flex h-16 items-center justify-between px-6 backdrop-blur">
        <div className="flex items-center gap-3">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-white/20 text-base font-semibold text-white">
            PO
          </div>
          <Typography.Title level={5} className="!mb-0 !text-white">
            采购订单管理系统
          </Typography.Title>
          <span className="rounded bg-white/15 px-2 py-0.5 text-xs text-white/90">练习项目</span>
        </div>

        <div className="flex items-center gap-4">
          <Dropdown
            menu={{
              items: roles.map((role) => ({
                key: role,
                label: ROLE_NAME[role],
                disabled: role === currentRole
              })),
              onClick: ({ key }) => handleSwitchRole(key as RoleCode)
            }}
          >
            <button className="cursor-pointer rounded-md bg-white/15 px-3 py-1.5 text-sm text-white transition-160 hover:bg-white/25">
              当前角色：{currentRole ? ROLE_NAME[currentRole] : '未选择'} ▾
            </button>
          </Dropdown>

          <div className="flex items-center gap-2 text-white">
            <Avatar size="small" className="!bg-white/25">
              {realName?.slice(0, 1) ?? 'U'}
            </Avatar>
            <div className="flex flex-col leading-tight">
              <span className="text-sm">{realName || username}</span>
              <span className="text-xs text-white/70">{username}</span>
            </div>
          </div>

          <button
            onClick={handleLogout}
            className="cursor-pointer rounded-md border border-white/30 px-3 py-1.5 text-sm text-white transition-160 hover:bg-white/15"
          >
            退出
          </button>
        </div>
      </Header>

      <Layout className="pt-16">
        <Sider
          collapsible
          collapsed={collapsed}
          onCollapse={setCollapsed}
          theme="dark"
          width={200}
          className="!fixed inset-y-16 left-0 overflow-auto"
        >
          <Menu
            theme="dark"
            mode="inline"
            selectedKeys={[selectedKey]}
            items={menuItems}
            onClick={({ key }) => navigate(key)}
          />
        </Sider>

        <Layout className="bg-surface-page" style={{ marginLeft: collapsed ? 80 : 200 }}>
          <Content className="min-h-[calc(100vh-64px)] p-6">
            <div className="page-enter">
              <Outlet />
            </div>
          </Content>
        </Layout>
      </Layout>
    </Layout>
  )
}
