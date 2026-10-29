import { createBrowserRouter, Navigate } from 'react-router-dom'
import ActivityDetailPage from '../pages/ActivityDetail'
import AdminPage from '../pages/Admin'
import AdminSignupsPage from '../pages/AdminSignups'
import HomePage from '../pages/Home'
import LoginPage from '../pages/Login'
import MySignupsPage from '../pages/MySignups'
import RequireAdmin from './RequireAdmin'
import RequireAuth from './RequireAuth'

/**
 * 路由表。
 *
 * 登录页之外的页面都包在守卫里；管理端用 RequireAdmin（同时校验登录与角色）。
 * 用 createBrowserRouter（data router）而不是 <BrowserRouter>，
 * 是为了以后能直接上 loader / errorElement，不用重构。
 */
const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/',
    element: (
      <RequireAuth>
        <HomePage />
      </RequireAuth>
    ),
  },
  {
    path: '/activity/:id',
    element: (
      <RequireAuth>
        <ActivityDetailPage />
      </RequireAuth>
    ),
  },
  {
    path: '/my-signups',
    element: (
      <RequireAuth>
        <MySignupsPage />
      </RequireAuth>
    ),
  },
  {
    path: '/admin',
    element: (
      <RequireAdmin>
        <AdminPage />
      </RequireAdmin>
    ),
  },
  {
    path: '/admin/activities/:id/signups',
    element: (
      <RequireAdmin>
        <AdminSignupsPage />
      </RequireAdmin>
    ),
  },
  {
    path: '*',
    element: <Navigate to="/" replace />,
  },
])

export default router
