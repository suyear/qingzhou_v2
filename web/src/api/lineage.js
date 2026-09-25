import http from './http'

export function getComponentLineage(id) {
  return http.get(`/api/lineage/components/${id}`)
}

export function getWorkflowLineage(id) {
  return http.get(`/api/lineage/workflows/${id}`)
}
