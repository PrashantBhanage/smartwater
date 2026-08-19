import { useEffect, useState, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { householdsApi, apartmentsApi, usageLogsApi, invoicesApi } from '../api'
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

function formatMoney(value) {
  return Number(value ?? 0).toLocaleString(undefined, {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 2,
  })
}

export default function ResidentDashboardPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [household, setHousehold] = useState(null)
  const [apartment, setApartment] = useState(null)
  const [allHouseholds, setAllHouseholds] = useState([])
  const [logs, setLogs] = useState([])
  const [invoices, setInvoices] = useState([])
  const [loading, setLoading] = useState(true)
  const [downloadingPdf, setDownloadingPdf] = useState(false)
  const [error, setError] = useState('')
  const [activeTab, setActiveTab] = useState('usage')
  const [chartMode, setChartMode] = useState('daily') // 'daily' | 'monthly'

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
          const [aptData, aptHouseholds] = await Promise.all([
            apartmentsApi.get(hhData.apartmentId),
            householdsApi.listByApartment(hhData.apartmentId),
          ])
          setApartment(aptData)
          setAllHouseholds(aptHouseholds)
        }

        const [logsData, invData] = await Promise.all([
          usageLogsApi.list(user.householdId),
          invoicesApi.listByHousehold(user.householdId).catch(() => []),
        ])

        // Sort logs: oldest reading date first for chart progression
        const sortedLogs = [...logsData].sort((a, b) => new Date(a.readingDate) - new Date(b.readingDate))
        setLogs(sortedLogs)
        setInvoices([...invData].sort((a, b) => b.id - a.id))
      } catch (err) {
        setError(err.message || 'Failed to load dashboard data.')
      } finally {
        setLoading(false)
      }
    }

    loadDashboardData()
  }, [user?.householdId])

  // Aggregate monthly consumption trends for Recharts
  const monthlyLogs = useMemo(() => {
    if (!logs.length) return []
    const groups = {}
    logs.forEach((log) => {
      if (!log.readingDate) return
      const date = new Date(log.readingDate)
      const monthKey = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
      const monthLabel = date.toLocaleString('default', { month: 'short', year: 'numeric' })
      if (!groups[monthKey]) {
        groups[monthKey] = { label: monthLabel, monthKey, totalLiters: 0, count: 0 }
      }
      groups[monthKey].totalLiters += log.volumeUsedLiters || 0
      groups[monthKey].count += 1
    })

    return Object.values(groups).map((g) => ({
      readingDate: g.label,
      volumeUsedLiters: Math.round(g.totalLiters),
      volumeKl: Number((g.totalLiters / 1000).toFixed(2)),
      daysLogged: g.count,
    }))
  }, [logs])

  // Peer Benchmarking Calculations
  const householdAvgDaily = useMemo(() => {
    if (!logs.length) return 0
    const total = logs.reduce((acc, l) => acc + (l.volumeUsedLiters || 0), 0)
    return Math.round(total / logs.length)
  }, [logs])

  const apartmentAvgDaily = useMemo(() => {
    if (!allHouseholds.length) return 400 // default benchmark
    // Compute total occupancy / threshold ratio across complex or use benchmark
    const totalThreshold = allHouseholds.reduce((acc, h) => acc + Number(h.dailyThresholdLiters || 500), 0)
    return Math.round(totalThreshold / allHouseholds.length)
  }, [allHouseholds])

  const benchmarkDiff = useMemo(() => {
    if (!apartmentAvgDaily) return 0
    return Math.round(((householdAvgDaily - apartmentAvgDaily) / apartmentAvgDaily) * 100)
  }, [householdAvgDaily, apartmentAvgDaily])

  const latestInvoice = invoices.length > 0 ? invoices[0] : null

  const handleDownloadPdf = async (invoiceId) => {
    if (!invoiceId) return
    setDownloadingPdf(true)
    try {
      await invoicesApi.downloadPdf(invoiceId)
    } catch (err) {
      alert('Failed to download invoice PDF: ' + (err.message || 'Server error'))
    } finally {
      setDownloadingPdf(false)
    }
  }

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
  const greenLogs = logs.filter((l) => l.usageStatus === 'GREEN').length
  const yellowLogs = logs.filter((l) => l.usageStatus === 'YELLOW').length
  const redLogs = logs.filter((l) => l.usageStatus === 'RED').length

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
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 16 }}>
        <div>
          <h1 className="sw-page-title">Welcome home, {user.fullName}</h1>
          <p className="sw-page-subtitle">
            Your household water usage summary for Flat {household?.flatNumber} at {apartment?.name}.
          </p>
        </div>
        {latestInvoice && (
          <button
            className="sw-btn sw-btn--primary"
            disabled={downloadingPdf}
            onClick={() => handleDownloadPdf(latestInvoice.id)}
            style={{ display: 'flex', alignItems: 'center', gap: 8 }}
          >
            {downloadingPdf ? 'Downloading PDF...' : `📄 Download PDF Invoice #${latestInvoice.id}`}
          </button>
        )}
      </header>

      {/* Navigation Tabs */}
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
          Usage & Benchmarking
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
          {/* Summary Cards */}
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

            {/* Current Billing Overview Quick Status */}
            {latestInvoice && (
              <div className="sw-panel" style={{ padding: 'var(--sw-space-4)', background: 'var(--sw-surface)', borderLeft: '4px solid var(--sw-accent)' }}>
                <div style={{ fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  Current Billing Status
                </div>
                <div style={{ fontFamily: 'var(--sw-font-display)', fontSize: 'var(--sw-fs-xl)', marginTop: 8, fontWeight: 500 }}>
                  {formatMoney(latestInvoice.totalAmount ?? latestInvoice.totalCharge)}
                </div>
                <div style={{ marginTop: 8, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <span className={`sw-status ${ (latestInvoice.status || latestInvoice.paidStatus) === 'PAID' ? 'sw-status--green' : 'sw-status--red'}`}>
                    {latestInvoice.status || latestInvoice.paidStatus}
                  </span>
                  <button
                    className="sw-btn sw-btn--secondary"
                    style={{ minHeight: 28, padding: '0 8px', fontSize: 'var(--sw-fs-xs)' }}
                    onClick={() => handleDownloadPdf(latestInvoice.id)}
                  >
                    PDF ⬇
                  </button>
                </div>
              </div>
            )}
          </div>

          {/* Peer Benchmarking Component */}
          <div className="sw-panel" style={{ padding: 'var(--sw-space-5)', background: 'var(--sw-surface)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
              <div>
                <h2 className="sw-page-title" style={{ fontSize: 'var(--sw-fs-lg)', margin: 0 }}>
                  🏢 Peer Benchmarking
                </h2>
                <p style={{ fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-tertiary)', margin: '4px 0 0' }}>
                  Real household consumption relative to apartment complex average ({allHouseholds.length} units).
                </p>
              </div>
              <span className={`sw-status ${benchmarkDiff <= 0 ? 'sw-status--green' : 'sw-status--yellow'}`} style={{ fontSize: 'var(--sw-fs-sm)' }}>
                {benchmarkDiff <= 0 ? `${Math.abs(benchmarkDiff)}% Below Complex Average 👏` : `${benchmarkDiff}% Above Complex Average ⚠️`}
              </span>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 'var(--sw-space-4)', marginTop: 12 }}>
              <div style={{ padding: 12, background: 'var(--sw-surface-raised)', borderRadius: 'var(--sw-radius)' }}>
                <span style={{ fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-secondary)' }}>My Flat (Flat {household?.flatNumber})</span>
                <div style={{ fontSize: 'var(--sw-fs-lg)', fontWeight: 600, color: 'var(--sw-text)', marginTop: 4 }}>
                  {householdAvgDaily} <span style={{ fontSize: 'var(--sw-fs-xs)', fontWeight: 400 }}>Liters/day</span>
                </div>
              </div>

              <div style={{ padding: 12, background: 'var(--sw-surface-raised)', borderRadius: 'var(--sw-radius)' }}>
                <span style={{ fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-secondary)' }}>Apartment Complex Average</span>
                <div style={{ fontSize: 'var(--sw-fs-lg)', fontWeight: 600, color: 'var(--sw-text)', marginTop: 4 }}>
                  {apartmentAvgDaily} <span style={{ fontSize: 'var(--sw-fs-xs)', fontWeight: 400 }}>Liters/day</span>
                </div>
              </div>
            </div>

            {/* Visual comparison bar */}
            <div style={{ marginTop: 16 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-secondary)', marginBottom: 6 }}>
                <span>Usage Comparison</span>
                <span>{householdAvgDaily} L vs {apartmentAvgDaily} L target</span>
              </div>
              <div style={{ height: 10, background: 'var(--sw-border)', borderRadius: 5, overflow: 'hidden', position: 'relative' }}>
                <div
                  style={{
                    height: '100%',
                    width: `${Math.min(100, Math.round((householdAvgDaily / (apartmentAvgDaily * 1.5)) * 100))}%`,
                    background: benchmarkDiff <= 0 ? 'var(--sw-green)' : 'var(--sw-yellow)',
                    borderRadius: 5,
                    transition: 'width 0.4s ease'
                  }}
                />
              </div>
            </div>
          </div>

          {/* Consumption Chart Panel (Daily & Monthly Trends) */}
          <div className="sw-panel" style={{ padding: 'var(--sw-space-5)', background: 'var(--sw-surface)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20, flexWrap: 'wrap', gap: 12 }}>
              <h2 className="sw-page-title" style={{ fontSize: 'var(--sw-fs-lg)', margin: 0 }}>
                Consumption Trends
              </h2>
              {/* Chart Mode Toggle */}
              <div style={{ display: 'flex', background: 'var(--sw-surface-raised)', borderRadius: 6, padding: 3, border: '1px solid var(--sw-border)' }}>
                <button
                  className="sw-btn"
                  onClick={() => setChartMode('daily')}
                  style={{
                    padding: '4px 12px',
                    fontSize: 'var(--sw-fs-xs)',
                    borderRadius: 4,
                    background: chartMode === 'daily' ? 'var(--sw-surface)' : 'transparent',
                    boxShadow: chartMode === 'daily' ? '0 1px 3px rgba(0,0,0,0.1)' : 'none',
                    fontWeight: chartMode === 'daily' ? 600 : 400,
                    cursor: 'pointer'
                  }}
                >
                  Daily Trend (L)
                </button>
                <button
                  className="sw-btn"
                  onClick={() => setChartMode('monthly')}
                  style={{
                    padding: '4px 12px',
                    fontSize: 'var(--sw-fs-xs)',
                    borderRadius: 4,
                    background: chartMode === 'monthly' ? 'var(--sw-surface)' : 'transparent',
                    boxShadow: chartMode === 'monthly' ? '0 1px 3px rgba(0,0,0,0.1)' : 'none',
                    fontWeight: chartMode === 'monthly' ? 600 : 400,
                    cursor: 'pointer'
                  }}
                >
                  Monthly Trend (L)
                </button>
              </div>
            </div>

            {chartMode === 'daily' ? (
              logs.length === 0 ? (
                <div className="sw-empty">No daily usage logs available to plot.</div>
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
                      name="Daily Usage (L)"
                      stroke="var(--sw-accent)"
                      strokeWidth={2}
                      dot={<CustomizedDot />}
                      activeDot={{ r: 7 }}
                    />
                  </LineChart>
                </ResponsiveContainer>
              )
            ) : (
              monthlyLogs.length === 0 ? (
                <div className="sw-empty">No monthly aggregate data available to plot.</div>
              ) : (
                <ResponsiveContainer width="100%" height={320}>
                  <LineChart data={monthlyLogs} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
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
                      name="Monthly Usage (L)"
                      stroke="var(--sw-green)"
                      strokeWidth={2.5}
                      dot={{ r: 5, fill: 'var(--sw-green)' }}
                      activeDot={{ r: 8 }}
                    />
                  </LineChart>
                </ResponsiveContainer>
              )
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
