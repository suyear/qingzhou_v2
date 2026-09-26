/**
 * 开放平台对外契约：文档 / curl / 试调共用同一份示例。
 * 字段须与真实网关 R<OpenApiExecuteResultVO> 一致（DB 业务键为 rows，不是 preview）。
 * 工作流若配置「查询首行」，data.output 则为单行对象而非整包。
 */
export const OPENAPI_DEMO_INPUT = { userId: '1' }

/** 与 OPENAPI_DEMO_INPUT 对应：查询首行形态（按 ID 查用户常见） */
export const OPENAPI_DEMO_OUTPUT = {
  id: 1,
  username: 'admin',
  display_name: '系统管理员',
  role: 'ADMIN',
  status: 1,
}

/** 未做首行/字段投影时的 DB 完整结果示例 */
export const OPENAPI_DEMO_OUTPUT_DB_FULL = {
  rowCount: 1,
  affectedRows: 0,
  rows: [OPENAPI_DEMO_OUTPUT],
  truncated: false,
}

export function openapiBodyCompact(input = OPENAPI_DEMO_INPUT) {
  return JSON.stringify(input)
}

export function openapiBodyPretty(input = OPENAPI_DEMO_INPUT) {
  return JSON.stringify(input, null, 2)
}

/** 工作流成功：含 executionId / executionNo */
export function openapiWorkflowSuccessSample(output = OPENAPI_DEMO_OUTPUT) {
  return {
    code: 0,
    message: 'ok',
    data: {
      executionId: 12,
      executionNo: 'E20260101120000xxxx',
      status: 'SUCCESS',
      durationMs: 86,
      output,
    },
  }
}

/** 组件成功：无执行实例字段，与网关真实返回一致 */
export function openapiComponentSuccessSample(output = OPENAPI_DEMO_OUTPUT_DB_FULL) {
  return {
    code: 0,
    message: 'ok',
    data: {
      status: 'SUCCESS',
      durationMs: 86,
      output,
    },
  }
}

export function openapiSuccessSampleText(kind = 'workflow', output) {
  const sample = kind === 'component'
    ? openapiComponentSuccessSample(output ?? OPENAPI_DEMO_OUTPUT_DB_FULL)
    : openapiWorkflowSuccessSample(output ?? OPENAPI_DEMO_OUTPUT)
  return JSON.stringify(sample, null, 2)
}

export function mergeComponentInputSchema(component) {
  if (!component) return null
  const properties = {}
  const required = []
  for (const field of ['querySchema', 'bodySchema']) {
    let schema = component[field]
    if (typeof schema === 'string') {
      try {
        schema = JSON.parse(schema)
      } catch {
        schema = null
      }
    }
    if (!schema || typeof schema !== 'object') continue
    const props = schema.properties && typeof schema.properties === 'object' ? schema.properties : {}
    Object.assign(properties, props)
    if (Array.isArray(schema.required)) {
      for (const key of schema.required) {
        if (!required.includes(key)) required.push(key)
      }
    }
  }
  if (!Object.keys(properties).length) return null
  return { type: 'object', properties, required }
}

export function grantSummary(row) {
  const wf = row?.grantedWorkflowCount ?? 0
  const comp = row?.grantedComponentCount ?? 0
  if (!wf && !comp) return '去授权'
  return `接口 ${comp} · 组合 ${wf}`
}
