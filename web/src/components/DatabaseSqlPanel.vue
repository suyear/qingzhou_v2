<template>
  <div class="db-panel">
    <el-alert type="info" :closable="false" show-icon class="db-alert">
      <template #title>把一段 SQL 变成工作流节点</template>
      <div class="db-alert-body">
        选择数据源，用 <code>:param</code> 写命名参数。保存后可在设计器里像普通接口一样编排。
        默认禁止多条语句；只读组件只能 SELECT。
      </div>
    </el-alert>

    <el-form label-position="top">
      <el-form-item label="组件名称" required>
        <el-input v-model="form.componentName" placeholder="例如：按 ID 查询用户" maxlength="128" @blur="syncCode" />
      </el-form-item>
      <el-form-item label="数据源" required>
        <div class="ds-row">
          <el-select
            v-model="form.datasourceId"
            filterable
            placeholder="选择已配置的数据库数据源"
            style="flex: 1"
          >
            <el-option
              v-for="item in datasources"
              :key="item.id"
              :label="datasourceLabel(item)"
              :value="item.id"
            />
          </el-select>
          <el-button @click="openCreateDs">新建数据源</el-button>
        </div>
        <p v-if="!datasources.length" class="field-tip">还没有数据库数据源，请先新建（支持 MySQL / PG / SQL Server / Oracle）。</p>
      </el-form-item>
      <el-form-item label="访问模式">
        <el-radio-group v-model="form.accessMode">
          <el-radio-button value="READ">只读查询</el-radio-button>
          <el-radio-button value="WRITE">允许写入</el-radio-button>
        </el-radio-group>
        <p class="field-tip">
          {{ form.accessMode === 'WRITE'
            ? '允许 SELECT / INSERT / UPDATE / DELETE。禁止 DDL、存储过程和多语句。'
            : '只允许 SELECT / SHOW / DESCRIBE / EXPLAIN。' }}
        </p>
      </el-form-item>
      <el-form-item label="SQL 脚本" required>
        <el-input
          v-model="form.sql"
          type="textarea"
          :rows="8"
          class="sql-input"
          placeholder="SELECT id, name FROM users WHERE id = :userId"
          @input="syncParams"
        />
      </el-form-item>
      <el-form-item label="入参（由 :参数名 自动识别，可改说明）">
        <div v-if="!form.params.length" class="param-none">当前 SQL 没有命名参数，将按原文执行。</div>
        <div v-for="(row, index) in form.params" :key="row.key || index" class="param-card">
          <div class="param-inline">
            <el-input :model-value="row.key" disabled />
            <el-input v-model="row.description" placeholder="说明" />
            <el-checkbox v-model="row.required">必填</el-checkbox>
          </div>
        </div>
      </el-form-item>
      <el-form-item label="用途说明（选填）">
        <el-input v-model="form.description" type="textarea" :rows="2" placeholder="这段 SQL 在工作流里做什么" />
      </el-form-item>
      <div class="adv-row">
        <el-form-item label="超时">
          <el-input-number v-model="form.timeoutMs" :min="500" :step="500" />
          <span class="muted"> 毫秒</span>
        </el-form-item>
        <el-form-item label="最多返回行数">
          <el-input-number v-model="form.maxRows" :min="1" :max="2000" :step="50" />
        </el-form-item>
      </div>
    </el-form>

    <el-dialog v-model="dsVisible" title="新建数据库数据源" width="520px" append-to-body destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="dsForm.credentialName" placeholder="例如：业务库只读" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="dsForm.dbType" style="width: 100%" @change="onDsTypeChange">
            <el-option v-for="item in DB_TYPES" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="主机" required>
          <el-input v-model="dsForm.dbHost" placeholder="127.0.0.1" />
        </el-form-item>
        <el-form-item label="端口">
          <el-input-number v-model="dsForm.dbPort" :min="1" :max="65535" />
        </el-form-item>
        <el-form-item :label="dsForm.dbType === 'oracle' ? 'SID' : '数据库'" required>
          <el-input v-model="dsForm.dbName" placeholder="demo" />
        </el-form-item>
        <el-form-item label="用户名" required>
          <el-input v-model="dsForm.dbUsername" placeholder="qingzhou" />
        </el-form-item>
        <el-form-item label="密码" required>
          <el-input v-model="dsForm.secret" type="password" show-password placeholder="不会回显，加密存储" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dsVisible = false">取消</el-button>
        <el-button :loading="dsTesting" @click="testDs">试连通</el-button>
        <el-button type="primary" :loading="dsSaving" @click="saveDs">保存并选用</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createCredential, pageCredentials, testCredential } from '@/api/credential'
import { extractNamedParams, paramsToRows } from '@/utils/sqlParams'
import { DB_TYPES, defaultDbPort, isDatabaseCredential } from '@/utils/credentialTypes'

const props = defineProps({
  form: { type: Object, required: true },
})

const form = props.form
const datasources = ref([])
const dsVisible = ref(false)
const dsSaving = ref(false)
const dsTesting = ref(false)
const pendingTestId = ref(null)
const dsForm = reactive({
  credentialName: '',
  dbType: 'mysql',
  dbHost: '127.0.0.1',
  dbPort: 3306,
  dbName: '',
  dbUsername: '',
  secret: '',
})

function datasourceLabel(item) {
  const host = item.dbHost ? `${item.dbType || 'mysql'}://${item.dbHost}:${item.dbPort || 3306}/${item.dbName || ''}` : ''
  return host ? `${item.credentialName}（${host}）` : item.credentialName
}

function onDsTypeChange() {
  dsForm.dbPort = defaultDbPort(dsForm.dbType)
}

function generateCode(name) {
  const ascii = String(name || '').trim().toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '')
  if (ascii && ascii.length >= 2) return `custom.db.${ascii}`
  return `custom.db.${Date.now().toString(36)}`
}

function syncCode() {
  if (!form.componentCode && form.componentName.trim()) {
    form.componentCode = generateCode(form.componentName)
  }
}

function syncParams() {
  form.params = paramsToRows(extractNamedParams(form.sql), form.params)
}

async function loadDatasources() {
  const res = await pageCredentials({ current: 1, size: 100 })
  datasources.value = (res.data?.records || []).filter((item) => isDatabaseCredential(item.credentialType) && item.status === 1)
}

function openCreateDs() {
  dsForm.credentialName = ''
  dsForm.dbType = 'mysql'
  dsForm.dbHost = '127.0.0.1'
  dsForm.dbPort = 3306
  dsForm.dbName = ''
  dsForm.dbUsername = ''
  dsForm.secret = ''
  pendingTestId.value = null
  dsVisible.value = true
}

function buildDsPayload() {
  return {
    credentialName: dsForm.credentialName.trim(),
    credentialType: 'DATABASE',
    scope: 'GLOBAL',
    dbType: dsForm.dbType,
    dbHost: dsForm.dbHost.trim(),
    dbPort: dsForm.dbPort,
    dbName: dsForm.dbName.trim(),
    dbUsername: dsForm.dbUsername.trim(),
    secret: dsForm.secret,
    remark: '数据库组件数据源',
  }
}

async function saveDs() {
  if (!dsForm.credentialName || !dsForm.dbHost || !dsForm.dbName || !dsForm.dbUsername || !dsForm.secret) {
    ElMessage.warning('请填写数据源名称、主机、数据库、用户名和密码')
    return
  }
  dsSaving.value = true
  try {
    const res = await createCredential(buildDsPayload())
    await loadDatasources()
    form.datasourceId = res.data?.id
    dsVisible.value = false
    ElMessage.success('数据源已保存，密码已加密存储')
  } finally {
    dsSaving.value = false
  }
}

async function testDs() {
  if (!dsForm.credentialName || !dsForm.dbHost || !dsForm.dbName || !dsForm.dbUsername || !dsForm.secret) {
    ElMessage.warning('请先填完整再试连通')
    return
  }
  dsTesting.value = true
  try {
    if (!pendingTestId.value) {
      const res = await createCredential(buildDsPayload())
      pendingTestId.value = res.data?.id
      form.datasourceId = res.data?.id
      await loadDatasources()
    }
    const test = await testCredential(pendingTestId.value)
    if (test.data?.success) ElMessage.success(test.data.message)
    else ElMessage.warning(test.data?.message || '连通失败')
  } finally {
    dsTesting.value = false
  }
}

onMounted(loadDatasources)

defineExpose({ loadDatasources, syncParams, generateCode })
</script>

<style scoped>
.db-alert { margin-bottom: 14px; }
.db-alert-body { margin-top: 4px; font-size: 13px; color: var(--qz-text-muted); line-height: 1.5; }
.ds-row { display: flex; gap: 8px; width: 100%; }
.field-tip { margin: 6px 0 0; font-size: 12px; color: var(--qz-text-muted); line-height: 1.5; }
.sql-input :deep(textarea) { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 13px; }
.param-none { font-size: 13px; color: var(--qz-text-muted); }
.param-card {
  padding: 8px 10px;
  margin-bottom: 8px;
  border: 1px solid var(--qz-border);
  border-radius: 8px;
  background: var(--qz-fill);
}
.param-inline {
  display: grid;
  grid-template-columns: 140px 1fr auto;
  gap: 8px;
  align-items: center;
}
.adv-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.muted { color: var(--qz-text-muted); font-size: 13px; margin-left: 6px; }
@media (max-width: 640px) {
  .param-inline, .adv-row { grid-template-columns: 1fr; }
}
</style>
