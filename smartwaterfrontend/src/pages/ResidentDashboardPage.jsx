import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { householdsApi, apartmentsApi, usageLogsApi } from '../api'
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts'

const CustomizedDot = (props) => {
  const { cx, cy, payload } = props
  if (!cx || !cy) return null
  let color = 'var(--sw-green)'
  if (payload.usageStatus === 'YELLOW') color = 'var(--sw-yellow)'
  else if (payload.usageStatus === 'RED') color = 'var(--sw-red)'
  return (
    <circle cx={cx} cy={cy} r={5} fill={color} stroke="#fff" strokeWidth={1.5} />
  )
}

export default function ResidentDashboardPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [household, setHousehold] = useState(null)
  const [apartment, setApartment] = useState(null)
  const [logs, setLogs] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [activeTab, setActiveTab] = useState('usage')

  useEffect(() => {
    async function loadDashboardData() {
      setError('')
      setLoading(true)

      if (!user?.householdId) {
        setError('No household assigned to your user account. Please contact your administrator.')
        setLoading(false)
        return
      }
      try {
        const hhData = await householdsApi.get(user.householdId)
        setHousehold(hhData)

        if (hhData.apartmentId) {
          const aptData = await apartmentsApi.get(hhData.apartmentId)
          setApartment(aptData)
        }

        const logsData = await usageLogsApi.list(user.householdId)
        // Sort logs: oldest reading date first for chart progression
        const sortedLogs = [...logsData].sort((a, b) => new Date(a.readingDate) - new Date(b.readingDate))
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

  const totalVolume = logs.reduce((acc, log) => acc + log.volumeUsedLiters, 0)
  const avgVolume = logs.length > 0 ? (totalVolume / logs.length).toFixed(1) : 0
  const greenLogs = logs.filter(l => l.usageStatus === 'GREEN').length
  const yellowLogs = logs.filter(l => l.usageStatus === 'YELLOW').length
  const redLogs = logs.filter(l => l.usageStatus === 'RED').length

  const handleTabChange = (tab) => {
    setActiveTab(tab)
    if (tab === 'invoices') {
      navigate('/resident/invoices')
    } else if (tab === 'alerts') {
      navigate('/resident/alerts')
    }
  }

  return (
    <div className="sw-fade-in" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sw-space-6)' }}>
      <header>
        <h1 className="sw-page-title">Welcome home, {user.fullName}</h1>
        <p className="sw-page-subtitle">
          Your household water usage summary for Flat {household?.flatNumber} at {apartment?.name}.
        </p>
      </header>

      {/* Tabs */}
      <div style={{
        display: 'flex',
        borderBottom: '1px solid var(--sw-border)',
        gap: '24px',
        marginBottom: '12px'
      }}>
        <button
          onClick={() => handleTabChange('usage')}
          style={{
            background: 'none',
            border: 'none',
            padding: '12px 4px',
            cursor: 'pointer',
            fontSize: 'var(--sw-fs-base)',
            fontWeight: 500,
            color: activeTab === 'usage' ? 'var(--sw-accent)' : 'var(--sw-text-secondary)',
            borderBottom: activeTab === 'usage' ? '2px solid var(--sw-accent)' : 'none',
            outline: 'none'
          }}
        >
          Usage
        </button>
        <button
          onClick={() => handleTabChange('invoices')}
          style={{
            background: 'none',
            border: 'none',
            padding: '12px 4px',
            cursor: 'pointer',
            fontSize: 'var(--sw-fs-base)',
            fontWeight: 500,
            color: activeTab === 'invoices' ? 'var(--sw-accent)' : 'var(--sw-text-secondary)',
            borderBottom: activeTab === 'invoices' ? '2px solid var(--sw-accent)' : 'none',
            outline: 'none'
          }}
        >
          Invoices
        </button>
        <button
          onClick={() => handleTabChange('alerts')}
          style={{
            background: 'none',
            border: 'none',
            padding: '12px 4px',
            cursor: 'pointer',
            fontSize: 'var(--sw-fs-base)',
            fontWeight: 500,
            color: activeTab === 'alerts' ? 'var(--sw-accent)' : 'var(--sw-text-secondary)',
            borderBottom: activeTab === 'alerts' ? '2px solid var(--sw-accent)' : 'none',
            outline: 'none'
          }}
        >
          Alerts
        </button>
      </div>

      {activeTab === 'usage' && (
        <>
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

          {/* Consumption Chart Panel */}
          <div className="sw-panel" style={{ padding: 'var(--sw-space-5)', background: 'var(--sw-surface)' }}>
            <h2 className="sw-page-title" style={{ fontSize: 'var(--sw-fs-lg)', marginBottom: 24 }}>Consumption Trends</h2>
            {logs.length === 0 ? (
              <div className="sw-empty">No usage logs available to plot.</div>
            ) : (
              <ResponsiveContainer width="100%" height={320}>
                <LineChart data={logs} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--sw-border)" />
                  <XAxis dataKey="readingDate" stroke="var(--sw-text-secondary)" fontSize={12} />
                  <YAxis stroke="var(--sw-text-secondary)" fontSize={12} unit=" L" />
                  <Tooltip
                    contentStyle={{
                      background: 'var(--sw-surface-raised)',
                      border: '1px solid var(--sw-border)',
                      borderRadius: 'var(--sw-radius)'
                    }}
                    labelStyle={{ fontWeight: 600, color: 'var(--sw-text)' }}
                  />
                  <Line
                    type="monotone"
                    dataKey="volumeUsedLiters"
                    name="Usage (L)"
                    stroke="var(--sw-accent)"
                    strokeWidth={2}
                    dot={<CustomizedDot />}
                    activeDot={{ r: 7 }}
                  />
                </LineChart>
              </ResponsiveContainer>
            )}
          </div>

          {/* Flat & Apartment info */}
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
        </>
      )}
    </div>
  )
}
