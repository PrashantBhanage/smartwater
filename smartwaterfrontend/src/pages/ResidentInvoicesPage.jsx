import { useEffect, useState } from 'react'
import { useAuth } from '../context/AuthContext'
import { getMyInvoices, invoicesApi } from '../api'

function formatMoney(value) {
  return Number(value ?? 0).toLocaleString(undefined, {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 2,
  })
}

export default function ResidentInvoicesPage() {
  const { user } = useAuth()
  const [invoices, setInvoices] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selectedInvoice, setSelectedInvoice] = useState(null)
  const [downloadingId, setDownloadingId] = useState(null)

  useEffect(() => {
    let active = true
    async function loadInvoices() {
      try {
        const data = await getMyInvoices(user?.householdId)
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
  }, [user?.householdId])

  const handleDownloadPdf = async (invoiceId) => {
    if (!invoiceId) return
    setDownloadingId(invoiceId)
    try {
      await invoicesApi.downloadPdf(invoiceId)
    } catch (err) {
      alert('Failed to download invoice PDF: ' + (err.message || 'Server error'))
    } finally {
      setDownloadingId(null)
    }
  }

  return (
    <div className="sw-fade-in" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sw-space-6)' }}>
      <header>
        <h1 className="sw-page-title">Invoices</h1>
        <p className="sw-page-subtitle">Billing cycle history and detailed cost allocation for your household.</p>
      </header>

      {error && <div className="sw-banner sw-banner--error">{error}</div>}

      {!loading && invoices.length > 0 && (() => {
        const latest = invoices[0]
        const totalAmt = latest.totalAmount ?? latest.totalCharge
        const sharedAlloc = latest.sharedAllocation ?? latest.sharedCostAllocation
        const statusVal = latest.status ?? latest.paidStatus ?? 'ISSUED'

        return (
          <section className="sw-panel" style={{
            padding: 'var(--sw-space-5)',
            background: 'var(--sw-surface)',
            border: '1px solid var(--sw-accent)',
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 16 }}>
              <div>
                <div style={{ fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  Current Cycle Balance
                </div>
                <div style={{ fontFamily: 'var(--sw-font-display)', fontSize: 'var(--sw-fs-display)', marginTop: 8, fontWeight: 500 }}>
                  {formatMoney(totalAmt)}
                </div>
                <p style={{ fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-tertiary)', margin: '8px 0 0' }}>
                  Invoice #{latest.id} for Flat {latest.flatNumber}
                </p>
              </div>
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
                <span className="sw-status sw-status--neutral">Base: {formatMoney(latest.baseCharge)}</span>
                <span className="sw-status sw-status--neutral">Shared: {formatMoney(sharedAlloc)}</span>
                <span className={`sw-status ${statusVal === 'PAID' ? 'sw-status--green' : 'sw-status--red'}`}>
                  {statusVal}
                </span>
                <button
                  className="sw-btn sw-btn--primary"
                  disabled={downloadingId === latest.id}
                  onClick={() => handleDownloadPdf(latest.id)}
                  style={{ minHeight: '36px', padding: '0 12px' }}
                >
                  {downloadingId === latest.id ? 'Downloading...' : '📄 Download PDF'}
                </button>
              </div>
            </div>
          </section>
        )
      })()}

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
                {invoices.map((inv) => {
                  const totalAmt = inv.totalAmount ?? inv.totalCharge
                  const sharedAlloc = inv.sharedAllocation ?? inv.sharedCostAllocation
                  const statusVal = inv.status ?? inv.paidStatus ?? 'ISSUED'

                  return (
                    <tr key={inv.id}>
                      <td>#{inv.id}</td>
                      <td>{inv.flatNumber}</td>
                      <td>{formatMoney(inv.baseCharge)}</td>
                      <td>{formatMoney(sharedAlloc)}</td>
                      <td><strong>{formatMoney(totalAmt)}</strong></td>
                      <td>
                        <span className={`sw-status sw-status--${statusVal === 'PAID' ? 'green' : 'red'}`}>
                          {statusVal}
                        </span>
                      </td>
                      <td>
                        <div style={{ display: 'flex', gap: 6 }}>
                          <button
                            className="sw-btn sw-btn--secondary"
                            style={{ minHeight: '36px', padding: '0 12px' }}
                            onClick={() => setSelectedInvoice(inv)}
                          >
                            Details
                          </button>
                          <button
                            className="sw-btn sw-btn--ghost"
                            disabled={downloadingId === inv.id}
                            style={{ minHeight: '36px', padding: '0 10px' }}
                            onClick={() => handleDownloadPdf(inv.id)}
                            title="Download PDF Invoice"
                          >
                            {downloadingId === inv.id ? '⌛' : '📄 PDF'}
                          </button>
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Invoice Details Modal */}
      {selectedInvoice && (() => {
        const totalAmt = selectedInvoice.totalAmount ?? selectedInvoice.totalCharge
        const sharedAlloc = selectedInvoice.sharedAllocation ?? selectedInvoice.sharedCostAllocation
        const statusVal = selectedInvoice.status ?? selectedInvoice.paidStatus ?? 'ISSUED'

        return (
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
                  <span className={`sw-status sw-status--${statusVal === 'PAID' ? 'green' : 'red'}`}>
                    {statusVal}
                  </span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--sw-border)', paddingBottom: '8px' }}>
                  <span style={{ color: 'var(--sw-text-secondary)' }}>Base Charge</span>
                  <span>{formatMoney(selectedInvoice.baseCharge)}</span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--sw-border)', paddingBottom: '8px' }}>
                  <span style={{ color: 'var(--sw-text-secondary)' }}>Shared Allocation</span>
                  <span>{formatMoney(sharedAlloc)}</span>
                </div>
                {selectedInvoice.adjustments != null && (
                  <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--sw-border)', paddingBottom: '8px' }}>
                    <span style={{ color: 'var(--sw-text-secondary)' }}>Adjustments</span>
                    <span>{formatMoney(selectedInvoice.adjustments)}</span>
                  </div>
                )}
                <div style={{ display: 'flex', justifyContent: 'space-between', paddingTop: '8px' }}>
                  <span style={{ fontWeight: 'bold' }}>Total Amount</span>
                  <strong style={{ fontSize: 'var(--sw-fs-md)' }}>{formatMoney(totalAmt)}</strong>
                </div>
              </div>
              <div style={{ display: 'flex', gap: '12px', marginTop: 'var(--sw-space-5)', justifyContent: 'flex-end' }}>
                <button
                  className="sw-btn sw-btn--primary"
                  disabled={downloadingId === selectedInvoice.id}
                  onClick={() => handleDownloadPdf(selectedInvoice.id)}
                >
                  {downloadingId === selectedInvoice.id ? 'Downloading...' : '📄 Download PDF'}
                </button>
                <button
                  className="sw-btn sw-btn--ghost"
                  onClick={() => setSelectedInvoice(null)}
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        )
      })()}
    </div>
  )
}
