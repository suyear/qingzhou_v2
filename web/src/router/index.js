import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
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

export default router
