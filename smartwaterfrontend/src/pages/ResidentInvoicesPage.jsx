import { useEffect, useState } from 'react'
import { getMyInvoices } from '../api'

function formatMoney(value) {
  return Number(value ?? 0).toLocaleString(undefined, {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 2,
  })
}

export default function ResidentInvoicesPage() {
  const [invoices, setInvoices] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selectedInvoice, setSelectedInvoice] = useState(null)

  useEffect(() => {
    let active = true
    async function loadInvoices() {
      try {
        const data = await getMyInvoices()
        if (active) {
          // Sort by newest first
          const sorted = [...data].sort((a, b) => b.id - a.id)
          setInvoices(sorted)
        }
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
  }, [])

  return (
    <div className="sw-fade-in" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sw-space-6)' }}>
      <header>
        <h1 className="sw-page-title">Invoices</h1>
        <p className="sw-page-subtitle">Billing cycle history and detailed cost allocation for your household.</p>
      </header>

      {error && <div className="sw-banner sw-banner--error">{error}</div>}

      <section className="sw-panel" style={{ padding: 'var(--sw-space-5)' }}>
        {loading ? (
          <div style={{ display: 'grid', placeItems: 'center', minHeight: 160 }}>
            <div className="sw-spinner" style={{ width: 32, height: 32 }} />
          </div>
        ) : invoices.length === 0 ? (
          <div className="sw-empty">No invoices found for your household.</div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="sw-table">
              <thead>
                <tr>
                  <th>Invoice ID</th>
                  <th>Flat Number</th>
                  <th>Base Charge</th>
                  <th>Shared Allocation</th>
                  <th>Total Charge</th>
                  <th>Status</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {invoices.map((inv) => (
                  <tr key={inv.id}>
                    <td>#{inv.id}</td>
                    <td>{inv.flatNumber}</td>
                    <td>{formatMoney(inv.baseCharge)}</td>
                    <td>{formatMoney(inv.sharedCostAllocation)}</td>
                    <td><strong>{formatMoney(inv.totalCharge)}</strong></td>
                    <td>
                      <span className={`sw-status sw-status--${inv.paidStatus === 'PAID' ? 'green' : 'red'}`}>
                        {inv.paidStatus}
                      </span>
                    </td>
                    <td>
                      <button
                        className="sw-btn sw-btn--secondary"
                        style={{ minHeight: '36px', padding: '0 12px' }}
                        onClick={() => setSelectedInvoice(inv)}
                      >
                        View Details
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Invoice Details Modal */}
      {selectedInvoice && (
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
          animation: 'sw-fade 200ms var(--sw-ease)'
        }}>
          <div className="sw-panel" style={{
            background: 'var(--sw-surface)',
            padding: 'var(--sw-space-6)',
            width: '100%',
            maxWidth: '500px',
            position: 'relative'
          }}>
            <h2 className="sw-page-title" style={{ fontSize: 'var(--sw-fs-lg)', marginBottom: 'var(--sw-space-4)' }}>
              Invoice Details #{selectedInvoice.id}
            </h2>
            <div style={{ display: 'grid', gap: 'var(--sw-space-3)', margin: 'var(--sw-space-4) 0' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--sw-border)', paddingBottom: '8px' }}>
                <span style={{ color: 'var(--sw-text-secondary)' }}>Flat Number</span>
                <strong>{selectedInvoice.flatNumber}</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--sw-border)', paddingBottom: '8px' }}>
                <span style={{ color: 'var(--sw-text-secondary)' }}>Status</span>
                <span className={`sw-status sw-status--${selectedInvoice.paidStatus === 'PAID' ? 'green' : 'red'}`}>
                  {selectedInvoice.paidStatus}
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--sw-border)', paddingBottom: '8px' }}>
                <span style={{ color: 'var(--sw-text-secondary)' }}>Base Charge</span>
                <span>{formatMoney(selectedInvoice.baseCharge)}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--sw-border)', paddingBottom: '8px' }}>
                <span style={{ color: 'var(--sw-text-secondary)' }}>Shared Allocation</span>
                <span>{formatMoney(selectedInvoice.sharedCostAllocation)}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', paddingTop: '8px' }}>
                <span style={{ fontWeight: 'bold' }}>Total Amount</span>
                <strong style={{ fontSize: 'var(--sw-fs-md)' }}>{formatMoney(selectedInvoice.totalCharge)}</strong>
              </div>
            </div>
            <div style={{ display: 'flex', gap: '12px', marginTop: 'var(--sw-space-5)', justifyContent: 'flex-end' }}>
              <button
                className="sw-btn sw-btn--ghost"
                onClick={() => setSelectedInvoice(null)}
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
