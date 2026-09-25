<template>
  <div>
    <PageHeader title="审计日志" desc="谁在何时做了什么：登录、发布、启停调度、密钥重置等。">
      <el-input v-model="actorName" class="search-input" placeholder="操作人" clearable @keyup.enter="load" @clear="load" />
      <el-input v-model="action" class="search-input" placeholder="动作编码" clearable @keyup.enter="load" @clear="load" />
      <el-button @click="load">查询</el-button>
    </PageHeader>
    <PageState :error="loadError" @retry="load" />
    <div class="qz-panel">
      <el-table class="qz-table" :data="records" v-loading="loading" stripe>
        <el-table-column label="时间" min-width="160">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column prop="actorName" label="操作人" width="120" />
        <el-table-column prop="action" label="动作" min-width="150" />
        <el-table-column label="资源" min-width="140">
          <template #default="{ row }">{{ row.resourceType || '—' }} {{ row.resourceId || '' }}</template>
        </el-table-column>
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">{{ row.result }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="200" show-overflow-tooltip />
        <el-table-column prop="clientIp" label="IP" width="130" />
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="current"
          v-model:page-size="size"
          layout="total, prev, pager, next"
          :total="total"
          @current-change="load"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import PageState from '@/components/PageState.vue'
import { pageAuditLogs } from '@/api/auth'
import { networkErrorMessage } from '@/api/http'
import { formatTime } from '@/utils/format'

const loading = ref(false)
const loadError = ref('')
const records = ref([])
const actorName = ref('')
const action = ref('')
const current = ref(1)
const size = ref(20)
const total = ref(0)

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await pageAuditLogs({
      current: current.value,
      size: size.value,
      actorName: actorName.value || undefined,
      action: action.value || undefined,
    })
    records.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } catch (e) {
    loadError.value = networkErrorMessage(e)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.pager { display: flex; justify-content: flex-end; padding: 12px 16px; }
.search-input { width: 160px; }
</style>
