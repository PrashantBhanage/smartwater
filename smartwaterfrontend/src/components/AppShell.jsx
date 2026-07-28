import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import './AppShell.css'

const ADMIN_NAV = [
  { to: '/admin', label: 'Overview', end: true, icon: OverviewIcon },
  { to: '/admin/households', label: 'Households', icon: HomeIcon },
  { to: '/admin/billing', label: 'Billing', icon: BillingIcon },
  { to: '/admin/uploads', label: 'CSV Upload', icon: UploadIcon },
]

const RESIDENT_NAV = [
  { to: '/resident', label: 'Dashboard', end: true, icon: OverviewIcon },
  { to: '/resident/invoices', label: 'Invoices', icon: BillingIcon },
  { to: '/resident/alerts', label: 'Alerts', icon: AlertIcon },
]

export default function AppShell() {
  const { user, logout, isAdmin } = useAuth()
  const nav = isAdmin ? ADMIN_NAV : RESIDENT_NAV

  return (
    <div className="shell">
      <aside className="shell__sidebar sw-glass">
        <div className="shell__brand">
          <span className="shell__mark" aria-hidden />
          <div>
            <div className="shell__brand-name">SmartWater</div>
            <div className="shell__brand-meta">{isAdmin ? 'Admin' : 'Resident'}</div>
          </div>
        </div>

        <nav className="shell__nav" aria-label="Main">
          {nav.map(({ to, label, end, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                `shell__nav-item${isActive ? ' is-active' : ''}`
              }
            >
              <Icon />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="shell__footer">
          <div className="shell__user">
            <div className="shell__avatar" aria-hidden>
              {(user?.fullName || user?.email || '?').charAt(0).toUpperCase()}
            </div>
            <div className="shell__user-text">
              <div className="shell__user-name">{user?.fullName || 'User'}</div>
              <div className="shell__user-email">{user?.email}</div>
            </div>
          </div>
          <button type="button" className="sw-btn sw-btn--ghost shell__signout" onClick={logout}>
            Sign out
          </button>
        </div>
      </aside>

      <main className="shell__main">
        <div className="shell__content">
          <Outlet />
        </div>
      </main>
    </div>
  )
}

function OverviewIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden>
      <rect x="1.5" y="1.5" width="5.5" height="5.5" rx="1.2" stroke="currentColor" strokeWidth="1.4" />
      <rect x="9" y="1.5" width="5.5" height="5.5" rx="1.2" stroke="currentColor" strokeWidth="1.4" />
      <rect x="1.5" y="9" width="5.5" height="5.5" rx="1.2" stroke="currentColor" strokeWidth="1.4" />
      <rect x="9" y="9" width="5.5" height="5.5" rx="1.2" stroke="currentColor" strokeWidth="1.4" />
    </svg>
  )
}

function HomeIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden>
      <path d="M2.5 7.2 8 2.5l5.5 4.7V13a1 1 0 0 1-1 1H3.5a1 1 0 0 1-1-1V7.2Z" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" />
    </svg>
  )
}

function BillingIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden>
      <rect x="2.5" y="3" width="11" height="10" rx="1.5" stroke="currentColor" strokeWidth="1.4" />
      <path d="M2.5 6.5h11M5.5 9.5h5" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
    </svg>
  )
}

function UploadIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden>
      <path d="M8 10.5V3.5M8 3.5 5.5 6M8 3.5 10.5 6" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M3 11.5v1a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1v-1" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
    </svg>
  )
}

function AlertIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden>
      <path d="M8 2.5 14 13H2L8 2.5Z" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" />
      <path d="M8 6.5v3M8 11.2h.01" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
    </svg>
  )
}
