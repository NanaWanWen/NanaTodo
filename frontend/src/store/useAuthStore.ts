import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { RoleCode } from '../types'
import { useTabsStore } from './useTabsStore'

interface AuthState {
  userId: number | null
  username: string
  realName: string
  roles: RoleCode[]
  currentRole: RoleCode | null
  setUser: (payload: {
    userId: number
    username: string
    realName: string
    roles: RoleCode[]
    currentRole: RoleCode
  }) => void
  setRole: (role: RoleCode) => void
  clear: () => void
}

/**
 * 当前登录用户与角色（简化鉴权：选择用户即登录，可切换角色）
 * 登录 / 切换角色 / 退出时联动清空全局页签，回到工作台
 */
export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      userId: null,
      username: '',
      realName: '',
      roles: [],
      currentRole: null,
      setUser: (payload) => {
        useTabsStore.getState().closeAll()
        set({ ...payload })
      },
      setRole: (role) => {
        useTabsStore.getState().closeAll()
        set({ currentRole: role })
      },
      clear: () => {
        useTabsStore.getState().closeAll()
        set({ userId: null, username: '', realName: '', roles: [], currentRole: null })
      }
    }),
    { name: 'yonerp-auth' }
  )
)
