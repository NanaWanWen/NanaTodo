import { newIdempotencyKey, request, unwrap } from './request'
import type { InventoryItem, Result } from '../types'

export const inventoryApi = {
  listAll: (): Promise<InventoryItem[]> =>
    unwrap(request.get<Result<InventoryItem[]>>('/inventory')),

  alerts: (): Promise<InventoryItem[]> =>
    unwrap(request.get<Result<InventoryItem[]>>('/inventory/alerts')),

  updateSafetyStock: (id: number, safetyStock: number): Promise<void> =>
    unwrap(
      request.put<Result<void>>(`/inventory/${id}/safety-stock?${new URLSearchParams({ safetyStock: String(safetyStock) })}`)
    )
}
