<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">轻舟</div>
      <p class="sub">{{ bootstrapped === false ? '首次安装：创建管理员账号' : '登录控制台' }}</p>

      <el-form v-if="bootstrapped === false" :model="bootstrapForm" @submit.prevent="onBootstrap">
        <el-form-item label="用户名">
          <el-input v-model="bootstrapForm.username" placeholder="admin" autocomplete="username" />
        </el-form-item>
        <el-form-item label="显示名">
          <el-input v-model="bootstrapForm.displayName" placeholder="管理员" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="bootstrapForm.password" type="password" show-password placeholder="至少 8 位" autocomplete="new-password" />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" style="width: 100%">创建并进入</el-button>
      </el-form>

      <el-form v-else :model="loginForm" @submit.prevent="onLogin">
        <el-form-item label="用户名">
          <el-input v-model="loginForm.username" placeholder="用户名" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="loginForm.password" type="password" show-password placeholder="密码" autocomplete="current-password" />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" style="width: 100%">登录</el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { bootstrap, bootstrapStatus, login } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)
const bootstrapped = ref(null)

const loginForm = reactive({ username: '', password: '' })
const bootstrapForm = reactive({ username: 'admin', displayName: '管理员', password: '' })

async function loadStatus() {
  try {
    const res = await bootstrapStatus()
    bootstrapped.value = !!res.data?.bootstrapped
  } catch {
    bootstrapped.value = true
  }
}

async function onLogin() {
  loading.value = true
  try {
    const res = await login(loginForm)
    auth.setSession(res.data)
    ElMessage.success('登录成功')
    await router.replace(route.query.redirect || '/')
  } finally {
    loading.value = false
  }
}

async function onBootstrap() {
  loading.value = true
  try {
    const res = await bootstrap(bootstrapForm)
    auth.setSession(res.data)
    ElMessage.success('管理员已创建')
    await router.replace('/')
  } finally {
    loading.value = false
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
  background: linear-gradient(160deg, #0f172a 0%, #1e293b 45%, #334155 100%);
  padding: 24px;
}
.login-card {
  width: 100%;
  max-width: 400px;
  background: #fff;
  border-radius: 14px;
  padding: 28px 28px 24px;
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.35);
}
.brand {
  font-size: 28px;
  font-weight: 750;
  color: #0f172a;
  letter-spacing: 0.02em;
}
.sub {
  margin: 6px 0 22px;
  color: #64748b;
  font-size: 13px;
}
</style>
