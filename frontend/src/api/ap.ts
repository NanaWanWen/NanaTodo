import { newIdempotencyKey, request, unwrap } from './request'
import type { ApSettlementListItem, PageResult, Result, SupplierBalanceVO } from '../types'

export const apApi = {
  settlements: (params: {
    orderId?: number
    matchStatus?: string
    matchType?: string
    diffType?: string
    supplierId?: number
    keyword?: string
    pageNo?: number
    pageSize?: number
  }): Promise<PageResult<ApSettlementListItem>> =>
    unwrap(request.get<Result<PageResult<ApSettlementListItem>>>('/ap/settlements', { params })),

  match: (orderId: number): Promise<void> =>
    unwrap(request.post<Result<void>>(`/ap/settlements/${orderId}/match`, {}, {
      headers: { 'Idempotency-Key': newIdempotencyKey() }
    })),

  autoSettle: (orderIds: number[]): Promise<ApSettlementListItem[]> =>
    unwrap(request.post<Result<ApSettlementListItem[]>>('/ap/settlements/auto-settle', orderIds, {
      headers: { 'Idempotency-Key': newIdempotencyKey() }
    })),

  manualSettle: (cmd: { orderId: number; receiptId?: number; invoiceId?: number; remark?: string }): Promise<ApSettlementListItem> =>
    unwrap(request.post<Result<ApSettlementListItem>>('/ap/settlements/manual-settle', cmd, {
      headers: { 'Idempotency-Key': newIdempotencyKey() }
    })),

  forceSettle: (settlementId: number, cmd: { reason: string; resolution: string }): Promise<void> =>
    unwrap(request.post<Result<void>>(`/ap/settlements/${settlementId}/force-settle`, cmd, {
      headers: { 'Idempotency-Key': newIdempotencyKey() }
    })),

  resolveDiff: (settlementId: number, cmd: { resolution: string; remark?: string }): Promise<void> =>
    unwrap(request.post<Result<void>>(`/ap/settlements/${settlementId}/resolve-diff`, cmd, {
      headers: { 'Idempotency-Key': newIdempotencyKey() }
    })),

  supplierBalances: (): Promise<SupplierBalanceVO[]> =>
    unwrap(request.get<Result<SupplierBalanceVO[]>>('/ap/balances'))
}
