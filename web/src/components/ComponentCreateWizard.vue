<template>
  <el-dialog
    :model-value="visible"
    title="新建组件"
    width="760px"
    destroy-on-close
    :close-on-click-modal="false"
    class="component-wizard-dialog"
    @update:model-value="emit('update:visible', $event)"
    @closed="reset"
    @opened="onOpened"
  >
    <div v-if="entryMode !== 'manual'" class="entry-switch" :class="{ 'entry-switch-dual': entryOptions.length === 2 }">
      <button
        v-for="item in entryOptions"
        :key="item.value"
        type="button"
        class="entry-btn"
        :class="{ active: entryMode === item.value }"
        @click="switchEntry(item.value)"
      >
        <span class="entry-icon">{{ item.icon }}</span>
        <span class="entry-label">{{ item.label }}</span>
        <span v-if="item.sub" class="entry-sub">{{ item.sub }}</span>
      </button>
    </div>

    <div class="step-banner">
      <div class="step-banner-title">{{ bannerTitle }}</div>
      <div class="step-banner-desc">{{ bannerDesc }}</div>
    </div>

    <!-- 统一接入：curl 快捷导入 + 分步表单 -->
    <div v-show="entryMode === 'easy'" class="wizard-body">
      <el-collapse v-model="curlOpen" class="curl-quick">
        <el-collapse-item name="curl">
          <template #title>
            <div class="curl-collapse-title">
              <span>快捷导入：粘贴 curl 或完整地址</span>
              <el-tag size="small" type="info">可选</el-tag>
            </div>
          </template>
          <ComponentCurlImport compact @import="onCurlImportEasy" />
        </el-collapse-item>
      </el-collapse>

      <div class="input-divider">
        <span>或按步骤填写</span>
      </div>

      <el-alert
        v-if="curlImported"
        class="import-alert"
        type="success"
        :closable="false"
        show-icon
        title="已从 curl 识别并填入下方表单，请核对名称、地址和密钥是否正确"
      />

      <el-form ref="easyFormRef" :model="form" :rules="easyRules" label-position="top" @submit.prevent="emitSave(true)">
        <div class="easy-step">
          <div class="easy-step-num">1</div>
          <div class="easy-step-body">
            <el-form-item label="这个接口叫什么？" prop="componentName">
              <el-input
                v-model="form.componentName"
                placeholder="例如：查询订单、同步客户资料"
                maxlength="128"
                @blur="syncCode"
              />
            </el-form-item>
          </div>
        </div>

        <div class="easy-step">
          <div class="easy-step-num">2</div>
          <div class="easy-step-body">
            <el-form-item label="接口地址是什么？" prop="urlTemplate">
              <el-input
                v-model="form.urlTemplate"
                placeholder="https://api.example.com/orders"
                clearable
                @blur="onEasyUrlBlur"
                @paste="onEasyUrlPaste"
              />
              <p class="field-tip">从接口文档、Postman 或浏览器复制完整链接；若误贴了 curl 命令也会自动识别。</p>
            </el-form-item>
          </div>
        </div>

        <div class="easy-step">
          <div class="easy-step-num">3</div>
          <div class="easy-step-body">
            <el-form-item label="怎么调用这个接口？">
              <el-radio-group v-model="form.httpMethod" class="method-radio">
                <el-radio-button v-for="item in simpleMethodOptions" :key="item.value" :value="item.value">
                  {{ item.label }}
                </el-radio-button>
              </el-radio-group>
            </el-form-item>
          </div>
        </div>

        <div class="easy-step">
          <div class="easy-step-num">4</div>
          <div class="easy-step-body">
            <div class="easy-step-label">需要密钥才能访问吗？</div>
            <div class="auth-cards">
              <button
                v-for="item in simpleAuthOptions"
                :key="item.value"
                type="button"
                class="auth-card"
                :class="{ active: simpleAuth === item.value }"
                @click="selectSimpleAuth(item.value)"
              >
                <span class="auth-card-title">{{ item.label }}</span>
                <span class="auth-card-desc">{{ item.desc }}</span>
              </button>
            </div>

            <el-form-item v-if="simpleAuth === 'bearer'" label="访问令牌（Token）" class="auth-field">
              <el-input
                v-model="authState.bearerToken"
                type="password"
                show-password
                placeholder="把文档里的 Token、API Key 粘贴到这里"
              />
              <p class="field-tip">文档里可能写作 Bearer Token、访问令牌、API Token，内容一般是一长串字符。</p>
            </el-form-item>

            <div v-else-if="simpleAuth === 'basic'" class="auth-field auth-inline-fields">
              <el-form-item label="用户名">
                <el-input v-model="authState.basicUsername" placeholder="接口文档中的用户名" />
              </el-form-item>
              <el-form-item label="密码">
                <el-input v-model="authState.basicPassword" type="password" show-password placeholder="接口文档中的密码" />
              </el-form-item>
            </div>

            <el-button
              v-if="simpleAuth === 'more'"
              class="more-auth-btn"
              text
              type="primary"
              @click="advancedOpen = 'advanced'"
            >
              在下方高级设置中配置 API Key、企业微信等
            </el-button>
          </div>
        </div>

        <el-form-item label="用途说明（选填）">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="一句话说明这个接口用来做什么" />
        </el-form-item>

        <el-collapse v-model="advancedOpen" class="easy-advanced">
          <el-collapse-item title="高级设置（一般不用改）" name="advanced">
            <div class="param-section param-section-flat">
              <div class="param-section-head">
                <span class="param-section-title">调用时需要传参数吗？</span>
                <el-radio-group v-model="paramMode" size="small">
                  <el-radio-button value="none">不需要</el-radio-button>
                  <el-radio-button value="custom">需要</el-radio-button>
                </el-radio-group>
              </div>
              <p v-if="paramMode === 'none'" class="param-none-hint">大多数接口不需要额外参数。</p>
              <div v-else class="param-list">
                <div v-for="(row, index) in form.params" :key="index" class="param-card">
                  <div class="param-card-head">
                    <span>参数 {{ index + 1 }}</span>
                    <el-button type="danger" link @click="removeParam(index)">删除</el-button>
                  </div>
                  <div class="param-inline">
                    <el-input v-model="row.key" placeholder="参数名" />
                    <el-input v-model="row.description" placeholder="说明（选填）" />
                    <el-checkbox v-model="row.required">必填</el-checkbox>
                  </div>
                </div>
                <el-button size="small" @click="addParam">+ 添加参数</el-button>
              </div>
            </div>

            <div v-if="simpleAuth === 'more'" class="full-auth-panel">
              <ComponentAuthPanel v-model="authState" embedded @append-wecom-token="appendWecomTokenPlaceholder" />
            </div>

            <el-form label-width="88px" class="timeout-form">
              <el-form-item label="超时">
                <el-input-number v-model="form.timeoutMs" :min="500" :step="500" />
                <span class="muted"> 毫秒</span>
              </el-form-item>
              <el-form-item label="失败重试">
                <el-input-number v-model="form.retryTimes" :min="0" :max="5" />
                <span class="muted"> 次</span>
              </el-form-item>
            </el-form>
          </el-collapse-item>
        </el-collapse>
      </el-form>
    </div>

    <!-- 数据库脚本 -->
    <div v-show="entryMode === 'sql'" class="wizard-body">
      <DatabaseSqlPanel :form="dbForm" />
    </div>

    <!-- 健康检查 -->
    <div v-show="entryMode === 'health'" class="wizard-body health-pane">
      <div class="health-card">
        <div class="health-title">本机健康检查</div>
        <p class="health-desc">已预填地址，无需 Token，适合第一次体验完整流程。</p>
        <div class="health-url mono">GET http://127.0.0.1:18080/api/health</div>
        <el-form label-position="top" class="name-form">
          <el-form-item label="组件名称">
            <el-input v-model="form.componentName" maxlength="128" />
          </el-form-item>
        </el-form>
      </div>
    </div>

    <!-- 高级手动配置 -->
    <div v-show="entryMode === 'manual'" class="wizard-body">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="emitSave(true)">
        <el-tabs v-model="configTab" class="config-tabs">
          <el-tab-pane label="连接" name="connect">
            <ComponentCurlImport compact @import="onCurlImportEasy" />
            <el-form-item label="请求方式" prop="httpMethod">
              <el-radio-group v-model="form.httpMethod" class="method-radio">
                <el-radio-button v-for="item in allMethodOptions" :key="item.value" :value="item.value">
                  {{ item.label }}
                </el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="接口地址" prop="urlTemplate">
              <el-input
                v-model="form.urlTemplate"
                placeholder="https://api.example.com/..."
                clearable
                @blur="normalizeUrlField"
              />
            </el-form-item>
          </el-tab-pane>
          <el-tab-pane label="鉴权" name="auth">
            <ComponentAuthPanel v-model="authState" embedded @append-wecom-token="appendWecomTokenPlaceholder" />
          </el-tab-pane>
          <el-tab-pane label="参数" name="params">
            <div class="param-section param-section-flat">
              <div class="param-section-head">
                <span class="param-section-title">调用时需要传参数吗？</span>
                <el-radio-group v-model="paramMode" size="small">
                  <el-radio-button value="none">不需要</el-radio-button>
                  <el-radio-button value="custom">需要</el-radio-button>
                </el-radio-group>
              </div>
              <p v-if="paramMode === 'none'" class="param-none-hint">大多数接口不需要额外参数。</p>
              <div v-else class="param-list">
                <div v-for="(row, index) in form.params" :key="index" class="param-card">
                  <div class="param-card-head">
                    <span>参数 {{ index + 1 }}</span>
                    <el-button type="danger" link @click="removeParam(index)">删除</el-button>
                  </div>
                  <div class="param-inline">
                    <el-input v-model="row.key" placeholder="参数名" />
                    <el-input v-model="row.description" placeholder="说明（选填）" />
                    <el-checkbox v-model="row.required">必填</el-checkbox>
                  </div>
                </div>
                <el-button size="small" @click="addParam">+ 添加参数</el-button>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
        <el-form-item label="组件名称">
          <el-input v-model="form.componentName" maxlength="128" @blur="syncCode" />
        </el-form-item>
        <el-form-item label="用途说明（选填）">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <div class="wizard-footer">
        <div class="footer-left">
          <span v-if="footerHint" class="footer-hint">{{ footerHint }}</span>
        </div>
        <div class="footer-right">
          <el-button @click="emit('update:visible', false)">取消</el-button>
          <el-button
            v-if="entryMode === 'health'"
            type="primary"
            :loading="saving"
            @click="quickCreateHealth"
          >
            创建并试连通
          </el-button>
          <el-button
            v-else
            type="primary"
            :loading="saving"
            :disabled="!canSave"
            @click="emitSave(true)"
          >
            {{ entryMode === 'sql' ? '保存并试运行 SQL' : '保存并试连通' }}
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import ComponentAuthPanel from '@/components/ComponentAuthPanel.vue'
import ComponentCurlImport from '@/components/ComponentCurlImport.vue'
import DatabaseSqlPanel from '@/components/DatabaseSqlPanel.vue'
import { applyCurlImport, parseCurl } from '@/utils/parseCurl'
import {
  buildExtraConfig,
  createEmptyAuthState,
  validateAuthState,
} from '@/utils/componentAuth'
import { fieldsToSchema } from '@/utils/schema'
import { defaultDbResponseSchema, extractNamedParams, paramsToRows } from '@/utils/sqlParams'

const props = defineProps({
  visible: { type: Boolean, default: false },
  saving: { type: Boolean, default: false },
  initialMode: { type: String, default: 'easy' },
})

const emit = defineEmits(['update:visible', 'save'])

const entryOptions = [
  { value: 'easy', label: '接入接口', sub: '填地址或 curl', icon: '🔗' },
  { value: 'sql', label: '数据库脚本', sub: '写 SQL 当接口', icon: '🗄️' },
  { value: 'health', label: '先体验一下', sub: '免配置', icon: '💚' },
]

const simpleMethodOptions = [
  { value: 'GET', label: '查询数据' },
  { value: 'POST', label: '提交数据' },
  { value: 'PUT', label: '更新数据' },
  { value: 'DELETE', label: '删除数据' },
]

const simpleAuthOptions = [
  { value: 'none', label: '不需要', desc: '公开接口或已在别处鉴权' },
  { value: 'bearer', label: '访问令牌', desc: '文档里的 Token / API Key' },
  { value: 'basic', label: '账号密码', desc: '用户名 + 密码' },
  { value: 'more', label: '更多方式', desc: 'API Key、企业微信等' },
]

const allMethodOptions = [
  { value: 'GET', label: 'GET 查询' },
  { value: 'POST', label: 'POST 提交' },
  { value: 'PUT', label: 'PUT 更新' },
  { value: 'DELETE', label: 'DELETE 删除' },
]

const entryMode = ref('easy')
const configTab = ref('connect')
const paramMode = ref('none')
const simpleAuth = ref('none')
const advancedOpen = ref('')
const curlOpen = ref('')
const curlImported = ref(false)
const formRef = ref(null)
const easyFormRef = ref(null)
const authState = ref(createEmptyAuthState())

const form = reactive({
  componentName: '',
  componentCode: '',
  httpMethod: 'GET',
  urlTemplate: '',
  timeoutMs: 10000,
  retryTimes: 0,
  description: '',
  params: [],
})

const dbForm = reactive({
  componentName: '',
  componentCode: '',
  description: '',
  sql: '',
  datasourceId: null,
  accessMode: 'READ',
  maxRows: 200,
  timeoutMs: 10000,
  params: [],
})

const rules = {
  urlTemplate: [
    { required: true, message: '请填写接口地址', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        const normalized = normalizeUrl(value)
        if (!normalized || !/^https?:\/\//i.test(normalized)) {
          callback(new Error('请填写有效的 http(s) 地址'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

const easyRules = {
  componentName: [
    { required: true, message: '请给这个接口起个名字', trigger: 'blur' },
    { min: 2, message: '名称至少 2 个字', trigger: 'blur' },
  ],
  urlTemplate: rules.urlTemplate,
}

const bannerCopy = {
  easy: {
    title: '两种方式，任选其一',
    desc: '有 Postman curl 可粘贴快速填入；没有的话按下面步骤填写名称和地址即可。',
  },
  sql: {
    title: '把 SQL 变成可编排节点',
    desc: '选择数据源，编写 :userId 这类命名参数 SQL。执行时走 PreparedStatement，避免拼接注入。',
  },
  health: {
    title: '快速体验',
    desc: '无需配置，点下方按钮即可完成创建并试连通。',
  },
  manual: {
    title: '高级手动配置',
    desc: '按连接、鉴权、参数分 Tab 填写，适合复杂接口。',
  },
}

const bannerTitle = computed(() => bannerCopy[entryMode.value]?.title || '')
const bannerDesc = computed(() => bannerCopy[entryMode.value]?.desc || '')
const hasConnection = computed(() => Boolean(normalizeUrl(form.urlTemplate)))
const canSave = computed(() => {
  if (entryMode.value === 'health') return true
  if (entryMode.value === 'sql') {
    return Boolean(dbForm.componentName.trim()) && Boolean(dbForm.sql.trim()) && Boolean(dbForm.datasourceId)
  }
  if (entryMode.value === 'easy') {
    return Boolean(form.componentName.trim()) && hasConnection.value
  }
  return hasConnection.value
})

const footerHint = computed(() => {
  if (entryMode.value === 'easy') {
    if (curlImported.value && hasConnection.value) {
      return '已识别 curl，请核对下方信息后保存'
    }
    return hasConnection.value ? '保存后会自动试连通' : '可粘贴 curl 导入，或填写名称和地址'
  }
  if (entryMode.value === 'sql') {
    return dbForm.datasourceId ? '保存后会按当前参数试运行 SQL' : '请先选择或新建 MySQL 数据源'
  }
  return ''
})

function resolveInitialMode() {
  const mode = props.initialMode || 'easy'
  if (mode === 'curl') {
    return { entry: 'easy', curlOpen: 'curl' }
  }
  return { entry: mode, curlOpen: '' }
}

function isLocalHost(url) {
  return /^(https?:\/\/)?(127\.0\.0\.1|localhost)(:\d+)?/i.test(url)
}

function normalizeUrl(value) {
  let url = String(value || '').trim()
  if (!url) return ''
  if (!/^https?:\/\//i.test(url)) {
    const prefix = isLocalHost(url) ? 'http://' : 'https://'
    url = `${prefix}${url.replace(/^\/+/, '')}`
  }
  return url
}

function normalizeUrlField() {
  if (form.urlTemplate.trim()) {
    form.urlTemplate = normalizeUrl(form.urlTemplate)
  }
}

function looksLikeCurl(value) {
  const raw = String(value || '').trim()
  return /^curl(\s|$)/i.test(raw) || (raw.includes('--header') && raw.includes('http'))
}

function tryParseCurlInUrl(silent = true) {
  const raw = form.urlTemplate.trim()
  if (!looksLikeCurl(raw)) return false
  const parsed = parseCurl(raw)
  if (!parsed.ok) {
    if (!silent) ElMessage.warning(parsed.message)
    return false
  }
  applyCurlImport(parsed, { form, authState, paramMode })
  syncSimpleAuthFromState()
  if (parsed.componentName && !form.componentName.trim()) {
    form.componentName = parsed.componentName
  }
  syncCode()
  ElMessage.success('已从 curl 命令自动识别地址和鉴权')
  return true
}

function onEasyUrlBlur() {
  if (tryParseCurlInUrl()) return
  normalizeUrlField()
  if (!form.componentName.trim()) {
    const guessed = guessNameFromUrl(form.urlTemplate)
    if (guessed) form.componentName = guessed
  }
  syncCode()
}

function onEasyUrlPaste() {
  window.setTimeout(() => onEasyUrlBlur(), 0)
}

function selectSimpleAuth(value) {
  simpleAuth.value = value
  if (value === 'none') {
    authState.value = { ...authState.value, authType: 'none' }
    return
  }
  if (value === 'bearer') {
    authState.value = { ...authState.value, authType: 'bearer' }
    return
  }
  if (value === 'basic') {
    authState.value = { ...authState.value, authType: 'basic' }
    return
  }
  if (value === 'more') {
    advancedOpen.value = 'advanced'
  }
}

function syncSimpleAuthFromState() {
  const type = authState.value.authType || 'none'
  if (type === 'none') simpleAuth.value = 'none'
  else if (type === 'bearer') simpleAuth.value = 'bearer'
  else if (type === 'basic') simpleAuth.value = 'basic'
  else simpleAuth.value = 'more'
}

function generateCode(name) {
  const ascii = String(name || '').trim().toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '')
  if (ascii && ascii.length >= 2) return `custom.http.${ascii}`
  return `custom.http.${Date.now().toString(36)}`
}

function syncCode() {
  if (!form.componentCode && form.componentName.trim()) {
    form.componentCode = generateCode(form.componentName)
  }
}

function guessNameFromUrl(url) {
  try {
    const pathname = new URL(normalizeUrl(url)).pathname
    const parts = pathname.split('/').filter(Boolean)
    const last = parts[parts.length - 1] || ''
    if (last === 'health') return '健康检查'
    return last.replace(/[-_]/g, ' ').slice(0, 32)
  } catch {
    return ''
  }
}

function switchEntry(mode) {
  entryMode.value = mode
  if (mode === 'health') {
    form.componentName = form.componentName || '健康检查'
    form.description = form.description || '检查本服务是否正常运行'
    form.httpMethod = 'GET'
    form.urlTemplate = 'http://127.0.0.1:18080/api/health'
    paramMode.value = 'none'
    form.params = []
    authState.value = createEmptyAuthState()
    simpleAuth.value = 'none'
  }
  if (mode === 'easy') {
    syncSimpleAuthFromState()
  }
}

function openManual(tab) {
  entryMode.value = 'manual'
  configTab.value = tab
}

function onCurlImport(parsed) {
  applyCurlImport(parsed, { form, authState, paramMode })
  if (parsed.componentName && !form.componentName.trim()) {
    form.componentName = parsed.componentName
  }
  syncSimpleAuthFromState()
  syncCode()
  if (entryMode.value === 'manual') {
    configTab.value = parsed.paramMode === 'custom' ? 'params' : (parsed.authState?.authType !== 'none' ? 'auth' : 'connect')
  }
  if (parsed.paramMode === 'custom') {
    advancedOpen.value = 'advanced'
  }
  if (parsed.authState?.authType && parsed.authState.authType !== 'none' && parsed.authState.authType !== 'bearer' && parsed.authState.authType !== 'basic') {
    simpleAuth.value = 'more'
    advancedOpen.value = 'advanced'
  }
}

function onCurlImportEasy(parsed) {
  onCurlImport(parsed)
  curlImported.value = true
}

function paramLocation() {
  return form.httpMethod === 'GET' || form.httpMethod === 'DELETE' ? 'query' : 'body'
}

function addParam() {
  form.params.push({ key: '', type: 'string', required: false, description: '', location: paramLocation() })
}

function removeParam(index) {
  form.params.splice(index, 1)
  if (!form.params.length) paramMode.value = 'none'
}

function appendWecomTokenPlaceholder() {
  const placeholder = 'access_token=${access_token}'
  const url = form.urlTemplate.trim()
  if (!url) {
    form.urlTemplate = `https://qyapi.weixin.qq.com/cgi-bin/example?${placeholder}`
    return
  }
  if (!url.includes('access_token')) {
    form.urlTemplate = url.includes('?') ? `${url}&${placeholder}` : `${url}?${placeholder}`
  }
}

function reset() {
  const initial = resolveInitialMode()
  entryMode.value = initial.entry
  curlOpen.value = initial.curlOpen
  configTab.value = 'connect'
  paramMode.value = 'none'
  simpleAuth.value = 'none'
  advancedOpen.value = ''
  curlImported.value = false
  form.componentName = ''
  form.componentCode = ''
  form.httpMethod = 'GET'
  form.urlTemplate = ''
  form.timeoutMs = 10000
  form.retryTimes = 0
  form.description = ''
  form.params = []
  dbForm.componentName = ''
  dbForm.componentCode = ''
  dbForm.description = ''
  dbForm.sql = ''
  dbForm.datasourceId = null
  dbForm.accessMode = 'READ'
  dbForm.maxRows = 200
  dbForm.timeoutMs = 10000
  dbForm.params = []
  authState.value = createEmptyAuthState()
  formRef.value?.clearValidate()
  easyFormRef.value?.clearValidate()
  if (entryMode.value === 'health') {
    switchEntry('health')
  }
}

function onOpened() {
  if (entryMode.value === 'health') {
    switchEntry('health')
  }
}

function validateParams() {
  if (paramMode.value === 'none') return true
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

function ensureComponentName() {
  if (!form.componentName.trim()) {
    form.componentName = guessNameFromUrl(form.urlTemplate) || '新接口'
  }
  syncCode()
}

function buildDatabasePayload() {
  if (!dbForm.componentName.trim()) {
    throw new Error('请填写组件名称')
  }
  if (!dbForm.datasourceId) {
    throw new Error('请选择 MySQL 数据源')
  }
  if (!dbForm.sql.trim()) {
    throw new Error('请填写 SQL 脚本')
  }
  dbForm.params = paramsToRows(extractNamedParams(dbForm.sql), dbForm.params)
  const code = (dbForm.componentCode || `custom.db.${Date.now().toString(36)}`).trim()
  if (!/^[a-z0-9._-]+$/.test(code)) {
    throw new Error('编码格式不正确')
  }
  return {
    componentCode: code,
    componentName: dbForm.componentName.trim(),
    provider: 'DATABASE',
    category: 'DATABASE',
    httpMethod: 'QUERY',
    urlTemplate: dbForm.sql.trim(),
    timeoutMs: dbForm.timeoutMs,
    retryTimes: 0,
    description: dbForm.description,
    bodySchema: fieldsToSchema(dbForm.params),
    responseSchema: defaultDbResponseSchema(),
    extraConfig: {
      kind: 'DATABASE',
      datasourceId: dbForm.datasourceId,
      accessMode: dbForm.accessMode,
      maxRows: dbForm.maxRows,
    },
  }
}

function buildPayload() {
  ensureComponentName()
  const cleaned = paramMode.value === 'none' ? [] : form.params.filter((item) => String(item.key || '').trim())
  const queryFields = cleaned.filter((item) => item.location === 'query')
  const bodyFields = cleaned.filter((item) => item.location !== 'query')
  const code = (form.componentCode || generateCode(form.componentName)).trim()
  if (!/^[a-z0-9._-]+$/.test(code)) {
    throw new Error('编码格式不正确')
  }
  return {
    componentCode: code,
    componentName: form.componentName.trim(),
    provider: 'CUSTOM',
    category: 'HTTP',
    httpMethod: form.httpMethod,
    urlTemplate: normalizeUrl(form.urlTemplate),
    timeoutMs: form.timeoutMs,
    retryTimes: form.retryTimes,
    description: form.description,
    querySchema: fieldsToSchema(queryFields),
    bodySchema: fieldsToSchema(bodyFields),
    extraConfig: buildExtraConfig(authState.value),
  }
}

async function emitSave(testAfter) {
  if (entryMode.value === 'sql') {
    try {
      emit('save', { payload: buildDatabasePayload(), testAfter })
    } catch (error) {
      ElMessage.warning(error.message || '请检查填写内容')
    }
    return
  }
  if (!hasConnection.value) {
    ElMessage.warning(entryMode.value === 'easy' ? '请填写接口地址' : '请先粘贴 curl 或填写接口地址')
    return
  }
  form.urlTemplate = normalizeUrl(form.urlTemplate)

  if (entryMode.value === 'easy') {
    const valid = await easyFormRef.value?.validate().catch(() => false)
    if (!valid) return
  } else if (entryMode.value === 'manual') {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) {
      configTab.value = 'connect'
      return
    }
  }

  for (const row of form.params) {
    row.location = paramLocation()
  }
  if (!validateParams()) return
  const authCheck = validateAuthState(authState.value)
  if (!authCheck.valid) {
    ElMessage.warning(authCheck.message)
    if (entryMode.value === 'manual') configTab.value = 'auth'
    else if (entryMode.value === 'easy') {
      advancedOpen.value = 'advanced'
      if (simpleAuth.value === 'more') {
        // stay in advanced panel
      } else {
        simpleAuth.value = authState.value.authType === 'basic' ? 'basic' : 'bearer'
      }
    } else {
      openManual('auth')
    }
    return
  }
  try {
    emit('save', { payload: buildPayload(), testAfter })
  } catch (error) {
    ElMessage.warning(error.message || '请检查填写内容')
  }
}

function quickCreateHealth() {
  form.componentCode = generateCode(form.componentName || '健康检查')
  form.httpMethod = 'GET'
  form.urlTemplate = 'http://127.0.0.1:18080/api/health'
  form.componentName = form.componentName || '健康检查'
  paramMode.value = 'none'
  authState.value = createEmptyAuthState()
  emitSave(true)
}

watch(paramMode, (mode) => {
  if (mode === 'custom' && !form.params.length) addParam()
  if (mode === 'none') form.params = []
})

watch(() => form.httpMethod, () => {
  const location = paramLocation()
  for (const row of form.params) row.location = location
})

watch(() => props.visible, (open) => {
  if (open) {
    reset()
  }
})
</script>

<style scoped>
.entry-switch {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 14px;
}
.entry-switch-dual {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.curl-quick {
  margin-bottom: 4px;
  border: none;
}
.curl-quick :deep(.el-collapse-item__header) {
  font-weight: 600;
  font-size: 14px;
  border-bottom: none;
}
.curl-quick :deep(.el-collapse-item__wrap) {
  border-bottom: none;
}
.curl-collapse-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.input-divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 16px 0;
  color: var(--qz-text-muted);
  font-size: 12px;
}
.input-divider::before,
.input-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--qz-border);
}
.import-alert {
  margin-bottom: 14px;
}
.entry-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 10px 8px;
  border: 1px solid var(--qz-border);
  border-radius: var(--qz-radius);
  background: var(--qz-card);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.entry-btn:hover,
.entry-btn.active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--qz-primary-soft);
}
.entry-icon { font-size: 20px; }
.entry-label { font-size: 13px; font-weight: 600; white-space: nowrap; }
.entry-sub { font-size: 11px; color: var(--qz-text-muted); white-space: nowrap; }
.step-banner {
  margin-bottom: 16px;
  padding: 12px 14px;
  border-radius: var(--qz-radius);
  background: var(--qz-primary-soft);
  border: 1px solid var(--el-color-primary-light-7);
}
.step-banner-title { font-size: 15px; font-weight: 700; }
.step-banner-desc { margin-top: 4px; font-size: 13px; color: var(--qz-text-muted); line-height: 1.5; }
.wizard-body { max-height: 58vh; overflow-y: auto; padding-right: 4px; }
.easy-step {
  display: flex;
  gap: 12px;
  margin-bottom: 4px;
}
.easy-step-num {
  flex-shrink: 0;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 28px;
}
.easy-step-body { flex: 1; min-width: 0; }
.easy-step-label {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 10px;
  color: var(--el-text-color-regular);
}
.field-tip {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--qz-text-muted);
  line-height: 1.5;
}
.auth-cards {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 12px;
}
.auth-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid var(--qz-border);
  border-radius: 8px;
  background: var(--qz-card);
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.auth-card:hover,
.auth-card.active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--qz-primary-soft);
}
.auth-card-title { font-size: 13px; font-weight: 600; }
.auth-card-desc { font-size: 11px; color: var(--qz-text-muted); line-height: 1.4; }
.auth-field { margin-top: 4px; }
.auth-inline-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.more-auth-btn { margin-bottom: 8px; }
.easy-advanced { margin-top: 8px; }
.full-auth-panel { margin: 12px 0; }
.timeout-form { margin-top: 12px; }
.muted { color: var(--qz-text-muted); font-size: 13px; }
.checklist-card {
  padding: 14px;
  margin-bottom: 14px;
  border: 1px solid var(--qz-border);
  border-radius: var(--qz-radius);
  background: var(--qz-fill);
}
.checklist-title { font-size: 13px; font-weight: 600; margin-bottom: 10px; }
.checklist-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  font-size: 13px;
  flex-wrap: wrap;
}
.checklist-url { flex: 1; min-width: 0; word-break: break-all; color: var(--qz-text-muted); }
.check-ok { color: var(--el-color-success); flex-shrink: 0; }
.check-muted { color: var(--qz-border); flex-shrink: 0; }
.name-form { margin-top: 4px; }
.manual-link { padding-left: 0; margin-bottom: 8px; }
.health-pane { padding-top: 4px; }
.health-card {
  padding: 20px;
  border: 1px solid var(--qz-border);
  border-radius: var(--qz-radius);
  background: var(--qz-fill);
}
.health-title { font-size: 17px; font-weight: 700; }
.health-desc { margin: 8px 0 12px; color: var(--qz-text-muted); font-size: 13px; line-height: 1.5; }
.health-url { font-size: 13px; color: var(--el-color-success); margin-bottom: 14px; }
.config-tabs :deep(.el-tabs__header) { margin-bottom: 14px; }
.method-radio { flex-wrap: wrap; }
.param-section-flat { margin-top: 4px; }
.param-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.param-section-title { font-weight: 600; font-size: 14px; }
.param-none-hint { margin: 0; font-size: 13px; color: var(--qz-text-muted); }
.param-card {
  padding: 10px 12px;
  margin-bottom: 10px;
  border: 1px solid var(--qz-border);
  border-radius: 8px;
  background: var(--qz-fill);
}
.param-card-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--qz-text-muted);
}
.param-inline {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 8px;
  align-items: center;
}
.wizard-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}
.footer-hint { font-size: 12px; color: var(--qz-text-muted); }
.footer-right { display: flex; gap: 8px; }
.mono { font-family: ui-monospace, Menlo, monospace; font-size: 12px; }
@media (max-width: 640px) {
  .entry-switch { grid-template-columns: 1fr; }
  .param-inline { grid-template-columns: 1fr; }
  .auth-cards { grid-template-columns: 1fr; }
  .auth-inline-fields { grid-template-columns: 1fr; }
  .easy-step-num { margin-top: 0; }
}
</style>
