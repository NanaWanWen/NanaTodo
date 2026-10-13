import { newIdempotencyKey, request, unwrap } from './request'
import type { InvoiceCreatePayload, InvoiceListItem, PageResult, Result } from '../types'

export interface InvoiceQueryParams {
  keyword?: string
  invoiceStatus?: string
  supplierId?: number
  orderId?: number
  pageNo?: number
  pageSize?: number
}

export const invoiceApi = {
  page: (params: InvoiceQueryParams): Promise<PageResult<InvoiceListItem>> =>
    unwrap(request.get<Result<PageResult<InvoiceListItem>>>('/invoices', { params })),

  create: (payload: InvoiceCreatePayload): Promise<number> =>
    unwrap(request.post<Result<number>>('/invoices', payload, {
      headers: { 'Idempotency-Key': newIdempotencyKey() }
    })),

  check: (id: number): Promise<void> =>
    unwrap(request.post<Result<void>>(`/invoices/${id}/actions/check`, {}, {
      headers: { 'Idempotency-Key': newIdempotencyKey() }
    })),

  uncheck: (id: number): Promise<void> =>
    unwrap(request.post<Result<void>>(`/invoices/${id}/actions/uncheck`, {}, {
      headers: { 'Idempotency-Key': newIdempotencyKey() }
    }))
}
