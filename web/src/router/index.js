import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/login/LoginView.vue') },
  {
    path: '/',
    component: () => import('../layouts/BasicLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'dashboard', component: () => import('../views/dashboard/DashboardView.vue'), meta: { title: '总览' } },
      { path: 'projects', name: 'projects', component: () => import('../views/projects/ProjectListView.vue'), meta: { title: '项目列表' } },
      { path: 'projects/:id', name: 'project-detail', component: () => import('../views/projects/ProjectDetailView.vue'), meta: { title: '项目详情' } },
      { path: 'trends', name: 'trends', component: () => import('../views/trends/TrendsView.vue'), meta: { title: '历史趋势' } },
      { path: 'errors', name: 'errors', component: () => import('../views/errors/ErrorMonitorView.vue'), meta: { title: '错误监控' } },
      { path: 'alerts', name: 'alerts', component: () => import('../views/alerts/AlertLogView.vue'), meta: { title: '告警记录' } },
      { path: 'settings/projects', name: 'settings-projects', component: () => import('../views/settings/ProjectManageView.vue'), meta: { title: '项目管理' } },
      { path: 'settings/alert-rules', name: 'settings-rules', component: () => import('../views/settings/AlertRuleManageView.vue'), meta: { title: '告警规则' } }
    ]
  }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (to.name !== 'login' && !token) {
    return { name: 'login' }
  }
})

export default router
