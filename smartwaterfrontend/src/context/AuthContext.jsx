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

function userFromAuth(data) {
  return {
    id: data.userId ?? data.id,
    email: data.email,
    fullName: data.fullName,
    role: data.role,
    apartmentId: data.apartmentId ?? null,
    householdId: data.householdId ?? null,
  }
}

export function AuthProvider({ children }) {
  const [token, setToken] = useState(null)
  const [user, setUser] = useState(null)
  const [bootstrapping, setBootstrapping] = useState(false)

  const applySession = useCallback((data) => {
    setAuthToken(data.token)
    setToken(data.token)
    setUser(userFromAuth(data))
    return data
  }, [])

  const login = useCallback(
    async (email, password) => {
      const data = await authApi.login(email, password)
      applySession(data)
      const profile = await authApi.me()
      setUser(userFromAuth({ ...data, ...profile, userId: profile.id }))
      return { ...data, ...profile }
    },
    [applySession],
  )

  const register = useCallback(
    async (payload) => {
      const data = await authApi.register(payload)
      applySession(data)
      const profile = await authApi.me()
      setUser(userFromAuth({ ...data, ...profile, userId: profile.id }))
      return { ...data, ...profile }
    },
    [applySession],
  )

  const logout = useCallback(() => {
    setAuthToken(null)
    setToken(null)
    setUser(null)
  }, [])

  const refreshProfile = useCallback(async () => {
    setBootstrapping(true)
    try {
      const profile = await authApi.me()
      setUser((prev) => ({
        ...prev,
        ...userFromAuth({ ...prev, ...profile, userId: profile.id }),
      }))
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
      register,
      logout,
      refreshProfile,
    }),
    [token, user, bootstrapping, login, register, logout, refreshProfile],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
