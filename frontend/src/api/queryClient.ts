import { QueryClient } from '@tanstack/react-query'

/**
 * 全局 React Query 客户端。
 * 登录 / 切换角色 / 退出时调用 queryClient.clear()，
 * 避免上一个用户的列表数据残留在缓存里导致"串号"。
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30 * 1000,
      refetchOnWindowFocus: false,
      retry: 1
    }
  }
})