import { useCallback, useEffect, useState } from 'react'
import { App as AntdApp, Button, Card, Empty, Space, Tag, Typography } from 'antd'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { agentAlerts, agentHandleAlert } from '../../api/agent'
import { PageCard } from '../../components/PageCard'
import type { AgentAlertVO } from '../../types'

const LEVEL_COLOR: Record<string, string> = {
  CRITICAL: 'error',
  WARNING: 'warning',
  INFO: 'default'
}

const TYPE_LABEL: Record<string, string> = {
  OVERDUE_ORDER: '逾期订单',
  LOW_STOCK: '库存预警',
  PRICE_ABNORMAL: '价格异常',
  SYSTEM: '系统预警'
}

/**
 * AI Agent 预警中心
 * 每 30 秒轮询一次未处理预警列表，支持手动标记已处理
 */
export function AgentAlertsPage() {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const [alerts, setAlerts] = useState<AgentAlertVO[]>([])
  const [loading, setLoading] = useState(false)

  const fetchAlerts = useCallback(async () => {
    setLoading(true)
    try {
      const list = await agentAlerts()
      setAlerts(list)
    } catch {
      console.error('获取预警列表失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchAlerts()
    const interval = setInterval(fetchAlerts, 30_000)
    return () => clearInterval(interval)
  }, [fetchAlerts])

  const handleMarkProcessed = async (alertId: string) => {
    try {
      await agentHandleAlert(alertId)
      message.success('已标记为已处理')
      queryClient.invalidateQueries()
      fetchAlerts()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '操作失败')
    }
  }

  return (
    <div className="flex flex-col gap-5">
      <PageCard
        title="AI 预警中心"
        extra={
          <Space>
            <Tag>每 30 秒自动刷新</Tag>
            <Button onClick={fetchAlerts} loading={loading}>
              手动刷新
            </Button>
          </Space>
        }
      >
        <div className="text-xs text-ink-muted mb-4">
          预警由后端定时任务每 5 分钟自动扫描生成（逾期订单 / 库存低于安全库存 / 价格异常），
          存入 Redis（TTL 1 小时）。配置 DASHSCOPE_API_KEY 后，预警将包含 AI 生成建议。
        </div>

        {alerts.length === 0 && !loading && (
          <Empty description="当前无未处理预警" className="py-8" />
        )}

        <div className="flex flex-col gap-4">
          {alerts.map((alert) => (
            <Card
              key={alert.alertId}
              size="small"
              className={`transition-160 hover:shadow-md ${
                alert.level === 'CRITICAL' ? 'border-l-4 border-l-red-500' :
                alert.level === 'WARNING' ? 'border-l-4 border-l-amber-400' :
                'border-l-4 border-l-blue-400'
              }`}
            >
              <div className="flex items-start justify-between gap-4">
                <div className="flex flex-col gap-2 flex-1">
                  <div className="flex items-center gap-2">
                    <Tag color={LEVEL_COLOR[alert.level]}>
                      {TYPE_LABEL[alert.alertType] ?? alert.alertType}
                    </Tag>
                    <span className="text-sm font-medium text-ink">
                      {alert.title}
                    </span>
                    {alert.refNo && (
                      <Tag className="text-xs">{alert.refNo}</Tag>
                    )}
                  </div>
                  <Typography.Text className="text-xs text-ink-muted whitespace-pre-wrap">
                    {alert.content}
                  </Typography.Text>
                  <span className="text-xs text-ink-muted/60">
                    生成时间：{alert.createTime?.replace('T', ' ')}
                  </span>
                </div>
                <Button
                  type="primary"
                  size="small"
                  onClick={() => handleMarkProcessed(alert.alertId)}
                >
                  标记已处理
                </Button>
              </div>
            </Card>
          ))}
        </div>
      </PageCard>
    </div>
  )
}
