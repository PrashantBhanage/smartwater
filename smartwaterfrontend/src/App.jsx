import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from './context/AuthContext'
import ProtectedRoute from './components/ProtectedRoute'
import AppShell from './components/AppShell'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import ResidentDashboardPage from './pages/ResidentDashboardPage'
import ResidentInvoicesPage from './pages/ResidentInvoicesPage'
import ResidentAlertsPage from './pages/ResidentAlertsPage'
import AdminHouseholdsPage from './pages/AdminHouseholdsPage'
import AdminBulkPurchasesPage from './pages/AdminBulkPurchasesPage'
import AdminCycleInvoicesPage from './pages/AdminCycleInvoicesPage'
import AdminAlertsPage from './pages/AdminAlertsPage'
import { AdminOverviewPage, AdminBillingPage, AdminUploadsPage } from './pages/stubs'

function HomeRedirect() {
  const { isAuthenticated, user } = useAuth()
  if (!isAuthenticated) return <Navigate to="/login" replace />
  return <Navigate to={user?.role === 'ADMIN' ? '/admin' : '/resident'} replace />
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/" element={<HomeRedirect />} />

          <Route
            element={
              <ProtectedRoute role="ADMIN">
                <AppShell />
              </ProtectedRoute>
            }
          >
            <Route path="/admin" element={<AdminOverviewPage />} />
            <Route path="/admin/households" element={<AdminHouseholdsPage />} />
            <Route path="/admin/billing" element={<AdminBillingPage />} />
            <Route path="/admin/bulk-purchases" element={<AdminBulkPurchasesPage />} />
            <Route path="/admin/invoices" element={<AdminCycleInvoicesPage />} />
            <Route path="/admin/alerts" element={<AdminAlertsPage />} />
            <Route path="/admin/uploads" element={<AdminUploadsPage />} />
          </Route>

          <Route
            element={
              <ProtectedRoute role="RESIDENT">
                <AppShell />
              </ProtectedRoute>
            }
          >
            <Route path="/resident" element={<ResidentDashboardPage />} />
            <Route path="/resident/invoices" element={<ResidentInvoicesPage />} />
            <Route path="/resident/alerts" element={<ResidentAlertsPage />} />
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
