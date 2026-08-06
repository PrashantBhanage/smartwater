import { useEffect, useState } from 'react'
import { useAuth } from '../context/AuthContext'
import { billingApi, finalizeBillingCycle, getInvoices } from '../api'

function formatMoney(value) {
  return Number(value ?? 0).toLocaleString(undefined, {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 2,
  })
}

export default function AdminCycleInvoicesPage() {
  const { user } = useAuth()
  const [cycles, setCycles] = useState([])
  const [selectedCycleId, setSelectedCycleId] = useState('')
  const [invoices, setInvoices] = useState([])
  const [loading, setLoading] = useState(true)
  const [finalizing, setFinalizing] = useState(false)
  const [error, setError] = useState('')
  const [confirmCycle, setConfirmCycle] = useState(null)
  const [summary, setSummary] = useState(null)

  useEffect(() => {
    let active = true

    async function loadCycles() {
      if (!user?.apartmentId) {
        setError('You are not associated with an apartment complex.')
        setLoading(false)
        return
      }
      try {
        const data = await billingApi.listByApartment(user.apartmentId)
        if (active) {
          setCycles(data)
          setSelectedCycleId((prev) => prev || String(data[0]?.id ?? ''))
        }
      } catch (err) {
        if (active) setError(err.message || 'Failed to load billing cycles.')
      } finally {
        if (active) setLoading(false)
      }
    }

    loadCycles()
    return () => {
      active = false
    }
  }, [user?.apartmentId])

  useEffect(() => {
    let active = true

    async function loadInvoices() {
      if (!selectedCycleId) {
        setInvoices([])
        setLoading(false)
        return
      }
      setLoading(true)
      setError('')
      try {
        const data = await getInvoices(Number(selectedCycleId))
        if (active) setInvoices(data)
      } catch (err) {
        if (active) setError(err.message || 'Failed to load cycle invoices.')
      } finally {
        if (active) setLoading(false)
      }
    }

    loadInvoices()
    return () => {
      active = false
    }
  }, [selectedCycleId])

  const selectedCycle = cycles.find((c) => String(c.id) === selectedCycleId)

  async function handleFinalize(e) {
    e.preventDefault()
    if (!confirmCycle) return
    setError('')
    setFinalizing(true)
    try {
      const result = await finalizeBillingCycle(confirmCycle.id)
      setSummary(result)
      setConfirmCycle(null)
      setSelectedCycleId('')
      const refreshed = await billingApi.listByApartment(user.apartmentId)
      setCycles(refreshed)
      setSelectedCycleId((prev) => prev || String(refreshed[0]?.id ?? ''))
    } catch (err) {
      setError(err.message || 'Failed to finalize cycle.')
      setConfirmCycle(null)
    } finally {
      setFinalizing(false)
    }
  }

  return (
    <div className="sw-fade-in" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sw-space-6)' }}>
      <header>
        <h1 className="sw-page-title">Cycle Invoices</h1>
        <p className="sw-page-subtitle">
          Finalize a billing cycle to generate household invoices and review the published invoice ledger.
        </p>
      </header>

      {error ? <div className="sw-banner sw-banner--error" role="alert">{error}</div> : null}

      <section className="sw-panel" style={{ padding: 'var(--sw-space-5)' }}>
        <h2 className="sw-page-title" style={{ fontSize: 'var(--sw-fs-lg)', marginBottom: 16 }}>Billing Cycle</h2>
        {loading && cycles.length === 0 ? (
          <div style={{ display: 'grid', placeItems: 'center', minHeight: 120 }}>
            <div className="sw-spinner" style={{ width: 32, height: 32 }} />
          </div>
        ) : cycles.length === 0 ? (
          <div className="sw-empty">No billing cycles found. Open a billing cycle first.</div>
        ) : (
          <>
            <label className="sw-field">
              <span className="sw-field__label">Select Cycle</span>
              <select
                className="sw-select"
                value={selectedCycleId}
                onChange={(e) => setSelectedCycleId(e.target.value)}
              >
                {cycles.map((cycle) => (
                  <option key={cycle.id} value={cycle.id}>
                    {cycle.cycleStartDate} to {cycle.cycleEndDate} — {cycle.status}
                  </option>
                ))}
              </select>
            </label>

            {selectedCycle && (
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 16, marginTop: 20 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <span className={`sw-status ${selectedCycle.status === 'OPEN' ? 'sw-status--yellow' : selectedCycle.status === 'FINALIZED' ? 'sw-status--green' : 'sw-status--neutral'}`}>
                    {selectedCycle.status}
                  </span>
                  <span style={{ color: 'var(--sw-text-secondary)', fontSize: 'var(--sw-fs-sm)' }}>
                    {selectedCycle.cycleStartDate} to {selectedCycle.cycleEndDate}
                    {selectedCycle.invoicesGenerated != null && selectedCycle.status !== 'OPEN'
                      ? ` · ${selectedCycle.invoicesGenerated} invoices`
                      : ''}
                  </span>
                </div>
                {selectedCycle.status === 'OPEN' && (
                  <button className="sw-btn sw-btn--primary" onClick={() => setConfirmCycle(selectedCycle)}>
                    Finalize Cycle
                  </button>
                )}
              </div>
            )}
          </>
        )}
      </section>

      <section className="sw-panel" style={{ padding: 'var(--sw-space-5)' }}>
        <h2 className="sw-page-title" style={{ fontSize: 'var(--sw-fs-lg)', marginBottom: 16 }}>Household Invoices</h2>
        {loading ? (
          <div style={{ display: 'grid', placeItems: 'center', minHeight: 160 }}>
            <div className="sw-spinner" style={{ width: 32, height: 32 }} />
          </div>
        ) : invoices.length === 0 ? (
          <div className="sw-empty">No invoices generated for this cycle yet.</div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="sw-table">
              <thead>
                <tr>
                  <th>Household</th>
                  <th>Base Charge</th>
                  <th>Shared Allocation</th>
                  <th>Total Charge</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {invoices.map((invoice) => (
                  <tr key={invoice.id}>
                    <td>{invoice.flatNumber}</td>
                    <td>{formatMoney(invoice.baseCharge)}</td>
                    <td>{formatMoney(invoice.sharedCostAllocation)}</td>
                    <td><strong>{formatMoney(invoice.totalCharge)}</strong></td>
                    <td>
                      <span className={`sw-status ${invoice.paidStatus === 'PAID' ? 'sw-status--green' : 'sw-status--red'}`}>
                        {invoice.paidStatus}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {confirmCycle && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          background: 'rgba(44, 41, 38, 0.4)',
          backdropFilter: 'blur(4px)',
          display: 'grid',
          placeItems: 'center',
          zIndex: 1000,
          animation: 'sw-fade 200ms var(--sw-ease)',
        }}>
          <div className="sw-panel" style={{
            background: 'var(--sw-surface)',
            padding: 'var(--sw-space-6)',
            width: '100%',
            maxWidth: '500px',
            position: 'relative',
          }}>
            <h2 className="sw-page-title" style={{ fontSize: 'var(--sw-fs-lg)', marginBottom: 'var(--sw-space-4)' }}>
              Finalize Billing Cycle?
            </h2>
            <div className="sw-banner sw-banner--info" style={{ marginBottom: 'var(--sw-space-4)' }}>
              This will generate invoices for every household and is <strong>irreversible</strong>.
              Re-finalizing the same cycle is not allowed.
            </div>
            <p style={{ margin: '0 0 8px', color: 'var(--sw-text-secondary)', fontSize: 'var(--sw-fs-sm)' }}>
              Cycle period: <strong>{confirmCycle.cycleStartDate}</strong> to <strong>{confirmCycle.cycleEndDate}</strong>
            </p>
            <div style={{ display: 'flex', gap: 12, marginTop: 'var(--sw-space-5)', justifyContent: 'flex-end' }}>
              <button className="sw-btn sw-btn--secondary" onClick={() => setConfirmCycle(null)} disabled={finalizing}>
                Cancel
              </button>
              <button className="sw-btn sw-btn--primary" onClick={handleFinalize} disabled={finalizing}>
                {finalizing ? 'Finalizing…' : 'Confirm Finalize'}
              </button>
            </div>
          </div>
        </div>
      )}

      {summary && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          background: 'rgba(44, 41, 38, 0.4)',
          backdropFilter: 'blur(4px)',
          display: 'grid',
          placeItems: 'center',
          zIndex: 1000,
          animation: 'sw-fade 200ms var(--sw-ease)',
        }}>
          <div className="sw-panel" style={{
            background: 'var(--sw-surface)',
            padding: 'var(--sw-space-6)',
            width: '100%',
            maxWidth: '500px',
            position: 'relative',
          }}>
            <h2 className="sw-page-title" style={{ fontSize: 'var(--sw-fs-lg)', marginBottom: 'var(--sw-space-4)' }}>
              Billing Cycle Finalized
            </h2>
            <div className="sw-banner sw-banner--ok" style={{ marginBottom: 16 }}>
              Invoices have been generated and published.
            </div>
            <div style={{ display: 'grid', gap: 'var(--sw-space-3)', margin: 'var(--sw-space-4) 0' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--sw-border)', paddingBottom: '8px' }}>
                <span style={{ color: 'var(--sw-text-secondary)' }}>Invoices Generated</span>
                <strong>{summary.invoicesGenerated}</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--sw-border)', paddingBottom: '8px' }}>
                <span style={{ color: 'var(--sw-text-secondary)' }}>Total Base Charge</span>
                <span>{formatMoney(summary.totalBaseCharge)}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--sw-border)', paddingBottom: '8px' }}>
                <span style={{ color: 'var(--sw-text-secondary)' }}>Total Shared Allocation</span>
                <span>{formatMoney(summary.totalSharedAllocation)}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', paddingTop: '8px' }}>
                <span style={{ fontWeight: 'bold' }}>Total Invoice Amount</span>
                <strong style={{ fontSize: 'var(--sw-fs-md)' }}>{formatMoney(summary.totalAmount)}</strong>
              </div>
            </div>
            <div style={{ display: 'flex', gap: 12, marginTop: 'var(--sw-space-5)', justifyContent: 'flex-end' }}>
              <button className="sw-btn sw-btn--primary" onClick={() => setSummary(null)}>
                Done
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
