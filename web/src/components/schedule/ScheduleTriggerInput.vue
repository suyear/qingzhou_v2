<template>
  <div class="schedule-trigger-input">
    <div class="panel-head">
      <div>
        <strong>{{ compact ? '调用入参' : '触发入参' }}</strong>
        <p class="head-desc">{{ compact ? '用表单填写即可，也可切换 JSON' : '用表单填写即可，右侧会实时显示实际传给工作流的数据' }}</p>
      </div>
      <el-button size="small" text type="primary" @click="toggleAdvanced">
        {{ advanced ? '返回表单填写' : '高级 JSON 模式' }}
      </el-button>
    </div>

    <div class="panel-body" :class="{ compact }">
      <div class="input-side">
        <template v-if="!advanced">
          <el-alert
            v-if="!fields.length"
            type="info"
            :closable="false"
            show-icon
            class="info-alert"
            title="该工作流未预定义入参字段，可通过下方表格添加「参数名 + 参数值」"
          />

          <div v-if="fields.length" class="field-list">
            <div v-for="field in fields" :key="field.key" class="field-card">
              <div class="field-head">
                <span class="field-title">{{ fieldLabel(field) }}</span>
                <el-tag v-if="field.required" size="small" type="danger" effect="plain">必填</el-tag>
                <el-tag v-else size="small" type="info" effect="plain">可选</el-tag>
              </div>
              <p v-if="showFieldKey(field)" class="field-key">字段名：{{ field.key }}</p>

              <el-select
                v-if="field.enums?.length"
                v-model="formValues[field.key]"
                filterable
                allow-create
                clearable
                :placeholder="`请选择${fieldLabel(field)}`"
                style="width: 100%"
              >
                <el-option
                  v-for="opt in field.enums"
                  :key="String(opt)"
                  :label="String(opt)"
                  :value="opt"
                />
              </el-select>

              <el-switch
                v-else-if="field.type === 'boolean'"
                v-model="formValues[field.key]"
                active-text="是"
                inactive-text="否"
              />

              <el-input-number
                v-else-if="field.type === 'integer' || field.type === 'number'"
                v-model="formValues[field.key]"
                :precision="field.type === 'integer' ? 0 : undefined"
                controls-position="right"
                style="width: 100%"
                :placeholder="fieldPlaceholder(field)"
              />

              <el-input
                v-else-if="field.type === 'array'"
                v-model="formValues[field.key]"
                :placeholder="fieldPlaceholder(field) || '多个值用英文逗号分隔'"
              />

              <el-input
                v-else-if="field.type === 'object'"
                v-model="formValues[field.key]"
                type="textarea"
                :rows="3"
                placeholder='可填简单对象，例如 {"id": 1}'
              />

              <el-input
                v-else
                v-model="formValues[field.key]"
                clearable
                :placeholder="fieldPlaceholder(field)"
              />
            </div>
          </div>

          <div v-else class="kv-editor">
            <div v-for="(row, index) in kvRows" :key="index" class="kv-row">
              <el-input v-model="row.key" placeholder="参数名，如 userId" clearable />
              <el-input v-model="row.value" placeholder="参数值，如 10001" clearable />
              <button
                type="button"
                class="kv-remove"
                :disabled="kvRows.length <= 1"
                title="删除此行"
                @click="removeKvRow(index)"
              >
                ×
              </button>
            </div>
            <el-button size="small" @click="addKvRow">+ 添加一行参数</el-button>
          </div>
        </template>

        <template v-else>
          <div class="json-toolbar">
            <el-button size="small" text type="primary" @click="formatJson">格式化</el-button>
            <el-button size="small" text @click="jsonText = '{}'">清空</el-button>
          </div>
          <el-input
            v-model="jsonText"
            type="textarea"
            :rows="10"
            placeholder='{"参数名": "参数值"}'
            class="json-input"
          />
          <p v-if="jsonError" class="json-error">{{ jsonError }}</p>
          <p v-else class="hint">仅建议在熟悉 JSON 时使用；普通填写请切回「表单填写」</p>
        </template>
      </div>

      <div v-if="!compact" class="preview-side">
        <div class="preview-head">
          <span>传参预览</span>
          <el-tag size="small" :type="previewCount ? 'success' : 'info'">
            {{ previewCount ? `${previewCount} 个参数` : '未配置' }}
          </el-tag>
        </div>
        <DetailCodeBlock
          title="实际传参"
          :value="previewCount ? previewObject : null"
          empty-text="尚未填写参数"
          copy-message="已复制传参"
          max-height="220px"
        />
        <ul v-if="previewLines.length" class="preview-plain">
          <li v-for="line in previewLines" :key="line.key">
            <span class="plain-key">{{ line.label }}</span>
            <span class="plain-value">{{ line.display }}</span>
          </li>
        </ul>
        <p class="hint">调度触发、立即触发时，工作流将收到以上数据</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import DetailCodeBlock from '@/components/detail/DetailCodeBlock.vue'
import { fieldLabel } from '@/utils/workflowBinding'
import { parseJson, schemaToFields } from '@/utils/schema'
import {
  buildTriggerPayload,
  emptyKvRow,
  formatTriggerPreview,
  objectToFormValues,
  objectToKvRows,
  previewKeyCount,
  validateTriggerPayload,
} from '@/utils/triggerInput'

const props = defineProps({
  inputSchema: { type: [Object, String], default: null },
  modelValue: { type: [Object, String], default: null },
  compact: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue'])

const advanced = ref(false)
const jsonText = ref('{}')
const jsonError = ref('')
const formValues = reactive({})
const kvRows = ref([emptyKvRow()])

const fields = computed(() => schemaToFields(props.inputSchema))

function currentBuild() {
  return buildTriggerPayload({
    fields: fields.value,
    formValues,
    kvRows: kvRows.value,
    jsonText: jsonText.value,
    advanced: advanced.value,
  })
}

const previewObject = computed(() => {
  const built = currentBuild()
  return built?.error ? {} : (built?.value || {})
})

watch(
  () => currentBuild(),
  (built) => {
    jsonError.value = built?.error || ''
  },
  { immediate: true },
)

const previewCount = computed(() => previewKeyCount(previewObject.value))

const previewLines = computed(() => {
  const obj = previewObject.value
  return Object.entries(obj).map(([key, value]) => {
    const field = fields.value.find((item) => item.key === key)
    return {
      key,
      label: field ? fieldLabel(field) : key,
      display: formatDisplayValue(value),
    }
  })
})

function formatDisplayValue(value) {
  if (value == null) return '—'
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function fieldPlaceholder(field) {
  if (field.description) return field.description
  if (field.type === 'number' || field.type === 'integer') return '请输入数字'
  return `请输入${fieldLabel(field)}`
}

function showFieldKey(field) {
  return field.description && field.description !== field.key
}

function toggleAdvanced() {
  if (!advanced.value) {
    const payload = buildTriggerPayload({
      fields: fields.value,
      formValues,
      kvRows: kvRows.value,
      jsonText: jsonText.value,
      advanced: false,
    })
    jsonText.value = formatTriggerPreview(payload?.value || {})
  } else {
    hydrateFromObject(previewObject.value)
  }
  advanced.value = !advanced.value
}

function formatJson() {
  const parsed = parseJson(jsonText.value, null)
  if (parsed == null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    jsonError.value = 'JSON 格式不正确'
    return
  }
  jsonText.value = JSON.stringify(parsed, null, 2)
  jsonError.value = ''
}

function addKvRow() {
  kvRows.value.push(emptyKvRow())
}

function removeKvRow(index) {
  if (kvRows.value.length <= 1) {
    kvRows.value[0] = emptyKvRow()
    return
  }
  kvRows.value.splice(index, 1)
}

function hydrateFromObject(source) {
  const parsed = parseJson(source, {})
  if (fields.value.length) {
    const next = objectToFormValues(fields.value, parsed)
    Object.keys(formValues).forEach((key) => delete formValues[key])
    Object.assign(formValues, next)
  } else {
    kvRows.value = objectToKvRows(parsed)
  }
  jsonText.value = formatTriggerPreview(parsed)
  jsonError.value = ''
}

let lastEmitted = null
let hydrating = false

function serializePayload(value) {
  const parsed = parseJson(value, null)
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed) || !Object.keys(parsed).length) {
    return ''
  }
  return JSON.stringify(parsed)
}

watch(
  previewObject,
  (value) => {
    if (hydrating) return
    lastEmitted = serializePayload(value)
    emit('update:modelValue', lastEmitted ? { ...value } : null)
  },
  { deep: true },
)

function applyExternalValue(source) {
  hydrating = true
  hydrateFromObject(source)
  lastEmitted = serializePayload(previewObject.value)
  hydrating = false
}

watch(
  () => props.inputSchema,
  () => {
    applyExternalValue(props.modelValue)
  },
  { immediate: true },
)

watch(
  () => props.modelValue,
  (val) => {
    const incoming = serializePayload(val)
    if (incoming === lastEmitted) return
    if (incoming === serializePayload(previewObject.value)) return
    applyExternalValue(val)
  },
)

defineExpose({
  validate() {
    return validateTriggerPayload({
      fields: fields.value,
      formValues,
      kvRows: kvRows.value,
      jsonText: jsonText.value,
      advanced: advanced.value,
    })
  },
  getPayload() {
    const built = currentBuild()
    if (built?.error) return undefined
    return built?.value
  },
})
</script>

<style scoped>
.schedule-trigger-input {
  width: 100%;
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  overflow: hidden;
  background: #fff;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-light);
}

.panel-head strong {
  font-size: 14px;
}

.head-desc {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.panel-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(220px, 280px);
  gap: 0;
}

.panel-body.compact {
  grid-template-columns: 1fr;
}
.panel-body.compact .input-side {
  border-right: none;
}

.input-side {
  padding: 14px;
  border-right: 1px solid var(--el-border-color-lighter);
}

.preview-side {
  padding: 14px;
  background: var(--el-fill-color-blank);
}

.info-alert {
  margin-bottom: 12px;
}

.field-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.field-card {
  padding: 12px;
  border-radius: 10px;
  background: var(--el-fill-color-light);
}

.field-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.field-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.field-key {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.kv-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.kv-row {
  display: grid;
  grid-template-columns: 1fr 1fr 32px;
  gap: 8px;
  align-items: center;
}

.kv-remove {
  width: 32px;
  height: 32px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: #fff;
  color: var(--el-text-color-secondary);
  cursor: pointer;
}

.kv-remove:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.json-toolbar {
  display: flex;
  gap: 4px;
  margin-bottom: 6px;
}

.json-input :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}

.json-error {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--el-color-danger);
}

.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
}

.preview-plain {
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
}

.preview-plain li {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 0;
  border-top: 1px dashed var(--el-border-color-lighter);
  font-size: 12px;
}

.plain-key {
  color: var(--el-text-color-secondary);
}

.plain-value {
  color: var(--el-text-color-primary);
  text-align: right;
  word-break: break-all;
}

.hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.45;
}

@media (max-width: 720px) {
  .panel-body {
    grid-template-columns: 1fr;
  }
  .input-side {
    border-right: none;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }
}
</style>
