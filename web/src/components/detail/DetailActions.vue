<template>
  <div class="qz-detail-actions" :class="{ 'is-stack': stack }">
    <slot />
  </div>
</template>

<script setup>
defineProps({
  stack: { type: Boolean, default: false },
})
</script>

<style scoped>
.qz-detail-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-top: 4px;
}
.qz-detail-actions.is-stack {
  grid-template-columns: 1fr;
}
.qz-detail-actions :deep(.el-button) {
  margin-left: 0;
}
@media (max-width: 520px) {
  .qz-detail-actions {
    grid-template-columns: 1fr;
  }
}
</style>
