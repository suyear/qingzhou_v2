/** 凭证类型与 HTTP 鉴权 / 数据库子类型常量 */

export const CREDENTIAL_TYPES = [
  { value: 'WECOM', label: '企业微信', desc: '微信侧密钥，自动换访问令牌' },
  { value: 'HTTP_AUTH', label: '接口密钥', desc: '访问令牌、账号密码等' },
  { value: 'DATABASE', label: '数据库', desc: '连业务库跑 SQL' },
]

/** 创建表单常用鉴权 */
export const HTTP_AUTH_COMMON = [
  { value: 'bearer', label: '访问令牌', desc: 'Bearer Token', mark: 'Be' },
  { value: 'basic', label: '账号密码', desc: '用户名 + 密码', mark: 'Ba' },
  { value: 'apiKey', label: 'API Key', desc: 'Header 或 Query', mark: 'AK' },
]

/** 创建表单高级鉴权（默认折叠） */
export const HTTP_AUTH_ADVANCED = [
  { value: 'digest', label: 'HTTP Digest', desc: '挑战-响应摘要', mark: 'Di' },
  { value: 'jwt', label: 'JWT', desc: '静态或本地签发', mark: 'JW' },
  { value: 'oauth2_cc', label: 'OAuth2 客户端凭证', desc: '自动换票', mark: 'O2' },
  { value: 'aksk', label: 'AK/SK + HMAC', desc: '云厂商风格签名', mark: 'SK' },
  { value: 'cookie', label: 'Cookie / Session', desc: '注入 Cookie 头', mark: 'Ck' },
  { value: 'mtls', label: 'mTLS', desc: '客户端证书', mark: 'mT' },
]

export const HTTP_AUTH_TYPES = [...HTTP_AUTH_COMMON, ...HTTP_AUTH_ADVANCED]

export const HTTP_AUTH_COMMON_VALUES = HTTP_AUTH_COMMON.map((item) => item.value)

export const DB_TYPES_COMMON = [
  { value: 'mysql', label: 'MySQL', port: 3306 },
]

export const DB_TYPES_MORE = [
  { value: 'mariadb', label: 'MariaDB', port: 3306 },
  { value: 'postgresql', label: 'PostgreSQL', port: 5432 },
  { value: 'sqlserver', label: 'SQL Server', port: 1433 },
  { value: 'oracle', label: 'Oracle', port: 1521 },
]

export const DB_TYPES = [...DB_TYPES_COMMON, ...DB_TYPES_MORE]

export function normalizeCredentialType(type) {
  if (type === 'CUSTOM') return 'HTTP_AUTH'
  if (type === 'MYSQL') return 'DATABASE'
  return type || 'WECOM'
}

export function isDatabaseCredential(type) {
  return type === 'DATABASE' || type === 'MYSQL'
}

export function isHttpAuthCredential(type) {
  return type === 'HTTP_AUTH' || type === 'CUSTOM'
}

export function credentialTypeLabel(type) {
  const normalized = normalizeCredentialType(type)
  return CREDENTIAL_TYPES.find((item) => item.value === normalized)?.label
    || (type === 'CUSTOM' ? '自定义 HTTP' : type === 'MYSQL' ? 'MySQL' : '企业微信')
}

export function httpAuthTypeLabel(authType) {
  return HTTP_AUTH_TYPES.find((item) => item.value === authType)?.label || authType || '—'
}

export function dbTypeLabel(dbType) {
  return DB_TYPES.find((item) => item.value === dbType)?.label || dbType || 'MySQL'
}

export function defaultDbPort(dbType) {
  return DB_TYPES.find((item) => item.value === dbType)?.port || 3306
}

export function createEmptyCredentialForm() {
  return {
    id: null,
    credentialName: '',
    credentialType: 'WECOM',
    scope: 'GLOBAL',
    workflowId: null,
    corpId: '',
    agentId: '',
    secret: '',
    remark: '',
    dbType: 'mysql',
    dbHost: '127.0.0.1',
    dbPort: 3306,
    dbName: '',
    dbUsername: '',
    authType: 'bearer',
    username: '',
    apiKeyName: 'X-API-Key',
    apiKeyIn: 'header',
    tokenUrl: '',
    clientId: '',
    jwtMode: 'static',
    jwtAlg: 'HS256',
    jwtIssuer: '',
    jwtAudience: '',
    jwtSubject: '',
    jwtTtlSeconds: 3600,
    accessKey: '',
    hmacAlg: 'HmacSHA256',
    signHeader: 'X-Signature',
    signTemplate: '{method}\\n{path}\\n{timestamp}\\n{nonce}\\n{accessKey}',
    includeTimestamp: true,
    includeNonce: true,
    timestampHeader: 'X-Timestamp',
    nonceHeader: 'X-Nonce',
    clientCert: '',
    privateKey: '',
    cookie: '',
    apiKeyValue: '',
    clientSecret: '',
    secretKey: '',
    jwtSecret: '',
    password: '',
  }
}

export function buildCredentialPayload(form) {
  const type = normalizeCredentialType(form.credentialType)
  const payload = {
    credentialName: form.credentialName,
    credentialType: type,
    scope: form.scope,
    workflowId: form.scope === 'WORKFLOW' ? form.workflowId : null,
    remark: form.remark,
  }

  if (type === 'WECOM') {
    payload.corpId = form.corpId
    payload.agentId = form.agentId
    if (form.secret) payload.secret = form.secret
    return payload
  }

  if (type === 'DATABASE') {
    payload.dbType = form.dbType || 'mysql'
    payload.dbHost = form.dbHost
    payload.dbPort = form.dbPort
    payload.dbName = form.dbName
    payload.dbUsername = form.dbUsername
    if (form.secret) payload.secret = form.secret
    return payload
  }

  payload.authType = form.authType || 'bearer'
  payload.username = form.username || undefined
  payload.apiKeyName = form.apiKeyName || undefined
  payload.apiKeyIn = form.apiKeyIn || undefined
  payload.tokenUrl = form.tokenUrl || undefined
  payload.clientId = form.clientId || undefined
  payload.jwtMode = form.jwtMode || undefined
  payload.jwtAlg = form.jwtAlg || undefined
  payload.jwtIssuer = form.jwtIssuer || undefined
  payload.jwtAudience = form.jwtAudience || undefined
  payload.jwtSubject = form.jwtSubject || undefined
  payload.jwtTtlSeconds = form.jwtTtlSeconds || undefined
  payload.accessKey = form.accessKey || undefined
  payload.hmacAlg = form.hmacAlg || undefined
  payload.signHeader = form.signHeader || undefined
  payload.signTemplate = form.signTemplate || undefined
  payload.includeTimestamp = form.includeTimestamp
  payload.includeNonce = form.includeNonce
  payload.timestampHeader = form.timestampHeader || undefined
  payload.nonceHeader = form.nonceHeader || undefined

  const secrets = {}
  const authType = payload.authType
  if (form.password) secrets.password = form.password
  if (form.secret) {
    // 通用 secret 字段按类型映射
    if (['basic', 'digest'].includes(authType) && !secrets.password) secrets.password = form.secret
    else if (authType === 'cookie' && !form.cookie) secrets.cookie = form.secret
    else if (authType === 'apiKey' && !form.apiKeyValue) secrets.apiKeyValue = form.secret
    else if (authType === 'oauth2_cc' && !form.clientSecret) secrets.clientSecret = form.secret
    else if (authType === 'aksk' && !form.secretKey) secrets.secretKey = form.secret
    else if (authType === 'jwt' && form.jwtMode === 'sign' && !form.jwtSecret) secrets.jwtSecret = form.secret
    else if (authType === 'jwt' && form.jwtMode !== 'sign') secrets.token = form.secret
    else if (authType === 'bearer') secrets.token = form.secret
    else secrets.secret = form.secret
  }
  if (form.cookie) secrets.cookie = form.cookie
  if (form.apiKeyValue) secrets.apiKeyValue = form.apiKeyValue
  if (form.clientSecret) secrets.clientSecret = form.clientSecret
  if (form.secretKey) secrets.secretKey = form.secretKey
  if (form.jwtSecret) secrets.jwtSecret = form.jwtSecret
  if (form.clientCert) secrets.clientCert = form.clientCert
  if (form.privateKey) secrets.privateKey = form.privateKey

  if (Object.keys(secrets).length) {
    payload.secrets = secrets
  }
  return payload
}
