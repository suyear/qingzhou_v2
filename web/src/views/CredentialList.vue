<template>
  <div>
    <PageHeader title="凭证管理" desc="保存微信、访问令牌、账号密码或数据库连接，接口和工作流里直接引用。">
      <el-input
        v-model="keyword"
        class="search-input"
        placeholder="搜索名称"
        clearable
        @keyup.enter="load"
        @clear="load"
      />
      <el-button @click="load">查询</el-button>
      <el-button type="primary" @click="openCreate">新建凭证</el-button>
    </PageHeader>
    <PageState :error="loadError" @retry="load" />
    <div v-if="!showGuide && moduleStats" class="stat-grid cols-3">
      <div class="stat-card">
        <div class="stat-label">全部凭证</div>
        <div class="stat-num">{{ moduleStats.total ?? 0 }}</div>
      </div>
      <div class="stat-card stat-ok">
        <div class="stat-label">已启用</div>
        <div class="stat-num">{{ moduleStats.enabled ?? 0 }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">类型分布</div>
        <div class="stat-hint">{{ typeDistText }}</div>
      </div>
    </div>
    <el-alert v-if="showGuide" class="mode-alert" type="info" :closable="true" show-icon @close="showGuide = false">
      <template #title>怎么选？</template>
      <div class="cred-guide">
        <span><strong>企业微信</strong>：填企业 ID 和密钥，可测连通。</span>
        <span><strong>接口密钥</strong>：访问令牌、账号密码等，给 HTTP 组件引用。</span>
        <span><strong>数据库</strong>：连业务库，给数据库脚本组件用。</span>
      </div>
    </el-alert>
    <div class="qz-panel">
    <el-table class="qz-table" :data="records" v-loading="loading" stripe>
      <el-table-column prop="credentialName" label="名称" min-width="150" />
      <el-table-column label="类型" width="120">
        <template #default="{ row }">
          <el-tag size="small" :type="typeTag(row.credentialType)">
            {{ credentialTypeLabel(row.credentialType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="子类型" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="isHttpAuthCredential(row.credentialType)">{{ httpAuthTypeLabel(row.authType) }}</span>
          <span v-else-if="isDatabaseCredential(row.credentialType)">{{ dbTypeLabel(row.dbType || 'mysql') }}</span>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="作用域" min-width="160">
        <template #default="{ row }">
          <span v-if="row.scope === 'WORKFLOW'">{{ workflowTitle(row.workflowId) }}</span>
          <span v-else>全局共享</span>
        </template>
      </el-table-column>
      <el-table-column label="连接信息" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="isDatabaseCredential(row.credentialType)" class="mono">{{ dbTarget(row) }}</span>
          <span v-else-if="row.credentialType === 'WECOM'">{{ row.corpId || '—' }}</span>
          <span v-else-if="isHttpAuthCredential(row.credentialType)" class="mono">{{ httpTarget(row) }}</span>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="密钥" width="90">
        <template #default="{ row }">
          <el-tag :type="row.hasSecret ? 'success' : 'danger'" size="small">
            {{ row.hasSecret ? '已配置' : '未配置' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <StatusTag kind="enable" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <div class="qz-ops">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button type="primary" link :loading="row._testing" @click="onTest(row)">测连通</el-button>
            <el-button v-if="row.status !== 1" type="success" link @click="onEnable(row)">启用</el-button>
            <el-button v-else type="warning" link @click="onDisable(row)">停用</el-button>
            <el-button type="danger" link @click="onDelete(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty v-if="!loading && !loadError" :description="emptyText">
          <el-button type="primary" @click="openCreate">新建凭证</el-button>
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
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑凭证' : '新建凭证'" width="720px" destroy-on-close>
      <el-form :model="form" label-width="120px">
        <el-form-item label="名称" required>
          <el-input v-model="form.credentialName" placeholder="例如：订单库只读 / 开放平台令牌" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.credentialType" style="width: 100%" :disabled="!!form.id" @change="onTypeChange">
            <el-option v-for="item in CREDENTIAL_TYPES" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <p class="form-hint">{{ typeHint }}</p>
        </el-form-item>
        <el-form-item label="作用域">
          <el-select v-model="form.scope" style="width: 100%">
            <el-option label="全局共享" value="GLOBAL" />
            <el-option label="工作流独立" value="WORKFLOW" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.scope === 'WORKFLOW'" label="绑定工作流" required>
          <el-select v-model="form.workflowId" filterable placeholder="选择工作流" style="width: 100%">
            <el-option
              v-for="wf in workflows"
              :key="wf.id"
              :label="`${wf.workflowName} (${wf.workflowCode})`"
              :value="wf.id"
            />
          </el-select>
        </el-form-item>

        <template v-if="form.credentialType === 'WECOM'">
          <el-form-item label="企业 ID" required>
            <el-input v-model="form.corpId" placeholder="CorpId" />
          </el-form-item>
          <el-form-item label="应用 ID">
            <el-input v-model="form.agentId" placeholder="AgentId，选填" />
          </el-form-item>
          <el-form-item :label="form.id ? '密钥（留空不改）' : '密钥'" :required="!form.id">
            <el-input v-model="form.secret" type="password" show-password placeholder="CorpSecret" />
          </el-form-item>
        </template>

        <template v-else-if="form.credentialType === 'DATABASE'">
          <el-form-item label="数据库类型" required>
            <el-select v-model="form.dbType" style="width: 100%" @change="onDbTypeChange">
              <el-option-group label="常用">
                <el-option v-for="item in DB_TYPES_COMMON" :key="item.value" :label="item.label" :value="item.value" />
              </el-option-group>
              <el-option-group label="更多">
                <el-option v-for="item in DB_TYPES_MORE" :key="item.value" :label="item.label" :value="item.value" />
              </el-option-group>
            </el-select>
          </el-form-item>
          <el-form-item label="主机" required>
            <el-input v-model="form.dbHost" placeholder="127.0.0.1" />
          </el-form-item>
          <el-form-item label="端口">
            <el-input-number v-model="form.dbPort" :min="1" :max="65535" />
          </el-form-item>
          <el-form-item :label="form.dbType === 'oracle' ? 'SID/服务名' : '数据库'" required>
            <el-input v-model="form.dbName" placeholder="demo" />
          </el-form-item>
          <el-form-item label="用户名" required>
            <el-input v-model="form.dbUsername" />
          </el-form-item>
          <el-form-item :label="form.id ? '密码（留空不改）' : '密码'" :required="!form.id">
            <el-input v-model="form.secret" type="password" show-password />
          </el-form-item>
        </template>

        <template v-else-if="form.credentialType === 'HTTP_AUTH'">
          <el-form-item label="常用方式" required>
            <AuthTypePicker
              v-model="form.authType"
              :options="HTTP_AUTH_COMMON"
              :columns="3"
              compact
            />
          </el-form-item>
          <el-collapse v-model="advancedAuthOpen" class="adv-auth-collapse">
            <el-collapse-item title="高级鉴权（OAuth2 / JWT / 签名 / 证书等）" name="advanced">
              <AuthTypePicker
                v-model="form.authType"
                :options="HTTP_AUTH_ADVANCED"
                :columns="2"
                compact
              />
            </el-collapse-item>
          </el-collapse>

          <template v-if="['basic', 'digest'].includes(form.authType)">
            <el-form-item label="用户名" required>
              <el-input v-model="form.username" />
            </el-form-item>
            <el-form-item :label="form.id ? '密码（留空不改）' : '密码'" :required="!form.id">
              <el-input v-model="form.password" type="password" show-password />
            </el-form-item>
          </template>

          <template v-else-if="form.authType === 'bearer'">
            <el-form-item :label="form.id ? 'Token（留空不改）' : 'Token'" :required="!form.id">
              <el-input v-model="form.secret" type="password" show-password />
            </el-form-item>
          </template>

          <template v-else-if="form.authType === 'apiKey'">
            <el-form-item label="参数名" required>
              <el-input v-model="form.apiKeyName" placeholder="X-API-Key" />
            </el-form-item>
            <el-form-item label="位置">
              <el-radio-group v-model="form.apiKeyIn">
                <el-radio-button value="header">Header</el-radio-button>
                <el-radio-button value="query">Query</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item :label="form.id ? 'Key（留空不改）' : 'Key'" :required="!form.id">
              <el-input v-model="form.apiKeyValue" type="password" show-password />
            </el-form-item>
          </template>

          <template v-else-if="form.authType === 'cookie'">
            <el-form-item :label="form.id ? 'Cookie（留空不改）' : 'Cookie'" :required="!form.id">
              <el-input v-model="form.cookie" type="textarea" :rows="2" placeholder="session=xxx; path=/" />
            </el-form-item>
          </template>

          <template v-else-if="form.authType === 'jwt'">
            <el-form-item label="模式">
              <el-radio-group v-model="form.jwtMode">
                <el-radio-button value="static">静态 Token</el-radio-button>
                <el-radio-button value="sign">本地签发</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <template v-if="form.jwtMode === 'static'">
              <el-form-item :label="form.id ? 'JWT（留空不改）' : 'JWT'" :required="!form.id">
                <el-input v-model="form.secret" type="password" show-password />
              </el-form-item>
            </template>
            <template v-else>
              <el-form-item label="算法">
                <el-select v-model="form.jwtAlg" style="width: 100%">
                  <el-option label="HS256" value="HS256" />
                  <el-option label="RS256" value="RS256" />
                </el-select>
              </el-form-item>
              <el-form-item label="Issuer">
                <el-input v-model="form.jwtIssuer" />
              </el-form-item>
              <el-form-item label="Audience">
                <el-input v-model="form.jwtAudience" />
              </el-form-item>
              <el-form-item label="Subject">
                <el-input v-model="form.jwtSubject" />
              </el-form-item>
              <el-form-item label="有效期(秒)">
                <el-input-number v-model="form.jwtTtlSeconds" :min="60" :step="60" />
              </el-form-item>
              <el-form-item :label="form.id ? '签名密钥（留空不改）' : '签名密钥'" :required="!form.id">
                <el-input
                  v-model="form.jwtSecret"
                  type="textarea"
                  :rows="form.jwtAlg === 'RS256' ? 4 : 2"
                  :placeholder="form.jwtAlg === 'RS256' ? 'PKCS8 私钥 PEM' : 'HS256 密钥'"
                />
              </el-form-item>
            </template>
          </template>

          <template v-else-if="form.authType === 'oauth2_cc'">
            <el-form-item label="Token URL" required>
              <el-input v-model="form.tokenUrl" placeholder="https://example.com/oauth/token" />
            </el-form-item>
            <el-form-item label="Client ID" required>
              <el-input v-model="form.clientId" />
            </el-form-item>
            <el-form-item :label="form.id ? 'Client Secret（留空不改）' : 'Client Secret'" :required="!form.id">
              <el-input v-model="form.clientSecret" type="password" show-password />
            </el-form-item>
          </template>

          <template v-else-if="form.authType === 'aksk'">
            <el-form-item label="Access Key" required>
              <el-input v-model="form.accessKey" />
            </el-form-item>
            <el-form-item :label="form.id ? 'Secret Key（留空不改）' : 'Secret Key'" :required="!form.id">
              <el-input v-model="form.secretKey" type="password" show-password />
            </el-form-item>
            <el-form-item label="HMAC 算法">
              <el-select v-model="form.hmacAlg" style="width: 100%">
                <el-option label="HmacSHA256" value="HmacSHA256" />
                <el-option label="HmacSHA1" value="HmacSHA1" />
              </el-select>
            </el-form-item>
            <el-form-item label="签名头">
              <el-input v-model="form.signHeader" placeholder="X-Signature" />
            </el-form-item>
            <el-form-item label="签名模板">
              <el-input v-model="form.signTemplate" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item label="附加头">
              <el-checkbox v-model="form.includeTimestamp">时间戳</el-checkbox>
              <el-checkbox v-model="form.includeNonce">Nonce</el-checkbox>
            </el-form-item>
          </template>

          <template v-else-if="form.authType === 'mtls'">
            <el-form-item :label="form.id ? '客户端证书（留空不改）' : '客户端证书 PEM'" :required="!form.id">
              <el-input v-model="form.clientCert" type="textarea" :rows="4" placeholder="-----BEGIN CERTIFICATE-----" />
            </el-form-item>
            <el-form-item :label="form.id ? '私钥（留空不改）' : '私钥 PEM'" :required="!form.id">
              <el-input v-model="form.privateKey" type="textarea" :rows="4" placeholder="-----BEGIN PRIVATE KEY-----" />
            </el-form-item>
          </template>
        </template>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="testVisible" title="凭证连通结果" width="480px">
      <DetailResultBanner
        v-if="testResult"
        :ok="testResult.success"
        :title="testResult.success ? '连通成功' : '连通失败'"
        :message="testResult.message"
      />
      <DetailEmpty v-else text="暂无连通结果" />
      <template #footer>
        <el-button @click="testVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import PageState from '@/components/PageState.vue'
import DetailResultBanner from '@/components/detail/DetailResultBanner.vue'
import DetailEmpty from '@/components/detail/DetailEmpty.vue'
import StatusTag from '@/components/StatusTag.vue'
import AuthTypePicker from '@/components/AuthTypePicker.vue'
import { askConfirm } from '@/utils/confirm'
import { pageWorkflows } from '@/api/workflow'
import { networkErrorMessage } from '@/api/http'
import { getModuleStats } from '@/api/dashboard'
import {
  CREDENTIAL_TYPES,
  DB_TYPES_COMMON,
  DB_TYPES_MORE,
  HTTP_AUTH_ADVANCED,
  HTTP_AUTH_COMMON,
  HTTP_AUTH_COMMON_VALUES,
  buildCredentialPayload,
  createEmptyCredentialForm,
  credentialTypeLabel,
  dbTypeLabel,
  defaultDbPort,
  httpAuthTypeLabel,
  isDatabaseCredential,
  isHttpAuthCredential,
  normalizeCredentialType,
} from '@/utils/credentialTypes'
import {
  createCredential,
  deleteCredential,
  disableCredential,
  enableCredential,
  pageCredentials,
  testCredential,
  updateCredential,
} from '@/api/credential'

const keyword = ref('')
const records = ref([])
const total = ref(0)
const current = ref(1)
const size = ref(10)
const loading = ref(false)
const loadError = ref('')
const showGuide = ref(true)
const moduleStats = ref(null)
const testVisible = ref(false)
const testResult = ref(null)
const workflows = ref([])
const workflowMap = computed(() => {
  const map = {}
  for (const item of workflows.value) {
    map[Number(item.id)] = item
  }
  return map
})

const typeDistText = computed(() => {
  const byType = moduleStats.value?.byType || {}
  const parts = Object.entries(byType).map(([k, v]) => `${credentialTypeLabel(k)} ${v}`)
  return parts.length ? parts.join(' · ') : '暂无'
})

function workflowTitle(id) {
  const wf = workflowMap.value[id] || workflowMap.value[Number(id)]
  return wf ? `${wf.workflowName} (${wf.workflowCode})` : `工作流 #${id}`
}

function typeTag(type) {
  const t = normalizeCredentialType(type)
  if (t === 'WECOM') return 'success'
  if (t === 'DATABASE') return 'warning'
  return 'info'
}

function dbTarget(row) {
  if (!row?.dbHost) return '—'
  return `${row.dbType || 'mysql'}://${row.dbHost}:${row.dbPort || 3306}/${row.dbName || ''}`
}

function httpTarget(row) {
  if (row.authType === 'oauth2_cc') return row.tokenUrl || 'OAuth2'
  if (row.authType === 'apiKey') return row.apiKeyName || 'API Key'
  if (row.authType === 'aksk') return row.accessKey || 'AK/SK'
  if (row.username) return row.username
  return httpAuthTypeLabel(row.authType)
}

const dialogVisible = ref(false)
const saving = ref(false)
const advancedAuthOpen = ref([])
const form = reactive(createEmptyCredentialForm())

const typeHint = computed(() => {
  if (form.credentialType === 'WECOM') return '用于企业微信接口，保存后可测连通。'
  if (form.credentialType === 'DATABASE') return '用于数据库脚本组件。密码加密存储，可测连通。'
  return '用于 HTTP 接口。常用令牌/账号密码即可；OAuth2、证书等在下方「高级鉴权」。'
})

const emptyText = computed(() => (
  keyword.value ? '没有匹配的凭证' : '还没有凭证。可按企业微信 / 接口密钥 / 数据库三类创建。'
))

function onTypeChange() {
  if (form.credentialType === 'DATABASE') {
    form.dbType = form.dbType || 'mysql'
    form.dbPort = defaultDbPort(form.dbType)
  }
  if (form.credentialType === 'HTTP_AUTH') {
    form.authType = form.authType || 'bearer'
  }
}

function onDbTypeChange() {
  form.dbPort = defaultDbPort(form.dbType)
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [res, statsRes] = await Promise.all([
      pageCredentials({ current: current.value, size: size.value, keyword: keyword.value }),
      getModuleStats().catch(() => null),
    ])
    records.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
    moduleStats.value = statsRes?.data?.credentials || null
  } catch (error) {
    loadError.value = networkErrorMessage(error)
    records.value = []
  } finally {
    loading.value = false
  }
}

async function loadWorkflows() {
  const res = await pageWorkflows({ current: 1, size: 100 })
  workflows.value = res.data?.records || []
}

function resetForm() {
  Object.assign(form, createEmptyCredentialForm())
}

function openCreate() {
  resetForm()
  advancedAuthOpen.value = []
  dialogVisible.value = true
}

function openEdit(row) {
  resetForm()
  form.id = row.id
  form.credentialName = row.credentialName
  form.credentialType = normalizeCredentialType(row.credentialType)
  form.scope = row.scope || 'GLOBAL'
  form.workflowId = row.workflowId
  form.corpId = row.corpId || ''
  form.agentId = row.agentId || ''
  form.remark = row.remark || ''
  form.dbType = row.dbType || 'mysql'
  form.dbHost = row.dbHost || '127.0.0.1'
  form.dbPort = row.dbPort || defaultDbPort(form.dbType)
  form.dbName = row.dbName || ''
  form.dbUsername = row.dbUsername || ''
  form.authType = row.authType || 'bearer'
  form.username = row.username || ''
  form.apiKeyName = row.apiKeyName || 'X-API-Key'
  form.apiKeyIn = row.apiKeyIn || 'header'
  form.tokenUrl = row.tokenUrl || ''
  form.clientId = row.clientId || ''
  form.jwtMode = row.jwtMode || 'static'
  form.jwtAlg = row.jwtAlg || 'HS256'
  form.jwtIssuer = row.jwtIssuer || ''
  form.jwtAudience = row.jwtAudience || ''
  form.jwtSubject = row.jwtSubject || ''
  form.jwtTtlSeconds = row.jwtTtlSeconds || 3600
  form.accessKey = row.accessKey || ''
  form.hmacAlg = row.hmacAlg || 'HmacSHA256'
  form.signHeader = row.signHeader || 'X-Signature'
  form.signTemplate = row.signTemplate || '{method}\\n{path}\\n{timestamp}\\n{nonce}\\n{accessKey}'
  form.includeTimestamp = row.includeTimestamp !== false
  form.includeNonce = row.includeNonce !== false
  form.timestampHeader = row.timestampHeader || 'X-Timestamp'
  form.nonceHeader = row.nonceHeader || 'X-Nonce'
  advancedAuthOpen.value = HTTP_AUTH_COMMON_VALUES.includes(form.authType) ? [] : ['advanced']
  dialogVisible.value = true
}

function validateForm() {
  if (!form.credentialName) return '请填写名称'
  if (form.scope === 'WORKFLOW' && !form.workflowId) return '请选择绑定的工作流'
  if (form.credentialType === 'WECOM') {
    if (!form.corpId) return '请填写 CorpId'
    if (!form.id && !form.secret) return '请填写 Secret'
  }
  if (form.credentialType === 'DATABASE') {
    if (!form.dbHost || !form.dbName || !form.dbUsername) return '请填写数据库主机、库名和用户名'
    if (!form.id && !form.secret) return '请填写数据库密码'
  }
  if (form.credentialType === 'HTTP_AUTH') {
    const t = form.authType
    if (['basic', 'digest'].includes(t)) {
      if (!form.username) return '请填写用户名'
      if (!form.id && !form.password) return '请填写密码'
    } else if (t === 'bearer' && !form.id && !form.secret) return '请填写 Token'
    else if (t === 'apiKey') {
      if (!form.apiKeyName) return '请填写 API Key 参数名'
      if (!form.id && !form.apiKeyValue) return '请填写 API Key'
    } else if (t === 'cookie' && !form.id && !form.cookie) return '请填写 Cookie'
    else if (t === 'jwt') {
      if (form.jwtMode === 'sign') {
        if (!form.id && !form.jwtSecret) return '请填写签名密钥'
      } else if (!form.id && !form.secret) return '请填写 JWT'
    } else if (t === 'oauth2_cc') {
      if (!form.tokenUrl || !form.clientId) return '请填写 Token URL 与 Client ID'
      if (!form.id && !form.clientSecret) return '请填写 Client Secret'
    } else if (t === 'aksk') {
      if (!form.accessKey) return '请填写 Access Key'
      if (!form.id && !form.secretKey) return '请填写 Secret Key'
    } else if (t === 'mtls') {
      if (!form.id && (!form.clientCert || !form.privateKey)) return '请填写证书与私钥'
    }
  }
  return ''
}

async function save() {
  const message = validateForm()
  if (message) {
    ElMessage.warning(message)
    return
  }
  saving.value = true
  try {
    const payload = buildCredentialPayload(form)
    if (form.id) {
      await updateCredential(form.id, payload)
    } else {
      await createCredential(payload)
    }
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onTest(row) {
  row._testing = true
  try {
    const res = await testCredential(row.id)
    testResult.value = res.data || { success: false, message: '连通失败' }
    testVisible.value = true
    if (res.data?.success) ElMessage.success(res.data.message)
    else ElMessage.warning(res.data?.message || '连通失败')
  } finally {
    row._testing = false
  }
}

async function onEnable(row) {
  if (!(await askConfirm(`启用凭证「${row.credentialName}」？`, '启用确认', { type: 'info' }))) return
  await enableCredential(row.id)
  ElMessage.success('已启用')
  await load()
}

async function onDisable(row) {
  if (!(await askConfirm(`停用「${row.credentialName}」后，依赖它的组件将无法鉴权。`, '停用确认'))) return
  await disableCredential(row.id)
  ElMessage.success('已停用')
  await load()
}

async function onDelete(row) {
  if (!(await askConfirm(`删除凭证「${row.credentialName}」？`))) return
  await deleteCredential(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(async () => {
  await Promise.all([load(), loadWorkflows()])
})
</script>

<style scoped>
.cred-guide {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 4px;
  font-size: 13px;
  line-height: 1.5;
}
.form-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--qz-text-muted);
  line-height: 1.45;
}
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; }
.adv-auth-collapse {
  margin: 0 0 12px 120px;
}
.adv-auth-collapse :deep(.el-collapse-item__header) {
  font-size: 13px;
  color: var(--qz-text-muted);
}
</style>
