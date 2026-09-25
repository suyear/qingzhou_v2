<template>
  <div class="qz-code" :class="[`is-${tone}`, { 'is-empty': empty }]">
    <div class="qz-code__bar">
      <span class="qz-code__title">{{ title }}</span>
      <el-button v-if="!empty && copyable" type="primary" link @click="onCopy">
        {{ copied ? '已复制' : copyLabel }}
      </el-button>
    </div>
    <pre class="qz-code__body" :style="{ maxHeight }">{{ text }}</pre>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { copyText } from '@/utils/format'
import { codeDisplayText } from '@/utils/detailText'

const props = defineProps({
  title: { type: String, default: '' },
  value: { type: [String, Object, Array, Number, Boolean], default: null },
  emptyText: { type: String, default: '暂无内容' },
  copyable: { type: Boolean, default: true },
  copyLabel: { type: String, default: '复制' },
  copyMessage: { type: String, default: '已复制' },
  tone: { type: String, default: 'soft' },
  maxHeight: { type: String, default: '280px' },
})

const copied = ref(false)
const resolved = computed(() => codeDisplayText(props.value, props.emptyText))
const text = computed(() => resolved.value.text)
const empty = computed(() => resolved.value.empty)

async function onCopy() {
  await copyText(text.value)
  copied.value = true
  ElMessage.success(props.copyMessage)
  window.setTimeout(() => {
    copied.value = false
  }, 1600)
}
</script>

<style scoped>
.qz-code {
  min-width: 0;
  border: 1px solid var(--qz-border);
  border-radius: var(--qz-radius-sm);
  background: var(--qz-card);
  overflow: hidden;
}
.qz-code__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 36px;
  padding: 0 10px 0 12px;
  background: var(--qz-fill);
  border-bottom: 1px solid var(--qz-border);
}
.qz-code__title {
  font-size: 12px;
  font-weight: 650;
  color: var(--qz-text-secondary);
}
.qz-code__body {
  margin: 0;
  padding: 10px 12px;
  max-height: 280px;
  overflow: auto;
  background: var(--qz-code-bg);
  color: var(--qz-text);
  font-family: var(--qz-code-font);
  font-size: 12px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}
.qz-code.is-empty .qz-code__body {
  color: var(--qz-text-muted);
  min-height: 72px;
}
.qz-code.is-ink {
  background: var(--qz-code-ink);
  border-color: #1e293b;
}
.qz-code.is-ink .qz-code__bar {
  background: #111827;
  border-bottom-color: #1e293b;
}
.qz-code.is-ink .qz-code__title {
  color: var(--qz-code-ink-muted);
}
.qz-code.is-ink .qz-code__body {
  background: var(--qz-code-ink);
  color: var(--qz-code-ink-fg);
}
.qz-code.is-ink.is-empty .qz-code__body {
  color: var(--qz-code-ink-muted);
}
.qz-code.is-ink :deep(.el-button.is-link) {
  color: #93c5fd;
}
</style>
