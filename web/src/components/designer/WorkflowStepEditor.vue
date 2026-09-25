<template>
  <main class="step-editor">
    <div class="editor-head">
      <div class="head-main">
        <h2 class="editor-title">编排步骤</h2>
        <p class="editor-desc">{{ chainNodes.length ? `共 ${chainNodes.length} 步，${configuredCount} 步已配置` : '从下方选择接口开始' }}</p>
        <el-progress
          v-if="chainNodes.length"
          :percentage="progressPercent"
          :stroke-width="8"
          :status="allDone ? 'success' : undefined"
          class="progress-bar"
        />
      </div>
      <div class="head-actions">
        <el-button
          v-if="chainNodes.length"
          size="small"
          :type="canvasVisible ? 'primary' : 'default'"
          @click="emit('toggle-canvas')"
        >
          {{ canvasVisible ? '收起流程图' : '查看流程图' }}
        </el-button>
        <el-button v-if="chainNodes.length" type="success" :disabled="!canTryRun" @click="emit('try-run')">试运行</el-button>
        <el-button type="primary" @click="focusQuickAdd">+ 添加步骤</el-button>
      </div>
    </div>

    <div class="editor-body">
      <div class="left-column">
        <div class="left-tabs">
          <button
            type="button"
            class="left-tab"
            :class="{ active: leftTab === 'steps' }"
            @click="leftTab = 'steps'"
          >
            步骤 ({{ chainNodes.length }})
          </button>
          <button
            type="button"
            class="left-tab"
            :class="{ active: leftTab === 'add' }"
            @click="leftTab = 'add'"
          >
            添加接口
          </button>
        </div>

        <div ref="leftScrollRef" class="left-scroll">
          <nav v-show="leftTab === 'steps'" class="step-timeline" aria-label="步骤列表">
            <div v-if="!chainNodes.length" class="timeline-empty">
              <p>还没有步骤</p>
              <el-button type="primary" size="small" @click="leftTab = 'add'">去添加接口</el-button>
            </div>

            <div
              v-for="(item, index) in chainNodes"
              :id="`step-${item.id}`"
              :key="item.id"
              class="timeline-item"
              :class="{
                active: selectedId === item.id,
                done: item.configured,
                pending: !item.configured && item.fieldCount,
              }"
            >
              <div v-if="index > 0" class="timeline-line" />
              <button
                type="button"
                class="timeline-main"
                @click="selectStep(item.id)"
              >
                <span class="timeline-index">
                  <span v-if="item.configured" class="check">✓</span>
                  <span v-else>{{ index + 1 }}</span>
                </span>
                <span class="timeline-content">
                  <span class="timeline-title-row">
                    <span class="timeline-title">{{ item.name }}</span>
                    <el-tag v-if="item.configured" size="small" type="success" effect="plain">完成</el-tag>
                    <el-tag v-else-if="item.fieldCount" size="small" type="warning" effect="plain">待填</el-tag>
                  </span>
                  <span class="timeline-sub">{{ item.method }} {{ item.path }}</span>
                  <span v-if="item.summaryLines?.length" class="timeline-summary">
                    <span
                      v-for="line in item.summaryLines.slice(0, 2)"
                      :key="line.key"
                      class="summary-chip"
                      :class="{ ok: line.done }"
                    >
                      {{ line.label }}：{{ line.text }}
                    </span>
                  </span>
                </span>
              </button>
              <div class="timeline-ops">
                <button
                  v-if="index > 0"
                  type="button"
                  class="op-btn"
                  title="上移"
                  @click="emit('move', item.id, -1)"
                >
                  ↑
                </button>
                <button
                  v-if="index < chainNodes.length - 1"
                  type="button"
                  class="op-btn"
                  title="下移"
                  @click="emit('move', item.id, 1)"
                >
                  ↓
                </button>
                <el-popconfirm title="删除此步骤？" @confirm="emit('remove', item.id)">
                  <template #reference>
                    <button type="button" class="op-btn danger" title="删除">×</button>
                  </template>
                </el-popconfirm>
              </div>
            </div>
          </nav>

          <section v-show="leftTab === 'add'" ref="quickAddRef" class="quick-add">
            <p class="quick-hint">从组件库挑选 HTTP 接口或数据库脚本，添加到调用链末尾</p>
            <el-input
              v-model="pickerKeyword"
              size="default"
              placeholder="搜索接口名称 / 编码"
              clearable
              class="quick-search"
            />
            <div v-if="!components.length" class="quick-empty">
              <p>还没有接口组件</p>
              <el-button type="primary" size="small" @click="router.push('/components')">去接入接口</el-button>
            </div>
            <div v-else-if="!groupedComponents.length" class="quick-empty">没有匹配的接口</div>
            <div v-else>
              <div v-for="group in groupedComponents" :key="group.key" class="quick-group">
                <div class="quick-group-name">{{ group.label }}</div>
                <div class="quick-list">
                  <button
                    v-for="item in group.items"
                    :key="item.id"
                    type="button"
                    class="quick-item"
                    @click="pickComponent(item)"
                  >
                    <span class="quick-main">
                      <span class="quick-name">{{ item.componentName }}</span>
                      <span class="quick-sub">{{ item.httpMethod }} {{ item.urlPath || item.urlTemplate }}</span>
                    </span>
                    <span class="quick-add-btn">添加</span>
                  </button>
                </div>
              </div>
            </div>
          </section>
        </div>
      </div>

      <section class="step-config">
        <template v-if="!selectedId && chainNodes.length">
          <div class="config-placeholder">
            <p>点击左侧步骤卡片配置参数</p>
            <el-button type="primary" @click="selectFirstPending">打开待配置步骤</el-button>
          </div>
        </template>

        <template v-else-if="!chainNodes.length">
          <div class="config-placeholder">
            <p>切换到「添加接口」选择第一个步骤</p>
            <el-button type="primary" @click="leftTab = 'add'">添加接口</el-button>
          </div>
        </template>

        <template v-else>
          <div class="config-head">
            <div class="config-badge">第 {{ selectedIndex + 1 }} 步 / 共 {{ chainNodes.length }} 步</div>
            <h3 class="config-title">{{ nodeName || '未命名步骤' }}</h3>
            <p v-if="selectedNode" class="config-sub">{{ selectedNode.method }} {{ selectedNode.path }}</p>
          </div>

          <div class="step-toolbar">
            <el-button :disabled="selectedIndex <= 0" @click="goStep(-1)">← 上一步</el-button>
            <el-button :disabled="selectedIndex >= chainNodes.length - 1" @click="goStep(1)">下一步 →</el-button>
            <el-button :disabled="selectedIndex <= 0" @click="emit('move', selectedId, -1)">上移</el-button>
            <el-button :disabled="selectedIndex >= chainNodes.length - 1" @click="emit('move', selectedId, 1)">下移</el-button>
            <el-popconfirm title="确定删除此步骤？" @confirm="emit('remove', selectedId)">
              <template #reference>
                <el-button type="danger" plain>删除</el-button>
              </template>
            </el-popconfirm>
          </div>

          <el-form label-position="top" size="default" class="config-form">
            <el-form-item label="步骤名称">
              <el-input
                :model-value="nodeName"
                placeholder="例如：查询客户、发送通知"
                @input="(val) => emit('update-name', val)"
              />
            </el-form-item>
          </el-form>

          <div v-if="!fields.length" class="config-empty">
            <el-alert type="success" :closable="false" show-icon title="此接口无需填写参数" />
            <div class="empty-actions">
              <el-button type="primary" @click="focusQuickAdd">+ 添加下一步</el-button>
              <el-button type="success" :disabled="!canTryRun" @click="emit('try-run')">试运行</el-button>
            </div>
          </div>

          <div v-else class="config-fields">
            <div v-if="requiredFields.length" class="field-section">
              <div class="section-head">
                <span class="section-title">必填参数</span>
                <span class="section-hint">{{ requiredDone }}/{{ requiredFields.length }} 已完成</span>
              </div>
              <WorkflowParamField
                v-for="field in requiredFields"
                :key="field.key"
                :field="field"
                :binding="findBinding(field.key)"
                :upstream-nodes="upstreamNodes"
                :upstream-field-map="upstreamFieldMap"
                @change="(patch) => patchBinding(field.key, patch)"
              />
            </div>

            <div v-if="optionalFields.length" class="field-section">
              <button type="button" class="section-toggle" @click="showOptional = !showOptional">
                <span class="section-title">可选参数</span>
                <span class="section-hint">{{ optionalFields.length }} 项 · {{ showOptional ? '收起' : '展开' }}</span>
              </button>
              <template v-if="showOptional">
                <WorkflowParamField
                  v-for="field in optionalFields"
                  :key="field.key"
                  :field="field"
                  :binding="findBinding(field.key)"
                  :upstream-nodes="upstreamNodes"
                  :upstream-field-map="upstreamFieldMap"
                  @change="(patch) => patchBinding(field.key, patch)"
                />
              </template>
            </div>
          </div>
        </template>
      </section>
    </div>

    <footer v-if="chainNodes.length" class="editor-footer">
      <span v-if="!allDone" class="footer-hint">还有 {{ chainNodes.length - configuredCount }} 步待配置</span>
      <span v-else class="footer-hint ok">全部已配置</span>
      <div class="footer-actions">
        <el-button type="primary" plain @click="focusQuickAdd">+ 添加</el-button>
        <el-button type="success" :disabled="!canTryRun" @click="emit('try-run')">试运行</el-button>
        <el-button type="warning" :disabled="!canPublish" @click="emit('publish')">发布</el-button>
      </div>
    </footer>
  </main>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import WorkflowParamField from './WorkflowParamField.vue'
import { isFieldConfigured } from '@/utils/workflowBinding'
import { CATEGORY_LABEL } from '@/utils/schema'

const props = defineProps({
  chainNodes: { type: Array, default: () => [] },
  selectedId: { type: String, default: '' },
  nodeName: { type: String, default: '' },
  fields: { type: Array, default: () => [] },
  bindings: { type: Array, default: () => [] },
  upstreamNodes: { type: Array, default: () => [] },
  upstreamFieldMap: { type: Object, default: () => ({}) },
  components: { type: Array, default: () => [] },
  canTryRun: { type: Boolean, default: false },
  canPublish: { type: Boolean, default: false },
  canvasVisible: { type: Boolean, default: false },
})

const emit = defineEmits(['select', 'remove', 'move', 'add', 'update-name', 'update-bindings', 'try-run', 'publish', 'toggle-canvas'])

const router = useRouter()
const leftTab = ref('add')
const pickerKeyword = ref('')
const quickAddRef = ref(null)
const leftScrollRef = ref(null)
const showOptional = ref(false)

const selectedIndex = computed(() => {
  const idx = props.chainNodes.findIndex((item) => item.id === props.selectedId)
  return idx >= 0 ? idx : 0
})
const selectedNode = computed(() => props.chainNodes.find((item) => item.id === props.selectedId))
const requiredFields = computed(() => props.fields.filter((item) => item.required))
const optionalFields = computed(() => props.fields.filter((item) => !item.required))
const configuredCount = computed(() => props.chainNodes.filter((item) => item.configured).length)
const allDone = computed(() => props.chainNodes.length > 0 && configuredCount.value === props.chainNodes.length)
const progressPercent = computed(() => {
  if (!props.chainNodes.length) return 0
  return Math.round((configuredCount.value / props.chainNodes.length) * 100)
})
const requiredDone = computed(() =>
  requiredFields.value.filter((field) => isFieldConfigured(findBinding(field.key), field)).length,
)
const filteredComponents = computed(() => {
  const kw = pickerKeyword.value.trim()
  if (!kw) return props.components
  return props.components.filter((item) =>
    `${item.componentName}${item.componentCode}`.includes(kw),
  )
})
const groupedComponents = computed(() => {
  const map = new Map()
  for (const item of filteredComponents.value) {
    const key = item.category || 'HTTP'
    if (!map.has(key)) {
      map.set(key, { key, label: CATEGORY_LABEL[key] || key, items: [] })
    }
    map.get(key).items.push(item)
  }
  return [...map.values()]
})

watch(() => props.selectedId, async (id) => {
  showOptional.value = false
  if (!id) return
  leftTab.value = 'steps'
  await nextTick()
  document.getElementById(`step-${id}`)?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
})

watch(() => props.chainNodes.length, (len, prev) => {
  if (len > (prev || 0)) leftTab.value = 'steps'
  if (!len) leftTab.value = 'add'
})

function findBinding(key) {
  return props.bindings.find((item) => item.key === key) || { key, mode: 'fixed', value: '' }
}

function patchBinding(key, patch) {
  const next = props.bindings.map((item) => (item.key === key ? { ...item, ...patch } : item))
  if (!next.find((item) => item.key === key)) {
    next.push({ key, mode: 'fixed', value: '', ...patch })
  }
  emit('update-bindings', next)
}

function selectStep(id) {
  if (props.selectedId === id) return
  emit('select', id)
}

function goStep(delta) {
  const next = props.chainNodes[selectedIndex.value + delta]
  if (next) emit('select', next.id)
}

function pickComponent(item) {
  pickerKeyword.value = ''
  emit('add', item)
  leftTab.value = 'steps'
}

function selectFirstPending() {
  leftTab.value = 'steps'
  const pending = props.chainNodes.find((item) => !item.configured)
  emit('select', (pending || props.chainNodes[0]).id)
}

function focusQuickAdd() {
  leftTab.value = 'add'
  nextTick(() => {
    quickAddRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    quickAddRef.value?.querySelector('input')?.focus()
  })
}
</script>

<style scoped>
.step-editor {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--qz-card);
  border-right: 1px solid var(--qz-border);
}
.editor-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--qz-border);
}
.head-main { flex: 1; min-width: 0; }
.head-actions { display: flex; gap: 8px; flex-shrink: 0; }
.editor-title { margin: 0; font-size: 16px; font-weight: 700; }
.editor-desc { margin: 4px 0 8px; font-size: 12px; color: var(--qz-text-muted); }
.progress-bar { max-width: 260px; }
.editor-body {
  flex: 1;
  display: flex;
  min-height: 0;
  overflow: hidden;
}
.left-column {
  width: 360px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--qz-border);
  background: var(--qz-fill);
  min-height: 0;
}
.left-tabs {
  display: flex;
  border-bottom: 1px solid var(--qz-border);
  flex-shrink: 0;
}
.left-tab {
  flex: 1;
  padding: 12px 8px;
  border: none;
  background: transparent;
  font-size: 13px;
  font-weight: 600;
  color: var(--qz-text-muted);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}
.left-tab.active {
  color: var(--el-color-primary);
  border-bottom-color: var(--el-color-primary);
  background: var(--qz-card);
}
.left-scroll {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
}
.step-timeline {
  padding: 10px;
}
.timeline-empty {
  padding: 32px 12px;
  text-align: center;
  font-size: 13px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
}
.timeline-item {
  position: relative;
  display: flex;
  align-items: stretch;
  gap: 6px;
  margin-bottom: 10px;
  border-radius: var(--qz-radius);
  border: 2px solid var(--qz-border);
  background: var(--qz-card);
  overflow: hidden;
  transition: border-color 0.12s, box-shadow 0.12s;
}
.timeline-item:hover { border-color: var(--el-color-primary-light-5); }
.timeline-item.active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 3px var(--qz-primary-soft);
}
.timeline-item.done:not(.active) { border-color: var(--el-color-success-light-5); }
.timeline-item.pending:not(.active) { border-color: var(--el-color-warning-light-5); }
.timeline-line {
  position: absolute;
  top: -11px;
  left: 26px;
  width: 2px;
  height: 11px;
  background: var(--el-color-primary-light-5);
  pointer-events: none;
}
.timeline-main {
  flex: 1;
  display: flex;
  gap: 10px;
  align-items: flex-start;
  min-width: 0;
  padding: 12px 10px;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
  touch-action: manipulation;
}
.timeline-main:hover { background: var(--qz-primary-soft); }
.timeline-index {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.timeline-item.done .timeline-index { background: var(--el-color-success); }
.timeline-item.pending .timeline-index { background: var(--el-color-warning); }
.timeline-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.timeline-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.timeline-title {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.3;
}
.timeline-sub {
  font-size: 11px;
  color: #64748b;
  word-break: break-all;
}
.timeline-summary {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.summary-chip {
  font-size: 11px;
  color: var(--el-color-warning-dark-2);
  background: var(--el-color-warning-light-9);
  border-radius: 4px;
  padding: 2px 6px;
  line-height: 1.3;
}
.summary-chip.ok {
  color: var(--el-color-success-dark-2);
  background: var(--el-color-success-light-9);
}
.timeline-ops {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
  padding: 6px 6px 6px 0;
  flex-shrink: 0;
}
.op-btn {
  width: 32px;
  height: 32px;
  border: 1px solid var(--qz-border);
  border-radius: 6px;
  background: var(--qz-card);
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  color: var(--qz-text);
  touch-action: manipulation;
}
.op-btn:hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
  background: var(--qz-primary-soft);
}
.op-btn.danger:hover {
  border-color: var(--el-color-danger);
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}
.quick-add {
  padding: 12px;
}
.quick-hint {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--qz-text-muted);
  line-height: 1.45;
}
.quick-search { margin-bottom: 10px; }
.quick-group { margin-bottom: 14px; }
.quick-group-name {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--qz-text-muted);
}
.quick-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.quick-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-height: 52px;
  padding: 12px 14px;
  border: 1px solid var(--qz-border);
  border-radius: var(--qz-radius);
  background: var(--qz-card);
  cursor: pointer;
  text-align: left;
  touch-action: manipulation;
}
.quick-item:hover,
.quick-item:active {
  border-color: var(--el-color-primary);
  background: var(--qz-primary-soft);
}
.quick-main { flex: 1; min-width: 0; }
.quick-name { display: block; font-size: 14px; font-weight: 600; }
.quick-sub {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: #64748b;
}
.quick-add-btn {
  flex-shrink: 0;
  padding: 4px 12px;
  border-radius: 999px;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}
.quick-empty {
  padding: 24px;
  text-align: center;
  font-size: 13px;
  color: var(--qz-text-muted);
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
}
.step-config {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 16px 20px 72px;
}
.config-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 240px;
  color: var(--qz-text-muted);
  font-size: 14px;
}
.config-head { margin-bottom: 14px; }
.config-badge {
  display: inline-block;
  font-size: 11px;
  font-weight: 600;
  color: var(--el-color-primary);
  background: var(--qz-primary-soft);
  padding: 2px 8px;
  border-radius: 999px;
  margin-bottom: 6px;
}
.config-title { margin: 0; font-size: 18px; font-weight: 700; }
.config-sub { margin: 4px 0 0; font-size: 12px; color: #64748b; }
.step-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 0 14px;
  border-bottom: 1px solid var(--qz-border);
  margin-bottom: 14px;
}
.config-form { margin-bottom: 8px; }
.config-empty { padding: 12px 0; }
.empty-actions { display: flex; gap: 8px; margin-top: 14px; flex-wrap: wrap; }
.field-section { margin-bottom: 16px; }
.section-head,
.section-toggle {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  margin-bottom: 10px;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
}
.section-toggle:hover .section-title {
  color: var(--el-color-primary);
}
.section-title { font-size: 13px; font-weight: 700; }
.section-hint { font-size: 12px; color: var(--qz-text-muted); }
.editor-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  border-top: 1px solid var(--qz-border);
  background: var(--qz-card);
  flex-shrink: 0;
  z-index: 5;
}
.footer-hint { font-size: 13px; color: var(--el-color-warning); }
.footer-hint.ok { color: var(--el-color-success); }
.footer-actions { display: flex; gap: 8px; flex-wrap: wrap; }
@media (max-width: 900px) {
  .editor-body { flex-direction: column; }
  .left-column { width: 100%; max-height: 42vh; }
}
</style>
