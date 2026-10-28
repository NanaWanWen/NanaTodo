import type { ReactNode } from 'react'
import {
  DashboardOutlined,
  FileTextOutlined,
  FileAddOutlined,
  CheckSquareOutlined,
  InboxOutlined,
  FileProtectOutlined,
  PayCircleOutlined,
  SafetyOutlined,
  AuditOutlined,
  CalculatorOutlined,
  SettingOutlined,
  ToolOutlined,
  RobotOutlined,
  AlertOutlined,
  DatabaseOutlined,
  BarChartOutlined
} from '@ant-design/icons'

export interface RouteMeta {
  title: string
  icon?: ReactNode
}

/** 路由 -> 页签标题（与 src/App.tsx 路由表保持一致） */
export const ROUTE_META: Record<string, RouteMeta> = {
  '/workbench': { title: '工作台' },
  '/orders': { title: '采购订单' },
  '/orders/create': { title: '新建订单' },
  '/approvals': { title: '我的待办' },
  '/receipts': { title: '收货入库' },
  '/invoices': { title: '采购发票' },
  '/payments': { title: '付款单' },
  '/inventory': { title: '库存管理' },
  '/ap-settlements': { title: '应付对账' },
  '/ap-accruals': { title: '暂估与回冲' },
  '/ap-match-rules': { title: '匹配规则配置' },
  '/ap-diff-workbench': { title: '差异处理工作台' },
  '/agent-chat': { title: '智能对话' },
  '/agent-alerts': { title: '预警中心' },
  '/alert-rules': { title: '预警规则配置' },
  '/base': { title: '基础数据' },
  '/reports': { title: '报表统计' }
}

/** 由 pathname 推导页签元信息，兼容动态路由（/orders/:id 等） */
export function getTabMeta(pathname: string): RouteMeta {
  if (ROUTE_META[pathname]) return ROUTE_META[pathname]
  if (pathname.startsWith('/orders/')) return { title: '订单详情' }
  const base = '/' + pathname.split('/').slice(1, 3).join('/')
  if (ROUTE_META[base]) return ROUTE_META[base]
  return { title: '页面' }
}
