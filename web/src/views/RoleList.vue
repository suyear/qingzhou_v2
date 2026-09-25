<template>
  <div>
    <PageHeader title="角色权限" desc="为角色勾选可访问的菜单与可执行的操作。管理员角色权限固定为全部。">
      <el-button @click="load">刷新</el-button>
    </PageHeader>
    <PageState :error="loadError" @retry="load" />

    <div class="role-layout" v-loading="loading">
      <div class="qz-panel role-list">
        <div
          v-for="role in roles"
          :key="role.id"
          class="role-item"
          :class="{ active: current?.id === role.id }"
          @click="selectRole(role)"
        >
          <strong>{{ role.roleName }}</strong>
          <span class="code">{{ role.roleCode }}</span>
          <p>{{ role.description || '—' }}</p>
          <div class="meta">{{ role.userCount || 0 }} 人 · {{ (role.permissions || []).length }} 项权限</div>
        </div>
      </div>

      <div class="qz-panel role-detail" v-if="current">
        <div class="detail-head">
          <div>
            <h3>{{ current.roleName }}</h3>
            <p class="muted">{{ current.roleCode }} · {{ current.description }}</p>
          </div>
          <el-button
            type="primary"
            :loading="saving"
            :disabled="current.roleCode === 'ADMIN'"
            @click="onSave"
          >
            保存权限
          </el-button>
        </div>
        <el-alert
          v-if="current.roleCode === 'ADMIN'"
          type="info"
          :closable="false"
          show-icon
          title="管理员拥有全部菜单与操作权限，不可在此修改。"
          class="mb12"
        />
        <div v-for="group in permissionGroups" :key="group.key" class="perm-group">
          <h4>{{ group.label }}</h4>
          <el-checkbox-group v-model="checked" :disabled="current.roleCode === 'ADMIN'">
            <el-checkbox
              v-for="p in group.items"
              :key="p.permCode"
              :value="p.permCode"
              class="perm-check"
            >
              {{ p.permName }}
              <span class="code">{{ p.permCode }}</span>
            </el-checkbox>
          </el-checkbox-group>
        </div>
      </div>
      <el-empty v-else description="请选择左侧角色" />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import PageState from '@/components/PageState.vue'
import { listPermissions, listRolesDetail, updateRolePermissions } from '@/api/auth'
import { networkErrorMessage } from '@/api/http'

const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const roles = ref([])
const permissions = ref([])
const current = ref(null)
const checked = ref([])

const GROUP_LABEL = {
  null: '通用',
  'group:编排': '编排菜单',
  'group:运行': '运行菜单',
  'group:开放': '开放菜单',
  'group:系统': '系统菜单',
  'group:操作': '操作权限',
}

const permissionGroups = computed(() => {
  const map = new Map()
  for (const p of permissions.value) {
    const key = p.parentCode || 'null'
    if (!map.has(key)) map.set(key, [])
    map.get(key).push(p)
  }
  return [...map.entries()].map(([key, items]) => ({
    key,
    label: GROUP_LABEL[key] || key,
    items,
  }))
})

function selectRole(role) {
  current.value = role
  checked.value = [...(role.permissions || [])]
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [roleRes, permRes] = await Promise.all([listRolesDetail(), listPermissions()])
    roles.value = roleRes.data || []
    permissions.value = permRes.data || []
    if (current.value) {
      const fresh = roles.value.find((r) => r.id === current.value.id)
      if (fresh) selectRole(fresh)
    } else if (roles.value.length) {
      selectRole(roles.value[0])
    }
  } catch (e) {
    loadError.value = networkErrorMessage(e)
  } finally {
    loading.value = false
  }
}

async function onSave() {
  if (!current.value || current.value.roleCode === 'ADMIN') return
  saving.value = true
  try {
    const res = await updateRolePermissions(current.value.id, { permissions: checked.value })
    ElMessage.success('权限已保存')
    const idx = roles.value.findIndex((r) => r.id === current.value.id)
    if (idx >= 0) roles.value[idx] = res.data
    selectRole(res.data)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.role-layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 12px;
  align-items: start;
}
.role-list { padding: 8px; }
.role-item {
  padding: 12px 14px;
  border-radius: 10px;
  cursor: pointer;
  border: 1px solid transparent;
}
.role-item:hover { background: var(--qz-fill, #f8fafc); }
.role-item.active {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}
.role-item strong { display: block; font-size: 14px; }
.role-item .code, .perm-check .code {
  font-size: 11px;
  color: #94a3b8;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  margin-left: 6px;
}
.role-item p {
  margin: 4px 0;
  font-size: 12px;
  color: #64748b;
}
.role-item .meta { font-size: 12px; color: #94a3b8; }
.role-detail { padding: 16px 18px 20px; }
.detail-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}
.detail-head h3 { margin: 0; font-size: 16px; }
.muted { margin: 4px 0 0; color: #64748b; font-size: 13px; }
.mb12 { margin-bottom: 12px; }
.perm-group { margin-top: 14px; }
.perm-group h4 {
  margin: 0 0 8px;
  font-size: 13px;
  color: #334155;
}
.perm-check {
  display: flex;
  width: 100%;
  margin: 4px 0;
}
@media (max-width: 900px) {
  .role-layout { grid-template-columns: 1fr; }
}
</style>
