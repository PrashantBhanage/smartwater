import { useEffect, useState } from 'react'
import { getMyAlerts } from '../api'

function formatNumber(value, suffix = '') {
  return `${Number(value ?? 0).toLocaleString(undefined, { maximumFractionDigits: 2 })}${suffix}`
}

export default function ResidentAlertsPage() {
  const [alerts, setAlerts] = useState([])
  const [filteredAlerts, setFilteredAlerts] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [filterStart, setFilterStart] = useState('')
  const [filterEnd, setFilterEnd] = useState('')

  useEffect(() => {
    let active = true
    async function loadAlerts() {
      try {
        const data = await getMyAlerts()
        if (active) {
          // Sort by date descending
          const sorted = [...data].sort((a, b) => new Date(b.readingDate) - new Date(a.readingDate))
          setAlerts(sorted)
          setFilteredAlerts(sorted)
        }
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
  }, [])

  useEffect(() => {
    let result = [...alerts]
    if (filterStart) {
      result = result.filter((a) => a.readingDate >= filterStart)
    }
    if (filterEnd) {
      result = result.filter((a) => a.readingDate <= filterEnd)
    }
    setFilteredAlerts(result)
  }, [filterStart, filterEnd, alerts])

  const getStatusColor = (type) => {
    if (type === 'THRESHOLD_EXCEEDED') return 'yellow'
    if (type === 'LEAK_SUSPECTED' || type === 'ANOMALY_DETECTED') return 'red'
    return 'green'
  }

  return (
    <div className="sw-fade-in" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sw-space-6)' }}>
      <header>
        <h1 className="sw-page-title">Alerts</h1>
        <p className="sw-page-subtitle">Water consumption alerts and anomalies detected for your household.</p>
      </header>

      {error && <div className="sw-banner sw-banner--error">{error}</div>}

      {/* Date Filter Panel */}
      <section className="sw-panel" style={{ padding: 'var(--sw-space-4)' }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '16px', alignItems: 'center' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ fontSize: 'var(--sw-fs-sm)', color: 'var(--sw-text-secondary)', fontWeight: 500 }}>Filter from:</span>
            <input
              type="date"
              className="sw-input"
              style={{ minHeight: '38px', width: '160px' }}
              value={filterStart}
              onChange={(e) => setFilterStart(e.target.value)}
              aria-label="Start date filter"
            />
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ fontSize: 'var(--sw-fs-sm)', color: 'var(--sw-text-secondary)', fontWeight: 500 }}>to:</span>
            <input
              type="date"
              className="sw-input"
              style={{ minHeight: '38px', width: '160px' }}
              value={filterEnd}
              onChange={(e) => setFilterEnd(e.target.value)}
              aria-label="End date filter"
            />
          </div>
          {(filterStart || filterEnd) && (
            <button
              className="sw-btn sw-btn--ghost"
              style={{ minHeight: '38px' }}
              onClick={() => {
                setFilterStart('')
                setFilterEnd('')
              }}
            >
              Clear filters
            </button>
          )}
        </div>
      </section>

      {/* Alerts Table */}
      <section className="sw-panel" style={{ padding: 'var(--sw-space-5)' }}>
        {loading ? (
          <div style={{ display: 'grid', placeItems: 'center', minHeight: 160 }}>
            <div className="sw-spinner" style={{ width: 32, height: 32 }} />
          </div>
        ) : filteredAlerts.length === 0 ? (
          <div className="sw-empty">No alerts found.</div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="sw-table">
              <thead>
                <tr>
                  <th>Reading Date</th>
                  <th>Flat</th>
                  <th>Alert Type</th>
                  <th>Usage (Liters)</th>
                  <th>Message</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {filteredAlerts.map((alert) => (
                  <tr key={alert.id}>
                    <td>{alert.readingDate}</td>
                    <td>{alert.flatNumber}</td>
                    <td>
                      <span className={`sw-status sw-status--${getStatusColor(alert.alertType)}`}>
                        {alert.alertType}
                      </span>
                    </td>
                    <td>{formatNumber(alert.usageLiters, ' L')}</td>
                    <td>{alert.message}</td>
                    <td>
                      <span className={`sw-status sw-status--${alert.acknowledged ? 'green' : 'neutral'}`}>
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
