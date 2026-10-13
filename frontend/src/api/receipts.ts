import { newIdempotencyKey, request, unwrap } from './request'
import type { PageResult, ReceiptCreatePayload, ReceiptDetail, ReceiptListItem, Result } from '../types'

export interface ReceiptQueryParams {
  keyword?: string
  status?: string
  supplierId?: number
  orderId?: number
  pageNo?: number
  pageSize?: number
}

export const receiptApi = {
  page: (params: ReceiptQueryParams): Promise<PageResult<ReceiptListItem>> =>
    unwrap(request.get<Result<PageResult<ReceiptListItem>>>('/receipts', { params })),

  detail: (id: number): Promise<ReceiptDetail> =>
    unwrap(request.get<Result<ReceiptDetail>>(`/receipts/${id}`)),

  create: (payload: ReceiptCreatePayload): Promise<number> =>
    unwrap(request.post<Result<number>>('/receipts', payload, {
      headers: { 'Idempotency-Key': newIdempotencyKey() }
    })),

  confirm: (id: number): Promise<void> =>
    unwrap(request.post<Result<void>>(`/receipts/${id}/actions/confirm`, {}, {
      headers: { 'Idempotency-Key': newIdempotencyKey() }
    })),

  cancel: (id: number): Promise<void> =>
    unwrap(request.post<Result<void>>(`/receipts/${id}/actions/cancel`, {}, {
      headers: { 'Idempotency-Key': newIdempotencyKey() }
    }))
}
