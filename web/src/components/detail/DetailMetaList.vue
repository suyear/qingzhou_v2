<template>
  <dl class="qz-meta" :class="{ 'is-grid': columns === 2 }">
    <div v-for="item in visible" :key="item.label" class="qz-meta__row">
      <dt>{{ item.label }}</dt>
      <dd>
        <template v-if="item.slot">
          <slot :name="item.slot" :item="item">{{ display(item) }}</slot>
        </template>
        <span v-else :class="{ 'qz-mono': item.mono }">{{ display(item) }}</span>
        <el-button
          v-if="item.copy && !isEmpty(item.value)"
          type="primary"
          link
          @click="onCopy(item)"
        >
          复制
        </el-button>
      </dd>
    </div>
  </dl>
</template>

<script setup>
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { copyText } from '@/utils/format'

const props = defineProps({
  items: { type: Array, default: () => [] },
  columns: { type: Number, default: 1 },
})

const visible = computed(() =>
  (props.items || []).filter((item) => item && item.hidden !== true),
)

function isEmpty(value) {
  return value == null || value === ''
}

function display(item) {
  if (isEmpty(item.value)) return item.emptyText || '—'
  return item.value
}

async function onCopy(item) {
  await copyText(item.value)
  ElMessage.success(item.copyMessage || '已复制')
}
</script>

<style scoped>
.qz-meta {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.qz-meta.is-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 16px;
}
.qz-meta__row {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 8px;
  align-items: start;
  min-width: 0;
}
.qz-meta dt {
  margin: 0;
  padding-top: 1px;
  color: var(--qz-text-muted);
  font-size: 12px;
  font-weight: 500;
  line-height: 1.5;
}
.qz-meta dd {
  margin: 0;
  display: flex;
  align-items: flex-start;
  gap: 6px;
  min-width: 0;
  color: var(--qz-text);
  font-size: 13px;
  line-height: 1.5;
  word-break: break-all;
}
.qz-mono {
  font-family: var(--qz-code-font);
  font-size: 12px;
}
@media (max-width: 720px) {
  .qz-meta.is-grid {
    grid-template-columns: 1fr;
  }
}
</style>
