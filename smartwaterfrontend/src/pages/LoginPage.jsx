import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import './LoginPage.css'

/**
 * Visual-direction showcase page.
 * Frosted panel on a soft atmospheric field — macOS System Settings tone.
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
    <div className="login">
      <div className="login__atmosphere" aria-hidden>
        <div className="login__orb login__orb--a" />
        <div className="login__orb login__orb--b" />
        <div className="login__grain" />
      </div>

      <main className="login__stage">
        <section className="login__panel sw-glass" aria-labelledby="login-brand">
          <header className="login__header">
            <div className="login__mark" aria-hidden />
            <h1 id="login-brand" className="login__brand">
              SmartWater
            </h1>
            <p className="login__tagline">
              Apartment water usage, shared costs, and billing — clearly.
            </p>
          </header>

          <form className="login__form" onSubmit={handleSubmit} noValidate>
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
              className="sw-btn sw-btn--primary login__submit"
              disabled={loading}
            >
              {loading ? <span className="sw-spinner" aria-hidden /> : null}
              {loading ? 'Signing in…' : 'Sign in'}
            </button>
          </form>

          <p className="login__hint">
            Default admin seed: <code>admin@smartwater.local</code>
          </p>
        </section>
      </main>
    </div>
  )
}
