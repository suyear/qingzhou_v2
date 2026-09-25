<template>
  <div>
    <PageHeader title="工作台" desc="看看今天跑得怎样，以及接下来该做什么。">
      <template v-if="chartsExpanded">
        <el-radio-group v-model="days" size="small" @change="loadDashboard">
          <el-radio-button :value="7">近 7 日</el-radio-button>
          <el-radio-button :value="14">近 14 日</el-radio-button>
          <el-radio-button :value="30">近 30 日</el-radio-button>
        </el-radio-group>
      </template>
    </PageHeader>
    <PageState :error="loadError" @retry="load" />

    <div class="stat-grid" v-loading="loading && !overview">
      <button class="stat-card" @click="$router.push('/components')">
        <div class="stat-label">接口组件</div>
        <div class="stat-num">{{ displayNum(summary.components) }}</div>
      </button>
      <button class="stat-card" @click="$router.push('/workflows')">
        <div class="stat-label">工作流</div>
        <div class="stat-num">{{ displayNum(summary.workflows) }}</div>
      </button>
      <button class="stat-card" @click="$router.push('/executions')">
        <div class="stat-label">今日执行</div>
        <div class="stat-num">{{ displayNum(summary.todayExecutions) }}</div>
        <div class="stat-hint">{{ successHint }}</div>
      </button>
      <button
        class="stat-card"
        :class="summary.rangeFailed ? '' : 'stat-ok'"
        @click="$router.push('/executions?filter=problem')"
      >
        <div class="stat-label">区间失败 / 超时</div>
        <div class="stat-num">{{ displayNum(summary.rangeFailed) }}</div>
        <div class="stat-hint">{{ durationHint }}</div>
      </button>
    </div>

    <!-- 空态：三步旅程 -->
    <div v-if="!loading && !loadError && isEmptyJourney" class="journey-grid">
      <button type="button" class="journey-card" @click="$router.push('/components')">
        <span class="journey-step">①</span>
        <strong>接入接口</strong>
        <p>填地址或写数据库脚本，试连通后保存</p>
      </button>
      <button type="button" class="journey-card" @click="$router.push('/designer')">
        <span class="journey-step">②</span>
        <strong>编成流程</strong>
        <p>按顺序加步骤，试跑通过再发布</p>
      </button>
      <button type="button" class="journey-card" @click="$router.push('/schedules')">
        <span class="journey-step">③</span>
        <strong>定时或开放</strong>
        <p>设每天几点跑，或给外部应用调用</p>
      </button>
    </div>

    <!-- 有数据：下一步 + 告警 -->
    <template v-else>
      <div class="next-section">
        <h3 class="section-title">下一步</h3>
        <div class="next-actions">
          <el-button type="primary" @click="$router.push('/components')">接入组件</el-button>
          <el-button @click="$router.push('/designer')">新建编排</el-button>
          <el-button @click="$router.push('/schedules')">定时调度</el-button>
          <el-button @click="$router.push('/openapi')">开放平台</el-button>
          <el-button v-if="summary.rangeFailed > 0" type="warning" plain @click="$router.push('/executions?filter=problem')">
            查看失败
          </el-button>
        </div>
      </div>

      <el-alert
        v-if="!loading && !loadError && summary.rangeFailed > 0"
        class="guide-alert"
        type="warning"
        show-icon
        :closable="false"
        :title="`近 ${days} 日有 ${summary.rangeFailed} 次失败或超时`"
        description="打开运行结果里的「失败与超时」，从触发来源点到出错节点。"
      >
        <el-button type="primary" size="small" @click="$router.push('/executions?filter=problem')">去查看</el-button>
      </el-alert>
    </template>

    <!-- 运行分析：默认折叠 -->
    <div v-if="!isEmptyJourney" class="analysis-block">
      <button type="button" class="analysis-toggle" @click="chartsExpanded = !chartsExpanded">
        <span>运行分析</span>
        <span class="analysis-meta">近 {{ days }} 日 · {{ chartsExpanded ? '收起' : '展开图表' }}</span>
      </button>

      <div v-show="chartsExpanded" class="analysis-body">
        <div class="analysis-toolbar">
          <el-radio-group v-model="days" size="small" @change="loadDashboard">
            <el-radio-button :value="7">近 7 日</el-radio-button>
            <el-radio-button :value="14">近 14 日</el-radio-button>
            <el-radio-button :value="30">近 30 日</el-radio-button>
          </el-radio-group>
        </div>
        <div class="qz-chart-grid">
          <ChartPanel
            title="执行次数趋势"
            :desc="`近 ${days} 日工作流执行量`"
            :option="trendChart"
            :loading="chartLoading"
            :error="chartError"
            :empty="!hasTrend"
            empty-text="这段时间还没有执行记录"
            @retry="loadDashboard"
          />
          <ChartPanel
            title="失败率趋势"
            desc="已结束执行中失败+超时的占比"
            :option="failRateChart"
            :loading="chartLoading"
            :error="chartError"
            :empty="!hasFailRate"
            empty-text="暂无已结束的执行，无法计算失败率"
            @retry="loadDashboard"
          />
          <ChartPanel
            title="成功 / 失败占比"
            desc="仅统计已结束的执行"
            :option="statusChart"
            :loading="chartLoading"
            :error="chartError"
            :empty="!hasStatus"
            empty-text="暂无成功或失败数据"
            @retry="loadDashboard"
          />
          <ChartPanel
            title="触发类型分布"
            desc="试运行 / 调度 / 开放调用"
            :option="triggerChart"
            :loading="chartLoading"
            :error="chartError"
            :empty="!hasTrigger"
            empty-text="暂无触发记录"
            @retry="loadDashboard"
          />
          <ChartPanel
            title="失败工作流 Top"
            desc="区间内失败 / 超时次数最多"
            :option="failWorkflowChart"
            :loading="chartLoading"
            :error="chartError"
            :empty="!hasFailWorkflows"
            empty-text="这段时间没有失败的工作流"
            @retry="loadDashboard"
          />
          <ChartPanel
            title="失败组件 Top"
            desc="节点日志中失败次数最多的接口"
            :option="failComponentChart"
            :loading="chartLoading"
            :error="chartError"
            :empty="!hasFailComponents"
            empty-text="这段时间没有失败的组件节点"
            @retry="loadDashboard"
          />
          <ChartPanel
            title="调度任务健康"
            :desc="scheduleDesc"
            :option="scheduleChart"
            :loading="chartLoading"
            :error="chartError"
            :empty="!hasScheduleHealth"
            empty-text="还没有调度任务"
            @retry="loadDashboard"
          />
          <section class="qz-chart-panel">
            <div class="qz-chart-panel__head">
              <div>
                <h3>执行耗时</h3>
                <p class="sub">已结束执行的平均耗时与 P95</p>
              </div>
            </div>
            <div v-if="chartError" class="qz-chart-panel__state">
              <el-empty :image-size="56" :description="chartError">
                <el-button size="small" type="primary" @click="loadDashboard">重新加载</el-button>
              </el-empty>
            </div>
            <div v-else-if="!hasDuration && !chartLoading" class="qz-chart-panel__state">
              <el-empty :image-size="56" description="还没有带耗时的结束执行" />
            </div>
            <div v-else class="duration-body" v-loading="chartLoading">
              <div class="duration-item">
                <div class="stat-label">平均耗时</div>
                <div class="stat-num">{{ durationText(duration.avgMs) }}</div>
              </div>
              <div class="duration-item">
                <div class="stat-label">P95</div>
                <div class="stat-num">{{ durationText(duration.p95Ms) }}</div>
              </div>
              <p class="muted">样本 {{ duration.sampleCount || 0 }} 次</p>
            </div>
          </section>
        </div>
      </div>
    </div>

    <div v-if="!isEmptyJourney" class="recent-grid">
      <div class="qz-panel recent">
        <div class="recent-head">
          <div>
            <h3>最近失败</h3>
            <p class="sub">点进去看完整链路：触发 → 工作流 → 出错节点</p>
          </div>
          <el-button type="primary" link @click="$router.push('/executions?filter=problem')">失败与超时</el-button>
        </div>
        <el-table
          v-if="failures.length"
          class="qz-table"
          :data="failures"
          stripe
          style="cursor: pointer"
          @row-click="openChain"
        >
          <el-table-column prop="executionNo" label="执行单号" min-width="160" />
          <el-table-column label="工作流" min-width="120">
            <template #default="{ row }">{{ row.workflowName || row.workflowId }}</template>
          </el-table-column>
          <el-table-column label="摘要" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.errorMsg" class="err">{{ row.errorMsg }}</span>
              <span v-else class="muted">打开链路查看节点报文</span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else-if="!loading && !loadError" description="最近没有失败执行">
          <el-button type="primary" link @click="$router.push('/executions')">查看全部记录</el-button>
        </el-empty>
      </div>

      <div class="qz-panel recent">
        <div class="recent-head">
          <div>
            <h3>最近运行</h3>
            <p class="sub">点击行打开执行链路</p>
          </div>
          <el-button type="primary" link @click="$router.push('/executions')">全部记录</el-button>
        </div>
        <el-table
          v-if="recent.length"
          class="qz-table"
          :data="recent"
          stripe
          style="cursor: pointer"
          @row-click="openChain"
        >
          <el-table-column prop="executionNo" label="执行单号" min-width="160" />
          <el-table-column label="工作流" min-width="120">
            <template #default="{ row }">{{ row.workflowName || row.workflowId }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <StatusTag kind="exec" :value="row.status" />
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else-if="!loading && !loadError" description="还没有执行记录，去设计器试运行一条工作流">
          <el-button type="primary" @click="$router.push('/workflows')">去编排</el-button>
        </el-empty>
      </div>
    </div>

    <ExecutionChainDrawer ref="chainDrawer" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import PageState from '@/components/PageState.vue'
import ChartPanel from '@/components/ChartPanel.vue'
import StatusTag from '@/components/StatusTag.vue'
import ExecutionChainDrawer from '@/components/ExecutionChainDrawer.vue'
import { getDashboardOverview } from '@/api/dashboard'
import { pageExecutions } from '@/api/execution'
import { networkErrorMessage } from '@/api/http'
import { durationText } from '@/utils/format'
import {
  STATUS_COLOR,
  TRIGGER_COLOR,
  barOption,
  failRankBarOption,
  failRateOption,
  hasChartData,
  jobHealthOption,
  sharePieOption,
  trendOption,
} from '@/utils/charts'

const days = ref(7)
const loading = ref(false)
const chartLoading = ref(false)
const loadError = ref('')
const chartError = ref('')
const recent = ref([])
const failures = ref([])
const overview = ref(null)
const chainDrawer = ref(null)
const chartsExpanded = ref(false)

const summary = computed(() => overview.value?.summary || {
  components: null,
  workflows: null,
  todayExecutions: null,
  runningJobs: null,
  successRate: null,
  rangeTotal: 0,
  rangeFailed: 0,
})

const duration = computed(() => overview.value?.duration || { sampleCount: 0, avgMs: null, p95Ms: null })
const scheduleHealth = computed(() => overview.value?.scheduleHealth || {
  total: 0,
  running: 0,
  stopped: 0,
  recentTriggers: 0,
  recentSuccess: 0,
  recentFailed: 0,
})

const isEmptyJourney = computed(() => {
  const s = summary.value
  return !s.components && !s.workflows && !s.todayExecutions && !s.rangeTotal
})

const successHint = computed(() => {
  const rate = summary.value.successRate
  if (rate == null) return '区间成功率 —'
  return `近 ${days.value} 日成功率 ${rate}%`
})

const durationHint = computed(() => {
  if (!duration.value.sampleCount) return '点击查看失败与超时'
  return `P95 ${durationText(duration.value.p95Ms)}`
})

const scheduleDesc = computed(() => {
  const health = scheduleHealth.value
  if (!health.recentTriggers) return '运行中 / 已停止'
  return `近 ${days.value} 日调度触发成功 ${health.recentSuccess} / 失败 ${health.recentFailed}`
})

const hasTrend = computed(() => hasChartData(overview.value?.trend || [], 'total'))
const hasFailRate = computed(() => (overview.value?.trend || []).some((item) => item.failRate != null))
const hasStatus = computed(() => hasChartData(overview.value?.statusShare))
const hasTrigger = computed(() => hasChartData(overview.value?.triggerShare))
const hasFailWorkflows = computed(() => hasChartData(overview.value?.topFailedWorkflows || [], 'failedCount'))
const hasFailComponents = computed(() => hasChartData(overview.value?.topFailedComponents || [], 'failedCount'))
const hasScheduleHealth = computed(() => Number(scheduleHealth.value.total || 0) > 0)
const hasDuration = computed(() => Number(duration.value.sampleCount || 0) > 0)

const trendChart = computed(() => trendOption(overview.value?.trend || []))
const failRateChart = computed(() => failRateOption(overview.value?.trend || []))
const statusChart = computed(() => sharePieOption(overview.value?.statusShare || [], STATUS_COLOR))
const triggerChart = computed(() => barOption(overview.value?.triggerShare || [], TRIGGER_COLOR))
const failWorkflowChart = computed(() => failRankBarOption(overview.value?.topFailedWorkflows || []))
const failComponentChart = computed(() => failRankBarOption(overview.value?.topFailedComponents || []))
const scheduleChart = computed(() => jobHealthOption(scheduleHealth.value))

function displayNum(value) {
  return value == null ? '—' : value
}

function openChain(row) {
  if (row?.id) chainDrawer.value?.open(row.id)
}

async function loadDashboard() {
  chartLoading.value = true
  chartError.value = ''
  try {
    const res = await getDashboardOverview({ days: days.value })
    overview.value = res.data || null
  } catch (error) {
    chartError.value = networkErrorMessage(error)
    overview.value = null
  } finally {
    chartLoading.value = false
  }
}

async function loadRecent() {
  const [execs, failed] = await Promise.all([
    pageExecutions({ current: 1, size: 5 }),
    pageExecutions({ current: 1, size: 5, problem: true }),
  ])
  recent.value = execs.data?.records || []
  failures.value = failed.data?.records || []
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    await Promise.all([loadDashboard(), loadRecent()])
    if (chartError.value) {
      loadError.value = chartError.value
    }
  } catch (error) {
    loadError.value = networkErrorMessage(error)
    recent.value = []
    failures.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.section-title {
  margin: 0 0 10px;
  font-size: 14px;
  font-weight: 650;
  color: var(--qz-text);
}
.next-section {
  margin-bottom: 16px;
}
.next-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.guide-alert {
  margin-bottom: 16px;
}
.journey-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}
.journey-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 20px 18px;
  border: 1px solid var(--qz-border);
  border-radius: var(--qz-radius);
  background: var(--qz-panel, #fff);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.journey-card:hover {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 1px var(--el-color-primary-light-7);
}
.journey-step {
  font-size: 18px;
  font-weight: 700;
  color: var(--el-color-primary);
}
.journey-card strong {
  font-size: 15px;
  color: var(--qz-text);
}
.journey-card p {
  margin: 0;
  font-size: 13px;
  line-height: 1.45;
  color: var(--el-text-color-secondary);
}
.analysis-block {
  margin-bottom: 16px;
}
.analysis-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 12px 16px;
  border: 1px solid var(--qz-border);
  border-radius: var(--qz-radius-sm);
  background: var(--qz-fill);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  color: var(--qz-text);
}
.analysis-toggle:hover {
  border-color: var(--el-color-primary-light-5);
}
.analysis-meta {
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}
.analysis-body {
  margin-top: 12px;
}
.analysis-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}
.recent-grid {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 12px;
}
.recent-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 16px 16px 8px;
}
.recent :deep(.el-empty) {
  padding: 24px 16px 32px;
}
.recent-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 650;
}
.recent-head .sub {
  margin: 4px 0 0;
}
.duration-body {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  align-content: center;
  min-height: 220px;
  padding: 8px 8px 16px;
}
.duration-item {
  padding: 12px;
  border: 1px solid var(--qz-border);
  border-radius: var(--qz-radius-sm);
  background: var(--qz-fill);
}
.err {
  color: var(--qz-danger);
  font-size: 12px;
}
@media (max-width: 1100px) {
  .recent-grid,
  .journey-grid {
    grid-template-columns: 1fr;
  }
}
</style>
