import { useEffect, useState } from 'react'
import { Navigate, useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { apartmentsApi, householdsApi } from '../api'
import './AuthLayout.css'

/**
 * Resident self-registration page.
 * Styled in Japandi style matching LoginPage.
 */
export default function RegisterPage() {
  const { register, isAuthenticated, user } = useAuth()
  const navigate = useNavigate()

  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [selectedApartmentId, setSelectedApartmentId] = useState('')
  const [selectedHouseholdId, setSelectedHouseholdId] = useState('')

  const [apartments, setApartments] = useState([])
  const [households, setHouseholds] = useState([])
  const [loadingApartments, setLoadingApartments] = useState(false)
  const [loadingHouseholds, setLoadingHouseholds] = useState(false)

  const [error, setError] = useState('')
  const [submitLoading, setSubmitLoading] = useState(false)


  // Fetch apartments on mount
  useEffect(() => {
    async function fetchApartments() {
      setLoadingApartments(true)
      try {
        const data = await apartmentsApi.list()
        setApartments(data)
      } catch (err) {
        setError(err.message || 'Failed to load apartments.')
      } finally {
        setLoadingApartments(false)
      }
    }
    fetchApartments()
  }, [])

  // Fetch households when apartment changes
  useEffect(() => {
    if (!selectedApartmentId) {
      setHouseholds([])
      setSelectedHouseholdId('')
      return
    }

    async function fetchHouseholds() {
      setLoadingHouseholds(true)
      try {
        const data = await householdsApi.listByApartment(selectedApartmentId)
        setHouseholds(data)
      } catch (err) {
        setError(err.message || 'Failed to load households.')
      } finally {
        setLoadingHouseholds(false)
      }
    }
    fetchHouseholds()
  }, [selectedApartmentId])

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')

    if (!fullName.trim()) return setError('Full name is required.')
    if (!email.trim()) return setError('Email is required.')
    if (!password) return setError('Password is required.')
    if (password.length < 8) return setError('Password must be at least 8 characters.')
    if (!selectedApartmentId) return setError('Please select an apartment.')
    if (!selectedHouseholdId) return setError('Please select your flat number.')

    setSubmitLoading(true)
    try {
      await register({
        fullName: fullName.trim(),
        email: email.trim(),
        password,
        role: 'RESIDENT',
        householdId: Number(selectedHouseholdId),
      })
      navigate('/resident', { replace: true })
    } catch (err) {
      setError(err.message || 'Registration failed.')
    } finally {
      setSubmitLoading(false)
    }
  }

  // Redirect if already authenticated
  if (isAuthenticated) {
    return <Navigate to={user?.role === 'ADMIN' ? '/admin' : '/resident'} replace />
  }

  return (
    <div className="auth">
      <div className="auth__grain" aria-hidden />

      <main className="auth__stage">
        <section className="auth__panel sw-panel" aria-labelledby="register-brand">
          <header className="auth__header">
            <h1 id="register-brand" className="auth__brand">
              Join SmartWater
            </h1>
            <p className="auth__tagline">
              Register as a resident for your household.
            </p>
          </header>

          <form className="auth__form" onSubmit={handleSubmit} noValidate>
            {error ? (
              <div className="sw-banner sw-banner--error" role="alert">
                {error}
              </div>
            ) : null}

            <label className="sw-field">
              <span className="sw-field__label">Full Name</span>
              <input
                className="sw-input"
                type="text"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                required
              />
            </label>

            <label className="sw-field">
              <span className="sw-field__label">Email</span>
              <input
                className="sw-input"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </label>

            <label className="sw-field">
              <span className="sw-field__label">Password (Min 8 characters)</span>
              <input
                className="sw-input"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </label>

            <label className="sw-field">
              <span className="sw-field__label">Apartment Complex</span>
              <select
                className="sw-select"
                value={selectedApartmentId}
                onChange={(e) => setSelectedApartmentId(e.target.value)}
                required
                disabled={loadingApartments}
              >
                <option value="">
                  {loadingApartments ? 'Loading complexes…' : 'Select your complex'}
                </option>
                {apartments.map((apt) => (
                  <option key={apt.id} value={apt.id}>
                    {apt.name}
                  </option>
                ))}
              </select>
            </label>

            <label className="sw-field">
              <span className="sw-field__label">Flat Number</span>
              <select
                className="sw-select"
                value={selectedHouseholdId}
                onChange={(e) => setSelectedHouseholdId(e.target.value)}
                required
                disabled={!selectedApartmentId || loadingHouseholds}
              >
                <option value="">
                  {!selectedApartmentId
                    ? 'Select an apartment first'
                    : loadingHouseholds
                      ? 'Loading flats…'
                      : households.length === 0
                        ? 'No flats found. Register flat as admin first.'
                        : 'Select your flat number'}
                </option>
                {households.map((hh) => (
                  <option key={hh.id} value={hh.id}>
                    {hh.flatNumber}
                  </option>
                ))}
              </select>
            </label>

            <button
              type="submit"
              className="sw-btn sw-btn--primary auth__submit"
              disabled={submitLoading}
            >
              {submitLoading ? <span className="sw-spinner" aria-hidden /> : null}
              {submitLoading ? 'Creating account…' : 'Create resident account'}
            </button>
          </form>

          <footer className="auth__footer">
            Already registered? <Link to="/login">Sign in</Link>
          </footer>
        </section>
      </main>
    </div>
  )
}
