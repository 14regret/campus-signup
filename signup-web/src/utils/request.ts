import { message } from 'antd'
import axios from 'axios'
import type { AxiosError, AxiosRequestConfig, AxiosResponse } from 'axios'
import type { ApiResult } from '../api/types'
import { clearAuth, getToken } from './auth'

/**
 * 统一的 Axios 实例。
 *
 * 分工刻意拆开：
 * - 拦截器只负责「识别失败并提示」；
 * - 成功时把 AxiosResponse 原样返回，由下面的 request.get/post/put 拆包成 T。
 *
 * 这样既有正确的类型（不用 any 强转），又能在拦截器里集中处理错误。
 */

/**
 * 成功码集合。
 *
 * 后端实际返回的是 code: 0（ErrorCode.SUCCESS(0, "ok")），
 * 而接口文档/约定里常写成 200，这里两个都放行，避免契约不一致时整站报错。
 * 等约定统一后，把不需要的那个删掉即可。
 */
const SUCCESS_CODES: readonly number[] = [0, 200]

const instance = axios.create({
  // 走 vite 代理：/api -> http://localhost:8080
  baseURL: '/api',
  timeout: 10_000,
  headers: { 'Content-Type': 'application/json' },
})

// 请求拦截：带上 Bearer token
instance.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：code 判定 + 错误提示 + 401 兜底
instance.interceptors.response.use(
  (response: AxiosResponse<ApiResult<unknown>>) => {
    const body = response.data
    if (SUCCESS_CODES.includes(body.code)) {
      return response
    }
    // 业务失败仍是 HTTP 200 的情况（例如某些网关会把错误包成 200）
    message.error(body.message || '请求失败')
    return Promise.reject(new Error(body.message || '请求失败'))
  },
  (error: AxiosError<ApiResult<unknown>>) => {
    const status = error.response?.status
    const body = error.response?.data

    if (status === 401) {
      clearAuth()
      // 已经在登录页就不用再跳，否则会打断用户正在输入的密码
      if (!window.location.pathname.startsWith('/login')) {
        window.location.replace('/login')
      }
      return Promise.reject(error)
    }

    // 后端所有错误都带业务文案，优先用它；没有再退回 axios 自己的描述
    message.error(body?.message || error.message || '网络异常，请稍后重试')
    return Promise.reject(error)
  },
)

export const request = {
  async get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    const response = await instance.get<ApiResult<T>>(url, config)
    return response.data.data
  },

  async post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    const response = await instance.post<ApiResult<T>>(url, data, config)
    return response.data.data
  },

  async put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    const response = await instance.put<ApiResult<T>>(url, data, config)
    return response.data.data
  },

  async delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    const response = await instance.delete<ApiResult<T>>(url, config)
    return response.data.data
  },
}
