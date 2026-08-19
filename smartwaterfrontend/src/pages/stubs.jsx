import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from "react-router-dom";
import DashboardChart from "../components/dashboard/DashboardChart";
import RecentBillingTable from "../components/dashboard/RecentBillingTable";
import StatCard from "../components/dashboard/StatCard";

import {
  FaHome,
  FaFileInvoice,
  FaMoneyBillWave,
  FaCalendarAlt,
} from "react-icons/fa";
import {
  alertsApi,
  billingApi,
  householdsApi,
  invoicesApi,
  purchasesApi,
  tariffApi,
  usageLogsApi,
  createBulkPurchase,
  getBulkPurchases,
} from '../api'
import { useAuth } from '../context/AuthContext'

function formatMoney(value) {
  return Number(value ?? 0).toLocaleString(undefined, {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 2,
  })
}

function formatNumber(value, suffix = '') {
  return `${Number(value ?? 0).toLocaleString(undefined, { maximumFractionDigits: 2 })}${suffix}`
}

function DataState({ loading, error, empty, children }) {
  if (loading) {
    return (
      <div style={{ display: 'grid', placeItems: 'center', minHeight: 160 }}>
        <div className="sw-spinner" style={{ width: 32, height: 32 }} />
      </div>
    )
  }

  if (error) {
    return (
      <div className="sw-banner sw-banner--error" role="alert">
        {error}
      </div>
    )
  }

  if (empty) return <div className="sw-empty">No records found.</div>
  return children
}

function PageHeader({ title, subtitle }) {
  return (
    <header>
      <h1 className="sw-page-title">{title}</h1>
      <p className="sw-page-subtitle">{subtitle}</p>
    </header>
  )
}

export function AdminOverviewPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [households, setHouseholds] = useState([])
  const [cycles, setCycles] = useState([])
  const [tariffs, setTariffs] = useState([])
  const [householdUsageMap, setHouseholdUsageMap] = useState({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true

    async function loadOverview() {
      if (!user?.apartmentId) {
        setError('You are not associated with an apartment complex.')
        setLoading(false)
        return
      }

      try {
        const [householdData, cycleData, tariffData] = await Promise.all([
          householdsApi.listByApartment(user.apartmentId),
          billingApi.listByApartment(user.apartmentId),
          tariffApi.list(user.apartmentId),
        ])

        if (!active) return

        setHouseholds(householdData)
        setCycles(cycleData)
        setTariffs(tariffData)

        // Load usage logs for all households to build All-Household Usage Comparison Bar Chart
        const usagePairs = await Promise.all(
          householdData.map(async (hh) => {
            try {
              const logs = await usageLogsApi.list(hh.id)
              const totalLiters = logs.reduce((sum, l) => sum + (l.volumeUsedLiters || 0), 0)
              return [hh.id, totalLiters]
            } catch {
              return [hh.id, 0]
            }
          })
        )
        if (active) {
          setHouseholdUsageMap(Object.fromEntries(usagePairs))
        }
      } catch (err) {
        if (active) {
          setError(err.message || 'Failed to load admin overview.')
        }
      } finally {
        if (active) {
          setLoading(false)
        }
      }
    }

    loadOverview()

    return () => {
      active = false
    }
  }, [user?.apartmentId])

  const openCycle = cycles.find((cycle) => cycle.status === 'OPEN')

  // Live All-Household Usage Comparison Chart Data
  const chartData = useMemo(() => {
    if (!households.length) return []
    return households.map((h) => ({
      name: `Flat ${h.flatNumber}`,
      value: householdUsageMap[h.id] ?? 0,
    }))
  }, [households, householdUsageMap])

  return (
    <div
      className="sw-fade-in"
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--sw-space-6)',
      }}
    >
      <PageHeader
        title="Overview"
        subtitle="Apartment operations, billing readiness, and recent setup status."
      />

      <DataState loading={loading} error={error}>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
            gap: '20px',
          }}
        >
          <StatCard
            title="Households"
            value={households.length}
            subtitle="Registered households"
            icon={<FaHome />}
          />

          <StatCard
            title="Billing Cycles"
            value={cycles.length}
            subtitle="Billing history"
            icon={<FaFileInvoice />}
          />

          <StatCard
            title="Tariff Plans"
            value={tariffs.length}
            subtitle="Configured tariff plans"
            icon={<FaMoneyBillWave />}
          />

          <StatCard
            title="Open Cycle"
            value={openCycle ? 'Active' : 'None'}
            subtitle={
              openCycle
                ? `${openCycle.cycleStartDate} - ${openCycle.cycleEndDate}`
                : 'No active billing cycle'
            }
            icon={<FaCalendarAlt />}
          />
        </div>

        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '3fr 1.1fr',
            gap: '20px',
            marginTop: '32px',
          }}
        >
          <DashboardChart
            data={chartData}
            title="All-Household Usage Comparison"
            subtitle="Total Water Volume Logged per Unit (Liters)"
          />

          <div
            className="sw-panel"
            style={{
              padding: '24px',
              borderRadius: '16px',
              height: 'fit-content',
              alignSelf: 'start',
            }}
          >
            <h3 style={{ margin: 0, marginBottom: '20px' }}>Quick Actions</h3>

            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                gap: '12px',
              }}
            >
              <button
                className="sw-btn sw-btn--primary"
                style={{ width: '100%' }}
                onClick={() => navigate('/admin/billing')}
              >
                Open Billing Cycle
              </button>

              <button
                className="sw-btn sw-btn--secondary"
                style={{ width: '100%' }}
                onClick={() => navigate('/admin/uploads')}
              >
                Manual Entry / CSV Upload
              </button>

              <button
                className="sw-btn sw-btn--secondary"
                style={{ width: '100%' }}
                onClick={() => navigate('/admin/billing')}
              >
                Create Tariff
              </button>
            </div>
          </div>
        </div>

        <div style={{ marginTop: '32px' }}>
          <RecentBillingTable cycles={cycles} />
        </div>
      </DataState>
    </div>
  )
}

export function AdminBillingPage() {
  const { user } = useAuth()
  const [tariffs, setTariffs] = useState([])
  const [cycles, setCycles] = useState([])
  const [purchasesByCycle, setPurchasesByCycle] = useState({})
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [tariffForm, setTariffForm] = useState({
    tier1LimitKl: '20',
    tier1Rate: '18',
    tier2Rate: '30',
    effectiveFromDate: new Date().toISOString().slice(0, 10),
  })
  const [cycleForm, setCycleForm] = useState(() => ({
    cycleStartDate: new Date(Date.now() - 6 * 86400000).toISOString().slice(0, 10),
    cycleEndDate: new Date().toISOString().slice(0, 10),
  }))
  const [purchaseForm, setPurchaseForm] = useState({
    cycleId: '',
    volumePurchasedKl: '40',
    unitCost: '28',
    purchaseDate: new Date().toISOString().slice(0, 10),
  })

  const allPurchases = useMemo(
    () => Object.values(purchasesByCycle).flat(),
    [purchasesByCycle],
  )

  async function loadBilling() {
    if (!user?.apartmentId) {
      setError('You are not associated with an apartment complex.')
      setLoading(false)
      return
    }

    setLoading(true)
    try {
      const [tariffData, cycleData] = await Promise.all([
        tariffApi.list(user.apartmentId),
        billingApi.listByApartment(user.apartmentId),
      ])
      const purchasePairs = await Promise.all(
        cycleData.map(async (cycle) => [cycle.id, await purchasesApi.listByCycle(cycle.id).catch(() => [])]),
      )
      setTariffs(tariffData)
      setCycles(cycleData)
      setPurchasesByCycle(Object.fromEntries(purchasePairs))
      setPurchaseForm((prev) => ({ ...prev, cycleId: prev.cycleId || String(cycleData[0]?.id ?? '') }))
    } catch (err) {
      setError(err.message || 'Failed to load billing data.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadBilling()
  }, [user?.apartmentId])

  async function handleCreateTariff(e) {
    e.preventDefault()
    setError('')
    setSuccess('')
    setSaving(true)
    try {
      await tariffApi.create({
        apartmentId: user.apartmentId,
        tier1LimitKl: Number(tariffForm.tier1LimitKl),
        tier1Rate: Number(tariffForm.tier1Rate),
        tier2Rate: Number(tariffForm.tier2Rate),
        effectiveFromDate: tariffForm.effectiveFromDate,
      })
      setSuccess('Tariff plan created successfully.')
      await loadBilling()
    } catch (err) {
      setError(err.message || 'Failed to create tariff plan.')
    } finally {
      setSaving(false)
    }
  }

  async function handleOpenCycle(e) {
    e.preventDefault()
    setError('')
    setSuccess('')
    setSaving(true)
    try {
      await billingApi.open({ apartmentId: user.apartmentId, ...cycleForm })
      setSuccess('Billing cycle opened successfully.')
      await loadBilling()
    } catch (err) {
      setError(err.message || 'Failed to open billing cycle.')
    } finally {
      setSaving(false)
    }
  }

  async function handleCreatePurchase(e) {
    e.preventDefault()
    setError('')
    setSuccess('')
    setSaving(true)
    try {
      await createBulkPurchase(user.apartmentId, {
        purchaseDate: purchaseForm.purchaseDate,
        volumeLiters: Number(purchaseForm.volumePurchasedKl) * 1000,
        unitCost: Number(purchaseForm.unitCost),
      })
      setSuccess('Bulk water purchase recorded successfully.')
      await loadBilling()
    } catch (err) {
      setError(err.message || 'Failed to record purchase.')
    } finally {
      setSaving(false)
    }
  }

  async function finalizeCycle(id) {
    setError('')
    setSuccess('')
    setSaving(true)
    try {
      const result = await billingApi.finalize(id)
      setSuccess(`Cycle finalized. Generated ${result.invoicesGenerated ?? 0} invoices.`)
      await loadBilling()
    } catch (err) {
      setError(err.message || 'Failed to finalize cycle.')
    } finally {
      setSaving(false)
    }
  }

  async function archiveCycle(id) {
    setError('')
    setSuccess('')
    setSaving(true)
    try {
      await billingApi.archive(id)
      setSuccess(`Cycle #${id} archived.`)
      await loadBilling()
    } catch (err) {
      setError(err.message || 'Failed to archive cycle.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="sw-fade-in" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sw-space-6)' }}>
      <PageHeader
        title="Billing"
        subtitle="Tariff configuration, water purchases, billing cycles, and generated invoices."
      />
      {error ? <div className="sw-banner sw-banner--error" role="alert">{error}</div> : null}
      {success ? <div className="sw-banner sw-banner--ok" role="alert">{success}</div> : null}
      <DataState loading={loading} error="">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 'var(--sw-space-5)' }}>
          <form className="sw-panel" onSubmit={handleCreateTariff} style={{ padding: 'var(--sw-space-4)', display: 'grid', gap: 12 }}>
            <h2 className="sw-page-title" style={{ fontSize: 'var(--sw-fs-lg)' }}>Tariff Config</h2>
            <label style={{ fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-secondary)' }}>Tier 1 Limit (kL)</label>
            <input className="sw-input" type="number" step="0.001" value={tariffForm.tier1LimitKl} onChange={(e) => setTariffForm({ ...tariffForm, tier1LimitKl: e.target.value })} aria-label="Tier 1 limit KL" required />
            <label style={{ fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-secondary)' }}>Tier 1 Rate (₹/kL)</label>
            <input className="sw-input" type="number" step="0.01" value={tariffForm.tier1Rate} onChange={(e) => setTariffForm({ ...tariffForm, tier1Rate: e.target.value })} aria-label="Tier 1 rate" required />
            <label style={{ fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-secondary)' }}>Tier 2 Rate (₹/kL)</label>
            <input className="sw-input" type="number" step="0.01" value={tariffForm.tier2Rate} onChange={(e) => setTariffForm({ ...tariffForm, tier2Rate: e.target.value })} aria-label="Tier 2 rate" required />
            <label style={{ fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-secondary)' }}>Effective From Date</label>
            <input className="sw-input" type="date" value={tariffForm.effectiveFromDate} onChange={(e) => setTariffForm({ ...tariffForm, effectiveFromDate: e.target.value })} aria-label="Effective from" required />
            <button className="sw-btn sw-btn--primary" disabled={saving}>{saving ? 'Saving...' : 'Save Tariff'}</button>
          </form>

          <form className="sw-panel" onSubmit={handleOpenCycle} style={{ padding: 'var(--sw-space-4)', display: 'grid', gap: 12 }}>
            <h2 className="sw-page-title" style={{ fontSize: 'var(--sw-fs-lg)' }}>Billing Cycles</h2>
            <label style={{ fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-secondary)' }}>Cycle Start Date</label>
            <input className="sw-input" type="date" value={cycleForm.cycleStartDate} onChange={(e) => setCycleForm({ ...cycleForm, cycleStartDate: e.target.value })} aria-label="Cycle start" required />
            <label style={{ fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-secondary)' }}>Cycle End Date</label>
            <input className="sw-input" type="date" value={cycleForm.cycleEndDate} onChange={(e) => setCycleForm({ ...cycleForm, cycleEndDate: e.target.value })} aria-label="Cycle end" required />
            <button className="sw-btn sw-btn--primary" disabled={saving}>{saving ? 'Opening...' : 'Open Cycle'}</button>
          </form>

          <form className="sw-panel" onSubmit={handleCreatePurchase} style={{ padding: 'var(--sw-space-4)', display: 'grid', gap: 12 }}>
            <h2 className="sw-page-title" style={{ fontSize: 'var(--sw-fs-lg)' }}>Purchases</h2>
            <label style={{ fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-secondary)' }}>Select Cycle</label>
            <select className="sw-select" value={purchaseForm.cycleId} onChange={(e) => setPurchaseForm({ ...purchaseForm, cycleId: e.target.value })} required>
              <option value="">Select cycle</option>
              {cycles.map((cycle) => <option key={cycle.id} value={cycle.id}>{cycle.cycleStartDate} to {cycle.cycleEndDate}</option>)}
            </select>
            <label style={{ fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-secondary)' }}>Volume Purchased (kL)</label>
            <input className="sw-input" type="number" step="0.001" value={purchaseForm.volumePurchasedKl} onChange={(e) => setPurchaseForm({ ...purchaseForm, volumePurchasedKl: e.target.value })} aria-label="Volume purchased KL" required />
            <label style={{ fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-secondary)' }}>Unit Cost (₹)</label>
            <input className="sw-input" type="number" step="0.01" value={purchaseForm.unitCost} onChange={(e) => setPurchaseForm({ ...purchaseForm, unitCost: e.target.value })} aria-label="Unit cost" required />
            <label style={{ fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-secondary)' }}>Purchase Date</label>
            <input className="sw-input" type="date" value={purchaseForm.purchaseDate} onChange={(e) => setPurchaseForm({ ...purchaseForm, purchaseDate: e.target.value })} aria-label="Purchase date" required />
            <button className="sw-btn sw-btn--primary" disabled={saving}>{saving ? 'Recording...' : 'Record Purchase'}</button>
          </form>
        </div>

        <section className="sw-panel" style={{ padding: 'var(--sw-space-5)' }}>
          <h2 className="sw-page-title" style={{ fontSize: 'var(--sw-fs-lg)', marginBottom: 16 }}>Current Tariffs</h2>
          <DataState loading={false} empty={tariffs.length === 0}>
            <table className="sw-table">
              <thead><tr><th>Effective Date</th><th>Tier 1 Limit</th><th>Tier 1 Rate</th><th>Tier 2 Rate</th></tr></thead>
              <tbody>{tariffs.map((plan) => <tr key={plan.id}><td>{plan.effectiveFromDate}</td><td>{formatNumber(plan.tier1LimitKl, ' KL')}</td><td>{formatMoney(plan.tier1Rate)}</td><td>{formatMoney(plan.tier2Rate)}</td></tr>)}</tbody>
            </table>
          </DataState>
        </section>

        <section className="sw-panel" style={{ padding: 'var(--sw-space-5)' }}>
          <h2 className="sw-page-title" style={{ fontSize: 'var(--sw-fs-lg)', marginBottom: 16 }}>Cycle Ledger</h2>
          <DataState loading={false} empty={cycles.length === 0}>
            <table className="sw-table">
              <thead><tr><th>Dates</th><th>Status</th><th>Purchases</th><th>Total Cost</th><th>Action</th></tr></thead>
              <tbody>
                {cycles.map((cycle) => {
                  const purchases = purchasesByCycle[cycle.id] ?? []
                  const totalCost = purchases.reduce((sum, purchase) => sum + Number(purchase.totalCost ?? 0), 0)
                  return (
                    <tr key={cycle.id}>
                      <td>{cycle.cycleStartDate} to {cycle.cycleEndDate}</td>
                      <td><span className="sw-status sw-status--neutral">{cycle.status}</span></td>
                      <td>{purchases.length}</td>
                      <td>{formatMoney(totalCost)}</td>
                      <td>
                        {cycle.status === 'OPEN' && (
                          <button className="sw-btn sw-btn--primary" style={{ minHeight: 30, padding: '0 10px', fontSize: 'var(--sw-fs-xs)' }} disabled={saving} onClick={() => finalizeCycle(cycle.id)}>
                            Finalize
                          </button>
                        )}
                        {cycle.status === 'FINALIZED' && (
                          <button className="sw-btn sw-btn--secondary" style={{ minHeight: 30, padding: '0 10px', fontSize: 'var(--sw-fs-xs)' }} disabled={saving} onClick={() => archiveCycle(cycle.id)}>
                            Archive
                          </button>
                        )}
                        {cycle.status === 'ARCHIVED' && (
                          <span className="sw-status sw-status--neutral">Archived</span>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </DataState>
        </section>

        <section className="sw-panel" style={{ padding: 'var(--sw-space-5)' }}>
          <h2 className="sw-page-title" style={{ fontSize: 'var(--sw-fs-lg)', marginBottom: 16 }}>Recorded Purchases</h2>
          <DataState loading={false} empty={allPurchases.length === 0}>
            <table className="sw-table">
              <thead><tr><th>Date</th><th>Cycle</th><th>Volume</th><th>Unit Cost</th><th>Total</th><th>Source</th></tr></thead>
              <tbody>{allPurchases.map((purchase) => <tr key={purchase.id}><td>{purchase.purchaseDate}</td><td>{purchase.cycleId ?? '-'}</td><td>{formatNumber(purchase.volumePurchasedKl || (purchase.volumeLiters / 1000), ' KL')}</td><td>{formatMoney(purchase.unitCost)}</td><td>{formatMoney(purchase.totalCost)}</td><td>{purchase.source ?? 'TANKER'}</td></tr>)}</tbody>
            </table>
          </DataState>
        </section>
      </DataState>
    </div>
  )
}

export function AdminUploadsPage() {
  const { user } = useAuth()
  const [households, setHouseholds] = useState([])
  const [file, setFile] = useState(null)
  const [summary, setSummary] = useState(null)
  const [loading, setLoading] = useState(false)
  const [savingManual, setSavingManual] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  // Manual Reading Form State
  const [manualForm, setManualForm] = useState({
    householdId: '',
    readingDate: new Date().toISOString().slice(0, 10),
    meterReadingValue: '',
    volumeUsedLiters: '',
  })

  useEffect(() => {
    async function load() {
      if (!user?.apartmentId) return
      try {
        const hhData = await householdsApi.listByApartment(user.apartmentId)
        setHouseholds(hhData)
        if (hhData.length > 0) {
          setManualForm((prev) => ({ ...prev, householdId: String(hhData[0].id) }))
        }
      } catch (err) {
        setError(err.message || 'Failed to load households.')
      }
    }
    load()
  }, [user?.apartmentId])

  async function handleManualSubmit(e) {
    e.preventDefault()
    if (!manualForm.householdId || !manualForm.volumeUsedLiters) return
    setError('')
    setSuccess('')
    setSavingManual(true)
    try {
      await usageLogsApi.create({
        householdId: Number(manualForm.householdId),
        readingDate: manualForm.readingDate,
        meterReadingValue: manualForm.meterReadingValue ? Number(manualForm.meterReadingValue) : null,
        volumeUsedLiters: Number(manualForm.volumeUsedLiters),
        source: 'MANUAL',
      })
      const selectedHh = households.find((h) => String(h.id) === String(manualForm.householdId))
      setSuccess(`Meter reading of ${manualForm.volumeUsedLiters} L logged successfully for Flat ${selectedHh?.flatNumber || ''}.`)
      setManualForm((prev) => ({ ...prev, meterReadingValue: '', volumeUsedLiters: '' }))
    } catch (err) {
      setError(err.message || 'Failed to submit manual meter reading.')
    } finally {
      setSavingManual(false)
    }
  }

  async function handleUpload(e) {
    e.preventDefault()
    if (!file) return
    setLoading(true)
    setError('')
    setSuccess('')
    setSummary(null)
    try {
      const res = await usageLogsApi.bulkUpload(file)
      setSummary(res)
      setSuccess('CSV bulk upload processed successfully.')
    } catch (err) {
      setError(err.message || 'CSV upload failed.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="sw-fade-in" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sw-space-6)' }}>
      <PageHeader title="Usage Logging & CSV Upload" subtitle="Log manual meter readings or bulk import household CSV readings." />
      
      {error ? <div className="sw-banner sw-banner--error" role="alert">{error}</div> : null}
      {success ? <div className="sw-banner sw-banner--ok" role="alert">{success}</div> : null}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 'var(--sw-space-6)' }}>
        {/* Manual Meter Reading Form */}
        <form className="sw-panel" onSubmit={handleManualSubmit} style={{ padding: '32px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <div>
            <h2 className="sw-page-title" style={{ fontSize: '1.35rem', marginBottom: '8px' }}>
              Manual Meter Reading
            </h2>
            <p className="sw-page-subtitle" style={{ margin: 0 }}>
              POST a single water usage reading for a household.
            </p>
          </div>

          <div>
            <label style={{ display: 'block', fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-secondary)', marginBottom: 6 }}>
              Select Flat / Household
            </label>
            <select
              className="sw-select"
              style={{ width: '100%' }}
              value={manualForm.householdId}
              onChange={(e) => setManualForm({ ...manualForm, householdId: e.target.value })}
              required
            >
              <option value="">Select Household</option>
              {households.map((hh) => (
                <option key={hh.id} value={hh.id}>
                  Flat {hh.flatNumber} ({hh.hasMeter ? 'Smart Meter' : 'No Meter'})
                </option>
              ))}
            </select>
          </div>

          <div>
            <label style={{ display: 'block', fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-secondary)', marginBottom: 6 }}>
              Reading Date
            </label>
            <input
              className="sw-input"
              type="date"
              style={{ width: '100%' }}
              value={manualForm.readingDate}
              onChange={(e) => setManualForm({ ...manualForm, readingDate: e.target.value })}
              required
            />
          </div>

          <div>
            <label style={{ display: 'block', fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-secondary)', marginBottom: 6 }}>
              Cumulative Meter Value (Optional)
            </label>
            <input
              className="sw-input"
              type="number"
              step="0.001"
              style={{ width: '100%' }}
              placeholder="e.g. 1450.5"
              value={manualForm.meterReadingValue}
              onChange={(e) => setManualForm({ ...manualForm, meterReadingValue: e.target.value })}
            />
          </div>

          <div>
            <label style={{ display: 'block', fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-secondary)', marginBottom: 6 }}>
              Volume Used (Liters) *
            </label>
            <input
              className="sw-input"
              type="number"
              step="0.01"
              style={{ width: '100%' }}
              placeholder="e.g. 450"
              value={manualForm.volumeUsedLiters}
              onChange={(e) => setManualForm({ ...manualForm, volumeUsedLiters: e.target.value })}
              required
            />
          </div>

          <button
            className="sw-btn sw-btn--primary"
            style={{ marginTop: 8 }}
            disabled={savingManual || !manualForm.householdId}
          >
            {savingManual ? 'Submitting...' : 'Submit Reading'}
          </button>
        </form>

        {/* CSV Bulk Meter Upload Component */}
        <form
          className="sw-panel"
          onSubmit={handleUpload}
          style={{
            padding: '32px',
            display: 'flex',
            flexDirection: 'column',
            gap: '24px',
          }}
        >
          <div>
            <h2 className="sw-page-title" style={{ fontSize: '1.35rem', marginBottom: '8px' }}>
              Upload CSV File
            </h2>
            <p className="sw-page-subtitle" style={{ margin: 0 }}>
              Select a CSV file containing household water usage readings.
            </p>
          </div>

          <label
            htmlFor="csv-upload"
            style={{
              border: '2px dashed var(--sw-border-strong)',
              borderRadius: '16px',
              padding: '28px 24px',
              textAlign: 'center',
              cursor: 'pointer',
              background: 'var(--sw-surface-raised)',
              transition: 'all .2s',
            }}
          >
            <div style={{ fontSize: '42px', marginBottom: '12px' }}>📄</div>
            <div style={{ fontWeight: 600, marginBottom: '8px' }}>
              {file ? file.name : 'Click to choose a CSV file'}
            </div>
            <div style={{ color: 'var(--sw-text-secondary)', fontSize: '.95rem' }}>
              CSV files only • Max 5 MB
            </div>
            <input
              id="csv-upload"
              type="file"
              accept=".csv,text/csv"
              style={{ display: 'none' }}
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
          </label>

          {file && (
            <div className="sw-panel" style={{ padding: '16px', background: 'var(--sw-surface-raised)' }}>
              <strong>Selected File</strong>
              <div style={{ marginTop: '8px' }}>{file.name}</div>
              <div style={{ color: 'var(--sw-text-secondary)', fontSize: '0.9rem' }}>
                {(file.size / 1024).toFixed(1)} KB
              </div>
            </div>
          )}

          <button className="sw-btn sw-btn--primary" style={{ width: '220px', alignSelf: 'center' }} disabled={!file || loading}>
            {loading ? 'Uploading...' : 'Upload CSV'}
          </button>
        </form>
      </div>

      {summary && (
        <section className="sw-panel" style={{ padding: '32px' }}>
          <h2 className="sw-page-title" style={{ fontSize: '1.35rem', marginBottom: '16px' }}>
            Upload Summary
          </h2>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: '16px' }}>
            <div style={{ padding: 12, background: 'var(--sw-surface-raised)', borderRadius: 8 }}>
              <div style={{ fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-secondary)' }}>Processed</div>
              <div style={{ fontSize: '1.4rem', fontWeight: 600 }}>{summary.totalProcessed ?? summary.length ?? 0}</div>
            </div>
            <div style={{ padding: 12, background: 'var(--sw-surface-raised)', borderRadius: 8 }}>
              <div style={{ fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-secondary)' }}>Success</div>
              <div style={{ fontSize: '1.4rem', fontWeight: 600, color: 'var(--sw-green)' }}>{summary.successCount ?? summary.length ?? 0}</div>
            </div>
          </div>
        </section>
      )}

      <section className="sw-panel" style={{ padding: '32px' }}>
        <h2 className="sw-page-title" style={{ fontSize: '1.35rem', marginBottom: '20px' }}>
          CSV Format Requirements
        </h2>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '24px' }}>
          <div>
            <strong>Headers required:</strong>
            <p style={{ margin: '8px 0 0', fontFamily: 'monospace', fontSize: '0.9rem', color: 'var(--sw-text-secondary)' }}>
              household_id,reading_date,meter_reading_value,volume_used_liters
            </p>
          </div>
          <div>
            <strong>Example Row:</strong>
            <p style={{ margin: '8px 0 0', fontFamily: 'monospace', fontSize: '0.9rem', color: 'var(--sw-text-secondary)' }}>
              1,2026-07-15,1450.500,450.00
            </p>
          </div>
        </div>
      </section>
    </div>
  )
}
