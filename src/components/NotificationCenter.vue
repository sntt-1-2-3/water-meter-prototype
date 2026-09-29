<script setup lang="ts">
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { Bell, CircleCheck, InfoFilled, Warning } from '@element-plus/icons-vue'
import { useSystemStore } from '../stores/system'
import type { SystemNotification } from '../types/domain'

const router = useRouter()
const store = useSystemStore()
const { notificationsVisible, notifications, unreadCount } = storeToRefs(store)

const iconFor = (type: SystemNotification['type']) => type === 'warning' ? Warning : type === 'success' ? CircleCheck : InfoFilled

async function openNotification(item: SystemNotification) {
  store.markNotificationRead(item.id)
  notificationsVisible.value = false
  if (item.route) await router.push(item.route)
}
</script>

<template>
  <el-drawer v-model="notificationsVisible" title="通知中心" size="420px" direction="rtl">
    <div class="notification-toolbar"><span>共 {{ notifications.length }} 条通知，{{ unreadCount }} 条未读</span><el-button v-if="unreadCount" link type="primary" @click="store.markAllNotificationsRead">全部标为已读</el-button></div>
    <div class="notification-list">
      <button v-for="item in notifications" :key="item.id" class="notification-item" :class="{ unread: !store.isNotificationRead(item.id) }" type="button" @click="openNotification(item)">
        <span class="notification-icon" :class="item.type"><el-icon><component :is="iconFor(item.type)" /></el-icon></span>
        <span class="notification-copy"><strong>{{ item.title }}</strong><span>{{ item.content }}</span><small>{{ item.time }}</small></span>
        <i v-if="!store.isNotificationRead(item.id)" aria-label="未读"></i>
      </button>
      <div v-if="!notifications.length" class="notification-empty"><el-icon><Bell /></el-icon><strong>暂无通知</strong><span>系统消息会显示在这里</span></div>
    </div>
  </el-drawer>
</template>
