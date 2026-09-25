/**
 * 侧栏菜单定义：path 对应路由，perm 为 menu:* 权限编码。
 */
export const MENU_GROUPS = [
  {
    key: 'main',
    label: null,
    items: [
      { path: '/', title: '工作台', icon: 'HomeFilled', perm: 'menu:workbench' },
    ],
  },
  {
    key: 'orch',
    label: '编排',
    items: [
      { path: '/components', title: '接口组件', icon: 'Grid', perm: 'menu:components' },
      { path: '/workflows', title: '工作流编排', icon: 'Share', perm: 'menu:workflows' },
      { path: '/credentials', title: '凭证管理', icon: 'Key', perm: 'menu:credentials' },
    ],
  },
  {
    key: 'run',
    label: '运行',
    items: [
      { path: '/schedules', title: '定时调度', icon: 'Timer', perm: 'menu:schedules' },
      { path: '/executions', title: '运行结果', icon: 'List', perm: 'menu:executions' },
    ],
  },
  {
    key: 'open',
    label: '开放',
    items: [
      { path: '/openapi', title: '开放平台', icon: 'Connection', perm: 'menu:openapi' },
    ],
  },
  {
    key: 'sys',
    label: '系统',
    items: [
      { path: '/users', title: '用户管理', icon: 'User', perm: 'menu:users' },
      { path: '/roles', title: '角色权限', icon: 'Lock', perm: 'menu:roles' },
      { path: '/license', title: 'License', icon: 'Ticket', perm: 'menu:license' },
      { path: '/audit', title: '审计日志', icon: 'Document', perm: 'menu:audit' },
      { path: '/settings', title: '系统设置', icon: 'Setting', perm: 'menu:settings' },
    ],
  },
]

export const PAGE_TITLES = Object.fromEntries(
  MENU_GROUPS.flatMap((g) => g.items.map((i) => [i.path, i.title])),
)
