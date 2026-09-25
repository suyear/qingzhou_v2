<template>
  <el-alert
    v-if="error"
    class="page-state-alert"
    type="error"
    show-icon
    :closable="false"
  >
    <template #title>{{ errorTitle }}</template>
    <div class="page-state-body">
      <p>{{ error }}</p>
      <el-button v-if="retryable" size="small" type="primary" @click="$emit('retry')">重新加载</el-button>
    </div>
  </el-alert>
</template>

<script setup>
defineProps({
  error: { type: String, default: '' },
  errorTitle: { type: String, default: '无法加载数据' },
  retryable: { type: Boolean, default: true },
})

defineEmits(['retry'])
</script>

<style scoped>
.page-state-alert {
  margin-bottom: 16px;
  border-radius: 10px;
}
.page-state-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.page-state-body p {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.5;
}
</style>
