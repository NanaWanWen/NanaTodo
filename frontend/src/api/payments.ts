import { newIdempotencyKey, request, unwrap } from './request'
import type { PageResult, PaymentCreatePayload, PaymentListItem, Result } from '../types'

export interface PaymentQueryParams {
  keyword?: string
  status?: string
  supplierId?: number
  orderId?: number
  pageNo?: number
  pageSize?: number
}

export const paymentApi = {
  page: (params: PaymentQueryParams): Promise<PageResult<PaymentListItem>> =>
    unwrap(request.get<Result<PageResult<PaymentListItem>>>('/payments', { params })),

  create: (payload: PaymentCreatePayload): Promise<number> =>
    unwrap(request.post<Result<number>>('/payments', payload, {
      headers: { 'Idempotency-Key': newIdempotencyKey() }
    })),

  confirm: (id: number): Promise<void> =>
    unwrap(request.post<Result<void>>(`/payments/${id}/actions/confirm`, {}, {
      headers: { 'Idempotency-Key': newIdempotencyKey() }
    })),

  reject: (id: number): Promise<void> =>
    unwrap(request.post<Result<void>>(`/payments/${id}/actions/reject`, {}, {
      headers: { 'Idempotency-Key': newIdempotencyKey() }
    }))
}
