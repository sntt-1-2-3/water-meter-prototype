<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import {
  Bell, ChatDotRound, DataAnalysis, Fold, Menu as MenuIcon, Odometer,
  Operation, TrendCharts, UserFilled, Wallet, Warning,
} from '@element-plus/icons-vue'
import BrandMark from '../components/BrandMark.vue'
import AiAssistant from '../components/AiAssistant.vue'
import { useSystemStore } from '../stores/system'
import type { Role } from '../types/domain'

const route = useRoute()
const router = useRouter()
const store = useSystemStore()
const { role, sidebarCollapsed, mobileMenuVisible, aiVisible, currentUser } = storeToRefs(store)

const navItems = [
  { path: '/admin/dashboard', label: '运营总览', icon: DataAnalysis },
  { path: '/admin/meters', label: '水表管理', icon: Odometer },
  { path: '/admin/reading', label: '抄表中心', icon: Operation },
  { path: '/admin/billing', label: '计费账单', icon: Wallet },
  { path: '/admin/anomalies', label: '异常中心', icon: Warning },
  { path: '/admin/reports', label: '统计报表', icon: TrendCharts },
]

const pageTitle = computed(() => String(route.meta.title || '智水云'))
const pageDescription = computed(() => String(route.meta.description || '水表抄表收费管理系统'))

function switchRole(nextRole: string | number | boolean | undefined) {
  const target = nextRole as Role
  store.setRole(target)
  router.push(target === 'admin' ? '/admin/dashboard' : '/user/home')
}

function closeMobileMenu() {
  mobileMenuVisible.value = false
}

watch(() => route.meta.role, (nextRole) => {
  if (nextRole === 'admin' || nextRole === 'user') store.setRole(nextRole)
}, { immediate: true })
</script>

<template>
  <div class="system-shell">
    <aside v-if="role === 'admin'" class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="brand"><BrandMark /><div v-if="!sidebarCollapsed" class="brand-copy"><strong>智水云</strong><small>抄表收费管理系统</small></div></div>
      <el-menu :default-active="route.path" class="system-menu" :collapse="sidebarCollapsed" router>
        <el-menu-item v-for="item in navItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon><template #title>{{ item.label }}</template>
        </el-menu-item>
      </el-menu>
      <div class="sidebar-bottom"><button class="collapse-button" type="button" @click="sidebarCollapsed = !sidebarCollapsed"><el-icon><Fold v-if="!sidebarCollapsed" /><MenuIcon v-else /></el-icon><span v-if="!sidebarCollapsed">收起导航</span></button></div>
    </aside>

    <main class="main-area">
      <header class="topbar">
        <div class="topbar-left">
          <h1>{{ pageTitle }}</h1><p>{{ pageDescription }}</p>
        </div>
        <div class="mobile-brand">
          <el-button v-if="role === 'admin'" class="mobile-menu-button" circle :icon="MenuIcon" aria-label="打开菜单" @click="mobileMenuVisible = true" />
          <BrandMark /><strong>智水云</strong>
        </div>
        <div class="topbar-actions">
          <el-radio-group :model-value="role" size="small" @change="switchRole">
            <el-radio-button value="admin">管理员端</el-radio-button><el-radio-button value="user">用户端</el-radio-button>
          </el-radio-group>
          <el-badge :value="3" class="notice-badge"><el-button circle :icon="Bell" aria-label="通知" /></el-badge>
          <div class="account"><el-avatar :size="34"><el-icon><UserFilled /></el-icon></el-avatar><div><strong>{{ currentUser.name }}</strong><small>{{ currentUser.title }}</small></div></div>
        </div>
      </header>

      <router-view />
      <button class="ai-fab" type="button" aria-label="打开 AI 助手" @click="aiVisible = true"><el-icon><ChatDotRound /></el-icon><span>AI 助手</span></button>
    </main>

    <el-drawer v-model="mobileMenuVisible" title="系统导航" direction="ltr" size="270px">
      <el-menu :default-active="route.path" router @select="closeMobileMenu">
        <el-menu-item v-for="item in navItems" :key="item.path" :index="item.path"><el-icon><component :is="item.icon" /></el-icon><span>{{ item.label }}</span></el-menu-item>
      </el-menu>
    </el-drawer>
    <AiAssistant />
  </div>
</template>
