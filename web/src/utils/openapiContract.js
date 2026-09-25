/**
 * 开放平台对外契约：文档 / curl / 试调共用同一套示例字段。
 */
export const OPENAPI_DEMO_INPUT = { id: '10001' }

export const OPENAPI_DEMO_OUTPUT = {
  rowCount: 1,
  rows: [{ id: 1, name: 'demo' }],
}

export function openapiSuccessSample(input = OPENAPI_DEMO_INPUT, output = OPENAPI_DEMO_OUTPUT) {
  return {
    code: 0,
    message: 'ok',
    data: {
      executionId: 12,
      executionNo: 'E20260101120000xxxx',
      status: 'SUCCESS',
      durationMs: 86,
      errorMsg: null,
      output,
    },
  }
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
