import axios from 'axios'
import { authExpiredEvent, clearSession, readToken } from '../utils/session'

interface ApiResponse<T> { code: string; message: string; data: T; requestId?: string }
export const useRealApi = import.meta.env.VITE_USE_REAL_API !== 'false'
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 70000,
  headers: { 'Content-Type': 'application/json' },
})
apiClient.interceptors.request.use((config) => {
  if (!useRealApi) throw new Error('请将 VITE_USE_REAL_API 设置为 true 后重启前端')
  const token = readToken()
  if (token) config.headers.Authorization = 'Bearer ' + token
  return config
})
apiClient.interceptors.response.use(
  (response) => {
    const sentToken = String(response.config.headers.Authorization || '')
    if (sentToken && sentToken !== 'Bearer ' + readToken()) throw new Error('账号已切换，请刷新数据')
    const body = response.data as ApiResponse<unknown>
    if (body.code !== 'OK') throw new Error(body.message || '服务器返回了无法识别的响应')
    response.data = body.data
    return response
  },
  (error) => {
    const loginRequest = /\/auth\/(login|register)$/.test(error.config?.url || '')
    const sentToken = String(error.config?.headers?.Authorization || '')
    if (error.response?.status === 401 && !loginRequest && (!sentToken || sentToken === 'Bearer ' + readToken())) {
      clearSession()
      window.dispatchEvent(new Event(authExpiredEvent))
    }
    const message = error.response?.data?.message
      || (error.code === 'ECONNABORTED' ? '请求超时，请检查服务后重试' : error.message)
      || '网络请求失败'
    return Promise.reject(new Error(message))
  },
)
