import { formatJson } from './format.js'

export function isBlankPayload(value) {
  if (value == null || value === '') return true
  if (typeof value === 'string' && !value.trim()) return true
  if (typeof value === 'object' && !Array.isArray(value) && !Object.keys(value).length) return true
  return false
}

export function codeDisplayText(value, emptyText = '暂无内容') {
  if (isBlankPayload(value)) {
    return { text: emptyText, empty: true }
  }
  return { text: formatJson(value), empty: false }
}
