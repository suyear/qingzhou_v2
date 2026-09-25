<template>
  <div class="execution-page">
    <PageHeader title="运行结果" desc="按时间与来源定位失败，支持导出与批量重放。点开链路可看卡在哪一步。">
      <el-button :loading="exporting" @click="onExport">导出 CSV</el-button>
    </PageHeader>
    <PageState :error="loadError" @retry="load" />

    <div class="filter-bar qz-panel">
      <el-date-picker
        v-model="timeRange"
        type="datetimerange"
        range-separator="至"
        start-placeholder="开始"
        end-placeholder="结束"
        value-format="YYYY-MM-DD HH:mm:ss"
        :clearable="true"
        class="filter-date"
        @change="onFilterChange"
      />
      <el-select v-model="workflowId" placeholder="全部工作流" clearable class="filter-wf" @change="onFilterChange">
        <el-option
          v-for="wf in workflows"
          :key="wf.id"
          :label="wf.workflowName"
          :value="wf.id"
        />
      </el-select>
      <el-select v-model="triggerType" placeholder="触发方式" clearable class="filter-sm" @change="onFilterChange">
        <el-option label="试运行" value="TRY_RUN" />
        <el-option label="调度" value="SCHEDULE" />
        <el-option label="开放调用" value="OPENAPI" />
        <el-option label="重放" value="REPLAY" />
        <el-option label="手动" value="MANUAL" />
      </el-select>
      <el-select
        v-if="viewFilter === 'all'"
        v-model="status"
        placeholder="状态"
        clearable
        class="filter-sm"
        @change="onFilterChange"
      >
        <el-option label="成功" value="SUCCESS" />
        <el-option label="失败" value="FAILED" />
        <el-option label="超时" value="TIMEOUT" />
        <el-option label="运行中" value="RUNNING" />
      </el-select>
      <el-input-number
        v-model="minDurationMs"
        :min="0"
        :step="1000"
        controls-position="right"
        placeholder="慢单 ms"
        class="filter-ms"
        @change="onFilterChange"
      />
      <el-input
        v-model="keyword"
        class="filter-keyword"
        placeholder="单号 / Trace / 错误摘要"
        clearable
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-button type="primary" @click="reload">查询</el-button>
    </div>

    <div class="stat-grid cols-4" v-if="execStats">
      <div class="stat-card">
        <div class="stat-label">今日执行</div>
        <div class="stat-num">{{ execStats.todayTotal ?? 0 }}</div>
      </div>
      <div class="stat-card stat-ok">
        <div class="stat-label">今日成功</div>
        <div class="stat-num">{{ execStats.todaySuccess ?? 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">今日失败</div>
        <div class="stat-num">{{ execStats.todayFailed ?? 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">平均耗时</div>
        <div class="stat-num">{{ execStats.todayAvgMs != null ? `${execStats.todayAvgMs}ms` : '—' }}</div>
        <div class="stat-hint">超时 {{ execStats.todayTimeout ?? 0 }}</div>
      </div>
    </div>
    <p v-if="rangeSummary" class="range-summary">{{ rangeSummary }}</p>

    <div class="toolbar-row">
      <div class="view-tabs">
        <button
          type="button"
          class="view-tab"
          :class="{ active: viewFilter === 'all' }"
          @click="setViewFilter('all')"
        >
          全部
        </button>
        <button
          type="button"
          class="view-tab"
          :class="{ active: viewFilter === 'problem' }"
          @click="setViewFilter('problem')"
        >
          失败与超时
        </button>
      </div>
      <div class="batch-actions" v-if="selectedIds.length">
        <span class="muted">已选 {{ selectedIds.length }}</span>
        <el-button type="primary" :loading="batchReplaying" @click="onBatchReplay">批量重放</el-button>
      </div>
    </div>

    <div v-if="triggerAppId" class="qz-filter-chip">
      仅看待定开放应用的调用
      <el-button type="primary" link @click="clearAppFilter">清除</el-button>
    </div>

    <div class="qz-panel">
      <el-table
        class="qz-table"
        :data="records"
        v-loading="loading"
        stripe
        highlight-current-row
        @row-click="openDetail"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="44" :selectable="rowSelectable" />
        <el-table-column label="执行单号" min-width="180">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="openDetail(row)">{{ row.executionNo }}</el-button>
            <div v-if="viewFilter === 'problem'" class="sub">{{ formatTime(row.startTime) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="工作流" min-width="150">
          <template #default="{ row }">
            <div>{{ row.workflowName || row.workflowId }}</div>
            <div class="sub">
              <el-button
                v-if="row.workflowCode"
                type="primary"
                link
                class="mono"
                @click.stop="copyField(row.workflowCode, '已复制编码')"
              >{{ row.workflowCode }}</el-button>
              <span v-else>v{{ row.workflowVersion }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="触发" width="96">
          <template #default="{ row }">
            <StatusTag kind="trigger" :value="row.triggerType" />
          </template>
        </el-table-column>
        <el-table-column label="来源" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.triggerSourceLabel">{{ row.triggerSourceLabel }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <StatusTag kind="exec" :value="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="分类" width="80">
          <template #default="{ row }">
            <StatusTag
              v-if="row.failureCategory"
              :type="failureTagType(row.failureCategory)"
              :label="row.failureCategoryLabel || row.failureCategory"
            />
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="80">
          <template #default="{ row }">{{ durationText(row.durationMs) }}</template>
        </el-table-column>
        <el-table-column :label="viewFilter === 'problem' ? '失败原因' : '说明'" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.errorMsg" class="err">{{ row.errorMsg }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column v-if="viewFilter === 'all'" label="开始时间" min-width="150">
          <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <div class="qz-ops" @click.stop>
              <el-button type="primary" link @click="openDetail(row)">详情</el-button>
              <el-button type="primary" link :disabled="row.status === 'RUNNING'" :loading="row._replaying" @click="onReplay(row)">重放</el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty v-if="!loading && !loadError" :description="emptyText">
            <el-button type="primary" @click="$router.push('/workflows')">去编排</el-button>
          </el-empty>
        </template>
      </el-table>
      <div class="pager">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="total"
          v-model:current-page="current"
          v-model:page-size="size"
          @current-change="onPageChange"
        />
      </div>
    </div>

    <ExecutionChainDrawer ref="chainDrawer" :replay-loading="replaying" @replay="onReplay" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import PageState from '@/components/PageState.vue'
import StatusTag from '@/components/StatusTag.vue'
import ExecutionChainDrawer from '@/components/ExecutionChainDrawer.vue'
import { askConfirm } from '@/utils/confirm'
import { pageWorkflows } from '@/api/workflow'
import {
  batchReplayExecutions,
  exportExecutions,
  getExecutionChain,
  pageExecutions,
  replayExecution,
} from '@/api/execution'
import { getDashboardOverview, getModuleStats } from '@/api/dashboard'
import { networkErrorMessage } from '@/api/http'
import { copyText, durationText, execStatusLabel, formatTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const records = ref([])
const workflows = ref([])
const total = ref(0)
const current = ref(1)
const size = ref(10)
const loading = ref(false)
const loadError = ref('')
const execStats = ref(null)
const rangeSummary = ref('')
const keyword = ref(route.query.keyword || '')
const workflowId = ref(route.query.workflowId ? Number(route.query.workflowId) : null)
const triggerType = ref(route.query.triggerType || '')
const triggerAppId = ref(route.query.triggerAppId ? Number(route.query.triggerAppId) : null)
const status = ref(route.query.status || '')
const viewFilter = ref(route.query.filter === 'problem' ? 'problem' : 'all')
const minDurationMs = ref(route.query.minDurationMs ? Number(route.query.minDurationMs) : null)
const timeRange = ref(defaultTimeRange())
const replaying = ref(false)
const batchReplaying = ref(false)
const exporting = ref(false)
const syncingQuery = ref(false)
const chainDrawer = ref(null)
const selectedRows = ref([])

const selectedIds = computed(() => selectedRows.value.map((item) => item.id).filter(Boolean))

const emptyText = computed(() => {
  if (keyword.value || workflowId.value || triggerType.value || status.value || triggerAppId.value || timeRange.value?.length) {
    return viewFilter.value === 'problem' ? '没有匹配的失败执行' : '没有匹配的执行记录'
  }
  return viewFilter.value === 'problem'
    ? '最近没有失败或超时，链路是通的'
    : '还没有执行记录'
})

function defaultTimeRange() {
  const end = new Date()
  const start = new Date(end.getTime() - 7 * 24 * 3600 * 1000)
  return [formatDateTime(start), formatDateTime(end)]
}

function formatDateTime(d) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function failureTagType(cat) {
  if (cat === 'TIMEOUT') return 'warning'
  if (cat === 'AUTH' || cat === 'PARAM') return 'danger'
  if (cat === 'UPSTREAM') return 'warning'
  if (cat === 'BUSINESS') return 'info'
  return 'info'
}

function rowSelectable(row) {
  return row.status !== 'RUNNING'
}

function queryParams() {
  return {
    current: current.value,
    size: size.value,
    keyword: keyword.value || undefined,
    workflowId: workflowId.value || undefined,
    triggerType: triggerType.value || undefined,
    triggerAppId: triggerAppId.value || undefined,
    status: viewFilter.value === 'all' ? (status.value || undefined) : undefined,
    problem: viewFilter.value === 'problem' ? true : undefined,
    startTimeFrom: timeRange.value?.[0] || undefined,
    startTimeTo: timeRange.value?.[1] || undefined,
    minDurationMs: minDurationMs.value || undefined,
  }
}

function applyQuery() {
  workflowId.value = route.query.workflowId ? Number(route.query.workflowId) : null
  triggerType.value = route.query.triggerType || ''
  triggerAppId.value = route.query.triggerAppId ? Number(route.query.triggerAppId) : null
  status.value = route.query.status || ''
  keyword.value = route.query.keyword || ''
  viewFilter.value = route.query.filter === 'problem' ? 'problem' : 'all'
  current.value = route.query.page ? Number(route.query.page) : 1
  minDurationMs.value = route.query.minDurationMs ? Number(route.query.minDurationMs) : null
  if (route.query.from && route.query.to) {
    timeRange.value = [route.query.from, route.query.to]
  }
}

function syncQuery() {
  const query = {}
  if (viewFilter.value === 'problem') query.filter = 'problem'
  if (workflowId.value) query.workflowId = String(workflowId.value)
  if (triggerType.value) query.triggerType = triggerType.value
  if (triggerAppId.value) query.triggerAppId = String(triggerAppId.value)
  if (viewFilter.value === 'all' && status.value) query.status = status.value
  if (keyword.value) query.keyword = keyword.value
  if (current.value > 1) query.page = String(current.value)
  if (timeRange.value?.[0]) query.from = timeRange.value[0]
  if (timeRange.value?.[1]) query.to = timeRange.value[1]
  if (minDurationMs.value) query.minDurationMs = String(minDurationMs.value)
  if (route.query.id) query.id = String(route.query.id)
  syncingQuery.value = true
  router.replace({ path: '/executions', query }).finally(() => {
    syncingQuery.value = false
  })
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [res, statsRes, overviewRes] = await Promise.all([
      pageExecutions(queryParams()),
      getModuleStats().catch(() => null),
      getDashboardOverview({ days: 7 }).catch(() => null),
    ])
    records.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
    execStats.value = statsRes?.data?.executions || null
    const summary = overviewRes?.data?.summary
    const duration = overviewRes?.data?.duration
    if (summary) {
      const rate = summary.successRate != null ? `${Number(summary.successRate).toFixed(1)}%` : '—'
      const avg = duration?.avgMs != null ? `${duration.avgMs}ms` : '—'
      rangeSummary.value = `近 7 日：执行 ${summary.rangeTotal ?? 0} · 失败 ${summary.rangeFailed ?? 0} · 成功率 ${rate} · 平均耗时 ${avg}`
    } else {
      rangeSummary.value = ''
    }
  } catch (error) {
    loadError.value = networkErrorMessage(error)
    records.value = []
  } finally {
    loading.value = false
  }
}

function setViewFilter(next) {
  if (viewFilter.value === next) return
  viewFilter.value = next
  if (next === 'problem') status.value = ''
  current.value = 1
  syncQuery()
  load()
}

function onFilterChange() {
  current.value = 1
  syncQuery()
  load()
}

function clearAppFilter() {
  triggerAppId.value = null
  onFilterChange()
}

function reload() {
  current.value = 1
  syncQuery()
  load()
}

function onPageChange() {
  syncQuery()
  load()
}

function onSelectionChange(rows) {
  selectedRows.value = rows || []
}

function openDetail(row) {
  if (!row?.id) return
  chainDrawer.value?.open(row.id)
  router.replace({
    path: '/executions',
    query: {
      ...route.query,
      id: String(row.id),
    },
  })
}

async function copyField(text, message) {
  await copyText(text)
  ElMessage.success(message)
}

async function onExport() {
  exporting.value = true
  try {
    const params = { ...queryParams() }
    delete params.current
    delete params.size
    await exportExecutions(params)
    ElMessage.success('已开始下载 CSV（最多 5000 条）')
  } catch (error) {
    ElMessage.error(networkErrorMessage(error))
  } finally {
    exporting.value = false
  }
}

async function onBatchReplay() {
  if (!selectedIds.value.length) return
  if (!(await askConfirm(`按原入参批量重放 ${selectedIds.value.length} 条？将生成新的执行单。`, '批量重放'))) return
  batchReplaying.value = true
  try {
    const res = await batchReplayExecutions(selectedIds.value)
    const data = res.data || {}
    ElMessage.success(`重放完成：成功 ${data.success ?? 0} · 跳过 ${data.skipped ?? 0} · 失败 ${data.failed ?? 0}`)
    selectedRows.value = []
    await load()
  } finally {
    batchReplaying.value = false
  }
}

async function onReplay(row) {
  if (!row?.id) return
  const hint = row.errorMsg ? `\n上次失败：${row.errorMsg}` : ''
  if (!(await askConfirm(`按原入参重放「${row.executionNo}」？将生成新的执行单。${hint}`, '重放确认'))) return
  if (row._replaying !== undefined) {
    row._replaying = true
  } else {
    replaying.value = true
  }
  try {
    const res = await replayExecution(row.id)
    const nextNo = res.data?.instance?.executionNo || ''
    ElMessage.success(`重放完成 ${nextNo} · ${execStatusLabel(res.data?.instance?.status)}`)
    keyword.value = nextNo
    viewFilter.value = 'all'
    current.value = 1
    syncQuery()
    await load()
    if (res.data?.instance?.id) {
      const chain = await getExecutionChain(res.data.instance.id)
      chainDrawer.value?.setChain(chain.data)
    }
  } finally {
    if (row._replaying !== undefined) {
      row._replaying = false
    }
    replaying.value = false
  }
}

watch(
  () => [
    route.query.workflowId,
    route.query.triggerType,
    route.query.triggerAppId,
    route.query.status,
    route.query.keyword,
    route.query.page,
    route.query.filter,
    route.query.from,
    route.query.to,
    route.query.minDurationMs,
  ],
  () => {
    if (syncingQuery.value) return
    applyQuery()
    load()
  },
)

onMounted(async () => {
  applyQuery()
  if (!route.query.from && !route.query.to && !timeRange.value?.length) {
    timeRange.value = defaultTimeRange()
  }
  loading.value = true
  try {
    const [wfRes] = await Promise.all([
      pageWorkflows({ current: 1, size: 100 }),
      load(),
    ])
    workflows.value = wfRes.data?.records || []
    if (route.query.id) {
      chainDrawer.value?.open(Number(route.query.id))
    }
  } catch (error) {
    loadError.value = networkErrorMessage(error)
  }
})
</script>

<style scoped>
.execution-page { padding-bottom: 8px; }
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  margin-bottom: 14px;
}
.filter-date { width: 340px; }
.filter-wf { width: 180px; }
.filter-sm { width: 120px; }
.filter-ms { width: 128px; }
.filter-keyword { width: 220px; }
.toolbar-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.view-tabs {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  border-radius: 10px;
  background: var(--qz-fill);
  border: 1px solid var(--qz-border);
}
.view-tab {
  margin: 0;
  padding: 6px 14px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--qz-text-muted);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.view-tab:hover {
  color: var(--qz-text);
}
.view-tab.active {
  background: var(--qz-card);
  color: var(--qz-primary);
  box-shadow: var(--qz-shadow);
}
.batch-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.range-summary {
  margin: -4px 0 12px;
  font-size: 12px;
  color: var(--qz-text-muted);
}
.pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
}
.sub { color: var(--qz-text-muted); font-size: 12px; }
.err { color: var(--qz-danger); font-size: 12px; }
.muted { color: var(--qz-text-muted); }
.mono { font-family: ui-monospace, Menlo, monospace; font-size: 12px; }
@media (max-width: 1100px) {
  .filter-date,
  .filter-wf,
  .filter-keyword { width: 100%; max-width: 100%; }
  .filter-sm,
  .filter-ms { width: calc(50% - 4px); }
}
</style>
