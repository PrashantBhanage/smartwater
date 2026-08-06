import { useEffect, useState, useMemo } from 'react'
import { useAuth } from '../context/AuthContext'
import {
  billingApi,
  householdsApi,
  tariffApi,
  getBulkPurchases,
  finalizeBillingCycle,
  usageLogsApi
} from '../api'
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts'

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

export default function AdminBillingPage() {
  const { user } = useAuth()
  const [activeCycle, setActiveCycle] = useState(null)
  const [households, setHouseholds] = useState([])
  const [householdData, setHouseholdData] = useState([])
  const [tariffPlan, setTariffPlan] = useState(null)
  const [bulkPurchases, setBulkPurchases] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [finalizing, setFinalizing] = useState(false)
  const [summaryModal, setSummaryModal] = useState(null)

  useEffect(() => {
    let active = true
    async function loadBillingDetails() {
      if (!user?.apartmentId) {
        setError('No apartment complex associated.')
        setLoading(false)
        return
      }

      try {
        setLoading(true)
        setError('')

        // 1. Fetch cycles and find the active/OPEN one
        const cycles = await billingApi.listByApartment(user.apartmentId)
        const openCycle = cycles.find(c => c.status === 'OPEN')
        
        if (!openCycle) {
          if (active) {
            setActiveCycle(null)
            setLoading(false)
          }
          return
        }
        
        if (active) setActiveCycle(openCycle)

        // 2. Fetch households
        const hhList = await householdsApi.listByApartment(user.apartmentId)
        if (active) setHouseholds(hhList)

        // 3. Fetch active tariff plan
        const tariffs = await tariffApi.list(user.apartmentId)
        const activePlan = tariffs.find(t => new Date(t.effectiveFromDate) <= new Date(openCycle.cycleEndDate)) || tariffs[0]
        if (active) setTariffPlan(activePlan)

        // 4. Fetch bulk purchases
        const purchasesSummary = await getBulkPurchases(user.apartmentId, openCycle.cycleStartDate, openCycle.cycleEndDate)
        if (active) setBulkPurchases(purchasesSummary.purchases || [])

        // 5. Fetch usage logs for each household in cycle range
        const listWithUsage = await Promise.all(
          hhList.map(async (hh) => {
            const logs = await usageLogsApi.list(hh.id)
            const cycleLogs = logs.filter(l => l.readingDate >= openCycle.cycleStartDate && l.readingDate <= openCycle.cycleEndDate)
            const consumption = cycleLogs.reduce((sum, l) => sum + Number(l.volumeUsedLiters), 0)
            return {
              ...hh,
              consumption
            }
          })
        )

        if (active) {
          setHouseholdData(listWithUsage)
        }
      } catch (err) {
        if (active) setError(err.message || 'Failed to load billing details.')
      } finally {
        if (active) setLoading(false)
      }
    }

    loadBillingDetails()
    return () => {
      active = false
    }
  }, [user?.apartmentId])

  // Calculation details in memory
  const calculations = useMemo(() => {
    if (!activeCycle || !tariffPlan || householdData.length === 0) return []

    // Calculate total cost from bulk purchases
    const totalSharedCost = bulkPurchases.reduce((sum, p) => sum + (Number(p.volumeLiters) * Number(p.unitCost)), 0)

    // Calculate total usage
    const totalUsage = householdData.reduce((sum, h) => sum + h.consumption, 0)

    return householdData.map((h) => {
      // Calculate base charge (TariffBillingEngine logic in JS)
      let baseCharge = 0
      if (h.hasMeter && h.consumption > 0) {
        const usageKl = h.consumption / 1000
        const tier1Limit = Number(tariffPlan.tier1LimitKl)
        const tier1Rate = Number(tariffPlan.tier1Rate)
        const tier2Rate = Number(tariffPlan.tier2Rate)

        if (usageKl <= tier1Limit) {
          baseCharge = usageKl * tier1Rate
        } else {
          baseCharge = (tier1Limit * tier1Rate) + ((usageKl - tier1Limit) * tier2Rate)
        }
      }

      // Shared cost allocation
      let sharedAllocation = 0
      if (totalSharedCost > 0) {
        if (totalUsage > 0) {
          sharedAllocation = (h.consumption / totalUsage) * totalSharedCost
        } else {
          // Fallback to flat area
          const totalArea = householdData.reduce((sum, hh) => sum + (hh.areaSqft || 0), 0)
          if (totalArea > 0) {
            sharedAllocation = ((h.areaSqft || 0) / totalArea) * totalSharedCost
          } else {
            sharedAllocation = totalSharedCost / householdData.length
          }
        }
      }

      return {
        ...h,
        baseCharge,
        sharedAllocation,
        totalCharge: baseCharge + sharedAllocation
      }
    })
  }, [activeCycle, tariffPlan, householdData, bulkPurchases])

  const handleFinalize = async () => {
    if (!activeCycle) return
    try {
      setFinalizing(true)
      const summary = await finalizeBillingCycle(activeCycle.id)
      setSummaryModal(summary)
      // Clear active cycle
      setActiveCycle(null)
    } catch (err) {
      setError(err.message || 'Failed to finalize cycle.')
    } finally {
      setFinalizing(false)
    }
  }

  const chartData = calculations.map(c => ({
    name: `Flat ${c.flatNumber}`,
    consumption: c.consumption
  }))

  return (
    <div className="sw-fade-in" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sw-space-6)' }}>
      <header>
        <h1 className="sw-page-title">Cycle Finalization</h1>
        <p className="sw-page-subtitle">Review active billing cycle, cost distributions, and generate household invoices.</p>
      </header>

      {error && <div className="sw-banner sw-banner--error">{error}</div>}

      {loading ? (
        <div style={{ display: 'grid', placeItems: 'center', minHeight: 160 }}>
          <div className="sw-spinner" style={{ width: 32, height: 32 }} />
        </div>
      ) : !activeCycle ? (
        <div className="sw-empty">No active (OPEN) billing cycle found. Please open a billing cycle first.</div>
      ) : (
        <>
          {/* Active Cycle Panel */}
          <section className="sw-panel" style={{ padding: 'var(--sw-space-5)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
              <div>
                <h3 style={{ margin: 0 }}>Active Billing Cycle</h3>
                <p style={{ margin: '4px 0 0', color: 'var(--sw-text-secondary)' }}>
                  Period: <strong>{activeCycle.cycleStartDate}</strong> to <strong>{activeCycle.cycleEndDate}</strong>
                </p>
              </div>
              <button
                className="sw-btn sw-btn--primary"
                onClick={handleFinalize}
                disabled={finalizing}
              >
                {finalizing ? 'Finalizing...' : 'Finalize Cycle'}
              </button>
            </div>
          </section>

          {/* Recharts Bar Chart Panel */}
          <section className="sw-panel" style={{ padding: 'var(--sw-space-5)' }}>
            <h3 style={{ margin: '0 0 24px' }}>Consumption Comparison (Liters)</h3>
            <ResponsiveContainer width="100%" height={300}>
              <BarChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--sw-border)" />
                <XAxis dataKey="name" stroke="var(--sw-text-secondary)" fontSize={12} />
                <YAxis stroke="var(--sw-text-secondary)" fontSize={12} unit=" L" />
                <Tooltip
                  contentStyle={{
                    background: 'var(--sw-surface-raised)',
                    border: '1px solid var(--sw-border)',
                    borderRadius: 'var(--sw-radius)'
                  }}
                />
                <Bar dataKey="consumption" fill="var(--sw-accent)" radius={[4, 4, 0, 0]} name="Consumption (L)" />
              </BarChart>
            </ResponsiveContainer>
          </section>

          {/* Cost breakdown table */}
          <section className="sw-panel" style={{ padding: 'var(--sw-space-5)' }}>
            <h3 style={{ margin: '0 0 16px' }}>Estimated Invoice Ledger</h3>
            <div style={{ overflowX: 'auto' }}>
              <table className="sw-table">
                <thead>
                  <tr>
                    <th>Flat Number</th>
                    <th>Consumption (L)</th>
                    <th>Base Charge</th>
                    <th>Shared Allocation</th>
                    <th>Total Charge</th>
                  </tr>
                </thead>
                <tbody>
                  {calculations.map((c) => (
                    <tr key={c.id}>
                      <td>{c.flatNumber}</td>
                      <td>{formatNumber(c.consumption, ' L')}</td>
                      <td>{formatMoney(c.baseCharge)}</td>
                      <td>{formatMoney(c.sharedAllocation)}</td>
                      <td><strong>{formatMoney(c.totalCharge)}</strong></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </>
      )}

      {/* Summary Modal */}
      {summaryModal && (
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
              Billing Cycle Finalized
            </h2>
            <div className="sw-banner sw-banner--ok" style={{ marginBottom: '16px' }}>
              Invoices have been generated and published successfully.
            </div>
            <div style={{ display: 'grid', gap: 'var(--sw-space-3)', margin: 'var(--sw-space-4) 0' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--sw-border)', paddingBottom: '8px' }}>
                <span style={{ color: 'var(--sw-text-secondary)' }}>Invoices Generated</span>
                <strong>{summaryModal.invoicesGenerated}</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--sw-border)', paddingBottom: '8px' }}>
                <span style={{ color: 'var(--sw-text-secondary)' }}>Total Base Charge</span>
                <span>{formatMoney(summaryModal.totalBaseCharge)}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid var(--sw-border)', paddingBottom: '8px' }}>
                <span style={{ color: 'var(--sw-text-secondary)' }}>Total Shared Cost</span>
                <span>{formatMoney(summaryModal.totalSharedAllocation)}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', paddingTop: '8px' }}>
                <span style={{ fontWeight: 'bold' }}>Total Invoice Amount</span>
                <strong style={{ fontSize: 'var(--sw-fs-md)' }}>{formatMoney(summaryModal.totalAmount)}</strong>
              </div>
            </div>
            <div style={{ display: 'flex', gap: '12px', marginTop: 'var(--sw-space-5)', justifyContent: 'flex-end' }}>
              <button
                className="sw-btn sw-btn--primary"
                onClick={() => setSummaryModal(null)}
              >
                Done
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
