<template>
  <div class="curl-import" :class="{ compact }">
    <div v-if="!compact" class="curl-head">
      <span class="curl-title">{{ displayTitle }}</span>
      <el-tag v-if="recommend" size="small" type="success">开发者</el-tag>
    </div>
    <p class="curl-hint">{{ displayHint }}</p>
    <el-input
      v-model="text"
      type="textarea"
      :rows="compact ? 3 : 5"
      :placeholder="placeholder"
      @paste="onPaste"
    />
    <div class="curl-actions">
      <el-button type="primary" :loading="parsing" @click="onParse(false)">{{ lastSummary ? '重新识别' : '识别并填入' }}</el-button>
      <el-button text type="primary" @click="fillExample">填入示例</el-button>
      <el-button v-if="text.trim()" text @click="clearAll">清空</el-button>
    </div>
    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="warning"
      :closable="false"
      show-icon
      class="curl-error"
    />
    <div v-if="lastSummary" class="curl-result">
      <el-icon class="ok-icon"><CircleCheck /></el-icon>
      <span>{{ lastSummary }}</span>
    </div>
    <p v-if="!compact" class="curl-tip">也支持直接粘贴接口地址（如 https://api.example.com/users），无需 curl 前缀。</p>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheck } from '@element-plus/icons-vue'
import { getExampleCurl, parseCurl } from '@/utils/parseCurl'

const props = defineProps({
  compact: { type: Boolean, default: false },
  recommend: { type: Boolean, default: false },
  title: { type: String, default: '' },
  hint: { type: String, default: '' },
  placeholder: {
    type: String,
    default: "curl --location 'https://api.example.com/users' --header 'Authorization: Bearer your-token'",
  },
})

const displayTitle = computed(() => {
  if (props.title) return props.title
  return props.compact ? '粘贴 curl 或完整地址' : '粘贴 curl 或接口地址'
})

const displayHint = computed(() => {
  if (props.hint) return props.hint
  return props.compact
    ? 'Postman「Code → cURL」复制后粘贴，识别结果会自动填入下方表单。'
    : '从 Postman「Code → cURL」或接口文档复制整段命令，粘贴后会自动识别。支持 --header、--url 等常见格式。'
})

const emit = defineEmits(['import'])

const text = ref('')
const parsing = ref(false)
const lastSummary = ref('')
const errorMessage = ref('')

function clearAll() {
  text.value = ''
  lastSummary.value = ''
  errorMessage.value = ''
}

function fillExample() {
  text.value = getExampleCurl()
  errorMessage.value = ''
  onParse()
}

function onPaste() {
  window.setTimeout(() => {
    if (text.value.trim().length > 12) {
      onParse(true)
    }
  }, 0)
}

let parseTimer = null
watch(text, (value) => {
  if (parseTimer) {
    window.clearTimeout(parseTimer)
  }
  const raw = value.trim()
  if (raw.length < 12) {
    return
  }
  parseTimer = window.setTimeout(() => onParse(true), 400)
})

function onParse(silent = false) {
  const raw = text.value.trim()
  if (!raw) {
    errorMessage.value = '请先粘贴 curl 或接口地址'
    return
  }

  parsing.value = true
  errorMessage.value = ''

  try {
    const parsed = parseCurl(raw)
    if (!parsed.ok) {
      errorMessage.value = parsed.message
      lastSummary.value = ''
      if (!silent) {
        ElMessage.warning(parsed.message)
      }
      return
    }
    lastSummary.value = `已识别：${parsed.summary}`
    emit('import', parsed)
    if (!silent) {
      ElMessage.success('已自动填入下方配置')
    }
  } finally {
    parsing.value = false
  }
}
</script>

<style scoped>
.curl-import {
  padding: 14px;
  margin-bottom: 14px;
  border: 1px dashed var(--el-color-primary-light-5);
  border-radius: var(--qz-radius);
  background: var(--qz-primary-soft);
}
.curl-import.compact {
  padding: 0;
  margin-bottom: 0;
  border: none;
  background: transparent;
}
.curl-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.curl-title {
  font-weight: 600;
  font-size: 14px;
}
.curl-hint {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--qz-text-muted);
  line-height: 1.5;
}
.curl-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.curl-error {
  margin-top: 10px;
}
.curl-result {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 10px;
  font-size: 13px;
  color: var(--el-color-success);
  line-height: 1.5;
}
.ok-icon {
  margin-top: 2px;
  flex-shrink: 0;
}
.curl-tip {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--qz-text-muted);
}
</style>
