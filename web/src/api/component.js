import http from './http'

export function pageComponents(params) {
  return http.get('/api/components', { params })
}

export function getComponent(id) {
  return http.get(`/api/components/${id}`)
}

export function createComponent(payload) {
  return http.post('/api/components', payload)
}

export function updateComponent(id, payload) {
  return http.put(`/api/components/${id}`, payload)
}

export function deleteComponent(id) {
  return http.delete(`/api/components/${id}`)
}

export function testComponent(id, payload) {
  return http.post(`/api/components/${id}/test`, payload || {}, { timeout: 30000 })
}
