import { useEffect, useMemo, useState } from 'react'
import {
  alertsApi,
  billingApi,
  householdsApi,
  invoicesApi,
  purchasesApi,
  tariffApi,
  usageLogsApi,
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
  const [households, setHouseholds] = useState([])
  const [cycles, setCycles] = useState([])
  const [tariffs, setTariffs] = useState([])
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
      } catch (err) {
        if (active) setError(err.message || 'Failed to load admin overview.')
      } finally {
        if (active) setLoading(false)
      }
    }

    loadOverview()
    return () => {
      active = false
    }
  }, [user?.apartmentId])

  const openCycle = cycles.find((cycle) => cycle.status === 'OPEN')

  return (
    <div className="sw-fade-in" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sw-space-6)' }}>
      <PageHeader
        title="Overview"
        subtitle="Apartment operations, billing readiness, and recent setup status."
      />
      <DataState loading={loading} error={error}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 'var(--sw-space-4)' }}>
          <div className="sw-panel" style={{ padding: 'var(--sw-space-4)' }}>
            <div className="sw-field__label">Households</div>
            <div className="sw-page-title" style={{ fontSize: 'var(--sw-fs-xl)' }}>{households.length}</div>
          </div>
          <div className="sw-panel" style={{ padding: 'var(--sw-space-4)' }}>
            <div className="sw-field__label">Billing Cycles</div>
            <div className="sw-page-title" style={{ fontSize: 'var(--sw-fs-xl)' }}>{cycles.length}</div>
          </div>
          <div className="sw-panel" style={{ padding: 'var(--sw-space-4)' }}>
            <div className="sw-field__label">Tariff Plans</div>
            <div className="sw-page-title" style={{ fontSize: 'var(--sw-fs-xl)' }}>{tariffs.length}</div>
          </div>
          <div className="sw-panel" style={{ padding: 'var(--sw-space-4)' }}>
            <div className="sw-field__label">Open Cycle</div>
            <div className="sw-page-title" style={{ fontSize: 'var(--sw-fs-lg)' }}>
              {openCycle ? `${openCycle.cycleStartDate} to ${openCycle.cycleEndDate}` : 'None'}
            </div>
          </div>
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
        cycleData.map(async (cycle) => [cycle.id, await purchasesApi.listByCycle(cycle.id)]),
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
    async function load() {
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
          cycleData.map(async (cycle) => [cycle.id, await purchasesApi.listByCycle(cycle.id)]),
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
    load()
  }, [user?.apartmentId])

  async function handleCreateTariff(e) {
    e.preventDefault()
    setError('')
    setSuccess('')
    setSaving(true)
    try {
      await tariffApi.create({ apartmentId: user.apartmentId, ...tariffForm })
      setSuccess('Tariff plan created.')
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
      setSuccess('Billing cycle opened.')
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
      await purchasesApi.create({
        apartmentId: user.apartmentId,
        cycleId: Number(purchaseForm.cycleId),
        volumePurchasedKl: purchaseForm.volumePurchasedKl,
        unitCost: purchaseForm.unitCost,
        purchaseDate: purchaseForm.purchaseDate,
        source: 'TANKER',
      })
      setSuccess('Water purchase recorded.')
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
            <input className="sw-input" type="number" step="0.001" value={tariffForm.tier1LimitKl} onChange={(e) => setTariffForm({ ...tariffForm, tier1LimitKl: e.target.value })} aria-label="Tier 1 limit KL" required />
            <input className="sw-input" type="number" step="0.01" value={tariffForm.tier1Rate} onChange={(e) => setTariffForm({ ...tariffForm, tier1Rate: e.target.value })} aria-label="Tier 1 rate" required />
            <input className="sw-input" type="number" step="0.01" value={tariffForm.tier2Rate} onChange={(e) => setTariffForm({ ...tariffForm, tier2Rate: e.target.value })} aria-label="Tier 2 rate" required />
            <input className="sw-input" type="date" value={tariffForm.effectiveFromDate} onChange={(e) => setTariffForm({ ...tariffForm, effectiveFromDate: e.target.value })} aria-label="Effective from" required />
            <button className="sw-btn sw-btn--primary" disabled={saving}>Save Tariff</button>
          </form>

          <form className="sw-panel" onSubmit={handleOpenCycle} style={{ padding: 'var(--sw-space-4)', display: 'grid', gap: 12 }}>
            <h2 className="sw-page-title" style={{ fontSize: 'var(--sw-fs-lg)' }}>Billing Cycles</h2>
            <input className="sw-input" type="date" value={cycleForm.cycleStartDate} onChange={(e) => setCycleForm({ ...cycleForm, cycleStartDate: e.target.value })} aria-label="Cycle start" required />
            <input className="sw-input" type="date" value={cycleForm.cycleEndDate} onChange={(e) => setCycleForm({ ...cycleForm, cycleEndDate: e.target.value })} aria-label="Cycle end" required />
            <button className="sw-btn sw-btn--primary" disabled={saving}>Open Cycle</button>
          </form>

          <form className="sw-panel" onSubmit={handleCreatePurchase} style={{ padding: 'var(--sw-space-4)', display: 'grid', gap: 12 }}>
            <h2 className="sw-page-title" style={{ fontSize: 'var(--sw-fs-lg)' }}>Purchases</h2>
            <select className="sw-select" value={purchaseForm.cycleId} onChange={(e) => setPurchaseForm({ ...purchaseForm, cycleId: e.target.value })} required>
              <option value="">Select cycle</option>
              {cycles.map((cycle) => <option key={cycle.id} value={cycle.id}>{cycle.cycleStartDate} to {cycle.cycleEndDate}</option>)}
            </select>
            <input className="sw-input" type="number" step="0.001" value={purchaseForm.volumePurchasedKl} onChange={(e) => setPurchaseForm({ ...purchaseForm, volumePurchasedKl: e.target.value })} aria-label="Volume purchased KL" required />
            <input className="sw-input" type="number" step="0.01" value={purchaseForm.unitCost} onChange={(e) => setPurchaseForm({ ...purchaseForm, unitCost: e.target.value })} aria-label="Unit cost" required />
            <input className="sw-input" type="date" value={purchaseForm.purchaseDate} onChange={(e) => setPurchaseForm({ ...purchaseForm, purchaseDate: e.target.value })} aria-label="Purchase date" required />
            <button className="sw-btn sw-btn--primary" disabled={saving || !purchaseForm.cycleId}>Record Purchase</button>
          </form>
        </div>

        <section className="sw-panel" style={{ padding: 'var(--sw-space-5)' }}>
          <h2 className="sw-page-title" style={{ fontSize: 'var(--sw-fs-lg)', marginBottom: 16 }}>Current Tariffs</h2>
          <DataState loading={false} empty={tariffs.length === 0}>
            <table className="sw-table">
              <thead><tr><th>Effective</th><th>Tier 1 Limit</th><th>Tier 1 Rate</th><th>Tier 2 Rate</th></tr></thead>
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
                      <td>{cycle.status === 'OPEN' ? <button className="sw-btn sw-btn--secondary" disabled={saving} onClick={() => finalizeCycle(cycle.id)}>Finalize</button> : 'Done'}</td>
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
              <tbody>{allPurchases.map((purchase) => <tr key={purchase.id}><td>{purchase.purchaseDate}</td><td>{purchase.cycleId}</td><td>{formatNumber(purchase.volumePurchasedKl, ' KL')}</td><td>{formatMoney(purchase.unitCost)}</td><td>{formatMoney(purchase.totalCost)}</td><td>{purchase.source}</td></tr>)}</tbody>
            </table>
          </DataState>
        </section>
      </DataState>
    </div>
  )
}

export function AdminUploadsPage() {
  const [file, setFile] = useState(null)
  const [summary, setSummary] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  async function handleUpload(e) {
    e.preventDefault()
    if (!file) return
    setLoading(true)
    setError('')
    setSummary(null)
    try {
      setSummary(await usageLogsApi.bulkUpload(file))
    } catch (err) {
      setError(err.message || 'CSV upload failed.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="sw-fade-in" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sw-space-6)' }}>
      <PageHeader title="CSV Upload" subtitle="Bulk import household usage readings and review row outcomes." />
      {error ? <div className="sw-banner sw-banner--error" role="alert">{error}</div> : null}
      <form className="sw-panel" onSubmit={handleUpload} style={{ padding: 'var(--sw-space-5)', display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
        <input className="sw-input" type="file" accept=".csv,text/csv" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
        <button className="sw-btn sw-btn--primary" disabled={!file || loading}>{loading ? 'Uploading...' : 'Upload CSV'}</button>
      </form>
      {summary ? (
        <section className="sw-panel" style={{ padding: 'var(--sw-space-5)' }}>
          <h2 className="sw-page-title" style={{ fontSize: 'var(--sw-fs-lg)', marginBottom: 16 }}>Upload Summary</h2>
          <table className="sw-table">
            <tbody>
              <tr><td>Rows Processed</td><td>{summary.rowsProcessed}</td></tr>
              <tr><td>Inserted</td><td>{summary.rowsInserted}</td></tr>
              <tr><td>Skipped</td><td>{summary.rowsSkipped}</td></tr>
              <tr><td>Failed</td><td>{summary.rowsFailed}</td></tr>
              <tr><td>GREEN / YELLOW / RED</td><td>{summary.greenCount} / {summary.yellowCount} / {summary.redCount}</td></tr>
            </tbody>
          </table>
        </section>
      ) : null}
    </div>
  )
}

export function ResidentInvoicesPage() {
  const { user } = useAuth()
  const [invoices, setInvoices] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    async function loadInvoices() {
      if (!user?.householdId) {
        setError('No household assigned to your user account.')
        setLoading(false)
        return
      }
      try {
        const data = await invoicesApi.listByHousehold(user.householdId)
        if (active) setInvoices(data)
      } catch (err) {
        if (active) setError(err.message || 'Failed to load invoices.')
      } finally {
        if (active) setLoading(false)
      }
    }
    loadInvoices()
    return () => {
      active = false
    }
  }, [user?.householdId])

  return (
    <div className="sw-fade-in" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sw-space-6)' }}>
      <PageHeader title="Invoices" subtitle="Generated billing history for your household." />
      <section className="sw-panel" style={{ padding: 'var(--sw-space-5)' }}>
        <DataState loading={loading} error={error} empty={invoices.length === 0}>
          <table className="sw-table">
            <thead><tr><th>Invoice</th><th>Flat</th><th>Base</th><th>Shared</th><th>Total</th><th>Status</th></tr></thead>
            <tbody>{invoices.map((invoice) => <tr key={invoice.id}><td>#{invoice.id}</td><td>{invoice.flatNumber}</td><td>{formatMoney(invoice.baseCharge)}</td><td>{formatMoney(invoice.sharedAllocation)}</td><td>{formatMoney(invoice.totalAmount)}</td><td><span className="sw-status sw-status--green">{invoice.status}</span></td></tr>)}</tbody>
          </table>
        </DataState>
      </section>
    </div>
  )
}

export function ResidentAlertsPage() {
  const { user } = useAuth()
  const [alerts, setAlerts] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    async function loadAlerts() {
      if (!user?.householdId) {
        setError('No household assigned to your user account.')
        setLoading(false)
        return
      }
      try {
        const data = await alertsApi.list(user.householdId)
        if (active) setAlerts(data)
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
  }, [user?.householdId])

  return (
    <div className="sw-fade-in" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sw-space-6)' }}>
      <PageHeader title="Alerts" subtitle="Threshold alerts generated from your household usage readings." />
      <section className="sw-panel" style={{ padding: 'var(--sw-space-5)' }}>
        <DataState loading={loading} error={error} empty={alerts.length === 0}>
          <table className="sw-table">
            <thead><tr><th>Date</th><th>Flat</th><th>Type</th><th>Usage</th><th>Message</th><th>Status</th></tr></thead>
            <tbody>{alerts.map((alert) => <tr key={alert.id}><td>{alert.readingDate}</td><td>{alert.flatNumber}</td><td>{alert.alertType}</td><td>{formatNumber(alert.usageLiters, ' L')}</td><td>{alert.message}</td><td>{alert.acknowledged ? 'Acknowledged' : 'Open'}</td></tr>)}</tbody>
          </table>
        </DataState>
      </section>
    </div>
  )
}
