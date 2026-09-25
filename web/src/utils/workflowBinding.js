import { parseJson } from './schema.js'

const META_KEYS = new Set([
  'componentId', 'componentCode', 'componentName', 'httpMethod',
  'urlTemplate', 'urlPath', 'timeoutMs', 'retryTimes', 'retryIntervalMs', 'requiredParams',
  'provider', 'category', 'sqlPreview',
])

const UPSTREAM_PRESETS = ['errcode', 'errmsg', 'chatid', 'userid', 'id', 'msgid', 'access_token', 'data']

export function pathToKey(path) {
  return String(path || '').replace(/^\$\.?/, '').trim()
}

export function keyToPath(key) {
  const name = String(key || '').trim()
  return name ? `$.${name}` : '$.'
}

export function fieldLabel(field) {
  if (field?.description) return field.description
  return field?.key || ''
}

export function responseFieldOptions(component) {
  const schema = parseJson(component?.responseSchema, {})
  const properties = schema.properties && typeof schema.properties === 'object' ? schema.properties : {}
  const keys = Object.keys(properties)
  if (keys.length) return keys
  return UPSTREAM_PRESETS
}

export function inferBindingsForNode(nodeId, fields, nodeData, mappings, inputKeys) {
  const inputSet = new Set(inputKeys || [])
  return (fields || []).map((field) => {
    const key = field.key
    if (inputSet.has(key)) {
      return { key, mode: 'runtime', value: '' }
    }
    const mapping = (mappings || []).find(
      (item) => item.toNode === nodeId && pathToKey(item.toPath) === key,
    )
    if (mapping) {
      return {
        key,
        mode: 'upstream',
        fromNode: mapping.fromNode,
        fromField: pathToKey(mapping.fromPath),
        value: '',
      }
    }
    if (nodeData?.[key] !== undefined && nodeData[key] !== '') {
      return { key, mode: 'fixed', value: formatBindingValue(nodeData[key], field) }
    }
    return { key, mode: 'fixed', value: '' }
  })
}

function formatBindingValue(value, field) {
  if (value == null) return ''
  if (field?.type === 'object' && typeof value === 'object') {
    return JSON.stringify(value, null, 2)
  }
  if (field?.type === 'array' && Array.isArray(value)) {
    return value.join(',')
  }
  return String(value)
}

export function parseBindingValue(raw, field) {
  if (raw === '' || raw == null) return undefined
  if (field?.type === 'integer') return Number(raw)
  if (field?.type === 'number') return Number(raw)
  if (field?.type === 'boolean') return raw === true || raw === 'true'
  if (field?.type === 'array') {
    return String(raw).split(/[,|]/).map((item) => item.trim()).filter(Boolean)
  }
  if (field?.type === 'object') {
    return parseJson(raw, undefined)
  }
  return raw
}

export function bindingsToMappings(bindingsByNode) {
  const mappings = []
  for (const [nodeId, bindings] of Object.entries(bindingsByNode || {})) {
    for (const item of bindings || []) {
      if (item.mode !== 'upstream' || !item.fromNode || !item.fromField) continue
      mappings.push({
        fromNode: item.fromNode,
        fromPath: keyToPath(item.fromField),
        toNode: nodeId,
        toPath: keyToPath(item.key),
      })
    }
  }
  return mappings
}

export function bindingsToInputFields(bindingsByNode, fieldMetaMap) {
  const seen = new Set()
  const rows = []
  for (const bindings of Object.values(bindingsByNode || {})) {
    for (const item of bindings || []) {
      if (item.mode !== 'runtime' || !item.key || seen.has(item.key)) continue
      seen.add(item.key)
      const meta = fieldMetaMap?.[item.key]
      rows.push({
        key: item.key,
        type: meta?.type || 'string',
        required: Boolean(meta?.required),
        description: meta?.description || '',
      })
    }
  }
  return rows
}

export function applyBindingsToNodeData(nodeData, bindings) {
  const next = { ...(nodeData || {}) }
  for (const key of Object.keys(next)) {
    if (!META_KEYS.has(key)) delete next[key]
  }
  for (const item of bindings || []) {
    if (item.mode !== 'fixed') continue
    const meta = { type: 'string' }
    const value = parseBindingValue(item.value, meta)
    if (value === undefined) continue
    next[item.key] = value
  }
  return next
}

export function collectRuntimeBindings(bindingsByNode) {
  const rows = []
  const seen = new Set()
  for (const bindings of Object.values(bindingsByNode || {})) {
    for (const item of bindings || []) {
      if (item.mode !== 'runtime' || !item.key || seen.has(item.key)) continue
      seen.add(item.key)
      rows.push({ key: item.key })
    }
  }
  return rows
}

export function defaultBinding(field, upstreamNodes) {
  if (!field) return { key: '', mode: 'fixed', value: '' }
  const binding = { key: field.key, mode: 'fixed', value: '' }
  if (!upstreamNodes?.length) {
    if (field.required) binding.mode = 'runtime'
    return binding
  }
  const upstream = upstreamNodes[0]
  const upstreamFields = upstream?.fields || []
  const matched = upstreamFields.find((name) => name === field.key)
  if (matched) {
    return {
      key: field.key,
      mode: 'upstream',
      fromNode: upstream.id,
      fromField: matched,
      value: '',
    }
  }
  if (field.required) binding.mode = 'runtime'
  return binding
}

export function bindingSummary(binding, field, upstreamName) {
  if (!binding) return '未配置'
  if (binding.mode === 'fixed') {
    const value = String(binding.value ?? '').trim()
    if (!value) return '未填写'
    const short = value.length > 24 ? `${value.slice(0, 24)}…` : value
    return `固定值：${short}`
  }
  if (binding.mode === 'upstream') {
    const from = upstreamName || '上一步'
    const key = binding.fromField || field?.key || ''
    return `来自「${from}」的 ${key}`
  }
  if (binding.mode === 'runtime') return '调用时传入'
  return '未配置'
}

export function stepConfigSummary(bindings, fields, upstreamName) {
  const required = (fields || []).filter((item) => item.required)
  if (!required.length) return []
  return required.map((field) => {
    const binding = (bindings || []).find((item) => item.key === field.key)
    return {
      key: field.key,
      label: fieldLabel(field),
      text: bindingSummary(binding, field, upstreamName),
      done: isFieldConfigured(binding, field),
    }
  })
}

export function isFieldConfigured(binding, field) {
  if (!field?.required) return true
  if (!binding) return false
  if (binding.mode === 'fixed') return String(binding.value ?? '').trim().length > 0
  if (binding.mode === 'upstream') return Boolean(binding.fromNode && binding.fromField)
  return binding.mode === 'runtime'
}

export function isNodeConfigured(bindings, fields) {
  const required = (fields || []).filter((item) => item.required)
  if (!required.length) return true
  for (const field of required) {
    const binding = (bindings || []).find((item) => item.key === field.key)
    if (!isFieldConfigured(binding, field)) return false
  }
  return true
}

export function buildFieldMetaMap(bindingsByNode, fieldsByNode) {
  const map = {}
  for (const [nodeId, fields] of Object.entries(fieldsByNode || {})) {
    for (const field of fields || []) {
      map[field.key] = field
    }
  }
  return map
}
