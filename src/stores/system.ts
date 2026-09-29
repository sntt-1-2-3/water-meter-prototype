import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { initialAnomalies, initialBills, initialMeters, initialStats, initialTasks } from '../mocks/data'
import type { Meter, RegistrationPayload, Role, SystemNotification, UserSession } from '../types/domain'

interface StoredAccount extends UserSession {
  password: string
}

const SESSION_KEY = 'water-session'
const USERS_KEY = 'water-registered-users'

const builtInAccounts: StoredAccount[] = [
  { id: 'admin-001', username: 'admin', password: 'admin123', name: '运营管理员', role: 'admin', title: '系统管理员' },
  { id: 'user-001', username: 'user', password: 'user123', name: '张晓明', phone: '13800000001', role: 'user', title: '居民用户' },
]

const initialNotifications: SystemNotification[] = [
  { id: 'notice-001', title: '3 条高风险异常待处理', content: '滨江花园持续高流量、高新工业园阀门故障等异常需要尽快处置。', time: '10 分钟前', type: 'warning', route: '/admin/anomalies', roles: ['admin'] },
  { id: 'notice-002', title: '自动抄表任务即将开始', content: '今日 09:00 自动抄表任务覆盖全部区域 1,000 台水表。', time: '35 分钟前', type: 'info', route: '/admin/reading', roles: ['admin'] },
  { id: 'notice-003', title: '9 月账单核算完成', content: '本月 1,000 户账单已经生成，预计应收 ¥86,420。', time: '1 小时前', type: 'success', route: '/admin/billing', roles: ['admin'] },
  { id: 'notice-101', title: '夜间小流量提醒', content: '9 月 18 日 02:00–04:00 检测到连续小流量，请检查水龙头和室内管道。', time: '昨天 08:30', type: 'warning', route: '/user/home', roles: ['user'] },
  { id: 'notice-102', title: '9 月水费账单已生成', content: '本月用水量 18.6 m³，应缴 ¥56.80，缴费截止日期为 9 月 30 日。', time: '09-21 10:00', type: 'info', route: '/user/home', roles: ['user'] },
]

function readJson<T>(key: string, fallback: T): T {
  try {
    const value = localStorage.getItem(key)
    return value ? JSON.parse(value) as T : fallback
  } catch {
    return fallback
  }
}

export const useSystemStore = defineStore('system', () => {
  const session = ref<UserSession | null>(readJson<UserSession | null>(SESSION_KEY, null))
  const registeredUsers = ref<StoredAccount[]>(readJson<StoredAccount[]>(USERS_KEY, []))
  const sidebarCollapsed = ref(false)
  const mobileMenuVisible = ref(false)
  const aiVisible = ref(false)
  const notificationsVisible = ref(false)
  const stats = ref(initialStats.map((item) => ({ ...item })))
  const meters = ref(initialMeters.map((item) => ({ ...item })))
  const anomalies = ref(initialAnomalies.map((item) => ({ ...item })))
  const bills = ref(initialBills.map((item) => ({ ...item })))
  const tasks = ref(initialTasks.map((item) => ({ ...item })))
  const readingProgress = ref(86)
  const readingRunning = ref(false)
  const readNotificationIds = ref<string[]>([])

  const role = computed<Role>(() => session.value?.role || 'user')
  const currentUser = computed(() => session.value)
  const isAuthenticated = computed(() => Boolean(session.value))
  const notifications = computed(() => initialNotifications.filter((item) => item.roles.includes(role.value)))
  const unreadCount = computed(() => notifications.value.filter((item) => !readNotificationIds.value.includes(item.id)).length)

  function readNotificationKey(username = session.value?.username) {
    return username ? `water-read-notifications-${username}` : 'water-read-notifications-guest'
  }

  function loadNotificationState() {
    readNotificationIds.value = readJson<string[]>(readNotificationKey(), [])
  }

  function saveSession(user: UserSession) {
    session.value = user
    localStorage.setItem(SESSION_KEY, JSON.stringify(user))
    loadNotificationState()
  }

  function login(username: string, password: string) {
    const account = [...builtInAccounts, ...registeredUsers.value].find((item) => item.username === username.trim() && item.password === password)
    if (!account) throw new Error('账号或密码错误')
    const { password: _password, ...user } = account
    saveSession(user)
    return user
  }

  function register(payload: RegistrationPayload) {
    const username = payload.username.trim()
    if ([...builtInAccounts, ...registeredUsers.value].some((item) => item.username === username)) throw new Error('该账号已被注册')
    const account: StoredAccount = {
      id: `user-${Date.now()}`,
      username,
      password: payload.password,
      name: payload.name.trim(),
      phone: payload.phone.trim(),
      role: 'user',
      title: '居民用户',
    }
    registeredUsers.value.push(account)
    localStorage.setItem(USERS_KEY, JSON.stringify(registeredUsers.value))
    const { password: _password, ...user } = account
    saveSession(user)
    return user
  }

  function logout() {
    session.value = null
    readNotificationIds.value = []
    localStorage.removeItem(SESSION_KEY)
  }

  function markNotificationRead(id: string) {
    if (!readNotificationIds.value.includes(id)) {
      readNotificationIds.value.push(id)
      localStorage.setItem(readNotificationKey(), JSON.stringify(readNotificationIds.value))
    }
  }

  function markAllNotificationsRead() {
    readNotificationIds.value = notifications.value.map((item) => item.id)
    localStorage.setItem(readNotificationKey(), JSON.stringify(readNotificationIds.value))
  }

  function isNotificationRead(id: string) {
    return readNotificationIds.value.includes(id)
  }

  function saveMeter(meter: Meter, previousId?: string) {
    const index = previousId ? meters.value.findIndex((item) => item.id === previousId) : -1
    if (index >= 0) meters.value[index] = { ...meter, updated: '刚刚' }
    else meters.value.unshift({ ...meter, updated: '刚刚' })
  }

  function startReading() {
    if (readingRunning.value) return
    readingRunning.value = true
    readingProgress.value = 0
    const timer = window.setInterval(() => {
      readingProgress.value = Math.min(100, readingProgress.value + Math.ceil(Math.random() * 9))
      if (readingProgress.value >= 100) {
        window.clearInterval(timer)
        readingRunning.value = false
        stats.value[1].value = '100%'
        stats.value[1].note = '1,000 台全部完成'
        tasks.value.unshift({ name: '即时自动抄表任务', scope: '全部区域 · 1,000 台', progress: 100, status: '执行完成', time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) })
      }
    }, 260)
  }

  function advanceAnomaly(id: string) {
    const item = anomalies.value.find((entry) => entry.id === id)
    if (!item) return
    item.status = item.status === '待处理' ? '处理中' : '已完成'
  }

  if (session.value) loadNotificationState()

  return {
    session, role, currentUser, isAuthenticated, sidebarCollapsed, mobileMenuVisible, aiVisible,
    notificationsVisible, notifications, unreadCount, stats, meters, anomalies, bills, tasks,
    readingProgress, readingRunning, login, register, logout, markNotificationRead,
    markAllNotificationsRead, isNotificationRead, saveMeter, startReading, advanceAnomaly,
  }
})
