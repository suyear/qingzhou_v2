<template>
  <DetailSection :title="title" :hint="hint">
    <el-table v-if="fields.length" :data="fields" size="small" class="qz-schema-table">
      <el-table-column prop="key" label="字段" min-width="120">
        <template #default="{ row }">
          <span class="qz-mono">{{ row.key }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="type" label="类型" width="90" />
      <el-table-column label="必填" width="72">
        <template #default="{ row }">
          <el-tag size="small" :type="row.required ? 'danger' : 'info'" effect="plain">
            {{ row.required ? '必填' : '可选' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="说明" min-width="140" show-overflow-tooltip />
    </el-table>
    <p v-else class="qz-schema-empty">{{ emptyText }}</p>
  </DetailSection>
</template>

<script setup>
import DetailSection from './DetailSection.vue'

defineProps({
  title: { type: String, default: '入参 Schema' },
  hint: { type: String, default: '' },
  fields: { type: Array, default: () => [] },
  emptyText: { type: String, default: '未声明入参 Schema' },
})
</script>

<style scoped>
.qz-schema-table {
  width: 100%;
}
.qz-schema-empty {
  margin: 0;
  color: var(--qz-text-muted);
  font-size: 13px;
}
.qz-mono {
  font-family: var(--qz-code-font);
  font-size: 12px;
}
</style>
