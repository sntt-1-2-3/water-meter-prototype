<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { Bell, ChatDotRound, DataAnalysis, Fold, Menu as MenuIcon, Odometer, Operation, SwitchButton, TrendCharts, UserFilled, Wallet, Warning, ChatLineRound } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import BrandMark from '../components/BrandMark.vue'
import AiAssistant from '../components/AiAssistant.vue'
import NotificationCenter from '../components/NotificationCenter.vue'
import { useSystemStore } from '../stores/system'
const route = useRoute()
const router = useRouter()
const store = useSystemStore()
const { role, title, sidebarCollapsed, mobileMenuVisible, aiVisible, notificationsVisible, currentUser, unreadCount } = storeToRefs(store)
const navItems = computed(() => {
  if (role.value === 'USER') return [{ path: '/user/home', label: '我的用水', icon: Odometer }]
  const items = [
    { path: '/admin/dashboard', label: '运营总览', icon: DataAnalysis },
    { path: '/admin/meters', label: '水表管理', icon: Odometer },
    { path: '/admin/reading', label: '抄表中心', icon: Operation },
    { path: '/admin/billing', label: '计费账单', icon: Wallet },
    { path: '/admin/anomalies', label: '异常中心', icon: Warning },
    { path: '/admin/reports', label: '统计报表', icon: TrendCharts },
  ]
  if (['ADMIN', 'OPERATOR', 'AUDITOR'].includes(role.value)) items.push({ path: '/admin/feedback', label: '居民反馈', icon: ChatLineRound })
  if (role.value === 'ADMIN') items.push({ path: '/admin/accounts', label: '账户绑定', icon: UserFilled })
  return items
})
const pageTitle = computed(() => String(route.meta.title || '智水云'))
const pageDescription = computed(() => String(route.meta.description || '水务抄表收费管理系统'))
function handleAccountCommand(command: string | number | object) {
  if (command !== 'logout') return
  ElMessageBox.confirm('是否退出当前账号？', '退出登录', { confirmButtonText: '退出登录', cancelButtonText: '取消' })
    .then(async () => { store.logout(); await router.replace('/login') }).catch(() => undefined)
}
let noticeTimer: ReturnType<typeof setInterval> | undefined
onMounted(() => {
  void store.loadNotifications().catch(() => undefined)
  noticeTimer = setInterval(() => { if (store.isAuthenticated) void store.loadNotifications().catch(() => undefined) }, 30000)
})
onBeforeUnmount(() => { if (noticeTimer) clearInterval(noticeTimer) })
</script>
<template>
  <div class="system-shell">
    <aside v-if="role !== 'USER'" class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="brand"><BrandMark /><div v-if="!sidebarCollapsed" class="brand-copy"><strong>智水云</strong><small>抄表收费管理系统</small></div></div>
      <el-menu :default-active="route.path" class="system-menu" :collapse="sidebarCollapsed" router><el-menu-item v-for="item in navItems" :key="item.path" :index="item.path"><el-icon><component :is="item.icon" /></el-icon><template #title>{{ item.label }}</template></el-menu-item></el-menu>
      <div class="sidebar-bottom"><button class="collapse-button" type="button" @click="sidebarCollapsed = !sidebarCollapsed"><el-icon><Fold v-if="!sidebarCollapsed" /><MenuIcon v-else /></el-icon><span v-if="!sidebarCollapsed">收起导航</span></button></div>
    </aside>
    <main class="main-area">
      <header class="topbar">
        <div class="topbar-left"><h1>{{ pageTitle }}</h1><p>{{ pageDescription }}</p></div>
        <div class="mobile-brand"><el-button v-if="role !== 'USER'" class="mobile-menu-button" circle :icon="MenuIcon" aria-label="打开菜单" @click="mobileMenuVisible = true" /><BrandMark /><strong>智水云</strong></div>
        <div class="topbar-actions">
          <el-tag class="identity-tag" :type="role === 'AUDITOR' ? 'info' : 'primary'" effect="light">{{ title }}</el-tag>
          <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="notice-badge"><el-button circle :icon="Bell" aria-label="打开通知中心" @click="notificationsVisible = true" /></el-badge>
          <el-dropdown trigger="click" @command="handleAccountCommand"><button class="account-button" type="button"><el-avatar :size="34"><el-icon><UserFilled /></el-icon></el-avatar><span class="account-copy"><strong>{{ currentUser?.name }}</strong><small>{{ title }}</small></span><span class="account-arrow">⌄</span></button><template #dropdown><el-dropdown-menu><el-dropdown-item disabled>{{ currentUser?.username }}</el-dropdown-item><el-dropdown-item command="logout" divided><el-icon><SwitchButton /></el-icon>退出登录</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
        </div>
      </header><router-view /><button class="ai-fab" type="button" aria-label="打开 AI 助手" @click="aiVisible = true"><el-icon><ChatDotRound /></el-icon><span>AI 助手</span></button>
    </main>
    <el-drawer v-model="mobileMenuVisible" title="系统导航" direction="ltr" size="270px"><el-menu :default-active="route.path" router @select="mobileMenuVisible = false"><el-menu-item v-for="item in navItems" :key="item.path" :index="item.path"><el-icon><component :is="item.icon" /></el-icon><span>{{ item.label }}</span></el-menu-item></el-menu></el-drawer>
    <AiAssistant /><NotificationCenter />
  </div>
</template>
