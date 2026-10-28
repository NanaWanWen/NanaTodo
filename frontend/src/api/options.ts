import { request, unwrap } from './request'
import type { Result, PageResult } from '../types'

export interface BizOption {
  id: number
  no: string
  label: string
  subLabel?: string
  status: string
  statusName: string
  amount?: number
  date?: string
  supplierName?: string
  buyerName?: string
  remainQty?: number
}

export const optionsApi = {
  /** 可收货订单（已审批） */
  receivableOrders: (keyword?: string, pageNo = 1, pageSize = 20): Promise<PageResult<BizOption>> =>
    unwrap(request.get<Result<PageResult<BizOption>>>('/options/orders/receivable', {
      params: { keyword, pageNo, pageSize }
    })),

  /** 可开票收货单（已确认） */
  invoicableReceipts: (keyword?: string, pageNo = 1, pageSize = 20): Promise<PageResult<BizOption>> =>
    unwrap(request.get<Result<PageResult<BizOption>>>('/options/receipts/invoicable', {
      params: { keyword, pageNo, pageSize }
    })),

  /** 可付款发票（已核对） */
  payableInvoices: (keyword?: string, pageNo = 1, pageSize = 20): Promise<PageResult<BizOption>> =>
    unwrap(request.get<Result<PageResult<BizOption>>>('/options/invoices/payable', {
      params: { keyword, pageNo, pageSize }
    })),

  /** 供应商搜索 */
  suppliers: (keyword?: string): Promise<{ total: number; records: BizOption[] }> =>
    unwrap(request.get<Result<BizOption[]>>('/options/suppliers', { params: { keyword } }))
      .then((records) => ({ total: records.length, records })),

  /** 物料搜索 */
  materials: (keyword?: string): Promise<{ total: number; records: BizOption[] }> =>
    unwrap(request.get<Result<BizOption[]>>('/options/materials', { params: { keyword } }))
      .then((records) => ({ total: records.length, records }))
}