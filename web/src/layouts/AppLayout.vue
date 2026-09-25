<template>
  <el-container class="qz-layout" direction="vertical">
    <el-header class="qz-header" height="56px">
      <div class="qz-brand">轻舟<small>低代码集成调度中台</small></div>
      <div class="qz-header-page">{{ currentTitle }}</div>
      <div class="qz-header-user">
        <el-dropdown trigger="click" @command="onUserCommand">
          <span class="user-trigger">
            {{ auth.user.value?.displayName || auth.user.value?.username || '用户' }}
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>{{ roleText }}</el-dropdown-item>
              <el-dropdown-item command="password">修改密码</el-dropdown-item>
              <el-dropdown-item v-if="auth.hasPermission('menu:settings')" command="settings">系统设置</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>
    <el-container class="qz-body">
      <el-aside v-if="!route.meta.full" class="qz-aside" width="216px">
        <el-menu
          class="qz-menu"
          :default-active="activeMenu"
          router
          background-color="#0f172a"
          text-color="#94a3b8"
          active-text-color="#ffffff"
        >
          <template v-for="group in visibleGroups" :key="group.key">
            <div v-if="group.label" class="nav-group">{{ group.label }}</div>
            <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
              <el-icon><component :is="iconMap[item.icon]" /></el-icon>
              <span>{{ item.title }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </el-aside>
      <el-main :class="route.meta.full ? 'qz-main-full' : 'qz-main'">
        <el-alert
          v-if="backendUnreachable && !route.meta.full"
          class="backend-alert"
          type="error"
          show-icon
          :closable="false"
          title="无法连接后端服务"
          description="请确认已启动 server（默认 http://127.0.0.1:18080）。前端开发服务会把 /api 代理到该地址。"
        />
        <router-view />
      </el-main>
    </el-container>

    <el-dialog v-model="pwdVisible" title="修改密码" width="420px">
      <el-form label-position="top">
        <el-form-item label="当前密码">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwdForm.newPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSaving" @click="onChangePassword">保存</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowDown,
  Connection,
  Document,
  Grid,
  HomeFilled,
  Key,
  List,
  Lock,
  Setting,
  Share,
  Ticket,
  Timer,
  User,
} from '@element-plus/icons-vue'
import { backendUnreachable } from '@/api/http'
import { changePassword, fetchMe, logout } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { MENU_GROUPS, PAGE_TITLES } from '@/utils/menus'

const iconMap = {
  HomeFilled,
  Grid,
  Share,
  Key,
  Timer,
  List,
  Connection,
  User,
  Lock,
  Ticket,
  Document,
  Setting,
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const pwdVisible = ref(false)
const pwdSaving = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '' })

const visibleGroups = computed(() =>
  MENU_GROUPS
    .map((g) => ({
      ...g,
      items: g.items.filter((item) => auth.hasPermission(item.perm)),
    }))
    .filter((g) => g.items.length > 0),
)

const currentTitle = computed(() => {
  if (route.path.startsWith('/designer')) {
    return '工作流设计器'
  }
  return PAGE_TITLES[route.path] || '轻舟'
})

const activeMenu = computed(() => {
  if (route.path.startsWith('/designer')) {
    return '/workflows'
  }
  return route.path
})

const roleText = computed(() => {
  const map = { ADMIN: '管理员', DEVELOPER: '开发者', VIEWER: '只读运维' }
  return (auth.roles.value || []).map((r) => map[r] || r).join('、') || '未分配角色'
})

async function onUserCommand(cmd) {
  if (cmd === 'logout') {
    try {
      await logout()
    } catch {
      /* ignore */
    }
    auth.clearSession()
    await router.replace('/login')
    return
  }
  if (cmd === 'settings') {
    await router.push('/settings')
    return
  }
  if (cmd === 'password') {
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdVisible.value = true
  }
}

async function onChangePassword() {
  pwdSaving.value = true
  try {
    await changePassword(pwdForm)
    ElMessage.success('密码已更新')
    pwdVisible.value = false
  } finally {
    pwdSaving.value = false
  }
}

onMounted(async () => {
  try {
    const res = await fetchMe()
    if (res.data) {
      auth.setSession({ ...res.data, token: auth.getToken() })
    }
  } catch {
    /* keep cached session */
  }
})
</script>

<style scoped>
.el-header {
  display: flex;
  align-items: center;
  gap: 16px;
}
.qz-header-page {
  flex: 1;
  font-size: 14px;
  color: #64748b;
}
.qz-header-user {
  margin-left: auto;
}
.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: #334155;
  font-size: 13px;
}
.nav-group {
  padding: 14px 20px 6px;
  font-size: 11px;
  letter-spacing: 0.06em;
  color: #64748b;
  text-transform: uppercase;
}
.qz-menu {
  border-right: none;
}
.qz-menu :deep(.el-menu-item) {
  height: 42px;
  margin: 2px 8px;
  border-radius: 8px;
}
.qz-menu :deep(.el-menu-item:hover) {
  background: rgba(148, 163, 184, 0.12) !important;
}
.qz-menu :deep(.el-menu-item.is-active) {
  background: #2563eb !important;
}
.backend-alert {
  margin-bottom: 12px;
}
</style>
