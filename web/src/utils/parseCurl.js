const AUTH_HEADER_NAMES = new Set(['authorization', 'x-api-key', 'api-key', 'x-auth-token', 'token'])
const SKIP_FLAGS = new Set([
  '-L', '--location', '-s', '-S', '--silent', '--compressed', '-k', '--insecure',
  '-i', '--include', '-v', '--verbose', '-o', '--output', '--path-as-is',
])

const EXAMPLE_CURL = `curl --location 'http://127.0.0.1:18080/api/health' \\
  --header 'Authorization: Bearer your-token-here'`

function normalizeCurlText(raw) {
  return String(raw || '')
    .replace(/\\\r?\n/g, ' ')
    .replace(/\r?\n/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

function tokenize(text) {
  const tokens = []
  let i = 0
  while (i < text.length) {
    while (i < text.length && /\s/.test(text[i])) {
      i += 1
    }
    if (i >= text.length) {
      break
    }
    const quote = text[i]
    if (quote === "'" || quote === '"') {
      i += 1
      let value = ''
      while (i < text.length && text[i] !== quote) {
        if (text[i] === '\\' && i + 1 < text.length) {
          i += 1
        }
        value += text[i]
        i += 1
      }
      if (text[i] === quote) {
        i += 1
      }
      tokens.push(value)
      continue
    }
    let value = ''
    while (i < text.length && !/\s/.test(text[i])) {
      value += text[i]
      i += 1
    }
    tokens.push(value)
  }
  return tokens
}

function isFlag(token) {
  return token.startsWith('-')
}

function isUrl(token) {
  return /^https?:\/\//i.test(token)
}

function readHeaderValue(tokens, index) {
  const first = tokens[index] || ''
  if (first.includes(':')) {
    return { value: first, nextIndex: index + 1 }
  }
  const parts = [first]
  let cursor = index + 1
  while (cursor < tokens.length) {
    const next = tokens[cursor]
    if (isFlag(next) || isUrl(next)) {
      break
    }
    parts.push(next)
    cursor += 1
  }
  return { value: parts.join(' '), nextIndex: cursor }
}

function splitHeader(line) {
  const idx = line.indexOf(':')
  if (idx <= 0) {
    return null
  }
  return {
    key: line.slice(0, idx).trim(),
    value: line.slice(idx + 1).trim(),
  }
}

function guessNameFromUrl(url) {
  try {
    const pathname = new URL(url).pathname
    const parts = pathname.split('/').filter(Boolean)
    const last = parts[parts.length - 1] || '接口'
    if (last === 'health') {
      return '健康检查'
    }
    return last.replace(/[-_]/g, ' ').slice(0, 32)
  } catch {
    return ''
  }
}

function guessParamsFromBody(body, method) {
  if (!body || method === 'GET' || method === 'DELETE') {
    return []
  }
  try {
    const json = JSON.parse(body)
    if (!json || typeof json !== 'object' || Array.isArray(json)) {
      return []
    }
    const location = method === 'GET' || method === 'DELETE' ? 'query' : 'body'
    return Object.keys(json).map((key) => ({
      key,
      type: 'string',
      required: false,
      description: '',
      location,
    }))
  } catch {
    return []
  }
}

function createEmptyAuthState() {
  return {
    authType: 'none',
    bearerToken: '',
    apiKeyName: 'X-API-Key',
    apiKeyValue: '',
    apiKeyIn: 'header',
    basicUsername: '',
    basicPassword: '',
    headers: [],
  }
}

function applyHeaders(headerLines) {
  const authState = createEmptyAuthState()

  for (const line of headerLines) {
    const header = splitHeader(line)
    if (!header) {
      continue
    }
    const lower = header.key.toLowerCase()

    if (lower === 'authorization') {
      const bearer = header.value.match(/^Bearer\s+(.+)$/i)
      if (bearer) {
        authState.authType = 'bearer'
        authState.bearerToken = bearer[1].trim()
        continue
      }
      const basic = header.value.match(/^Basic\s+(.+)$/i)
      if (basic) {
        try {
          const decoded = atob(basic[1].trim())
          const idx = decoded.indexOf(':')
          authState.authType = 'basic'
          authState.basicUsername = idx >= 0 ? decoded.slice(0, idx) : decoded
          authState.basicPassword = idx >= 0 ? decoded.slice(idx + 1) : ''
        } catch {
          authState.authType = 'basic'
        }
        continue
      }
    }

    if (['x-api-key', 'api-key', 'x-auth-token'].includes(lower)) {
      authState.authType = 'apiKey'
      authState.apiKeyName = header.key
      authState.apiKeyValue = header.value
      authState.apiKeyIn = 'header'
      continue
    }

    if (!AUTH_HEADER_NAMES.has(lower) && lower !== 'content-type' && lower !== 'accept') {
      authState.headers.push({ key: header.key, value: header.value, enabled: true })
    }
  }

  return authState
}

function buildResult({ method, url, headerLines, body }) {
  if (!url) {
    return { ok: false, message: '未能识别接口地址。请确认 curl 里包含 http:// 或 https:// 开头的网址。' }
  }

  const authState = applyHeaders(headerLines)
  const params = guessParamsFromBody(body, method)
  const componentName = guessNameFromUrl(url)

  return {
    ok: true,
    method,
    url,
    componentName,
    authState,
    params,
    paramMode: params.length ? 'custom' : 'none',
    summary: [
      method,
      url,
      authState.authType !== 'none' ? `鉴权：${authState.authType}` : null,
      params.length ? `识别 ${params.length} 个参数` : null,
    ].filter(Boolean).join(' · '),
  }
}

function parsePlainUrl(raw) {
  const url = normalizeCurlText(raw)
  if (!/^https?:\/\//i.test(url)) {
    return { ok: false, message: '请输入以 http:// 或 https:// 开头的完整地址，或粘贴 curl 命令' }
  }
  return buildResult({ method: 'GET', url, headerLines: [], body: '' })
}

function parseCurlCommand(text) {
  const tokens = tokenize(text)
  if (!tokens.length || tokens[0].toLowerCase() !== 'curl') {
    return { ok: false, message: '内容需以 curl 开头，或直接粘贴接口地址' }
  }

  let method = 'GET'
  let url = ''
  const headerLines = []
  let body = ''

  let index = 1
  while (index < tokens.length) {
    const token = tokens[index]

    if (token === '-X' || token === '--request') {
      method = String(tokens[index + 1] || 'GET').toUpperCase()
      index += 2
      continue
    }

    if (token === '-H' || token === '--header') {
      const header = readHeaderValue(tokens, index + 1)
      if (header.value) {
        headerLines.push(header.value)
      }
      index = header.nextIndex
      continue
    }

    if (token === '--url' || token === '-u') {
      url = tokens[index + 1] || url
      index += 2
      continue
    }

    if (token === '-d' || token === '--data' || token === '--data-raw' || token === '--data-binary' || token === '--data-urlencode') {
      body = tokens[index + 1] || body
      if (method === 'GET') {
        method = 'POST'
      }
      index += 2
      continue
    }

    if (SKIP_FLAGS.has(token)) {
      if (token === '-o' || token === '--output') {
        index += 2
      } else {
        index += 1
      }
      continue
    }

    if (isUrl(token)) {
      url = token
      index += 1
      continue
    }

    if (isFlag(token)) {
      if (index + 1 < tokens.length && !isFlag(tokens[index + 1]) && !isUrl(tokens[index + 1])) {
        index += 2
      } else {
        index += 1
      }
      continue
    }

    index += 1
  }

  return buildResult({ method, url, headerLines, body })
}

export function parseCurl(raw) {
  const text = normalizeCurlText(raw)
  if (!text) {
    return { ok: false, message: '请粘贴 curl 命令或接口地址' }
  }
  if (!/^curl\b/i.test(text)) {
    return parsePlainUrl(text)
  }
  return parseCurlCommand(text)
}

export function getExampleCurl() {
  return EXAMPLE_CURL
}

export function applyCurlImport(parsed, targets) {
  if (!parsed?.ok) {
    return parsed
  }

  const { form, authState, paramMode } = targets

  if (form) {
    form.httpMethod = parsed.method
    form.urlTemplate = parsed.url
    if (!String(form.componentName || '').trim() && parsed.componentName) {
      form.componentName = parsed.componentName
    }
    if (parsed.paramMode === 'custom') {
      form.params = parsed.params.map((item) => ({ ...item }))
    }
  }

  const authTarget = authState?.value ?? authState
  if (authTarget) {
    const nextAuth = {
      ...createEmptyAuthState(),
      ...parsed.authState,
      headers: Array.isArray(parsed.authState?.headers)
        ? parsed.authState.headers.map((item) => ({ ...item }))
        : [],
    }
    if (authState?.value !== undefined) {
      authState.value = nextAuth
    } else {
      Object.assign(authTarget, nextAuth)
    }
  }

  if (paramMode && typeof paramMode === 'object' && 'value' in paramMode) {
    paramMode.value = parsed.paramMode
  }

  return parsed
}
