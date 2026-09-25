<template>
  <div>
    <PageHeader title="用户管理" desc="管理控制台账号与角色（管理员 / 开发者 / 只读运维）。">
      <el-button type="primary" @click="openCreate">新建用户</el-button>
    </PageHeader>
    <PageState :error="loadError" @retry="load" />
    <div class="qz-panel">
      <el-table class="qz-table" :data="records" v-loading="loading" stripe>
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="displayName" label="显示名" min-width="120" />
        <el-table-column label="角色" min-width="180">
          <template #default="{ row }">
            <el-tag v-for="r in row.roles || []" :key="r" size="small" class="role-tag">{{ roleLabel(r) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近登录" min-width="160">
          <template #default="{ row }">{{ formatTime(row.lastLoginAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 1" type="warning" link @click="onDisable(row)">停用</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="visible" :title="form.id ? '编辑用户' : '新建用户'" width="480px">
      <el-form label-position="top">
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="显示名" required>
          <el-input v-model="form.displayName" />
        </el-form-item>
        <el-form-item :label="form.id ? '重置密码（留空不改）' : '初始密码'" :required="!form.id">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-checkbox-group v-model="form.roles">
            <el-checkbox v-for="r in roleOptions" :key="r.code" :value="r.code">{{ r.name }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import PageState from '@/components/PageState.vue'
import { createUser, disableUser, listRoles, pageUsers, updateUser } from '@/api/auth'
import { networkErrorMessage } from '@/api/http'
import { formatTime } from '@/utils/format'
import { askConfirm } from '@/utils/confirm'

const loading = ref(false)
const loadError = ref('')
const records = ref([])
const roleOptions = ref([])
const visible = ref(false)
const saving = ref(false)
const form = reactive({
  id: null,
  username: '',
  displayName: '',
  password: '',
  roles: [],
  enabled: true,
})

function roleLabel(code) {
  return roleOptions.value.find((r) => r.code === code)?.name || code
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [users, roles] = await Promise.all([pageUsers({ current: 1, size: 100 }), listRoles()])
    records.value = users.data?.records || []
    roleOptions.value = roles.data || []
  } catch (e) {
    loadError.value = networkErrorMessage(e)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: null, username: '', displayName: '', password: '', roles: ['DEVELOPER'], enabled: true })
  visible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    username: row.username,
    displayName: row.displayName,
    password: '',
    roles: [...(row.roles || [])],
    enabled: row.status === 1,
  })
  visible.value = true
}

async function onSave() {
  saving.value = true
  try {
    const payload = {
      username: form.username,
      displayName: form.displayName,
      password: form.password || undefined,
      roles: form.roles,
      status: form.enabled ? 1 : 0,
    }
    if (form.id) {
      await updateUser(form.id, payload)
    } else {
      await createUser(payload)
    }
    ElMessage.success('已保存')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onDisable(row) {
  await askConfirm(`确定停用用户「${row.username}」？`)
  await disableUser(row.id)
  ElMessage.success('已停用')
  await load()
}

onMounted(load)
</script>

<style scoped>
.role-tag { margin-right: 4px; }
</style>
