<template>
  <div class="param-field" :class="{ done: isDone }">
    <div class="param-head">
      <div class="param-title">
        <div class="param-label">{{ fieldLabel(field) }}</div>
        <div v-if="field.key" class="param-key" :title="field.key">
          {{ field.key }}
          <span v-if="field.custom" class="custom-mark">自定义</span>
        </div>
      </div>
      <el-tag v-if="field.required" size="small" type="danger" effect="plain">必填</el-tag>
      <el-tag v-else size="small" type="info" effect="plain">可选</el-tag>
      <el-icon v-if="isDone" class="done-icon"><Check /></el-icon>
      <button
        v-if="field.custom"
        type="button"
        class="remove-btn"
        title="移除此参数"
        @click="emit('remove', field.key)"
      >×</button>
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
        type="button"
        class="mode-card"
        :class="{ active: mode === 'data' }"
        @click="onModeChange('data')"
      >
        <span class="mode-name">取数据</span>
        <span class="mode-desc">入参或前序步骤</span>
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

    <div v-else-if="mode === 'data'" class="param-value">
      <div class="data-box">
        <el-select
          :model-value="sourceValue"
          filterable
          allow-create
          default-first-option
          placeholder="选择数据来源，或输入入参键"
          style="width: 100%"
          @change="onSourceChange"
        >
          <el-option-group label="工作流入参">
            <el-option
              :value="`input:${field.key}`"
              :label="`本字段同名入参（${field.key}）`"
            />
            <el-option
              v-for="item in inputFields"
              :key="`input:${item.key}`"
              :value="`input:${item.key}`"
              :label="inputOptionLabel(item)"
            />
          </el-option-group>
          <el-option-group
            v-for="(step, index) in upstreamSources"
            :key="step.id"
            :label="`第 ${index + 1} 步 · ${step.name}`"
          >
            <el-option
              v-for="name in step.requestFields"
              :key="`${step.id}:request:${name}`"
              :value="`${step.id}:request:${name}`"
              :label="`请求 · ${name}`"
            />
            <el-option
              v-for="name in step.responseFields"
              :key="`${step.id}:output:${name}`"
              :value="`${step.id}:output:${name}`"
              :label="`响应 · ${name}`"
            />
          </el-option-group>
        </el-select>
        <p class="data-hint">{{ dataHint }}</p>
      </div>
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
  upstreamSources: { type: Array, default: () => [] },
  inputFields: { type: Array, default: () => [] },
})

const emit = defineEmits(['change', 'remove'])

const mode = computed(() => {
  const m = props.binding.mode || 'fixed'
  if (m === 'runtime' || m === 'upstream') return 'data'
  return 'fixed'
})

const isDone = computed(() => {
  if (!props.field.required) return false
  return isFieldConfigured(props.binding, props.field)
})

const sourceValue = computed(() => {
  if (props.binding.mode === 'runtime') {
    const key = props.binding.inputKey || props.field.key
    return key ? `input:${key}` : ''
  }
  if (props.binding.mode === 'upstream' && props.binding.fromNode && props.binding.fromField) {
    const side = props.binding.fromSource === 'request' ? 'request' : 'output'
    return `${props.binding.fromNode}:${side}:${props.binding.fromField}`
  }
  return ''
})

const dataHint = computed(() => {
  if (props.binding.mode === 'runtime') {
    const key = props.binding.inputKey || props.field.key
    if (key && key !== props.field.key) {
      return `调用时填入参 ${key}，映射到本步 ${props.field.key}`
    }
    return '试运行 / 调度 / 开放 API 调用时填写'
  }
  if (props.binding.mode === 'upstream') {
    const step = props.upstreamSources.find((item) => item.id === props.binding.fromNode)
    const side = props.binding.fromSource === 'request' ? '请求' : '响应'
    return `取自「${step?.name || '前序步骤'}」的${side}字段`
  }
  return '从工作流入参或前序步骤的请求/响应取值'
})

function inputOptionLabel(item) {
  if (item.description && item.description !== item.key) {
    return `${item.description}（${item.key}）`
  }
  return item.key
}

function emitChange(patch) {
  emit('change', { key: props.field.key, ...patch })
}

function onModeChange(nextMode) {
  if (nextMode === 'fixed') {
    emitChange({
      mode: 'fixed',
      value: props.binding.value ?? '',
      fromNode: '',
      fromField: '',
      fromSource: '',
      inputKey: '',
    })
    return
  }
  if (props.inputFields.length) {
    const key = props.inputFields.find((item) => item.key === props.field.key)?.key
      || props.inputFields[0].key
    emitChange({
      mode: 'runtime',
      inputKey: key,
      fromNode: '',
      fromField: '',
      fromSource: '',
      value: '',
    })
    return
  }
  const step = props.upstreamSources[0]
  if (step) {
    const fromField = step.responseFields?.[0] || step.requestFields?.[0] || props.field.key
    const fromSource = step.responseFields?.includes(fromField) ? 'output' : 'request'
    emitChange({
      mode: 'upstream',
      fromNode: step.id,
      fromField,
      fromSource,
      inputKey: '',
      value: '',
    })
    return
  }
  emitChange({
    mode: 'runtime',
    inputKey: props.field.key,
    fromNode: '',
    fromField: '',
    fromSource: '',
    value: '',
  })
}

function onSourceChange(raw) {
  let value = String(raw || '').trim()
  if (!value) return
  // allow-create: 用户直接输入入参键
  if (!value.includes(':')) {
    value = `input:${value}`
  }
  if (value.startsWith('input:')) {
    const inputKey = value.slice('input:'.length)
    emitChange({
      mode: 'runtime',
      inputKey,
      fromNode: '',
      fromField: '',
      fromSource: '',
      value: '',
    })
    return
  }
  const parts = value.split(':')
  if (parts.length >= 3) {
    const fromNode = parts[0]
    const fromSource = parts[1] === 'request' ? 'request' : 'output'
    const fromField = parts.slice(2).join(':')
    emitChange({
      mode: 'upstream',
      fromNode,
      fromField,
      fromSource,
      inputKey: '',
      value: '',
    })
  }
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
.param-title { flex: 1; min-width: 0; }
.param-label { font-size: 14px; font-weight: 600; }
.param-key {
  margin-top: 2px;
  font-size: 11px;
  color: var(--qz-text-muted);
  font-family: var(--qz-code-font);
}
.custom-mark {
  margin-left: 6px;
  font-family: inherit;
  color: var(--el-color-primary);
}
.done-icon { color: var(--el-color-success); font-size: 16px; }
.remove-btn {
  width: 28px;
  height: 28px;
  border: 1px solid var(--qz-border);
  border-radius: 6px;
  background: var(--qz-card);
  color: var(--qz-text-muted);
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
}
.remove-btn:hover {
  border-color: var(--el-color-danger);
  color: var(--el-color-danger);
}
.mode-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(110px, 1fr));
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
.data-box {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
  border-radius: var(--qz-radius);
  background: var(--qz-fill);
  border: 1px dashed var(--el-color-primary-light-7);
}
.data-hint {
  margin: 0;
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
}
</style>
