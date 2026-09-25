import assert from 'node:assert/strict'
import { failRateOption, failRankBarOption, hasChartData, statusMeta, trendOption } from './charts.js'

assert.equal(hasChartData([]), false)
assert.equal(hasChartData([{ count: 0 }, { count: 0 }]), false)
assert.equal(hasChartData([{ count: 2 }]), true)
assert.equal(hasChartData([{ total: 3 }], 'total'), true)

const meta = statusMeta('exec', 'SUCCESS')
assert.equal(meta.type, 'success')
assert.equal(meta.label, '成功')
assert.equal(statusMeta('job', 1).label, '运行中')
assert.equal(statusMeta('enable', 0).label, '停用')

const option = trendOption([
  { date: '2026-09-10', total: 2, successCount: 1, failedCount: 1 },
  { date: '2026-09-11', total: 0, successCount: 0, failedCount: 0 },
])
assert.equal(option.series.length, 3)
assert.deepEqual(option.series[0].data, [2, 0])
assert.equal(option.xAxis.data[0], '09-10')

const failOption = failRateOption([
  { date: '2026-09-10', failRate: 25 },
  { date: '2026-09-11', failRate: null },
])
assert.deepEqual(failOption.series[0].data, [25, null])

const failRank = failRankBarOption([
  { name: '订单同步', failedCount: 3 },
  { name: '库存同步', failedCount: 1 },
])
assert.deepEqual(failRank.series[0].data, [1, 3])

console.log('charts.test.js ok')
