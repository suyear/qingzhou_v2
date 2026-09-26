import { parseJson } from './schema.js'

export const INPUT_SOURCE_NODE = '__input__'

const META_KEYS = new Set([
  'componentId', 'componentCode', 'componentName', 'httpMethod',
  'urlTemplate', 'urlPath', 'timeoutMs', 'retryTimes', 'retryIntervalMs', 'requiredParams',
  'provider', 'category', 'sqlPreview', 'customFields',
  'stepInputs', 'stepOutputs',
])

export const DEFAULT_STEP_OUTPUT = { key: 'result', fromPath: '', description: '完整响应' }
export const MERGE_SOURCE_NODE = '__merge__'

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

/** 规范化本步入参声明；空则返回 []（调用方用组件字段兜底） */
export function normalizeStepInputs(raw) {
  if (!Array.isArray(raw)) return []
  const seen = new Set()
  const rows = []
  for (const item of raw) {
    const key = String(item?.key || '').trim()
    if (!key || !KEY_PATTERN.test(key) || seen.has(key)) continue
    seen.add(key)
    rows.push({
      key,
      type: item.type || 'string',
      required: Boolean(item.required),
      description: item.description || '',
    })
  }
  return rows
}

/** 规范化本步出参；空则默认 result←整包 */
export function normalizeStepOutputs(raw, options = {}) {
  const withDefault = options.withDefault !== false
  if (!Array.isArray(raw) || !raw.length) {
    return withDefault ? [{ ...DEFAULT_STEP_OUTPUT }] : []
  }
  const seen = new Set()
  const rows = []
  for (const item of raw) {
    const key = String(item?.key || '').trim()
    if (!key || !KEY_PATTERN.test(key) || seen.has(key)) continue
    seen.add(key)
    const rawPath = item.fromPath == null ? '' : String(item.fromPath).trim()
    let fromPath = ''
    if (rawPath && rawPath !== '$' && rawPath !== '*') {
      fromPath = rawPath.startsWith('$') ? rawPath : keyToPath(rawPath)
    }
    rows.push({
      key,
      fromPath,
      type: item.type || 'object',
      description: item.description || '',
    })
  }
  return rows.length ? rows : (withDefault ? [{ ...DEFAULT_STEP_OUTPUT }] : [])
}

/** 从组件字段推导 stepInputs（无持久化声明时） */
export function deriveStepInputsFromFields(fields) {
  return (fields || []).map((field) => ({
    key: field.key,
    type: field.type || 'string',
    required: Boolean(field.required),
    description: field.description || field.key,
  }))
}

/** 读取节点有效入参声明（持久化 ∪ 组件字段） */
export function resolveStepInputs(nodeData, schemaFieldsList) {
  const stored = normalizeStepInputs(nodeData?.stepInputs)
  if (stored.length) return stored
  return deriveStepInputsFromFields(mergeNodeFields(schemaFieldsList, nodeData))
}

/** 读取节点有效出参声明 */
export function resolveStepOutputs(nodeData) {
  return normalizeStepOutputs(nodeData?.stepOutputs)
}

/** 各步 stepInputs 全量并集（1B） */
export function unionStepInputs(stepsInputs, panelOverrides = []) {
  const map = new Map()
  for (const field of panelOverrides || []) {
    const key = String(field?.key || '').trim()
    if (!key) continue
    map.set(key, {
      key,
      type: field.type || 'string',
      required: Boolean(field.required),
      description: field.description || '',
    })
  }
  for (const list of stepsInputs || []) {
    for (const field of list || []) {
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
  }
  return [...map.values()]
}

/** 预览按步骤名合并的出参形状 */
export function previewMergeOutputShape(stepSources) {
  const shape = {}
  const used = new Set()
  for (const step of stepSources || []) {
    let name = step.name || step.id || 'step'
    let unique = name
    let i = 2
    while (used.has(unique)) unique = `${name}_${i++}`
    used.add(unique)
    const ports = step.outputPorts?.length
      ? step.outputPorts
      : [{ key: 'result', fromPath: '' }]
    const obj = {}
    for (const port of ports) {
      obj[port.key] = port.fromPath
        ? `← ${port.fromPath}`
        : '← 完整响应'
    }
    shape[unique] = obj
  }
  return shape
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

function leafName(path) {
  return String(path || '').split('.').pop().replace(/\[\d+\]/g, '')
}

function pathPool(node) {
  const names = [...(node?.responseFields || node?.fields || [])]
  for (const port of node?.outputPorts || []) {
    const path = String(port?.fromPath || '').replace(/^\$\.?/, '').trim()
    if (path && path !== '*' && path !== '$') names.push(path)
  }
  return [...new Set(names)]
}

function pickNamedField(names, lower) {
  const exact = names.find((name) => String(name).toLowerCase() === lower)
  if (exact) return exact
  const leaves = names.filter((name) => leafName(name).toLowerCase() === lower)
  return leaves.find((name) => String(name).startsWith('rows[0].')) || leaves[0] || null
}

/** 在上游节点字段中找同名（忽略大小写）或常见 id 别名。优先最近的前序步骤。 */
export function findUpstreamFieldMatch(upstreamNodes, fieldKey) {
  const key = String(fieldKey || '').trim()
  if (!key || !upstreamNodes?.length) return null
  const lower = key.toLowerCase()
  const ordered = [...upstreamNodes].reverse()
  for (const node of ordered) {
    const named = pickNamedField(pathPool(node), lower)
    if (named) return { nodeId: node.id, field: named, fromSource: 'output' }
    for (const port of node.outputPorts || []) {
      if (String(port?.key || '').toLowerCase() !== lower) continue
      const path = String(port.fromPath || '').replace(/^\$\.?/, '').trim()
      if (!path || path === '*' || path === '$') continue
      return { nodeId: node.id, field: path, fromSource: 'output' }
    }
    // userId / order_id → 这一步结果里的 id，先于「上一步填过的同名项」
    if (/Id$/i.test(key) || /_id$/i.test(key)) {
      const pool = pathPool(node)
      for (const candidate of ['id', 'rows[0].id']) {
        if (pool.includes(candidate)) {
          return { nodeId: node.id, field: candidate, fromSource: 'output' }
        }
      }
    }
    for (const name of node.requestFields || []) {
      if (String(name).toLowerCase() === lower) {
        return { nodeId: node.id, field: name, fromSource: 'request' }
      }
    }
  }
  return null
}

/** 给人看的字段名，避免露出 $.path */
export function plainFieldName(name) {
  const raw = String(name || '').trim()
  if (!raw || raw === '*' || raw === '__whole__' || raw === '$') return '整份结果'
  if (raw === 'rows') return '全部数据行'
  if (raw === 'rowCount') return '一共多少行'
  if (raw === 'truncated') return '是否被截断'
  const rowCol = raw.match(/^rows\[0\]\.(.+)$/)
  if (rowCol) return `第一行的「${rowCol[1]}」`
  return `「${raw}」`
}

/** 前序步骤可交给本步的结果项（不含“上一步填过的内容”） */
export function handoffOptionsForStep(step) {
  const options = [{
    fromField: '__whole__',
    fromSource: 'output',
    label: '整份结果',
    hint: '后面的步骤拿到这一步的全部内容',
  }]
  const seen = new Set(['__whole__'])
  const push = (fromField, label, hint) => {
    const field = String(fromField || '').trim()
    if (!field || seen.has(field)) return
    seen.add(field)
    options.push({
      fromField: field,
      fromSource: 'output',
      label,
      hint: hint || '',
    })
  }
  for (const port of step?.outputPorts || []) {
    const path = String(port?.fromPath || '').replace(/^\$\.?/, '').trim()
    if (!path || path === '*' || path === '$') continue
    const title = port.description && port.description !== port.key
      ? port.description
      : (port.key || plainFieldName(path))
    push(path, `${title} · ${plainFieldName(path)}`, port.key ? `交给后面时叫 ${port.key}` : '')
  }
  for (const name of step?.responseFields || []) {
    push(name, plainFieldName(name), '从这一步的结果里取')
  }
  return options
}

/** 上一步已经填进去的内容，收在「更多」里 */
export function filledOptionsForStep(step) {
  const options = [{
    fromField: '__whole__',
    fromSource: 'request',
    label: '这一步填过的全部内容',
    hint: '',
  }]
  for (const name of step?.requestFields || []) {
    options.push({
      fromField: name,
      fromSource: 'request',
      label: plainFieldName(name),
      hint: '',
    })
  }
  return options
}

/** 给非技术用户的一条建议：最近前序步骤里同名的结果，不建议「请求」侧 */
export function suggestHandoff(fieldKey, upstreamSources) {
  const matched = findUpstreamFieldMatch(upstreamSources, fieldKey)
  if (!matched || matched.fromSource !== 'output') return null
  const index = (upstreamSources || []).findIndex((item) => item.id === matched.nodeId)
  const step = index >= 0 ? upstreamSources[index] : null
  return {
    fromNode: matched.nodeId,
    fromField: matched.field,
    fromSource: 'output',
    stepName: step?.name || '',
    stepIndex: index >= 0 ? index + 1 : null,
    label: plainFieldName(matched.field),
  }
}

/** 汇总本步对上游的依赖，供时间线展示 */
export function collectUpstreamDeps(bindings, ctx = {}) {
  const seen = new Set()
  const deps = []
  for (const binding of bindings || []) {
    if (binding?.mode !== 'upstream' || !binding.fromNode) continue
    const side = binding.fromSource === 'request' ? '填写项' : '结果'
    const depKey = `${binding.fromNode}:${side}`
    if (seen.has(depKey)) continue
    seen.add(depKey)
    const fromIndex = ctx.nodeIndexes?.[binding.fromNode]
    const stepPrefix = fromIndex != null ? `第 ${fromIndex} 步` : '前面步骤'
    deps.push({
      fromNode: binding.fromNode,
      fromIndex: fromIndex || null,
      fromSource: binding.fromSource === 'request' ? 'request' : 'output',
      label: `接到${stepPrefix}的${side}`,
    })
  }
  return deps
}

export function bindingSummary(binding, field, ctx = {}) {
  if (!binding) return '还没设置'
  if (binding.mode === 'fixed') {
    const value = String(binding.value ?? '').trim()
    if (!value) return '还没填写'
    const short = value.length > 24 ? `${value.slice(0, 24)}…` : value
    return `每次都用「${short}」`
  }
  if (binding.mode === 'upstream') {
    const stepLabel = ctx.nodeNames?.[binding.fromNode]
      || ctx.upstreamName
      || '前面的步骤'
    const stepIndex = ctx.nodeIndexes?.[binding.fromNode]
    const where = stepIndex != null ? `第 ${stepIndex} 步「${stepLabel}」` : `「${stepLabel}」`
    const side = binding.fromSource === 'request' ? '里填写过的' : '交出的'
    const piece = binding.fromField === '__whole__' || binding.fromField === '*'
      ? '整份结果'
      : plainFieldName(binding.fromField || field?.key || '')
    return `使用${where}${side}${piece}`
  }
  if (binding.mode === 'runtime') {
    const inputKey = binding.inputKey || field?.key || binding.key
    const label = ctx.inputLabels?.[inputKey]
    if (label && label !== inputKey) return `调用时由外面传入「${label}」`
    return `调用时由外面传入「${inputKey}」`
  }
  return '还没设置'
}

/** 规范化出参配置。forPersist=true 时，空 fields 的投影落库为 merge */
export function normalizeOutputSchema(raw, options = {}) {
  const forPersist = !!options.forPersist
  const preferMerge = !!options.preferMerge
  if (!raw || typeof raw !== 'object') {
    return { mode: preferMerge || forPersist ? 'merge' : 'last', fields: [], reshapeFrom: 'merge' }
  }
  let mode = 'last'
  if (raw.mode === 'fields') mode = 'fields'
  else if (raw.mode === 'firstRow' || raw.mode === 'first_row') mode = 'firstRow'
  else if (raw.mode === 'merge') mode = 'merge'
  else if (preferMerge && !raw.mode) mode = 'merge'
  const fields = Array.isArray(raw.fields)
    ? raw.fields
      .filter((item) => item?.key && item?.fromNode)
      .map((item) => {
        const rawPath = item.fromPath == null ? '' : String(item.fromPath).trim()
        let fromPath = ''
        if (rawPath && rawPath !== '$' && rawPath !== '*') {
          fromPath = rawPath.startsWith('$') ? rawPath : keyToPath(rawPath)
        }
        const fromSource = normalizeFromSource(item.fromSource)
        return {
          key: String(item.key).trim(),
          fromNode: String(item.fromNode).trim(),
          fromPath,
          fromSource,
          description: item.description || '',
        }
      })
    : []
  const reshapeFrom = raw.reshapeFrom === 'last' ? 'last' : 'merge'
  // 仅持久化时把空投影收成 merge（新产品默认）或 last（兼容）
  if (forPersist && mode === 'fields' && !fields.length) {
    mode = preferMerge ? 'merge' : 'last'
  }
  return { mode, fields, reshapeFrom }
}

export function normalizeFromSource(raw) {
  const value = String(raw || '').trim().toLowerCase()
  if (value === 'request') return 'request'
  if (value === 'input' || value === '__input__') return 'input'
  if (value === 'merge' || value === '__merge__') return 'merge'
  return 'output'
}

/** 从 outputSchema JSON 中抽出试跑学到的响应字段（与 mode/fields 同级挂载） */
export function extractLearnedSchemas(raw) {
  if (!raw || typeof raw !== 'object') return {}
  const source = raw.learnedSchemas
  if (!source || typeof source !== 'object' || Array.isArray(source)) return {}
  const result = {}
  for (const [nodeId, keys] of Object.entries(source)) {
    if (!nodeId || !Array.isArray(keys)) continue
    const cleaned = keys.map((item) => String(item || '').trim()).filter(Boolean)
    if (cleaned.length) result[nodeId] = [...new Set(cleaned)]
  }
  return result
}

/** 把 learnedSchemas 并入待持久化的 outputSchema */
export function attachLearnedSchemas(schema, learned) {
  const base = schema && typeof schema === 'object' ? { ...schema } : { mode: 'merge', fields: [], reshapeFrom: 'merge' }
  const cleaned = extractLearnedSchemas({ learnedSchemas: learned })
  if (Object.keys(cleaned).length) {
    base.learnedSchemas = cleaned
  } else {
    delete base.learnedSchemas
  }
  return base
}

export function outputSchemaHint(schema) {
  const normalized = normalizeOutputSchema(schema)
  if (schema?.mode === 'fields' && !(schema.fields || []).length) {
    return '还没挑字段，调用方会拿到每一步的结果'
  }
  if (normalized.mode === 'fields' && normalized.fields.length) {
    return `调用方只拿到挑出的 ${normalized.fields.length} 项`
  }
  if (normalized.mode === 'firstRow') {
    return '调用方只拿到查询结果的第一行'
  }
  if (normalized.mode === 'merge') {
    return '调用方按步骤名拿到每一步交出的内容'
  }
  return '调用方只拿到最后一步的完整结果'
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
