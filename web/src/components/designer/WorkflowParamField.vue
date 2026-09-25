<template>
  <div class="param-field" :class="{ done: isDone }">
    <div class="param-head">
      <div class="param-title">
        <div class="param-label">{{ fieldLabel(field) }}</div>
        <div v-if="field.description && field.key && field.description !== field.key" class="param-key" :title="field.key">
          {{ field.key }}
        </div>
      </div>
      <el-tag v-if="field.required" size="small" type="danger" effect="plain">必填</el-tag>
      <el-tag v-else size="small" type="info" effect="plain">可选</el-tag>
      <el-icon v-if="isDone" class="done-icon"><Check /></el-icon>
    </div>

    <div class="mode-cards">
      <button
        type="button"
        class="mode-card"
        :class="{ active: mode === 'fixed' }"
        @click="onModeChange('fixed')"
      >
        <span class="mode-name">固定值</span>
        <span class="mode-desc">写死不变</span>
      </button>
      <button
        v-if="upstreamNodes.length"
        type="button"
        class="mode-card"
        :class="{ active: mode === 'upstream' }"
        @click="onModeChange('upstream')"
      >
        <span class="mode-name">接上一步</span>
        <span class="mode-desc">用上游结果</span>
      </button>
      <button
        type="button"
        class="mode-card"
        :class="{ active: mode === 'runtime' }"
        @click="onModeChange('runtime')"
      >
        <span class="mode-name">外部传入</span>
        <span class="mode-desc">调用时填写</span>
      </button>
    </div>

    <div v-if="mode === 'fixed'" class="param-value">
      <el-select
        v-if="field.enums?.length"
        :model-value="binding.value ?? ''"
        filterable
        allow-create
        placeholder="选择或输入"
        style="width: 100%"
        @change="(val) => emitChange({ mode: 'fixed', value: val })"
      >
        <el-option v-for="opt in field.enums" :key="opt" :value="String(opt)" :label="String(opt)" />
      </el-select>
      <el-input
        v-else-if="field.type === 'object' || field.type === 'array'"
        :model-value="binding.value ?? ''"
        type="textarea"
        :rows="2"
        :placeholder="field.type === 'array' ? '多个值用逗号分隔' : '{ }'"
        @input="(val) => emitChange({ mode: 'fixed', value: val })"
      />
      <el-input
        v-else
        :model-value="binding.value ?? ''"
        :placeholder="field.description || '请输入固定值'"
        @input="(val) => emitChange({ mode: 'fixed', value: val })"
      />
    </div>

    <div v-else-if="mode === 'upstream'" class="param-value">
      <div class="upstream-box">
        <span class="upstream-from">从「{{ upstreamLabel }}」取字段</span>
        <el-select
          :model-value="binding.fromField || ''"
          filterable
          allow-create
          placeholder="选择返回字段"
          style="width: 100%"
          @change="(val) => emitChange({
            mode: 'upstream',
            fromNode: binding.fromNode || upstreamNodes[0]?.id,
            fromField: val,
          })"
        >
          <el-option v-for="name in fieldOptions" :key="name" :value="name" :label="name" />
        </el-select>
      </div>
    </div>

    <div v-else class="param-value runtime-box">
      试运行、定时任务、开放 API 调用时由外部填写此参数
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Check } from '@element-plus/icons-vue'
import { fieldLabel, isFieldConfigured } from '@/utils/workflowBinding'

const props = defineProps({
  field: { type: Object, required: true },
  binding: { type: Object, default: () => ({}) },
  upstreamNodes: { type: Array, default: () => [] },
  upstreamFieldMap: { type: Object, default: () => ({}) },
})

const emit = defineEmits(['change'])

const mode = computed(() => props.binding.mode || 'fixed')
const isDone = computed(() => {
  if (!props.field.required) return false
  return isFieldConfigured(props.binding, props.field)
})
const upstreamLabel = computed(() => props.upstreamNodes[0]?.name || '上一步')

const fieldOptions = computed(() => {
  const nodeId = props.binding.fromNode || props.upstreamNodes[0]?.id
  return props.upstreamFieldMap[nodeId] || []
})

function emitChange(patch) {
  emit('change', { key: props.field.key, ...patch })
}

function onModeChange(nextMode) {
  if (nextMode === mode.value) return
  if (nextMode === 'fixed') {
    emitChange({ mode: 'fixed', value: props.binding.value ?? '', fromNode: '', fromField: '' })
    return
  }
  if (nextMode === 'upstream') {
    const fromNode = props.upstreamNodes[0]?.id || ''
    const fromField = fieldOptions.value[0] || props.field.key
    emitChange({ mode: 'upstream', fromNode, fromField, value: '' })
    return
  }
  emitChange({ mode: 'runtime', value: '', fromNode: '', fromField: '' })
}
</script>

<style scoped>
.param-field {
  margin-bottom: 14px;
  padding: 14px;
  border-radius: var(--qz-radius);
  background: var(--qz-card);
  border: 1px solid var(--qz-border);
}
.param-field.done { border-color: var(--el-color-success-light-7); }
.param-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.param-title {
  flex: 1;
  min-width: 0;
}
.param-label {
  font-size: 14px;
  font-weight: 600;
}
.param-key {
  margin-top: 2px;
  font-size: 11px;
  color: var(--qz-text-muted);
  font-family: var(--qz-code-font);
}
.done-icon { color: var(--el-color-success); font-size: 16px; }
.mode-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(96px, 1fr));
  gap: 8px;
  margin-bottom: 12px;
}
.mode-card {
  min-height: 52px;
  padding: 8px 6px;
  border: 2px solid var(--qz-border);
  border-radius: var(--qz-radius);
  background: var(--qz-fill);
  cursor: pointer;
  text-align: center;
  touch-action: manipulation;
  transition: border-color 0.12s, background 0.12s;
}
.mode-card:hover {
  border-color: var(--el-color-primary-light-5);
  background: var(--qz-primary-soft);
}
.mode-card.active {
  border-color: var(--el-color-primary);
  background: var(--qz-primary-soft);
  box-shadow: 0 0 0 2px var(--qz-primary-soft);
}
.mode-name {
  display: block;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.3;
}
.mode-desc {
  display: block;
  margin-top: 2px;
  font-size: 10px;
  color: var(--qz-text-muted);
}
.param-value { margin-top: 4px; }
.upstream-box {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
  border-radius: var(--qz-radius);
  background: var(--qz-fill);
  border: 1px dashed var(--el-color-primary-light-7);
}
.upstream-from {
  font-size: 12px;
  color: var(--el-color-primary);
  font-weight: 600;
}
.runtime-box {
  padding: 12px;
  border-radius: var(--qz-radius);
  background: var(--qz-fill);
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
}
@media (max-width: 520px) {
  .mode-cards { grid-template-columns: 1fr; }
}
</style>
