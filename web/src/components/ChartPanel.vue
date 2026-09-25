<template>
  <section class="qz-chart-panel" :class="{ 'is-wide': wide }">
    <div class="qz-chart-panel__head">
      <div>
        <h3>{{ title }}</h3>
        <p v-if="desc" class="sub">{{ desc }}</p>
      </div>
      <div class="qz-chart-panel__extra">
        <slot name="extra" />
      </div>
    </div>
    <div v-if="error" class="qz-chart-panel__state">
      <el-empty :image-size="56" description="">
        <template #description>
          <p class="muted">{{ error }}</p>
        </template>
        <el-button size="small" type="primary" @click="$emit('retry')">重新加载</el-button>
      </el-empty>
    </div>
    <div v-else-if="empty && !loading" class="qz-chart-panel__state">
      <el-empty :image-size="56" :description="emptyText" />
    </div>
    <div v-else class="qz-chart-panel__body" v-loading="loading">
      <QzChart :option="option" :height="height" />
    </div>
  </section>
</template>

<script setup>
import QzChart from './QzChart.vue'

defineProps({
  title: { type: String, required: true },
  desc: { type: String, default: '' },
  option: { type: Object, default: () => ({}) },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  empty: { type: Boolean, default: false },
  emptyText: { type: String, default: '暂无执行数据' },
  height: { type: String, default: '240px' },
  wide: { type: Boolean, default: false },
})

defineEmits(['retry'])
</script>
