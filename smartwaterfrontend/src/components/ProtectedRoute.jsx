import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

/**
 * Guards routes by auth + optional role.
 * @param {'ADMIN' | 'RESIDENT'} [role]
 */
export default function ProtectedRoute({ children, role }) {
  const { isAuthenticated, user } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  if (role && user?.role !== role) {
    const fallback = user?.role === 'ADMIN' ? '/admin' : '/resident'
    return <Navigate to={fallback} replace />
  }

  return children
}
