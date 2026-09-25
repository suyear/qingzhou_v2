import http from './http'

export function getDashboardOverview(params) {
  return http.get('/api/dashboard/overview', { params })
}

export function getModuleStats() {
  return http.get('/api/dashboard/module-stats')
}
