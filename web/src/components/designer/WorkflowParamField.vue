<template>
  <div class="param-field" :class="{ done: isDone }">
    <div class="param-head">
      <div class="param-title">
        <div class="param-label">{{ fieldLabel(field) }}</div>
        <div v-if="field.key && fieldLabel(field) !== field.key" class="param-key" :title="field.key">
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
        title="移除此项"
        @click="emit('remove', field.key)"
      >×</button>
    </div>

    <div v-if="suggestion && !suggestionApplied" class="suggest">
      <span>建议：用第 {{ suggestion.stepIndex }} 步「{{ suggestion.stepName }}」交出的{{ suggestion.label }}</span>
      <button type="button" class="suggest-btn" @click="applySuggestion">就用这个</button>
    </div>

    <div class="mode-switch" role="tablist">
      <button
        type="button"
        class="mode-chip"
        :class="{ active: choice === 'fixed' }"
        @click="chooseFixed"
      >自己填写</button>
      <button
        type="button"
        class="mode-chip"
        :class="{ active: choice === 'caller' }"
        @click="chooseCaller"
      >调用时传入</button>
      <button
        type="button"
        class="mode-chip"
        :class="{ active: choice === 'previous' }"
        :disabled="!upstreamSources.length"
        @click="choosePrevious"
      >用前面步骤的结果</button>
    </div>

    <div v-if="choice === 'fixed'" class="param-value">
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
        :placeholder="field.type === 'array' ? '多个值用逗号分隔' : '按接口要求填写'"
        @input="(val) => emitChange({ mode: 'fixed', value: val })"
      />
      <el-input
        v-else
        :model-value="binding.value ?? ''"
        :placeholder="field.description || '每次运行都用这个内容'"
        @input="(val) => emitChange({ mode: 'fixed', value: val })"
      />
    </div>

    <div v-else-if="choice === 'caller'" class="param-value">
      <p class="lead">调用这个工作流时，由外面填写。</p>
      <el-select
        :model-value="callerValue"
        filterable
        allow-create
        default-first-option
        placeholder="选一项，或起一个新名字"
        style="width: 100%"
        @change="onCallerChange"
      >
        <el-option :value="field.key" :label="`就用「${fieldLabel(field)}」`" />
        <el-option
          v-for="item in otherInputs"
          :key="item.key"
          :value="item.key"
          :label="inputOptionLabel(item)"
        />
      </el-select>
    </div>

    <div v-else class="param-value">
      <p class="lead">选前面哪一步，再选它交出的哪一项。</p>
      <div class="step-picks">
        <button
          v-for="(step, index) in upstreamSources"
          :key="step.id"
          type="button"
          class="step-pick"
          :class="{ active: activeStepId === step.id }"
          @click="pickStep(step)"
        >
          <span class="step-no">第 {{ index + 1 }} 步</span>
          <span class="step-name">{{ step.name }}</span>
        </button>
      </div>
      <div v-if="activeStep" class="result-picks">
        <button
          v-for="opt in activeOptions"
          :key="`${opt.fromSource}:${opt.fromField}`"
          type="button"
          class="result-chip"
          :class="{ active: isOptionActive(opt) }"
          @click="pickOption(activeStep, opt)"
        >
          {{ opt.label }}
        </button>
        <button type="button" class="text-btn" @click="showFilled = !showFilled">
          {{ showFilled ? '收起已填内容' : '用这一步里已经填过的内容' }}
        </button>
        <div v-if="showFilled" class="result-picks nested">
          <button
            v-for="opt in activeFilled"
            :key="`req:${opt.fromField}`"
            type="button"
            class="result-chip"
            :class="{ active: isOptionActive(opt) }"
            @click="pickOption(activeStep, opt)"
          >
            {{ opt.label }}
          </button>
        </div>
        <button type="button" class="text-btn" @click="showPath = !showPath">
          {{ showPath ? '收起' : '按具体项目名取值' }}
        </button>
        <el-input
          v-if="showPath"
          :model-value="pathDraft"
          placeholder="例如第一行的姓名：rows[0].name"
          @change="onPathCommit"
        />
      </div>
    </div>

    <p class="sentence">{{ sentence }}</p>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { Check } from '@element-plus/icons-vue'
import {
  bindingSummary,
  fieldLabel,
  filledOptionsForStep,
  findUpstreamFieldMatch,
  handoffOptionsForStep,
  isFieldConfigured,
  suggestHandoff,
} from '@/utils/workflowBinding'

const props = defineProps({
  field: { type: Object, required: true },
  binding: { type: Object, default: () => ({}) },
  upstreamSources: { type: Array, default: () => [] },
  inputFields: { type: Array, default: () => [] },
})

const emit = defineEmits(['change', 'remove'])

const showFilled = ref(false)
const showPath = ref(false)

const choice = computed(() => {
  const mode = props.binding.mode || 'fixed'
  if (mode === 'runtime') return 'caller'
  if (mode === 'upstream') return 'previous'
  return 'fixed'
})

const isDone = computed(() => {
  if (!props.field.required) return false
  return isFieldConfigured(props.binding, props.field)
})

const suggestion = computed(() => suggestHandoff(props.field.key, props.upstreamSources))
const suggestionApplied = computed(() => (
  props.binding.mode === 'upstream'
  && props.binding.fromNode === suggestion.value?.fromNode
  && props.binding.fromField === suggestion.value?.fromField
  && props.binding.fromSource !== 'request'
))

const otherInputs = computed(() =>
  (props.inputFields || []).filter((item) => item.key && item.key !== props.field.key),
)

const callerValue = computed(() => props.binding.inputKey || props.field.key)

const activeStepId = computed(() => {
  if (props.binding.mode === 'upstream' && props.binding.fromNode) return props.binding.fromNode
  const last = props.upstreamSources[props.upstreamSources.length - 1]
  return last?.id || ''
})

const activeStep = computed(() =>
  props.upstreamSources.find((item) => item.id === activeStepId.value) || null,
)

const activeOptions = computed(() => handoffOptionsForStep(activeStep.value))
const activeFilled = computed(() => filledOptionsForStep(activeStep.value))

const pathDraft = computed(() => {
  if (props.binding.mode !== 'upstream' || props.binding.fromSource === 'request') return ''
  const field = props.binding.fromField
  if (!field || field === '__whole__' || field === '*') return ''
  const known = activeOptions.value.some((item) => item.fromField === field)
  return known ? '' : field
})

const sentence = computed(() => bindingSummary(props.binding, props.field, {
  nodeNames: Object.fromEntries((props.upstreamSources || []).map((step) => [step.id, step.name])),
  nodeIndexes: Object.fromEntries((props.upstreamSources || []).map((step, index) => [step.id, index + 1])),
  inputLabels: Object.fromEntries((props.inputFields || []).map((item) => [item.key, item.description || item.key])),
}))

watch(() => props.binding.fromSource, (value) => {
  if (value === 'request') showFilled.value = true
}, { immediate: true })

watch(pathDraft, (value) => {
  if (value) showPath.value = true
}, { immediate: true })

function inputOptionLabel(item) {
  if (item.description && item.description !== item.key) {
    return `${item.description}（${item.key}）`
  }
  return item.key
}

function emitChange(patch) {
  emit('change', { key: props.field.key, ...patch })
}

function clearLink() {
  return {
    fromNode: '',
    fromField: '',
    fromSource: '',
    inputKey: '',
  }
}

function chooseFixed() {
  emitChange({
    mode: 'fixed',
    value: props.binding.value ?? '',
    ...clearLink(),
  })
}

function chooseCaller() {
  emitChange({
    ...clearLink(),
    mode: 'runtime',
    inputKey: props.binding.inputKey || props.field.key,
    value: '',
  })
}

function emitUpstream(step, fromField, fromSource) {
  emitChange({
    mode: 'upstream',
    fromNode: step.id,
    fromField: fromField || '__whole__',
    fromSource: fromSource === 'request' ? 'request' : 'output',
    inputKey: '',
    value: '',
  })
}

function choosePrevious() {
  if (props.binding.mode === 'upstream' && props.binding.fromNode) return
  if (suggestion.value) {
    const step = props.upstreamSources.find((item) => item.id === suggestion.value.fromNode)
    if (step) {
      emitUpstream(step, suggestion.value.fromField, 'output')
      return
    }
  }
  const step = props.upstreamSources[props.upstreamSources.length - 1]
  if (!step) {
    chooseCaller()
    return
  }
  const matched = findUpstreamFieldMatch([step], props.field.key)
  if (matched?.fromSource === 'output') {
    emitUpstream(step, matched.field, 'output')
    return
  }
  emitUpstream(step, '__whole__', 'output')
}

function pickStep(step) {
  const matched = findUpstreamFieldMatch([step], props.field.key)
  if (matched?.fromSource === 'output') {
    emitUpstream(step, matched.field, 'output')
    return
  }
  const current = props.binding.fromField
  const opts = handoffOptionsForStep(step)
  if (props.binding.fromNode === step.id && opts.some((item) => item.fromField === current)) return
  emitUpstream(step, '__whole__', 'output')
}

function pickOption(step, opt) {
  emitUpstream(step, opt.fromField, opt.fromSource)
}

function isOptionActive(opt) {
  if (props.binding.mode !== 'upstream') return false
  if (props.binding.fromNode !== activeStepId.value) return false
  const side = props.binding.fromSource === 'request' ? 'request' : 'output'
  if (side !== opt.fromSource) return false
  const field = props.binding.fromField === '*' ? '__whole__' : (props.binding.fromField || '__whole__')
  return field === opt.fromField
}

function applySuggestion() {
  const step = props.upstreamSources.find((item) => item.id === suggestion.value?.fromNode)
  if (!step || !suggestion.value) return
  emitUpstream(step, suggestion.value.fromField, 'output')
}

function onCallerChange(raw) {
  const inputKey = String(raw || '').trim() || props.field.key
  emitChange({
    ...clearLink(),
    mode: 'runtime',
    inputKey,
    value: '',
  })
}

function onPathCommit(raw) {
  const text = String(raw || '').trim().replace(/^\$\.?/, '')
  if (!activeStep.value) return
  if (!text || text === '*' || text === '$') {
    emitUpstream(activeStep.value, '__whole__', 'output')
    return
  }
  emitUpstream(activeStep.value, text, 'output')
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
.suggest {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--el-color-success-light-9);
  color: var(--el-color-success-dark-2);
  font-size: 12px;
  line-height: 1.5;
}
.suggest-btn {
  border: 0;
  border-radius: 6px;
  padding: 4px 10px;
  background: var(--el-color-success);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
}
.mode-switch {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}
.mode-chip {
  border: 1px solid var(--qz-border);
  background: var(--qz-fill);
  padding: 6px 10px;
  font-size: 12px;
  line-height: 1.4;
  border-radius: 999px;
  color: var(--qz-text-muted);
  cursor: pointer;
}
.mode-chip.active {
  background: var(--qz-card);
  border-color: var(--el-color-primary-light-5);
  color: var(--el-color-primary);
  font-weight: 600;
}
.mode-chip:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.param-value { margin-top: 2px; }
.lead {
  margin: 0 0 8px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
}
.step-picks,
.result-picks {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.result-picks { margin-top: 8px; }
.result-picks.nested { margin-top: 6px; }
.step-pick,
.result-chip,
.text-btn {
  border: 1px solid var(--qz-border);
  background: var(--qz-card);
  border-radius: 8px;
  cursor: pointer;
  text-align: left;
}
.step-pick {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 108px;
  padding: 6px 10px;
}
.step-pick.active,
.result-chip.active {
  border-color: var(--el-color-primary);
  background: var(--qz-primary-soft);
  color: var(--el-color-primary);
}
.step-no { font-size: 11px; color: var(--qz-text-muted); }
.step-pick.active .step-no { color: var(--el-color-primary); }
.step-name { font-size: 13px; font-weight: 600; }
.result-chip,
.text-btn {
  padding: 5px 10px;
  font-size: 12px;
}
.text-btn {
  border-style: dashed;
  color: var(--qz-text-muted);
  background: transparent;
}
.sentence {
  margin: 8px 0 0;
  font-size: 12px;
  color: #475569;
  line-height: 1.5;
}
</style>
