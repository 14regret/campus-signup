import type { ReactElement } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { getToken } from '../utils/auth'

interface RequireAuthProps {
  children: ReactElement
}

/**
 * 最简路由守卫：没有 token 就回登录页，并把当前路径带过去，登录后原路返回。
 *
 * 注意这只是「前端体验」层面的拦截，不构成安全边界——
 * 真正的鉴权在每个接口上用 JWT 校验，前端跳转只是避免闪一下空白页。
 */
export default function RequireAuth({ children }: RequireAuthProps) {
  const location = useLocation()

  if (!getToken()) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }

  return children
}
