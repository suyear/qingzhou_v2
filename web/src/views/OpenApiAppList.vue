<template>
  <div class="openapi-page">
    <PageHeader title="开放平台" desc="创建应用、授权已发布的工作流，用调用助手试一下就能给别人调。">
      <template v-if="pageTab === 'apps'">
        <el-input
          v-model="keyword"
          class="search-input"
          placeholder="搜索应用名称 / App Key"
          clearable
          @keyup.enter="load"
          @clear="load"
        />
        <el-button @click="load">查询</el-button>
        <el-button type="primary" @click="openCreate">新建应用</el-button>
      </template>
      <el-button v-else text type="primary" @click="pageTab = 'apps'">返回应用管理</el-button>
    </PageHeader>

    <div class="page-toolbar">
      <div class="tab-switch">
        <button type="button" class="tab-btn" :class="{ active: pageTab === 'apps' }" @click="pageTab = 'apps'">
          应用管理
        </button>
        <button type="button" class="tab-btn" :class="{ active: pageTab === 'docs' }" @click="pageTab = 'docs'">
          接入文档
        </button>
      </div>
    </div>

    <PageState v-if="pageTab === 'apps'" :error="loadError" @retry="load" />

    <template v-if="pageTab === 'apps'">
      <div v-if="showGuide" class="flow-strip">
        <div class="flow-step" :class="{ done: stats.published > 0 }">
          <span class="flow-badge">1</span>
          <div class="flow-body">
            <strong>发布工作流</strong>
            <p>{{ stats.published > 0 ? `已有 ${stats.published} 个可授权` : '编排完成后点发布' }}</p>
            <el-button type="primary" link @click="$router.push('/workflows')">去编排</el-button>
          </div>
        </div>
        <div class="flow-arrow">→</div>
        <div class="flow-step" :class="{ done: stats.apps > 0 }">
          <span class="flow-badge">2</span>
          <div class="flow-body">
            <strong>创建应用</strong>
            <p>获取 App Key 和 Secret</p>
            <el-button type="primary" link @click="openCreate">新建应用</el-button>
          </div>
        </div>
        <div class="flow-arrow">→</div>
        <div class="flow-step" :class="{ done: stats.ready > 0 }">
          <span class="flow-badge">3</span>
          <div class="flow-body">
            <strong>授权并试调</strong>
            <p>{{ stats.ready > 0 ? `${stats.ready} 个应用已就绪` : '授权工作流后调用助手试调' }}</p>
            <el-button v-if="firstReadyApp" type="primary" link @click="openInvoke(firstReadyApp)">打开调用助手</el-button>
          </div>
        </div>
        <button type="button" class="flow-close" title="关闭引导" @click="dismissGuide">×</button>
      </div>

      <div v-else class="stat-grid cols-3">
        <div class="stat-card">
          <div class="stat-label">开放应用</div>
          <div class="stat-num">{{ stats.apps }}</div>
        </div>
        <div class="stat-card stat-ok">
          <div class="stat-label">可对外调用</div>
          <div class="stat-num">{{ stats.ready }}</div>
          <div class="stat-hint">近 7 日调用 {{ openapiExtra.invoke7d ?? 0 }} · 失败 {{ openapiExtra.fail7d ?? 0 }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">已发布工作流</div>
          <div class="stat-num">{{ stats.published }}</div>
          <el-button v-if="!stats.published" type="primary" link @click="$router.push('/workflows')">去发布</el-button>
        </div>
      </div>

      <div class="qz-panel">
        <el-table
          class="qz-table app-table"
          :data="records"
          v-loading="loading"
          stripe
          highlight-current-row
          @row-click="onRowClick"
        >
          <el-table-column label="应用" min-width="180">
            <template #default="{ row }">
              <div class="app-name-cell">
                <span class="app-name">{{ row.appName }}</span>
                <span v-if="row.remark" class="app-remark">{{ row.remark }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="App Key" min-width="210">
            <template #default="{ row }">
              <div class="key-cell">
                <span class="mono">{{ maskedKey(row.appKey, revealedKeys[row.id]) }}</span>
                <el-button type="primary" link class="copy-btn" @click.stop="toggleKeyReveal(row)">
                  {{ revealedKeys[row.id] ? '隐藏' : '展开' }}
                </el-button>
                <el-button type="primary" link class="copy-btn" @click.stop="copyField(row.appKey, '已复制 App Key')">复制</el-button>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="就绪状态" width="120">
            <template #default="{ row }">
              <StatusTag :type="readinessTag(row).type" :label="readinessTag(row).label" />
            </template>
          </el-table-column>
          <el-table-column label="授权" width="88" align="center">
            <template #default="{ row }">
              <el-button type="primary" link @click.stop="openGrant(row)">
                {{ row.grantedCount > 0 ? `${row.grantedCount} 个` : '去授权' }}
              </el-button>
            </template>
          </el-table-column>
          <el-table-column label="限流" width="88">
            <template #default="{ row }">{{ row.rateLimitQps > 0 ? `${row.rateLimitQps}/s` : '不限' }}</template>
          </el-table-column>
          <el-table-column label="更新时间" min-width="150">
            <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <div class="qz-ops" @click.stop>
                <el-button
                  type="primary"
                  :disabled="row.status !== 1 || !row.grantedCount"
                  @click="openInvoke(row)"
                >
                  调用助手
                </el-button>
                <el-dropdown trigger="click" @command="(cmd) => onRowCommand(cmd, row)">
                  <el-button type="primary" link>更多</el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="detail">应用详情</el-dropdown-item>
                      <el-dropdown-item command="grant">授权工作流</el-dropdown-item>
                      <el-dropdown-item command="edit">限流 / 白名单</el-dropdown-item>
                      <el-dropdown-item command="records">调用记录</el-dropdown-item>
                      <el-dropdown-item command="docs">查看接入文档</el-dropdown-item>
                      <el-dropdown-item command="reset-secret" divided>重置 Secret</el-dropdown-item>
                      <el-dropdown-item v-if="row.status === 1" command="disable">停用应用</el-dropdown-item>
                      <el-dropdown-item v-else command="enable">启用应用</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty v-if="!loading && !loadError" description="还没有开放应用">
              <p class="empty-hint">三步即可对外提供 API：发布工作流 → 创建应用 → 授权试调</p>
              <el-button type="primary" @click="openCreate">新建应用</el-button>
              <el-button @click="pageTab = 'docs'">先看接入文档</el-button>
            </el-empty>
          </template>
        </el-table>
        <p v-if="records.length" class="table-foot">点击行可查看应用详情 · 共 {{ records.length }} 个应用</p>
      </div>
    </template>

    <div v-else class="qz-panel docs-panel">
      <OpenApiDocsPanel />
    </div>

    <!-- 应用详情抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="drawerApp?.appName || '应用详情'"
      size="560px"
      class="qz-detail-drawer"
      destroy-on-close
    >
      <template v-if="drawerApp">
        <div class="drawer-stack">
          <DetailSection title="应用状态">
            <div class="drawer-head">
              <StatusTag kind="enable" :value="drawerApp.status" />
              <StatusTag :type="readinessTag(drawerApp).type" :label="readinessTag(drawerApp).label" />
            </div>
            <div class="checklist">
              <div class="check-item" :class="{ ok: drawerApp.status === 1 }">
                <span class="check-dot" />
                <span>应用已{{ drawerApp.status === 1 ? '启用' : '停用' }}</span>
              </div>
              <div class="check-item" :class="{ ok: drawerApp.grantedCount > 0 }">
                <span class="check-dot" />
                <span>{{ drawerApp.grantedCount > 0 ? `已授权 ${drawerApp.grantedCount} 个工作流` : '尚未授权工作流' }}</span>
              </div>
            </div>
            <DetailCopyField label="App Key" :value="drawerApp.appKey" copy-message="已复制" />
            <DetailMetaList class="drawer-meta" :items="drawerMetaItems" />
          </DetailSection>
          <DetailActions>
            <el-button type="primary" :disabled="drawerApp.status !== 1 || !drawerApp.grantedCount" @click="openInvoke(drawerApp); drawerVisible = false">
              调用助手
            </el-button>
            <el-button @click="openGrant(drawerApp); drawerVisible = false">授权工作流</el-button>
            <el-button @click="openEdit(drawerApp)">应用设置</el-button>
            <el-button @click="goRecords(drawerApp)">调用记录</el-button>
            <el-button @click="goProblems(drawerApp)">失败链路</el-button>
          </DetailActions>
        </div>
      </template>
    </el-drawer>

    <!-- 新建 -->
    <el-dialog v-model="createVisible" title="新建开放应用" width="500px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="应用名称" required>
          <el-input v-model="createForm.appName" placeholder="例如：订单系统、CRM 对接" autofocus @keyup.enter="saveApp" />
        </el-form-item>
        <el-collapse class="create-advanced">
          <el-collapse-item title="高级设置（一般不用改）" name="advanced">
            <el-form-item label="每秒请求上限（QPS）">
              <el-input-number v-model="createForm.rateLimitQps" :min="0" :max="10000" />
              <span class="muted">0 表示不限制，默认 10</span>
            </el-form-item>
            <el-form-item label="IP 白名单">
              <el-input v-model="createForm.ipWhitelist" type="textarea" :rows="2" placeholder="留空不限制" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="createForm.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-collapse-item>
        </el-collapse>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="saveApp">创建并获取密钥</el-button>
      </template>
    </el-dialog>

    <!-- 编辑 -->
    <el-dialog v-model="editVisible" title="应用设置" width="500px">
      <el-form label-position="top">
        <el-form-item label="应用名称" required>
          <el-input v-model="editForm.appName" />
        </el-form-item>
        <el-form-item label="每秒请求上限（QPS）">
          <el-input-number v-model="editForm.rateLimitQps" :min="0" :max="10000" />
          <span class="muted">0 表示不限制</span>
        </el-form-item>
        <el-form-item label="IP 白名单">
          <el-input v-model="editForm.ipWhitelist" type="textarea" :rows="3" placeholder="留空不限制；支持 CIDR" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 授权 -->
    <el-dialog v-model="grantVisible" :title="`授权工作流 · ${currentApp?.appName || ''}`" width="600px" destroy-on-close>
      <p class="hint">勾选允许该应用调用的<strong>已发布</strong>工作流。可搜索名称或编码。</p>
      <div class="grant-toolbar">
        <el-input v-model="grantKeyword" placeholder="搜索工作流" clearable />
        <el-button text type="primary" @click="selectAllGrants">全选</el-button>
        <el-button text @click="grantedIds = []">清空</el-button>
      </div>
      <div v-if="filteredGrantWorkflows.length" class="grant-grid">
        <label
          v-for="wf in filteredGrantWorkflows"
          :key="wf.id"
          class="grant-card"
          :class="{ active: grantedIds.includes(wf.id) }"
        >
          <el-checkbox :model-value="grantedIds.includes(wf.id)" @change="toggleGrant(wf.id, $event)" />
          <div class="grant-card-body">
            <div class="grant-name">{{ wf.workflowName }}</div>
            <div class="grant-code mono">{{ wf.workflowCode }}</div>
          </div>
        </label>
      </div>
      <el-empty v-else-if="!publishedWorkflows.length" description="暂无已发布工作流">
        <el-button type="primary" @click="$router.push('/workflows')">去编排发布</el-button>
      </el-empty>
      <el-empty v-else description="没有匹配的工作流" />
      <template #footer>
        <div class="dialog-footer-row">
          <span class="grant-footer-hint">已选 {{ grantedIds.length }} 个</span>
          <el-button @click="grantVisible = false">取消</el-button>
          <el-button type="primary" :loading="granting" @click="saveGrant()">保存授权</el-button>
          <el-button
            type="success"
            :loading="granting"
            :disabled="!grantedIds.length"
            @click="saveGrant(true)"
          >
            保存并试调
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Secret -->
    <el-dialog v-model="secretVisible" title="请立即保存密钥" width="540px" :close-on-click-modal="false" :show-close="secretConfirmed">
      <el-alert type="warning" :closable="false" show-icon class="mode-alert">
        <template #title>Secret 只显示一次</template>
        关闭后无法查看。请复制保存到安全的地方，丢失后可在应用「更多」中重置。
      </el-alert>
      <el-form label-position="top">
        <el-form-item label="App Key">
          <DetailCopyField :value="createdSecret.appKey" copy-message="已复制 App Key" />
        </el-form-item>
        <el-form-item label="App Secret">
          <DetailCopyField :value="createdSecret.appSecret" copy-message="已复制 Secret" primary />
        </el-form-item>
      </el-form>
      <el-button class="copy-all-btn" @click="copyAllSecrets">一键复制 Key + Secret</el-button>
      <el-checkbox v-model="secretConfirmed" class="secret-check">我已将密钥保存到安全的地方</el-checkbox>
      <template #footer>
        <el-button :disabled="!secretConfirmed" @click="afterSecretSaved()">完成</el-button>
        <el-button type="primary" :disabled="!secretConfirmed" @click="afterSecretSaved(true)">完成并去授权</el-button>
      </template>
    </el-dialog>

    <!-- 调用助手 -->
    <el-dialog v-model="invokeVisible" :title="invokeTitle" width="960px" destroy-on-close class="invoke-dialog" @closed="resetInvoke">
      <div v-if="!invokeWorkflows.length" class="invoke-empty">
        <el-empty description="该应用尚未授权任何工作流">
          <el-button type="primary" @click="openGrantFromInvoke">去授权工作流</el-button>
        </el-empty>
      </div>
      <div v-else class="invoke-layout">
        <div class="invoke-left">
          <el-form label-position="top">
            <el-form-item label="选择工作流" required>
              <el-select v-model="invokeForm.workflowCode" filterable style="width: 100%">
                <el-option
                  v-for="wf in invokeWorkflows"
                  :key="wf.id"
                  :label="`${wf.workflowName} (${wf.workflowCode})`"
                  :value="wf.workflowCode"
                />
              </el-select>
            </el-form-item>
            <div v-if="invokeFieldGuide.length" class="field-guide">
              <div class="field-guide-head">
                <strong>入参字段说明</strong>
                <el-button type="primary" link @click="fillInvokeExample">填入示例</el-button>
              </div>
              <ul class="field-guide-list">
                <li v-for="field in invokeFieldGuide" :key="field.key">
                  <code>{{ field.key }}</code>
                  <span>{{ field.label }}</span>
                  <el-tag size="small" :type="field.required ? 'danger' : 'info'" effect="plain">
                    {{ field.required ? '必填' : '可选' }}
                  </el-tag>
                  <span class="muted">{{ field.type }} · 例 {{ formatExample(field.example) }}</span>
                </li>
              </ul>
            </div>
            <p v-else class="hint">该工作流未预定义入参，可用下方键值对或 JSON 自行添加。</p>
            <el-form-item label="触发入参">
              <ScheduleTriggerInput
                ref="invokeInputRef"
                v-model="invokeInputData"
                compact
                :input-schema="selectedInvokeWorkflow?.inputSchema"
              />
            </el-form-item>
          </el-form>
          <div class="invoke-btns">
            <el-button type="primary" :loading="invoking" :disabled="!invokeForm.workflowCode" @click="runInvoke">
              走网关试调
            </el-button>
            <el-button :loading="previewing" :disabled="!invokeForm.workflowCode" @click="makePreview">
              生成 curl
            </el-button>
          </div>
        </div>
        <div class="invoke-right">
          <el-tabs v-model="invokeResultTab" class="result-tabs">
            <el-tab-pane label="试调结果" name="result">
              <div v-if="invokeResult" class="result-pane">
                <DetailResultBanner
                  :ok="invokeOk"
                  :title="`HTTP ${invokeResult.httpStatus} · ${invokeOk ? '成功' : '失败'}`"
                >
                  <template #extra>
                    <el-button v-if="invokeOk" type="primary" link @click="goRecords(invokeApp)">查看记录</el-button>
                  </template>
                  <DetailCodeBlock
                    title="响应"
                    :value="invokeResult.response"
                    copy-message="已复制响应"
                    max-height="300px"
                  />
                </DetailResultBanner>
              </div>
              <DetailEmpty v-else text="点击「走网关试调」查看响应" />
            </el-tab-pane>
            <el-tab-pane label="curl 代码" name="curl">
              <div v-if="preview?.curl" class="result-pane">
                <DetailCodeBlock
                  title="可复制到终端或 Postman"
                  :value="annotatedCurl"
                  copy-label="复制 curl"
                  copy-message="已复制 curl"
                  tone="ink"
                  max-height="320px"
                />
                <p v-if="preview.tip" class="hint">{{ preview.tip }}</p>
                <p class="hint">curl 上方注释来自工作流 schema，复制后可直接给对接同学。</p>
              </div>
              <DetailEmpty v-else text="点击「生成 curl」或试调成功后自动出现" />
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>
      <template #footer>
        <el-button text type="primary" @click="invokeVisible = false; pageTab = 'docs'">查看接入文档</el-button>
        <el-button @click="invokeVisible = false">关闭</el-button>
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
import StatusTag from '@/components/StatusTag.vue'
import OpenApiDocsPanel from '@/components/OpenApiDocsPanel.vue'
import ScheduleTriggerInput from '@/components/schedule/ScheduleTriggerInput.vue'
import DetailSection from '@/components/detail/DetailSection.vue'
import DetailCopyField from '@/components/detail/DetailCopyField.vue'
import DetailMetaList from '@/components/detail/DetailMetaList.vue'
import DetailActions from '@/components/detail/DetailActions.vue'
import DetailCodeBlock from '@/components/detail/DetailCodeBlock.vue'
import DetailResultBanner from '@/components/detail/DetailResultBanner.vue'
import DetailEmpty from '@/components/detail/DetailEmpty.vue'
import { askConfirm } from '@/utils/confirm'
import { copyText, formatTime } from '@/utils/format'
import { annotateCurlWithFields, examplePayloadFromSchema, schemaFieldGuide } from '@/utils/triggerInput'
import { pageWorkflows } from '@/api/workflow'
import { networkErrorMessage } from '@/api/http'
import { getModuleStats } from '@/api/dashboard'
import {
  bindOpenapiWorkflows,
  createOpenapiApp,
  invokeOpenapi,
  listGrantedWorkflows,
  pageOpenapiApps,
  previewOpenapiInvoke,
  resetOpenapiSecret,
  updateOpenapiApp,
} from '@/api/openapi'

const GUIDE_KEY = 'qz-openapi-guide-dismissed'

const route = useRoute()
const router = useRouter()
const pageTab = ref('apps')
const showGuide = ref(localStorage.getItem(GUIDE_KEY) !== '1')
const keyword = ref('')
const records = ref([])
const loading = ref(false)
const loadError = ref('')
const revealedKeys = reactive({})
const publishedWorkflows = ref([])
const createVisible = ref(false)
const creating = ref(false)
const createForm = reactive({ appName: '', remark: '', rateLimitQps: 10, ipWhitelist: '' })
const editVisible = ref(false)
const saving = ref(false)
const editForm = reactive({ id: null, appName: '', remark: '', rateLimitQps: 10, ipWhitelist: '' })
const grantVisible = ref(false)
const granting = ref(false)
const grantKeyword = ref('')
const currentApp = ref(null)
const grantedIds = ref([])
const drawerVisible = ref(false)
const drawerApp = ref(null)
const invokeVisible = ref(false)
const previewing = ref(false)
const invoking = ref(false)
const invokeApp = ref(null)
const invokeResultTab = ref('result')
const invokeForm = reactive({ workflowCode: '' })
const invokeInputData = ref(null)
const invokeInputRef = ref(null)
const preview = ref(null)
const invokeResult = ref(null)
const secretVisible = ref(false)
const secretConfirmed = ref(false)
const pendingGrantApp = ref(null)
const createdSecret = reactive({ appKey: '', appSecret: '' })

const stats = computed(() => ({
  apps: records.value.length,
  ready: records.value.filter((item) => item.status === 1 && item.grantedCount > 0).length,
  published: publishedWorkflows.value.length,
}))
const openapiExtra = ref({ invoke7d: 0, fail7d: 0 })

const firstReadyApp = computed(() =>
  records.value.find((item) => item.status === 1 && item.grantedCount > 0),
)

const invokeTitle = computed(() => (invokeApp.value ? `调用助手 · ${invokeApp.value.appName}` : '调用助手'))

const invokeWorkflows = computed(() => {
  const ids = new Set(grantedIds.value)
  return publishedWorkflows.value.filter((item) => ids.has(Number(item.id)))
})

const selectedInvokeWorkflow = computed(() =>
  invokeWorkflows.value.find((item) => item.workflowCode === invokeForm.workflowCode) || null,
)

const invokeFieldGuide = computed(() => schemaFieldGuide(selectedInvokeWorkflow.value?.inputSchema))

const annotatedCurl = computed(() => annotateCurlWithFields(preview.value?.curl, invokeFieldGuide.value))
const drawerMetaItems = computed(() => {
  const app = drawerApp.value
  if (!app) return []
  return [
    { label: 'QPS', value: app.rateLimitQps > 0 ? app.rateLimitQps : '不限制' },
    { label: 'IP 白名单', value: formatIps(app.ipWhitelist) },
    { label: '更新时间', value: formatTime(app.updateTime) },
    { label: '备注', value: app.remark, hidden: !app.remark },
  ]
})

const filteredGrantWorkflows = computed(() => {
  const q = grantKeyword.value.trim().toLowerCase()
  if (!q) return publishedWorkflows.value
  return publishedWorkflows.value.filter(
    (item) => item.workflowName.toLowerCase().includes(q) || item.workflowCode.toLowerCase().includes(q),
  )
})

const invokeOk = computed(() => invokeResult.value?.response?.code === 0)

watch(secretVisible, (open) => {
  if (!open) secretConfirmed.value = false
})
watch(
  () => invokeForm.workflowCode,
  () => {
    invokeInputData.value = null
  },
)

function readinessTag(row) {
  if (row.status !== 1) return { label: '已停用', type: 'info' }
  if (!row.grantedCount) return { label: '待授权', type: 'warning' }
  return { label: '可调用', type: 'success' }
}

function dismissGuide() {
  showGuide.value = false
  localStorage.setItem(GUIDE_KEY, '1')
}

function maskedKey(key, revealed) {
  if (!key) return '—'
  if (revealed) return key
  if (key.length <= 8) return '••••••••'
  return `${key.slice(0, 4)}••••${key.slice(-4)}`
}

function toggleKeyReveal(row) {
  revealedKeys[row.id] = !revealedKeys[row.id]
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [res, statsRes] = await Promise.all([
      pageOpenapiApps({ current: 1, size: 50, keyword: keyword.value }),
      getModuleStats().catch(() => null),
    ])
    records.value = res.data?.records || []
    if (statsRes?.data?.openapi) {
      openapiExtra.value = {
        invoke7d: statsRes.data.openapi.invoke7d || 0,
        fail7d: statsRes.data.openapi.fail7d || 0,
      }
    }
  } catch (error) {
    loadError.value = networkErrorMessage(error)
    records.value = []
  } finally {
    loading.value = false
  }
}

async function loadPublished() {
  const res = await pageWorkflows({ current: 1, size: 100 })
  publishedWorkflows.value = (res.data?.records || []).filter((item) => item.status === 'PUBLISHED')
}

function formatIps(raw) {
  if (!raw) return '不限制'
  try {
    const parsed = JSON.parse(raw)
    if (Array.isArray(parsed)) return parsed.length ? parsed.join(', ') : '不限制'
  } catch {
    // keep raw
  }
  return raw
}

function onRowClick(row) {
  openDetail(row)
}

function openDetail(row) {
  drawerApp.value = row
  drawerVisible.value = true
}

function openCreate() {
  createForm.appName = ''
  createForm.remark = ''
  createForm.rateLimitQps = 10
  createForm.ipWhitelist = ''
  createVisible.value = true
}

function openEdit(row) {
  editForm.id = row.id
  editForm.appName = row.appName
  editForm.remark = row.remark || ''
  editForm.rateLimitQps = row.rateLimitQps ?? 10
  editForm.ipWhitelist = formatIps(row.ipWhitelist) === '不限制' ? '' : formatIps(row.ipWhitelist)
  editVisible.value = true
  drawerVisible.value = false
}

async function saveEdit() {
  if (!editForm.appName) {
    ElMessage.warning('请填写应用名称')
    return
  }
  saving.value = true
  try {
    await updateOpenapiApp(editForm.id, {
      appName: editForm.appName,
      remark: editForm.remark,
      rateLimitQps: editForm.rateLimitQps,
      ipWhitelist: editForm.ipWhitelist,
    })
    ElMessage.success('已保存')
    editVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function saveApp() {
  if (!createForm.appName.trim()) {
    ElMessage.warning('请填写应用名称')
    return
  }
  creating.value = true
  try {
    const res = await createOpenapiApp({
      appName: createForm.appName.trim(),
      remark: createForm.remark,
      rateLimitQps: createForm.rateLimitQps,
      ipWhitelist: createForm.ipWhitelist,
    })
    createVisible.value = false
    createdSecret.appKey = res.data.appKey
    createdSecret.appSecret = res.data.appSecret
    pendingGrantApp.value = { id: res.data.id, appName: createForm.appName }
    secretVisible.value = true
    await load()
  } finally {
    creating.value = false
  }
}

function afterSecretSaved(goGrant = false) {
  if (!secretConfirmed.value) {
    ElMessage.warning('请先确认已保存密钥')
    return
  }
  secretVisible.value = false
  if (goGrant && pendingGrantApp.value) {
    const row = records.value.find((item) => item.id === pendingGrantApp.value.id) || pendingGrantApp.value
    openGrant(row)
  }
  pendingGrantApp.value = null
}

async function copyAllSecrets() {
  const text = `App Key: ${createdSecret.appKey}\nApp Secret: ${createdSecret.appSecret}`
  await copyText(text)
  ElMessage.success('已复制 Key 和 Secret')
}

async function openGrant(row) {
  currentApp.value = row
  grantKeyword.value = ''
  await loadPublished()
  const res = await listGrantedWorkflows(row.id)
  grantedIds.value = (res.data || []).map((id) => Number(id))
  grantVisible.value = true
}

function toggleGrant(id, checked) {
  const numId = Number(id)
  if (checked) {
    if (!grantedIds.value.includes(numId)) grantedIds.value.push(numId)
  } else {
    grantedIds.value = grantedIds.value.filter((item) => item !== numId)
  }
}

function selectAllGrants() {
  grantedIds.value = filteredGrantWorkflows.value.map((item) => Number(item.id))
}

async function saveGrant(andInvoke = false) {
  granting.value = true
  try {
    await bindOpenapiWorkflows(currentApp.value.id, grantedIds.value)
    ElMessage.success(grantedIds.value.length ? '授权已更新' : '已撤销全部授权')
    const app = currentApp.value
    grantVisible.value = false
    await load()
    if (drawerApp.value?.id === app?.id) {
      drawerApp.value = records.value.find((item) => item.id === app.id) || drawerApp.value
    }
    if (andInvoke && app && grantedIds.value.length) {
      const latest = records.value.find((item) => item.id === app.id) || app
      await openInvoke(latest)
    }
  } finally {
    granting.value = false
  }
}

function goRecords(row) {
  if (!row) return
  router.push({
    path: '/executions',
    query: { triggerType: 'OPENAPI', triggerAppId: String(row.id) },
  })
}

function goProblems(row) {
  if (!row) return
  router.push({
    path: '/executions',
    query: { triggerType: 'OPENAPI', triggerAppId: String(row.id), filter: 'problem' },
  })
}

async function onRowCommand(command, row) {
  if (command === 'detail') return openDetail(row)
  if (command === 'grant') return openGrant(row)
  if (command === 'edit') return openEdit(row)
  if (command === 'records') return goRecords(row)
  if (command === 'docs') {
    pageTab.value = 'docs'
    return
  }
  if (command === 'reset-secret') {
    if (!(await askConfirm(`重置后旧 Secret 立即失效。确定重置「${row.appName}」？`, '重置 Secret', { type: 'warning' }))) {
      return
    }
    const res = await resetOpenapiSecret(row.id)
    createdSecret.appKey = res.data.appKey
    createdSecret.appSecret = res.data.appSecret
    pendingGrantApp.value = null
    secretVisible.value = true
    return
  }
  if (command === 'disable' || command === 'enable') {
    await toggleStatus(row, command === 'enable' ? 1 : 0)
  }
}

function resetInvoke() {
  preview.value = null
  invokeResult.value = null
  invokeResultTab.value = 'result'
  invokeForm.workflowCode = ''
  invokeInputData.value = null
}

async function copyField(text, message) {
  await copyText(text)
  ElMessage.success(message)
}

async function toggleStatus(row, status) {
  const action = status === 1 ? '启用' : '停用'
  if (!(await askConfirm(`${action}应用「${row.appName}」？`, `${action}确认`, { type: status === 1 ? 'info' : 'warning' }))) return
  await updateOpenapiApp(row.id, {
    appName: row.appName,
    remark: row.remark,
    rateLimitQps: row.rateLimitQps,
    ipWhitelist: formatIps(row.ipWhitelist) === '不限制' ? '' : formatIps(row.ipWhitelist),
    status,
  })
  ElMessage.success(`已${action}`)
  await load()
}

async function openInvoke(row) {
  invokeApp.value = row
  resetInvoke()
  await loadPublished()
  const res = await listGrantedWorkflows(row.id)
  grantedIds.value = (res.data || []).map((id) => Number(id))
  invokeForm.workflowCode = invokeWorkflows.value[0]?.workflowCode || ''
  invokeVisible.value = true
}

function openGrantFromInvoke() {
  invokeVisible.value = false
  if (invokeApp.value) openGrant(invokeApp.value)
}

function fillInvokeExample() {
  const example = examplePayloadFromSchema(selectedInvokeWorkflow.value?.inputSchema)
  invokeInputData.value = Object.keys(example).length ? example : null
  ElMessage.success('已填入示例入参，可按实际值修改')
}

function formatExample(value) {
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function parseInvokeInput() {
  const inputError = invokeInputRef.value?.validate?.()
  if (inputError) {
    ElMessage.warning(inputError)
    return null
  }
  const fromComponent = invokeInputRef.value?.getPayload?.()
  if (fromComponent && typeof fromComponent === 'object') {
    return fromComponent
  }
  return invokeInputData.value && typeof invokeInputData.value === 'object'
    ? invokeInputData.value
    : {}
}

async function makePreview() {
  if (!invokeApp.value || !invokeForm.workflowCode) {
    ElMessage.warning('请先选择工作流')
    return
  }
  let input = invokeInputRef.value?.getPayload?.()
  const empty = !input || typeof input !== 'object' || !Object.keys(input).length
  if (empty) {
    const example = examplePayloadFromSchema(selectedInvokeWorkflow.value?.inputSchema)
    if (Object.keys(example).length) {
      invokeInputData.value = example
      input = example
    } else {
      input = {}
    }
  } else {
    const inputError = invokeInputRef.value?.validate?.()
    if (inputError) {
      ElMessage.warning(inputError)
      return
    }
  }
  previewing.value = true
  try {
    const res = await previewOpenapiInvoke(invokeApp.value.id, {
      workflowCode: invokeForm.workflowCode,
      input,
    })
    preview.value = res.data
    invokeResultTab.value = 'curl'
    ElMessage.success('已生成 curl')
  } finally {
    previewing.value = false
  }
}

async function runInvoke() {
  if (!invokeApp.value || !invokeForm.workflowCode) {
    ElMessage.warning('请先选择工作流')
    return
  }
  const input = parseInvokeInput()
  if (input == null) return
  invoking.value = true
  try {
    const res = await invokeOpenapi(invokeApp.value.id, {
      workflowCode: invokeForm.workflowCode,
      input,
    })
    invokeResult.value = res.data
    preview.value = res.data?.preview || preview.value
    invokeResultTab.value = 'result'
    if (invokeOk.value) {
      ElMessage.success('试调成功')
    } else {
      ElMessage.warning(res.data?.response?.message || '网关返回失败')
    }
  } finally {
    invoking.value = false
  }
}

async function handleRouteQuery() {
  if (route.query.tab === 'docs') pageTab.value = 'docs'
  if (route.query.action === 'create') openCreate()
  if (route.query.appId) {
    const row = records.value.find((item) => String(item.id) === String(route.query.appId))
    if (row && route.query.action === 'invoke') openInvoke(row)
    else if (row && route.query.action === 'grant') openGrant(row)
    else if (row) openDetail(row)
  }
}

onMounted(async () => {
  await Promise.all([load(), loadPublished()])
  await handleRouteQuery()
})
</script>

<style scoped>
.openapi-page { padding-bottom: 8px; }
.page-toolbar { margin-bottom: 14px; }
.tab-switch {
  display: inline-flex;
  padding: 3px;
  border-radius: 10px;
  background: var(--qz-fill);
  border: 1px solid var(--qz-border);
}
.tab-btn {
  padding: 8px 18px;
  border: none;
  border-radius: 8px;
  background: transparent;
  font-size: 13px;
  font-weight: 600;
  color: var(--qz-text-muted);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.tab-btn.active {
  background: var(--qz-card);
  color: var(--el-color-primary);
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
}
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
.flow-step {
  flex: 1;
  display: flex;
  gap: 10px;
  min-width: 0;
}
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
.flow-arrow {
  flex-shrink: 0;
  align-self: center;
  color: var(--qz-text-muted);
  font-size: 18px;
}
.flow-close {
  position: absolute;
  top: 8px;
  right: 10px;
  border: none;
  background: transparent;
  font-size: 20px;
  line-height: 1;
  color: var(--qz-text-muted);
  cursor: pointer;
}
.app-table :deep(.el-table__row) { cursor: pointer; }
.app-name-cell { display: flex; flex-direction: column; gap: 2px; }
.app-name { font-weight: 600; }
.app-remark { font-size: 12px; color: var(--qz-text-muted); }
.key-cell { display: flex; align-items: center; gap: 4px; min-width: 0; }
.copy-btn { flex-shrink: 0; }
.table-foot {
  margin: 10px 16px 12px;
  font-size: 12px;
  color: var(--qz-text-muted);
}
.docs-panel { padding: 16px 20px; }
.empty-hint { margin: 0 0 12px; color: var(--qz-text-muted); font-size: 13px; }
.drawer-stack { display: flex; flex-direction: column; gap: 12px; }
.drawer-head { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.checklist { margin-bottom: 14px; }
.check-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  font-size: 13px;
  color: var(--qz-text-muted);
}
.check-item.ok { color: var(--el-text-color-regular); }
.check-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--qz-border);
  flex-shrink: 0;
}
.check-item.ok .check-dot { background: var(--el-color-success); }
.drawer-meta { margin-top: 14px; }
.mode-alert { margin-bottom: 12px; }
.secret-row { display: flex; gap: 8px; width: 100%; }
.copy-all-btn { width: 100%; margin-bottom: 12px; }
.secret-check { margin-top: 4px; }
.hint { color: var(--qz-text-muted); margin: 0 0 12px; font-size: 13px; line-height: 1.5; }
.muted { margin-left: 8px; color: var(--qz-text-muted); font-size: 12px; }
.create-advanced { margin-top: 4px; border: none; }
.create-advanced :deep(.el-collapse-item__header) { font-size: 13px; color: var(--qz-text-muted); border: none; }
.create-advanced :deep(.el-collapse-item__wrap) { border: none; }
.grant-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.grant-toolbar .el-input { flex: 1; }
.grant-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  max-height: 360px;
  overflow-y: auto;
}
.grant-card {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--qz-border);
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.grant-card:hover,
.grant-card.active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--qz-primary-soft);
}
.grant-card-body { min-width: 0; }
.grant-name { font-size: 13px; font-weight: 600; }
.grant-code { font-size: 11px; color: var(--qz-text-muted); margin-top: 2px; word-break: break-all; }
.dialog-footer-row {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  width: 100%;
}
.grant-footer-hint {
  margin-right: auto;
  font-size: 13px;
  color: var(--qz-text-muted);
}
.mono { font-family: ui-monospace, Menlo, monospace; font-size: 12px; }
.field-guide {
  margin-bottom: 12px;
  padding: 10px 12px;
  border: 1px solid var(--qz-border);
  border-radius: 8px;
  background: var(--qz-fill);
}
.field-guide-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  font-size: 13px;
}
.field-guide-list {
  margin: 0;
  padding: 0;
  list-style: none;
}
.field-guide-list li {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  padding: 4px 0;
  font-size: 12px;
}
.field-guide-list code {
  font-family: ui-monospace, Menlo, monospace;
  background: #fff;
  padding: 1px 6px;
  border-radius: 4px;
}
.invoke-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(280px, 0.85fr);
  gap: 20px;
  min-height: 360px;
}
.invoke-btns { display: flex; gap: 8px; flex-wrap: wrap; }
.json-toolbar { display: flex; gap: 4px; margin-bottom: 6px; }
.json-input :deep(textarea) { font-family: ui-monospace, Menlo, monospace; font-size: 12px; }
.result-tabs { height: 100%; }
.result-tabs :deep(.el-tabs__content) { height: calc(100% - 40px); }
.result-pane { min-width: 0; }
@media (max-width: 900px) {
  .flow-strip { flex-direction: column; padding-right: 14px; }
  .flow-arrow { display: none; }
  .grant-grid { grid-template-columns: 1fr; }
  .invoke-layout { grid-template-columns: 1fr; }
}
</style>
