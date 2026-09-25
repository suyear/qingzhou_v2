<template>
  <div class="designer">
    <PageState v-if="bootError" :error="bootError" @retry="retryBootstrap" />
    <template v-else>
    <div class="toolbar">
      <div class="tb-left">
        <div class="crumb">
          <el-button link type="primary" @click="goBack">工作流编排</el-button>
          <span class="crumb-sep">/</span>
          <span class="crumb-name">{{ form.workflowName || '未命名' }}</span>
        </div>
        <el-input v-model="form.workflowName" placeholder="工作流名称" style="width: 200px" />
      </div>
      <div class="tb-mid">
        <div class="step-pills">
          <span class="step-pill" :class="{ active: canvasEmpty, done: !canvasEmpty }">① 添加</span>
          <span class="step-pill" :class="{ active: !canvasEmpty && !allConfigured, done: allConfigured }">② 配置</span>
          <span class="step-pill" :class="{ active: allConfigured && form.status !== 'PUBLISHED', done: form.status === 'PUBLISHED' }">③ 试跑</span>
          <span class="step-pill" :class="{ active: form.status === 'PUBLISHED', done: form.status === 'PUBLISHED' }">④ 发布</span>
        </div>
        <el-button :loading="running" @click="tryRun">试运行</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        <el-button type="primary" plain :loading="publishing" @click="publish">发布</el-button>
        <el-tag v-if="dirty" size="small" type="warning">未保存</el-tag>
        <span v-else-if="lastSavedAt" class="saved-hint">已保存 {{ lastSavedAt }}</span>
      </div>
      <div class="tb-right">
        <el-tag v-if="form.status" size="small" :type="statusType">{{ statusText }}</el-tag>
        <el-button @click="settingsVisible = true">设置</el-button>
      </div>
    </div>
    <el-alert
      v-if="form.status === 'PUBLISHED'"
      class="draft-alert"
      type="info"
      :closable="false"
      :title="`调度和开放调用走已发布快照 v${form.version || 1}；试运行仍用当前草稿。`"
    />
    <div class="body">
      <WorkflowStepEditor
        :chain-nodes="chainNodes"
        :selected-id="selectedId"
        :node-name="selectedData.componentName || ''"
        :fields="selectedFields"
        :bindings="currentBindings"
        :upstream-sources="upstreamSourcesForSelected"
        :all-step-sources="allStepSources"
        :input-fields="inputFields"
        :output-schema="outputSchema"
        :components="componentOptions"
        :can-try-run="!canvasEmpty && allConfigured"
        :can-publish="Boolean(route.params.id) && allConfigured"
        :canvas-visible="canvasVisible"
        @select="selectNode"
        @add="addToChain"
        @remove="removeStep"
        @move="moveStep"
        @update-name="updateNodeName"
        @update-bindings="onBindingsChange"
        @update-input-fields="onInputFieldsUpdate"
        @update-output-schema="onOutputSchemaUpdate"
        @add-custom-field="onAddCustomField"
        @remove-custom-field="onRemoveCustomField"
        @try-run="tryRun"
        @publish="publish"
        @toggle-canvas="toggleCanvas"
      />
      <aside class="canvas-panel" :class="{ open: canvasVisible }">
        <div class="canvas-panel-head">
          <span>流程图预览</span>
          <div class="canvas-head-actions">
            <span class="zoom-label">{{ zoomPercent }}%</span>
            <el-button-group size="small">
              <el-button @click="zoomOut">−</el-button>
              <el-button @click="zoomReset">100%</el-button>
              <el-button @click="zoomIn">+</el-button>
              <el-button @click="zoomFit">适应</el-button>
            </el-button-group>
            <el-button text size="small" @click="canvasVisible = false">收起</el-button>
          </div>
        </div>
        <div ref="canvasWrapRef" class="canvas-wrap">
          <div ref="canvasRef" class="canvas" />
          <div v-if="canvasEmpty" class="canvas-empty">
            <p>添加步骤后可在此查看流程图</p>
          </div>
          <div v-else class="canvas-overlay">
            <span class="canvas-hint">拖空白或双指平移 · 捏合 / Ctrl+滚轮缩放</span>
            <div class="canvas-float-tools">
              <span class="zoom-pill">{{ zoomPercent }}%</span>
              <el-button-group size="small">
                <el-button @click="zoomOut">−</el-button>
                <el-button @click="zoomReset">1:1</el-button>
                <el-button @click="zoomIn">+</el-button>
                <el-button @click="zoomFit">适应</el-button>
              </el-button-group>
            </div>
          </div>
          <TeleportContainer />
        </div>
      </aside>
      <div v-if="!canvasEmpty" class="canvas-bar">
        <el-button size="small" :type="canvasVisible ? 'primary' : 'default'" @click="canvasVisible = !canvasVisible">
          {{ canvasVisible ? '收起流程图' : '查看流程图' }}
        </el-button>
      </div>
    </div>

    <el-dialog v-model="tryRunDialogVisible" title="试运行" width="560px">
      <template v-if="tryRunFields.length">
        <p class="hint-block">填写以下参数后执行试运行（与调度 / 开放调用入参一致）</p>
        <el-form label-position="top" size="default">
          <el-form-item
            v-for="field in tryRunFields"
            :key="field.key"
            :required="field.required"
          >
            <template #label>
              <span>{{ runtimeFieldLabel(field.key) }}</span>
              <span
                v-if="runtimeFieldLabel(field.key) !== field.key"
                class="field-key-hint"
              >{{ field.key }}</span>
            </template>
            <el-input v-model="tryRunForm[field.key]" :placeholder="`请输入 ${runtimeFieldLabel(field.key)}`" />
          </el-form-item>
        </el-form>
      </template>
      <template v-else>
        <p class="hint-block">当前编排没有需要外部传入的参数，将直接执行。</p>
      </template>
      <p class="hint-block output-hint">{{ outputHint }}</p>
      <el-collapse v-if="tryRunFields.length" class="json-advanced">
        <el-collapse-item title="高级：JSON 入参" name="json">
          <el-input v-model="tryRunInput" type="textarea" :rows="6" placeholder="{}" class="json-input" />
        </el-collapse-item>
      </el-collapse>
      <template #footer>
        <el-button @click="tryRunDialogVisible = false">取消</el-button>
        <el-button type="success" :loading="running" @click="confirmTryRun">开始试运行</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="publishSuccessVisible" title="发布成功" width="480px">
      <p class="publish-msg">
        「<strong>{{ form.workflowName }}</strong>」已发布为
        <strong>v{{ form.version }}</strong>，调度和开放 API 将使用此快照。
      </p>
      <p class="hint-block">接下来你可以：</p>
      <div class="publish-actions">
        <el-button type="primary" @click="goSchedule">创建定时调度</el-button>
        <el-button @click="goOpenapi">开放 API 授权</el-button>
      </div>
      <template #footer>
        <el-button @click="publishSuccessVisible = false">继续编排</el-button>
      </template>
    </el-dialog>
    <el-drawer v-model="logVisible" title="试运行结果" size="80%" class="qz-detail-drawer">
      <ExecutionLogView
        v-if="runResult"
        :instance="runResult.instance"
        :logs="runResult.logs"
        :execution-link="executionLink"
        :workflow-name="form.workflowName"
        :workflow-id="route.params.id"
      />
      <DetailEmpty v-else text="暂无试运行结果" />
    </el-drawer>

    <el-drawer v-model="settingsVisible" title="工作流设置" size="400px">
      <el-form label-position="top">
        <el-form-item label="编码">
          <el-tooltip :content="codeLocked ? '编码已对外暴露，发布后不可修改' : '调度和开放调用用这个编码识别工作流'" placement="top">
            <el-input v-model="form.workflowCode" placeholder="例如 order.notify" :disabled="codeLocked" />
          </el-tooltip>
          <p class="settings-hint">一般不用改。开放平台、定时调度靠它找到这条流程。</p>
        </el-form-item>
        <el-form-item label="企业微信凭证">
          <el-select v-model="form.credentialMode" style="width: 100%">
            <el-option label="使用全局凭证" value="GLOBAL" />
            <el-option label="使用独立凭证" value="INDEPENDENT" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.credentialMode === 'INDEPENDENT'" label="选择凭证">
          <el-select v-model="form.credentialId" placeholder="选择企业微信凭证" style="width: 100%" filterable>
            <el-option
              v-for="item in credentials"
              :key="item.id"
              :label="item.credentialName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-divider />
        <div class="hotkeys">
          <div class="settings-section-title">快捷操作</div>
          <div>Delete：删除当前步骤</div>
          <div>流程图：空白拖拽或双指滑动平移</div>
          <div>流程图：捏合，或 Ctrl / ⌘ + 滚轮缩放</div>
        </div>
      </el-form>
    </el-drawer>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageState from '@/components/PageState.vue'
import { askConfirm } from '@/utils/confirm'
import { Graph } from '@antv/x6'
import { Snapline } from '@antv/x6-plugin-snapline'
import { Selection } from '@antv/x6-plugin-selection'
import { Keyboard } from '@antv/x6-plugin-keyboard'
import { getTeleport } from '@antv/x6-vue-shape'
import { pageComponents } from '@/api/component'
import { pageCredentials } from '@/api/credential'
import { createWorkflow, getWorkflow, publishWorkflow, tryRunWorkflow, updateWorkflow } from '@/api/workflow'
import { getExecution } from '@/api/execution'
import { networkErrorMessage } from '@/api/http'
import { fieldsToSchema, schemaFields, schemaToFields, toNodeData, urlPath } from '@/utils/schema'
import {
  applyBindingsToNodeData,
  bindingsToInputFields,
  bindingsToMappings,
  buildFieldMetaMap,
  defaultBinding,
  inferBindingsForNode,
  isNodeConfigured,
  mergeInputFields,
  mergeNodeFields,
  normalizeOutputSchema,
  outputSchemaHint,
  parseBindingValue,
  renameInputKeyInBindings,
  responseFieldOptions,
  stepConfigSummary,
} from '@/utils/workflowBinding'
import WorkflowStepEditor from '@/components/designer/WorkflowStepEditor.vue'
import { NODE_SHAPE, registerComponentNode } from '@/components/designer/registerNodes'
import { formatTime, workflowStatusLabel } from '@/utils/format'
import ExecutionLogView from '@/components/ExecutionLogView.vue'
import DetailEmpty from '@/components/detail/DetailEmpty.vue'

registerComponentNode()
const TeleportContainer = getTeleport()

const route = useRoute()
const router = useRouter()
const canvasRef = ref(null)
const saving = ref(false)
const publishing = ref(false)
const running = ref(false)
const bootError = ref('')
const logVisible = ref(false)
const tryRunDialogVisible = ref(false)
const publishSuccessVisible = ref(false)
const runResult = ref(null)
const components = ref([])
const credentials = ref([])
const paramMappings = ref([])
const inputFields = ref([])
const outputSchema = ref({ mode: 'last', fields: [] })
const nodeBindings = ref({})
const tryRunInput = ref('{}')
const tryRunForm = ref({})
const nodeTick = ref(0)
const selectedId = ref(null)
const selectedData = ref({})
const lastSavedAt = ref('')
const dirty = ref(false)
const canvasVisible = ref(false)
const settingsVisible = ref(false)
const canvasWrapRef = ref(null)
const zoomPercent = ref(100)
const ZOOM_MIN = 0.25
const ZOOM_MAX = 2.5
const ZOOM_STEP = 0.15
const WHEEL_ZOOM_FACTOR = 1.02
let hydrating = false
let ready = false
let canvasResizeObserver = null
let canvasWrapEl = null
const canvasEmpty = computed(() => {
  nodeTick.value
  return !graph || graph.getNodes().length === 0
})
const executionLink = computed(() => {
  const id = route.params.id
  return id ? `/executions?workflowId=${id}` : '/executions'
})
const selectedFields = computed(() => {
  nodeTick.value
  if (!selectedId.value || !graph) return []
  const node = graph.getCellById(selectedId.value)
  if (!node || !node.isNode?.()) return []
  return fieldsForNode(node)
})
const componentOptions = computed(() =>
  components.value.map((item) => ({
    ...item,
    urlPath: urlPath(item.urlTemplate, item),
  })),
)
const chainNodes = computed(() => {
  nodeTick.value
  if (!graph) return []
  const ordered = getOrderedNodes()
  const nodeNames = Object.fromEntries(
    ordered.map((node) => [node.id, node.getData()?.componentName || node.id]),
  )
  const nodeIndexes = Object.fromEntries(
    ordered.map((node, index) => [node.id, index + 1]),
  )
  const inputLabels = Object.fromEntries(
    inputFields.value.map((item) => [item.key, item.description || item.key]),
  )
  return ordered.map((node) => {
    const data = node.getData() || {}
    const fields = fieldsForNode(node)
    const bindings = nodeBindings.value[node.id] || []
    const requiredCount = fields.filter((item) => item.required).length
    return {
      id: node.id,
      name: data.componentName || node.id,
      method: data.httpMethod || 'GET',
      path: data.urlPath || urlPath(data.urlTemplate, data),
      configured: isNodeConfigured(bindings, fields),
      fieldCount: requiredCount,
      summaryLines: stepConfigSummary(bindings, fields, { nodeNames, nodeIndexes, inputLabels }),
    }
  })
})
const currentBindings = computed(() => {
  if (!selectedId.value) return []
  return nodeBindings.value[selectedId.value] || []
})
const allStepSources = computed(() => {
  nodeTick.value
  if (!graph) return []
  return getOrderedNodes().map((node) => buildStepSource(node))
})
const upstreamSourcesForSelected = computed(() => {
  nodeTick.value
  if (!graph || !selectedId.value) return []
  return getUpstreamNodes(selectedId.value).map((node) => buildStepSource(node))
})
const tryRunFields = computed(() => inputFields.value)
const outputHint = computed(() => outputSchemaHint(outputSchema.value))
const allConfigured = computed(() => chainNodes.value.length > 0 && chainNodes.value.every((item) => item.configured))
const form = reactive({
  workflowName: '未命名工作流',
  workflowCode: `wf_${Date.now()}`,
  status: 'DRAFT',
  version: 1,
  credentialMode: 'GLOBAL',
  credentialId: null,
})
const codeLocked = computed(() => form.status === 'PUBLISHED')
const statusType = computed(() => {
  if (form.status === 'PUBLISHED') return 'success'
  if (form.status === 'DISABLED') return 'info'
  return 'warning'
})
const statusText = computed(() => {
  const label = workflowStatusLabel(form.status)
  if (form.status === 'PUBLISHED') {
    return `${label} v${form.version || 1}`
  }
  return label
})

function markDirty() {
  if (hydrating || !ready) {
    return
  }
  dirty.value = true
}

function markClean() {
  dirty.value = false
}

function pauseDirty() {
  ready = false
  hydrating = true
}

async function resumeClean() {
  hydrating = false
  await nextTick()
  markClean()
  ready = true
}

let graph = null

function getOrderedNodes() {
  if (!graph) return []
  return [...graph.getNodes()].sort((a, b) => a.position().x - b.position().x)
}

function relayoutChain() {
  if (!graph) return
  const nodes = getOrderedNodes()
  graph.getEdges().forEach((edge) => graph.removeEdge(edge))
  nodes.forEach((node, index) => {
    node.position(80 + index * 320, 100)
  })
  for (let i = 0; i < nodes.length - 1; i += 1) {
    graph.addEdge({
      source: { cell: nodes[i].id, port: 'out' },
      target: { cell: nodes[i + 1].id, port: 'in' },
      attrs: {
        line: { stroke: '#64748b', strokeWidth: 1.6, targetMarker: { name: 'block', width: 8, height: 8 } },
      },
    })
  }
  nodeTick.value += 1
  if (canvasVisible.value) {
    nextTick(() => refreshCanvasView(true))
  }
}

function syncZoomPercent() {
  zoomPercent.value = Math.round((graph?.zoom() || 1) * 100)
}

function resizeGraphCanvas() {
  if (!graph || !canvasWrapRef.value) return
  const { clientWidth, clientHeight } = canvasWrapRef.value
  if (clientWidth > 0 && clientHeight > 0) {
    graph.resize(clientWidth, clientHeight)
  }
}

function refreshCanvasView(fit = false) {
  if (!graph) return
  resizeGraphCanvas()
  syncZoomPercent()
  if (fit) zoomFit()
}

function createGraph() {
  const wrap = canvasWrapRef.value
  const width = Math.max(wrap?.clientWidth || 0, 420)
  const height = Math.max(wrap?.clientHeight || 0, 360)
  graph = new Graph({
    container: canvasRef.value,
    width,
    height,
    autoResize: true,
    grid: { size: 12, visible: true },
    panning: {
      enabled: true,
      eventTypes: ['leftMouseDown', 'mouseWheel'],
    },
    mousewheel: {
      enabled: true,
      global: false,
      modifiers: ['ctrl', 'meta'],
      factor: WHEEL_ZOOM_FACTOR,
      zoomAtMousePosition: true,
      minScale: ZOOM_MIN,
      maxScale: ZOOM_MAX,
      guard(evt) {
        if (evt.ctrlKey || evt.metaKey) {
          evt.preventDefault()
        }
        return true
      },
    },
    interacting: {
      nodeMovable: false,
      edgeMovable: false,
      magnetConnectable: false,
    },
    scaling: { min: ZOOM_MIN, max: ZOOM_MAX },
    background: { color: '#eef2f6' },
    connecting: {
      allowBlank: false,
      allowLoop: false,
      allowNode: false,
      allowEdge: false,
      allowMulti: 'withPort',
      highlight: true,
      snap: { radius: 28 },
      router: { name: 'manhattan' },
      connector: { name: 'rounded' },
      createEdge() {
        return graph.createEdge({
          attrs: {
            line: { stroke: '#64748b', strokeWidth: 1.6, targetMarker: { name: 'block', width: 8, height: 8 } },
          },
        })
      },
      validateMagnet({ magnet }) {
        return magnet.getAttribute('port-group') === 'out'
      },
      validateConnection({ sourceMagnet, targetMagnet }) {
        return sourceMagnet?.getAttribute('port-group') === 'out'
          && targetMagnet?.getAttribute('port-group') === 'in'
      },
    },
  })
  graph.use(new Snapline({ enabled: true }))
  graph.use(new Selection({ enabled: true, rubberband: false, showNodeSelectionBox: false }))
  graph.on('scale', syncZoomPercent)
  graph.use(new Keyboard({ enabled: true }))
  graph.bindKey(['backspace', 'delete'], () => {
    if (!selectedId.value) return false
    const id = selectedId.value
    askConfirm('删除当前步骤？未保存的参数配置会一起丢掉。', '删除步骤').then((ok) => {
      if (ok) removeStep(id)
    })
    return false
  })
  graph.on('node:added', ({ node }) => {
    nodeTick.value += 1
    if (node) ensureBindings(node)
    markDirty()
  })
  graph.on('node:removed', ({ node }) => {
    nodeTick.value += 1
    if (node?.id) {
      const next = { ...nodeBindings.value }
      delete next[node.id]
      nodeBindings.value = next
    }
    markDirty()
    if (selectedId.value && !graph.getCellById(selectedId.value)) {
      clearSelection()
    }
  })
  graph.on('edge:connected', ({ edge }) => {
    const targetId = edge?.getTargetCellId?.()
    const target = targetId ? graph.getCellById(targetId) : null
    if (target) buildBindingsForNode(target, true)
    markDirty()
  })
  graph.on('edge:removed', markDirty)
  graph.on('selection:changed', ({ selected }) => {
    const node = selected.find((cell) => typeof cell.isNode === 'function' && cell.isNode())
    if (!node) {
      clearSelection()
      return
    }
    selectNode(node.id)
  })
  bindCanvasResize()
}

function bindCanvasResize() {
  canvasResizeObserver?.disconnect()
  canvasResizeObserver = null
  if (canvasWrapEl) {
    canvasWrapEl.removeEventListener('wheel', onCanvasWheel)
    canvasWrapEl = null
  }
  if (!canvasWrapRef.value) return
  canvasWrapEl = canvasWrapRef.value
  canvasWrapEl.addEventListener('wheel', onCanvasWheel, { passive: false })
  if (typeof ResizeObserver === 'undefined') return
  canvasResizeObserver = new ResizeObserver(() => {
    if (!graph || !canvasVisible.value) return
    refreshCanvasView(false)
  })
  canvasResizeObserver.observe(canvasWrapEl)
}

function onCanvasWheel(event) {
  if (event.ctrlKey || event.metaKey) {
    event.preventDefault()
  }
}

function toggleCanvas() {
  canvasVisible.value = !canvasVisible.value
}

function findComponent(data) {
  if (!data) return null
  return components.value.find(
    (item) => item.id === data.componentId || item.componentCode === data.componentCode,
  )
}

function fieldsForNode(node) {
  const data = node.getData() || {}
  return mergeNodeFields(schemaFields(findComponent(data)), data)
}

function buildStepSource(node) {
  const data = node.getData() || {}
  const requestFields = fieldsForNode(node).map((item) => item.key)
  for (const binding of nodeBindings.value[node.id] || []) {
    if (binding?.key && !requestFields.includes(binding.key)) {
      requestFields.push(binding.key)
    }
  }
  return {
    id: node.id,
    name: data.componentName || node.id,
    requestFields,
    responseFields: responseFieldOptions(findComponent(data)),
  }
}

function getUpstreamNodes(nodeId) {
  if (!graph) return []
  const ordered = getOrderedNodes()
  const index = ordered.findIndex((item) => item.id === nodeId)
  if (index <= 0) return []
  return ordered.slice(0, index)
}

function buildBindingsForNode(node, force = false) {
  const fields = fieldsForNode(node)
  if (!force && nodeBindings.value[node.id]) return
  const upstream = getUpstreamNodes(node.id).map((item) => {
    const source = buildStepSource(item)
    return {
      id: item.id,
      fields: [...new Set([...(source.responseFields || []), ...(source.requestFields || [])])],
    }
  })
  nodeBindings.value = {
    ...nodeBindings.value,
    [node.id]: fields.map((field) => defaultBinding(field, upstream)),
  }
}

function ensureBindings(node) {
  buildBindingsForNode(node, false)
}

function initBindingsFromWorkflow(mappings, inputSchema) {
  const inputKeys = schemaToFields(inputSchema).map((item) => item.key)
  const next = {}
  if (!graph) return
  for (const node of graph.getNodes()) {
    const fields = fieldsForNode(node)
    next[node.id] = inferBindingsForNode(
      node.id,
      fields,
      node.getData(),
      mappings,
      inputKeys,
    )
  }
  nodeBindings.value = next
}

function selectNode(nodeId) {
  const node = graph?.getCellById(nodeId)
  if (!node) return
  ensureBindings(node)
  selectedId.value = nodeId
  selectedData.value = { ...(node.getData() || {}) }
  if (!canvasVisible.value) {
    graph?.cleanSelection()
    return
  }
  graph.select(node)
  nextTick(() => graph.centerCell(node))
}

function addToChain(item) {
  if (!graph) return
  const nodes = getOrderedNodes()
  const last = nodes[nodes.length - 1]
  const node = graph.addNode({
    shape: NODE_SHAPE,
    x: 80 + nodes.length * 320,
    y: 100,
    data: toNodeData(item),
  })
  if (last) {
    graph.addEdge({
      source: { cell: last.id, port: 'out' },
      target: { cell: node.id, port: 'in' },
      attrs: {
        line: { stroke: '#64748b', strokeWidth: 1.6, targetMarker: { name: 'block', width: 8, height: 8 } },
      },
    })
  }
  nodeTick.value += 1
  buildBindingsForNode(node, true)
  refreshInputFieldsFromBindings()
  selectNode(node.id)
  markDirty()
  if (canvasVisible.value) {
    nextTick(() => refreshCanvasView(true))
  }
}

async function removeStep(nodeId) {
  const node = graph?.getCellById(nodeId)
  if (!node) return
  graph.removeNode(nodeId)
  relayoutChain()
  if (selectedId.value === nodeId) {
    const next = getOrderedNodes()[0]
    if (next) selectNode(next.id)
    else clearSelection()
  }
  markDirty()
}

function moveStep(nodeId, direction) {
  const nodes = getOrderedNodes()
  const index = nodes.findIndex((item) => item.id === nodeId)
  const target = index + direction
  if (index < 0 || target < 0 || target >= nodes.length) return
  const swapped = [...nodes]
  ;[swapped[index], swapped[target]] = [swapped[target], swapped[index]]
  swapped.forEach((item, idx) => {
    item.position(80 + idx * 320, 100)
  })
  relayoutChain()
  buildBindingsForNode(swapped[target], true)
  buildBindingsForNode(swapped[index], true)
  selectNode(nodeId)
  markDirty()
}

function onBindingsChange(bindings) {
  if (!selectedId.value) return
  nodeBindings.value = {
    ...nodeBindings.value,
    [selectedId.value]: bindings,
  }
  applyBindingsToSelectedNode()
  // 外部传入新键并入入参面板
  const metaMap = buildFieldMetaMap(nodeBindings.value, {
    [selectedId.value]: fieldsForNode(graph.getCellById(selectedId.value)),
  })
  inputFields.value = mergeInputFields(inputFields.value, bindingsToInputFields(nodeBindings.value, metaMap))
  markDirty()
  nodeTick.value += 1
}

function onInputFieldsUpdate(next, meta = {}) {
  inputFields.value = next || []
  if (meta.renameFrom && meta.renameTo) {
    nodeBindings.value = renameInputKeyInBindings(nodeBindings.value, meta.renameFrom, meta.renameTo)
  }
  if (meta.removedKey) {
    const removedKey = meta.removedKey
    const patched = {}
    for (const [nodeId, list] of Object.entries(nodeBindings.value)) {
      patched[nodeId] = (list || []).map((item) => {
        if (item.mode === 'runtime' && (item.inputKey || item.key) === removedKey) {
          return { ...item, mode: 'fixed', value: '', inputKey: '' }
        }
        return item
      })
    }
    nodeBindings.value = patched
  }
  markDirty()
  nodeTick.value += 1
}

function onOutputSchemaUpdate(next) {
  outputSchema.value = normalizeOutputSchema(next)
  markDirty()
}

function onAddCustomField(field) {
  const node = graph?.getCellById(selectedId.value)
  if (!node || !field?.key) return
  const data = { ...(node.getData() || {}) }
  const custom = Array.isArray(data.customFields) ? [...data.customFields] : []
  if (custom.some((item) => item.key === field.key)) return
  custom.push({
    key: field.key,
    description: field.description || field.key,
    required: Boolean(field.required),
    type: field.type || 'string',
  })
  data.customFields = custom
  node.setData(data)
  selectedData.value = data
  const upstream = getUpstreamNodes(node.id).map((item) => {
    const source = buildStepSource(item)
    return {
      id: item.id,
      fields: [...new Set([...(source.responseFields || []), ...(source.requestFields || [])])],
    }
  })
  const bindings = [...(nodeBindings.value[selectedId.value] || [])]
  bindings.push(defaultBinding(field, upstream))
  nodeBindings.value = {
    ...nodeBindings.value,
    [selectedId.value]: bindings,
  }
  if (field.required || bindings[bindings.length - 1]?.mode === 'runtime') {
    inputFields.value = mergeInputFields(inputFields.value, [{
      key: field.key,
      type: field.type || 'string',
      required: Boolean(field.required),
      description: field.description || '',
    }])
  }
  markDirty()
  nodeTick.value += 1
}

function onRemoveCustomField(key) {
  const node = graph?.getCellById(selectedId.value)
  if (!node || !key) return
  const data = { ...(node.getData() || {}) }
  data.customFields = (Array.isArray(data.customFields) ? data.customFields : [])
    .filter((item) => item.key !== key)
  delete data[key]
  node.setData(data)
  selectedData.value = data
  nodeBindings.value = {
    ...nodeBindings.value,
    [selectedId.value]: (nodeBindings.value[selectedId.value] || []).filter((item) => item.key !== key),
  }
  markDirty()
  nodeTick.value += 1
}

function applyBindingsToSelectedNode() {
  const node = graph?.getCellById(selectedId.value)
  if (!node) return
  const fields = fieldsForNode(node)
  const bindings = nodeBindings.value[selectedId.value] || []
  const data = applyBindingsToNodeData(node.getData(), bindings)
  for (const field of fields) {
    const binding = bindings.find((item) => item.key === field.key)
    if (binding?.mode === 'fixed') {
      const value = parseBindingValue(binding.value, field)
      if (value !== undefined) data[field.key] = value
    }
  }
  node.setData(data)
  selectedData.value = data
}

function syncBindingsToPayload() {
  if (!graph) return
  const fieldsByNode = {}
  for (const node of graph.getNodes()) {
    ensureBindings(node)
    fieldsByNode[node.id] = fieldsForNode(node)
    const bindings = nodeBindings.value[node.id] || []
    const data = applyBindingsToNodeData(node.getData(), bindings)
    for (const field of fieldsByNode[node.id]) {
      const binding = bindings.find((item) => item.key === field.key)
      if (binding?.mode === 'fixed') {
        const value = parseBindingValue(binding.value, field)
        if (value !== undefined) data[field.key] = value
      }
    }
    node.setData(data)
  }
  const metaMap = buildFieldMetaMap(nodeBindings.value, fieldsByNode)
  paramMappings.value = bindingsToMappings(nodeBindings.value)
  inputFields.value = mergeInputFields(inputFields.value, bindingsToInputFields(nodeBindings.value, metaMap))
}

function refreshInputFieldsFromBindings() {
  if (!graph) return
  const fieldsByNode = {}
  for (const node of graph.getNodes()) {
    fieldsByNode[node.id] = fieldsForNode(node)
  }
  const metaMap = buildFieldMetaMap(nodeBindings.value, fieldsByNode)
  inputFields.value = mergeInputFields(inputFields.value, bindingsToInputFields(nodeBindings.value, metaMap))
}

function toBackendGraph() {
  const nodes = graph.getNodes().map((node) => {
    const pos = node.position()
    const data = node.getData() || {}
    return {
      id: node.id,
      componentId: data.componentId,
      componentCode: data.componentCode,
      name: data.componentName,
      x: pos.x,
      y: pos.y,
      data,
    }
  })
  const edges = graph.getEdges().map((edge) => ({
    id: edge.id,
    source: { cell: edge.getSourceCellId(), port: edge.getSourcePortId() || 'out' },
    target: { cell: edge.getTargetCellId(), port: edge.getTargetPortId() || 'in' },
  }))
  return { nodes, edges }
}

function placeNode(node, index, seen) {
  const hasX = Number.isFinite(Number(node.x))
  const hasY = Number.isFinite(Number(node.y))
  let x = hasX ? Number(node.x) : 80 + index * 280
  let y = hasY ? Number(node.y) : 80
  const key = `${Math.round(x)}_${Math.round(y)}`
  const clash = seen.get(key) || 0
  if (clash > 0) {
    x += clash * 280
  }
  seen.set(key, clash + 1)
  return { x, y }
}

function resolveNodeData(node) {
  const lib = components.value.find(
    (item) => item.id === node.componentId || item.componentCode === node.componentCode,
  )
  const fromLib = lib ? toNodeData(lib) : {}
  return {
    ...fromLib,
    ...(node.data || {}),
    componentId: node.componentId || fromLib.componentId,
    componentCode: node.componentCode || fromLib.componentCode,
    componentName: node.data?.componentName || node.name || fromLib.componentName,
  }
}

function restoreGraph(graphData) {
  const nodes = graphData?.nodes || []
  const edges = graphData?.edges || []
  const seen = new Map()
  nodes.forEach((node, index) => {
    const placed = placeNode(node, index, seen)
    graph.addNode({
      id: node.id,
      shape: NODE_SHAPE,
      x: placed.x,
      y: placed.y,
      data: resolveNodeData(node),
    })
  })
  for (const edge of edges) {
    const source = typeof edge.source === 'string'
      ? { cell: edge.source, port: 'out' }
      : { cell: edge.source?.cell || edge.source, port: edge.source?.port || 'out' }
    const target = typeof edge.target === 'string'
      ? { cell: edge.target, port: 'in' }
      : { cell: edge.target?.cell || edge.target, port: edge.target?.port || 'in' }
    graph.addEdge({
      id: edge.id,
      source,
      target,
      attrs: {
        line: { stroke: '#64748b', strokeWidth: 1.6, targetMarker: { name: 'block', width: 8, height: 8 } },
      },
    })
  }
}

async function save() {
  if (!form.workflowName || !form.workflowCode) {
    ElMessage.warning('请填写名称和编码')
    return
  }
  if (form.credentialMode === 'INDEPENDENT' && !form.credentialId) {
    ElMessage.warning('独立凭证模式请选择凭证')
    return
  }
  syncBindingsToPayload()
  saving.value = true
  try {
    const payload = {
      workflowCode: form.workflowCode,
      workflowName: form.workflowName,
      graph: toBackendGraph(),
      paramMapping: paramMappings.value.filter((item) => item.fromNode && item.toNode && item.fromPath && item.toPath),
      inputSchema: fieldsToSchema(inputFields.value),
      outputSchema: normalizeOutputSchema(outputSchema.value),
      credentialMode: form.credentialMode,
      credentialId: form.credentialMode === 'INDEPENDENT' ? form.credentialId : null,
    }
    const id = route.params.id
    const res = id
      ? await updateWorkflow(id, payload)
      : await createWorkflow(payload)
    ElMessage.success('保存成功')
    lastSavedAt.value = formatTime(new Date())
    pauseDirty()
    applyMeta(res.data)
    await resumeClean()
    if (!id && res.data?.id) {
      await router.replace(`/designer/${res.data.id}`)
    }
  } finally {
    saving.value = false
  }
}

async function publish() {
  if (!route.params.id) {
    ElMessage.warning('请先保存工作流')
    return
  }
  if (!(await askConfirm('发布后，调度和开放调用将使用新快照。试运行仍走当前草稿。', '发布确认'))) {
    return
  }
  publishing.value = true
  try {
    await save()
    const res = await publishWorkflow(route.params.id)
    pauseDirty()
    applyMeta(res.data)
    await resumeClean()
    ElMessage.success(`已发布 v${res.data?.version || ''}`)
    publishSuccessVisible.value = true
  } finally {
    publishing.value = false
  }
}

function goSchedule() {
  publishSuccessVisible.value = false
  router.push({ path: '/schedules', query: { workflowId: String(route.params.id) } })
}

function goOpenapi() {
  publishSuccessVisible.value = false
  router.push({ path: '/openapi', query: { tab: 'apps' } })
}

function zoomIn() {
  if (!graph) return
  const next = Math.min(ZOOM_MAX, graph.zoom() + ZOOM_STEP)
  graph.zoomTo(next)
  syncZoomPercent()
}

function zoomOut() {
  if (!graph) return
  const next = Math.max(ZOOM_MIN, graph.zoom() - ZOOM_STEP)
  graph.zoomTo(next)
  syncZoomPercent()
}

function zoomReset() {
  graph?.zoomTo(1)
  syncZoomPercent()
}

function zoomFit() {
  graph?.zoomToFit({ padding: 32, minScale: ZOOM_MIN, maxScale: ZOOM_MAX })
  syncZoomPercent()
}

function applyMeta(wf) {
  if (!wf) return
  form.workflowName = wf.workflowName || form.workflowName
  form.workflowCode = wf.workflowCode || form.workflowCode
  form.status = wf.status || form.status
  form.version = wf.version || form.version
  form.credentialMode = wf.credentialMode || 'GLOBAL'
  form.credentialId = wf.credentialId || null
}

async function tryRun() {
  if (!graph?.getNodes().length) {
    ElMessage.warning('请先添加至少一个步骤')
    return
  }
  if (!allConfigured.value) {
    const pending = chainNodes.value.find((item) => !item.configured)
    if (pending) selectNode(pending.id)
    ElMessage.warning('请先完成各步骤的必填参数')
    return
  }
  syncBindingsToPayload()
  if (!route.params.id || dirty.value) {
    await save()
    if (!route.params.id || dirty.value) {
      return
    }
  }
  const fields = tryRunFields.value
  if (!fields.length) {
    await confirmTryRun()
    return
  }
  tryRunForm.value = Object.fromEntries(fields.map((item) => [item.key, '']))
  tryRunInput.value = '{}'
  tryRunDialogVisible.value = true
}

function runtimeFieldLabel(key) {
  const panel = inputFields.value.find((item) => item.key === key)
  if (panel?.description) return panel.description
  if (!graph) return key
  for (const node of graph.getNodes()) {
    const field = fieldsForNode(node).find((item) => item.key === key)
    if (field?.description) return field.description
  }
  return key
}

async function confirmTryRun() {
  if (!route.params.id) {
    return
  }
  running.value = true
  try {
    let input = {}
    if (tryRunFields.value.length) {
      let parsed = null
      if (tryRunInput.value.trim()) {
        try {
          parsed = JSON.parse(tryRunInput.value)
        } catch {
          ElMessage.warning('试运行入参不是合法 JSON')
          return
        }
      }
      input = parsed && typeof parsed === 'object' && !Array.isArray(parsed)
        ? parsed
        : { ...tryRunForm.value }
      for (const field of tryRunFields.value) {
        if (!field.required) continue
        if (!String(input[field.key] ?? '').trim()) {
          ElMessage.warning(`请填写 ${runtimeFieldLabel(field.key)}`)
          return
        }
      }
    }
    const res = await tryRunWorkflow(route.params.id, { input })
    tryRunDialogVisible.value = false
    const slim = res.data || {}
    if (slim.executionId) {
      try {
        const detail = await getExecution(slim.executionId)
        runResult.value = detail.data
      } catch {
        runResult.value = null
      }
    } else {
      runResult.value = null
    }
    logVisible.value = true
    if (slim.status === 'SUCCESS') {
      ElMessage.success(`试运行成功${slim.durationMs != null ? ` · ${slim.durationMs}ms` : ''}`)
    } else {
      ElMessage.warning(slim.errorMsg || '试运行结束（存在失败节点）')
    }
  } finally {
    running.value = false
  }
}

async function bootstrap() {
  bootError.value = ''
  ready = false
  hydrating = true
  const [list, creds] = await Promise.all([
    pageComponents({ current: 1, size: 100 }),
    pageCredentials({ current: 1, size: 100 }),
  ])
  components.value = list.data?.records || []
  credentials.value = (creds.data?.records || []).filter((item) => item.status === 1 && item.credentialType === 'WECOM')
  await nextTick()
  createGraph()
  resizeGraphCanvas()
  syncZoomPercent()
  nodeTick.value += 1
  if (route.params.id) {
    const detail = await getWorkflow(route.params.id)
    const wf = detail.data || {}
    applyMeta(wf)
    restoreGraph(wf.graph)
    paramMappings.value = (wf.paramMapping || []).map((item) => ({ ...item }))
    inputFields.value = schemaToFields(wf.inputSchema)
    outputSchema.value = normalizeOutputSchema(wf.outputSchema)
    initBindingsFromWorkflow(wf.paramMapping, wf.inputSchema)
    refreshInputFieldsFromBindings()
    nodeTick.value += 1
    const first = chainNodes.value[0]
    if (first) selectNode(first.id)
  }
  hydrating = false
  await nextTick()
  markClean()
  ready = true
}

async function retryBootstrap() {
  try {
    await bootstrap()
  } catch (e) {
    bootError.value = networkErrorMessage(e) || '设计器加载失败，请确认后端服务已启动'
  }
}

async function confirmLeave() {
  if (!dirty.value) {
    return true
  }
  return askConfirm('有未保存的修改，离开将丢失。', '未保存', {
    confirmButtonText: '离开',
    cancelButtonText: '留下',
  })
}

async function goBack() {
  if (await confirmLeave()) {
    dirty.value = false
    router.push('/workflows')
  }
}

function onBeforeUnload(event) {
  if (!dirty.value) {
    return
  }
  event.preventDefault()
  event.returnValue = ''
}

onBeforeRouteLeave(async () => {
  if (!dirty.value) {
    return true
  }
  const ok = await confirmLeave()
  if (ok) {
    dirty.value = false
  }
  return ok
})

function clearSelection() {
  selectedId.value = null
  selectedData.value = {}
}

function updateNodeName(name) {
  const node = graph?.getCellById(selectedId.value)
  if (!node) {
    return
  }
  const data = { ...node.getData(), componentName: name }
  node.setData(data)
  selectedData.value = data
  nodeTick.value += 1
  markDirty()
}

watch(tryRunForm, (val) => {
  tryRunInput.value = JSON.stringify(val || {}, null, 2)
}, { deep: true })
watch(() => [form.workflowName, form.workflowCode, form.credentialMode, form.credentialId], markDirty)
watch(nodeBindings, markDirty, { deep: true })
watch(
  () => chainNodes.value.map((item) => item.id).join(','),
  () => {
    const nodes = chainNodes.value
    if (!nodes.length) {
      clearSelection()
      return
    }
    if (!nodes.find((item) => item.id === selectedId.value)) {
      const pending = nodes.find((item) => !item.configured)
      selectNode((pending || nodes[nodes.length - 1]).id)
    }
  },
  { flush: 'post' },
)
watch(canvasVisible, async (open) => {
  if (!graph || !open) return
  await nextTick()
  bindCanvasResize()
  // 等待侧栏展开动画结束后再计算尺寸，否则拖拽/缩放命中区域是收起时的 0 宽
  window.setTimeout(() => refreshCanvasView(true), 240)
})

function onWindowResize() {
  if (canvasVisible.value) {
    refreshCanvasView(false)
  }
}

onMounted(() => {
  window.addEventListener('beforeunload', onBeforeUnload)
  window.addEventListener('resize', onWindowResize)
  retryBootstrap()
})
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', onBeforeUnload)
  window.removeEventListener('resize', onWindowResize)
  canvasResizeObserver?.disconnect()
  canvasResizeObserver = null
  if (canvasWrapEl) {
    canvasWrapEl.removeEventListener('wheel', onCanvasWheel)
    canvasWrapEl = null
  }
  graph?.dispose()
  graph = null
})
</script>

<style scoped>
.designer {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--qz-card);
}
.toolbar {
  height: 56px;
  border-bottom: 1px solid var(--qz-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 16px;
}
.tb-left,
.tb-mid,
.tb-right {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.tb-mid {
  flex: 1;
  justify-content: center;
}
.saved-hint {
  color: #16a34a;
  font-size: 12px;
  white-space: nowrap;
}
.draft-alert {
  border-radius: 0;
}
.hotkeys {
  font-size: 12px;
  line-height: 1.8;
  color: var(--qz-text-muted);
}
.settings-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--qz-text-muted);
  line-height: 1.45;
}
.settings-section-title {
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--qz-text);
}
.hint {
  color: #94a3b8;
  font-size: 12px;
}
.body {
  flex: 1;
  display: flex;
  min-height: 0;
  position: relative;
}
.step-pills {
  display: flex;
  gap: 6px;
  margin-right: 8px;
}
.step-pill {
  font-size: 11px;
  color: var(--qz-text-muted);
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--qz-fill);
  white-space: nowrap;
}
.step-pill.active {
  color: var(--el-color-primary);
  background: var(--qz-primary-soft);
  font-weight: 600;
}
.step-pill.done:not(.active) {
  color: var(--el-color-success);
}
.canvas-panel {
  width: 0;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-left: none;
  background: var(--qz-fill);
  transition: width 0.2s ease;
}
.canvas-panel.open {
  width: min(640px, 50vw);
  border-left: 1px solid var(--qz-border);
}
.canvas-panel-head {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--qz-border);
  font-size: 13px;
  font-weight: 600;
}
.canvas-head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.zoom-label {
  font-size: 12px;
  color: var(--qz-text-muted);
  min-width: 40px;
}
.canvas-wrap {
  flex: 1;
  position: relative;
  min-height: 360px;
  overflow: hidden;
  cursor: grab;
}
.canvas-wrap:active {
  cursor: grabbing;
}
.canvas {
  width: 100%;
  height: 100%;
  min-height: 360px;
}
.canvas-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
  color: var(--qz-text-muted);
  font-size: 13px;
}
.canvas-overlay {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 2;
}
.canvas-hint {
  position: absolute;
  top: 8px;
  left: 50%;
  transform: translateX(-50%);
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid var(--qz-border);
  font-size: 11px;
  color: var(--qz-text-muted);
  white-space: nowrap;
  box-shadow: var(--qz-shadow);
}
.canvas-float-tools {
  position: absolute;
  right: 10px;
  bottom: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  pointer-events: auto;
  padding: 6px 8px;
  border-radius: var(--qz-radius);
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid var(--qz-border);
  box-shadow: var(--qz-shadow);
}
.zoom-pill {
  font-size: 12px;
  color: var(--qz-text-muted);
  min-width: 36px;
  text-align: center;
}
.canvas-bar {
  position: absolute;
  right: 16px;
  bottom: 72px;
  z-index: 3;
}
.crumb {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-right: 4px;
  white-space: nowrap;
}
.crumb-sep { color: var(--qz-text-muted); }
.crumb-name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  color: var(--qz-text-muted);
}
.publish-msg { margin: 0 0 12px; line-height: 1.6; }
.publish-actions { display: flex; flex-direction: column; gap: 8px; }
.json-advanced { margin-top: 12px; }
.json-input :deep(textarea) {
  font-family: var(--qz-code-font);
  font-size: 12px;
  line-height: 1.65;
}
.run-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.run-head p {
  margin: 0;
}
.hint-block {
  color: #94a3b8;
  font-size: 12px;
  margin: 0 0 10px;
}
.output-hint {
  margin-top: 8px;
  color: var(--el-color-primary);
}
.field-key-hint {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  font-weight: 400;
  color: #94a3b8;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
.mt16 { margin-top: 16px; }
.log-title { font-weight: 600; }
.log-sub { color: #64748b; font-size: 12px; word-break: break-all; }
.err { color: #dc2626; font-size: 12px; }
</style>
