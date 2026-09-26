<template>
  <div v-if="instance" class="exec-log">
    <DetailSection v-if="!compact">
      <div class="exec-head">
        <div class="exec-status">
          <StatusTag kind="exec" :value="instance.status" />
          <span class="exec-no">
            单号
            <el-button type="primary" link @click="copy(instance.executionNo, '已复制单号')">
              {{ instance.executionNo }}
            </el-button>
          </span>
        </div>
        <div class="exec-head-actions">
          <el-button
            v-if="executionLink"
            type="primary"
            link
            @click="$router.push(executionLink)"
          >
            去运行结果
          </el-button>
          <slot name="actions" />
        </div>
      </div>

      <DetailMetaList :items="metaItems" :columns="2">
        <template #workflow="{ item }">
          <el-button
            v-if="workflowId"
            type="primary"
            link
            @click="$router.push(`/designer/${workflowId}`)"
          >
            {{ item.value }}
          </el-button>
          <span v-else>{{ item.value }}</span>
        </template>
      </DetailMetaList>

      <div v-if="instance.errorMsg" class="exec-error">
        <div class="exec-error__label">失败原因</div>
        <p>{{ instance.errorMsg }}</p>
      </div>
    </DetailSection>

    <DetailSection
      :title="hasPublicOutput ? '入参 / 对外出参' : '入参 / 执行结果'"
      :hint="sectionHint"
    >
      <DetailCompare
        left-title="入参"
        :right-title="hasPublicOutput ? '对外出参' : '执行结果'"
        :left-value="instance.inputParams"
        :right-value="displayPublicOutput"
        left-empty="暂无入参"
        :right-empty="hasPublicOutput ? '暂无出参' : '暂无结果'"
        left-copy-message="已复制入参"
        :right-copy-message="hasPublicOutput ? '已复制对外出参' : '已复制执行结果'"
      />
    </DetailSection>

    <DetailSection
      v-if="showDebugOutput"
      title="调试 · 按节点原始结果"
      hint="内部按 nodeId 聚合，不等于开放 API 的 data.output"
    >
      <DetailCodeBlock
        title="按节点原始结果"
        :value="instance.outputResult"
        copy-message="已复制原始结果"
        max-height="220px"
      />
    </DetailSection>

    <DetailSection title="节点日志" :hint="logs.length ? `共 ${logs.length} 个节点` : ''">
      <DetailEmpty v-if="!logs.length" text="还没有节点日志" />
      <el-timeline v-else class="log-timeline">
        <el-timeline-item
          v-for="(item, index) in logs"
          :key="item.id || index"
          :timestamp="formatTime(item.startTime)"
          :type="timelineType(item.status)"
        >
          <div
            :ref="(el) => setNodeRef(item, el)"
            class="log-card"
            :class="[`is-${(item.status || '').toLowerCase()}`, { 'is-focus': isFocused(item) }]"
          >
            <div class="log-title">
              <span>{{ index + 1 }}. {{ item.nodeName || item.nodeId }}</span>
              <StatusTag kind="exec" :value="item.status" />
            </div>
            <div class="log-sub qz-mono">{{ item.requestMethod }} {{ item.requestUrl || '—' }}</div>
            <div v-if="item.errorMsg" class="log-error">{{ item.errorMsg }}</div>
            <div v-else class="log-meta">
              <template v-if="isSqlLog(item)">
                {{ sqlResultText(item) }}
              </template>
              <template v-else>
                HTTP {{ item.responseStatus ?? '—' }}
              </template>
              <span>耗时 {{ durationText(item.durationMs) }}</span>
              <span>重试 {{ item.retryCount || 0 }}</span>
            </div>
            <template v-if="hasIo(item)">
              <button type="button" class="io-toggle" @click="toggle(reqName(item, index))">
                {{ isOpen(reqName(item, index)) ? '收起请求 / 响应' : '展开请求 / 响应' }}
              </button>
              <div v-show="isOpen(reqName(item, index))" class="io-panel">
                <DetailCompare
                  :left-title="isSqlLog(item) ? 'SQL / 参数' : '请求'"
                  :right-title="isSqlLog(item) ? '节点结果' : '响应'"
                  :left-value="item.requestBody"
                  :right-value="displayResponseBody(item)"
                  :left-empty="isSqlLog(item) ? '暂无 SQL' : '暂无请求体'"
                  :right-empty="isSqlLog(item) ? '暂无结果' : '暂无响应体'"
                  :left-copy-message="isSqlLog(item) ? '已复制 SQL' : '已复制请求'"
                  :right-copy-message="isSqlLog(item) ? '已复制节点结果' : '已复制响应'"
                  max-height="220px"
                />
                <DetailCodeBlock
                  v-if="item.requestHeaders && !isSqlLog(item)"
                  class="io-headers"
                  title="请求头"
                  :value="item.requestHeaders"
                  copy-message="已复制请求头"
                  max-height="160px"
                />
              </div>
            </template>
          </div>
        </el-timeline-item>
      </el-timeline>
    </DetailSection>
  </div>
  <DetailEmpty v-else text="暂无执行详情" />
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import StatusTag from '@/components/StatusTag.vue'
import DetailSection from '@/components/detail/DetailSection.vue'
import DetailMetaList from '@/components/detail/DetailMetaList.vue'
import DetailCompare from '@/components/detail/DetailCompare.vue'
import DetailCodeBlock from '@/components/detail/DetailCodeBlock.vue'
import DetailEmpty from '@/components/detail/DetailEmpty.vue'
import { copyText, durationText, formatTime, triggerLabel } from '@/utils/format'
import { isSqlLog } from '@/utils/sqlParams'
import { normalizePayloadForDisplay } from '@/utils/workflowBinding'

const props = defineProps({
  instance: { type: Object, default: null },
  logs: { type: Array, default: () => [] },
  workflowName: { type: String, default: '' },
  workflowId: { type: [Number, String], default: '' },
  executionLink: { type: String, default: '' },
  highlightNodeId: { type: String, default: '' },
  compact: { type: Boolean, default: false },
  /** 试跑 / 开放调用同款投影结果；有则优先展示 */
  publicOutput: { type: [Object, Array, String, Number, Boolean], default: undefined },
  publicOutputHint: { type: String, default: '' },
})

const opened = ref([])
const nodeEls = new Map()
const focusedNodeId = ref('')

const displayPublicOutput = computed(() => {
  // 调用方传入了 publicOutput（含 null）→ 始终按对外出参展示，禁止回落调试大包
  if (props.publicOutput !== undefined) {
    return props.publicOutput
  }
  return props.instance?.outputResult
})

const hasPublicOutput = computed(() => props.publicOutput !== undefined)

const sectionHint = computed(() => {
  if (props.publicOutputHint) return props.publicOutputHint
  if (hasPublicOutput.value) return '左侧为触发入参；右侧为开放 API / 调度看到的结果形态'
  return '左侧为触发入参；右侧为按节点聚合的原始结果（未投影）'
})

const showDebugOutput = computed(() => {
  if (!props.instance?.outputResult) return false
  if (!hasPublicOutput.value) return false
  try {
    const raw = typeof props.instance.outputResult === 'string'
      ? JSON.parse(props.instance.outputResult)
      : props.instance.outputResult
    return JSON.stringify(props.publicOutput ?? null) !== JSON.stringify(raw)
  } catch {
    return true
  }
})

function nodeKey(item) {
  return item?.nodeId || String(item?.id || '')
}

function setNodeRef(item, el) {
  const key = nodeKey(item)
  if (!key) return
  if (el) nodeEls.set(key, el)
  else nodeEls.delete(key)
}

function isProblem(status) {
  return status === 'FAILED' || status === 'TIMEOUT'
}

function isFocused(item) {
  return focusedNodeId.value && focusedNodeId.value === nodeKey(item)
}

const metaItems = computed(() => {
  const instance = props.instance || {}
  return [
    {
      label: '工作流',
      value: props.workflowName || (instance.workflowId ? `#${instance.workflowId}` : ''),
      slot: props.workflowId ? 'workflow' : undefined,
    },
    { label: '触发', value: triggerLabel(instance.triggerType) },
    { label: '快照', value: instance.snapshotId ? String(instance.snapshotId) : '草稿' },
    { label: '总耗时', value: durationText(instance.durationMs) },
    { label: 'Trace', value: instance.traceId, mono: true, copy: true, copyMessage: '已复制 Trace', hidden: !instance.traceId },
    { label: '开始时间', value: formatTime(instance.startTime), hidden: !instance.startTime },
  ]
})

function reqName(item, index) {
  return `${item.id || index}-req`
}

function hasIo(item) {
  return Boolean(item.requestBody || item.responseBody || item.requestHeaders)
}

function sqlResultText(item) {
  try {
    const body = typeof item.responseBody === 'string' ? JSON.parse(item.responseBody) : item.responseBody
    if (body && typeof body === 'object') {
      if (item.requestMethod === 'UPDATE' || body.affectedRows) {
        return `影响 ${body.affectedRows ?? 0} 行`
      }
      const extra = body.truncated ? '（已截断）' : ''
      return `返回 ${body.rowCount ?? 0} 行${extra}`
    }
  } catch {
    // ignore
  }
  return 'SQL 已执行'
}

function displayResponseBody(item) {
  return normalizePayloadForDisplay(item?.responseBody)
}

function isOpen(name) {
  return opened.value.includes(name)
}

function toggle(name) {
  if (isOpen(name)) {
    opened.value = opened.value.filter((item) => item !== name)
    return
  }
  opened.value = [...opened.value, name]
}

function timelineType(status) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED' || status === 'TIMEOUT') return 'danger'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

function syncOpened() {
  const next = []
  props.logs.forEach((item, index) => {
    if (isProblem(item.status) || (props.highlightNodeId && nodeKey(item) === props.highlightNodeId)) {
      next.push(reqName(item, index))
    }
  })
  opened.value = next
}

async function focusNode(nodeId) {
  if (!nodeId) return
  focusedNodeId.value = nodeId
  const index = props.logs.findIndex((item) => nodeKey(item) === nodeId)
  if (index >= 0) {
    const name = reqName(props.logs[index], index)
    if (!opened.value.includes(name)) {
      opened.value = [...opened.value, name]
    }
  }
  await nextTick()
  const el = nodeEls.get(nodeId)
  el?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

defineExpose({ focusNode })

watch(
  () => [props.instance?.id, props.logs, props.highlightNodeId],
  async () => {
    syncOpened()
    if (props.highlightNodeId) {
      await nextTick()
      focusNode(props.highlightNodeId)
    }
  },
  { immediate: true },
)

async function copy(text, message = '已复制') {
  await copyText(text)
  ElMessage.success(message)
}
</script>

<style scoped>
.exec-log {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.exec-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 14px;
}
.exec-status,
.exec-head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.exec-no {
  font-size: 13px;
  color: var(--qz-text-secondary);
}
.exec-error {
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: var(--qz-radius-sm);
  background: var(--qz-danger-soft);
  border: 1px solid #fecaca;
}
.exec-error__label {
  font-size: 12px;
  font-weight: 650;
  color: var(--qz-danger);
  margin-bottom: 4px;
}
.exec-error p {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--qz-text);
  word-break: break-all;
}
.log-timeline {
  padding-left: 4px;
}
.log-card {
  padding: 12px 14px;
  border: 1px solid var(--qz-border);
  border-radius: var(--qz-radius-sm);
  background: var(--qz-fill);
}
.log-card.is-failed,
.log-card.is-timeout {
  border-color: #fecaca;
  background: #fff7f7;
}
.log-card.is-success {
  border-color: #bbf7d0;
  background: #f8fffb;
}
.log-card.is-focus {
  outline: 2px solid var(--el-color-primary-light-5);
}
.log-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-weight: 650;
  font-size: 13px;
}
.log-sub,
.log-meta {
  color: var(--qz-text-muted);
  font-size: 12px;
  word-break: break-all;
  margin-top: 6px;
}
.log-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
}
.log-error {
  margin-top: 8px;
  color: var(--qz-danger);
  font-size: 12px;
  line-height: 1.5;
  word-break: break-all;
}
.io-toggle {
  margin-top: 10px;
  padding: 0;
  border: none;
  background: none;
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}
.io-panel {
  margin-top: 10px;
}
.io-headers {
  margin-top: 10px;
}
@media (max-width: 720px) {
  .exec-head {
    flex-direction: column;
  }
}
</style>
