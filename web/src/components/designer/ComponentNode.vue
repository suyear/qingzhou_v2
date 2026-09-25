<template>
  <div class="node-card" :class="{ selected }">
    <div class="node-title" :title="data.componentName">{{ data.componentName || '未命名节点' }}</div>
    <div class="node-row">
      <span class="method" :class="methodClass">{{ data.httpMethod || '-' }}</span>
      <span class="path" :title="data.urlPath">{{ data.urlPath || data.urlTemplate }}</span>
    </div>
    <div class="node-meta">超时 {{ timeoutText }} · 重试 {{ data.retryTimes ?? 0 }} 次</div>
    <div class="node-tags">
      <span v-if="!tags.length" class="empty">无必填入参</span>
      <span v-for="tag in tags" :key="tag" class="tag">{{ tag }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed, inject, onBeforeUnmount, onMounted, ref } from 'vue'

const getNode = inject('getNode')
const data = ref({})
const selected = ref(false)
let offSelection = null

onMounted(() => {
  const node = getNode?.()
  if (!node) {
    return
  }
  data.value = node.getData() || {}
  node.on('change:data', ({ current }) => {
    data.value = current || {}
  })
  const graph = node.model?.graph
  const syncSelected = () => {
    selected.value = Boolean(graph?.isSelected?.(node))
  }
  if (graph) {
    graph.on('selection:changed', syncSelected)
    offSelection = () => graph.off('selection:changed', syncSelected)
    syncSelected()
  }
})

onBeforeUnmount(() => {
  offSelection?.()
})

const tags = computed(() => data.value.requiredParams || [])
const timeoutText = computed(() => {
  const ms = Number(data.value.timeoutMs || 0)
  return ms >= 1000 ? `${ms / 1000}s` : `${ms}ms`
})
const methodClass = computed(() => `m-${(data.value.httpMethod || 'GET').toLowerCase()}`)
</script>

<style scoped>
.node-card {
  width: 100%;
  height: 100%;
  background: var(--qz-card);
  border: 1px solid var(--qz-border);
  border-radius: var(--qz-radius);
  padding: 10px 12px;
  box-shadow: var(--qz-shadow);
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.node-card.selected {
  border-color: var(--qz-primary);
  box-shadow: 0 0 0 2px var(--el-color-primary-light-8);
}
.node-title {
  font-size: 14px;
  font-weight: 650;
  color: var(--qz-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.node-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.method {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 4px;
  color: #fff;
  background: #64748b;
}
.m-get { background: #16a34a; }
.m-post { background: #2563eb; }
.m-put { background: #d97706; }
.m-patch { background: #7c3aed; }
.m-delete { background: #dc2626; }
.m-query { background: #0d9488; }
.m-update { background: #d97706; }
.path {
  font-size: 12px;
  color: #475569;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.node-meta {
  font-size: 12px;
  color: #64748b;
}
.node-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.tag {
  font-size: 11px;
  background: var(--qz-primary-soft);
  color: var(--el-color-primary-dark-2);
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 999px;
  padding: 0 7px;
  line-height: 18px;
}
.empty {
  font-size: 11px;
  color: #94a3b8;
}
</style>
