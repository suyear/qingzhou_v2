<template>
  <div>
    <PageHeader title="凭证管理" desc="保存微信、访问令牌、账号密码或数据库连接，接口和工作流里直接引用。">
      <el-button type="primary" @click="openCreate">新建凭证</el-button>
    </PageHeader>
    <PageState :error="loadError" @retry="load" />
    <div class="filter-bar qz-panel">
      <el-input
        v-model="keyword"
        class="search-input"
        placeholder="搜索名称"
        clearable
        @keyup.enter="load"
        @clear="load"
      />
      <el-button type="primary" @click="load">查询</el-button>
    </div>
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
    <el-table
      class="qz-table is-clickable"
      :data="records"
      v-loading="loading"
      stripe
      highlight-current-row
      @row-click="openDetail"
    >
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
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <div class="qz-ops" @click.stop>
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button type="primary" link :loading="row._testing" @click="onTest(row)">测连通</el-button>
            <el-dropdown trigger="click" @command="(cmd) => onRowCommand(cmd, row)">
              <el-button type="primary" link>更多</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="detail">查看详情</el-dropdown-item>
                  <el-dropdown-item v-if="row.status !== 1" command="enable">启用</el-dropdown-item>
                  <el-dropdown-item v-else command="disable">停用</el-dropdown-item>
                  <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
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
    <p v-if="records.length" class="table-foot">点击行查看详情 · 共 {{ total }} 条</p>
    </div>

    <el-drawer
      v-model="drawerVisible"
      :title="drawerRow?.credentialName || '凭证详情'"
      size="560px"
      class="qz-detail-drawer"
    >
      <template v-if="drawerRow">
        <div class="drawer-stack">
          <DetailSection title="基本信息">
            <div class="drawer-tags">
              <StatusTag kind="enable" :value="drawerRow.status" />
              <el-tag size="small" :type="typeTag(drawerRow.credentialType)">
                {{ credentialTypeLabel(drawerRow.credentialType) }}
              </el-tag>
              <el-tag size="small" :type="drawerRow.hasSecret ? 'success' : 'danger'">
                {{ drawerRow.hasSecret ? '密钥已配置' : '密钥未配置' }}
              </el-tag>
            </div>
            <DetailMetaList :items="drawerMetaItems" />
          </DetailSection>
          <DetailActions>
            <el-button type="primary" @click="openEdit(drawerRow); drawerVisible = false">编辑</el-button>
            <el-button :loading="drawerRow._testing" @click="onTest(drawerRow)">测连通</el-button>
            <el-button v-if="drawerRow.status !== 1" type="success" @click="onEnable(drawerRow)">启用</el-button>
            <el-button v-else type="warning" @click="onDisable(drawerRow)">停用</el-button>
            <el-button type="danger" @click="onDelete(drawerRow)">删除</el-button>
          </DetailActions>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑凭证' : '新建凭证'" width="720px" destroy-on-close>
      <el-form :model="form" label-position="top">
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
        <div class="dialog-footer">
          <div class="probe-status">
            <template v-if="!form.id">
              <span v-if="probePassed" class="probe-ok">已测通，可保存</span>
              <span v-else-if="probeMessage" class="probe-fail">{{ probeMessage }}</span>
              <span v-else class="probe-hint">请先测连通，通过后再加入</span>
            </template>
          </div>
          <div class="dialog-actions">
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button :loading="probing" @click="onProbeForm">测连通</el-button>
            <el-button
              type="primary"
              :loading="saving"
              :disabled="!form.id && !probePassed"
              @click="save"
            >
              {{ form.id ? '保存' : '测通后加入' }}
            </el-button>
          </div>
        </div>
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
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import PageState from '@/components/PageState.vue'
import DetailResultBanner from '@/components/detail/DetailResultBanner.vue'
import DetailEmpty from '@/components/detail/DetailEmpty.vue'
import DetailSection from '@/components/detail/DetailSection.vue'
import DetailMetaList from '@/components/detail/DetailMetaList.vue'
import DetailActions from '@/components/detail/DetailActions.vue'
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
  probeCredential,
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
const drawerVisible = ref(false)
const drawerRow = ref(null)
const saving = ref(false)
const probing = ref(false)
const probePassed = ref(false)
const probeMessage = ref('')
const advancedAuthOpen = ref([])
const form = reactive(createEmptyCredentialForm())

watch(form, () => {
  if (probePassed.value || probeMessage.value) {
    probePassed.value = false
    probeMessage.value = ''
  }
}, { deep: true })

const drawerMetaItems = computed(() => {
  const row = drawerRow.value
  if (!row) return []
  const subtype = isHttpAuthCredential(row.credentialType)
    ? httpAuthTypeLabel(row.authType)
    : (isDatabaseCredential(row.credentialType) ? dbTypeLabel(row.dbType || 'mysql') : '—')
  const conn = isDatabaseCredential(row.credentialType)
    ? dbTarget(row)
    : (row.credentialType === 'WECOM'
      ? (row.corpId || '—')
      : (isHttpAuthCredential(row.credentialType) ? httpTarget(row) : '—'))
  return [
    { label: '类型', value: credentialTypeLabel(row.credentialType) },
    { label: '子类型', value: subtype },
    { label: '作用域', value: row.scope === 'WORKFLOW' ? workflowTitle(row.workflowId) : '全局共享' },
    { label: '连接', value: conn, mono: true },
    { label: '备注', value: row.remark || '—' },
  ]
})

function openDetail(row) {
  drawerRow.value = row
  drawerVisible.value = true
}

async function onRowCommand(cmd, row) {
  if (cmd === 'detail') {
    openDetail(row)
    return
  }
  if (cmd === 'enable') {
    await onEnable(row)
    return
  }
  if (cmd === 'disable') {
    await onDisable(row)
    return
  }
  if (cmd === 'delete') {
    await onDelete(row)
  }
}

const typeHint = computed(() => {
  if (form.credentialType === 'WECOM') return '填写后先点「测连通」，通过后再加入。'
  if (form.credentialType === 'DATABASE') return '填写连接信息后先测连通，通过后再加入。密码加密存储。'
  return '填写鉴权信息后先测连通；OAuth2/证书会实际校验，其它类型校验配置完整性。'
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
  probePassed.value = false
  probeMessage.value = ''
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

async function onProbeForm() {
  const message = validateForm()
  if (message) {
    ElMessage.warning(message)
    return
  }
  probing.value = true
  probePassed.value = false
  probeMessage.value = ''
  try {
    const payload = buildCredentialPayload(form)
    let res
    if (form.id && !hasSecretInput()) {
      res = await testCredential(form.id)
    } else {
      res = await probeCredential(payload)
    }
    const data = res.data || { success: false, message: '连通失败' }
    testResult.value = data
    testVisible.value = true
    if (data.success) {
      probePassed.value = true
      probeMessage.value = ''
      ElMessage.success(data.message || '连通成功')
    } else {
      probePassed.value = false
      probeMessage.value = data.message || '连通失败'
      ElMessage.warning(probeMessage.value)
    }
  } catch (error) {
    probePassed.value = false
    probeMessage.value = networkErrorMessage(error)
    ElMessage.error(probeMessage.value)
  } finally {
    probing.value = false
  }
}

/** 编辑时是否填写了新密钥；有则走 probe，否则走已入库 test */
function hasSecretInput() {
  if (form.credentialType === 'WECOM' || form.credentialType === 'DATABASE') {
    return Boolean(form.secret)
  }
  if (form.credentialType !== 'HTTP_AUTH') return false
  const t = form.authType
  if (['basic', 'digest'].includes(t)) return Boolean(form.password)
  if (t === 'bearer' || (t === 'jwt' && form.jwtMode !== 'sign')) return Boolean(form.secret)
  if (t === 'jwt' && form.jwtMode === 'sign') return Boolean(form.jwtSecret)
  if (t === 'apiKey') return Boolean(form.apiKeyValue)
  if (t === 'cookie') return Boolean(form.cookie)
  if (t === 'oauth2_cc') return Boolean(form.clientSecret)
  if (t === 'aksk') return Boolean(form.secretKey)
  if (t === 'mtls') return Boolean(form.clientCert || form.privateKey)
  return false
}

async function save() {
  const message = validateForm()
  if (message) {
    ElMessage.warning(message)
    return
  }
  if (!form.id && !probePassed.value) {
    ElMessage.warning('请先测连通，通过后再加入')
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
    ElMessage.success(form.id ? '已保存' : '已加入')
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
.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}
.dialog-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.probe-status {
  font-size: 13px;
  line-height: 1.4;
  text-align: left;
  min-height: 20px;
}
.probe-hint { color: var(--qz-text-muted); }
.probe-ok { color: var(--el-color-success); }
.probe-fail { color: var(--el-color-danger); }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; }
.drawer-stack { display: flex; flex-direction: column; gap: 12px; }
.drawer-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 12px; }
.adv-auth-collapse {
  margin: 0 0 12px;
}
.adv-auth-collapse :deep(.el-collapse-item__header) {
  font-size: 13px;
  color: var(--qz-text-muted);
}
</style>
