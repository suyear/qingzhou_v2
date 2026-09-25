import http from './http'

export function getScheduleMode() {
  return http.get('/api/schedule/mode')
}

export function pageScheduleJobs(params) {
  return http.get('/api/schedule/jobs', { params })
}

export function createScheduleJob(payload) {
  return http.post('/api/schedule/jobs', payload)
}

export function updateScheduleJob(id, payload) {
  return http.put(`/api/schedule/jobs/${id}`, payload)
}

export function startScheduleJob(id) {
  return http.post(`/api/schedule/jobs/${id}/start`)
}

export function stopScheduleJob(id) {
  return http.post(`/api/schedule/jobs/${id}/stop`)
}

export function triggerScheduleJob(id) {
  return http.post(`/api/schedule/jobs/${id}/trigger`, null, { timeout: 60000 })
}

export function deleteScheduleJob(id) {
  return http.delete(`/api/schedule/jobs/${id}`)
}

export function previewSchedule(payload) {
  return http.post('/api/schedule/preview', payload)
}
