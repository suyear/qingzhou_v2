<template>
  <div class="qz-detail-empty">
    <el-empty v-if="!loading" :description="text" :image-size="imageSize">
      <slot />
    </el-empty>
    <p v-else class="qz-detail-empty__loading">{{ loadingText }}</p>
  </div>
</template>

<script setup>
defineProps({
  text: { type: String, default: '暂无内容' },
  loading: { type: Boolean, default: false },
  loadingText: { type: String, default: '正在加载详情…' },
  imageSize: { type: Number, default: 72 },
})
</script>

<style scoped>
.qz-detail-empty {
  padding: 28px 12px;
  text-align: center;
}
.qz-detail-empty__loading {
  margin: 0;
  color: var(--qz-text-muted);
  font-size: 13px;
}
</style>
