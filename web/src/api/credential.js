import http from './http'

export function pageCredentials(params) {
  return http.get('/api/credentials', { params })
}

export function createCredential(payload) {
  return http.post('/api/credentials', payload)
}

export function updateCredential(id, payload) {
  return http.put(`/api/credentials/${id}`, payload)
}

export function deleteCredential(id) {
  return http.delete(`/api/credentials/${id}`)
}

export function enableCredential(id) {
  return http.post(`/api/credentials/${id}/enable`)
}

export function disableCredential(id) {
  return http.post(`/api/credentials/${id}/disable`)
}

export function testCredential(id) {
  return http.post(`/api/credentials/${id}/test`, null, { timeout: 30000 })
}
