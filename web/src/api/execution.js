import http from './http'

export function pageExecutions(params) {
  return http.get('/api/executions', { params })
}

export function getExecution(id) {
  return http.get(`/api/executions/${id}`)
}

export function getExecutionChain(id) {
  return http.get(`/api/executions/${id}/chain`)
}

export function replayExecution(id, input) {
  return http.post(`/api/executions/${id}/replay`, { input: input || {} }, { timeout: 30000 })
}
