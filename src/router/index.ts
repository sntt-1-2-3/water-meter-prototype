import { createRouter, createWebHistory } from 'vue-router'
import { useSystemStore } from '../stores/system'
import { homeFor, managementRoles } from '../utils/permissions'
import { readToken } from '../utils/session'
import type { Role } from '../types/domain'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/auth/LoginView.vue'), meta: { guestOnly: true } },
    { path: '/register', name: 'register', component: () => import('../views/auth/RegisterView.vue'), meta: { guestOnly: true } },
    {
      path: '/', component: () => import('../layouts/SystemLayout.vue'), meta: { requiresAuth: true },
      children: [
        { path: '', name: 'home', redirect: '/admin/dashboard' },
        { path: 'admin/dashboard', name: 'dashboard', component: () => import('../views/admin/DashboardView.vue'), meta: { roles: managementRoles, title: '运营总览', description: '权限范围内的经营指标、用水趋势和异常情况' } },
        { path: 'admin/meters', name: 'meters', component: () => import('../views/admin/MetersView.vue'), meta: { roles: managementRoles, title: '水表管理', description: '真实水表档案、读数和设备状态' } },
        { path: 'admin/reading', name: 'reading', component: () => import('../views/admin/ReadingView.vue'), meta: { roles: managementRoles, title: '抄表中心', description: '自动抄表进度、失败补采和执行记录' } },
        { path: 'admin/billing', name: 'billing', component: () => import('../views/admin/BillingView.vue'), meta: { roles: managementRoles, title: '计费账单', description: '后端计费规则、账单明细与缴费流水' } },
        { path: 'admin/anomalies', name: 'anomalies', component: () => import('../views/admin/AnomaliesView.vue'), meta: { roles: managementRoles, title: '异常中心', description: '异常告警与处理状态' } },
        { path: 'admin/reports', name: 'reports', component: () => import('../views/admin/ReportsView.vue'), meta: { roles: managementRoles, title: '统计报表', description: '当前授权数据的汇总及 CSV 导出' } },
        { path: 'admin/accounts', name: 'accounts', component: () => import('../views/admin/AccountsView.vue'), meta: { roles: ['ADMIN'], title: '账户绑定', description: '将居民账号绑定至对应水表' } },
        { path: 'admin/feedback', name: 'feedback', component: () => import('../views/admin/FeedbackView.vue'), meta: { roles: ['ADMIN', 'OPERATOR', 'AUDITOR'], title: '居民反馈', description: '反馈受理、结果回执与处理追踪' } },
        { path: 'user/home', name: 'user-home', component: () => import('../views/user/UserHomeView.vue'), meta: { roles: ['USER'], title: '我的用水', description: '个人水表、账单、缴费及反馈记录' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: () => ({ top: 0 }),
})
router.beforeEach(async (to) => {
  const store = useSystemStore()
  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth)
  if (!readToken()) {
    if (store.session) store.logout()
    if (requiresAuth) return { name: 'login', query: { redirect: to.fullPath } }
    return true
  }
  if (!store.sessionReady) {
    try { await store.refreshSession() } catch { store.logout(); return to.name === 'login' ? true : { name: 'login' } }
  }
  if (to.meta.guestOnly) return homeFor(store.role)
  const requiredRoles = to.meta.roles as Role[] | undefined
  if (requiredRoles && !requiredRoles.includes(store.role)) return homeFor(store.role)
  if (to.path === '/') return homeFor(store.role)
  return true
})
export default router
