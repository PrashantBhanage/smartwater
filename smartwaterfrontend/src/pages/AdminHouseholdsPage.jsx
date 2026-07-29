import { useEffect, useState } from 'react'
import { useAuth } from '../context/AuthContext'
import { householdsApi } from '../api'

/**
 * Admin Households Page.
 * Allows the admin to view all households in their complex,
 * register new households, assign residents to households by email,
 * and configure household meters/thresholds.
 * Styled in warm, minimal Japandi.
 */
export default function AdminHouseholdsPage() {
  const { user } = useAuth()
  const [households, setHouseholds] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [successMessage, setSuccessMessage] = useState('')

  // New Household Form state
  const [newFlatNumber, setNewFlatNumber] = useState('')
  const [newOccupancy, setNewOccupancy] = useState(1)
  const [newHasMeter, setNewHasMeter] = useState(false)
  const [newThreshold, setNewThreshold] = useState(500)
  const [createLoading, setCreateLoading] = useState(false)

  // Assign Resident State
  const [assigningHhId, setAssigningHhId] = useState(null)
  const [residentEmail, setResidentEmail] = useState('')
  const [assignLoading, setAssignLoading] = useState(false)

  // Edit Meter Config State
  const [editingHhId, setEditingHhId] = useState(null)
  const [editHasMeter, setEditHasMeter] = useState(false)
  const [editThreshold, setEditThreshold] = useState(500)
  const [editLoading, setEditLoading] = useState(false)

  async function loadHouseholds() {
    if (!user?.apartmentId) {
      setError('You are not associated with any apartment complex.')
      setLoading(false)
      return
    }
    try {
      const data = await householdsApi.listByApartment(user.apartmentId)
      setHouseholds(data)
    } catch (err) {
      setError(err.message || 'Failed to load households.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    async function load() {
      if (!user?.apartmentId) {
        setError('You are not associated with any apartment complex.')
        setLoading(false)
        return
      }
      try {
        const data = await householdsApi.listByApartment(user.apartmentId)
        setHouseholds(data)
      } catch (err) {
        setError(err.message || 'Failed to load households.')
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [user?.apartmentId])

  async function handleCreateHousehold(e) {
    e.preventDefault()
    setError('')
    setSuccessMessage('')
    if (!newFlatNumber.trim()) return setError('Flat number is required.')

    setCreateLoading(true)
    try {
      await householdsApi.create({
        apartmentId: user.apartmentId,
        flatNumber: newFlatNumber.trim(),
        occupancyCount: Number(newOccupancy),
        hasMeter: newHasMeter,
        dailyThresholdLiters: Number(newThreshold),
      })
      setSuccessMessage(`Flat ${newFlatNumber} registered successfully.`)
      setNewFlatNumber('')
      setNewOccupancy(1)
      setNewHasMeter(false)
      setNewThreshold(500)
      await loadHouseholds()
    } catch (err) {
      setError(err.message || 'Failed to create household.')
    } finally {
      setCreateLoading(false)
    }
  }

  async function handleAssignResident(e) {
    e.preventDefault()
    setError('')
    setSuccessMessage('')
    if (!residentEmail.trim()) return setError('Resident email is required.')

    setAssignLoading(true)
    try {
      await householdsApi.assignResident(assigningHhId, residentEmail.trim())
      setSuccessMessage(`Resident email ${residentEmail} assigned to flat.`)
      setResidentEmail('')
      setAssigningHhId(null)
      await loadHouseholds()
    } catch (err) {
      setError(err.message || 'Failed to assign resident. Verify that the resident has registered their account first.')
    } finally {
      setAssignLoading(false)
    }
  }

  async function handleUpdateMeterConfig(e) {
    e.preventDefault()
    setError('')
    setSuccessMessage('')

    setEditLoading(true)
    try {
      await householdsApi.updateMeterConfig(editingHhId, {
        hasMeter: editHasMeter,
        dailyThresholdLiters: Number(editThreshold),
      })
      setSuccessMessage('Meter and threshold configuration updated.')
      setEditingHhId(null)
      await loadHouseholds()
    } catch (err) {
      setError(err.message || 'Failed to update configuration.')
    } finally {
      setEditLoading(false)
    }
  }

  if (loading) {
    return (
      <div style={{ display: 'grid', placeItems: 'center', minHeight: '50vh' }}>
        <div className="sw-spinner" style={{ width: 32, height: 32 }} />
      </div>
    )
  }

  return (
    <div className="sw-fade-in" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sw-space-6)' }}>
      <header>
        <h1 className="sw-page-title">Manage Households</h1>
        <p className="sw-page-subtitle">
          Configure water meters, daily limits, and resident links for each household.
        </p>
      </header>

      {error ? (
        <div className="sw-banner sw-banner--error" role="alert">
          {error}
        </div>
      ) : null}

      {successMessage ? (
        <div className="sw-banner sw-banner--ok" role="alert">
          {successMessage}
        </div>
      ) : null}

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 320px', gap: 'var(--sw-space-5)', alignItems: 'start' }}>
        {/* Households List Table */}
        <div className="sw-panel" style={{ padding: 'var(--sw-space-5)', background: 'var(--sw-surface)' }}>
          <h2 className="sw-page-title" style={{ fontSize: 'var(--sw-fs-lg)', marginBottom: 16 }}>Households Directory</h2>
          {households.length === 0 ? (
            <div className="sw-empty">No households registered in your complex yet.</div>
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table className="sw-table">
                <thead>
                  <tr>
                    <th>Flat Number</th>
                    <th>Occupants</th>
                    <th>Smart Meter</th>
                    <th>Threshold</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {households.map((hh) => (
                    <tr key={hh.id}>
                      <td style={{ fontWeight: 500 }}>{hh.flatNumber}</td>
                      <td>{hh.occupancyCount}</td>
                      <td>
                        <span className={`sw-status ${hh.hasMeter ? 'sw-status--green' : 'sw-status--neutral'}`}>
                          {hh.hasMeter ? 'Active' : 'None'}
                        </span>
                      </td>
                      <td>{hh.dailyThresholdLiters} L</td>
                      <td>
                        <div style={{ display: 'flex', gap: 8 }}>
                          <button
                            className="sw-btn sw-btn--ghost"
                            style={{ minHeight: 30, padding: '0 8px', fontSize: '11px' }}
                            onClick={() => {
                              setAssigningHhId(hh.id)
                              setEditingHhId(null)
                            }}
                          >
                            Link Resident
                          </button>
                          <button
                            className="sw-btn sw-btn--secondary"
                            style={{ minHeight: 30, padding: '0 8px', fontSize: '11px' }}
                            onClick={() => {
                              setEditingHhId(hh.id)
                              setEditHasMeter(hh.hasMeter)
                              setEditThreshold(hh.dailyThresholdLiters)
                              setAssigningHhId(null)
                            }}
                          >
                            Configure
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Sidebar panels */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sw-space-5)' }}>
          {/* Register Household Panel */}
          <div className="sw-panel" style={{ padding: 'var(--sw-space-4)', background: 'var(--sw-surface)' }}>
            <h3 className="sw-page-title" style={{ fontSize: 'var(--sw-fs-base)', marginBottom: 12 }}>Register Flat</h3>
            <form onSubmit={handleCreateHousehold} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <label className="sw-field">
                <span className="sw-field__label">Flat Number</span>
                <input
                  className="sw-input"
                  type="text"
                  placeholder="e.g. A-101"
                  value={newFlatNumber}
                  onChange={(e) => setNewFlatNumber(e.target.value)}
                  required
                />
              </label>
              <label className="sw-field">
                <span className="sw-field__label">Occupancy count</span>
                <input
                  className="sw-input"
                  type="number"
                  min="0"
                  value={newOccupancy}
                  onChange={(e) => setNewOccupancy(e.target.value)}
                  required
                />
              </label>
              <label className="sw-field">
                <span className="sw-field__label">Daily Limit (Liters)</span>
                <input
                  className="sw-input"
                  type="number"
                  min="1"
                  value={newThreshold}
                  onChange={(e) => setNewThreshold(e.target.value)}
                  required
                />
              </label>
              <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 'var(--sw-fs-sm)', marginTop: 4 }}>
                <input
                  type="checkbox"
                  checked={newHasMeter}
                  onChange={(e) => setNewHasMeter(e.target.checked)}
                />
                <span>Has Smart Water Meter</span>
              </label>

              <button type="submit" className="sw-btn sw-btn--primary" style={{ width: '100%', marginTop: 8 }} disabled={createLoading}>
                {createLoading ? 'Registering…' : 'Register Flat'}
              </button>
            </form>
          </div>

          {/* Link Resident Dialog Panel */}
          {assigningHhId && (
            <div className="sw-panel" style={{ padding: 'var(--sw-space-4)', background: 'var(--sw-surface-raised)', border: '1px solid var(--sw-accent)' }}>
              <h3 className="sw-page-title" style={{ fontSize: 'var(--sw-fs-base)', marginBottom: 12 }}>Link Resident</h3>
              <p style={{ fontSize: 'var(--sw-fs-xs)', color: 'var(--sw-text-secondary)', marginBottom: 12 }}>
                Associate a registered resident's account by email.
              </p>
              <form onSubmit={handleAssignResident} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                <label className="sw-field">
                  <span className="sw-field__label">Resident Email</span>
                  <input
                    className="sw-input"
                    type="email"
                    placeholder="email@test.com"
                    value={residentEmail}
                    onChange={(e) => setResidentEmail(e.target.value)}
                    required
                  />
                </label>
                <div style={{ display: 'flex', gap: 8, marginTop: 4 }}>
                  <button type="submit" className="sw-btn sw-btn--primary" style={{ flex: 1 }} disabled={assignLoading}>
                    {assignLoading ? 'Saving…' : 'Link'}
                  </button>
                  <button type="button" className="sw-btn sw-btn--secondary" onClick={() => setAssigningHhId(null)}>
                    Cancel
                  </button>
                </div>
              </form>
            </div>
          )}

          {/* Meter Config Dialog Panel */}
          {editingHhId && (
            <div className="sw-panel" style={{ padding: 'var(--sw-space-4)', background: 'var(--sw-surface-raised)', border: '1px solid var(--sw-accent)' }}>
              <h3 className="sw-page-title" style={{ fontSize: 'var(--sw-fs-base)', marginBottom: 12 }}>Configure Meter</h3>
              <form onSubmit={handleUpdateMeterConfig} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                <label className="sw-field">
                  <span className="sw-field__label">Daily Limit (Liters)</span>
                  <input
                    className="sw-input"
                    type="number"
                    min="1"
                    value={editThreshold}
                    onChange={(e) => setEditThreshold(e.target.value)}
                    required
                  />
                </label>
                <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 'var(--sw-fs-sm)', marginTop: 4 }}>
                  <input
                    type="checkbox"
                    checked={editHasMeter}
                    onChange={(e) => setEditHasMeter(e.target.checked)}
                  />
                  <span>Has Smart Water Meter</span>
                </label>
                <div style={{ display: 'flex', gap: 8, marginTop: 4 }}>
                  <button type="submit" className="sw-btn sw-btn--primary" style={{ flex: 1 }} disabled={editLoading}>
                    {editLoading ? 'Saving…' : 'Update'}
                  </button>
                  <button type="button" className="sw-btn sw-btn--secondary" onClick={() => setEditingHhId(null)}>
                    Cancel
                  </button>
                </div>
              </form>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
