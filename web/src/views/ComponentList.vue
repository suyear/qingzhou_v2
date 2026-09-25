<template>
  <div>
    <PageHeader title="接口组件" desc="把外部 HTTP 接口或数据库脚本封装成可复用积木；列表「编码」即开放调用用的 componentCode。">
      <el-radio-group v-model="viewMode" size="small" class="view-toggle">
        <el-radio-button value="table">列表</el-radio-button>
        <el-radio-button value="card">卡片</el-radio-button>
      </el-radio-group>
      <el-dropdown v-if="canWrite" split-button type="primary" @click="openCreate('easy')" @command="openCreate">
        接入接口
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="easy">接入接口（推荐）</el-dropdown-item>
            <el-dropdown-item command="sql">数据库脚本</el-dropdown-item>
            <el-dropdown-item command="curl">从 curl 导入</el-dropdown-item>
            <el-dropdown-item command="health">先体验一下</el-dropdown-item>
            <el-dropdown-item command="manual" divided>高级手动配置</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </PageHeader>

    <PageState :error="loadError" @retry="load" />

    <div v-if="!showGuide && componentStats" class="stat-grid cols-3">
      <button type="button" class="stat-card clickable" :class="{ active: !category && !presetFilter }" @click="setQuickFilter({})">
        <div class="stat-label">全部组件</div>
        <div class="stat-num">{{ componentStats.total ?? 0 }}</div>
      </button>
      <button type="button" class="stat-card clickable" :class="{ active: category === 'HTTP' }" @click="setQuickFilter({ category: 'HTTP' })">
        <div class="stat-label">HTTP 接口</div>
        <div class="stat-num">{{ componentStats.httpCount ?? 0 }}</div>
      </button>
      <button type="button" class="stat-card clickable" :class="{ active: category === 'DATABASE' }" @click="setQuickFilter({ category: 'DATABASE' })">
        <div class="stat-label">数据库脚本</div>
        <div class="stat-num">{{ componentStats.databaseCount ?? 0 }}</div>
      </button>
    </div>

    <el-alert
      v-if="showGuide"
      class="guide-alert"
      type="info"
      show-icon
      closable
      @close="dismissGuide"
    >
      <template #title>外部接口接入 · 推荐流程</template>
      <div class="guide-steps">
        <span>① 新建组件，<strong>填地址</strong>、<strong>粘贴 curl</strong> 或写<strong>数据库脚本</strong></span>
        <span>② 确认信息后点<strong>试连通</strong></span>
        <span>③ 在工作流设计器左侧组件库拖入画布</span>
        <el-button v-if="canWrite" class="guide-action" type="primary" size="small" @click="openCreate('easy')">立即接入</el-button>
      </div>
    </el-alert>

    <div class="filter-bar qz-panel">
      <div class="chip-row">
        <button
          type="button"
          class="chip"
          :class="{ active: !category }"
          @click="setQuickFilter({ category: '' })"
        >全部类型</button>
        <button
          v-for="item in categoryOptions"
          :key="item.value"
          type="button"
          class="chip"
          :class="{ active: category === item.value }"
          @click="setQuickFilter({ category: item.value })"
        >{{ item.label }}</button>
        <span class="chip-sep" />
        <button type="button" class="chip" :class="{ active: presetFilter === '0' }" @click="setQuickFilter({ preset: presetFilter === '0' ? '' : '0' })">我创建的</button>
        <button type="button" class="chip" :class="{ active: presetFilter === '1' }" @click="setQuickFilter({ preset: presetFilter === '1' ? '' : '1' })">系统预置</button>
      </div>
      <div class="filter-actions">
        <el-select
          v-if="category !== 'DATABASE'"
          v-model="httpMethod"
          placeholder="请求方式"
          clearable
          style="width: 130px"
          @change="reload"
        >
          <el-option v-for="m in methods" :key="m" :label="methodFilterLabel(m)" :value="m" />
        </el-select>
        <el-input
          v-model="keyword"
          class="search-input"
          placeholder="搜索名称 / 编码 / 地址"
          clearable
          @keyup.enter="reload"
          @clear="reload"
          @input="onKeywordInput"
        />
        <el-button @click="reload">查询</el-button>
        <el-button v-if="hasListFilters" link type="primary" @click="clearFilters">清除筛选</el-button>
      </div>
    </div>

    <div v-if="viewMode === 'card'" class="card-grid" v-loading="loading">
      <button
        v-for="row in records"
        :key="row.id"
        type="button"
        class="comp-card"
        @click="openDetail(row)"
      >
        <div class="comp-card-top">
          <el-tag size="small" :type="httpMethodTagType(row.httpMethod)">{{ methodLabel(row) }}</el-tag>
          <el-tag size="small" type="info">{{ categoryLabel(row.category) }}</el-tag>
          <el-tag v-if="row.isPreset" size="small" type="warning">预置</el-tag>
        </div>
        <div class="comp-card-title">{{ row.componentName }}</div>
        <div class="comp-card-code mono" @click.stop="onCopy(row.componentCode, '已复制编码')">{{ row.componentCode }}</div>
        <div class="comp-card-path mono">{{ displayPath(row) }}</div>
        <div class="comp-card-foot">
          <span v-if="paramStats(row).total">入参 {{ paramStats(row).required }}/{{ paramStats(row).total }}</span>
          <span v-else class="muted">无入参</span>
          <span v-if="authSummary(row) || needsAccessToken(row)" class="auth-hint">
            {{ needsAccessToken(row) ? 'Token' : authSummary(row) }}
          </span>
        </div>
        <div class="comp-card-ops" @click.stop>
          <el-button type="primary" link size="small" @click="openTest(row)">
            {{ isDatabaseComponent(row) ? '试运行' : '试连通' }}
          </el-button>
          <el-button v-if="canWrite" type="primary" link size="small" @click="openEdit(row)">编辑</el-button>
        </div>
      </button>
      <el-empty v-if="!loading && !loadError && !records.length" :description="emptyText">
        <el-button v-if="canWrite" type="primary" @click="openCreate('easy')">新建组件</el-button>
      </el-empty>
    </div>

    <div v-else class="qz-panel">
      <el-table class="qz-table is-clickable" :data="records" v-loading="loading" stripe highlight-current-row @row-click="openDetail">
        <el-table-column label="名称" min-width="150">
          <template #default="{ row }">
            <div class="name-cell">
              <span>{{ row.componentName }}</span>
              <el-tag v-if="needsAccessToken(row)" size="small" type="warning" class="token-tag">Token</el-tag>
              <el-tag v-else-if="authSummary(row)" size="small" type="info" class="token-tag">{{ authSummary(row) }}</el-tag>
            </div>
            <div v-if="row.description" class="sub">{{ row.description }}</div>
          </template>
        </el-table-column>
        <el-table-column label="编码" min-width="180">
          <template #default="{ row }">
            <el-button type="primary" link class="mono" @click.stop="onCopy(row.componentCode, '已复制编码')">
              {{ row.componentCode }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="110">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ categoryLabel(row.category) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="方式" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="httpMethodTagType(row.httpMethod)">{{ methodLabel(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="路径" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono">{{ displayPath(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="入参" width="120">
          <template #default="{ row }">
            <span v-if="paramStats(row).total">
              必填 {{ paramStats(row).required }} / 共 {{ paramStats(row).total }}
            </span>
            <span v-else class="muted">无</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="90">
          <template #default="{ row }">
            <el-tag :type="row.isPreset ? 'warning' : 'info'" size="small">{{ row.isPreset ? '预置' : '自定义' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <div class="qz-ops" @click.stop>
              <el-button type="primary" link @click="openDetail(row)">详情</el-button>
              <el-button type="primary" link @click="openTest(row)">{{ isDatabaseComponent(row) ? '试运行' : '试连通' }}</el-button>
              <el-dropdown v-if="canWrite" trigger="click" @command="(cmd) => onRowCommand(cmd, row)">
                <el-button type="primary" link>更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="edit">编辑</el-dropdown-item>
                    <el-dropdown-item v-if="!row.isPreset" command="clone">复制</el-dropdown-item>
                    <el-dropdown-item v-if="!row.isPreset" command="delete" divided>删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty v-if="!loading && !loadError" :description="emptyText">
            <el-button v-if="canWrite" type="primary" @click="openCreate('easy')">新建组件</el-button>
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
          @current-change="load"
        />
      </div>
      <p v-if="viewMode === 'table' && records.length" class="table-foot">点击行查看详情 · 共 {{ total }} 条</p>
    </div>

    <div v-if="viewMode === 'card' && total > 0" class="pager card-pager">
      <el-pagination
        background
        layout="total, prev, pager, next"
        :total="total"
        v-model:current-page="current"
        v-model:page-size="size"
        @current-change="load"
      />
    </div>

    <el-drawer
      v-model="detailVisible"
      :title="detailTitle"
      size="560px"
      class="qz-detail-drawer"
      destroy-on-close
      @closed="detailRow = null"
    >
      <div v-if="detailRow" class="detail-stack">
        <DetailSection title="基本信息">
          <div class="detail-meta">
            <el-tag size="small" :type="httpMethodTagType(detailRow.httpMethod)">{{ methodLabel(detailRow) }}</el-tag>
            <el-tag size="small" type="info">{{ categoryLabel(detailRow.category) }}</el-tag>
            <el-tag size="small">{{ providerLabel(detailRow.provider) }}</el-tag>
            <el-tag size="small" :type="detailRow.isPreset ? 'warning' : 'info'">{{ detailRow.isPreset ? '预置' : '自定义' }}</el-tag>
          </div>
          <DetailCopyField label="编码" :value="detailRow.componentCode" copy-message="已复制编码" />
          <DetailCodeBlock
            class="detail-url"
            :title="isDatabaseComponent(detailRow) ? 'SQL 脚本' : '接口地址'"
            :value="detailRow.urlTemplate"
            :copy-message="isDatabaseComponent(detailRow) ? '已复制 SQL' : '已复制地址'"
            max-height="180px"
          />
          <DetailMetaList class="detail-meta-list" :items="detailMetaItems" />
        </DetailSection>
        <DetailSchemaTable
          v-if="detailQueryFields.length"
          title="Query 入参"
          :fields="detailQueryFields"
        />
        <DetailSchemaTable
          v-if="detailBodyFields.length"
          :title="isDatabaseComponent(detailRow) ? 'SQL 入参' : 'Body 入参'"
          :fields="detailBodyFields"
        />
        <DetailSection v-if="!detailQueryFields.length && !detailBodyFields.length" title="入参 Schema">
          <DetailEmpty text="未声明入参 Schema" />
        </DetailSection>
        <LineagePanel v-if="detailRow.id" type="component" :id="detailRow.id" />
        <DetailActions>
          <el-button type="primary" @click="openTest(detailRow)">{{ isDatabaseComponent(detailRow) ? '试运行 SQL' : '试连通' }}</el-button>
          <el-button v-if="canWrite" @click="openEdit(detailRow)">编辑</el-button>
        </DetailActions>
      </div>
    </el-drawer>

    <ComponentCreateWizard
      v-model:visible="createVisible"
      :initial-mode="createMode"
      :saving="saving"
      @save="onCreateSave"
    />

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑组件' : '复制为新组件'"
      width="720px"
      destroy-on-close
      @closed="editTab = 'basic'"
    >
      <el-alert
        v-if="presetLocked"
        title="预置组件仅可改名称、超时/重试和用途说明。"
        type="warning"
        :closable="false"
        class="mode-alert"
      />
      <el-alert
        v-if="isDbForm"
        title="数据库组件：命名参数走 PreparedStatement。只读模式禁止写语句，默认不能一次执行多条 SQL。"
        type="info"
        :closable="false"
        class="mode-alert"
      />
      <el-alert
        v-if="urlNeedsToken && !isDbForm"
        title="此接口需要企业微信 Token，运行时会自动注入，无需手填。"
        type="info"
        :closable="false"
        class="mode-alert"
      />
      <DatabaseSqlPanel v-if="isDbForm && !presetLocked" :form="form" />
      <el-tabs v-else-if="!presetLocked" v-model="editTab">
        <el-tab-pane label="基础信息" name="basic">
          <ComponentCurlImport @import="onCurlImportEdit" />
          <el-form label-position="top">
            <el-form-item label="组件名称" required>
              <el-input v-model="form.componentName" placeholder="例如：查询订单状态" />
            </el-form-item>
            <el-form-item label="用途说明">
              <el-input v-model="form.description" type="textarea" :rows="2" placeholder="一句话说明用途" />
            </el-form-item>
            <el-form-item label="请求方式" required>
              <el-radio-group v-model="form.httpMethod" class="method-group">
                <el-radio-button v-for="item in methodOptions" :key="item.value" :value="item.value">
                  {{ item.label }}
                </el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="接口地址" required>
              <el-input v-model="form.urlTemplate" placeholder="https://api.example.com/..." />
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="鉴权与请求头" name="auth">
          <ComponentAuthPanel
            v-model="form.authState"
            @append-wecom-token="appendWecomTokenPlaceholder"
          />
        </el-tab-pane>
        <el-tab-pane label="调用参数" name="params">
          <p class="section-hint">不需要传参可留空。参数名请与接口文档保持一致。</p>
          <div v-for="(row, index) in form.params" :key="index" class="param-card">
            <div class="param-card-head">
              <span>参数 {{ index + 1 }}</span>
              <el-button type="danger" link @click="form.params.splice(index, 1)">删除</el-button>
            </div>
            <el-form label-position="top" size="small">
              <el-form-item label="参数名" required>
                <el-input v-model="row.key" placeholder="如 orderId" />
              </el-form-item>
              <el-form-item label="说明">
                <el-input v-model="row.description" placeholder="这个参数代表什么" />
              </el-form-item>
              <el-form-item>
                <el-checkbox v-model="row.required">调用时必须填写</el-checkbox>
              </el-form-item>
            </el-form>
          </div>
          <el-button size="small" @click="addParam">添加参数</el-button>
        </el-tab-pane>
        <el-tab-pane label="高级" name="advanced">
          <el-form label-width="100px">
            <el-form-item label="编码">
              <el-input v-model="form.componentCode" />
              <div class="field-hint">系统自动生成，一般无需修改</div>
            </el-form-item>
            <el-form-item label="超时">
              <el-input-number v-model="form.timeoutMs" :min="500" :step="500" />
              <span class="muted"> 毫秒</span>
            </el-form-item>
            <el-form-item label="失败重试">
              <el-input-number v-model="form.retryTimes" :min="0" :max="5" />
              <span class="muted"> 次</span>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
      <el-form v-else label-position="top">
        <el-form-item label="组件名称" required>
          <el-input v-model="form.componentName" />
        </el-form-item>
        <el-form-item label="用途说明">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="超时 / 重试">
          <div class="inline">
            <el-input-number v-model="form.timeoutMs" :min="500" :step="500" />
            <span class="muted">毫秒</span>
            <el-input-number v-model="form.retryTimes" :min="0" :max="5" />
            <span class="muted">次</span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="testVisible" :title="testTitle" width="680px" destroy-on-close @closed="resetTest">
      <el-form label-width="100px" @submit.prevent="runTest">
        <el-form-item v-if="testNeedsToken" label="凭证">
          <el-select v-model="testCredentialId" clearable filterable placeholder="不选则用全局凭证" style="width: 100%">
            <el-option
              v-for="item in credentials"
              :key="item.id"
              :label="`${item.credentialName} (${item.scope === 'WORKFLOW' ? '工作流' : '全局'})`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-for="field in testFields" :key="field.key" :label="field.key" :required="field.required">
          <el-select
            v-if="field.enums.length"
            v-model="testParams[field.key]"
            filterable
            allow-create
            placeholder="选择或输入"
            style="width: 100%"
          >
            <el-option v-for="opt in field.enums" :key="opt" :value="String(opt)" :label="String(opt)" />
          </el-select>
          <el-input
            v-else-if="field.type === 'object' || field.type === 'array'"
            v-model="testParams[field.key]"
            type="textarea"
            :rows="field.type === 'object' ? 4 : 2"
            :placeholder="testPlaceholder(field)"
          />
          <el-input
            v-else
            v-model="testParams[field.key]"
            :type="field.type === 'integer' ? 'number' : 'text'"
            :placeholder="field.description || field.type"
          />
          <div v-if="field.description" class="field-hint">{{ field.description }}</div>
        </el-form-item>
        <el-form-item v-if="!testFields.length" label="入参">
          <span class="muted">{{ isDatabaseComponent(testRow) ? '该 SQL 没有命名参数，将直接执行' : '该组件没有声明入参，将按 URL 直接请求' }}</span>
        </el-form-item>
      </el-form>
      <DetailResultBanner
        v-if="testResult"
        :ok="testResult.success"
        :title="testResult.success ? (isDatabaseComponent(testRow) ? '执行成功' : '连通成功') : (isDatabaseComponent(testRow) ? '执行失败' : '连通失败')"
        :message="testResult.message"
        :meta="testResultMeta"
      >
        <DetailCodeBlock
          v-if="testResult.responseBody"
          title="响应"
          :value="testResult.responseBody"
          copy-message="已复制响应"
          max-height="260px"
        />
      </DetailResultBanner>
      <template #footer>
        <el-button @click="testVisible = false">关闭</el-button>
        <el-button type="primary" :loading="testing" @click="runTest">
          {{ isDatabaseComponent(testRow) ? '执行 SQL' : '发起请求' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import PageState from '@/components/PageState.vue'
import LineagePanel from '@/components/LineagePanel.vue'
import ComponentAuthPanel from '@/components/ComponentAuthPanel.vue'
import ComponentCreateWizard from '@/components/ComponentCreateWizard.vue'
import ComponentCurlImport from '@/components/ComponentCurlImport.vue'
import DatabaseSqlPanel from '@/components/DatabaseSqlPanel.vue'
import DetailSection from '@/components/detail/DetailSection.vue'
import DetailCopyField from '@/components/detail/DetailCopyField.vue'
import DetailCodeBlock from '@/components/detail/DetailCodeBlock.vue'
import DetailMetaList from '@/components/detail/DetailMetaList.vue'
import DetailSchemaTable from '@/components/detail/DetailSchemaTable.vue'
import DetailEmpty from '@/components/detail/DetailEmpty.vue'
import DetailActions from '@/components/detail/DetailActions.vue'
import DetailResultBanner from '@/components/detail/DetailResultBanner.vue'
import { applyCurlImport } from '@/utils/parseCurl'
import {
  authSummary,
  buildExtraConfig,
  createEmptyAuthState,
  parseAuthFromComponent,
  validateAuthState,
} from '@/utils/componentAuth'
import { askConfirm } from '@/utils/confirm'
import { copyText } from '@/utils/format'
import { createComponent, deleteComponent, pageComponents, testComponent, updateComponent } from '@/api/component'
import { pageCredentials } from '@/api/credential'
import { getModuleStats } from '@/api/dashboard'
import { networkErrorMessage } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import {
  CATEGORY_LABEL,
  categoryLabel,
  componentParamRows,
  fieldsToSchema,
  httpMethodTagType,
  needsAccessToken,
  paramStats,
  providerLabel,
  schemaFields,
  schemaToFields,
  urlPath,
} from '@/utils/schema'
import { compactSql, isDatabaseComponent, parseDatabaseExtra } from '@/utils/sqlParams'

const methods = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']
const methodOptions = [
  { value: 'GET', label: '查询' },
  { value: 'POST', label: '提交' },
  { value: 'PUT', label: '更新' },
  { value: 'PATCH', label: '修改' },
  { value: 'DELETE', label: '删除' },
]
const categoryOptions = Object.entries(CATEGORY_LABEL).map(([value, label]) => ({ value, label }))

const auth = useAuthStore()
const canWrite = computed(() => auth.hasPermission('component:write') || auth.canWrite.value)
const VIEW_KEY = 'qz-component-view-mode'
const viewMode = ref(localStorage.getItem(VIEW_KEY) === 'card' ? 'card' : 'table')
watch(viewMode, (v) => localStorage.setItem(VIEW_KEY, v))

const route = useRoute()
const router = useRouter()
const keyword = ref(route.query.keyword || '')
const category = ref(route.query.category || '')
const presetFilter = ref(route.query.preset || '')
const httpMethod = ref(route.query.method || '')
const records = ref([])
const total = ref(0)
const current = ref(route.query.page ? Number(route.query.page) : 1)
const size = ref(10)
const loading = ref(false)
const loadError = ref('')
const syncingQuery = ref(false)
let keywordTimer = null

const createVisible = ref(false)
const createMode = ref('easy')
const hasListFilters = computed(() => Boolean(keyword.value || category.value || presetFilter.value || httpMethod.value))

function openCreate(mode = 'easy') {
  if (!canWrite.value) return
  createMode.value = mode
  createVisible.value = true
}

function setQuickFilter(partial) {
  if (Object.prototype.hasOwnProperty.call(partial, 'category')) {
    category.value = partial.category || ''
    if (category.value === 'DATABASE') httpMethod.value = ''
  }
  if (Object.prototype.hasOwnProperty.call(partial, 'preset')) {
    presetFilter.value = partial.preset || ''
  }
  if (!Object.keys(partial).length) {
    category.value = ''
    presetFilter.value = ''
    httpMethod.value = ''
  }
  reload()
}

function clearFilters() {
  keyword.value = ''
  category.value = ''
  presetFilter.value = ''
  httpMethod.value = ''
  reload()
}

function onKeywordInput() {
  clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => reload(), 400)
}
const dialogVisible = ref(false)
const editTab = ref('basic')
const saving = ref(false)
const detailVisible = ref(false)
const detailRow = ref(null)

const testVisible = ref(false)
const testing = ref(false)
const testRow = ref(null)
const testCredentialId = ref(null)
const testParams = reactive({})
const testResult = ref(null)
const credentials = ref([])
const GUIDE_KEY = 'qz-component-guide-dismissed'
const showGuide = ref(localStorage.getItem(GUIDE_KEY) !== '1')
const componentStats = ref(null)

const form = reactive({
  id: null,
  isPreset: 0,
  componentName: '',
  componentCode: '',
  httpMethod: 'GET',
  urlTemplate: '',
  timeoutMs: 10000,
  retryTimes: 0,
  description: '',
  provider: 'CUSTOM',
  category: 'HTTP',
  params: [],
  authState: createEmptyAuthState(),
  sql: '',
  datasourceId: null,
  accessMode: 'READ',
  maxRows: 200,
})

const isDbForm = computed(() => form.provider === 'DATABASE' || form.category === 'DATABASE')
const presetLocked = computed(() => Boolean(form.isPreset))
const urlNeedsToken = computed(() => needsAccessToken({
  urlTemplate: form.urlTemplate,
  extraConfig: buildExtraConfig(form.authState),
}))
const testNeedsToken = computed(() => needsAccessToken(testRow.value) && !isDatabaseComponent(testRow.value))
const testFields = computed(() => (testRow.value ? schemaFields(testRow.value) : []))
const testTitle = computed(() => {
  if (!testRow.value) return '试连通'
  return isDatabaseComponent(testRow.value)
    ? `试运行 SQL · ${testRow.value.componentName}`
    : `试连通 · ${testRow.value.componentName}`
})
const detailTitle = computed(() => (detailRow.value ? `组件详情 · ${detailRow.value.componentName}` : '组件详情'))
const detailQueryFields = computed(() => {
  if (!detailRow.value || isDatabaseComponent(detailRow.value)) return []
  return schemaToFields(detailRow.value.querySchema)
})
const detailBodyFields = computed(() => (detailRow.value ? schemaToFields(detailRow.value.bodySchema) : []))
const detailMetaItems = computed(() => {
  const row = detailRow.value
  if (!row) return []
  if (isDatabaseComponent(row)) {
    const extra = parseDatabaseExtra(row)
    return [
      { label: '数据源 ID', value: extra.datasourceId ? String(extra.datasourceId) : '—' },
      { label: '访问模式', value: extra.accessMode === 'WRITE' ? '允许写入' : '只读查询' },
      { label: '最多行数', value: String(extra.maxRows || 200) },
      { label: '超时', value: `${row.timeoutMs}ms` },
      { label: '说明', value: row.description, hidden: !row.description },
    ]
  }
  const auth = needsAccessToken(row) ? '企业微信 Token' : authSummary(row)
  return [
    { label: '鉴权', value: auth, hidden: !auth },
    { label: '超时', value: `${row.timeoutMs}ms` },
    { label: '重试', value: `${row.retryTimes} 次` },
    { label: '说明', value: row.description, hidden: !row.description },
  ]
})
const testResultMeta = computed(() => {
  const result = testResult.value
  if (!result) return ''
  const parts = [`${result.requestMethod || ''} ${result.requestUrl || '-'}`]
  if (result.httpStatus && !isDatabaseComponent(testRow.value)) parts.push(`HTTP ${result.httpStatus}`)
  if (result.durationMs != null) parts.push(`${result.durationMs}ms`)
  if (result.wecomErrcode != null) parts.push(`errcode ${result.wecomErrcode}`)
  return parts.filter(Boolean).join(' · ')
})
const emptyText = computed(() => {
  if (keyword.value || category.value || presetFilter.value || httpMethod.value) {
    return '没有匹配的组件'
  }
  return '还没有接口组件。可接入 HTTP 接口，或把 SQL 脚本保存为数据库组件。'
})

function methodLabel(row) {
  if (isDatabaseComponent(row)) {
    return row.httpMethod === 'UPDATE' ? 'UPDATE' : 'QUERY'
  }
  return row.httpMethod || '—'
}

function methodFilterLabel(value) {
  if (value === 'QUERY') return '数据库查询'
  if (value === 'UPDATE') return '数据库更新'
  return value
}

function displayPath(row) {
  if (isDatabaseComponent(row)) return compactSql(row.urlTemplate)
  return urlPath(row.urlTemplate, row)
}

function applyQuery() {
  keyword.value = route.query.keyword || ''
  category.value = route.query.category || ''
  presetFilter.value = route.query.preset || ''
  httpMethod.value = route.query.method || ''
  current.value = route.query.page ? Number(route.query.page) : 1
}

function syncQuery() {
  const query = {}
  if (keyword.value) query.keyword = keyword.value
  if (category.value) query.category = category.value
  if (presetFilter.value) query.preset = presetFilter.value
  if (httpMethod.value) query.method = httpMethod.value
  if (current.value > 1) query.page = String(current.value)
  syncingQuery.value = true
  router.replace({ path: '/components', query }).finally(() => {
    syncingQuery.value = false
  })
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [res, statsRes] = await Promise.all([
      pageComponents({
        current: current.value,
        size: size.value,
        keyword: keyword.value,
        category: category.value || undefined,
        isPreset: presetFilter.value === '' ? undefined : Number(presetFilter.value),
        httpMethod: httpMethod.value || undefined,
      }),
      getModuleStats().catch(() => null),
    ])
    records.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
    componentStats.value = statsRes?.data?.components || null
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

function resetForm() {
  form.id = null
  form.isPreset = 0
  form.componentName = ''
  form.componentCode = ''
  form.httpMethod = 'GET'
  form.urlTemplate = ''
  form.timeoutMs = 10000
  form.retryTimes = 0
  form.description = ''
  form.provider = 'CUSTOM'
  form.category = 'HTTP'
  form.params = []
  form.authState = createEmptyAuthState()
  form.sql = ''
  form.datasourceId = null
  form.accessMode = 'READ'
  form.maxRows = 200
}

function dismissGuide() {
  localStorage.setItem(GUIDE_KEY, '1')
  showGuide.value = false
}

function onCurlImportEdit(parsed) {
  applyCurlImport(parsed, { form, authState: form.authState })
  if (parsed.componentName && !form.componentName.trim()) {
    form.componentName = parsed.componentName
  }
  editTab.value = 'basic'
  if (parsed.paramMode === 'custom') {
    editTab.value = 'params'
  } else if (parsed.authState?.authType && parsed.authState.authType !== 'none') {
    editTab.value = 'auth'
  }
}

function appendWecomTokenPlaceholder() {
  const placeholder = 'access_token=${access_token}'
  const url = form.urlTemplate.trim()
  if (!url) {
    form.urlTemplate = `https://qyapi.weixin.qq.com/cgi-bin/example?${placeholder}`
    return
  }
  if (url.includes('access_token')) {
    return
  }
  form.urlTemplate = url.includes('?') ? `${url}&${placeholder}` : `${url}?${placeholder}`
}

function openEdit(row) {
  editTab.value = 'basic'
  form.id = row.id
  form.isPreset = row.isPreset
  form.componentName = row.componentName
  form.componentCode = row.componentCode
  form.httpMethod = row.httpMethod
  form.urlTemplate = row.urlTemplate
  form.timeoutMs = row.timeoutMs || 10000
  form.retryTimes = row.retryTimes || 0
  form.description = row.description || ''
  form.provider = row.provider || 'CUSTOM'
  form.category = row.category || 'HTTP'
  form.params = componentParamRows(row)
  form.authState = parseAuthFromComponent(row)
  const extra = parseDatabaseExtra(row)
  form.sql = isDatabaseComponent(row) ? (row.urlTemplate || '') : ''
  form.datasourceId = extra.datasourceId
  form.accessMode = extra.accessMode
  form.maxRows = extra.maxRows
  dialogVisible.value = true
}

function openClone(row) {
  resetForm()
  editTab.value = 'basic'
  form.componentName = `${row.componentName} 副本`
  form.componentCode = `${row.componentCode}_copy`
  form.httpMethod = row.httpMethod
  form.urlTemplate = row.urlTemplate
  form.timeoutMs = row.timeoutMs || 10000
  form.retryTimes = row.retryTimes || 0
  form.description = row.description || ''
  form.provider = 'CUSTOM'
  form.category = row.category || 'HTTP'
  form.params = componentParamRows(row).map((item) => ({ ...item }))
  form.authState = parseAuthFromComponent(row)
  const extra = parseDatabaseExtra(row)
  form.sql = isDatabaseComponent(row) ? (row.urlTemplate || '') : ''
  form.datasourceId = extra.datasourceId
  form.accessMode = extra.accessMode
  form.maxRows = extra.maxRows
  if (isDatabaseComponent(row)) {
    form.provider = 'DATABASE'
    form.category = 'DATABASE'
  }
  dialogVisible.value = true
}

function openDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}

function paramLocation() {
  return form.httpMethod === 'GET' || form.httpMethod === 'DELETE' ? 'query' : 'body'
}

function addParam() {
  form.params.push({
    key: '',
    type: 'string',
    required: false,
    description: '',
    location: paramLocation(),
  })
}

async function onCreateSave({ payload, testAfter }) {
  saving.value = true
  try {
    const res = await createComponent(payload)
    createVisible.value = false
    await load()
    if (testAfter && res.data) {
      ElMessage.success('组件已创建，正在试连通…')
      openTest(res.data)
      return
    }
    ElMessage.success('组件已创建，可在设计器左侧组件库拖入使用')
  } finally {
    saving.value = false
  }
}

function validateParams() {
  const keys = []
  for (const row of form.params) {
    const key = String(row.key || '').trim()
    if (!key) {
      if (row.description || row.required) {
        ElMessage.warning('请填写参数名')
        return false
      }
      continue
    }
    if (keys.includes(key)) {
      ElMessage.warning(`参数名重复：${key}`)
      return false
    }
    keys.push(key)
    row.location = paramLocation()
  }
  return true
}

function validateCode() {
  if (presetLocked.value) {
    return true
  }
  const code = form.componentCode.trim()
  if (!/^[a-z0-9._-]+$/.test(code)) {
    ElMessage.warning('编码仅支持小写字母、数字、点、下划线和连字符')
    return false
  }
  return true
}

async function save() {
  if (isDbForm.value) {
    if (!form.componentName || !form.sql || !form.datasourceId) {
      ElMessage.warning('请填写组件名称、数据源和 SQL')
      return
    }
    if (!form.componentCode) {
      form.componentCode = `custom.db.${Date.now().toString(36)}`
    }
    if (!validateCode()) return
    saving.value = true
    try {
      const payload = {
        componentCode: form.componentCode.trim(),
        componentName: form.componentName.trim(),
        provider: 'DATABASE',
        category: 'DATABASE',
        httpMethod: 'QUERY',
        urlTemplate: form.sql.trim(),
        timeoutMs: form.timeoutMs,
        retryTimes: 0,
        description: form.description,
        bodySchema: fieldsToSchema(form.params),
        extraConfig: {
          kind: 'DATABASE',
          datasourceId: form.datasourceId,
          accessMode: form.accessMode,
          maxRows: form.maxRows,
        },
      }
      if (form.id) await updateComponent(form.id, payload)
      else await createComponent(payload)
      ElMessage.success('已保存')
      dialogVisible.value = false
      await load()
    } finally {
      saving.value = false
    }
    return
  }
  if (!form.componentName || !form.urlTemplate) {
    ElMessage.warning('请填写组件名称和接口地址')
    return
  }
  if (!form.componentCode) {
    form.componentCode = `custom.http.${Date.now().toString(36)}`
  }
  if (!validateCode() || !validateParams()) {
    return
  }
  const authCheck = validateAuthState(form.authState)
  if (!authCheck.valid) {
    ElMessage.warning(authCheck.message)
    return
  }
  saving.value = true
  try {
    const cleaned = form.params.filter((item) => String(item.key || '').trim())
    const queryFields = cleaned.filter((item) => item.location === 'query')
    const bodyFields = cleaned.filter((item) => item.location !== 'query')
    const payload = {
      componentCode: form.componentCode.trim(),
      componentName: form.componentName.trim(),
      provider: form.provider || 'CUSTOM',
      category: form.category || 'HTTP',
      httpMethod: form.httpMethod,
      urlTemplate: form.urlTemplate.trim(),
      timeoutMs: form.timeoutMs,
      retryTimes: form.retryTimes,
      description: form.description,
      querySchema: fieldsToSchema(queryFields),
      bodySchema: fieldsToSchema(bodyFields),
      extraConfig: buildExtraConfig(form.authState),
    }
    if (form.id) {
      await updateComponent(form.id, payload)
    } else {
      await createComponent(payload)
    }
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

function resetTest() {
  testRow.value = null
  testCredentialId.value = null
  testResult.value = null
  Object.keys(testParams).forEach((key) => delete testParams[key])
}

function openTest(row) {
  detailVisible.value = false
  testRow.value = row
  testCredentialId.value = null
  testResult.value = null
  Object.keys(testParams).forEach((key) => delete testParams[key])
  schemaFields(row).forEach((field) => {
    testParams[field.key] = ''
  })
  testVisible.value = true
  loadCredentials()
}

async function loadCredentials() {
  const res = await pageCredentials({ current: 1, size: 100 })
  credentials.value = (res.data?.records || []).filter((item) => item.status === 1)
}

function testPlaceholder(field) {
  if (field.type === 'array') {
    return '["a","b"] 或 a,b'
  }
  if (field.type === 'object') {
    return '{ }'
  }
  return field.description || field.type
}

function parseTestValue(field, raw) {
  if (raw === '' || raw == null) {
    return undefined
  }
  if (field.type === 'integer') {
    const n = Number(raw)
    if (Number.isNaN(n)) {
      throw new Error(`${field.key} 必须是整数`)
    }
    return n
  }
  if (field.type === 'array') {
    const text = String(raw).trim()
    if (text.startsWith('[')) {
      return JSON.parse(text)
    }
    return text.split(/[,|]/).map((item) => item.trim()).filter(Boolean)
  }
  if (field.type === 'object') {
    return JSON.parse(String(raw))
  }
  return raw
}

async function runTest() {
  if (!testRow.value) {
    return
  }
  const params = {}
  try {
    for (const field of testFields.value) {
      const raw = testParams[field.key]
      if (field.required && (raw === '' || raw == null)) {
        ElMessage.warning(`请填写 ${field.key}`)
        return
      }
      const value = parseTestValue(field, raw)
      if (value !== undefined) {
        params[field.key] = value
      }
    }
  } catch (error) {
    ElMessage.warning(error.message || '入参格式不正确')
    return
  }
  testing.value = true
  try {
    const res = await testComponent(testRow.value.id, {
      credentialId: testCredentialId.value || undefined,
      params,
    })
    testResult.value = res.data || {}
    if (testResult.value.success) {
      ElMessage.success(testResult.value.message || '连通成功')
    } else {
      ElMessage.warning(testResult.value.message || '连通失败')
    }
  } finally {
    testing.value = false
  }
}

async function onCopy(text, message = '已复制') {
  await copyText(text)
  ElMessage.success(message)
}

async function onDelete(row) {
  if (!(await askConfirm(`删除组件「${row.componentName}」？`))) return
  await deleteComponent(row.id)
  ElMessage.success('已删除')
  await load()
}

function onRowCommand(command, row) {
  if (command === 'edit') return openEdit(row)
  if (command === 'clone') return openClone(row)
  if (command === 'delete') return onDelete(row)
}

watch(
  () => form.httpMethod,
  () => {
    const location = paramLocation()
    for (const row of form.params) {
      row.location = location
    }
  },
)

watch(
  () => [route.query.keyword, route.query.category, route.query.preset, route.query.method, route.query.page],
  () => {
    if (syncingQuery.value) {
      return
    }
    applyQuery()
    load()
  },
)

onMounted(() => {
  applyQuery()
  load()
})
</script>

<style scoped>
.sub { color: var(--qz-text-muted); font-size: 12px; margin-top: 4px; }
.muted { color: var(--qz-text-muted); }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; }
.name-cell { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.guide-alert { margin-bottom: 14px; }
.guide-steps {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 20px;
  margin-top: 4px;
  font-size: 13px;
  color: var(--qz-text-muted);
}
.guide-action { margin-left: auto; }
.token-tag { flex-shrink: 0; }
.mode-alert { margin-bottom: 12px; }
.field-hint { margin-top: 4px; font-size: 12px; color: var(--qz-text-muted); }
.inline { display: flex; align-items: center; gap: 8px; }
.param-empty { margin-bottom: 8px; }
.method-group { flex-wrap: wrap; }
.section-hint { margin: 0 0 12px; font-size: 13px; color: var(--qz-text-muted); }
.param-card {
  padding: 12px;
  margin-bottom: 12px;
  border: 1px solid var(--qz-border);
  border-radius: var(--qz-radius);
  background: var(--qz-fill);
}
.param-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-weight: 600;
  font-size: 13px;
}
.detail-stack { display: flex; flex-direction: column; gap: 12px; }
.detail-meta { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 14px; }
.detail-url { margin-top: 14px; }
.detail-meta-list { margin-top: 14px; }
.filter-bar {
  padding: 12px 14px;
  margin-bottom: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.chip-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.chip {
  border: 1px solid var(--qz-border, #e2e8f0);
  background: #fff;
  color: #475569;
  border-radius: 999px;
  padding: 4px 12px;
  font-size: 12px;
  cursor: pointer;
  line-height: 1.4;
}
.chip:hover { border-color: #93c5fd; color: #1d4ed8; }
.chip.active {
  background: #eff6ff;
  border-color: #93c5fd;
  color: #1d4ed8;
  font-weight: 600;
}
.chip-sep {
  width: 1px;
  height: 16px;
  background: #e2e8f0;
  margin: 0 4px;
}
.filter-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.search-input { width: 240px; }
.stat-card.clickable {
  cursor: pointer;
  text-align: left;
  border: 1px solid transparent;
  background: var(--qz-panel, #fff);
  font: inherit;
}
.stat-card.clickable:hover,
.stat-card.clickable.active {
  border-color: #93c5fd;
  box-shadow: 0 0 0 1px rgba(37, 99, 235, 0.08);
}
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
  min-height: 120px;
}
.comp-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
  text-align: left;
  border: 1px solid var(--qz-border, #e2e8f0);
  border-radius: 12px;
  background: #fff;
  cursor: pointer;
  font: inherit;
  color: inherit;
}
.comp-card:hover {
  border-color: #93c5fd;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.06);
}
.comp-card-top { display: flex; flex-wrap: wrap; gap: 4px; }
.comp-card-title {
  font-size: 15px;
  font-weight: 650;
  color: #0f172a;
  line-height: 1.35;
}
.comp-card-code {
  font-size: 11px;
  color: var(--el-color-primary);
  word-break: break-all;
  cursor: pointer;
  line-height: 1.3;
}
.comp-card-path {
  color: #64748b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.comp-card-foot {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  color: #64748b;
}
.auth-hint { color: #b45309; }
.comp-card-ops {
  display: flex;
  gap: 4px;
  margin-top: 2px;
  padding-top: 8px;
  border-top: 1px dashed #e2e8f0;
}
.card-pager { margin-top: 12px; }
.view-toggle { margin-right: 4px; }
@media (max-width: 900px) {
  .param-row {
    grid-template-columns: 1fr 1fr;
  }
  .search-input { width: 100%; }
}
</style>
