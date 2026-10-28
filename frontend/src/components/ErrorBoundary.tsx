import { Component } from 'react'
import type { ErrorInfo, ReactNode } from 'react'
import { Button, Result } from 'antd'

interface Props {
  children: ReactNode
}

interface State {
  error: Error | null
}

/**
 * 全局错误边界：页面渲染异常时降级展示错误卡片，避免整个应用白屏
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('页面渲染异常：', error, info.componentStack)
  }

  render() {
    if (this.state.error) {
      return (
        <div
          style={{
            minHeight: '100vh',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: 24
          }}
        >
          <Result
            status="error"
            title="页面加载失败"
            subTitle={this.state.error.message || '未知错误，请刷新重试'}
            extra={[
              <Button
                key="reload"
                type="primary"
                onClick={() => {
                  window.location.reload()
                }}
              >
                刷新页面
              </Button>,
              <Button
                key="home"
                onClick={() => {
                  window.location.href = '/workbench'
                }}
              >
                返回工作台
              </Button>
            ]}
          />
        </div>
      )
    }
    return this.props.children
  }
}
