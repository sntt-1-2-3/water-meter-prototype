<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { Bell, InfoFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useSystemStore } from '../stores/system'
import { waterApi } from '../api/water'
import { dateTime, errorMessage } from '../utils/format'
import type { SystemNotification } from '../types/domain'
import RequestState from './RequestState.vue'
const router = useRouter()
const store = useSystemStore()
const { notificationsVisible, notifications, unreadCount } = storeToRefs(store)
const loading = ref(false)
const error = ref('')
const saving = ref(false)
const detailVisible = ref(false)
const selected = ref<SystemNotification | null>(null)
async function reload() {
  loading.value = true; error.value = ''
  try { await store.loadNotifications() } catch (e) { error.value = errorMessage(e) } finally { loading.value = false }
}
watch(notificationsVisible, (visible) => { if (visible) void reload() })
async function openNotification(item: SystemNotification) {
  if (saving.value) return
  saving.value = true
  try {
    selected.value = (await waterApi.getNotification(item.id)).data
    await store.markNotificationRead(item.id)
    detailVisible.value = true
  } catch (e) { ElMessage.error(errorMessage(e)) } finally { saving.value = false }
}
async function readAll() {
  saving.value = true
  try { await store.markAllNotificationsRead() } catch (e) { ElMessage.error(errorMessage(e)) } finally { saving.value = false }
}
async function goToPage() {
  if (!selected.value?.route) return
  detailVisible.value = false; notificationsVisible.value = false
  await router.push(selected.value.route)
}
</script>
<template>
  <el-drawer v-model="notificationsVisible" title="通知中心" size="420px" direction="rtl">
    <div class="notification-toolbar"><span>共 {{ notifications.length }} 条，{{ unreadCount }} 条未读</span><el-button :loading="loading" link @click="reload">刷新</el-button><el-button v-if="unreadCount" :loading="saving" link type="primary" @click="readAll">全部已读</el-button></div>
    <RequestState :loading="loading" :error="error" @retry="reload" />
    <div v-if="!loading && !error" class="notification-list">
      <button v-for="item in notifications" :key="item.id" class="notification-item" :class="{ unread: !item.read }" :disabled="saving" type="button" @click="openNotification(item)">
        <span class="notification-icon"><el-icon><InfoFilled /></el-icon></span>
        <span class="notification-copy"><strong>{{ item.title }}</strong><span>{{ item.content }}</span><small>{{ dateTime(item.time) }}</small></span><i v-if="!item.read" aria-label="未读"></i>
      </button>
      <div v-if="!notifications.length" class="notification-empty"><el-icon><Bell /></el-icon><strong>暂无通知</strong><span>账单与处理结果会显示在这里</span></div>
    </div>
  </el-drawer>
  <el-dialog v-model="detailVisible" :title="selected?.title || '通知详情'" width="min(560px, 92vw)">
    <p>{{ selected?.content }}</p><p class="muted">{{ dateTime(selected?.time) }}</p>
    <template #footer><el-button @click="detailVisible = false">关闭</el-button><el-button v-if="selected?.route" type="primary" @click="goToPage">查看相关页面</el-button></template>
  </el-dialog>
</template>
