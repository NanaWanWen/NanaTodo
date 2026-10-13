import axios, { AxiosError } from 'axios'
import type { Result } from '../types'
import { useAuthStore } from '../store/useAuthStore'

export const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

/**
 * 生成幂等键，动作接口防重复提交
 */
export const newIdempotencyKey = () => {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

request.interceptors.request.use((config) => {
  const { userId, currentRole } = useAuthStore.getState()
  if (userId) {
    config.headers['X-User-Id'] = String(userId)
  }
  if (currentRole) {
    config.headers['X-Role-Code'] = currentRole
  }
  return config
})

request.interceptors.response.use(
  (response) => response,
  (error: AxiosError<Result<unknown>>) => {
    const data = error.response?.data
    if (data?.code === 3001) {
      useAuthStore.getState().clear()
      window.location.href = '/login'
    }
    console.error('请求失败：', data?.message ?? error.message)
    return Promise.reject(new Error(data?.message ?? '请求失败，请稍后重试'))
  }
)

/**
 * 统一取出业务数据
 */
export async function unwrap<T>(promise: Promise<{ data: Result<T> }>): Promise<T> {
  const response = await promise
  return response.data.data
}
