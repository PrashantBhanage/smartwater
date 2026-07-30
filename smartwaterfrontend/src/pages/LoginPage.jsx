import { useState } from 'react'
import { Navigate, useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import './AuthLayout.css'
import { Eye, EyeOff } from "lucide-react"

/**
 * Visual-direction showcase page.
 * Redesigned in Japandi style — warm neutrals, quiet serif typography, and soft fades.
 */
export default function LoginPage() {
  const { login, isAuthenticated, user } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('admin@smartwater.local')
  const [password, setPassword] = useState('SmartWater#2024')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  if (isAuthenticated) {
    return <Navigate to={user?.role === 'ADMIN' ? '/admin' : '/resident'} replace />
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const data = await login(email.trim(), password)
      navigate(data.role === 'ADMIN' ? '/admin' : '/resident', { replace: true })
    } catch (err) {
      setError(err.message || 'Sign-in failed. Check your credentials and try again.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="auth">
      <div className="auth__grain" aria-hidden />

      <main className="auth__stage">
        <section className="auth__panel sw-panel" aria-labelledby="login-brand">
          <header className="auth__header">
            <h1 id="login-brand" className="auth__brand">
              SmartWater
            </h1>
            <p className="auth__tagline">
              Apartment water usage, cost splitting, and community billing.
            </p>
          </header>

          <form className="auth__form" onSubmit={handleSubmit} noValidate>
            {error ? (
              <div className="sw-banner sw-banner--error" role="alert">
                {error}
              </div>
            ) : null}

            <label className="sw-field">
              <span className="sw-field__label">Email</span>
              <input
                className="sw-input"
                type="email"
                autoComplete="username"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </label>

           <label className="sw-field">
  <span className="sw-field__label">Password</span>

  <div className="sw-password">
    <input
      className="sw-input"
      type={showPassword ? "text" : "password"}
      autoComplete="current-password"
      value={password}
      onChange={(e) => setPassword(e.target.value)}
      required
    />

    <button
      type="button"
      className="sw-password__toggle"
      onClick={() => setShowPassword(!showPassword)}
      aria-label={showPassword ? "Hide password" : "Show password"}
    >
      {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
    </button>
  </div>
</label>

            <button
              type="submit"
              className="sw-btn sw-btn--primary auth__submit"
              disabled={loading}
            >
              {loading ? <span className="sw-spinner" aria-hidden /> : null}
              {loading ? 'Signing in…' : 'Sign in'}
            </button>
          </form>

          <footer className="auth__footer">
            New resident? <Link to="/register">Create an account</Link>
          </footer>
        </section>
      </main>
    </div>
  )
}
