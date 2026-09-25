import { parseJson } from './schema.js'

export const INPUT_SOURCE_NODE = '__input__'

const META_KEYS = new Set([
  'componentId', 'componentCode', 'componentName', 'httpMethod',
  'urlTemplate', 'urlPath', 'timeoutMs', 'retryTimes', 'retryIntervalMs', 'requiredParams',
  'provider', 'category', 'sqlPreview', 'customFields',
])

const UPSTREAM_PRESETS = ['errcode', 'errmsg', 'chatid', 'userid', 'id', 'msgid', 'access_token', 'data']

const KEY_PATTERN = /^[a-zA-Z_][a-zA-Z0-9_]*$/

export function pathToKey(path) {
  return String(path || '').replace(/^\$\.?/, '').trim()
}

export function keyToPath(key) {
  const name = String(key || '').trim()
  return name ? `$.${name}` : '$.'
}

export function isValidParamKey(key) {
  return KEY_PATTERN.test(String(key || '').trim())
}

export function isMetaKey(key) {
  return META_KEYS.has(key)
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

export function customFieldsFromData(nodeData) {
  const list = Array.isArray(nodeData?.customFields) ? nodeData.customFields : []
  return list
    .filter((item) => item?.key && !META_KEYS.has(item.key))
    .map((item) => ({
      key: String(item.key).trim(),
      type: item.type || 'string',
      required: Boolean(item.required),
      description: item.description || item.key,
      custom: true,
    }))
}

export function mergeNodeFields(schemaFieldsList, nodeData) {
  const schema = schemaFieldsList || []
  const seen = new Set(schema.map((item) => item.key))
  const extras = customFieldsFromData(nodeData).filter((item) => !seen.has(item.key))
  return [...schema, ...extras]
}

export function inferBindingsForNode(nodeId, fields, nodeData, mappings, inputKeys) {
  const inputSet = new Set(inputKeys || [])
  return (fields || []).map((field) => {
    const key = field.key
    const mapping = (mappings || []).find(
      (item) => item.toNode === nodeId && pathToKey(item.toPath) === key,
    )
    if (mapping) {
      if (mapping.fromNode === INPUT_SOURCE_NODE) {
        return {
          key,
          mode: 'runtime',
          inputKey: pathToKey(mapping.fromPath) || key,
          value: '',
        }
      }
      return {
        key,
        mode: 'upstream',
        fromNode: mapping.fromNode,
        fromField: pathToKey(mapping.fromPath),
        fromSource: mapping.fromSource === 'request' ? 'request' : 'output',
        value: '',
      }
    }
    if (inputSet.has(key)) {
      return { key, mode: 'runtime', inputKey: key, value: '' }
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
      if (item.mode === 'upstream' && item.fromNode && item.fromField) {
        mappings.push({
          fromNode: item.fromNode,
          fromPath: keyToPath(item.fromField),
          toNode: nodeId,
          toPath: keyToPath(item.key),
          fromSource: item.fromSource === 'request' ? 'request' : 'output',
        })
        continue
      }
      if (item.mode === 'runtime' && item.key) {
        const inputKey = (item.inputKey || item.key).trim()
        if (inputKey && inputKey !== item.key) {
          mappings.push({
            fromNode: INPUT_SOURCE_NODE,
            fromPath: keyToPath(inputKey),
            toNode: nodeId,
            toPath: keyToPath(item.key),
          })
        }
      }
    }
  }
  return mappings
}

export function bindingsToInputFields(bindingsByNode, fieldMetaMap) {
  const seen = new Set()
  const rows = []
  for (const bindings of Object.values(bindingsByNode || {})) {
    for (const item of bindings || []) {
      if (item.mode !== 'runtime' || !item.key) continue
      const inputKey = (item.inputKey || item.key).trim()
      if (!inputKey || seen.has(inputKey)) continue
      seen.add(inputKey)
      const meta = fieldMetaMap?.[item.key] || fieldMetaMap?.[inputKey]
      rows.push({
        key: inputKey,
        type: meta?.type || 'string',
        required: Boolean(meta?.required),
        description: meta?.description || '',
      })
    }
  }
  return rows
}

/** 入参面板为准，并集补齐各步 runtime 引用到的 key */
export function mergeInputFields(panelFields, bindingFields) {
  const map = new Map()
  for (const field of panelFields || []) {
    const key = String(field?.key || '').trim()
    if (!key) continue
    map.set(key, {
      key,
      type: field.type || 'string',
      required: Boolean(field.required),
      description: field.description || '',
    })
  }
  for (const field of bindingFields || []) {
    const key = String(field?.key || '').trim()
    if (!key) continue
    if (map.has(key)) {
      const cur = map.get(key)
      map.set(key, {
        key,
        type: cur.type || field.type || 'string',
        required: cur.required || Boolean(field.required),
        description: cur.description || field.description || '',
      })
    } else {
      map.set(key, {
        key,
        type: field.type || 'string',
        required: Boolean(field.required),
        description: field.description || '',
      })
    }
  }
  return [...map.values()]
}

export function renameInputKeyInBindings(bindingsByNode, oldKey, newKey) {
  if (!oldKey || !newKey || oldKey === newKey) return bindingsByNode
  const next = {}
  for (const [nodeId, bindings] of Object.entries(bindingsByNode || {})) {
    next[nodeId] = (bindings || []).map((item) => {
      if (item.mode !== 'runtime') return item
      const inputKey = item.inputKey || item.key
      if (inputKey !== oldKey) return item
      return { ...item, inputKey: newKey }
    })
  }
  return next
}

export function applyBindingsToNodeData(nodeData, bindings) {
  const next = { ...(nodeData || {}) }
  const customFields = Array.isArray(next.customFields) ? next.customFields : []
  for (const key of Object.keys(next)) {
    if (!META_KEYS.has(key)) delete next[key]
  }
  next.customFields = customFields
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
      if (item.mode !== 'runtime' || !item.key) continue
      const inputKey = (item.inputKey || item.key).trim()
      if (!inputKey || seen.has(inputKey)) continue
      seen.add(inputKey)
      rows.push({ key: inputKey })
    }
  }
  return rows
}

export function defaultBinding(field, upstreamNodes) {
  if (!field) return { key: '', mode: 'fixed', value: '' }
  const binding = { key: field.key, mode: 'fixed', value: '' }
  if (!upstreamNodes?.length) {
    if (field.required) {
      binding.mode = 'runtime'
      binding.inputKey = field.key
    }
    return binding
  }
  const matchedUpstream = (upstreamNodes || []).find((node) =>
    (node.fields || []).includes(field.key),
  )
  if (matchedUpstream) {
    return {
      key: field.key,
      mode: 'upstream',
      fromNode: matchedUpstream.id,
      fromField: field.key,
      fromSource: 'output',
      value: '',
    }
  }
  if (field.required) {
    binding.mode = 'runtime'
    binding.inputKey = field.key
  }
  return binding
}

export function bindingSummary(binding, field, ctx = {}) {
  if (!binding) return '未配置'
  if (binding.mode === 'fixed') {
    const value = String(binding.value ?? '').trim()
    if (!value) return '未填写'
    const short = value.length > 24 ? `${value.slice(0, 24)}…` : value
    return `固定值：${short}`
  }
  if (binding.mode === 'upstream') {
    const stepLabel = ctx.nodeNames?.[binding.fromNode]
      || ctx.upstreamName
      || '上游'
    const stepIndex = ctx.nodeIndexes?.[binding.fromNode]
    const stepPrefix = stepIndex != null ? `第${stepIndex}步` : stepLabel
    const side = binding.fromSource === 'request' ? '请求' : '响应'
    const key = binding.fromField || field?.key || ''
    return `来自${stepPrefix}·${side} ${key}`
  }
  if (binding.mode === 'runtime') {
    const inputKey = binding.inputKey || field?.key || binding.key
    const label = ctx.inputLabels?.[inputKey]
    if (label && label !== inputKey) return `来自入参「${label}」`
    return `来自入参 ${inputKey}`
  }
  return '未配置'
}

/** 规范化出参配置 */
export function normalizeOutputSchema(raw) {
  if (!raw || typeof raw !== 'object') {
    return { mode: 'last', fields: [] }
  }
  const mode = raw.mode === 'fields' ? 'fields' : 'last'
  const fields = Array.isArray(raw.fields)
    ? raw.fields
      .filter((item) => item?.key && item?.fromNode && item?.fromPath)
      .map((item) => ({
        key: String(item.key).trim(),
        fromNode: String(item.fromNode).trim(),
        fromPath: String(item.fromPath).trim().startsWith('$')
          ? String(item.fromPath).trim()
          : keyToPath(item.fromPath),
        description: item.description || '',
      }))
    : []
  return { mode, fields }
}

export function outputSchemaHint(schema) {
  const normalized = normalizeOutputSchema(schema)
  if (normalized.mode === 'fields' && normalized.fields.length) {
    return `已配置 ${normalized.fields.length} 个出参字段`
  }
  return '对外输出：最后一步完整结果'
}

export function stepConfigSummary(bindings, fields, ctx = {}) {
  const required = (fields || []).filter((item) => item.required)
  if (!required.length) return []
  return required.map((field) => {
    const binding = (bindings || []).find((item) => item.key === field.key)
    return {
      key: field.key,
      label: fieldLabel(field),
      text: bindingSummary(binding, field, ctx),
      done: isFieldConfigured(binding, field),
    }
  })
}

export function isFieldConfigured(binding, field) {
  if (!field?.required) return true
  if (!binding) return false
  if (binding.mode === 'fixed') return String(binding.value ?? '').trim().length > 0
  if (binding.mode === 'upstream') return Boolean(binding.fromNode && binding.fromField)
  if (binding.mode === 'runtime') return Boolean((binding.inputKey || binding.key || '').trim())
  return false
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
  for (const fields of Object.values(fieldsByNode || {})) {
    for (const field of fields || []) {
      map[field.key] = field
    }
  }
  return map
}
