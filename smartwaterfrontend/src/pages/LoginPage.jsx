import { useState } from 'react'
import { Navigate, useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import './AuthLayout.css'

/**
 * Visual-direction showcase page.
 * Redesigned in Japandi style — warm neutrals, quiet serif typography, and soft fades.
 */
export default function LoginPage() {
  const { login, isAuthenticated, user } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('admin@smartwater.local')
  const [password, setPassword] = useState('SmartWater#2024')
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
              <input
                className="sw-input"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
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
