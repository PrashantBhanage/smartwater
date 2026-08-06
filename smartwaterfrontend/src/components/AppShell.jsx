import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import './AppShell.css'

const ADMIN_NAV = [
  { to: '/admin', label: 'Overview', end: true },
  { to: '/admin/households', label: 'Households' },
  { to: '/admin/billing', label: 'Billing' },
  { to: '/admin/bulk-purchases', label: 'Bulk Purchases' },
  { to: '/admin/invoices', label: 'Cycle Invoices' },
  { to: '/admin/alerts', label: 'Alerts' },
  { to: '/admin/uploads', label: 'CSV Upload' },
]

const RESIDENT_NAV = [
  { to: '/resident', label: 'Home', end: true },
  { to: '/resident/invoices', label: 'Invoices' },
  { to: '/resident/alerts', label: 'Alerts' },
]

export default function AppShell() {
  const { user, logout, isAdmin } = useAuth()
  const nav = isAdmin ? ADMIN_NAV : RESIDENT_NAV

  return (
    <div className="shell">
      <div className="shell__grain" aria-hidden />
      <aside className="shell__sidebar">
        <div className="shell__brand">
          <div className="shell__brand-name">SmartWater</div>
          <div className="shell__brand-meta">{isAdmin ? 'Administration' : 'Resident'}</div>
        </div>

        <nav className="shell__nav" aria-label="Main">
          {nav.map(({ to, label, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                `shell__nav-item${isActive ? ' is-active' : ''}`
              }
            >
              {label}
            </NavLink>
          ))}
        </nav>

        <div className="shell__footer">
          <div className="shell__user">
            <div className="shell__user-name">{user?.fullName || 'User'}</div>
            <div className="shell__user-email">{user?.email}</div>
          </div>
          <button type="button" className="sw-btn sw-btn--ghost shell__signout" onClick={logout}>
            Sign out
          </button>
        </div>
      </aside>

      <main className="shell__main">
        <div className="shell__content sw-fade-in">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
