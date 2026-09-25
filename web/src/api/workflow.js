import http from './http'

export function pageWorkflows(params) {
  return http.get('/api/workflows', { params })
}

export function getWorkflow(id) {
  return http.get(`/api/workflows/${id}`)
}

export function createWorkflow(payload) {
  return http.post('/api/workflows', payload)
}

export function updateWorkflow(id, payload) {
  return http.put(`/api/workflows/${id}`, payload)
}

export function tryRunWorkflow(id, payload) {
  return http.post(`/api/workflows/${id}/try-run`, payload, { timeout: 60000 })
}

export function publishWorkflow(id) {
  return http.post(`/api/workflows/${id}/publish`)
}

export function disableWorkflow(id) {
  return http.post(`/api/workflows/${id}/disable`)
}
