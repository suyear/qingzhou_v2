import assert from 'node:assert/strict'
import {
  buildTriggerPayload,
  coerceFieldValue,
  emptyKvRow,
  formatTriggerPreview,
  formValuesToObject,
  kvRowsToObject,
  normalizeTriggerInput,
  objectToFormValues,
  objectToKvRows,
  previewKeyCount,
  validateTriggerPayload,
  examplePayloadFromSchema,
  schemaFieldGuide,
  annotateCurlWithFields,
} from './triggerInput.js'
import { schemaToFields } from './schema.js'

const fields = [
  { key: 'userId', type: 'string', required: true, description: '用户 ID' },
  { key: 'count', type: 'integer', required: false, description: '数量' },
  { key: 'enabled', type: 'boolean', required: false },
  { key: 'tags', type: 'array', required: false },
  { key: 'extra', type: 'object', required: false, description: '扩展' },
]

assert.deepEqual(normalizeTriggerInput('{"userId":"u1"}'), { userId: 'u1' })
assert.equal(normalizeTriggerInput('{}'), null)
assert.equal(normalizeTriggerInput('[]'), null)
assert.equal(normalizeTriggerInput('not-json'), null)
assert.deepEqual(normalizeTriggerInput({ a: 1 }), { a: 1 })

assert.deepEqual(objectToKvRows({ city: '成都', n: 2 }), [
  { key: 'city', value: '成都' },
  { key: 'n', value: '2' },
])
assert.deepEqual(kvRowsToObject([{ key: 'city', value: '成都' }, { key: '', value: 'x' }]), { city: '成都' })
assert.deepEqual(kvRowsToObject([emptyKvRow()]), {})

assert.equal(coerceFieldValue({ type: 'integer' }, '12'), 12)
assert.equal(coerceFieldValue({ type: 'boolean' }, '是'), true)
assert.deepEqual(coerceFieldValue({ type: 'array' }, 'a, b'), ['a', 'b'])
assert.deepEqual(coerceFieldValue({ type: 'object' }, '{"id":1}'), { id: 1 })
assert.equal(coerceFieldValue({ type: 'object' }, '{bad'), undefined)

const formObj = formValuesToObject(fields, {
  userId: 'u1',
  count: '3',
  enabled: true,
  tags: 'a,b',
  extra: '{"ok":true}',
})
assert.deepEqual(formObj, {
  userId: 'u1',
  count: 3,
  enabled: true,
  tags: ['a', 'b'],
  extra: { ok: true },
})

const back = objectToFormValues(fields, formObj)
assert.equal(back.userId, 'u1')
assert.equal(back.count, 3)
assert.equal(back.enabled, true)
assert.equal(back.tags, 'a, b')
assert.equal(back.extra.includes('"ok"'), true)

const emptyForm = objectToFormValues(
  [{ key: 'flag', type: 'boolean' }, { key: 'n', type: 'number' }],
  {},
)
assert.equal(emptyForm.flag, false)
assert.equal(emptyForm.n, undefined)

const schemaFields = schemaToFields({
  type: 'object',
  required: ['name'],
  properties: {
    name: { type: 'string', title: '姓名', enum: ['张三', '李四'] },
    age: { type: 'integer', description: '年龄', default: 18 },
  },
})
assert.equal(schemaFields[0].description, '姓名')
assert.deepEqual(schemaFields[0].enums, ['张三', '李四'])
assert.equal(schemaFields[0].required, true)
assert.equal(schemaFields[1].default, 18)

const formBuilt = buildTriggerPayload({
  fields,
  formValues: { userId: 'u1', count: 2 },
  kvRows: [emptyKvRow()],
  jsonText: '{}',
  advanced: false,
})
assert.deepEqual(formBuilt, { value: { userId: 'u1', count: 2 } })

const kvBuilt = buildTriggerPayload({
  fields: [],
  formValues: {},
  kvRows: [{ key: 'dept', value: '研发' }],
  jsonText: '{}',
  advanced: false,
})
assert.deepEqual(kvBuilt, { value: { dept: '研发' } })

const jsonErr = buildTriggerPayload({
  fields,
  formValues: {},
  kvRows: [],
  jsonText: '{bad',
  advanced: true,
})
assert.equal(jsonErr.error.includes('JSON'), true)

assert.equal(
  validateTriggerPayload({
    fields,
    formValues: { userId: '' },
    kvRows: [],
    jsonText: '{}',
    advanced: false,
  }),
  '请填写「用户 ID」',
)

assert.equal(
  validateTriggerPayload({
    fields,
    formValues: { userId: 'u1', extra: '{bad' },
    kvRows: [],
    jsonText: '{}',
    advanced: false,
  }),
  '「扩展」需填写有效的 JSON 对象',
)

assert.equal(
  validateTriggerPayload({
    fields,
    formValues: { userId: 'u1' },
    kvRows: [],
    jsonText: '{"name":1}',
    advanced: true,
  }),
  '请填写「用户 ID」',
)

assert.equal(
  validateTriggerPayload({
    fields,
    formValues: { userId: 'u1' },
    kvRows: [],
    jsonText: '{"userId":"u1"}',
    advanced: true,
  }),
  '',
)

assert.equal(previewKeyCount({ a: 1, b: 2 }), 2)
assert.equal(formatTriggerPreview({ a: 1 }).includes('"a"'), true)

assert.deepEqual(
  examplePayloadFromSchema({
    type: 'object',
    required: ['userId'],
    properties: {
      userId: { type: 'string', description: '用户 ID' },
      count: { type: 'integer', default: 3 },
    },
  }),
  { userId: '10001', count: 3 },
)

const guide = schemaFieldGuide({
  type: 'object',
  required: ['userId'],
  properties: { userId: { type: 'string', description: '用户 ID' } },
})
assert.equal(guide[0].required, true)
assert.equal(guide[0].example, '10001')
assert.equal(
  annotateCurlWithFields('curl http://x', guide).startsWith('# userId 用户 ID · 必填 · string · 例 10001'),
  true,
)

console.log('triggerInput unit tests passed')
