import http from './http'

export function pageOpenapiApps(params) {
  return http.get('/api/openapi/apps', { params })
}

export function createOpenapiApp(payload) {
  return http.post('/api/openapi/apps', payload)
}

export function updateOpenapiApp(id, payload) {
  return http.put(`/api/openapi/apps/${id}`, payload)
}

export function resetOpenapiSecret(id) {
  return http.post(`/api/openapi/apps/${id}/reset-secret`)
}

export function listGrantedWorkflows(appId) {
  return http.get(`/api/openapi/apps/${appId}/workflows`)
}

export function bindOpenapiWorkflows(appId, workflowIds) {
  return http.put(`/api/openapi/apps/${appId}/workflows`, { workflowIds })
}

export function previewOpenapiInvoke(appId, payload) {
  return http.post(`/api/openapi/apps/${appId}/invoke-preview`, payload)
}

export function invokeOpenapi(appId, payload) {
  return http.post(`/api/openapi/apps/${appId}/invoke`, payload, { timeout: 30000 })
}
