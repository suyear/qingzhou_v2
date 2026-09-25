<template>
  <div class="auth-panel" :class="{ embedded }">
    <div class="auth-panel-head">
      <span class="auth-panel-title">鉴权（Authorization）</span>
      <span class="auth-panel-hint">推荐引用凭证中心；内联方式会把密钥明文写进组件配置</span>
    </div>

    <el-form label-position="top" size="default">
      <el-form-item label="鉴权方式">
        <AuthTypePicker
          v-model="model.authType"
          :options="AUTH_TYPES"
          :group-defs="AUTH_TYPE_GROUPS"
          :columns="2"
        />
      </el-form-item>

      <template v-if="model.authType === 'credential'">
        <el-form-item label="选择凭证" required>
          <el-select
            v-model="model.credentialId"
            filterable
            clearable
            placeholder="HTTP 鉴权 / 企业微信"
            style="width: 100%"
          >
            <el-option
              v-for="item in credentialOptions"
              :key="item.id"
              :label="credentialLabel(item)"
              :value="item.id"
            />
          </el-select>
          <p v-if="!credentialOptions.length" class="field-tip">
            还没有可用凭证，请先到「凭证管理」创建 HTTP 鉴权或企业微信凭证。
          </p>
        </el-form-item>
      </template>

      <template v-else-if="model.authType === 'bearer'">
        <el-form-item label="Token" required>
          <el-input
            v-model="model.bearerToken"
            type="password"
            show-password
            placeholder="粘贴 Bearer Token（将明文保存在组件配置）"
          />
        </el-form-item>
      </template>

      <template v-else-if="model.authType === 'apiKey'">
        <div class="auth-inline">
          <el-form-item label="参数名" required class="flex-1">
            <el-input v-model="model.apiKeyName" placeholder="如 X-API-Key、api_key" />
          </el-form-item>
          <el-form-item label="添加到" class="placement-item">
            <el-radio-group v-model="model.apiKeyIn">
              <el-radio-button value="header">Header</el-radio-button>
              <el-radio-button value="query">Query</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </div>
        <el-form-item label="Key 值" required>
          <el-input
            v-model="model.apiKeyValue"
            type="password"
            show-password
            placeholder="API Key 密钥"
          />
        </el-form-item>
      </template>

      <template v-else-if="model.authType === 'basic'">
        <div class="auth-inline">
          <el-form-item label="用户名" required class="flex-1">
            <el-input v-model="model.basicUsername" placeholder="Username" />
          </el-form-item>
          <el-form-item label="密码" class="flex-1">
            <el-input
              v-model="model.basicPassword"
              type="password"
              show-password
              placeholder="Password"
            />
          </el-form-item>
        </div>
      </template>

      <el-alert
        v-else-if="model.authType === 'wecom'"
        type="info"
        :closable="false"
        show-icon
        class="wecom-alert"
      >
        <template #title>
          运行时会从工作流绑定的「企业微信」凭证获取 AccessToken。请确保地址里包含
          <code class="inline-code">access_token=${access_token}</code>。
        </template>
        <el-button size="small" type="primary" link @click="appendWecomTokenPlaceholder">
          一键插入到地址
        </el-button>
      </el-alert>

      <p v-if="['bearer', 'apiKey', 'basic'].includes(model.authType)" class="inline-warn">
        密钥会明文写入组件配置。OAuth2 / mTLS / 签名等请先到「凭证管理」创建，再选「引用凭证」。
      </p>
    </el-form>

    <el-collapse class="headers-collapse">
      <el-collapse-item title="自定义请求头（可选）" name="headers">
        <div class="headers-section-inner">
          <p v-if="!model.headers.length" class="headers-empty">不需要可留空。例如 Content-Type、Accept-Language。</p>
          <div v-for="(row, index) in model.headers" :key="index" class="header-row">
            <el-checkbox v-model="row.enabled" />
            <el-input v-model="row.key" placeholder="Header 名" />
            <el-input v-model="row.value" placeholder="值" />
            <el-button type="danger" link @click="removeHeader(index)">删除</el-button>
          </div>
          <el-button size="small" @click="addHeader">+ 添加请求头</el-button>
        </div>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { AUTH_TYPES, AUTH_TYPE_GROUPS, createEmptyAuthState } from '@/utils/componentAuth'
import AuthTypePicker from '@/components/AuthTypePicker.vue'
import { pageCredentials } from '@/api/credential'
import { httpAuthTypeLabel, isHttpAuthCredential, normalizeCredentialType } from '@/utils/credentialTypes'

defineProps({
  embedded: { type: Boolean, default: false },
})

const emit = defineEmits(['append-wecom-token'])
const model = defineModel({ type: Object, default: () => createEmptyAuthState() })
const credentialOptions = ref([])

function ensureModel() {
  if (!model.value || typeof model.value !== 'object') {
    model.value = createEmptyAuthState()
  }
  if (!Array.isArray(model.value.headers)) {
    model.value.headers = []
  }
  if (!model.value.authType) {
    model.value.authType = 'none'
  }
}

ensureModel()

function credentialLabel(item) {
  const type = normalizeCredentialType(item.credentialType)
  if (type === 'WECOM') return `${item.credentialName}（企业微信）`
  if (isHttpAuthCredential(item.credentialType)) {
    return `${item.credentialName}（${httpAuthTypeLabel(item.authType || 'bearer')}）`
  }
  return item.credentialName
}

async function loadCredentials() {
  const res = await pageCredentials({ current: 1, size: 200 })
  credentialOptions.value = (res.data?.records || []).filter((item) => {
    if (item.status !== 1) return false
    const type = normalizeCredentialType(item.credentialType)
    return type === 'WECOM' || type === 'HTTP_AUTH'
  })
}

function addHeader() {
  ensureModel()
  model.value.headers.push({ key: '', value: '', enabled: true })
}

function removeHeader(index) {
  model.value.headers.splice(index, 1)
}

function appendWecomTokenPlaceholder() {
  emit('append-wecom-token')
}

onMounted(loadCredentials)
</script>

<style scoped>
.auth-panel {
  padding: 14px;
  border: 1px solid var(--qz-border);
  border-radius: var(--qz-radius);
  background: var(--qz-card);
  margin-bottom: 12px;
}
.auth-panel.embedded {
  padding: 0;
  border: none;
  background: transparent;
  margin-bottom: 0;
}
.auth-panel.embedded .auth-panel-head {
  display: none;
}
.auth-panel-head {
  margin-bottom: 12px;
}
.auth-panel-title {
  display: block;
  font-weight: 600;
  font-size: 14px;
}
.auth-panel-hint {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--qz-text-muted);
}
.auth-inline {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
  align-items: start;
}
.flex-1 { min-width: 0; }
.placement-item :deep(.el-form-item__content) {
  justify-content: flex-end;
}
.wecom-alert { margin-bottom: 0; }
.inline-warn {
  margin: 0 0 8px;
  font-size: 12px;
  line-height: 1.45;
  color: var(--qz-warning);
}
.inline-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}
.field-tip {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--qz-text-muted);
}
.headers-collapse {
  margin-top: 8px;
}
.headers-section-inner {
  padding-top: 4px;
}
.headers-empty {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--qz-text-muted);
}
.header-row {
  display: grid;
  grid-template-columns: auto 1fr 1fr auto;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}
@media (max-width: 640px) {
  .auth-inline,
  .header-row {
    grid-template-columns: 1fr;
  }
}
</style>
