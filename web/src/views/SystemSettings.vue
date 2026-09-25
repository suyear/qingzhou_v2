<template>
  <div>
    <PageHeader title="系统设置" desc="站点名称、开放平台地址、执行日志保留天数与健康检查。" />
    <PageState :error="loadError" @retry="load" />

    <div class="stat-grid cols-3" v-loading="healthLoading">
      <div class="stat-card" :class="health.mysql?.ok ? 'stat-ok' : ''">
        <div class="stat-label">MySQL</div>
        <div class="stat-num">{{ health.mysql?.ok ? '正常' : '异常' }}</div>
        <div class="stat-hint">{{ health.mysql?.message || '—' }}</div>
      </div>
      <div class="stat-card" :class="health.redis?.ok ? 'stat-ok' : ''">
        <div class="stat-label">Redis</div>
        <div class="stat-num">{{ health.redis?.ok ? '正常' : '异常' }}</div>
        <div class="stat-hint">{{ health.redis?.message || '—' }}</div>
      </div>
      <button type="button" class="stat-card clickable" @click="$router.push('/license')">
        <div class="stat-label">License</div>
        <div class="stat-num">{{ license.status || '—' }}</div>
        <div class="stat-hint">{{ license.message || '点击进入 License 管理' }}</div>
      </button>
    </div>

    <div class="qz-panel form-panel">
      <el-form label-position="top" style="max-width: 560px">
        <el-form-item label="站点名称">
          <el-input v-model="form.siteName" />
        </el-form-item>
        <el-form-item label="开放平台对外地址">
          <el-input v-model="form.openapiPublicBaseUrl" placeholder="例如 https://api.example.com" />
        </el-form-item>
        <el-form-item label="执行日志保留天数">
          <el-input-number v-model="form.executionRetentionDays" :min="7" :max="3650" />
        </el-form-item>
        <el-form-item label="时区">
          <el-input v-model="form.timezone" placeholder="Asia/Shanghai" />
        </el-form-item>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import PageState from '@/components/PageState.vue'
import {
  getSystemSettings,
  licenseStatus,
  saveSystemSettings,
  systemHealthDetail,
} from '@/api/auth'
import { networkErrorMessage } from '@/api/http'

const loadError = ref('')
const saving = ref(false)
const healthLoading = ref(false)
const health = reactive({ mysql: null, redis: null })
const license = reactive({})
const form = reactive({
  siteName: '轻舟',
  openapiPublicBaseUrl: '',
  executionRetentionDays: 90,
  timezone: 'Asia/Shanghai',
})

async function load() {
  loadError.value = ''
  healthLoading.value = true
  try {
    const [settings, healthRes, lic] = await Promise.all([
      getSystemSettings(),
      systemHealthDetail(),
      licenseStatus(),
    ])
    Object.assign(form, settings.data || {})
    Object.assign(health, healthRes.data || {})
    Object.assign(license, lic.data || {})
  } catch (e) {
    loadError.value = networkErrorMessage(e)
  } finally {
    healthLoading.value = false
  }
}

async function onSave() {
  saving.value = true
  try {
    const res = await saveSystemSettings(form)
    Object.assign(form, res.data || {})
    ElMessage.success('已保存')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.form-panel { padding: 16px 18px 20px; margin-bottom: 12px; }
.stat-card.clickable {
  cursor: pointer;
  text-align: left;
  border: 1px solid transparent;
  background: var(--qz-panel, #fff);
  font: inherit;
  color: inherit;
}
.stat-card.clickable:hover {
  border-color: #93c5fd;
  box-shadow: 0 0 0 1px rgba(37, 99, 235, 0.08);
}
</style>
