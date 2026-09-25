<template>
  <aside class="inspector">
    <div v-if="!selectedId" class="inspector-empty">
      <p class="inspector-title">编排步骤</p>
      <p v-if="!chainNodes.length" class="hint-block">点击左侧组件，按顺序添加调用步骤</p>
      <div v-else class="chain-list">
        <button
          v-for="(item, index) in chainNodes"
          :key="item.id"
          type="button"
          class="chain-item"
          @click="emit('select', item.id)"
        >
          <span class="chain-index">{{ index + 1 }}</span>
          <span class="chain-name">{{ item.name }}</span>
          <span class="chain-sub">{{ item.method }} {{ item.path }}</span>
        </button>
      </div>
      <p v-if="chainNodes.length" class="hint-block">点击步骤可配置参数</p>
    </div>

    <div v-else class="inspector-body">
      <div class="node-head">
        <p class="inspector-title">第 {{ selectedIndex + 1 }} 步</p>
        <el-input
          :model-value="nodeName"
          placeholder="步骤名称"
          size="small"
          @input="(val) => emit('update-name', val)"
        />
      </div>

      <p v-if="!fields.length" class="hint-block">该接口没有需要填写的参数，保存后即可试运行。</p>

      <div v-for="field in fields" :key="field.key" class="param-card">
        <div class="param-head">
          <span class="param-label">{{ fieldLabel(field) }}</span>
          <span v-if="field.required" class="param-req">必填</span>
        </div>
        <div class="param-key">{{ field.key }}</div>

        <el-radio-group
          :model-value="bindingMode(field.key)"
          size="small"
          class="mode-group"
          @change="(mode) => setMode(field, mode)"
        >
          <el-radio-button value="fixed">固定值</el-radio-button>
          <el-radio-button v-if="upstreamNodes.length" value="upstream">来自上一步</el-radio-button>
          <el-radio-button value="runtime">试运行时填</el-radio-button>
        </el-radio-group>

        <div v-if="bindingMode(field.key) === 'fixed'" class="param-input">
          <el-select
            v-if="field.enums.length"
            :model-value="bindingValue(field.key)"
            filterable
            allow-create
            placeholder="选择或输入"
            style="width: 100%"
            @change="(val) => setValue(field.key, val)"
          >
            <el-option v-for="opt in field.enums" :key="opt" :value="String(opt)" :label="String(opt)" />
          </el-select>
          <el-input
            v-else-if="field.type === 'object' || field.type === 'array'"
            :model-value="bindingValue(field.key)"
            type="textarea"
            :rows="field.type === 'object' ? 3 : 2"
            :placeholder="field.type === 'array' ? '多个值用逗号分隔' : '{ }'"
            @input="(val) => setValue(field.key, val)"
          />
          <el-input
            v-else
            :model-value="bindingValue(field.key)"
            :placeholder="field.description || '填写固定值'"
            @input="(val) => setValue(field.key, val)"
          />
        </div>

        <div v-else-if="bindingMode(field.key) === 'upstream'" class="param-input upstream-row">
          <el-select
            :model-value="bindingFromNode(field.key)"
            placeholder="选择上一步"
            style="width: 100%"
            @change="(val) => setUpstream(field.key, { fromNode: val })"
          >
            <el-option
              v-for="node in upstreamNodes"
              :key="node.id"
              :label="node.name"
              :value="node.id"
            />
          </el-select>
          <el-select
            :model-value="bindingFromField(field.key)"
            filterable
            allow-create
            placeholder="选择返回字段"
            style="width: 100%"
            @change="(val) => setUpstream(field.key, { fromField: val })"
          >
            <el-option
              v-for="name in upstreamFieldOptions(bindingFromNode(field.key))"
              :key="name"
              :value="name"
              :label="name"
            />
          </el-select>
        </div>

        <p v-else class="runtime-hint">试运行和调度时由外部传入此参数</p>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { fieldLabel } from '@/utils/workflowBinding'

const props = defineProps({
  selectedId: { type: String, default: '' },
  nodeName: { type: String, default: '' },
  fields: { type: Array, default: () => [] },
  bindings: { type: Array, default: () => [] },
  chainNodes: { type: Array, default: () => [] },
  upstreamNodes: { type: Array, default: () => [] },
  upstreamFieldMap: { type: Object, default: () => ({}) },
})

const emit = defineEmits(['select', 'update-name', 'update-bindings'])

const selectedIndex = computed(() => {
  const idx = props.chainNodes.findIndex((item) => item.id === props.selectedId)
  return idx >= 0 ? idx : 0
})

function findBinding(key) {
  return props.bindings.find((item) => item.key === key)
}

function bindingMode(key) {
  return findBinding(key)?.mode || 'fixed'
}

function bindingValue(key) {
  return findBinding(key)?.value ?? ''
}

function bindingFromNode(key) {
  return findBinding(key)?.fromNode || props.upstreamNodes[0]?.id || ''
}

function bindingFromField(key) {
  return findBinding(key)?.fromField || ''
}

function upstreamFieldOptions(nodeId) {
  return props.upstreamFieldMap[nodeId] || []
}

function patchBindings(key, patch) {
  const next = props.bindings.map((item) => (item.key === key ? { ...item, ...patch } : item))
  if (!next.find((item) => item.key === key)) {
    next.push({ key, mode: 'fixed', value: '', ...patch })
  }
  emit('update-bindings', next)
}

function setMode(field, mode) {
  const current = findBinding(field.key)
  const patch = { mode }
  if (mode === 'fixed') {
    patch.value = current?.value ?? ''
    patch.fromNode = ''
    patch.fromField = ''
  } else if (mode === 'upstream') {
    patch.fromNode = current?.fromNode || props.upstreamNodes[0]?.id || ''
    patch.fromField = current?.fromField || upstreamFieldOptions(patch.fromNode)[0] || field.key
    patch.value = ''
  } else {
    patch.value = ''
    patch.fromNode = ''
    patch.fromField = ''
  }
  patchBindings(field.key, patch)
}

function setValue(key, value) {
  patchBindings(key, { mode: 'fixed', value })
}

function setUpstream(key, patch) {
  const current = findBinding(key) || { key, mode: 'upstream' }
  patchBindings(key, {
    mode: 'upstream',
    fromNode: patch.fromNode ?? current.fromNode ?? props.upstreamNodes[0]?.id,
    fromField: patch.fromField ?? current.fromField,
    value: '',
  })
}
</script>

<style scoped>
.inspector {
  width: 320px;
  border-left: 1px solid var(--qz-border);
  padding: 12px;
  overflow: auto;
  background: var(--qz-fill);
}
.inspector-title {
  margin: 0 0 10px;
  font-size: 14px;
  font-weight: 700;
}
.inspector-empty .hint-block,
.inspector-body .hint-block {
  color: #94a3b8;
  font-size: 12px;
  margin: 0 0 10px;
  line-height: 1.5;
}
.chain-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.chain-item {
  display: grid;
  grid-template-columns: 28px 1fr;
  grid-template-rows: auto auto;
  gap: 2px 10px;
  padding: 10px 12px;
  border: 1px solid var(--qz-border);
  border-radius: var(--qz-radius);
  background: var(--qz-card);
  text-align: left;
  cursor: pointer;
}
.chain-item:hover {
  border-color: var(--el-color-primary-light-5);
}
.chain-index {
  grid-row: span 2;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.chain-name {
  font-size: 13px;
  font-weight: 600;
}
.chain-sub {
  font-size: 11px;
  color: #64748b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.node-head {
  margin-bottom: 12px;
}
.param-card {
  margin-bottom: 14px;
  padding: 10px 12px;
  border: 1px solid var(--qz-border);
  border-radius: var(--qz-radius);
  background: var(--qz-card);
}
.param-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.param-label {
  font-size: 13px;
  font-weight: 600;
}
.param-req {
  font-size: 11px;
  color: var(--el-color-danger);
}
.param-key {
  margin: 2px 0 8px;
  font-size: 11px;
  color: #94a3b8;
  font-family: ui-monospace, Menlo, monospace;
}
.mode-group {
  display: flex;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.param-input { margin-top: 4px; }
.upstream-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.runtime-hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: #64748b;
}
</style>
