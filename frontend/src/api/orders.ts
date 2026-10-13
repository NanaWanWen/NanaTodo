import { newIdempotencyKey, request, unwrap } from './request'
import type { OrderDetail, OrderListItem, OrderSavePayload, PageResult, Result } from '../types'

export interface OrderQueryParams {
  keyword?: string
  status?: string
  supplierId?: number
  buyerId?: number
  /** 部门ID（报表穿透联查） */
  deptId?: number
  /** 物料ID（报表穿透联查，按明细匹配） */
  materialId?: number
  startDate?: string
  endDate?: string
  pageNo?: number
  pageSize?: number
}

export const orderApi = {
  page: (params: OrderQueryParams): Promise<PageResult<OrderListItem>> =>
    unwrap(request.get<Result<PageResult<OrderListItem>>>('/orders', { params })),

  detail: (id: number): Promise<OrderDetail> =>
    unwrap(request.get<Result<OrderDetail>>(`/orders/${id}`)),

  create: (payload: OrderSavePayload): Promise<number> =>
    unwrap(request.post<Result<number>>('/orders', payload)),

  update: (id: number, payload: OrderSavePayload): Promise<void> =>
    unwrap(request.put<Result<void>>(`/orders/${id}`, payload)),

  remove: (id: number): Promise<void> =>
    unwrap(request.delete<Result<void>>(`/orders/${id}`)),

  removeBatch: (ids: number[]): Promise<void> =>
    unwrap(request.delete<Result<void>>('/orders/batch', { data: { ids } })),

  /**
   * 状态动作：submit / withdraw / cancel，带幂等键防重复提交
   */
  action: (
    id: number,
    action: 'submit' | 'withdraw' | 'cancel',
    payload: { remark?: string; version?: number } = {}
  ): Promise<OrderDetail> =>
    unwrap(
      request.post<Result<OrderDetail>>(`/orders/${id}/actions/${action}`, payload, {
        headers: { 'Idempotency-Key': newIdempotencyKey() }
      })
    )
}
