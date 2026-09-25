import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      meta: { public: true },
      component: () => import('@/views/Login.vue'),
    },
    {
      path: '/',
      component: AppLayout,
      children: [
        { path: '', name: 'workbench', component: () => import('@/views/Workbench.vue') },
        { path: '/components', name: 'components', component: () => import('@/views/ComponentList.vue') },
        { path: '/workflows', name: 'workflows', component: () => import('@/views/WorkflowList.vue') },
        { path: '/schedules', name: 'schedules', component: () => import('@/views/ScheduleJobList.vue') },
        { path: '/credentials', name: 'credentials', component: () => import('@/views/CredentialList.vue') },
        { path: '/executions', name: 'executions', component: () => import('@/views/ExecutionList.vue') },
        { path: '/problems', redirect: (to) => ({ path: '/executions', query: { ...to.query, filter: 'problem' } }) },
        { path: '/openapi', name: 'openapi', component: () => import('@/views/OpenApiAppList.vue') },
        { path: '/users', name: 'users', meta: { roles: ['ADMIN'] }, component: () => import('@/views/UserList.vue') },
        { path: '/audit', name: 'audit', meta: { roles: ['ADMIN'] }, component: () => import('@/views/AuditLogList.vue') },
        { path: '/settings', name: 'settings', meta: { roles: ['ADMIN'] }, component: () => import('@/views/SystemSettings.vue') },
        {
          path: '/designer/:id?',
          name: 'designer',
          meta: { full: true },
          component: () => import('@/views/WorkflowDesigner.vue'),
        },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    if (auth.isLoggedIn.value && to.path === '/login') {
      return '/'
    }
    return true
  }
  if (!auth.isLoggedIn.value) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  const needRoles = to.meta.roles
  if (needRoles?.length && !needRoles.some((r) => auth.hasRole(r))) {
    return '/'
  }
  return true
})

export default router
