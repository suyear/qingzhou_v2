<template>
  <el-tag class="qz-status-tag" size="small" effect="light" :type="resolved.type">
    <span class="qz-status-dot" />
    {{ resolved.label }}
  </el-tag>
</template>

<script setup>
import { computed } from 'vue'
import { statusMeta } from '@/utils/charts'

const props = defineProps({
  kind: { type: String, default: 'exec' },
  value: { type: [String, Number], default: '' },
  type: { type: String, default: '' },
  label: { type: String, default: '' },
})

const resolved = computed(() => {
  if (props.type || props.label) {
    return { type: props.type || 'info', label: props.label || '—' }
  }
  return statusMeta(props.kind, props.value)
})
</script>
