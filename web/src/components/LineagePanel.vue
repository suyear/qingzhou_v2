<template>
  <div v-loading="loading" class="lineage-panel">
    <p v-if="error" class="err">{{ error }}</p>
    <template v-else-if="lineage">
      <DetailSection v-if="showComponents" title="使用的接口组件">
        <div v-if="lineage.components?.length" class="lineage-list">
          <button
            v-for="item in lineage.components"
            :key="`c-${item.id || item.code}`"
            type="button"
            class="lineage-item"
            @click="goComponent(item)"
          >
            <div class="lineage-name">{{ item.name || item.code }}</div>
            <div class="sub">{{ [item.code, item.status, item.extra].filter(Boolean).join(' · ') }}</div>
          </button>
        </div>
        <p v-else class="muted">工作流尚未绑定接口组件</p>
      </DetailSection>

      <DetailSection v-if="showWorkflows" title="被哪些工作流使用">
        <div v-if="lineage.workflows?.length" class="lineage-list">
          <button
            v-for="item in lineage.workflows"
            :key="`w-${item.id}`"
            type="button"
            class="lineage-item"
            @click="goWorkflow(item)"
          >
            <div class="lineage-name">{{ item.name }}</div>
            <div class="sub">{{ [item.code, statusLabel(item.status), item.extra].filter(Boolean).join(' · ') }}</div>
          </button>
        </div>
        <p v-else class="muted">还没有工作流引用此组件</p>
      </DetailSection>

      <DetailSection title="调度任务">
        <div v-if="lineage.schedules?.length" class="lineage-list">
          <button
            v-for="item in lineage.schedules"
            :key="`s-${item.id}`"
            type="button"
            class="lineage-item"
            @click="goSchedule(item)"
          >
            <div class="lineage-name">{{ item.name }}</div>
            <div class="sub">{{ [item.status, item.extra].filter(Boolean).join(' · ') }}</div>
          </button>
        </div>
        <p v-else class="muted">{{ type === 'component' ? '引用此组件的工作流还没有调度任务' : '没有调度任务调用' }}</p>
      </DetailSection>

      <DetailSection title="开放应用">
        <div v-if="lineage.openapiApps?.length" class="lineage-list">
          <button
            v-for="item in lineage.openapiApps"
            :key="`a-${item.id}`"
            type="button"
            class="lineage-item"
            @click="goOpenapi(item)"
          >
            <div class="lineage-name">{{ item.name }}</div>
            <div class="sub">{{ [item.code, item.status, item.extra].filter(Boolean).join(' · ') }}</div>
          </button>
        </div>
        <p v-else class="muted">{{ type === 'component' ? '引用此组件的工作流还没有开放授权' : '没有开放应用授权此工作流' }}</p>
      </DetailSection>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import DetailSection from '@/components/detail/DetailSection.vue'
import { getComponentLineage, getWorkflowLineage } from '@/api/lineage'
import { networkErrorMessage } from '@/api/http'
import { workflowStatusLabel } from '@/utils/format'

const props = defineProps({
  type: { type: String, required: true },
  id: { type: [Number, String], default: null },
  modelValue: { type: Object, default: null },
})

const emit = defineEmits(['update:modelValue'])
const router = useRouter()
const loading = ref(false)
const error = ref('')
const local = ref(null)

const lineage = computed(() => props.modelValue || local.value)
const showComponents = computed(() => props.type === 'workflow')
const showWorkflows = computed(() => props.type === 'component')

function statusLabel(status) {
  return workflowStatusLabel(status) === status ? status : workflowStatusLabel(status)
}

async function load() {
  if (props.modelValue || !props.id) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    const res = props.type === 'component'
      ? await getComponentLineage(props.id)
      : await getWorkflowLineage(props.id)
    local.value = res.data || null
    emit('update:modelValue', local.value)
  } catch (e) {
    error.value = networkErrorMessage(e)
    local.value = null
  } finally {
    loading.value = false
  }
}

function goComponent(item) {
  if (item?.id) router.push({ path: '/components', query: { keyword: item.code || String(item.id) } })
}

function goWorkflow(item) {
  if (item?.id) router.push(`/designer/${item.id}`)
}

function goSchedule(item) {
  router.push({ path: '/schedules', query: item?.id ? { jobId: String(item.id) } : {} })
}

function goOpenapi(item) {
  router.push({ path: '/openapi', query: item?.id ? { appId: String(item.id) } : {} })
}

watch(() => [props.type, props.id], load)
onMounted(load)
</script>

<style scoped>
.lineage-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.lineage-item {
  text-align: left;
  border: 1px solid var(--qz-border);
  background: var(--qz-fill);
  border-radius: var(--qz-radius-sm);
  padding: 10px 12px;
  cursor: pointer;
  font: inherit;
  color: inherit;
}
.lineage-item:hover {
  border-color: var(--el-color-primary-light-5);
  background: var(--qz-primary-soft);
}
.lineage-name {
  font-size: 13px;
  font-weight: 600;
}
.err {
  color: var(--qz-danger);
  font-size: 12px;
}
</style>
