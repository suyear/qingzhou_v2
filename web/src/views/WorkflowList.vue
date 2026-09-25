<template>
  <div class="workflow-page">
    <PageHeader title="工作流编排" desc="按顺序加步骤组成调用链；列表「编码」即开放调用用的 workflowCode，发布后可供调度或开放平台调用。">
      <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 120px" @change="reload">
        <el-option label="草稿" value="DRAFT" />
        <el-option label="已发布" value="PUBLISHED" />
        <el-option label="已停用" value="DISABLED" />
      </el-select>
      <el-input
        v-model="keyword"
        class="search-input"
        placeholder="搜索名称 / 编码"
        clearable
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-button @click="reload">查询</el-button>
      <el-button type="primary" @click="goCreate">新建工作流</el-button>
    </PageHeader>

    <PageState :error="loadError" @retry="boot" />

    <div v-if="showGuide" class="flow-strip">
      <div class="flow-step" :class="{ done: statTotal > 0 }">
        <span class="flow-badge">1</span>
        <div class="flow-body">
          <strong>添加步骤</strong>
            <p>从「添加接口」点选组件，配置参数组成调用链</p>
          <el-button type="primary" link @click="goCreate">新建工作流</el-button>
        </div>
      </div>
      <div class="flow-arrow">→</div>
      <div class="flow-step" :class="{ done: statPublished > 0 }">
        <span class="flow-badge">2</span>
        <div class="flow-body">
          <strong>试运行</strong>
          <p>设计器内验证调用链</p>
        </div>
      </div>
      <div class="flow-arrow">→</div>
      <div class="flow-step" :class="{ done: statPublished > 0 }">
        <span class="flow-badge">3</span>
        <div class="flow-body">
          <strong>发布</strong>
          <p>生成对外可用的快照</p>
        </div>
      </div>
      <div class="flow-arrow">→</div>
      <div class="flow-step">
        <span class="flow-badge">4</span>
        <div class="flow-body">
          <strong>调度 / 开放调用</strong>
          <p>定时任务或 API 触发</p>
          <el-button type="primary" link @click="$router.push('/schedules')">去调度</el-button>
          <el-button type="primary" link @click="$router.push('/openapi')">开放平台</el-button>
        </div>
      </div>
      <button type="button" class="flow-close" @click="dismissGuide">×</button>
    </div>

    <div v-else class="stat-grid">
      <button type="button" class="stat-card" :class="{ active: statusFilter === '' }" @click="filterByStatus('')">
        <div class="stat-label">全部</div>
        <div class="stat-num">{{ statTotal }}</div>
      </button>
      <button type="button" class="stat-card" :class="{ active: statusFilter === 'DRAFT' }" @click="filterByStatus('DRAFT')">
        <div class="stat-label">草稿</div>
        <div class="stat-num">{{ statDraft }}</div>
      </button>
      <button type="button" class="stat-card stat-ok" :class="{ active: statusFilter === 'PUBLISHED' }" @click="filterByStatus('PUBLISHED')">
        <div class="stat-label">已发布</div>
        <div class="stat-num">{{ statPublished }}</div>
        <div class="stat-hint">可供调度 / OpenAPI</div>
      </button>
      <button type="button" class="stat-card" :class="{ active: statusFilter === 'DISABLED' }" @click="filterByStatus('DISABLED')">
        <div class="stat-label">已停用</div>
        <div class="stat-num">{{ statDisabled }}</div>
      </button>
    </div>

    <div class="qz-panel">
      <el-table
        class="qz-table workflow-table"
        :data="records"
        v-loading="loading"
        stripe
        highlight-current-row
        @row-click="onRowClick"
      >
        <el-table-column label="名称" min-width="170">
          <template #default="{ row }">
            <div class="name-cell">
              <span class="wf-name">{{ row.workflowName }}</span>
              <span v-if="hasDraftChanges(row)" class="draft-tag">草稿有修改</span>
            </div>
            <div v-if="row.description" class="sub">{{ row.description }}</div>
          </template>
        </el-table-column>
        <el-table-column label="编码" min-width="150">
          <template #default="{ row }">
            <el-button type="primary" link class="mono" @click.stop="copyCode(row.workflowCode)">
              {{ row.workflowCode }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <StatusTag kind="workflow" :value="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="版本" width="72" align="center">
          <template #default="{ row }">v{{ row.version || 1 }}</template>
        </el-table-column>
        <el-table-column label="节点" width="72" align="center">
          <template #default="{ row }">{{ nodeCount(row) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="150">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <div class="qz-ops" @click.stop>
              <el-button type="primary" link @click="$router.push(`/designer/${row.id}`)">编排</el-button>
              <el-button
                v-if="row.status !== 'PUBLISHED' || hasDraftChanges(row)"
                type="primary"
                link
                @click="onPublish(row)"
              >
                {{ row.status === 'DISABLED' ? '重新发布' : '发布' }}
              </el-button>
              <el-dropdown trigger="click" @command="(cmd) => onRowCommand(cmd, row)">
                <el-button type="primary" link>更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="detail">查看详情</el-dropdown-item>
                    <el-dropdown-item command="records">运行结果</el-dropdown-item>
                    <el-dropdown-item command="problems">失败与超时</el-dropdown-item>
                    <el-dropdown-item v-if="row.status === 'PUBLISHED'" command="schedule">创建定时调度</el-dropdown-item>
                    <el-dropdown-item v-if="row.status === 'PUBLISHED'" command="openapi">开放 API 授权</el-dropdown-item>
                    <el-dropdown-item v-if="row.status !== 'DISABLED'" command="disable" divided>停用</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty v-if="!loading && !loadError" :description="emptyText">
            <el-button type="primary" @click="goCreate">新建工作流</el-button>
            <el-button @click="$router.push('/components')">先接入接口组件</el-button>
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
      <p v-if="records.length" class="table-foot">点击行查看详情 · 共 {{ total }} 条</p>
    </div>

    <!-- 详情抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="drawerRow?.workflowName || '工作流详情'"
      size="560px"
      class="qz-detail-drawer"
    >
      <template v-if="drawerRow">
        <div class="drawer-stack">
          <DetailSection title="基本信息">
            <div class="drawer-tags">
              <StatusTag kind="workflow" :value="drawerRow.status" />
              <el-tag v-if="hasDraftChanges(drawerRow)" size="small" type="warning">草稿有修改</el-tag>
              <el-tag size="small" type="info">v{{ drawerRow.version || 1 }}</el-tag>
            </div>
            <DetailCopyField label="编码" :value="drawerRow.workflowCode" copy-message="已复制编码" />
            <DetailMetaList class="drawer-meta" :items="drawerMetaItems" />
          </DetailSection>
          <LineagePanel v-if="drawerRow.id" type="workflow" :id="drawerRow.id" />
          <DetailActions>
            <el-button type="primary" @click="goDesigner(drawerRow)">继续编排</el-button>
            <el-button type="warning" @click="onPublish(drawerRow)">发布</el-button>
            <el-button @click="goRecords(drawerRow)">运行结果</el-button>
            <el-button @click="goProblems(drawerRow)">失败与超时</el-button>
            <el-button v-if="drawerRow.status === 'PUBLISHED'" @click="goSchedule(drawerRow)">创建调度</el-button>
            <el-button v-if="drawerRow.status === 'PUBLISHED'" @click="goOpenapi">开放授权</el-button>
          </DetailActions>
        </div>
      </template>
    </el-drawer>

    <!-- 发布成功 -->
    <el-dialog v-model="publishSuccessVisible" title="发布成功" width="480px">
      <p class="publish-msg">
        「<strong>{{ publishedRow?.workflowName }}</strong>」已发布为
        <strong>v{{ publishedRow?.version }}</strong>，调度和开放 API 将使用此快照。
      </p>
      <p class="hint">接下来你可以：</p>
      <div class="publish-actions">
        <el-button type="primary" @click="goSchedule(publishedRow); publishSuccessVisible = false">创建定时调度</el-button>
        <el-button @click="goOpenapi(); publishSuccessVisible = false">开放 API 授权</el-button>
        <el-button @click="goDesigner(publishedRow); publishSuccessVisible = false">继续编排</el-button>
      </div>
      <template #footer>
        <el-button @click="publishSuccessVisible = false">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import DetailSection from '@/components/detail/DetailSection.vue'
import DetailCopyField from '@/components/detail/DetailCopyField.vue'
import DetailMetaList from '@/components/detail/DetailMetaList.vue'
import DetailActions from '@/components/detail/DetailActions.vue'
import LineagePanel from '@/components/LineagePanel.vue'
import { askConfirm } from '@/utils/confirm'
import { copyText, formatTime } from '@/utils/format'
import { disableWorkflow, pageWorkflows, publishWorkflow } from '@/api/workflow'
import { networkErrorMessage } from '@/api/http'
import PageState from '@/components/PageState.vue'

const GUIDE_KEY = 'qz-workflow-guide-dismissed'

const route = useRoute()
const router = useRouter()
const showGuide = ref(localStorage.getItem(GUIDE_KEY) !== '1')
const records = ref([])
const statRecords = ref([])
const loading = ref(false)
const loadError = ref('')
const keyword = ref('')
const statusFilter = ref('')
const total = ref(0)
const current = ref(1)
const size = ref(10)
const drawerVisible = ref(false)
const drawerRow = ref(null)
const publishSuccessVisible = ref(false)
const publishedRow = ref(null)
const syncingQuery = ref(false)

const statTotal = computed(() => statRecords.value.length)
const statDraft = computed(() => statRecords.value.filter((item) => item.status === 'DRAFT').length)
const statPublished = computed(() => statRecords.value.filter((item) => item.status === 'PUBLISHED').length)
const statDisabled = computed(() => statRecords.value.filter((item) => item.status === 'DISABLED').length)
const emptyText = computed(() => {
  if (keyword.value || statusFilter.value) return '没有匹配的工作流'
  return '还没有工作流，打开设计器后从「添加接口」点选第一步'
})
const drawerMetaItems = computed(() => {
  const row = drawerRow.value
  if (!row) return []
  return [
    { label: '节点数', value: nodeCount(row) },
    { label: '更新时间', value: formatTime(row.updateTime) },
    { label: '最近发布', value: formatTime(row.publishTime), hidden: !row.publishTime },
    { label: '说明', value: row.description, hidden: !row.description },
  ]
})

function dismissGuide() {
  showGuide.value = false
  localStorage.setItem(GUIDE_KEY, '1')
}

function applyQuery() {
  keyword.value = route.query.keyword || ''
  statusFilter.value = route.query.status || ''
  current.value = route.query.page ? Number(route.query.page) : 1
}

function syncQuery() {
  const query = {}
  if (keyword.value) query.keyword = keyword.value
  if (statusFilter.value) query.status = statusFilter.value
  if (current.value > 1) query.page = String(current.value)
  syncingQuery.value = true
  router.replace({ path: '/workflows', query }).finally(() => {
    syncingQuery.value = false
  })
}

async function loadStats() {
  const res = await pageWorkflows({ current: 1, size: 100 })
  statRecords.value = res.data?.records || []
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await pageWorkflows({
      current: current.value,
      size: size.value,
      keyword: keyword.value,
      status: statusFilter.value || undefined,
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

function reload() {
  current.value = 1
  syncQuery()
  load()
}

function onPageChange() {
  syncQuery()
  load()
}

function filterByStatus(status) {
  statusFilter.value = status
  reload()
}

function nodeCount(row) {
  try {
    const graph = typeof row.graphJson === 'string' ? JSON.parse(row.graphJson) : row.graphJson
    return graph?.nodes?.length || 0
  } catch {
    return 0
  }
}

function hasDraftChanges(row) {
  if (row.status !== 'PUBLISHED' || !row.publishTime || !row.updateTime) return false
  return String(row.updateTime) > String(row.publishTime)
}

function onRowClick(row) {
  drawerRow.value = row
  drawerVisible.value = true
}

function goCreate() {
  router.push('/designer')
}

function goDesigner(row) {
  if (!row?.id) return
  drawerVisible.value = false
  router.push(`/designer/${row.id}`)
}

function goRecords(row) {
  router.push({ path: '/executions', query: { workflowId: String(row.id) } })
}

function goProblems(row) {
  router.push({ path: '/executions', query: { workflowId: String(row.id), filter: 'problem' } })
}

function goSchedule(row) {
  const query = row?.id ? { workflowId: String(row.id) } : {}
  router.push({ path: '/schedules', query })
}

function goOpenapi() {
  router.push({ path: '/openapi', query: { tab: 'apps' } })
}

async function copyCode(code) {
  await copyText(code)
  ElMessage.success('已复制编码')
}

async function onPublish(row) {
  const action = row.status === 'DISABLED' ? '重新发布' : '发布'
  if (!(await askConfirm(`${action}「${row.workflowName}」？调度和开放调用将使用新快照。`, '发布确认'))) return
  const res = await publishWorkflow(row.id)
  ElMessage.success('已发布')
  publishedRow.value = { ...row, version: res.data?.version || row.version }
  publishSuccessVisible.value = true
  drawerVisible.value = false
  await Promise.all([load(), loadStats()])
}

async function onDisable(row) {
  if (!(await askConfirm(`停用「${row.workflowName}」后，调度和开放调用将无法执行。`, '停用确认'))) return
  await disableWorkflow(row.id)
  ElMessage.success('已停用')
  await Promise.all([load(), loadStats()])
}

function onRowCommand(command, row) {
  if (command === 'detail') return onRowClick(row)
  if (command === 'records') return goRecords(row)
  if (command === 'problems') return goProblems(row)
  if (command === 'schedule') return goSchedule(row)
  if (command === 'openapi') return goOpenapi()
  if (command === 'disable') return onDisable(row)
}

watch(
  () => [route.query.keyword, route.query.status, route.query.page],
  () => {
    if (syncingQuery.value) return
    applyQuery()
    load()
  },
)

onMounted(async () => {
  applyQuery()
  await boot()
})

async function boot() {
  loadError.value = ''
  try {
    await Promise.all([load(), loadStats()])
  } catch (error) {
    loadError.value = networkErrorMessage(error)
  }
}
</script>

<style scoped>
.flow-strip {
  position: relative;
  display: flex;
  align-items: stretch;
  gap: 8px;
  margin-bottom: 14px;
  padding: 14px 36px 14px 14px;
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: var(--qz-radius);
  background: var(--qz-primary-soft);
}
.flow-step { flex: 1; display: flex; gap: 10px; min-width: 0; }
.flow-step.done .flow-badge { background: var(--el-color-success); }
.flow-badge {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.flow-body strong { display: block; font-size: 14px; margin-bottom: 2px; }
.flow-body p { margin: 0; font-size: 12px; color: var(--qz-text-muted); line-height: 1.4; }
.flow-arrow { flex-shrink: 0; align-self: center; color: var(--qz-text-muted); font-size: 18px; }
.flow-close {
  position: absolute;
  top: 8px;
  right: 10px;
  border: none;
  background: transparent;
  font-size: 20px;
  color: var(--qz-text-muted);
  cursor: pointer;
}
.workflow-table :deep(.el-table__row) { cursor: pointer; }
.name-cell { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.wf-name { font-weight: 600; }
.draft-tag { font-size: 11px; color: var(--el-color-warning); }
.sub { margin-top: 4px; font-size: 12px; color: var(--qz-text-muted); }
.mono { font-family: ui-monospace, Menlo, monospace; font-size: 12px; }
.table-foot {
  margin: 0 16px 12px;
  font-size: 12px;
  color: var(--qz-text-muted);
}
.drawer-stack { display: flex; flex-direction: column; gap: 12px; }
.drawer-tags { display: flex; gap: 8px; margin-bottom: 14px; flex-wrap: wrap; }
.drawer-meta { margin-top: 14px; }
.publish-msg { margin: 0 0 12px; line-height: 1.6; }
.publish-actions { display: flex; flex-direction: column; gap: 8px; }
.hint { color: var(--qz-text-muted); font-size: 13px; margin: 0 0 10px; }
@media (max-width: 900px) {
  .flow-strip { flex-direction: column; padding-right: 14px; }
  .flow-arrow { display: none; }
}
</style>
