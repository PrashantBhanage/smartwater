import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../context/AuthContext'
import { billingApi, createBulkPurchase, getBulkPurchases } from '../api'

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

export default function AdminBulkPurchasesPage() {
  const { user } = useAuth()
  const [purchases, setPurchases] = useState([])
  const [summary, setSummary] = useState(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  const [form, setForm] = useState(() => ({
    purchaseDate: new Date().toISOString().slice(0, 10),
    volumeLiters: '',
    unitCost: '',
  }))

  const [filter, setFilter] = useState({ cycleStart: '', cycleEnd: '' })

  const loadPurchases = useCallback(async (start, end) => {
    if (!user?.apartmentId) return
    setLoading(true)
    setError('')
    try {
      const data = await getBulkPurchases(user.apartmentId, start || undefined, end || undefined)
      setPurchases(data.purchases || [])
      setSummary(data)
    } catch (err) {
      setError(err.message || 'Failed to load bulk purchases.')
    } finally {
      setLoading(false)
    }
  }, [user])

  useEffect(() => {
    let active = true

    async function init() {
      if (!user?.apartmentId) {
        setError('You are not associated with an apartment complex.')
        setLoading(false)
        return
      }

      try {
        const cycles = await billingApi.listByApartment(user.apartmentId)
        const openCycle = cycles.find((c) => c.status === 'OPEN')
        if (active && openCycle) {
          setFilter({ cycleStart: openCycle.cycleStartDate, cycleEnd: openCycle.cycleEndDate })
          await loadPurchases(openCycle.cycleStartDate, openCycle.cycleEndDate)
        } else if (active) {
          await loadPurchases()
        }
      } catch (err) {
        if (active) setError(err.message || 'Failed to load billing cycles.')
      } finally {
        if (active) setLoading(false)
      }
    }

    init()
    return () => {
      active = false
    }
  }, [user?.apartmentId, loadPurchases])

  async function handleSubmit(e) {
    e.preventDefault()
    if (!user?.apartmentId) return
    setError('')
    setSuccess('')
    setSaving(true)
    try {
      await createBulkPurchase(user.apartmentId, {
        purchaseDate: form.purchaseDate,
        volumeLiters: Number(form.volumeLiters),
        unitCost: Number(form.unitCost),
      })
      setSuccess('Bulk water purchase recorded.')
      setForm((prev) => ({ ...prev, volumeLiters: '', unitCost: '' }))
      await loadPurchases(filter.cycleStart, filter.cycleEnd)
    } catch (err) {
      setError(err.message || 'Failed to record bulk purchase.')
    } finally {
      setSaving(false)
    }
  }

  async function handleApplyFilter(e) {
    e.preventDefault()
    await loadPurchases(filter.cycleStart, filter.cycleEnd)
  }

  return (
    <div className="sw-fade-in" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sw-space-6)' }}>
      <header>
        <h1 className="sw-page-title">Bulk Purchases</h1>
        <p className="sw-page-subtitle">
          Record bulk water purchases for the complex and review purchase history within a billing cycle window.
        </p>
      </header>

      {error ? <div className="sw-banner sw-banner--error" role="alert">{error}</div> : null}
      {success ? <div className="sw-banner sw-banner--ok" role="alert">{success}</div> : null}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 'var(--sw-space-5)', alignItems: 'start' }}>
        <form className="sw-panel" onSubmit={handleSubmit} style={{ padding: 'var(--sw-space-5)', display: 'flex', flexDirection: 'column', gap: 14 }}>
          <h2 className="sw-page-title" style={{ fontSize: 'var(--sw-fs-lg)' }}>Record Purchase</h2>
          <label className="sw-field">
            <span className="sw-field__label">Purchase Date</span>
            <input
              className="sw-input"
              type="date"
              value={form.purchaseDate}
              onChange={(e) => setForm({ ...form, purchaseDate: e.target.value })}
              required
            />
          </label>
          <label className="sw-field">
            <span className="sw-field__label">Volume (Liters)</span>
            <input
              className="sw-input"
              type="number"
              min="0.01"
              step="0.001"
              placeholder="e.g. 40000"
              value={form.volumeLiters}
              onChange={(e) => setForm({ ...form, volumeLiters: e.target.value })}
              required
            />
            <span className="sw-field__hint">Total water received, in liters.</span>
          </label>
          <label className="sw-field">
            <span className="sw-field__label">Unit Cost</span>
            <input
              className="sw-input"
              type="number"
              min="0"
              step="0.01"
              placeholder="e.g. 30"
              value={form.unitCost}
              onChange={(e) => setForm({ ...form, unitCost: e.target.value })}
              required
            />
            <span className="sw-field__hint">Cost per liter (INR).</span>
          </label>
          <button type="submit" className="sw-btn sw-btn--primary" disabled={saving}>
            {saving ? 'Recording…' : 'Record Purchase'}
          </button>
        </form>

        <form className="sw-panel" onSubmit={handleApplyFilter} style={{ padding: 'var(--sw-space-5)', display: 'flex', flexDirection: 'column', gap: 14 }}>
          <h2 className="sw-page-title" style={{ fontSize: 'var(--sw-fs-lg)' }}>Cycle Window</h2>
          <p style={{ margin: 0, fontSize: 'var(--sw-fs-sm)', color: 'var(--sw-text-secondary)' }}>
            Filter history to a billing cycle window. Defaults to the currently open cycle.
          </p>
          <label className="sw-field">
            <span className="sw-field__label">Cycle Start</span>
            <input
              className="sw-input"
              type="date"
              value={filter.cycleStart}
              onChange={(e) => setFilter({ ...filter, cycleStart: e.target.value })}
            />
          </label>
          <label className="sw-field">
            <span className="sw-field__label">Cycle End</span>
            <input
              className="sw-input"
              type="date"
              value={filter.cycleEnd}
              onChange={(e) => setFilter({ ...filter, cycleEnd: e.target.value })}
            />
          </label>
          <button type="submit" className="sw-btn sw-btn--secondary" disabled={loading}>
            Apply Window
          </button>
        </form>
      </div>

      <section className="sw-panel" style={{ padding: 'var(--sw-space-5)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 16, marginBottom: 16 }}>
          <h2 className="sw-page-title" style={{ fontSize: 'var(--sw-fs-lg)', marginBottom: 0 }}>Purchase History</h2>
          {summary && (
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              <span className="sw-status sw-status--neutral">Total Volume: {formatNumber(summary.totalVolumeLiters, ' L')}</span>
              <span className="sw-status sw-status--neutral">Total Cost: {formatMoney(summary.totalCost)}</span>
            </div>
          )}
        </div>

        {loading ? (
          <div style={{ display: 'grid', placeItems: 'center', minHeight: 160 }}>
            <div className="sw-spinner" style={{ width: 32, height: 32 }} />
          </div>
        ) : purchases.length === 0 ? (
          <div className="sw-empty">No bulk purchases found for the selected window.</div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="sw-table">
              <thead>
                <tr>
                  <th>Purchase Date</th>
                  <th>Volume (Liters)</th>
                  <th>Unit Cost</th>
                  <th>Total Cost</th>
                </tr>
              </thead>
              <tbody>
                {purchases.map((purchase) => (
                  <tr key={purchase.id}>
                    <td>{purchase.purchaseDate}</td>
                    <td>{formatNumber(purchase.volumeLiters, ' L')}</td>
                    <td>{formatMoney(purchase.unitCost)}</td>
                    <td><strong>{formatMoney(purchase.totalCost)}</strong></td>
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
