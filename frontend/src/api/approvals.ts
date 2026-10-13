import { newIdempotencyKey, request, unwrap } from './request'
import type { ApprovalTodoItem, PageResult, Result } from '../types'

export const approvalApi = {
  todo: (keyword?: string, pageNo = 1, pageSize = 20): Promise<PageResult<ApprovalTodoItem>> =>
    unwrap(
      request.get<Result<PageResult<ApprovalTodoItem>>>('/approvals/todo', {
        params: { keyword, pageNo, pageSize }
      })
    ),

  todoCount: (): Promise<number> =>
    unwrap(request.get<Result<number>>('/approvals/todo-count')),

  approve: (taskId: number, opinion?: string, version?: number): Promise<void> =>
    unwrap(
      request.post<Result<void>>(`/approvals/${taskId}/approve`, { remark: opinion, version }, {
        headers: { 'Idempotency-Key': newIdempotencyKey() }
      })
    ),

  reject: (taskId: number, opinion: string, version?: number): Promise<void> =>
    unwrap(
      request.post<Result<void>>(`/approvals/${taskId}/reject`, { remark: opinion, version }, {
        headers: { 'Idempotency-Key': newIdempotencyKey() }
      })
    )
}
