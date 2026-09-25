import { compactSql, isDatabaseComponent } from './sqlParams.js'

export function parseJson(value, fallback = {}) {
  if (value == null || value === '') {
    return fallback
  }
  if (typeof value === 'object') {
    return value
  }
  try {
    return JSON.parse(value)
  } catch {
    return fallback
  }
}

export function schemaFields(component) {
  const fields = []
  for (const field of ['querySchema', 'bodySchema']) {
    const schema = parseJson(component?.[field], {})
    const required = new Set(Array.isArray(schema.required) ? schema.required : [])
    const properties = schema.properties && typeof schema.properties === 'object' ? schema.properties : {}
    for (const [key, def] of Object.entries(properties)) {
      const spec = def && typeof def === 'object' ? def : {}
      fields.push({
        key,
        type: spec.type || 'string',
        enums: Array.isArray(spec.enum) ? spec.enum : [],
        description: spec.description || '',
        required: required.has(key),
      })
    }
  }
  return fields
}

export function schemaToFields(schema) {
  const parsed = parseJson(schema, {})
  const required = new Set(Array.isArray(parsed.required) ? parsed.required : [])
  const properties = parsed.properties && typeof parsed.properties === 'object' ? parsed.properties : {}
  return Object.entries(properties).map(([key, def]) => {
    const spec = def && typeof def === 'object' ? def : {}
    return {
      key,
      type: spec.type || 'string',
      required: required.has(key),
      description: spec.description || spec.title || '',
      enums: Array.isArray(spec.enum) ? spec.enum : [],
      default: spec.default,
    }
  })
}

export function fieldsToSchema(fields) {
  const properties = {}
  const required = []
  for (const field of fields || []) {
    const key = String(field.key || '').trim()
    if (!key) {
      continue
    }
    properties[key] = {
      type: field.type || 'string',
    }
    if (field.description) {
      properties[key].description = field.description
    }
    if (field.required) {
      required.push(key)
    }
  }
  return { type: 'object', properties, required }
}

export function componentParamRows(component) {
  const rows = []
  for (const location of ['query', 'body']) {
    const schema = parseJson(component?.[`${location}Schema`], {})
    const required = new Set(Array.isArray(schema.required) ? schema.required : [])
    const properties = schema.properties && typeof schema.properties === 'object' ? schema.properties : {}
    for (const [key, def] of Object.entries(properties)) {
      const spec = def && typeof def === 'object' ? def : {}
      rows.push({
        key,
        type: spec.type || 'string',
        required: required.has(key),
        description: spec.description || '',
        location,
      })
    }
  }
  return rows
}

export function needsAccessToken(component) {
  const url = String(component?.urlTemplate || '')
  const config = parseJson(component?.extraConfig, {})
  if (config.needAccessToken || config.auth?.type === 'wecom') {
    return true
  }
  const extra = typeof component?.extraConfig === 'string'
    ? component.extraConfig
    : JSON.stringify(component?.extraConfig || {})
  return url.includes('access_token') || extra.includes('needAccessToken')
}

export function requiredParams(component) {
  const keys = new Set()
  for (const field of ['bodySchema', 'querySchema']) {
    const schema = parseJson(component?.[field], {})
    const required = Array.isArray(schema.required) ? schema.required : []
    required.forEach((key) => keys.add(key))
  }
  return [...keys]
}

export function urlPath(urlTemplate, component) {
  if (isDatabaseComponent(component || { urlTemplate })) {
    return compactSql(component?.urlTemplate || urlTemplate)
  }
  if (!urlTemplate) {
    return ''
  }
  if (!/^https?:\/\//i.test(urlTemplate) && /^\s*(select|insert|update|delete|replace|with|show|describe|desc|explain)\b/i.test(urlTemplate)) {
    return compactSql(urlTemplate)
  }
  const noQuery = urlTemplate.split('?')[0]
  try {
    return new URL(noQuery.replace(/\$\{[^}]+\}/g, 'x')).pathname
  } catch {
    const scheme = noQuery.indexOf('://')
    if (scheme < 0) {
      return noQuery
    }
    const slash = noQuery.indexOf('/', scheme + 3)
    return slash >= 0 ? noQuery.slice(slash) : noQuery
  }
}

export function toNodeData(component) {
  return {
    componentId: component.id,
    componentCode: component.componentCode,
    componentName: component.componentName,
    httpMethod: component.httpMethod,
    urlTemplate: component.urlTemplate,
    urlPath: urlPath(component.urlTemplate, component),
    timeoutMs: component.timeoutMs,
    retryTimes: component.retryTimes,
    requiredParams: requiredParams(component),
    provider: component.provider,
    category: component.category,
    sqlPreview: isDatabaseComponent(component) ? compactSql(component.urlTemplate) : undefined,
  }
}

export const CATEGORY_LABEL = {
  MESSAGE: '消息',
  ORG: '组织架构',
  GROUP: '群聊',
  HTTP: '自定义 HTTP',
  DATABASE: '数据库脚本',
}

export const PROVIDER_LABEL = {
  WECOM: '企业微信',
  CUSTOM: '自定义',
  DATABASE: '数据库',
}

export function categoryLabel(value) {
  return CATEGORY_LABEL[value] || value || '—'
}

export function providerLabel(value) {
  return PROVIDER_LABEL[value] || value || '—'
}

export function paramStats(component) {
  const fields = schemaFields(component)
  const required = fields.filter((item) => item.required).length
  return { total: fields.length, required }
}

export function httpMethodTagType(method) {
  const value = String(method || '').toUpperCase()
  if (value === 'GET' || value === 'QUERY') return 'success'
  if (value === 'POST') return 'primary'
  if (value === 'PUT' || value === 'PATCH' || value === 'UPDATE') return 'warning'
  if (value === 'DELETE') return 'danger'
  return 'info'
}
