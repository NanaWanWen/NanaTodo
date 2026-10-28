import { request, unwrap } from './request'
import type { Result, AgentAlertRule } from '../types'

export const alertRuleApi = {
  /** 规则列表 */
  list: (): Promise<AgentAlertRule[]> =>
    unwrap(request.get<Result<AgentAlertRule[]>>('/agent/alert-rules')),

  /** 创建规则 */
  create: (rule: AgentAlertRule): Promise<AgentAlertRule> =>
    unwrap(request.post<Result<AgentAlertRule>>('/agent/alert-rules', rule)),

  /** 更新规则 */
  update: (id: number, rule: AgentAlertRule): Promise<AgentAlertRule> =>
    unwrap(request.put<Result<AgentAlertRule>>(`/agent/alert-rules/${id}`, rule)),

  /** 删除规则 */
  remove: (id: number): Promise<void> =>
    unwrap(request.delete<Result<void>>(`/agent/alert-rules/${id}`))
}
