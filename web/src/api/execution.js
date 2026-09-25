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

export function batchReplayExecutions(ids) {
  return http.post('/api/executions/batch-replay', { ids }, { timeout: 120000 })
}

export async function exportExecutions(params) {
  // http 拦截器对 blob 直接返回 Blob 本体
  const blob = await http.get('/api/executions/export', {
    params,
    responseType: 'blob',
    timeout: 60000,
  })
  const file = blob instanceof Blob ? blob : new Blob([blob], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(file)
  const a = document.createElement('a')
  a.href = url
  a.download = `executions_${Date.now()}.csv`
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}
