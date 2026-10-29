import type { ReactElement } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { getStoredUser, getToken } from '../utils/auth'

interface RequireAdminProps {
  children: ReactElement
}

/**
 * 管理端路由守卫。
 *
 * 两层判断：没登录先去登录页；登录了但不是 ADMIN 直接回首页。
 * 刻意不弹「无权限」——普通用户不该知道后台存在，也不该看到 403 页面。
 *
 * 这只是体验层拦截，真正的权限在后端：/api/admin/** 由 Security 过滤。
 */
export default function RequireAdmin({ children }: RequireAdminProps) {
  const location = useLocation()

  if (!getToken()) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }

  const user = getStoredUser()
  if (user?.role !== 'ADMIN') {
    return <Navigate to="/" replace />
  }

  return children
}
