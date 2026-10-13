import { request, unwrap } from './request'
import type { LoginResult, Result, RoleItem, UserBrief } from '../types'
import type { RoleCode } from '../types'

export const authApi = {
  users: (): Promise<UserBrief[]> =>
    unwrap(request.get<Result<UserBrief[]>>('/auth/users')),

  roles: (): Promise<RoleItem[]> =>
    unwrap(request.get<Result<RoleItem[]>>('/auth/roles')),

  login: (userId: number): Promise<LoginResult> =>
    unwrap(request.post<Result<LoginResult>>('/auth/login', { userId })),

  switchRole: (userId: number, roleCode: RoleCode): Promise<LoginResult> =>
    unwrap(request.post<Result<LoginResult>>('/auth/switch-role', { userId, roleCode })),

  logout: (userId: number): Promise<void> =>
    unwrap(request.post<Result<void>>('/auth/logout', { userId }))
}
