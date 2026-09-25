/** 默认展示的自然语言调度类型（不含 Cron） */
export const SCHEDULE_TYPES = [
  { value: 'INTERVAL', label: '固定间隔', desc: '每 N 秒/分钟/小时' },
  { value: 'DAILY', label: '每天', desc: '指定时刻每天执行' },
  { value: 'WEEKLY', label: '每周', desc: '选择星期几执行' },
  { value: 'ONCE', label: '仅一次', desc: '指定时间执行一次' },
]

export const CRON_SCHEDULE_TYPE = {
  value: 'CRON',
  label: '自定义 Cron',
  desc: '6 位表达式，适合复杂规则',
}

/** 含 Cron，供标签/摘要等完整列表使用 */
export const ALL_SCHEDULE_TYPES = [...SCHEDULE_TYPES, CRON_SCHEDULE_TYPE]

export const WEEK_OPTIONS = [
  { value: 1, label: '周一' },
  { value: 2, label: '周二' },
  { value: 3, label: '周三' },
  { value: 4, label: '周四' },
  { value: 5, label: '周五' },
  { value: 6, label: '周六' },
  { value: 7, label: '周日' },
]

export const INTERVAL_UNITS = [
  { value: 'seconds', label: '秒', factor: 1 },
  { value: 'minutes', label: '分钟', factor: 60 },
  { value: 'hours', label: '小时', factor: 3600 },
]

export const CRON_PRESETS = [
  { label: '每分钟', cron: '0 * * * * *' },
  { label: '每 5 分钟', cron: '0 */5 * * * *' },
  { label: '每小时', cron: '0 0 * * * *' },
  { label: '每天 8 点', cron: '0 0 8 * * *' },
  { label: '工作日 9 点', cron: '0 0 9 * * MON-FRI' },
]

export function defaultScheduleForm() {
  return {
    scheduleType: 'INTERVAL',
    cronExpr: '0 */5 * * * *',
    intervalValue: 5,
    intervalUnit: 'minutes',
    fireAt: '',
    dailyTime: '08:00',
    weekDays: [1, 2, 3, 4, 5],
  }
}

export function intervalToForm(seconds) {
  if (!seconds) {
    return { intervalValue: 5, intervalUnit: 'minutes' }
  }
  if (seconds % 3600 === 0) {
    return { intervalValue: seconds / 3600, intervalUnit: 'hours' }
  }
  if (seconds % 60 === 0) {
    return { intervalValue: seconds / 60, intervalUnit: 'minutes' }
  }
  return { intervalValue: seconds, intervalUnit: 'seconds' }
}

export function intervalToSeconds(value, unit) {
  const num = Number(value)
  if (!num || num <= 0) return null
  const factor = INTERVAL_UNITS.find((item) => item.value === unit)?.factor || 60
  return Math.round(num * factor)
}

export function jobToForm(row) {
  const base = defaultScheduleForm()
  const type = row.scheduleType || 'CRON'
  const interval = intervalToForm(row.intervalSeconds)
  return {
    ...base,
    scheduleType: type,
    cronExpr: row.cronExpr || base.cronExpr,
    intervalValue: interval.intervalValue,
    intervalUnit: interval.intervalUnit,
    fireAt: row.fireAt ? String(row.fireAt).replace(' ', 'T').slice(0, 16) : '',
    dailyTime: row.dailyTime || base.dailyTime,
    weekDays: row.weekDays
      ? String(row.weekDays).split(',').map((item) => Number(item.trim())).filter(Boolean)
      : base.weekDays,
  }
}

export function formToPayload(form) {
  const payload = {
    scheduleType: form.scheduleType,
    cronExpr: form.scheduleType === 'CRON' ? form.cronExpr : undefined,
    intervalSeconds: form.scheduleType === 'INTERVAL'
      ? intervalToSeconds(form.intervalValue, form.intervalUnit)
      : undefined,
    fireAt: form.scheduleType === 'ONCE' && form.fireAt
      ? `${form.fireAt.replace('T', ' ')}:00`.replace('  ', ' ')
      : undefined,
    dailyTime: form.scheduleType === 'DAILY' || form.scheduleType === 'WEEKLY'
      ? form.dailyTime
      : undefined,
    weekDays: form.scheduleType === 'WEEKLY'
      ? (form.weekDays || []).join(',')
      : undefined,
  }
  return payload
}

export function previewPayload(form) {
  return formToPayload(form)
}

export function scheduleSummary(job) {
  const type = job.scheduleType || 'CRON'
  if (type === 'INTERVAL') {
    const { intervalValue, intervalUnit } = intervalToForm(job.intervalSeconds)
    const unitLabel = INTERVAL_UNITS.find((item) => item.value === intervalUnit)?.label || ''
    return `每 ${intervalValue} ${unitLabel}`
  }
  if (type === 'DAILY') {
    return `每天 ${job.dailyTime || '—'}`
  }
  if (type === 'WEEKLY') {
    const days = (job.weekDays || '')
      .split(',')
      .map((item) => WEEK_OPTIONS.find((opt) => opt.value === Number(item))?.label)
      .filter(Boolean)
      .join('、')
    return `每周 ${days || '—'} ${job.dailyTime || ''}`.trim()
  }
  if (type === 'ONCE') {
    return `一次性 ${job.fireAt || '—'}`
  }
  return job.cronExpr || '—'
}

export function scheduleTypeLabel(type) {
  return ALL_SCHEDULE_TYPES.find((item) => item.value === type)?.label || type || 'Cron'
}
