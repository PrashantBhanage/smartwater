import { useEffect, useState } from 'react'
import { useAuth } from '../context/AuthContext'
import { getApartmentAlerts } from '../api'

function formatNumber(value, suffix = '') {
  return `${Number(value ?? 0).toLocaleString(undefined, { maximumFractionDigits: 2 })}${suffix}`
}

function severityColor(severity) {
  if (severity === 'HIGH' || severity === 'CRITICAL') return 'sw-status--red'
  if (severity === 'MEDIUM') return 'sw-status--yellow'
  return 'sw-status--neutral'
}

function typeColor(type) {
  if (type === 'LEAK_SUSPECTED') return 'sw-status--red'
  if (type === 'THRESHOLD_EXCEEDED') return 'sw-status--yellow'
  return 'sw-status--neutral'
}

export default function AdminAlertsPage() {
  const { user } = useAuth()
  const [alerts, setAlerts] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true

    async function loadAlerts() {
      if (!user?.apartmentId) {
        setError('You are not associated with an apartment complex.')
        setLoading(false)
        return
      }
      try {
        const data = await getApartmentAlerts(user.apartmentId, 30)
        const sorted = [...data].sort((a, b) => new Date(b.readingDate) - new Date(a.readingDate))
        if (active) setAlerts(sorted)
      } catch (err) {
        if (active) setError(err.message || 'Failed to load alerts.')
      } finally {
        if (active) setLoading(false)
      }
    }

    loadAlerts()
    return () => {
      active = false
    }
  }, [user?.apartmentId])

  return (
    <div className="sw-fade-in" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sw-space-6)' }}>
      <header>
        <h1 className="sw-page-title">Alerts</h1>
        <p className="sw-page-subtitle">
          Threshold and anomaly alerts across all households, from the last 30 days.
        </p>
      </header>

      {error ? <div className="sw-banner sw-banner--error" role="alert">{error}</div> : null}

      <section className="sw-panel" style={{ padding: 'var(--sw-space-5)' }}>
        {loading ? (
          <div style={{ display: 'grid', placeItems: 'center', minHeight: 160 }}>
            <div className="sw-spinner" style={{ width: 32, height: 32 }} />
          </div>
        ) : alerts.length === 0 ? (
          <div className="sw-empty">No alerts raised in the last 30 days.</div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="sw-table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Household</th>
                  <th>Alert Type</th>
                  <th>Severity</th>
                  <th>Usage</th>
                  <th>Message</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {alerts.map((alert) => (
                  <tr key={alert.id}>
                    <td>{alert.readingDate}</td>
                    <td>{alert.flatNumber}</td>
                    <td>
                      <span className={`sw-status ${typeColor(alert.alertType)}`}>{alert.alertType}</span>
                    </td>
                    <td>
                      <span className={`sw-status ${severityColor(alert.severity)}`}>{alert.severity}</span>
                    </td>
                    <td>{formatNumber(alert.usageLiters, ' L')}</td>
                    <td>{alert.message}</td>
                    <td>
                      <span className={`sw-status ${alert.acknowledged ? 'sw-status--green' : 'sw-status--neutral'}`}>
                        {alert.acknowledged ? 'Acknowledged' : 'Open'}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}
