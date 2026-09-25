<template>
  <div>
    <PageHeader title="License 管理" desc="查看授权状态、席位用量，导入或生成演示 License。">
      <el-button @click="load">刷新</el-button>
    </PageHeader>
    <PageState :error="loadError" @retry="load" />

    <div class="stat-grid cols-4" v-loading="loading">
      <div class="stat-card" :class="statusCardClass">
        <div class="stat-label">状态</div>
        <div class="stat-num">{{ statusLabel }}</div>
        <div class="stat-hint">{{ license.message || '—' }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">客户</div>
        <div class="stat-num text-sm">{{ license.customer || '—' }}</div>
        <div class="stat-hint">签发方 {{ license.issuer || '—' }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">席位</div>
        <div class="stat-num">{{ license.usedSeats ?? 0 }} / {{ license.seats ?? '不限' }}</div>
        <div class="stat-hint">已用 / 授权上限</div>
      </div>
      <div class="stat-card" :class="license.writeBlocked ? '' : 'stat-ok'">
        <div class="stat-label">写操作</div>
        <div class="stat-num text-sm">{{ license.writeBlocked ? '已冻结' : '允许' }}</div>
        <div class="stat-hint">到期 {{ license.expiresAt || '—' }}</div>
      </div>
    </div>

    <div class="qz-panel form-panel">
      <h3>导入 License</h3>
      <p class="muted">粘贴供应商提供的 <code>payload.signature</code> 文本后导入。导入成功立即生效。</p>
      <el-input
        v-model="licenseText"
        type="textarea"
        :rows="5"
        placeholder="粘贴 License 文本…"
        class="mb12"
      />
      <div class="actions">
        <el-button type="primary" :loading="importing" :disabled="!licenseText.trim()" @click="onImport">
          导入 License
        </el-button>
        <el-button :loading="generating" @click="onGenerateDemo">生成一年期演示 License</el-button>
      </div>
      <el-alert
        v-if="generatedHint"
        class="mt12"
        type="success"
        :closable="false"
        show-icon
        title="演示 License 已填入上方文本框，确认无误后点「导入 License」。"
      />
    </div>

    <div class="qz-panel form-panel" v-if="featureList.length">
      <h3>授权能力</h3>
      <div class="feature-tags">
        <el-tag v-for="f in featureList" :key="f" type="info" effect="plain">{{ f }}</el-tag>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import PageState from '@/components/PageState.vue'
import { generateDemoLicense, importLicense, licenseStatus } from '@/api/auth'
import { networkErrorMessage } from '@/api/http'

const loading = ref(false)
const loadError = ref('')
const importing = ref(false)
const generating = ref(false)
const generatedHint = ref(false)
const licenseText = ref('')
const license = reactive({})

const STATUS_LABEL = {
  NONE: '未授权',
  ACTIVE: '有效',
  EXPIRED: '已过期',
  INVALID: '无效',
}

const statusLabel = computed(() => STATUS_LABEL[license.status] || license.status || '—')
const statusCardClass = computed(() => {
  if (license.status === 'ACTIVE') return 'stat-ok'
  if (license.status === 'EXPIRED' || license.status === 'INVALID') return 'stat-bad'
  return ''
})

const featureList = computed(() => {
  if (!license.featuresJson) return []
  try {
    const parsed = typeof license.featuresJson === 'string'
      ? JSON.parse(license.featuresJson)
      : license.featuresJson
    if (Array.isArray(parsed)) return parsed.map(String)
    if (parsed && typeof parsed === 'object') return Object.keys(parsed)
  } catch {
    /* ignore */
  }
  return []
})

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await licenseStatus()
    Object.keys(license).forEach((k) => delete license[k])
    Object.assign(license, res.data || {})
  } catch (e) {
    loadError.value = networkErrorMessage(e)
  } finally {
    loading.value = false
  }
}

async function onImport() {
  if (!licenseText.value.trim()) {
    ElMessage.warning('请先粘贴 License 文本')
    return
  }
  importing.value = true
  try {
    const res = await importLicense({ licenseText: licenseText.value })
    Object.keys(license).forEach((k) => delete license[k])
    Object.assign(license, res.data || {})
    ElMessage.success('License 已导入')
    licenseText.value = ''
    generatedHint.value = false
  } finally {
    importing.value = false
  }
}

async function onGenerateDemo() {
  generating.value = true
  try {
    const res = await generateDemoLicense({
      customer: license.customer || 'Demo',
      seats: license.seats || 20,
    })
    licenseText.value = res.data?.licenseText || ''
    generatedHint.value = true
    ElMessage.success('已生成演示 License')
  } finally {
    generating.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.form-panel { padding: 16px 18px 20px; margin-bottom: 12px; }
.form-panel h3 { margin: 0 0 8px; font-size: 15px; }
.muted { color: var(--el-text-color-secondary); font-size: 13px; margin: 0 0 12px; line-height: 1.5; }
.actions { display: flex; gap: 8px; flex-wrap: wrap; }
.mb12 { margin-bottom: 12px; }
.mt12 { margin-top: 12px; }
.stat-num.text-sm { font-size: 18px; }
.stat-bad { border-color: #fca5a5; }
.feature-tags { display: flex; flex-wrap: wrap; gap: 8px; }
.cols-4 {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}
@media (max-width: 1100px) {
  .cols-4 { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
