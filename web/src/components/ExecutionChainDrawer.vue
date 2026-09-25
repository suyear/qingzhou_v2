<template>
  <el-drawer
    v-model="visible"
    title="执行链路"
    size="800px"
    destroy-on-close
    class="qz-detail-drawer qz-chain-drawer"
    @closed="reset"
  >
    <PageState :error="error" @retry="reload" />
    <div v-loading="loading" class="chain-body">
      <ExecutionChainView v-if="chain" :chain="chain" />
      <DetailEmpty v-else-if="!loading && !error" text="请选择一条执行记录" />
    </div>
    <template #footer>
      <DetailActions v-if="chain?.instance" stack>
        <el-button
          type="primary"
          :disabled="chain.instance.status === 'RUNNING'"
          :loading="replayLoading"
          @click="$emit('replay', chain.instance)"
        >
          重放此单
        </el-button>
      </DetailActions>
    </template>
  </el-drawer>
</template>

<script setup>
import { ref } from 'vue'
import DetailActions from './detail/DetailActions.vue'
import DetailEmpty from './detail/DetailEmpty.vue'
import ExecutionChainView from './ExecutionChainView.vue'
import PageState from './PageState.vue'
import { getExecutionChain } from '@/api/execution'
import { networkErrorMessage } from '@/api/http'

defineProps({
  replayLoading: { type: Boolean, default: false },
})
defineEmits(['replay'])

const visible = ref(false)
const loading = ref(false)
const error = ref('')
const chain = ref(null)
const currentId = ref(null)

function reset() {
  chain.value = null
  error.value = ''
  currentId.value = null
}

async function open(id) {
  currentId.value = id
  visible.value = true
  await reload()
}

async function reload() {
  if (!currentId.value) return
  loading.value = true
  error.value = ''
  try {
    const res = await getExecutionChain(currentId.value)
    chain.value = res.data || null
  } catch (e) {
    error.value = networkErrorMessage(e)
    chain.value = null
  } finally {
    loading.value = false
  }
}

function setChain(next) {
  chain.value = next
  visible.value = true
}

defineExpose({ open, setChain, reload, visible })
</script>

<style scoped>
.chain-body {
  min-height: 120px;
}
</style>
