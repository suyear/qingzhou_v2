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

    <div class="mode-switch">
      <button
        type="button"
        class="mode-chip"
        :class="{ active: mode === 'fixed' }"
        @click="onModeChange('fixed')"
      >固定值</button>
      <button
        type="button"
        class="mode-chip"
        :class="{ active: mode === 'data' }"
        @click="onModeChange('data')"
      >取数据</button>
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
          placeholder="选择数据来源，或输入工作流入参键"
          style="width: 100%"
          @change="onSourceChange"
        >
          <el-option-group label="工作流入参">
            <el-option
              :value="`input:${field.key}`"
              :label="`同名入参（${field.key}）`"
            />
            <el-option
              v-for="item in inputFields"
              :key="`input:${item.key}`"
              :value="`input:${item.key}`"
              :label="inputOptionLabel(item)"
            />
          </el-option-group>
          <template v-for="(step, index) in upstreamSources" :key="step.id">
            <el-option-group :label="`第 ${index + 1} 步 · 入参 · ${step.name}`">
              <el-option
                :value="`${step.id}:request:*`"
                label="完整请求"
              />
              <el-option
                v-for="name in step.requestFields"
                :key="`${step.id}:request:${name}`"
                :value="`${step.id}:request:${name}`"
                :label="name"
              />
            </el-option-group>
            <el-option-group :label="`第 ${index + 1} 步 · 出参 · ${step.name}`">
              <el-option
                v-for="port in (step.outputPorts || [{ key: 'result', fromPath: '' }])"
                :key="`${step.id}:port:${port.key}`"
                :value="portSourceValue(step.id, port)"
                :label="portOptionLabel(port)"
              />
            </el-option-group>
            <el-option-group
              v-if="step.responseFields?.length"
              :label="`第 ${index + 1} 步 · 响应字段 · ${step.name}`"
            >
              <el-option
                :value="`${step.id}:output:*`"
                label="完整结果"
              />
              <el-option
                v-for="name in step.responseFields"
                :key="`${step.id}:output:${name}`"
                :value="`${step.id}:output:${name}`"
                :label="responseFieldLabel(name)"
              />
            </el-option-group>
          </template>
        </el-select>
        <p class="data-hint">{{ dataHint }}</p>
        <p v-if="mode === 'data' && !upstreamSources.some((s) => s.responseFields?.length) && upstreamSources.length" class="data-hint muted">
          前序暂无响应字段：可选手输 $.path，或先试跑，字段会自动出现（保存后仍保留）
        </p>
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
    const field = props.binding.fromField === '__whole__' ? '*' : props.binding.fromField
    return `${props.binding.fromNode}:${side}:${field}`
  }
  return ''
})

const dataHint = computed(() => {
  if (props.binding.mode === 'runtime') {
    const key = props.binding.inputKey || props.field.key
    if (key && key !== props.field.key) {
      return `来自工作流入参 ${key} → 本步 ${props.field.key}`
    }
    return `来自工作流入参 ${key || props.field.key}（调用时填写）`
  }
  if (props.binding.mode === 'upstream') {
    const index = props.upstreamSources.findIndex((item) => item.id === props.binding.fromNode)
    const step = index >= 0 ? props.upstreamSources[index] : null
    const stepLabel = index >= 0
      ? `第 ${index + 1} 步 · ${step?.name || ''}`
      : (step?.name || '前序步骤')
    const side = props.binding.fromSource === 'request' ? '请求' : '响应'
    const field = props.binding.fromField === '__whole__' || props.binding.fromField === '*'
      ? '完整'
      : (props.binding.fromField || '')
    return `来自${stepLabel} · ${side} · ${field}`
  }
  return '可选：工作流入参、前序任一步的请求或响应'
})

function inputOptionLabel(item) {
  if (item.description && item.description !== item.key) {
    return `${item.description}（${item.key}）`
  }
  return item.key
}

function responseFieldLabel(name) {
  const raw = String(name || '')
  if (raw.startsWith('rows[0].')) return `首行 · ${raw.slice('rows[0].'.length)}`
  if (raw === 'rows') return 'rows（行集）'
  return raw
}

function portSourceValue(stepId, port) {
  const path = String(port?.fromPath || '').replace(/^\$\.?/, '').trim()
  if (!path || path === '*') return `${stepId}:output:*`
  return `${stepId}:output:${path}`
}

function portOptionLabel(port) {
  const path = String(port?.fromPath || '').replace(/^\$\.?/, '').trim()
  if (!path) return `${port.key}（完整响应）`
  return `${port.key} ← ${path}`
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
  // 取数据：优先同名上游响应，再上游请求，再同名入参
  const fieldKey = props.field.key
  for (const step of props.upstreamSources || []) {
    if (step.responseFields?.includes(fieldKey)) {
      emitChange({
        mode: 'upstream',
        fromNode: step.id,
        fromField: fieldKey,
        fromSource: 'output',
        inputKey: '',
        value: '',
      })
      return
    }
  }
  for (const step of props.upstreamSources || []) {
    if (step.requestFields?.includes(fieldKey)) {
      emitChange({
        mode: 'upstream',
        fromNode: step.id,
        fromField: fieldKey,
        fromSource: 'request',
        inputKey: '',
        value: '',
      })
      return
    }
  }
  if (props.inputFields.length) {
    const key = props.inputFields.find((item) => item.key === fieldKey)?.key
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
    const fromField = step.responseFields?.[0] || step.requestFields?.[0] || fieldKey
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
    inputKey: fieldKey,
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
    let fromField = parts.slice(2).join(':')
    if (fromField === '*' || fromField === '$') {
      // 整步响应 → 引擎 fromPath 为空
      fromField = '__whole__'
    }
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
  margin-bottom: 10px;
  padding: 10px 12px;
  border-radius: var(--qz-radius);
  background: var(--qz-card);
  border: 1px solid var(--qz-border);
}
.param-field.done { border-color: var(--el-color-success-light-7); }
.param-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.param-title { flex: 1; min-width: 0; }
.param-label { font-size: 13px; font-weight: 600; }
.param-key {
  margin-top: 1px;
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
.mode-switch {
  display: inline-flex;
  gap: 0;
  margin-bottom: 8px;
  padding: 2px;
  border-radius: 8px;
  background: var(--qz-fill);
  border: 1px solid var(--qz-border);
}
.mode-chip {
  border: 0;
  background: transparent;
  padding: 4px 12px;
  font-size: 12px;
  line-height: 1.4;
  border-radius: 6px;
  color: var(--qz-text-muted);
  cursor: pointer;
}
.mode-chip.active {
  background: var(--qz-card);
  color: var(--el-color-primary);
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
}
.param-value { margin-top: 2px; }
.data-box {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px 10px;
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
.data-hint.muted { opacity: 0.85; }
</style>
