import assert from 'node:assert/strict'
import {
  bindingSummary,
  findUpstreamFieldMatch,
  handoffOptionsForStep,
  plainFieldName,
  suggestHandoff,
} from './workflowBinding.js'

assert.equal(plainFieldName(''), '整份结果')
assert.equal(plainFieldName('__whole__'), '整份结果')
assert.equal(plainFieldName('rows'), '全部数据行')
assert.equal(plainFieldName('rowCount'), '一共多少行')
assert.equal(plainFieldName('rows[0].username'), '第一行的「username」')
assert.equal(plainFieldName('deptId'), '「deptId」')

const steps = [
  {
    id: 'a',
    name: '查询客户',
    responseFields: ['rows', 'rows[0].id', 'rows[0].name'],
    requestFields: ['keyword'],
    outputPorts: [{ key: 'customerId', fromPath: '$.rows[0].id', description: '客户编号' }],
  },
  {
    id: 'b',
    name: '无关步骤',
    responseFields: ['status'],
    requestFields: [],
    outputPorts: [],
  },
]

const options = handoffOptionsForStep(steps[0])
assert.equal(options[0].label, '整份结果')
assert.equal(options[0].fromField, '__whole__')
assert.ok(options.some((item) => item.fromField === 'rows[0].id' && item.label.includes('客户编号')))
assert.equal(options.filter((item) => item.fromField === 'rows[0].id').length, 1)

assert.deepEqual(findUpstreamFieldMatch(steps, 'name'), {
  nodeId: 'a',
  field: 'rows[0].name',
  fromSource: 'output',
})

assert.deepEqual(findUpstreamFieldMatch(steps, 'customerId'), {
  nodeId: 'a',
  field: 'rows[0].id',
  fromSource: 'output',
})

assert.equal(findUpstreamFieldMatch([{
  id: 'earlier',
  responseFields: ['id'],
  requestFields: [],
  outputPorts: [],
}, {
  id: 'nearer',
  responseFields: ['id'],
  requestFields: [],
}], 'id').nodeId, 'nearer')

const suggestion = suggestHandoff('userId', [{
  id: 'q',
  name: '查用户',
  responseFields: ['rows[0].id'],
  requestFields: ['userId'],
  outputPorts: [],
}])
assert.equal(suggestion.fromNode, 'q')
assert.equal(suggestion.fromField, 'rows[0].id')
assert.equal(suggestion.stepIndex, 1)
assert.equal(suggestHandoff('keyword', [{
  id: 'q',
  name: '查用户',
  responseFields: [],
  requestFields: ['keyword'],
}]), null)

assert.equal(
  bindingSummary(
    { mode: 'upstream', fromNode: 'a', fromField: 'rows[0].id', fromSource: 'output' },
    { key: 'userId' },
    { nodeNames: { a: '查询客户' }, nodeIndexes: { a: 1 } },
  ),
  '使用第 1 步「查询客户」交出的第一行的「id」',
)
assert.equal(
  bindingSummary({ mode: 'runtime', inputKey: 'userId' }, { key: 'id' }, { inputLabels: { userId: '用户' } }),
  '调用时由外面传入「用户」',
)
assert.equal(bindingSummary({ mode: 'fixed', value: '' }, { key: 'id' }), '还没填写')

console.log('workflowBinding.test.js ok')
