import { newIdempotencyKey, request, unwrap } from './request'
import type { ApSettlementListItem, PageResult, Result, SupplierBalanceVO } from '../types'

export const apApi = {
  settlements: (params: { orderId?: number; matchStatus?: string; pageNo?: number; pageSize?: number }): Promise<
    PageResult<ApSettlementListItem>
  > =>
    unwrap(request.get<Result<PageResult<ApSettlementListItem>>>('/ap/settlements', { params })),

  match: (orderId: number): Promise<void> =>
    unwrap(request.post<Result<void>>(`/ap/settlements/${orderId}/match`, {}, {
      headers: { 'Idempotency-Key': newIdempotencyKey() }
    })),

  supplierBalances: (): Promise<SupplierBalanceVO[]> =>
    unwrap(request.get<Result<SupplierBalanceVO[]>>('/ap/balances'))
}
