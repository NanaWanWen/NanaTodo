import { Button, Result } from 'antd'
import { useNavigate } from 'react-router-dom'

/**
 * 无权限页
 */
export function ForbiddenPage() {
  const navigate = useNavigate()
  return (
    <div className="flex min-h-[70vh] items-center justify-center">
      <Result
        status="403"
        title="403"
        subTitle="当前角色没有访问该页面的权限，可切换角色后重试"
        extra={
          <Button type="primary" onClick={() => navigate('/orders')}>
            返回订单列表
          </Button>
        }
      />
    </div>
  )
}
