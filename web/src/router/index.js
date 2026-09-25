import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import { useAuthStore } from '@/stores/auth'
import { MENU_GROUPS } from '@/utils/menus'

const PATH_PERM = Object.fromEntries(
  MENU_GROUPS.flatMap((g) => g.items.map((i) => [i.path, i.perm])),
)

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
        { path: '', name: 'workbench', meta: { perm: 'menu:workbench' }, component: () => import('@/views/Workbench.vue') },
        { path: '/components', name: 'components', meta: { perm: 'menu:components' }, component: () => import('@/views/ComponentList.vue') },
        { path: '/workflows', name: 'workflows', meta: { perm: 'menu:workflows' }, component: () => import('@/views/WorkflowList.vue') },
        { path: '/schedules', name: 'schedules', meta: { perm: 'menu:schedules' }, component: () => import('@/views/ScheduleJobList.vue') },
        { path: '/credentials', name: 'credentials', meta: { perm: 'menu:credentials' }, component: () => import('@/views/CredentialList.vue') },
        { path: '/executions', name: 'executions', meta: { perm: 'menu:executions' }, component: () => import('@/views/ExecutionList.vue') },
        { path: '/problems', redirect: (to) => ({ path: '/executions', query: { ...to.query, filter: 'problem' } }) },
        { path: '/openapi', name: 'openapi', meta: { perm: 'menu:openapi' }, component: () => import('@/views/OpenApiAppList.vue') },
        { path: '/users', name: 'users', meta: { perm: 'menu:users' }, component: () => import('@/views/UserList.vue') },
        { path: '/roles', name: 'roles', meta: { perm: 'menu:roles' }, component: () => import('@/views/RoleList.vue') },
        { path: '/license', name: 'license', meta: { perm: 'menu:license' }, component: () => import('@/views/LicenseManage.vue') },
        { path: '/audit', name: 'audit', meta: { perm: 'menu:audit' }, component: () => import('@/views/AuditLogList.vue') },
        { path: '/settings', name: 'settings', meta: { perm: 'menu:settings' }, component: () => import('@/views/SystemSettings.vue') },
        {
          path: '/designer/:id?',
          name: 'designer',
          meta: { full: true, perm: 'menu:workflows' },
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
  const needPerm = to.meta.perm || PATH_PERM[to.path]
  if (needPerm && !auth.hasPermission(needPerm)) {
    return '/'
  }
  const needRoles = to.meta.roles
  if (needRoles?.length && !needRoles.some((r) => auth.hasRole(r))) {
    return '/'
  }
  return true
})

export default router
