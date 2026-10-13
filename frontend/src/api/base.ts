import { request, unwrap } from './request'
import type { DictItem, MaterialItem, Result, SupplierItem, UserBrief } from '../types'

export const baseApi = {
  suppliers: (keyword?: string, status?: number): Promise<SupplierItem[]> =>
    unwrap(request.get<Result<SupplierItem[]>>('/suppliers', { params: { keyword, status } })),

  createSupplier: (payload: Partial<SupplierItem>): Promise<number> =>
    unwrap(request.post<Result<number>>('/suppliers', payload)),

  updateSupplier: (id: number, payload: Partial<SupplierItem>): Promise<number> =>
    unwrap(request.put<Result<number>>(`/suppliers/${id}`, payload)),

  deleteSupplier: (id: number): Promise<void> =>
    unwrap(request.delete<Result<void>>(`/suppliers/${id}`)),

  materials: (keyword?: string, category?: string): Promise<MaterialItem[]> =>
    unwrap(request.get<Result<MaterialItem[]>>('/materials', { params: { keyword, category } })),

  createMaterial: (payload: Partial<MaterialItem>): Promise<number> =>
    unwrap(request.post<Result<number>>('/materials', payload)),

  updateMaterial: (id: number, payload: Partial<MaterialItem>): Promise<number> =>
    unwrap(request.put<Result<number>>(`/materials/${id}`, payload)),

  deleteMaterial: (id: number): Promise<void> =>
    unwrap(request.delete<Result<void>>(`/materials/${id}`)),

  dict: (type: string): Promise<DictItem[]> =>
    unwrap(request.get<Result<DictItem[]>>(`/dict/${type}`)),

  users: (): Promise<UserBrief[]> => unwrap(request.get<Result<UserBrief[]>>('/users')),

  assignRoles: (userId: number, roleIds: number[]): Promise<void> =>
    unwrap(request.put<Result<void>>(`/users/${userId}/roles`, { roleIds }))
}
