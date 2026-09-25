<template>
  <div>
    <PageHeader title="运行结果" desc="试运行、调度、开放调用都会落在这里。失败时可点开链路，看卡在哪一步。">
      <el-select v-model="workflowId" placeholder="全部工作流" clearable class="search-input" @change="onFilterChange">
        <el-option
          v-for="wf in workflows"
          :key="wf.id"
          :label="wf.workflowName"
          :value="wf.id"
        />
      </el-select>
      <el-select v-model="triggerType" placeholder="触发方式" clearable style="width: 140px" @change="onFilterChange">
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
        style="width: 120px"
        @change="onFilterChange"
      >
        <el-option label="成功" value="SUCCESS" />
        <el-option label="失败" value="FAILED" />
        <el-option label="超时" value="TIMEOUT" />
        <el-option label="运行中" value="RUNNING" />
      </el-select>
      <el-input
        v-model="keyword"
        class="search-input"
        placeholder="执行单号"
        clearable
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-button @click="reload">查询</el-button>
    </PageHeader>
    <PageState :error="loadError" @retry="load" />

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

    <div v-if="triggerAppId" class="qz-filter-chip">
      仅看待定开放应用的调用
      <el-button type="primary" link @click="clearAppFilter">清除</el-button>
    </div>
    <div class="qz-panel">
    <el-table class="qz-table" :data="records" v-loading="loading" stripe highlight-current-row @row-click="openDetail">
      <el-table-column label="执行单号" min-width="200">
        <template #default="{ row }">
          <el-button type="primary" link @click.stop="openDetail(row)">{{ row.executionNo }}</el-button>
          <div v-if="viewFilter === 'problem'" class="sub">{{ formatTime(row.startTime) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="工作流" min-width="160">
        <template #default="{ row }">
          {{ row.workflowName || row.workflowId }}
          <div class="sub">v{{ row.workflowVersion }}</div>
        </template>
      </el-table-column>
      <el-table-column label="触发" width="120">
        <template #default="{ row }">
          <StatusTag kind="trigger" :value="row.triggerType" />
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <StatusTag kind="exec" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="耗时" width="90">
        <template #default="{ row }">{{ durationText(row.durationMs) }}</template>
      </el-table-column>
      <el-table-column :label="viewFilter === 'problem' ? '失败原因' : '说明'" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.errorMsg" class="err">{{ row.errorMsg }}</span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column v-if="viewFilter === 'all'" label="开始时间" min-width="170">
        <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <div class="qz-ops" @click.stop>
            <el-button type="primary" link @click="openDetail(row)">查看详情</el-button>
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
import { getExecutionChain, pageExecutions, replayExecution } from '@/api/execution'
import { networkErrorMessage } from '@/api/http'
import { durationText, execStatusLabel, formatTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const records = ref([])
const workflows = ref([])
const total = ref(0)
const current = ref(1)
const size = ref(10)
const loading = ref(false)
const loadError = ref('')
const keyword = ref(route.query.keyword || '')
const workflowId = ref(route.query.workflowId ? Number(route.query.workflowId) : null)
const triggerType = ref(route.query.triggerType || '')
const triggerAppId = ref(route.query.triggerAppId ? Number(route.query.triggerAppId) : null)
const status = ref(route.query.status || '')
const viewFilter = ref(route.query.filter === 'problem' ? 'problem' : 'all')
const replaying = ref(false)
const syncingQuery = ref(false)
const chainDrawer = ref(null)

const emptyText = computed(() => {
  if (keyword.value || workflowId.value || triggerType.value || status.value || triggerAppId.value) {
    return viewFilter.value === 'problem' ? '没有匹配的失败执行' : '没有匹配的执行记录'
  }
  return viewFilter.value === 'problem'
    ? '最近没有失败或超时，链路是通的'
    : '还没有执行记录'
})

function applyQuery() {
  workflowId.value = route.query.workflowId ? Number(route.query.workflowId) : null
  triggerType.value = route.query.triggerType || ''
  triggerAppId.value = route.query.triggerAppId ? Number(route.query.triggerAppId) : null
  status.value = route.query.status || ''
  keyword.value = route.query.keyword || ''
  viewFilter.value = route.query.filter === 'problem' ? 'problem' : 'all'
  current.value = route.query.page ? Number(route.query.page) : 1
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
    const res = await pageExecutions({
      current: current.value,
      size: size.value,
      keyword: keyword.value,
      workflowId: workflowId.value || undefined,
      triggerType: triggerType.value || undefined,
      triggerAppId: triggerAppId.value || undefined,
      status: viewFilter.value === 'all' ? (status.value || undefined) : undefined,
      problem: viewFilter.value === 'problem' ? true : undefined,
    })
    records.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
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
  ],
  () => {
    if (syncingQuery.value) return
    applyQuery()
    load()
  },
)

onMounted(async () => {
  applyQuery()
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
.view-tabs {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  margin-bottom: 14px;
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
.sub { color: var(--qz-text-muted); font-size: 12px; }
.err { color: var(--qz-danger); font-size: 12px; }
.muted { color: var(--qz-text-muted); }
</style>
