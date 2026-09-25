<template>
  <div v-if="chain?.instance" class="chain">
    <div class="chain-head">
      <div class="chain-status">
        <StatusTag kind="exec" :value="instance.status" />
        <span class="exec-no">
          单号
          <el-button type="primary" link @click="copy(instance.executionNo, '已复制单号')">{{ instance.executionNo }}</el-button>
        </span>
      </div>
      <div class="chain-actions">
        <el-button type="primary" link @click="$router.push({ path: '/executions', query: { keyword: instance.executionNo } })">
          运行结果
        </el-button>
        <el-button
          v-if="instance.traceId"
          type="primary"
          link
          @click="copy(instance.traceId, '已复制 Trace')"
        >
          复制 Trace
        </el-button>
      </div>
    </div>

    <div class="chain-steps">
      <button type="button" class="chain-step" :class="triggerClass" @click="goTrigger">
        <div class="step-k">触发来源</div>
        <div class="step-v">{{ triggerTitle }}</div>
        <div class="step-s">{{ triggerHint }}</div>
      </button>
      <span class="chain-arrow">→</span>
      <button type="button" class="chain-step" @click="goWorkflow">
        <div class="step-k">工作流</div>
        <div class="step-v">{{ workflowTitle }}</div>
        <div class="step-s">{{ workflowHint }}</div>
      </button>
      <span class="chain-arrow">→</span>
      <button type="button" class="chain-step" :class="failedClass" @click="jumpFailed">
        <div class="step-k">{{ failedNode ? '出错节点' : '节点' }}</div>
        <div class="step-v">{{ failedTitle }}</div>
        <div class="step-s">{{ failedHint }}</div>
      </button>
    </div>

    <p v-if="instance.errorMsg" class="err">{{ instance.errorMsg }}</p>
    <p v-if="chain.trigger?.inferred" class="muted">
      历史调度未记下具体任务，已根据工作流反查上游配置。
    </p>

    <DetailSection v-if="hasUpstream" title="上游配置">
      <div class="upstream">
        <el-button
          v-for="item in chain.lineage?.schedules || []"
          :key="`s-${item.id}`"
          type="primary"
          link
          @click="$router.push({ path: '/schedules', query: { jobId: String(item.id) } })"
        >
          调度 · {{ item.name }}
        </el-button>
        <el-button
          v-for="item in chain.lineage?.openapiApps || []"
          :key="`a-${item.id}`"
          type="primary"
          link
          @click="$router.push({ path: '/openapi', query: { appId: String(item.id) } })"
        >
          开放应用 · {{ item.name }}
        </el-button>
        <el-button
          v-for="item in chain.lineage?.components || []"
          :key="`c-${item.id || item.code}`"
          type="primary"
          link
          @click="goComponent(item)"
        >
          组件 · {{ item.name || item.code }}
        </el-button>
      </div>
    </DetailSection>

    <ExecutionLogView
      ref="logView"
      compact
      :instance="instance"
      :logs="chain.logs || []"
      :workflow-id="chain.workflow?.id"
      :workflow-name="chain.workflow?.name"
      :highlight-node-id="chain.failedNodeId"
    />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import DetailSection from '@/components/detail/DetailSection.vue'
import ExecutionLogView from './ExecutionLogView.vue'
import StatusTag from './StatusTag.vue'
import { copyText, durationText, triggerLabel } from '@/utils/format'

const props = defineProps({
  chain: { type: Object, default: null },
})

const router = useRouter()
const logView = ref(null)

const instance = computed(() => props.chain?.instance || {})
const trigger = computed(() => props.chain?.trigger || {})
const failedNode = computed(() => (props.chain?.logs || []).find((item) => item.nodeId === props.chain?.failedNodeId))

const triggerTitle = computed(() => trigger.value.name || triggerLabel(instance.value.triggerType))
const triggerHint = computed(() => {
  const parts = [triggerLabel(instance.value.triggerType)]
  if (trigger.value.extra) parts.push(trigger.value.extra)
  if (instance.value.startTime) parts.push('点击查看来源配置')
  return parts.join(' · ')
})
const triggerClass = computed(() => (instance.value.status === 'FAILED' || instance.value.status === 'TIMEOUT') && !failedNode.value ? 'is-failed' : '')

const workflowTitle = computed(() => props.chain?.workflow?.name || `工作流 #${instance.value.workflowId || '—'}`)
const workflowHint = computed(() => {
  const parts = [props.chain?.workflow?.code, props.chain?.workflow?.extra]
  if (instance.value.durationMs != null) parts.push(`总耗时 ${durationText(instance.value.durationMs)}`)
  return parts.filter(Boolean).join(' · ') || '点击打开编排'
})

const failedTitle = computed(() => failedNode.value?.nodeName || failedNode.value?.nodeId || '全部节点已跑完')
const failedHint = computed(() => {
  if (!failedNode.value) {
    const count = props.chain?.logs?.length || 0
    return count ? `${count} 个节点 · 点击查看报文` : '还没有节点日志'
  }
  return failedNode.value.errorMsg || '点击查看请求 / 响应'
})
const failedClass = computed(() => (failedNode.value ? 'is-failed' : ''))

const hasUpstream = computed(() => {
  const lineage = props.chain?.lineage
  if (!lineage) return false
  return Boolean(lineage.schedules?.length || lineage.openapiApps?.length || lineage.components?.length)
})

function goTrigger() {
  const type = instance.value.triggerType
  const id = trigger.value.id
  if (type === 'OPENAPI' && id) {
    router.push({ path: '/openapi', query: { appId: String(id) } })
    return
  }
  if (type === 'SCHEDULE' && id) {
    router.push({ path: '/schedules', query: { jobId: String(id) } })
    return
  }
  if (type === 'SCHEDULE') {
    router.push({ path: '/schedules', query: instance.value.workflowId ? { workflowId: String(instance.value.workflowId) } : {} })
    return
  }
  if (type === 'TRY_RUN' || type === 'MANUAL') {
    goWorkflow()
  }
}

function goWorkflow() {
  const id = props.chain?.workflow?.id || instance.value.workflowId
  if (id) router.push(`/designer/${id}`)
}

function goComponent(item) {
  if (item?.code) {
    router.push({ path: '/components', query: { keyword: item.code } })
  }
}

function jumpFailed() {
  const nodeId = props.chain?.failedNodeId || props.chain?.logs?.[0]?.nodeId
  logView.value?.focusNode(nodeId)
}

async function copy(text, message) {
  await copyText(text)
  ElMessage.success(message)
}
</script>

<style scoped>
.chain-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.chain-status,
.chain-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.exec-no {
  font-size: 13px;
}
.chain-steps {
  display: grid;
  grid-template-columns: 1fr auto 1fr auto 1fr;
  gap: 8px;
  align-items: stretch;
  margin-bottom: 12px;
}
.chain-step {
  text-align: left;
  border: 1px solid var(--qz-border);
  background: var(--qz-card);
  border-radius: var(--qz-radius);
  padding: 10px 12px;
  cursor: pointer;
  font: inherit;
  color: inherit;
  min-width: 0;
}
.chain-step:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 0 0 3px var(--qz-primary-soft);
}
.chain-step.is-failed {
  border-color: #fecaca;
  background: var(--qz-danger-soft);
}
.step-k {
  font-size: 11px;
  color: var(--qz-text-muted);
  font-weight: 600;
  letter-spacing: 0.04em;
}
.step-v {
  margin-top: 4px;
  font-size: 14px;
  font-weight: 650;
  word-break: break-all;
}
.step-s {
  margin-top: 4px;
  font-size: 12px;
  color: var(--qz-text-muted);
  word-break: break-all;
}
.chain-arrow {
  align-self: center;
  color: var(--qz-text-muted);
}
.upstream {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
}
.err {
  color: var(--qz-danger);
  font-size: 13px;
  margin: 0 0 10px;
  word-break: break-all;
}
@media (max-width: 720px) {
  .chain-steps {
    grid-template-columns: 1fr;
  }
  .chain-arrow {
    display: none;
  }
}
</style>
