import { fieldLabel } from './workflowBinding.js'
import { parseJson, schemaToFields } from './schema.js'

export function emptyKvRow() {
  return { key: '', value: '' }
}

/** 将后端 JSON 字符串或对象规范为普通对象；无效/空则返回 null */
export function normalizeTriggerInput(raw) {
  const parsed = parseJson(raw, null)
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    return null
  }
  return Object.keys(parsed).length ? parsed : null
}

export function objectToKvRows(obj) {
  const entries = Object.entries(obj || {})
  if (!entries.length) {
    return [emptyKvRow()]
  }
  return entries.map(([key, value]) => ({
    key,
    value: formatKvValue(value),
  }))
}

export function kvRowsToObject(rows) {
  const result = {}
  for (const row of rows || []) {
    const key = String(row.key || '').trim()
    if (!key) continue
    const raw = row.value
    if (raw === '' || raw == null) continue
    result[key] = coerceScalar(raw)
  }
  return result
}

export function formatKvValue(value) {
  if (value == null) return ''
  if (typeof value === 'object') {
    return JSON.stringify(value)
  }
  return String(value)
}

export function coerceFieldValue(field, raw) {
  if (raw === '' || raw == null) {
    return undefined
  }
  const type = field?.type || 'string'
  if (type === 'boolean') {
    if (raw === true || raw === false) return raw
    const text = String(raw).toLowerCase()
    if (text === 'true' || text === '1' || text === '是') return true
    if (text === 'false' || text === '0' || text === '否') return false
    return undefined
  }
  if (type === 'integer') {
    const num = Number.parseInt(String(raw), 10)
    return Number.isNaN(num) ? undefined : num
  }
  if (type === 'number') {
    const num = Number(raw)
    return Number.isNaN(num) ? undefined : num
  }
  if (type === 'array') {
    if (Array.isArray(raw)) return raw
    return String(raw)
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean)
  }
  if (type === 'object') {
    if (typeof raw === 'object' && !Array.isArray(raw)) return raw
    const parsed = parseJson(String(raw), null)
    if (parsed == null || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return undefined
    }
    return parsed
  }
  return String(raw)
}

export function coerceScalar(raw) {
  const text = String(raw).trim()
  if (text === 'true') return true
  if (text === 'false') return false
  if (/^-?\d+$/.test(text)) return Number.parseInt(text, 10)
  if (/^-?\d+\.\d+$/.test(text)) return Number(text)
  if ((text.startsWith('{') && text.endsWith('}')) || (text.startsWith('[') && text.endsWith(']'))) {
    try {
      return JSON.parse(text)
    } catch {
      return text
    }
  }
  return text
}

export function formValuesToObject(fields, values) {
  const result = {}
  for (const field of fields || []) {
    const value = coerceFieldValue(field, values[field.key])
    if (value !== undefined) {
      result[field.key] = value
    }
  }
  return result
}

export function objectToFormValues(fields, obj) {
  const parsed = obj && typeof obj === 'object' ? obj : {}
  const values = {}
  for (const field of fields || []) {
    const raw = parsed[field.key]
    if (raw == null) {
      if (field.default != null) {
        values[field.key] = field.default
      } else if (field.type === 'boolean') {
        values[field.key] = false
      } else if (field.type === 'integer' || field.type === 'number') {
        values[field.key] = undefined
      } else {
        values[field.key] = ''
      }
      continue
    }
    if (field.type === 'boolean') {
      values[field.key] = raw
    } else if (field.type === 'array' && Array.isArray(raw)) {
      values[field.key] = raw.join(', ')
    } else if (field.type === 'object' && typeof raw === 'object') {
      values[field.key] = JSON.stringify(raw, null, 2)
    } else {
      values[field.key] = raw
    }
  }
  return values
}

export function buildTriggerPayload({ fields, formValues, kvRows, jsonText, advanced }) {
  if (advanced) {
    const text = String(jsonText || '').trim()
    if (!text) return undefined
    const parsed = parseJson(text, null)
    if (parsed == null || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return { error: 'JSON 格式不正确，需为对象，例如 {"name":"张三"}' }
    }
    if (!Object.keys(parsed).length) return undefined
    return { value: parsed }
  }
  if (fields?.length) {
    const value = formValuesToObject(fields, formValues)
    return Object.keys(value).length ? { value } : undefined
  }
  const value = kvRowsToObject(kvRows)
  return Object.keys(value).length ? { value } : undefined
}

export function validateTriggerPayload({ fields, formValues, kvRows, jsonText, advanced }) {
  const built = buildTriggerPayload({ fields, formValues, kvRows, jsonText, advanced })
  if (built?.error) {
    return built.error
  }
  const payload = built?.value || {}
  if (!fields?.length) {
    return ''
  }
  if (!advanced) {
    for (const field of fields) {
      if (field.type !== 'object') continue
      const raw = formValues?.[field.key]
      if (raw === '' || raw == null || typeof raw === 'object') continue
      const parsed = parseJson(String(raw), null)
      if (parsed == null || typeof parsed !== 'object' || Array.isArray(parsed)) {
        return `「${fieldLabel(field)}」需填写有效的 JSON 对象`
      }
    }
  }
  for (const field of fields) {
    if (!field.required) continue
    const value = advanced ? payload[field.key] : formValues?.[field.key]
    if (value === '' || value == null) {
      return `请填写「${fieldLabel(field)}」`
    }
  }
  return ''
}

export function formatTriggerPreview(value) {
  if (!value || typeof value !== 'object' || !Object.keys(value).length) {
    return '{\n  \n}'
  }
  return JSON.stringify(value, null, 2)
}

export function previewKeyCount(value) {
  if (!value || typeof value !== 'object') return 0
  return Object.keys(value).length
}

export function exampleValueForField(field) {
  if (field?.default != null) return field.default
  if (field?.enums?.length) return field.enums[0]
  const type = field?.type || 'string'
  if (type === 'integer') return 1
  if (type === 'number') return 1.5
  if (type === 'boolean') return true
  if (type === 'array') return ['example']
  if (type === 'object') return { id: 1 }
  const key = String(field?.key || '')
  if (/id$/i.test(key) || key.toLowerCase().includes('userid')) return '10001'
  return 'example'
}

export function examplePayloadFromSchema(schema) {
  const fields = schemaToFields(schema)
  const result = {}
  for (const field of fields) {
    result[field.key] = exampleValueForField(field)
  }
  return result
}

export function schemaFieldGuide(schema) {
  return schemaToFields(schema).map((field) => ({
    key: field.key,
    label: fieldLabel(field),
    required: Boolean(field.required),
    type: field.type || 'string',
    example: exampleValueForField(field),
  }))
}

function formatGuideExample(value) {
  if (value == null) return ''
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

export function annotateCurlWithFields(curl, fields) {
  const text = String(curl || '').trim()
  if (!text) return ''
  if (!fields?.length) return text
  const notes = fields.map((field) => {
    const flag = field.required ? '必填' : '可选'
    const label = field.label && field.label !== field.key ? ` ${field.label}` : ''
    const example = field.example != null ? ` · 例 ${formatGuideExample(field.example)}` : ''
    return `# ${field.key}${label} · ${flag} · ${field.type}${example}`
  })
  return `${notes.join('\n')}\n${text}`
}
