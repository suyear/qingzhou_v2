import { parseJson } from './schema.js'

export const INPUT_SOURCE_NODE = '__input__'

const META_KEYS = new Set([
  'componentId', 'componentCode', 'componentName', 'httpMethod',
  'urlTemplate', 'urlPath', 'timeoutMs', 'retryTimes', 'retryIntervalMs', 'requiredParams',
  'provider', 'category', 'sqlPreview', 'customFields',
])

const UPSTREAM_PRESETS = [] // 保留占位；无 responseSchema 时不再塞假字段

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
  return Object.keys(properties)
}

/** 从实际响应体抽顶层字段，供出参 / 取数据补全选项 */
export function topLevelKeysFromPayload(payload) {
  if (payload == null) return []
  let obj = payload
  if (typeof payload === 'string') {
    obj = parseJson(payload, null)
  }
  if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return []
  const keys = Object.keys(obj).filter((key) => key && !String(key).startsWith('_'))
  // 历史 DB 日志用 preview；业务输出是 rows —— 学习时统一成 rows
  const normalized = keys.map((key) => (key === 'preview' ? 'rows' : key))
  return [...new Set(normalized)].filter((key) => key !== 'preview')
}

/** 从 rows[0] / preview[0] 展开列路径，便于出参取第一行某列 */
export function rowColumnPathsFromPayload(payload) {
  if (payload == null) return []
  let obj = payload
  if (typeof payload === 'string') {
    obj = parseJson(payload, null)
  }
  if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return []
  const rows = Array.isArray(obj.rows) ? obj.rows : (Array.isArray(obj.preview) ? obj.preview : null)
  if (!rows?.length) return []
  const first = rows[0]
  if (!first || typeof first !== 'object' || Array.isArray(first)) return []
  return Object.keys(first)
    .filter((key) => key && !String(key).startsWith('_'))
    .slice(0, 40)
    .map((key) => `rows[0].${key}`)
}

/** 展示用：历史 preview 升格为 rows，与开放 API 一致 */
export function normalizePayloadForDisplay(payload) {
  let obj = payload
  if (typeof payload === 'string') {
    obj = parseJson(payload, null)
    if (obj == null) return payload
  }
  if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return obj
  if (!('preview' in obj)) return obj
  const next = { ...obj }
  if (!('rows' in next)) next.rows = next.preview
  delete next.preview
  return next
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
        fromField: (() => {
          const p = pathToKey(mapping.fromPath)
          return p ? p : '__whole__'
        })(),
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
        const whole = item.fromField === '__whole__' || item.fromField === '*'
        mappings.push({
          fromNode: item.fromNode,
          fromPath: whole ? '' : keyToPath(item.fromField),
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
  const matched = findUpstreamFieldMatch(upstreamNodes, field.key)
  if (matched) {
    return {
      key: field.key,
      mode: 'upstream',
      fromNode: matched.nodeId,
      fromField: matched.field,
      fromSource: matched.fromSource,
      value: '',
    }
  }
  if (field.required) {
    binding.mode = 'runtime'
    binding.inputKey = field.key
  }
  return binding
}

/** 在上游节点字段中找同名（忽略大小写）或常见 id 别名 */
export function findUpstreamFieldMatch(upstreamNodes, fieldKey) {
  const key = String(fieldKey || '').trim()
  if (!key || !upstreamNodes?.length) return null
  const lower = key.toLowerCase()
  for (const node of upstreamNodes) {
    const responseFields = node.responseFields || node.fields || []
    const requestFields = node.requestFields || []
    for (const name of responseFields) {
      if (String(name).toLowerCase() === lower) {
        return { nodeId: node.id, field: name, fromSource: 'output' }
      }
    }
    for (const name of requestFields) {
      if (String(name).toLowerCase() === lower) {
        return { nodeId: node.id, field: name, fromSource: 'request' }
      }
    }
  }
  // userId / order_id → 上游 id / rows[0].id
  if (/Id$/i.test(key) || /_id$/i.test(key)) {
    const last = upstreamNodes[upstreamNodes.length - 1]
    const pool = [...(last.responseFields || last.fields || [])]
    for (const candidate of ['id', 'rows[0].id']) {
      if (pool.includes(candidate)) {
        return { nodeId: last.id, field: candidate, fromSource: 'output' }
      }
    }
  }
  return null
}

/** 汇总本步对上游的依赖，供时间线展示 */
export function collectUpstreamDeps(bindings, ctx = {}) {
  const seen = new Set()
  const deps = []
  for (const binding of bindings || []) {
    if (binding?.mode !== 'upstream' || !binding.fromNode) continue
    if (seen.has(binding.fromNode)) continue
    seen.add(binding.fromNode)
    const fromIndex = ctx.nodeIndexes?.[binding.fromNode]
    deps.push({
      fromNode: binding.fromNode,
      fromIndex: fromIndex || null,
      label: fromIndex != null ? `← 第${fromIndex}步` : '← 上游',
    })
  }
  return deps
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
    const key = binding.fromField === '__whole__' || binding.fromField === '*'
      ? '完整结果'
      : (binding.fromField || field?.key || '')
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

/** 规范化出参配置。forPersist=true 时，空 fields 的投影落库为 last */
export function normalizeOutputSchema(raw, options = {}) {
  const forPersist = !!options.forPersist
  if (!raw || typeof raw !== 'object') {
    return { mode: 'last', fields: [] }
  }
  let mode = 'last'
  if (raw.mode === 'fields') mode = 'fields'
  else if (raw.mode === 'firstRow' || raw.mode === 'first_row') mode = 'firstRow'
  const fields = Array.isArray(raw.fields)
    ? raw.fields
      .filter((item) => item?.key && item?.fromNode)
      .map((item) => {
        const rawPath = item.fromPath == null ? '' : String(item.fromPath).trim()
        let fromPath = ''
        if (rawPath && rawPath !== '$' && rawPath !== '*') {
          fromPath = rawPath.startsWith('$') ? rawPath : keyToPath(rawPath)
        }
        return {
          key: String(item.key).trim(),
          fromNode: String(item.fromNode).trim(),
          fromPath,
          description: item.description || '',
        }
      })
    : []
  // 仅持久化时把空投影收成 last；编辑态保留 fields 以便继续加字段
  if (forPersist && mode === 'fields' && !fields.length) {
    mode = 'last'
  }
  return { mode, fields }
}

export function outputSchemaHint(schema) {
  const normalized = normalizeOutputSchema(schema)
  if (schema?.mode === 'fields' && !(schema.fields || []).length) {
    return '字段投影尚未配置，对外将按「最后一步完整结果」返回'
  }
  if (normalized.mode === 'fields' && normalized.fields.length) {
    return `对外出参：已投影 ${normalized.fields.length} 个字段（与开放 API 一致）`
  }
  if (normalized.mode === 'firstRow') {
    return '对外出参：查询结果首行对象（与开放 API 一致）'
  }
  return '对外出参：最后一步完整结果（与开放 API 一致）'
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
