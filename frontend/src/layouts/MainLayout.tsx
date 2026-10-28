import { useMemo, useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Avatar, Badge, Dropdown, Layout, Menu, Typography } from 'antd'
import type { MenuProps } from 'antd'
import {
  DashboardOutlined,
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
  AlertOutlined,
  SettingOutlined,
  CalculatorOutlined,
  ToolOutlined
} from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { approvalApi } from '../api/approvals'
import { authApi } from '../api/auth'
import { queryClient } from '../api/queryClient'
import { useAuthStore } from '../store/useAuthStore'
import { TabNav } from './TabNav'
import type { RoleCode } from '../types'

const { Header, Sider, Content } = Layout

const ROLE_NAME: Record<RoleCode, string> = {
  ADMIN: '管理员',
  BUYER: '采购员',
  PURCHASE_MANAGER: '采购经理',
  DIRECTOR: '总监'
}

/**
 * 主框架：BIP 风格固定顶栏 + 左侧功能菜单 + 全局多页签 + 内容区 + 右下角智能问答入口
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

  const { data: unreadAlertCount = 0 } = useQuery({
    queryKey: ['unread-alert-count', currentRole],
    queryFn: async () => {
      const res = await fetch('/api/agent/alerts/unread-count', {
        headers: { 'X-User-Id': String(userId), 'X-Role-Code': String(currentRole ?? '') }
      })
      const json = await res.json()
      return json.data ?? 0
    },
    enabled: !!userId
  })

  const menuItems = useMemo(() => {
    const items: MenuProps['items'] = [
      { key: '/workbench', icon: <DashboardOutlined />, label: '工作台' },
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
        { key: '/ap-settlements', icon: <AuditOutlined />, label: '应付对账' },
        { key: '/ap-accruals', icon: <CalculatorOutlined />, label: '暂估与回冲' },
        { key: '/ap-diff-workbench', icon: <ToolOutlined />, label: '差异处理工作台' },
        { key: '/ap-match-rules', icon: <SettingOutlined />, label: '匹配规则配置' }
      )
    }
    // AI Agent 入口（所有角色可用：只读报表 + 预警）
    items.push({
      type: 'group',
      label: 'AI Agent',
      key: 'agent-group',
      children: [
        { key: '/agent-chat', icon: <RobotOutlined />, label: '智能对话' },
        { key: '/agent-alerts', icon: <AlertOutlined />, label: '预警中心' },
        { key: '/alert-rules', icon: <SafetyOutlined />, label: '预警规则配置' }
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
    // 切换角色：清空缓存 + 页签，避免上一个角色的数据残留
    queryClient.clear()
    setRole(result.currentRole)
    navigate('/workbench')
  }

  const handleLogout = async () => {
    if (userId) {
      await authApi.logout(userId)
    }
    // 退出登录：清空缓存 + 页签，回到登录页
    queryClient.clear()
    clear()
    navigate('/login')
  }

  return (
    <Layout className="min-h-screen">
      <Header className="app-header fixed inset-x-0 top-0 z-30 flex h-16 items-center justify-between px-6 backdrop-blur">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-white/20 text-base font-semibold text-white shadow-sm">
            PO
          </div>
          <div>
            <Typography.Title level={5} className="!mb-0 !text-white">
              采购经营工作台
            </Typography.Title>
            <span className="text-xs text-white/70">YonErp · 用友 BIP 风格演示</span>
          </div>
        </div>

        <div className="flex items-center gap-4">
          <Badge count={unreadAlertCount} size="small" offset={[-4, 4]}>
            <button
              onClick={() => navigate('/agent-alerts')}
              className="cursor-pointer rounded-full bg-white/15 p-2 text-white transition-160 hover:bg-white/25"
            >
              <AlertOutlined />
            </button>
          </Badge>

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
          width={220}
          className="!fixed inset-y-16 left-0 overflow-auto"
        >
          <div className="bip-sider-brand">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-white/15 text-lg">
              <RobotOutlined />
            </div>
            <div className="ml-3">
              <div className="text-sm font-semibold text-white">AI 工作台</div>
              <div className="text-xs text-white/55">采购经营驾驶舱</div>
            </div>
          </div>
          <Menu
            theme="dark"
            mode="inline"
            selectedKeys={[selectedKey]}
            items={menuItems}
            onClick={({ key }) => navigate(key)}
          />
        </Sider>

        <Layout className="bip-content" style={{ marginLeft: collapsed ? 80 : 220 }}>
          <TabNav />
          <Content className="min-h-[calc(100vh-64px)] p-5">
            <div className="page-enter">
              <Outlet />
            </div>
            <button
              className="bip-ai-fab"
              onClick={() => navigate('/agent-chat')}
              aria-label="打开智能问答"
            >
              <span className="bip-ai-avatar">
                <RobotOutlined />
              </span>
              <span className="bip-ai-text">
                <span className="title">智能问答</span>
                <span className="desc">基于当前数据提问</span>
              </span>
            </button>
          </Content>
        </Layout>
      </Layout>
    </Layout>
  )
}
