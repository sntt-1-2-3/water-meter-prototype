import { createRouter, createWebHistory } from 'vue-router'
import type { Role, UserSession } from '../types/domain'

function readSession(): UserSession | null {
  try {
    const value = localStorage.getItem('water-session')
    return value ? JSON.parse(value) as UserSession : null
  } catch {
    return null
  }
}

function homeFor(role: Role) {
  return role === 'admin' ? '/admin/dashboard' : '/user/home'
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/auth/LoginView.vue'), meta: { guestOnly: true } },
    { path: '/register', name: 'register', component: () => import('../views/auth/RegisterView.vue'), meta: { guestOnly: true } },
    {
      path: '/',
      component: () => import('../layouts/SystemLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/admin/dashboard' },
        { path: 'admin/dashboard', name: 'dashboard', component: () => import('../views/admin/DashboardView.vue'), meta: { role: 'admin', title: '运营总览', description: '关键经营指标、抄表进度与异常情况' } },
        { path: 'admin/meters', name: 'meters', component: () => import('../views/admin/MetersView.vue'), meta: { role: 'admin', title: '水表管理', description: '维护水表档案并查看设备在线状态' } },
        { path: 'admin/reading', name: 'reading', component: () => import('../views/admin/ReadingView.vue'), meta: { role: 'admin', title: '抄表中心', description: '自动抄表、漏抄补抄和任务执行记录' } },
        { path: 'admin/billing', name: 'billing', component: () => import('../views/admin/BillingView.vue'), meta: { role: 'admin', title: '计费账单', description: '阶梯水价计算、账单生成和收费跟踪' } },
        { path: 'admin/anomalies', name: 'anomalies', component: () => import('../views/admin/AnomaliesView.vue'), meta: { role: 'admin', title: '异常中心', description: '识别高流量、零流量与设备故障并闭环处置' } },
        { path: 'admin/reports', name: 'reports', component: () => import('../views/admin/ReportsView.vue'), meta: { role: 'admin', title: '统计报表', description: '区域用水、收费率和设备运行分析' } },
        { path: 'user/home', name: 'user-home', component: () => import('../views/user/UserHomeView.vue'), meta: { role: 'user', title: '我的用水', description: '账单、用水趋势和服务提醒' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  const session = readSession()
  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth)
  const requiredRole = to.meta.role as Role | undefined

  if (requiresAuth && !session) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.meta.guestOnly && session) return homeFor(session.role)
  if (session && requiredRole && session.role !== requiredRole) return homeFor(session.role)
  if (to.path === '/' && session) return homeFor(session.role)
  return true
})

export default router
