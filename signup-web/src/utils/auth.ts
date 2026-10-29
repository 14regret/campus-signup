import type { UserProfile } from '../api/types'

const TOKEN_KEY = 'token'
const USER_KEY = 'user'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

/** 登录后顺手缓存用户信息，用于首屏渲染，避免刷新就先闪一下空白。 */
export function getStoredUser(): UserProfile | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as UserProfile
  } catch {
    // 手工改坏 localStorage 时不让整个应用挂掉
    return null
  }
}

export function setStoredUser(user: UserProfile): void {
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

/** 退出登录 / 401 时统一走这里，token 和用户信息一起清。 */
export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}
