import { newIdempotencyKey, request, unwrap } from './request'
import type { InvoiceCreatePayload, InvoiceListItem, InvoiceUpdatePayload, PageResult, Result } from '../types'

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
    })),

  update: (id: number, payload: InvoiceUpdatePayload): Promise<void> =>
    unwrap(request.put<Result<void>>(`/invoices/${id}`, payload, {
      headers: { 'Idempotency-Key': newIdempotencyKey() }
    })),

  redFlush: (id: number, payload?: { remark?: string }): Promise<number> =>
    unwrap(request.post<Result<number>>(`/invoices/${id}/actions/red-flush`, payload ?? {}, {
      headers: { 'Idempotency-Key': newIdempotencyKey() }
    })),

  voidInvoice: (id: number): Promise<void> =>
    unwrap(request.post<Result<void>>(`/invoices/${id}/actions/void`, {}, {
      headers: { 'Idempotency-Key': newIdempotencyKey() }
    }))
}
