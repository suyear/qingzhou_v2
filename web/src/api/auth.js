import http from './http'

export function bootstrapStatus() {
  return http.get('/api/auth/bootstrap-status')
}

export function bootstrap(data) {
  return http.post('/api/auth/bootstrap', data)
}

export function login(data) {
  return http.post('/api/auth/login', data)
}

export function logout() {
  return http.post('/api/auth/logout')
}

export function fetchMe() {
  return http.get('/api/auth/me')
}

export function changePassword(data) {
  return http.post('/api/auth/change-password', data)
}

export function pageUsers(params) {
  return http.get('/api/users', { params })
}

export function listRoles() {
  return http.get('/api/users/roles')
}

export function listRolesDetail() {
  return http.get('/api/roles')
}

export function listPermissions() {
  return http.get('/api/roles/permissions')
}

export function updateRolePermissions(id, data) {
  return http.put(`/api/roles/${id}/permissions`, data)
}

export function createUser(data) {
  return http.post('/api/users', data)
}

export function updateUser(id, data) {
  return http.put(`/api/users/${id}`, data)
}

export function disableUser(id) {
  return http.post(`/api/users/${id}/disable`)
}

export function pageAuditLogs(params) {
  return http.get('/api/audit', { params })
}

export function licenseStatus() {
  return http.get('/api/license/status')
}

export function importLicense(data) {
  return http.post('/api/license/import', data)
}

export function generateDemoLicense(data) {
  return http.post('/api/license/generate-demo', data)
}

export function getSystemSettings() {
  return http.get('/api/system/settings')
}

export function saveSystemSettings(data) {
  return http.put('/api/system/settings', data)
}

export function systemHealthDetail() {
  return http.get('/api/system/health-detail')
}
