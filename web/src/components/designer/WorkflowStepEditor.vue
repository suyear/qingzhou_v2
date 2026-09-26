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
          {{ canvasVisible ? '收起流程图' : '流程图预览' }}
        </el-button>
        <el-tooltip :disabled="canTryRun || !chainNodes.length" :content="tryRunHint || '请先完成配置'" placement="bottom">
          <span v-if="chainNodes.length" class="head-btn-wrap">
            <el-button type="success" :disabled="!canTryRun" @click="emit('try-run')">试运行</el-button>
          </span>
        </el-tooltip>
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
            :class="{ active: leftTab === 'inputs' }"
            @click="leftTab = 'inputs'"
          >
            入参 ({{ inputFields.length }})
          </button>
          <button
            type="button"
            class="left-tab"
            :class="{ active: leftTab === 'outputs' }"
            @click="leftTab = 'outputs'"
          >
            出参
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
            <p v-else class="timeline-tip">点击选中配置；卡片间 ⊕ 可插入；↑↓ 调顺序</p>

            <template v-for="(item, index) in chainNodes" :key="item.id">
              <div
                :id="`step-${item.id}`"
                class="timeline-item"
                :class="{
                  active: selectedId === item.id,
                  done: item.configured,
                  pending: !item.configured && item.fieldCount,
                }"
              >
                <div v-if="index > 0" class="timeline-line" :class="{ linked: item.deps?.length }" />
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
                    <span v-if="item.deps?.length" class="timeline-deps">
                      <span v-for="dep in item.deps.slice(0, 2)" :key="dep.fromNode" class="dep-chip">
                        {{ dep.label }}
                      </span>
                    </span>
                    <span v-else-if="index > 0" class="timeline-deps muted">未接上游数据</span>
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
              <button
                type="button"
                class="timeline-insert"
                :title="`在第 ${index + 1} 步后插入`"
                @click="beginInsertAfter(item.id, index)"
              >
                <span class="insert-plus">⊕</span>
                <span class="insert-text">插入下一步</span>
              </button>
            </template>
          </nav>

          <section v-show="leftTab === 'inputs'" class="inputs-panel">
            <p class="quick-hint">试运行、定时调度、开放 API 共用这组入参。步骤里选「外部传入」时可绑定到这里的键（支持异名）。</p>
            <div v-if="!inputFields.length" class="inputs-empty">
              <p>还没有工作流入参</p>
              <p class="inputs-empty-sub">可在此添加，或在步骤参数中选「外部传入」自动出现</p>
            </div>
            <div v-for="(row, index) in inputFields" :key="`${row.key}-${index}`" class="input-row">
              <el-input
                :model-value="row.key"
                placeholder="参数键，如 userId"
                class="input-key"
                @change="(val) => onInputKeyChange(index, val)"
              />
              <el-input
                :model-value="row.description"
                placeholder="说明（选填）"
                class="input-desc"
                @input="(val) => patchInputField(index, { description: val })"
              />
              <el-checkbox
                :model-value="row.required"
                @change="(val) => patchInputField(index, { required: val })"
              >必填</el-checkbox>
              <button type="button" class="op-btn danger" title="删除" @click="removeInputField(index)">×</button>
            </div>
            <el-button type="primary" plain class="add-input-btn" @click="addInputField">+ 添加入参</el-button>
          </section>

          <section v-show="leftTab === 'outputs'" class="inputs-panel">
            <p class="quick-hint">配置开放 API / 调度看到的结果。试运行主区会展示同一形态；节点明细仅供调试。</p>
            <el-alert
              v-if="workflowStatus === 'PUBLISHED'"
              type="warning"
              :closable="false"
              show-icon
              class="output-alert"
              :title="workflowDirty
                ? '草稿出参已改，开放/调度仍用已发布快照，需重新发布才生效'
                : `开放/调度使用已发布快照；试运行走当前草稿`"
            />
            <div class="output-mode">
              <el-radio-group :model-value="outputSchema.mode || 'last'" @change="onOutputModeChange">
                <el-radio-button value="last">完整结果</el-radio-button>
                <el-radio-button value="firstRow">查询首行</el-radio-button>
                <el-radio-button value="fields">字段投影</el-radio-button>
              </el-radio-group>
            </div>
            <p v-if="(outputSchema.mode || 'last') === 'last'" class="inputs-empty-sub">
              对外返回{{ lastStepName ? `第末步「${lastStepName}」` : '最后一步' }}的完整结果
              （DB 查询为 rowCount / rows / truncated 整包）。
            </p>
            <p v-else-if="outputSchema.mode === 'firstRow'" class="inputs-empty-sub">
              适合「按 ID 查单条」：从末步查询结果的 <code>rows[0]</code> 取出对象直接对外返回；
              无行时为 null；若末步不是查询结果则与「完整结果」相同。
            </p>
            <template v-else>
              <div v-if="!(outputSchema.fields || []).length" class="inputs-empty">
                <p>还没有出参字段</p>
                <p class="inputs-empty-sub warn">未配置时保存会按「最后一步」生效，不会静默变成多节点大包</p>
              </div>
              <div
                v-for="(row, index) in (outputSchema.fields || [])"
                :key="`out-${index}`"
                class="input-row"
              >
                <el-input
                  :model-value="row.key"
                  placeholder="对外字段名"
                  @change="(val) => patchOutputField(index, { key: val })"
                />
                <el-select
                  :model-value="outputSourceValue(row)"
                  filterable
                  allow-create
                  default-first-option
                  placeholder="来源：整步或字段（可手输 $.rows[0].列名）"
                  style="width: 100%"
                  @change="(val) => onOutputSourceChange(index, val)"
                >
                  <el-option-group
                    v-for="(step, si) in allStepSources"
                    :key="step.id"
                    :label="`第 ${si + 1} 步 · ${step.name}`"
                  >
                    <el-option
                      :value="`${step.id}:*`"
                      :label="`完整响应`"
                    />
                    <el-option
                      v-for="name in step.responseFields"
                      :key="`${step.id}:${name}`"
                      :value="`${step.id}:${name}`"
                      :label="fieldOptionLabel(name)"
                    />
                  </el-option-group>
                </el-select>
                <el-input
                  :model-value="row.description"
                  placeholder="说明（选填）"
                  @input="(val) => patchOutputField(index, { description: val })"
                />
                <button type="button" class="op-btn danger" title="删除" @click="removeOutputField(index)">×</button>
              </div>
              <el-button
                type="primary"
                plain
                class="add-input-btn"
                :disabled="!allStepSources.length"
                @click="addOutputField"
              >+ 添加出参字段</el-button>
            </template>

            <div class="output-preview">
              <div class="output-preview-title">预计返回形状</div>
              <pre class="output-preview-code">{{ outputShapeText }}</pre>
              <template v-if="recentPublicOutput !== undefined && recentPublicOutput !== null">
                <div class="output-preview-title mt">最近试跑 · 对外出参</div>
                <pre class="output-preview-code">{{ recentOutputText }}</pre>
              </template>
            </div>
          </section>

          <section v-show="leftTab === 'add'" ref="quickAddRef" class="quick-add">
            <p class="quick-hint">{{ addHint }}</p>
            <el-alert
              v-if="pendingInsertAfterId"
              type="info"
              :closable="true"
              show-icon
              class="insert-alert"
              :title="insertHintTitle"
              @close="pendingInsertAfterId = null"
            />
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
            <el-button @click="goStep(1)">{{ nextStepLabel }}</el-button>
            <el-button :disabled="selectedIndex <= 0" @click="emit('move', selectedId, -1)">上移</el-button>
            <el-button :disabled="selectedIndex >= chainNodes.length - 1" @click="emit('move', selectedId, 1)">下移</el-button>
            <el-popconfirm title="确定删除此步骤？" @confirm="emit('remove', selectedId)">
              <template #reference>
                <el-button type="danger" plain>删除</el-button>
              </template>
            </el-popconfirm>
          </div>

          <el-alert
            v-if="upstreamSources.length && selectedIndex > 0"
            type="info"
            :closable="false"
            show-icon
            class="upstream-alert"
            title="本步可从前序步骤取数"
          >
            <div class="upstream-alert-body">
              <span>有 {{ upstreamSources.length }} 个上游步骤。可一键按同名绑定，或在参数里选「取数据」。</span>
              <el-button size="small" type="primary" plain @click="emit('auto-bind-upstream')">按同名绑定上游</el-button>
            </div>
          </el-alert>

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
            <el-alert type="success" :closable="false" show-icon title="此接口无需填写组件参数，也可自行添加参数" />
            <div class="empty-actions">
              <el-button @click="showAddCustom = true">+ 添加参数</el-button>
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
                :upstream-sources="upstreamSources"
                :input-fields="inputFields"
                @change="(patch) => patchBinding(field.key, patch)"
                @remove="removeCustomField"
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
                  :upstream-sources="upstreamSources"
                  :input-fields="inputFields"
                  @change="(patch) => patchBinding(field.key, patch)"
                  @remove="removeCustomField"
                />
              </template>
            </div>

            <div class="add-param-block">
              <el-button v-if="!showAddCustom" @click="showAddCustom = true">+ 添加参数</el-button>
              <div v-else class="add-param-form">
                <el-input v-model="customDraft.key" placeholder="参数键，如 deptId" />
                <el-input v-model="customDraft.description" placeholder="说明（选填）" />
                <el-checkbox v-model="customDraft.required">必填</el-checkbox>
                <div class="add-param-actions">
                  <el-button type="primary" @click="confirmAddCustom">添加</el-button>
                  <el-button @click="cancelAddCustom">取消</el-button>
                </div>
              </div>
            </div>
          </div>

          <div v-if="!fields.length && showAddCustom" class="add-param-block">
            <div class="add-param-form">
              <el-input v-model="customDraft.key" placeholder="参数键，如 deptId" />
              <el-input v-model="customDraft.description" placeholder="说明（选填）" />
              <el-checkbox v-model="customDraft.required">必填</el-checkbox>
              <div class="add-param-actions">
                <el-button type="primary" @click="confirmAddCustom">添加</el-button>
                <el-button @click="cancelAddCustom">取消</el-button>
              </div>
            </div>
          </div>
        </template>
      </section>
    </div>

    <footer v-if="chainNodes.length" class="editor-footer">
      <span
        v-if="!allDone"
        class="footer-hint linkish"
        @click="selectFirstPending"
      >还有 {{ chainNodes.length - configuredCount }} 步待配置，点击跳转</span>
      <span v-else-if="!canPublish && publishHint" class="footer-hint">{{ publishHint }}</span>
      <span v-else class="footer-hint ok">全部已配置，可以试跑或发布</span>
      <div class="footer-actions">
        <el-button type="primary" plain @click="focusQuickAdd">+ 添加</el-button>
        <el-tooltip :disabled="canTryRun" :content="tryRunHint || '请先完成配置'" placement="top">
          <span class="head-btn-wrap">
            <el-button type="success" :disabled="!canTryRun" @click="emit('try-run')">试运行</el-button>
          </span>
        </el-tooltip>
        <el-tooltip :disabled="canPublish" :content="publishHint || '请先完成配置'" placement="top">
          <span class="head-btn-wrap">
            <el-button type="warning" :disabled="!canPublish" @click="emit('publish')">发布</el-button>
          </span>
        </el-tooltip>
      </div>
    </footer>
  </main>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import WorkflowParamField from './WorkflowParamField.vue'
import { isFieldConfigured, isValidParamKey, isMetaKey } from '@/utils/workflowBinding'
import { CATEGORY_LABEL } from '@/utils/schema'

const props = defineProps({
  chainNodes: { type: Array, default: () => [] },
  selectedId: { type: String, default: '' },
  nodeName: { type: String, default: '' },
  fields: { type: Array, default: () => [] },
  bindings: { type: Array, default: () => [] },
  upstreamSources: { type: Array, default: () => [] },
  allStepSources: { type: Array, default: () => [] },
  inputFields: { type: Array, default: () => [] },
  outputSchema: { type: Object, default: () => ({ mode: 'last', fields: [] }) },
  workflowStatus: { type: String, default: '' },
  workflowDirty: { type: Boolean, default: false },
  lastStepName: { type: String, default: '' },
  recentPublicOutput: { type: [Object, Array, String, Number, Boolean], default: undefined },
  components: { type: Array, default: () => [] },
  canTryRun: { type: Boolean, default: false },
  canPublish: { type: Boolean, default: false },
  tryRunHint: { type: String, default: '' },
  publishHint: { type: String, default: '' },
  canvasVisible: { type: Boolean, default: false },
})

const emit = defineEmits([
  'select', 'remove', 'move', 'add', 'update-name', 'update-bindings',
  'update-input-fields', 'update-output-schema', 'add-custom-field', 'remove-custom-field',
  'try-run', 'publish', 'toggle-canvas', 'auto-bind-upstream',
])

const router = useRouter()
const leftTab = ref('add')
const pickerKeyword = ref('')
const quickAddRef = ref(null)
const leftScrollRef = ref(null)
const showOptional = ref(false)
const showAddCustom = ref(false)
const pendingInsertAfterId = ref(null)
const customDraft = reactive({ key: '', description: '', required: false })

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
const insertHintTitle = computed(() => {
  if (!pendingInsertAfterId.value) return ''
  const idx = props.chainNodes.findIndex((item) => item.id === pendingInsertAfterId.value)
  const name = props.chainNodes[idx]?.name || '当前步骤'
  return `下一步将插入到「${name}」之后（第 ${(idx >= 0 ? idx : 0) + 2} 位）`
})
const addHint = computed(() => {
  if (pendingInsertAfterId.value) return '选择接口后插入到指定位置'
  if (props.selectedId && props.chainNodes.length) return '选择接口后默认插到当前步骤之后；也可在时间线点 ⊕'
  return '从组件库挑选 HTTP 接口或数据库脚本，开始编排'
})
const nextStepLabel = computed(() => {
  const current = props.chainNodes[selectedIndex.value]
  if (current && !current.configured) return '完成本步后继续'
  const pending = props.chainNodes.find((item, i) => i > selectedIndex.value && !item.configured)
  if (pending) return '下一待配 →'
  if (selectedIndex.value >= props.chainNodes.length - 1) return '添加下一步'
  return '下一步 →'
})
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

function fieldOptionLabel(name) {
  const raw = String(name || '')
  if (raw.startsWith('rows[0].')) return `首行 · ${raw.slice('rows[0].'.length)}`
  if (raw === 'rows') return 'rows（行集）'
  if (raw === 'rowCount') return 'rowCount（行数）'
  return raw
}

const outputShapePreview = computed(() => {
  const mode = props.outputSchema?.mode || 'last'
  if (mode === 'firstRow') {
    return {
      _mode: 'firstRow',
      _from: props.lastStepName
        ? `末步「${props.lastStepName}」→ rows[0]`
        : '末步查询结果 → rows[0]',
      id: '…',
      username: '…',
    }
  }
  if (mode !== 'fields') {
    return {
      _mode: 'last',
      _from: props.lastStepName ? `末步「${props.lastStepName}」完整结果` : '最后一步完整结果',
      rowCount: 1,
      rows: ['…'],
      truncated: false,
    }
  }
  const fields = props.outputSchema?.fields || []
  if (!fields.length) {
    return { _mode: 'fields', _note: '未配置字段；保存后按最后一步返回' }
  }
  const shape = {}
  for (const row of fields) {
    const key = row.key || '?'
    const path = row.fromPath ? String(row.fromPath) : '（完整）'
    shape[key] = `← ${row.fromNode || '?'}${path === '（完整）' ? path : path}`
  }
  return shape
})

const outputShapeText = computed(() => {
  try {
    return JSON.stringify(outputShapePreview.value, null, 2)
  } catch {
    return '{}'
  }
})

const recentOutputText = computed(() => {
  try {
    return JSON.stringify(props.recentPublicOutput, null, 2)
  } catch {
    return String(props.recentPublicOutput)
  }
})

watch(() => props.selectedId, async (id) => {
  showOptional.value = false
  showAddCustom.value = false
  cancelAddCustom()
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
  // 外部传入选了新 key 时，通知父级把入参面板补上
  if (patch.mode === 'runtime' && patch.inputKey) {
    ensureInputKey(patch.inputKey, key)
  }
  emit('update-bindings', next)
}

function ensureInputKey(inputKey, fieldKey) {
  const key = String(inputKey || '').trim()
  if (!key) return
  if (props.inputFields.some((item) => item.key === key)) return
  const field = props.fields.find((item) => item.key === fieldKey)
  emit('update-input-fields', [
    ...props.inputFields,
    {
      key,
      type: field?.type || 'string',
      required: Boolean(field?.required),
      description: field?.description || '',
    },
  ])
}

function patchInputField(index, patch) {
  const next = props.inputFields.map((item, i) => (i === index ? { ...item, ...patch } : item))
  emit('update-input-fields', next)
}

function onInputKeyChange(index, raw) {
  const nextKey = String(raw || '').trim()
  const prev = props.inputFields[index]
  if (!prev) return
  if (!isValidParamKey(nextKey)) {
    ElMessage.warning('参数键需以字母或下划线开头，仅含字母数字下划线')
    return
  }
  if (isMetaKey(nextKey)) {
    ElMessage.warning('不能使用系统保留字段名')
    return
  }
  if (props.inputFields.some((item, i) => i !== index && item.key === nextKey)) {
    ElMessage.warning('入参键已存在')
    return
  }
  const next = props.inputFields.map((item, i) => (i === index ? { ...item, key: nextKey } : item))
  emit('update-input-fields', next, { renameFrom: prev.key, renameTo: nextKey })
}

function addInputField() {
  let n = props.inputFields.length + 1
  let key = `param${n}`
  while (props.inputFields.some((item) => item.key === key)) {
    n += 1
    key = `param${n}`
  }
  emit('update-input-fields', [
    ...props.inputFields,
    { key, type: 'string', required: false, description: '' },
  ])
}

function removeInputField(index) {
  const removed = props.inputFields[index]
  const next = props.inputFields.filter((_, i) => i !== index)
  emit('update-input-fields', next, { removedKey: removed?.key })
}

function onOutputModeChange(mode) {
  emit('update-output-schema', {
    ...props.outputSchema,
    mode,
    fields: props.outputSchema.fields || [],
  })
}

function patchOutputField(index, patch) {
  const fields = [...(props.outputSchema.fields || [])]
  fields[index] = { ...fields[index], ...patch }
  emit('update-output-schema', { ...props.outputSchema, mode: 'fields', fields })
}

function outputSourceValue(row) {
  if (!row?.fromNode) return ''
  const field = String(row.fromPath || '').replace(/^\$\.?/, '')
  if (!field || field === '*') return `${row.fromNode}:*`
  return `${row.fromNode}:${field}`
}

function onOutputSourceChange(index, raw) {
  let value = String(raw || '').trim()
  if (!value) return
  // allow-create: 支持 "nodeId:$.a.b" 或仅路径时挂到末步
  if (!value.includes(':')) {
    const last = props.allStepSources[props.allStepSources.length - 1]
    if (!last) return
    value = `${last.id}:${value}`
  }
  const idx = value.indexOf(':')
  const fromNode = value.slice(0, idx)
  const fromField = value.slice(idx + 1)
  const row = props.outputSchema.fields?.[index] || {}
  const whole = !fromField || fromField === '*' || fromField === '$'
  patchOutputField(index, {
    fromNode,
    fromPath: whole ? '' : (fromField.startsWith('$.') ? fromField : `$.${fromField}`),
    key: row.key || (whole ? `${fromNode}_result` : fromField.replace(/^\$\./, '')),
  })
}

function addOutputField() {
  const last = props.allStepSources[props.allStepSources.length - 1]
  if (!last) return
  const fromField = last.responseFields?.[0] || ''
  const fields = [...(props.outputSchema.fields || []), {
    key: fromField || 'result',
    fromNode: last.id,
    fromPath: fromField ? `$.${fromField}` : '',
    description: '',
  }]
  emit('update-output-schema', { mode: 'fields', fields })
}

function removeOutputField(index) {
  const fields = (props.outputSchema.fields || []).filter((_, i) => i !== index)
  emit('update-output-schema', { ...props.outputSchema, mode: 'fields', fields })
}

function confirmAddCustom() {
  const key = String(customDraft.key || '').trim()
  if (!isValidParamKey(key)) {
    ElMessage.warning('参数键需以字母或下划线开头，仅含字母数字下划线')
    return
  }
  if (isMetaKey(key)) {
    ElMessage.warning('不能使用系统保留字段名')
    return
  }
  if (props.fields.some((item) => item.key === key)) {
    ElMessage.warning('该参数已存在')
    return
  }
  emit('add-custom-field', {
    key,
    description: customDraft.description || key,
    required: Boolean(customDraft.required),
    type: 'string',
  })
  cancelAddCustom()
}

function cancelAddCustom() {
  showAddCustom.value = false
  customDraft.key = ''
  customDraft.description = ''
  customDraft.required = false
}

function removeCustomField(key) {
  emit('remove-custom-field', key)
}

function selectStep(id) {
  if (props.selectedId === id) return
  emit('select', id)
}

function goStep(delta) {
  if (delta > 0) {
    const current = props.chainNodes[selectedIndex.value]
    if (current && !current.configured) {
      ElMessage.warning('请先完成本步必填参数')
      return
    }
    const pending = props.chainNodes.find((item, i) => i > selectedIndex.value && !item.configured)
    if (pending) {
      emit('select', pending.id)
      return
    }
    if (selectedIndex.value >= props.chainNodes.length - 1) {
      beginInsertAfter(props.selectedId, selectedIndex.value)
      return
    }
  }
  const next = props.chainNodes[selectedIndex.value + delta]
  if (next) emit('select', next.id)
}

function beginInsertAfter(afterId, index) {
  pendingInsertAfterId.value = afterId
  leftTab.value = 'add'
  nextTick(() => {
    quickAddRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    quickAddRef.value?.querySelector('input')?.focus()
  })
  const name = props.chainNodes[index]?.name || '当前步骤'
  ElMessage.info(`选择接口后，将插入到「${name}」之后`)
}

function pickComponent(item) {
  pickerKeyword.value = ''
  const afterId = pendingInsertAfterId.value || props.selectedId || null
  emit('add', item, afterId ? { afterId } : { append: !props.chainNodes.length })
  pendingInsertAfterId.value = null
  leftTab.value = 'steps'
}

function selectFirstPending() {
  leftTab.value = 'steps'
  const pending = props.chainNodes.find((item) => !item.configured)
  emit('select', (pending || props.chainNodes[0]).id)
}

function focusQuickAdd() {
  if (props.selectedId) {
    pendingInsertAfterId.value = props.selectedId
  }
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
.head-actions { display: flex; gap: 8px; flex-shrink: 0; align-items: center; }
.head-btn-wrap { display: inline-flex; }
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
  padding: 10px 4px;
  border: none;
  background: transparent;
  font-size: 12px;
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
.timeline-tip {
  margin: 0 0 10px;
  padding: 0 2px;
  font-size: 12px;
  color: var(--qz-text-muted);
  line-height: 1.4;
}
.timeline-line {
  position: absolute;
  left: 22px;
  top: -10px;
  width: 2px;
  height: 10px;
  background: var(--qz-border);
  pointer-events: none;
}
.timeline-line.linked {
  background: var(--el-color-primary-light-5);
}
.timeline-deps {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
}
.timeline-deps.muted {
  font-size: 11px;
  color: var(--qz-text-muted);
}
.dep-chip {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--qz-primary-soft);
  color: var(--el-color-primary);
}
.timeline-insert {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  width: 100%;
  margin: 0 0 6px;
  padding: 4px 8px;
  border: 1px dashed var(--qz-border);
  border-radius: 8px;
  background: transparent;
  color: var(--qz-text-muted);
  font-size: 12px;
  cursor: pointer;
}
.timeline-insert:hover {
  border-color: var(--el-color-primary-light-5);
  color: var(--el-color-primary);
  background: var(--qz-primary-soft);
}
.insert-plus { font-size: 14px; line-height: 1; }
.insert-alert { margin-bottom: 10px; }
.upstream-alert { margin: 0 0 12px; }
.upstream-alert-body {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  font-size: 12px;
}
.footer-hint.linkish {
  cursor: pointer;
  color: var(--el-color-primary);
}
.footer-hint.linkish:hover { text-decoration: underline; }
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
.inputs-panel {
  padding: 12px;
}
.inputs-empty {
  padding: 20px 8px;
  text-align: center;
  font-size: 13px;
  color: var(--qz-text-muted);
}
.inputs-empty-sub {
  margin-top: 6px;
  font-size: 12px;
}
.inputs-empty-sub.warn {
  color: var(--el-color-warning);
}
.output-alert {
  margin-bottom: 12px;
}
.output-preview {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed var(--el-border-color-lighter);
}
.output-preview-title {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
}
.output-preview-title.mt {
  margin-top: 12px;
}
.output-preview-code {
  margin: 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  font-size: 12px;
  line-height: 1.5;
  max-height: 180px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
}
.input-row {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
  margin-bottom: 12px;
  padding: 10px;
  border: 1px solid var(--qz-border);
  border-radius: var(--qz-radius);
  background: var(--qz-card);
}
.input-key,
.input-desc { width: 100%; }
.add-input-btn { width: 100%; margin-top: 4px; }
.output-mode { margin-bottom: 12px; }
.add-param-block { margin-top: 12px; }
.add-param-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border: 1px dashed var(--qz-border);
  border-radius: var(--qz-radius);
  background: var(--qz-fill);
}
.add-param-actions { display: flex; gap: 8px; }
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
