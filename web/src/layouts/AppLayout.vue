<template>
  <el-container class="qz-layout" direction="vertical">
    <el-header class="qz-header" height="60px">
      <div class="qz-brand">
        <div class="qz-brand-mark">
          <span class="qz-brand-name">{{ PRODUCT_NAME }}</span>
          <small>{{ PRODUCT_TAGLINE }}</small>
        </div>
        <div class="qz-brand-company">{{ COMPANY_NAME }}</div>
      </div>
      <div class="qz-header-page">
        <span class="qz-header-page-pill">{{ currentTitle }}</span>
      </div>
      <div class="qz-header-user">
        <el-dropdown trigger="click" @command="onUserCommand">
          <span class="qz-user-trigger">
            <span class="qz-user-avatar">{{ userInitial }}</span>
            <span class="qz-user-name">{{ displayName }}</span>
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
          :background-color="'transparent'"
          text-color="#94a3b8"
          active-text-color="#ffffff"
        >
          <template v-for="group in visibleGroups" :key="group.key">
            <div v-if="group.label" class="qz-nav-group">{{ group.label }}</div>
            <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
              <el-icon><component :is="iconMap[item.icon]" /></el-icon>
              <span>{{ item.title }}</span>
            </el-menu-item>
          </template>
        </el-menu>
        <div class="qz-aside-foot">
          <span class="qz-aside-product">{{ PRODUCT_NAME }}</span>
          <span class="qz-aside-company">{{ COMPANY_NAME }}</span>
        </div>
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
import { COMPANY_NAME, PRODUCT_NAME, PRODUCT_TAGLINE } from '@/utils/brand'

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
  return PAGE_TITLES[route.path] || PRODUCT_NAME
})

const activeMenu = computed(() => {
  if (route.path.startsWith('/designer')) {
    return '/workflows'
  }
  return route.path
})

const displayName = computed(
  () => auth.user.value?.displayName || auth.user.value?.username || '用户',
)

const userInitial = computed(() => {
  const name = displayName.value.trim()
  return name ? name.slice(0, 1).toUpperCase() : 'U'
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
.backend-alert {
  margin-bottom: 14px;
}
</style>
