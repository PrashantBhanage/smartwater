import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
} from 'react'
import { authApi } from '../api/auth'
import { setAuthToken } from '../api/client'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [token, setToken] = useState(null)
  const [user, setUser] = useState(null)
  const [bootstrapping, setBootstrapping] = useState(false)

  const login = useCallback(async (email, password) => {
    const data = await authApi.login(email, password)
    setAuthToken(data.token)
    setToken(data.token)
    setUser({
      id: data.userId,
      email: data.email,
      fullName: data.fullName,
      role: data.role,
    })
    return data
  }, [])

  const logout = useCallback(() => {
    setAuthToken(null)
    setToken(null)
    setUser(null)
  }, [])

  const refreshProfile = useCallback(async () => {
    setBootstrapping(true)
    try {
      const profile = await authApi.me()
      setUser(profile)
      return profile
    } finally {
      setBootstrapping(false)
    }
  }, [])

  const value = useMemo(
    () => ({
      token,
      user,
      isAuthenticated: Boolean(token),
      isAdmin: user?.role === 'ADMIN',
      isResident: user?.role === 'RESIDENT',
      bootstrapping,
      login,
      logout,
      refreshProfile,
    }),
    [token, user, bootstrapping, login, logout, refreshProfile],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
