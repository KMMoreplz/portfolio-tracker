import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

const NAV_ITEMS = [
  { to: '/', label: 'Дашборд', end: true },
  { to: '/transactions', label: 'Сделки', end: false },
  { to: '/calendar', label: 'Дивидендный календарь', end: false },
  { to: '/settings', label: 'Настройки', end: false },
]

/** Шапка с навигацией и выходом; содержимое страниц — <Outlet/>. */
export default function Layout() {
  const { user, logout } = useAuth()

  return (
    <div className="app">
      <header className="app-header">
        <div className="app-brand">Portfolio Tracker</div>
        <nav className="app-nav">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="app-user">
          <span className="app-user-email">{user?.email}</span>
          <button type="button" className="btn btn-ghost" onClick={logout}>
            Выйти
          </button>
        </div>
      </header>
      <main className="app-main">
        <Outlet />
      </main>
    </div>
  )
}
