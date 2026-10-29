import { Button } from 'antd'
import { NavLink, useNavigate } from 'react-router-dom'
import { clearAuth, getStoredUser } from '../../utils/auth'
import './index.css'

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  isActive ? 'hdr__link hdr__link--active' : 'hdr__link'

/**
 * 顶部导航（玻璃条）。
 *
 * 导航项按角色渲染：普通用户看不到「管理后台」，
 * 但真正的拦截在 RequireAdmin 和接口鉴权，这里只是不给无权限的人一个死链接。
 */
export default function AppHeader() {
  const navigate = useNavigate()
  const user = getStoredUser()
  const isAdmin = user?.role === 'ADMIN'

  const handleLogout = () => {
    clearAuth()
    navigate('/login', { replace: true })
  }

  return (
    <header className="hdr">
      <div className="hdr__inner">
        <div className="hdr__brand">
          <span className="hdr__mark" aria-hidden="true" />
          <span className="hdr__name">校园活动报名</span>
        </div>

        <nav className="hdr__nav" aria-label="主导航">
          <NavLink to="/" end className={navLinkClass}>
            活动
          </NavLink>
          <NavLink to="/my-signups" className={navLinkClass}>
            我的报名
          </NavLink>
          {isAdmin ? (
            <NavLink to="/admin" className={navLinkClass}>
              管理后台
            </NavLink>
          ) : null}
        </nav>

        <div className="hdr__right">
          <span className="hdr__user">{user?.nickname || user?.username || '未登录'}</span>
          {isAdmin ? <span className="hdr__role">管理员</span> : null}
          <Button type="text" onClick={handleLogout}>
            退出
          </Button>
        </div>
      </div>
    </header>
  )
}
