<template>
  <div class="login-page">
    <div class="login-shell">
      <aside class="login-hero">
        <div class="hero-brand-block">
          <div class="hero-brand">{{ PRODUCT_NAME }}</div>
          <div class="hero-tagline">{{ PRODUCT_TAGLINE }}</div>
        </div>
        <h1>把接口编排成可调度、可开放的流程</h1>
        <p>组件接入 → 工作流试跑发布 → 定时或开放调用 → 运行结果可追溯</p>
        <ul>
          <li>凭证集中管理，密钥不进组件明文</li>
          <li>失败链路三段式定位</li>
          <li>角色权限控制菜单与写操作</li>
        </ul>
        <div class="hero-foot">{{ COMPANY_NAME }}</div>
      </aside>

      <section class="login-card">
        <div v-if="statusLoading" class="status-loading">正在连接服务…</div>
        <template v-else>
          <div class="card-head">
            <div class="brand-mobile">
              <div class="brand-mobile-name">{{ PRODUCT_NAME }}</div>
              <div class="brand-mobile-tagline">{{ PRODUCT_TAGLINE }}</div>
              <div class="brand-mobile-company">{{ COMPANY_NAME }}</div>
            </div>
            <h2>{{ bootstrapped === false ? '首次安装' : '登录控制台' }}</h2>
            <p class="sub">
              {{ bootstrapped === false ? '创建管理员账号后即可开始使用' : '使用管理员分配的账号登录' }}
            </p>
          </div>

          <el-form
            v-if="bootstrapped === false"
            ref="bootstrapRef"
            :model="bootstrapForm"
            :rules="bootstrapRules"
            label-position="top"
            @submit.prevent="onBootstrap"
          >
            <el-form-item label="用户名" prop="username">
              <el-input v-model="bootstrapForm.username" placeholder="例如 admin" autocomplete="username" size="large" />
            </el-form-item>
            <el-form-item label="显示名" prop="displayName">
              <el-input v-model="bootstrapForm.displayName" placeholder="管理员" size="large" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="bootstrapForm.password"
                type="password"
                show-password
                placeholder="至少 8 位"
                autocomplete="new-password"
                size="large"
              />
            </el-form-item>
            <el-button type="primary" native-type="submit" :loading="loading" size="large" class="submit-btn">
              创建并进入
            </el-button>
          </el-form>

          <el-form
            v-else
            ref="loginRef"
            :model="loginForm"
            :rules="loginRules"
            label-position="top"
            @submit.prevent="onLogin"
          >
            <el-form-item label="用户名" prop="username">
              <el-input
                v-model="loginForm.username"
                placeholder="用户名"
                autocomplete="username"
                size="large"
                @keyup.enter="onLogin"
              />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                show-password
                placeholder="密码"
                autocomplete="current-password"
                size="large"
                @keyup.enter="onLogin"
              />
            </el-form-item>
            <div class="row-between">
              <el-checkbox v-model="rememberUsername">记住用户名</el-checkbox>
            </div>
            <el-button type="primary" native-type="submit" :loading="loading" size="large" class="submit-btn">
              登录
            </el-button>
          </el-form>
        </template>
      </section>
    </div>

    <el-dialog v-model="forcePwdVisible" title="请先修改密码" width="420px" :close-on-click-modal="false" :show-close="false">
      <p class="hint">管理员要求你首次登录后修改密码，才能继续使用。</p>
      <el-form label-position="top">
        <el-form-item label="当前密码">
          <el-input v-model="forcePwd.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="forcePwd.newPassword" type="password" show-password placeholder="至少 8 位" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="forcePwdSaving" @click="onForceChangePassword">保存并进入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { bootstrap, bootstrapStatus, changePassword, login } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { COMPANY_NAME, PRODUCT_NAME, PRODUCT_TAGLINE } from '@/utils/brand'

const REMEMBER_KEY = 'qz_login_username'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)
const statusLoading = ref(true)
const bootstrapped = ref(null)
const rememberUsername = ref(!!localStorage.getItem(REMEMBER_KEY))
const loginRef = ref(null)
const bootstrapRef = ref(null)
const forcePwdVisible = ref(false)
const forcePwdSaving = ref(false)
const forcePwd = reactive({ oldPassword: '', newPassword: '' })

const loginForm = reactive({
  username: localStorage.getItem(REMEMBER_KEY) || '',
  password: '',
})
const bootstrapForm = reactive({ username: 'admin', displayName: '管理员', password: '' })

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}
const bootstrapRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  displayName: [{ required: true, message: '请输入显示名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, message: '密码至少 8 位', trigger: 'blur' },
  ],
}

async function loadStatus() {
  statusLoading.value = true
  try {
    const res = await bootstrapStatus()
    bootstrapped.value = !!res.data?.bootstrapped
  } catch {
    bootstrapped.value = true
  } finally {
    statusLoading.value = false
  }
}

async function enterApp(session) {
  auth.setSession(session)
  if (session.mustChangePassword) {
    forcePwd.oldPassword = loginForm.password
    forcePwd.newPassword = ''
    forcePwdVisible.value = true
    return
  }
  await router.replace(route.query.redirect || '/')
}

async function onLogin() {
  await loginRef.value?.validate?.().catch(() => Promise.reject())
  loading.value = true
  try {
    const res = await login(loginForm)
    if (rememberUsername.value) {
      localStorage.setItem(REMEMBER_KEY, loginForm.username.trim())
    } else {
      localStorage.removeItem(REMEMBER_KEY)
    }
    ElMessage.success('登录成功')
    await enterApp(res.data)
  } finally {
    loading.value = false
  }
}

async function onBootstrap() {
  await bootstrapRef.value?.validate?.().catch(() => Promise.reject())
  loading.value = true
  try {
    const res = await bootstrap(bootstrapForm)
    ElMessage.success('管理员已创建')
    await enterApp(res.data)
  } finally {
    loading.value = false
  }
}

async function onForceChangePassword() {
  if (!forcePwd.newPassword || forcePwd.newPassword.length < 8) {
    ElMessage.warning('新密码至少 8 位')
    return
  }
  forcePwdSaving.value = true
  try {
    await changePassword(forcePwd)
    auth.setSession({ ...auth.user.value, mustChangePassword: false, token: auth.getToken() })
    // refresh session flags
    const u = auth.user.value
    if (u) {
      auth.setSession({
        token: auth.getToken(),
        userId: u.userId,
        username: u.username,
        displayName: u.displayName,
        roles: u.roles,
        permissions: u.permissions,
        mustChangePassword: false,
      })
    }
    forcePwdVisible.value = false
    ElMessage.success('密码已更新')
    await router.replace(route.query.redirect || '/')
  } finally {
    forcePwdSaving.value = false
  }
}

onMounted(loadStatus)
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    radial-gradient(ellipse 80% 60% at 10% 20%, rgba(37, 99, 235, 0.35), transparent 55%),
    radial-gradient(ellipse 60% 50% at 90% 80%, rgba(14, 165, 233, 0.2), transparent 50%),
    linear-gradient(160deg, #0b1220 0%, #111827 50%, #1e293b 100%);
}
.login-shell {
  width: min(920px, 100%);
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  border-radius: 18px;
  overflow: hidden;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.35);
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: #fff;
}
.login-hero {
  padding: 40px 36px;
  color: #e2e8f0;
  background: linear-gradient(165deg, #0f172a 0%, #1e3a8a 100%);
  display: flex;
  flex-direction: column;
  min-height: 100%;
}
.hero-brand-block {
  margin-bottom: 28px;
}
.hero-brand {
  font-size: 28px;
  font-weight: 750;
  letter-spacing: 0.04em;
  color: #fff;
}
.hero-tagline {
  margin-top: 6px;
  font-size: 13px;
  color: #94a3b8;
}
.hero-company {
  margin-top: 8px;
  font-size: 12px;
  color: #64748b;
  letter-spacing: 0.02em;
}
.login-hero h1 {
  margin: 0 0 12px;
  font-size: 26px;
  line-height: 1.35;
  font-weight: 700;
  color: #fff;
}
.login-hero > p {
  margin: 0 0 24px;
  color: #cbd5e1;
  font-size: 14px;
  line-height: 1.6;
}
.login-hero ul {
  margin: 0;
  padding-left: 18px;
  color: #94a3b8;
  font-size: 13px;
  line-height: 1.9;
}
.hero-foot {
  margin-top: auto;
  padding-top: 28px;
  font-size: 11px;
  color: #64748b;
  letter-spacing: 0.02em;
}
.login-card {
  padding: 36px 32px 32px;
  background: #fff;
  display: flex;
  flex-direction: column;
}
.brand-mobile {
  display: none;
  margin-bottom: 12px;
}
.brand-mobile-name {
  font-size: 22px;
  font-weight: 750;
  color: #0f172a;
}
.brand-mobile-tagline {
  margin-top: 4px;
  font-size: 12px;
  color: #64748b;
}
.brand-mobile-company {
  margin-top: 4px;
  font-size: 12px;
  color: #94a3b8;
}
.card-head h2 {
  margin: 0 0 6px;
  font-size: 22px;
  color: #0f172a;
}
.sub {
  margin: 0 0 22px;
  color: #64748b;
  font-size: 13px;
}
.row-between {
  display: flex;
  justify-content: space-between;
  margin-bottom: 14px;
}
.submit-btn {
  width: 100%;
}
.status-loading {
  padding: 48px 0;
  text-align: center;
  color: #64748b;
}
.hint {
  margin: 0 0 12px;
  color: #64748b;
  font-size: 13px;
}
@media (max-width: 800px) {
  .login-shell {
    grid-template-columns: 1fr;
  }
  .login-hero {
    display: none;
  }
  .brand-mobile {
    display: block;
  }
}
</style>
