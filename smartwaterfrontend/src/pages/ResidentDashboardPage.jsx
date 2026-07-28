import { useEffect, useState } from 'react'
import { useAuth } from '../context/AuthContext'
import { householdsApi, apartmentsApi, usageLogsApi } from '../api'

/**
 * Resident Dashboard Page.
 * Displays the logged-in resident's apartment/household information
 * and their water consumption log table with alert indicators.
 * Designed in a warm, minimal Japandi style.
 */
export default function ResidentDashboardPage() {
  const { user } = useAuth()
  const [household, setHousehold] = useState(null)
  const [apartment, setApartment] = useState(null)
  const [logs, setLogs] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!user?.householdId) {
      setError('No household assigned to your user account. Please contact your administrator.')
      setLoading(false)
      return
    }

    async function loadDashboardData() {
      try {
        const hhData = await householdsApi.get(user.householdId)
        setHousehold(hhData)

        // Fetch apartment details
        if (hhData.apartmentId) {
          const aptData = await apartmentsApi.get(hhData.apartmentId)
          setApartment(aptData)
        }

        // Fetch usage logs
        const logsData = await usageLogsApi.list(user.householdId)
        // Sort logs: newest reading date first
        const sortedLogs = [...logsData].sort((a, b) => new Date(b.readingDate) - new Date(a.readingDate))
        setLogs(sortedLogs)
      } catch (err) {
        setError(err.message || 'Failed to load dashboard data.')
      } finally {
        setLoading(false)
      }
    }

    loadDashboardData()
  }, [user?.householdId])

  if (loading) {
    return (
      <div style={{ display: 'grid', placeItems: 'center', minHeight: '50vh' }}>
        <div className="sw-spinner" style={{ width: 32, height: 32 }} />
      </div>
    )
  }

  if (error) {
    return (
      <div className="sw-banner sw-banner--error" style={{ margin: '20px 0' }}>
        {error}
      </div>
    )
  }

  // Calculate status summaries
  const totalVolume = logs.reduce((acc, log) => acc + log.volumeUsedLiters, 0)
  const avgVolume = logs.length > 0 ? (totalVolume / logs.length).toFixed(1) : 0
  const greenLogs = logs.filter(l => l.usageStatus === 'GREEN').length
  const yellowLogs = logs.filter(l => l.usageStatus === 'YELLOW').length
  const redLogs = logs.filter(l => l.usageStatus === 'RED').length

  return (
    <div className="sw-fade-in" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sw-space-6)' }}>
      <header>
        <h1 className="sw-page-title">Welcome home, {user.fullName}</h1>
        <p className="sw-page-subtitle">
          Your household water usage summary for Flat {household?.flatNumber} at {apartment?.name}.
        </p>
      </header>

      {/* Summary Row */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
        gap: 'var(--sw-space-4)'
      }}>
        <div className="sw-panel" style={{ padding: 'var(--sw-space-4)', background: 'var(--sw-surface)' }}>
          <div style={{ fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
            Daily Threshold
          </div>
          <div style={{ fontFamily: 'var(--sw-font-display)', fontSize: 'var(--sw-fs-xl)', marginTop: 8, fontWeight: 500 }}>
            {household?.dailyThresholdLiters} <span style={{ fontSize: 'var(--sw-fs-sm)', fontWeight: 400 }}>Liters</span>
          </div>
          <p style={{ fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-tertiary)', margin: '8px 0 0' }}>
            Calculated target daily usage.
          </p>
        </div>

        <div className="sw-panel" style={{ padding: 'var(--sw-space-4)', background: 'var(--sw-surface)' }}>
          <div style={{ fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
            Average Consumption
          </div>
          <div style={{ fontFamily: 'var(--sw-font-display)', fontSize: 'var(--sw-fs-xl)', marginTop: 8, fontWeight: 500 }}>
            {avgVolume} <span style={{ fontSize: 'var(--sw-fs-sm)', fontWeight: 400 }}>Liters</span>
          </div>
          <p style={{ fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-tertiary)', margin: '8px 0 0' }}>
            Based on {logs.length} logged days.
          </p>
        </div>

        <div className="sw-panel" style={{ padding: 'var(--sw-space-4)', background: 'var(--sw-surface)' }}>
          <div style={{ fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
            Alert Status
          </div>
          <div style={{ display: 'flex', gap: 6, marginTop: 12 }}>
            <span className="sw-status sw-status--green">{greenLogs} Green</span>
            <span className="sw-status sw-status--yellow">{yellowLogs} Yellow</span>
            <span className="sw-status sw-status--red">{redLogs} Red</span>
          </div>
          <p style={{ fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-tertiary)', margin: '10px 0 0' }}>
            Historical daily breakdown.
          </p>
        </div>
      </div>

      {/* Household & Apartment info */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))',
        gap: 'var(--sw-space-4)'
      }}>
        <div className="sw-panel" style={{ padding: 'var(--sw-space-5)', background: 'var(--sw-surface)' }}>
          <h2 className="sw-page-title" style={{ fontSize: 'var(--sw-fs-lg)', marginBottom: 16 }}>Flat Details</h2>
          <table className="sw-table" style={{ width: '100%' }}>
            <tbody>
              <tr>
                <td style={{ color: 'var(--sw-text-tertiary)', paddingLeft: 0 }}>Flat Number</td>
                <td style={{ textAlign: 'right', fontWeight: 500, paddingRight: 0 }}>{household?.flatNumber}</td>
              </tr>
              <tr>
                <td style={{ color: 'var(--sw-text-tertiary)', paddingLeft: 0 }}>Occupancy</td>
                <td style={{ textAlign: 'right', fontWeight: 500, paddingRight: 0 }}>{household?.occupancyCount} resident(s)</td>
              </tr>
              <tr>
                <td style={{ color: 'var(--sw-text-tertiary)', paddingLeft: 0 }}>Meter Config</td>
                <td style={{ textAlign: 'right', paddingRight: 0 }}>
                  <span className={`sw-status ${household?.hasMeter ? 'sw-status--green' : 'sw-status--neutral'}`}>
                    {household?.hasMeter ? 'Smart Meter Active' : 'No Meter'}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div className="sw-panel" style={{ padding: 'var(--sw-space-5)', background: 'var(--sw-surface)' }}>
          <h2 className="sw-page-title" style={{ fontSize: 'var(--sw-fs-lg)', marginBottom: 16 }}>Apartment Details</h2>
          <table className="sw-table" style={{ width: '100%' }}>
            <tbody>
              <tr>
                <td style={{ color: 'var(--sw-text-tertiary)', paddingLeft: 0 }}>Complex Name</td>
                <td style={{ textAlign: 'right', fontWeight: 500, paddingRight: 0 }}>{apartment?.name}</td>
              </tr>
              <tr>
                <td style={{ color: 'var(--sw-text-tertiary)', paddingLeft: 0 }}>Address</td>
                <td style={{ textAlign: 'right', fontWeight: 500, paddingRight: 0, fontSize: 'var(--sw-fs-xs)', maxWidth: '20ch', wordBreak: 'break-word' }}>{apartment?.address}</td>
              </tr>
              <tr>
                <td style={{ color: 'var(--sw-text-tertiary)', paddingLeft: 0 }}>Admin Contact</td>
                <td style={{ textAlign: 'right', fontWeight: 500, paddingRight: 0 }}>{apartment?.adminContact}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      {/* Usage Logs Table */}
      <div className="sw-panel" style={{ padding: 'var(--sw-space-5)', background: 'var(--sw-surface)' }}>
        <h2 className="sw-page-title" style={{ fontSize: 'var(--sw-fs-lg)', marginBottom: 16 }}>Water Usage History</h2>
        {logs.length === 0 ? (
          <div className="sw-empty">No water usage logs recorded for your household yet.</div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="sw-table">
              <thead>
                <tr>
                  <th>Reading Date</th>
                  <th>Meter Reading</th>
                  <th>Volume Used (Liters)</th>
                  <th>Daily Limit Status</th>
                </tr>
              </thead>
              <tbody>
                {logs.map((log) => (
                  <tr key={log.id}>
                    <td>{log.readingDate}</td>
                    <td>{log.meterReadingValue != null ? `${log.meterReadingValue.toFixed(3)} m³` : '—'}</td>
                    <td>{log.volumeUsedLiters.toFixed(1)} L</td>
                    <td>
                      <span className={`sw-status sw-status--${log.usageStatus.toLowerCase()}`}>
                        {log.usageStatus}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
