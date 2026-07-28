import axios from 'axios'

/**
 * Shared Axios client.
 * Base URL uses the Vite proxy in dev (`/api` → localhost:8080).
 * JWT is attached via setAuthToken() from AuthContext.
 */
const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
})

let authToken = null

export function setAuthToken(token) {
  authToken = token
}

api.interceptors.request.use((config) => {
  if (authToken) {
    config.headers.Authorization = `Bearer ${authToken}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const message =
      error.response?.data?.message ||
      error.message ||
      'Request failed'
    const status = error.response?.status
    return Promise.reject({ message, status, raw: error })
  },
)

export default api
