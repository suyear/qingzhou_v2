<template>
  <div class="qz-copy-field">
    <div v-if="label" class="qz-copy-field__label">{{ label }}</div>
    <div class="qz-copy-field__row">
      <el-input :model-value="display" readonly class="qz-copy-field__input" />
      <el-button :type="primary ? 'primary' : 'default'" @click="onCopy">复制</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { copyText } from '@/utils/format'

const props = defineProps({
  label: { type: String, default: '' },
  value: { type: [String, Number], default: '' },
  copyMessage: { type: String, default: '已复制' },
  primary: { type: Boolean, default: false },
})

const display = computed(() => (props.value == null ? '' : String(props.value)))

async function onCopy() {
  await copyText(display.value)
  ElMessage.success(props.copyMessage)
}
</script>

<style scoped>
.qz-copy-field {
  min-width: 0;
}
.qz-copy-field__label {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 650;
  color: var(--qz-text-muted);
}
.qz-copy-field__row {
  display: flex;
  gap: 8px;
  min-width: 0;
}
.qz-copy-field__input {
  flex: 1;
  min-width: 0;
}
.qz-copy-field__input :deep(.el-input__inner) {
  font-family: var(--qz-code-font);
  font-size: 12px;
}
</style>
