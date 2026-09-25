<template>
  <el-container class="qz-layout" direction="vertical">
    <el-header class="qz-header" height="56px">
      <div class="qz-brand">轻舟<small>低代码集成调度中台</small></div>
      <div class="qz-header-page">{{ currentTitle }}</div>
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
          <el-menu-item index="/">
            <el-icon><HomeFilled /></el-icon>
            <span>工作台</span>
          </el-menu-item>
          <div class="nav-group">编排</div>
          <el-menu-item index="/components">
            <el-icon><Grid /></el-icon>
            <span>接口组件</span>
          </el-menu-item>
          <el-menu-item index="/workflows">
            <el-icon><Share /></el-icon>
            <span>工作流编排</span>
          </el-menu-item>
          <el-menu-item index="/credentials">
            <el-icon><Key /></el-icon>
            <span>凭证管理</span>
          </el-menu-item>
          <div class="nav-group">运行</div>
          <el-menu-item index="/schedules">
            <el-icon><Timer /></el-icon>
            <span>定时调度</span>
          </el-menu-item>
          <el-menu-item index="/executions">
            <el-icon><List /></el-icon>
            <span>运行结果</span>
          </el-menu-item>
          <div class="nav-group">开放</div>
          <el-menu-item index="/openapi">
            <el-icon><Connection /></el-icon>
            <span>开放平台</span>
          </el-menu-item>
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
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Connection, Grid, HomeFilled, Key, List, Share, Timer } from '@element-plus/icons-vue'
import { backendUnreachable } from '@/api/http'

const TITLES = {
  '/': '工作台',
  '/components': '接口组件',
  '/workflows': '工作流编排',
  '/schedules': '定时调度',
  '/credentials': '凭证管理',
  '/executions': '运行结果',
  '/openapi': '开放平台',
}

const route = useRoute()

const currentTitle = computed(() => {
  if (route.path.startsWith('/designer')) {
    return '工作流设计器'
  }
  return TITLES[route.path] || '轻舟'
})

const activeMenu = computed(() => {
  if (route.path.startsWith('/designer')) {
    return '/workflows'
  }
  return route.path
})
</script>

<style scoped>
.el-header {
  padding: 0;
}
.qz-menu {
  border-right: none;
  padding: 10px 10px 16px;
}
.qz-menu :deep(.el-menu-item) {
  height: 40px;
  line-height: 40px;
  margin: 2px 0;
  border-radius: 8px;
}
.qz-menu :deep(.el-menu-item:hover) {
  background: var(--qz-aside-hover) !important;
  color: #e2e8f0;
}
.qz-menu :deep(.el-menu-item.is-active) {
  background: var(--qz-aside-active) !important;
  color: #fff;
  font-weight: 600;
}
.qz-main-full {
  padding: 0;
  overflow: hidden;
}
.qz-body {
  flex: 1;
  min-height: 0;
}
.backend-alert {
  margin-bottom: 14px;
}
.nav-group {
  padding: 16px 12px 6px;
  font-size: 11px;
  letter-spacing: 0.12em;
  color: #64748b;
}
</style>
