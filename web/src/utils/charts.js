import { execStatusLabel, execStatusType, triggerLabel, workflowStatusLabel, workflowStatusType } from './format.js'

export const CHART_COLORS = {
  primary: '#2563eb',
  success: '#059669',
  danger: '#dc2626',
  warning: '#d97706',
  info: '#64748b',
  muted: '#94a3b8',
  series: ['#2563eb', '#059669', '#d97706', '#7c3aed', '#0891b2', '#dc2626'],
}

export const STATUS_COLOR = {
  SUCCESS: CHART_COLORS.success,
  FAILED: CHART_COLORS.danger,
  TIMEOUT: CHART_COLORS.danger,
  RUNNING: CHART_COLORS.warning,
  PENDING: CHART_COLORS.info,
  CANCELLED: CHART_COLORS.muted,
}

export const TRIGGER_COLOR = {
  TRY_RUN: '#2563eb',
  SCHEDULE: '#7c3aed',
  OPENAPI: '#0891b2',
  REPLAY: '#d97706',
  MANUAL: '#64748b',
}

const AXIS = {
  axisLine: { lineStyle: { color: '#e2e8f0' } },
  axisTick: { show: false },
  axisLabel: { color: '#64748b', fontSize: 11 },
  splitLine: { lineStyle: { color: '#eef2f6', type: 'dashed' } },
}

export function hasChartData(list, key = 'count') {
  return Array.isArray(list) && list.some((item) => Number(item?.[key] || 0) > 0)
}

export function trendOption(points = []) {
  const dates = points.map((item) => String(item.date || '').slice(5))
  return {
    color: [CHART_COLORS.primary, CHART_COLORS.success, CHART_COLORS.danger],
    tooltip: { trigger: 'axis' },
    legend: {
      data: ['全部', '成功', '失败'],
      top: 0,
      right: 0,
      itemWidth: 10,
      itemHeight: 8,
      textStyle: { color: '#64748b', fontSize: 12 },
    },
    grid: { left: 8, right: 8, top: 32, bottom: 4, containLabel: true },
    xAxis: { type: 'category', data: dates, ...AXIS, boundaryGap: false },
    yAxis: { type: 'value', minInterval: 1, ...AXIS },
    series: [
      {
        name: '全部',
        type: 'line',
        smooth: true,
        showSymbol: points.length <= 14,
        data: points.map((item) => item.total || 0),
        areaStyle: { color: 'rgba(37, 99, 235, 0.08)' },
        lineStyle: { width: 2 },
      },
      {
        name: '成功',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: points.map((item) => item.successCount || 0),
        lineStyle: { width: 2 },
      },
      {
        name: '失败',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: points.map((item) => item.failedCount || 0),
        lineStyle: { width: 2 },
      },
    ],
  }
}

export function sharePieOption(items = [], colorMap = {}) {
  return {
    color: items.map((item, index) => colorMap[item.name] || CHART_COLORS.series[index % CHART_COLORS.series.length]),
    tooltip: { trigger: 'item', formatter: '{b}：{c}（{d}%）' },
    legend: {
      orient: 'vertical',
      right: 4,
      top: 'middle',
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { color: '#64748b', fontSize: 12 },
    },
    series: [
      {
        type: 'pie',
        radius: ['48%', '70%'],
        center: ['36%', '52%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        data: items.map((item) => ({
          name: item.label || item.name,
          value: item.count || 0,
        })),
      },
    ],
  }
}

export function barOption(items = [], colorMap = {}, valueKey = 'count') {
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 8, right: 12, top: 16, bottom: 4, containLabel: true },
    xAxis: { type: 'category', data: items.map((item) => item.label || item.name), ...AXIS },
    yAxis: { type: 'value', minInterval: 1, ...AXIS },
    series: [
      {
        type: 'bar',
        barMaxWidth: 28,
        itemStyle: {
          borderRadius: [6, 6, 0, 0],
          color: (params) => {
            const raw = items[params.dataIndex]
            return colorMap[raw?.name] || CHART_COLORS.series[params.dataIndex % CHART_COLORS.series.length]
          },
        },
        data: items.map((item) => item[valueKey] || 0),
      },
    ],
  }
}

export function failRateOption(points = []) {
  const dates = points.map((item) => String(item.date || '').slice(5))
  return {
    color: [CHART_COLORS.danger],
    tooltip: {
      trigger: 'axis',
      valueFormatter: (value) => (value == null ? '—' : `${value}%`),
    },
    grid: { left: 8, right: 8, top: 16, bottom: 4, containLabel: true },
    xAxis: { type: 'category', data: dates, ...AXIS, boundaryGap: false },
    yAxis: { type: 'value', min: 0, max: 100, axisLabel: { ...AXIS.axisLabel, formatter: '{value}%' }, ...AXIS },
    series: [
      {
        name: '失败率',
        type: 'line',
        smooth: true,
        showSymbol: points.length <= 14,
        connectNulls: true,
        data: points.map((item) => (item.failRate == null ? null : item.failRate)),
        areaStyle: { color: 'rgba(220, 38, 38, 0.08)' },
        lineStyle: { width: 2 },
      },
    ],
  }
}

export function failRankBarOption(items = []) {
  const rows = [...items].reverse()
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 8, right: 28, top: 8, bottom: 4, containLabel: true },
    xAxis: { type: 'value', minInterval: 1, ...AXIS },
    yAxis: {
      type: 'category',
      data: rows.map((item) => item.name || item.code || '—'),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#334155', fontSize: 12, width: 120, overflow: 'truncate' },
    },
    series: [
      {
        name: '失败次数',
        type: 'bar',
        barMaxWidth: 16,
        itemStyle: { borderRadius: [0, 6, 6, 0], color: CHART_COLORS.danger },
        data: rows.map((item) => item.failedCount || 0),
      },
    ],
  }
}

export function jobHealthOption(health = {}) {
  return sharePieOption(
    [
      { name: 'RUNNING', label: '运行中', count: health.running || 0 },
      { name: 'STOPPED', label: '已停止', count: health.stopped || 0 },
    ],
    { RUNNING: CHART_COLORS.success, STOPPED: CHART_COLORS.muted },
  )
}

export function rankBarOption(items = []) {
  const rows = [...items].reverse()
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 8, right: 28, top: 8, bottom: 4, containLabel: true },
    xAxis: { type: 'value', minInterval: 1, ...AXIS },
    yAxis: {
      type: 'category',
      data: rows.map((item) => item.name || item.code || '—'),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#334155', fontSize: 12, width: 120, overflow: 'truncate' },
    },
    series: [
      {
        type: 'bar',
        barMaxWidth: 16,
        itemStyle: { borderRadius: [0, 6, 6, 0], color: CHART_COLORS.primary },
        data: rows.map((item) => item.total || 0),
      },
    ],
  }
}

export function statusMeta(kind, value) {
  if (kind === 'workflow') {
    return { type: workflowStatusType(value), label: workflowStatusLabel(value) }
  }
  if (kind === 'trigger') {
    return { type: 'info', label: triggerLabel(value) }
  }
  if (kind === 'job') {
    return { type: value === 1 || value === 'running' ? 'success' : 'info', label: value === 1 || value === 'running' ? '运行中' : '已停止' }
  }
  if (kind === 'enable') {
    return { type: value === 1 ? 'success' : 'info', label: value === 1 ? '启用' : '停用' }
  }
  return { type: execStatusType(value), label: execStatusLabel(value) }
}
