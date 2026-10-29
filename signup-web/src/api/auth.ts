import { request } from '../utils/request'
import type { LoginParams, LoginResult } from './types'

/**
 * 登录。
 * 成功后需要调用方自己把 token 落盘（见 utils/auth.ts），
 * 这里不碰 localStorage，保持 api 层无副作用，方便以后换存储方案。
 */
export function login(params: LoginParams): Promise<LoginResult> {
  return request.post<LoginResult>('/auth/login', params)
}
