import assert from 'node:assert/strict'
import { codeDisplayText, isBlankPayload } from './detailText.js'

assert.equal(isBlankPayload(null), true)
assert.equal(isBlankPayload(''), true)
assert.equal(isBlankPayload({}), true)
assert.equal(isBlankPayload({ id: 1 }), false)
assert.equal(codeDisplayText(null).empty, true)
assert.equal(codeDisplayText({ orderId: '1' }).text.includes('orderId'), true)
assert.equal(codeDisplayText('{"a":1}').empty, false)

console.log('detailText.test.js ok')
