<template>
  <div>
    <PageHeader title="审计日志" desc="谁在何时做了什么：登录、发布、启停调度、密钥重置等。" />
    <PageState :error="loadError" @retry="load" />
    <div class="filter-bar qz-panel">
      <el-input v-model="actorName" class="search-input" placeholder="操作人" clearable @keyup.enter="load" @clear="load" />
      <el-input v-model="action" class="search-input" placeholder="动作编码" clearable @keyup.enter="load" @clear="load" />
      <el-button type="primary" @click="load">查询</el-button>
    </div>
    <div class="qz-panel">
      <el-table
        class="qz-table is-clickable"
        :data="records"
        v-loading="loading"
        stripe
        highlight-current-row
        @row-click="openDetail"
      >
        <el-table-column label="时间" min-width="160">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column prop="actorName" label="操作人" width="120" />
        <el-table-column prop="action" label="动作" min-width="150" />
        <el-table-column label="资源" min-width="140">
          <template #default="{ row }">{{ row.resourceType || '—' }} {{ row.resourceId || '' }}</template>
        </el-table-column>
        <el-table-column label="结果" width="100">
          <template #default="{ row }">
            <StatusTag
              :type="row.result === 'SUCCESS' ? 'success' : 'danger'"
              :label="row.result === 'SUCCESS' ? '成功' : (row.result === 'FAILED' ? '失败' : (row.result || '—'))"
            />
          </template>
        </el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="200" show-overflow-tooltip />
        <el-table-column prop="clientIp" label="IP" width="130" />
        <template #empty>
          <el-empty v-if="!loading && !loadError" description="暂无审计记录" />
        </template>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="current"
          v-model:page-size="size"
          background
          layout="total, prev, pager, next"
          :total="total"
          @current-change="load"
        />
      </div>
      <p v-if="records.length" class="table-foot">点击行查看摘要 · 共 {{ total }} 条</p>
    </div>

    <el-drawer
      v-model="drawerVisible"
      title="审计详情"
      size="480px"
      class="qz-detail-drawer"
    >
      <template v-if="drawerRow">
        <DetailSection title="记录">
          <div class="drawer-tags">
            <StatusTag
              :type="drawerRow.result === 'SUCCESS' ? 'success' : 'danger'"
              :label="drawerRow.result === 'SUCCESS' ? '成功' : (drawerRow.result === 'FAILED' ? '失败' : (drawerRow.result || '—'))"
            />
          </div>
          <DetailMetaList :items="drawerMetaItems" />
        </DetailSection>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import PageState from '@/components/PageState.vue'
import StatusTag from '@/components/StatusTag.vue'
import DetailSection from '@/components/detail/DetailSection.vue'
import DetailMetaList from '@/components/detail/DetailMetaList.vue'
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
const drawerVisible = ref(false)
const drawerRow = ref(null)

const drawerMetaItems = computed(() => {
  const row = drawerRow.value
  if (!row) return []
  return [
    { label: '时间', value: formatTime(row.createTime) },
    { label: '操作人', value: row.actorName || '—' },
    { label: '动作', value: row.action || '—', mono: true },
    { label: '资源', value: `${row.resourceType || '—'} ${row.resourceId || ''}`.trim() },
    { label: '摘要', value: row.summary || '—' },
    { label: 'IP', value: row.clientIp || '—', mono: true },
  ]
})

function openDetail(row) {
  drawerRow.value = row
  drawerVisible.value = true
}

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
.drawer-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 12px; }
</style>
