import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { initialAnomalies, initialBills, initialMeters, initialStats, initialTasks } from '../mocks/data'
import type { Meter, Role } from '../types/domain'

export const useSystemStore = defineStore('system', () => {
  const role = ref<Role>((localStorage.getItem('water-role') as Role) || 'admin')
  const sidebarCollapsed = ref(false)
  const mobileMenuVisible = ref(false)
  const aiVisible = ref(false)
  const stats = ref(initialStats.map((item) => ({ ...item })))
  const meters = ref(initialMeters.map((item) => ({ ...item })))
  const anomalies = ref(initialAnomalies.map((item) => ({ ...item })))
  const bills = ref(initialBills.map((item) => ({ ...item })))
  const tasks = ref(initialTasks.map((item) => ({ ...item })))
  const readingProgress = ref(86)
  const readingRunning = ref(false)

  const currentUser = computed(() => role.value === 'admin'
    ? { name: '运营管理员', title: '系统管理员' }
    : { name: '张晓明', title: '居民用户' })

  function setRole(nextRole: Role) {
    role.value = nextRole
    localStorage.setItem('water-role', nextRole)
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

  return {
    role, sidebarCollapsed, mobileMenuVisible, aiVisible, stats, meters, anomalies, bills, tasks,
    readingProgress, readingRunning, currentUser, setRole, saveMeter, startReading, advanceAnomaly,
  }
})
