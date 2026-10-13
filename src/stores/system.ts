import { computed, markRaw, ref } from 'vue'
import { defineStore } from 'pinia'
import { CircleCheck, Coin, Odometer, Warning } from '@element-plus/icons-vue'
import { waterApi } from '../api/water'
import { clearSession, persistSession, readSession, readToken } from '../utils/session'
import { financeRoles, operationsRoles, roleLabels } from '../utils/permissions'
import type { Anomaly, BillBatch, BillItem, DashboardData, Feedback, Meter, Payment, PriceRule, ReadingTask, RegistrationPayload, Role, SystemNotification, UserSession } from '../types/domain'

export const useSystemStore = defineStore('system', () => {
  const session = ref<UserSession | null>(readToken() ? readSession() : null)
  const sessionReady = ref(false)
  let profileRequest: Promise<UserSession> | null = null
  const sidebarCollapsed = ref(false)
  const mobileMenuVisible = ref(false)
  const aiVisible = ref(false)
  const notificationsVisible = ref(false)
  const dashboard = ref<DashboardData>({ stats: [], trend: [], categories: [] })
  const meters = ref<Meter[]>([])
  const meterTotal = ref(0)
  const anomalies = ref<Anomaly[]>([])
  const bills = ref<BillBatch[]>([])
  const billItems = ref<BillItem[]>([])
  const payments = ref<Payment[]>([])
  const priceRules = ref<PriceRule[]>([])
  const tasks = ref<ReadingTask[]>([])
  const notifications = ref<SystemNotification[]>([])
  const feedback = ref<Feedback[]>([])
  const accounts = ref<UserSession[]>([])
  const role = computed<Role>(() => session.value?.role || 'USER')
  const currentUser = computed(() => session.value)
  const title = computed(() => roleLabels[role.value])
  const isAuthenticated = computed(() => Boolean(session.value && readToken()))
  const canOperate = computed(() => operationsRoles.includes(role.value))
  const canBill = computed(() => financeRoles.includes(role.value))
  const isManagement = computed(() => role.value !== 'USER')
  const isAdmin = computed(() => role.value === 'ADMIN')
  const isBound = computed(() => Boolean(session.value?.boundMeterId))
  const unreadCount = computed(() => notifications.value.filter((item) => !item.read).length)
  const stats = computed(() => dashboard.value.stats)
  const readingProgress = computed(() => tasks.value[0]?.progress || 0)
  const readingRunning = computed(() => tasks.value.some((task) => ['待执行', '执行中', '补抄中'].includes(task.status)))

  function resetData() {
    dashboard.value = { stats: [], trend: [], categories: [] }
    meters.value = []; meterTotal.value = 0; anomalies.value = []; bills.value = []
    billItems.value = []; payments.value = []; priceRules.value = []; tasks.value = []
    notifications.value = []; feedback.value = []; accounts.value = []
    aiVisible.value = false; notificationsVisible.value = false
  }
  function saveSession(profile: UserSession, token?: string) {
    session.value = profile
    sessionReady.value = true
    persistSession(profile, token)
  }
  async function login(username: string, password: string) {
    const { data } = await waterApi.login(username.trim(), password)
    const { token, ...profile } = data
    resetData()
    saveSession(profile, token)
    return profile
  }
  async function register(payload: RegistrationPayload) {
    const { data } = await waterApi.register({
      username: payload.username.trim(), name: payload.name.trim(), phone: payload.phone.trim(), password: payload.password,
    })
    const { token, ...profile } = data
    resetData()
    saveSession(profile, token)
    return profile
  }
  async function refreshSession() {
    if (profileRequest) return profileRequest
    profileRequest = waterApi.me().then(({ data }) => { saveSession(data); return data })
    try { return await profileRequest } finally { profileRequest = null }
  }
  function logout() {
    session.value = null
    sessionReady.value = false
    clearSession()
    resetData()
  }
  async function loadDashboard() {
    const { data } = await waterApi.getDashboard()
    const icons = [Odometer, CircleCheck, Coin, Warning]
    dashboard.value = { ...data, stats: data.stats.map((stat, index) => ({ ...stat, icon: markRaw(icons[index % icons.length]!) })) }
  }
  async function loadMeters(params?: { page: number; pageSize: number; keyword?: string; type?: string }) {
    const query = params || { page: 1, pageSize: 200 }
    const { data } = await waterApi.getMeters(query)
    let items = data.items
    if (!params && data.total > data.items.length) {
      const pages = await Promise.all(Array.from({ length: Math.ceil(data.total / 200) - 1 }, (_, index) => waterApi.getMeters({ page: index + 2, pageSize: 200 })))
      items = [...items, ...pages.flatMap(page => page.data.items)]
    }
    meters.value = items; meterTotal.value = data.total
  }
  async function saveMeter(meter: Meter, previousId?: string) {
    const request = { meterId: meter.id, userName: meter.user, area: meter.area, type: meter.type, reading: Number(meter.reading), status: meter.status }
    if (previousId) await waterApi.updateMeter(previousId, request)
    else await waterApi.createMeter(request)
  }
  async function loadTasks() { tasks.value = (await waterApi.getReadingTasks()).data }
  async function startReading(simulateFailure = false) { await waterApi.startReading(simulateFailure); await loadTasks() }
  async function retryReading(id: number) { await waterApi.retryReading(id); await loadTasks() }
  async function loadBills() {
    const [batchResult, itemResult, paymentResult, ruleResult] = await Promise.all([
      waterApi.getBills(), waterApi.getBillItems(), waterApi.getPayments(), waterApi.getPriceRules(),
    ])
    bills.value = batchResult.data; billItems.value = itemResult.data
    payments.value = paymentResult.data; priceRules.value = ruleResult.data
  }
  async function loadAnomalies() { anomalies.value = (await waterApi.getAnomalies()).data }
  async function advanceAnomaly(id: string) {
    const item = anomalies.value.find((entry) => entry.id === id)
    if (!item || item.status === '已完成') return
    await waterApi.updateAnomaly(id, item.status === '待处理' ? '处理中' : '已完成')
    await loadAnomalies()
  }
  async function loadNotifications() { notifications.value = (await waterApi.getNotifications()).data }
  async function markNotificationRead(id: number) { await waterApi.readNotification(id); await loadNotifications() }
  async function markAllNotificationsRead() { await waterApi.readAllNotifications(); await loadNotifications() }
  function isNotificationRead(id: number) { return notifications.value.find((item) => item.id === id)?.read ?? true }
  async function loadFeedback() { feedback.value = (await waterApi.getFeedback()).data }
  async function loadAccounts() { accounts.value = (await waterApi.getAccounts()).data }

  return {
    session, sessionReady, role, currentUser, title, isAuthenticated, canOperate, canBill, isManagement, isAdmin, isBound,
    sidebarCollapsed, mobileMenuVisible, aiVisible, notificationsVisible, notifications, unreadCount,
    dashboard, stats, meters, meterTotal, anomalies, bills, billItems, payments, priceRules, tasks, feedback, accounts,
    readingProgress, readingRunning, login, register, logout, refreshSession, loadDashboard, loadMeters, saveMeter,
    loadTasks, startReading, retryReading, loadBills, loadAnomalies, advanceAnomaly,
    loadNotifications, markNotificationRead, markAllNotificationsRead, isNotificationRead, loadFeedback, loadAccounts,
  }
})
