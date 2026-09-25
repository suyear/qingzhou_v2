import assert from 'node:assert/strict'
import { compactSql, extractNamedParams, isDatabaseComponent, parseDatabaseExtra } from './sqlParams.js'

assert.deepEqual(
  extractNamedParams("SELECT * FROM t WHERE id = :userId AND name = ':skip' -- :cmt\nAND nick = :nick"),
  ['userId', 'nick'],
)
assert.deepEqual(extractNamedParams('UPDATE t SET a = :a WHERE id = :id'), ['a', 'id'])
assert.equal(compactSql('SELECT   *\nFROM users', 20), 'SELECT * FROM users')
assert.equal(isDatabaseComponent({ provider: 'DATABASE' }), true)
assert.equal(isDatabaseComponent({ httpMethod: 'GET' }), false)
assert.equal(parseDatabaseExtra({ extraConfig: { datasourceId: 3, accessMode: 'WRITE' } }).datasourceId, 3)
assert.equal(parseDatabaseExtra({ extraConfig: '{"accessMode":"READ"}' }).accessMode, 'READ')

console.log('sqlParams.test.js ok')
