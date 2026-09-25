<template>
  <div class="qz-result" :class="ok ? 'is-ok' : 'is-fail'">
    <div class="qz-result__head">
      <div>
        <StatusTag :type="ok ? 'success' : 'danger'" :label="title" />
        <p v-if="message" class="qz-result__msg">{{ message }}</p>
      </div>
      <div v-if="$slots.extra" class="qz-result__extra">
        <slot name="extra" />
      </div>
    </div>
    <p v-if="meta" class="qz-result__meta">{{ meta }}</p>
    <div v-if="$slots.default" class="qz-result__body">
      <slot />
    </div>
  </div>
</template>

<script setup>
import StatusTag from '@/components/StatusTag.vue'

defineProps({
  ok: { type: Boolean, default: false },
  title: { type: String, default: '' },
  message: { type: String, default: '' },
  meta: { type: String, default: '' },
})
</script>

<style scoped>
.qz-result {
  padding: 12px 14px;
  border-radius: var(--qz-radius-sm);
  border: 1px solid var(--qz-border);
}
.qz-result.is-ok {
  background: var(--qz-success-soft);
  border-color: #a7f3d0;
}
.qz-result.is-fail {
  background: var(--qz-danger-soft);
  border-color: #fecaca;
}
.qz-result__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}
.qz-result__msg {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--qz-text);
  word-break: break-all;
}
.qz-result__meta {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--qz-text-muted);
  word-break: break-all;
  line-height: 1.5;
}
.qz-result__body {
  margin-top: 10px;
}
</style>
