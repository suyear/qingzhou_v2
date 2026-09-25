const TRIGGER_LABEL = {
  TRY_RUN: '试运行',
  SCHEDULE: '调度',
  OPENAPI: '开放调用',
  REPLAY: '重放',
  MANUAL: '手动',
}

const WORKFLOW_STATUS = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  DISABLED: '已停用',
}

const EXEC_STATUS = {
  SUCCESS: '成功',
  FAILED: '失败',
  RUNNING: '运行中',
  TIMEOUT: '超时',
  PENDING: '排队中',
  CANCELLED: '已取消',
}

export function triggerLabel(value) {
  return TRIGGER_LABEL[value] || value || '—'
}

export function workflowStatusLabel(value) {
  return WORKFLOW_STATUS[value] || value || '—'
}

export function execStatusLabel(value) {
  return EXEC_STATUS[value] || value || '—'
}

export function statusTagType(value) {
  if (value === 'SUCCESS' || value === 'PUBLISHED' || value === 1) return 'success'
  if (value === 'FAILED' || value === 'TIMEOUT') return 'danger'
  if (value === 'RUNNING' || value === 'DRAFT' || value === 'PENDING') return 'warning'
  return 'info'
}

export function execStatusType(value) {
  if (value === 'SUCCESS') return 'success'
  if (value === 'FAILED' || value === 'TIMEOUT') return 'danger'
  if (value === 'RUNNING') return 'warning'
  return 'info'
}

export function workflowStatusType(value) {
  if (value === 'PUBLISHED') return 'success'
  if (value === 'DISABLED') return 'info'
  return 'warning'
}

export function formatTime(value) {
  if (!value) return '—'
  if (value instanceof Date) {
    const pad = (n) => String(n).padStart(2, '0')
    return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())} ${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`
  }
  const raw = String(value).replace('T', ' ')
  return raw.length >= 19 ? raw.slice(0, 19) : raw
}

export function durationText(ms) {
  if (ms == null || ms === '') return '—'
  const n = Number(ms)
  if (Number.isNaN(n)) return '—'
  if (n < 1000) return `${n}ms`
  return `${(n / 1000).toFixed(n >= 10000 ? 0 : 1)}s`
}

export function formatJson(value) {
  if (value == null || value === '') return '{}'
  if (typeof value === 'object') {
    return JSON.stringify(value, null, 2)
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return String(value)
  }
}

export async function copyText(text) {
  const value = String(text || '')
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(value)
    return
  }
  const el = document.createElement('textarea')
  el.value = value
  el.setAttribute('readonly', '')
  el.style.position = 'fixed'
  el.style.left = '-9999px'
  document.body.appendChild(el)
  el.select()
  document.execCommand('copy')
  document.body.removeChild(el)
}
