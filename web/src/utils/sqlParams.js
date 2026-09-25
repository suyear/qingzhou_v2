const IDENT_START = /[A-Za-z_]/
const IDENT_PART = /[A-Za-z0-9_]/

export function isDatabaseComponent(component) {
  if (!component) return false
  const provider = String(component.provider || '').toUpperCase()
  const category = String(component.category || '').toUpperCase()
  const method = String(component.httpMethod || '').toUpperCase()
  return provider === 'DATABASE' || category === 'DATABASE' || method === 'QUERY' || method === 'UPDATE'
}

export function parseDatabaseExtra(component) {
  const extra = component?.extraConfig
  if (!extra) return { datasourceId: null, accessMode: 'READ', maxRows: 200 }
  let parsed = extra
  if (typeof extra === 'string') {
    try {
      parsed = JSON.parse(extra)
    } catch {
      parsed = {}
    }
  }
  return {
    datasourceId: parsed.datasourceId ?? parsed.datasource_id ?? null,
    accessMode: String(parsed.accessMode || 'READ').toUpperCase() === 'WRITE' ? 'WRITE' : 'READ',
    maxRows: Number(parsed.maxRows) > 0 ? Number(parsed.maxRows) : 200,
  }
}

export function extractNamedParams(sql) {
  const text = String(sql || '')
  const names = []
  let state = 'normal'
  for (let i = 0; i < text.length; i++) {
    const c = text[i]
    const next = text[i + 1] || ''
    if (state === 'line') {
      if (c === '\n') state = 'normal'
      continue
    }
    if (state === 'block') {
      if (c === '*' && next === '/') {
        i++
        state = 'normal'
      }
      continue
    }
    if (state === 'sq') {
      if (c === "'" && next === "'") {
        i++
        continue
      }
      if (c === "'") state = 'normal'
      continue
    }
    if (state === 'dq') {
      if (c === '"' && next === '"') {
        i++
        continue
      }
      if (c === '"') state = 'normal'
      continue
    }
    if (state === 'bt') {
      if (c === '`') state = 'normal'
      continue
    }
    if (c === '-' && next === '-') {
      state = 'line'
      i++
      continue
    }
    if (c === '/' && next === '*') {
      state = 'block'
      i++
      continue
    }
    if (c === '#') {
      state = 'line'
      continue
    }
    if (c === "'") {
      state = 'sq'
      continue
    }
    if (c === '"') {
      state = 'dq'
      continue
    }
    if (c === '`') {
      state = 'bt'
      continue
    }
    if (c === ':' && IDENT_START.test(next) && text[i - 1] !== ':' && next !== '=') {
      let end = i + 1
      while (end < text.length && IDENT_PART.test(text[end])) end++
      names.push(text.slice(i + 1, end))
      i = end - 1
    }
  }
  return [...new Set(names)]
}

export function compactSql(sql, maxLen = 56) {
  const text = String(sql || '').replace(/\s+/g, ' ').trim()
  if (!text) return ''
  return text.length > maxLen ? `${text.slice(0, maxLen - 1)}…` : text
}

export function displayPath(component) {
  if (isDatabaseComponent(component)) {
    return compactSql(component?.urlTemplate || component?.sqlPreview || '')
  }
  return null
}

export function defaultDbResponseSchema() {
  return {
    type: 'object',
    properties: {
      rowCount: { type: 'integer', description: '查询返回行数' },
      affectedRows: { type: 'integer', description: '更新影响行数' },
      columns: { type: 'array', description: '查询列名' },
      rows: { type: 'array', description: '查询行集' },
      truncated: { type: 'boolean', description: '行集是否被截断' },
    },
  }
}

export function paramsToRows(names, existing = []) {
  const prev = new Map((existing || []).map((row) => [row.key, row]))
  return (names || []).map((key) => {
    const keep = prev.get(key)
    return {
      key,
      type: keep?.type || 'string',
      required: keep?.required !== false,
      description: keep?.description || `SQL 参数 :${key}`,
      location: 'body',
    }
  })
}

export function isSqlLog(item) {
  const method = String(item?.requestMethod || '').toUpperCase()
  return method === 'QUERY' || method === 'UPDATE'
}
