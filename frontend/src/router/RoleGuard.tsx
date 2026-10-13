import { Navigate } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useAuthStore } from '../store/useAuthStore'
import type { RoleCode } from '../types'

interface Props {
  roles?: RoleCode[]
  children: ReactNode
}

/**
 * 路由守卫：未登录跳登录页，角色不匹配跳无权限页
 */
export function RoleGuard({ roles, children }: Props) {
  const { userId, currentRole } = useAuthStore()

  if (!userId) {
    return <Navigate to="/login" replace />
  }
  if (roles && roles.length > 0 && currentRole && !roles.includes(currentRole)) {
    return <Navigate to="/403" replace />
  }
  return <>{children}</>
}
