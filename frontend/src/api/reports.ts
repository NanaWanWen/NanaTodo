import { request, unwrap } from './request'
import type { RankGroupBy, RankResult, ReportSummary, Result, StatusStat, TimeStat } from '../types'

export const reportApi = {
  summary: (startDate?: string, endDate?: string): Promise<ReportSummary> =>
    unwrap(
      request.get<Result<ReportSummary>>('/reports/summary', { params: { startDate, endDate } })
    ),

  rank: (params: {
    groupBy: RankGroupBy
    startDate?: string
    endDate?: string
    status?: string
    topN?: number
  }): Promise<RankResult> => unwrap(request.get<Result<RankResult>>('/reports/rank', { params })),

  byStatus: (startDate?: string, endDate?: string): Promise<StatusStat[]> =>
    unwrap(
      request.get<Result<StatusStat[]>>('/reports/by-status', { params: { startDate, endDate } })
    ),

  byTime: (granularity: 'month' | 'day', startDate?: string, endDate?: string): Promise<TimeStat> =>
    unwrap(
      request.get<Result<TimeStat>>('/reports/by-time', {
        params: { granularity, startDate, endDate }
      })
    ),

  refresh: (): Promise<void> => unwrap(request.get<Result<void>>('/reports/refresh')),

  /** 导出统计表（二进制流，走 axios 以带会话头） */
  exportRank: async (params: {
    groupBy: RankGroupBy
    startDate?: string
    endDate?: string
    status?: string
  }): Promise<Blob> =>
    (await request.get('/reports/rank/export', { params, responseType: 'blob' })).data,

  /** 导出订单明细 */
  exportOrders: async (params: {
    keyword?: string
    status?: string
    supplierId?: number
    buyerId?: number
    deptId?: number
    materialId?: number
    startDate?: string
    endDate?: string
  }): Promise<Blob> =>
    (await request.get('/reports/orders/export', { params, responseType: 'blob' })).data
}
