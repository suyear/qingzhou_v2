import { parseJson } from '@/utils/schema'

export const AUTH_TYPES = [
  { value: 'credential', label: '引用凭证', desc: '从凭证中心选择，推荐', mark: 'CR', group: 'recommend' },
  { value: 'none', label: '无需鉴权', desc: '公开接口或网关已处理', mark: '—', group: 'simple' },
  { value: 'bearer', label: 'Bearer Token', desc: '内联 Token，明文存组件', mark: 'Be', muted: true, group: 'inline' },
  { value: 'apiKey', label: 'API Key', desc: 'Header / Query，明文存组件', mark: 'AK', muted: true, group: 'inline' },
  { value: 'basic', label: 'Basic Auth', desc: '用户名密码，明文存组件', mark: 'Ba', muted: true, group: 'inline' },
  { value: 'wecom', label: '企业微信', desc: '工作流绑定凭证换 Token', mark: '企', group: 'inline' },
]

export const AUTH_TYPE_GROUPS = [
  { key: 'primary', title: null, values: ['credential', 'none'] },
  { key: 'inline', title: '内联配置（密钥会写入组件）', values: ['bearer', 'apiKey', 'basic', 'wecom'] },
]

export function createEmptyAuthState() {
  return {
    authType: 'none',
    credentialId: null,
    bearerToken: '',
    apiKeyName: 'X-API-Key',
    apiKeyValue: '',
    apiKeyIn: 'header',
    basicUsername: '',
    basicPassword: '',
    headers: [],
  }
}

export function parseAuthFromComponent(component) {
  const config = parseJson(component?.extraConfig, {})
  const auth = config.auth && typeof config.auth === 'object' ? config.auth : {}
  const headers = Array.isArray(config.headers)
    ? config.headers.map((item) => ({
      key: item.key || '',
      value: item.value || '',
      enabled: item.enabled !== false,
    }))
    : []
  return {
    authType: auth.type || (config.needAccessToken ? 'wecom' : 'none'),
    credentialId: auth.credentialId || null,
    bearerToken: auth.bearerToken || '',
    apiKeyName: auth.apiKeyName || 'X-API-Key',
    apiKeyValue: auth.apiKeyValue || '',
    apiKeyIn: auth.apiKeyIn || 'header',
    basicUsername: auth.basicUsername || '',
    basicPassword: auth.basicPassword || '',
    headers,
  }
}

export function buildExtraConfig(authState) {
  const state = authState || createEmptyAuthState()
  const auth = { type: state.authType || 'none' }
  if (auth.type === 'credential') {
    auth.credentialId = state.credentialId || null
  }
  if (auth.type === 'bearer') {
    auth.bearerToken = String(state.bearerToken || '').trim()
  }
  if (auth.type === 'apiKey') {
    auth.apiKeyName = String(state.apiKeyName || '').trim()
    auth.apiKeyValue = String(state.apiKeyValue || '').trim()
    auth.apiKeyIn = state.apiKeyIn === 'query' ? 'query' : 'header'
  }
  if (auth.type === 'basic') {
    auth.basicUsername = String(state.basicUsername || '').trim()
    auth.basicPassword = String(state.basicPassword || '')
  }

  const headers = (state.headers || [])
    .filter((item) => String(item.key || '').trim())
    .map((item) => ({
      key: String(item.key).trim(),
      value: String(item.value ?? ''),
      enabled: item.enabled !== false,
    }))

  const extraConfig = { auth }
  if (auth.type === 'wecom') {
    extraConfig.needAccessToken = true
  }
  if (headers.length) {
    extraConfig.headers = headers
  }
  return extraConfig
}

export function authTypeLabel(type) {
  return AUTH_TYPES.find((item) => item.value === type)?.label || '无需鉴权'
}

export function authSummary(component) {
  const state = parseAuthFromComponent(component)
  if (state.authType === 'credential') {
    return state.credentialId ? `引用凭证 #${state.credentialId}` : '引用凭证（未选）'
  }
  if (state.authType === 'none') {
    const headerCount = state.headers.filter((item) => item.enabled && item.key).length
    return headerCount ? `自定义 Header ×${headerCount}` : ''
  }
  if (state.authType === 'bearer') return 'Bearer Token（内联）'
  if (state.authType === 'apiKey') {
    const place = state.apiKeyIn === 'query' ? 'Query' : 'Header'
    return `API Key · ${state.apiKeyName || 'key'} (${place})`
  }
  if (state.authType === 'basic') return `Basic · ${state.basicUsername || '用户'}`
  if (state.authType === 'wecom') return '企业微信凭证'
  return ''
}

export function validateAuthState(authState) {
  const state = authState || createEmptyAuthState()
  if (state.authType === 'credential' && !state.credentialId) {
    return { valid: false, message: '请选择鉴权凭证' }
  }
  if (state.authType === 'bearer' && !String(state.bearerToken || '').trim()) {
    return { valid: false, message: '请填写 Bearer Token' }
  }
  if (state.authType === 'apiKey') {
    if (!String(state.apiKeyName || '').trim()) {
      return { valid: false, message: '请填写 API Key 参数名' }
    }
    if (!String(state.apiKeyValue || '').trim()) {
      return { valid: false, message: '请填写 API Key 值' }
    }
  }
  if (state.authType === 'basic' && !String(state.basicUsername || '').trim()) {
    return { valid: false, message: '请填写 Basic Auth 用户名' }
  }
  return { valid: true }
}

export function componentNeedsCredential(component) {
  const state = parseAuthFromComponent(component)
  return state.authType === 'wecom'
    || state.authType === 'credential'
    || needsAccessTokenFromUrl(component?.urlTemplate)
}

function needsAccessTokenFromUrl(urlTemplate) {
  return String(urlTemplate || '').includes('access_token')
}
