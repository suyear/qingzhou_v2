<template>
  <div class="qz-compare">
    <DetailCodeBlock
      :title="leftTitle"
      :value="leftValue"
      :empty-text="leftEmpty"
      :copy-message="leftCopyMessage"
      :tone="tone"
      :max-height="maxHeight"
    />
    <DetailCodeBlock
      :title="rightTitle"
      :value="rightValue"
      :empty-text="rightEmpty"
      :copy-message="rightCopyMessage"
      :tone="tone"
      :max-height="maxHeight"
    />
  </div>
</template>

<script setup>
import DetailCodeBlock from './DetailCodeBlock.vue'

defineProps({
  leftTitle: { type: String, default: '请求' },
  rightTitle: { type: String, default: '响应' },
  leftValue: { type: [String, Object, Array, Number, Boolean], default: null },
  rightValue: { type: [String, Object, Array, Number, Boolean], default: null },
  leftEmpty: { type: String, default: '暂无内容' },
  rightEmpty: { type: String, default: '暂无内容' },
  leftCopyMessage: { type: String, default: '已复制' },
  rightCopyMessage: { type: String, default: '已复制' },
  tone: { type: String, default: 'soft' },
  maxHeight: { type: String, default: '280px' },
})
</script>

<style scoped>
.qz-compare {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 12px;
  min-width: 0;
}
@media (max-width: 720px) {
  .qz-compare {
    grid-template-columns: 1fr;
  }
}
</style>
