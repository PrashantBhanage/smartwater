import api from './client'

export const apartmentsApi = {
  get: (id) => api.get(`/apartments/${id}`).then((r) => r.data),
  create: (payload) => api.post('/apartments', payload).then((r) => r.data),
}

export const householdsApi = {
  get: (id) => api.get(`/households/${id}`).then((r) => r.data),
  create: (payload) => api.post('/households', payload).then((r) => r.data),
  assignResident: (id, residentEmail) =>
    api.post(`/households/${id}/assign-resident`, { residentEmail }).then((r) => r.data),
  updateMeterConfig: (id, payload) =>
    api.patch(`/households/${id}/meter-config`, payload).then((r) => r.data),
}

export const usageLogsApi = {
  list: (householdId) =>
    api.get('/usage-logs', { params: { householdId } }).then((r) => r.data),
  create: (payload) => api.post('/usage-logs', payload).then((r) => r.data),
  bulkUpload: (file) => {
    const form = new FormData()
    form.append('file', file)
    return api
      .post('/usage-logs/bulk-upload', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((r) => r.data)
  },
}

export const tariffApi = {
  create: (payload) => api.post('/tariff-plans', payload).then((r) => r.data),
  list: (apartmentId) =>
    api.get('/tariff-plans', { params: { apartmentId } }).then((r) => r.data),
}

export const purchasesApi = {
  create: (payload) => api.post('/purchases', payload).then((r) => r.data),
  listByCycle: (cycleId) =>
    api.get('/purchases', { params: { cycleId } }).then((r) => r.data),
}

export const billingApi = {
  open: (payload) => api.post('/billing-cycles', payload).then((r) => r.data),
  get: (id) => api.get(`/billing-cycles/${id}`).then((r) => r.data),
  finalize: (id) => api.patch(`/billing-cycles/${id}/finalize`).then((r) => r.data),
  archive: (id) => api.patch(`/billing-cycles/${id}/archive`).then((r) => r.data),
  invoices: (id) => api.get(`/billing-cycles/${id}/invoices`).then((r) => r.data),
}

export const alertsApi = {
  list: (householdId) =>
    api.get('/alerts', { params: { householdId } }).then((r) => r.data),
}
