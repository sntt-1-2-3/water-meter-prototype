<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import { useSystemStore } from '../../stores/system'
import { waterApi } from '../../api/water'
import { errorMessage } from '../../utils/format'
import { roleLabels } from '../../utils/permissions'
import type { UserSession } from '../../types/domain'
import RequestState from '../../components/RequestState.vue'
const store = useSystemStore()
const { accounts, meters } = storeToRefs(store)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const keyword = ref('')
const dialogVisible = ref(false)
const selected = ref<UserSession | null>(null)
const meterId = ref('')
const occupiedMeterIds = computed(() => new Set(accounts.value
  .filter(account => account.id !== selected.value?.id && account.boundMeterId)
  .map(account => account.boundMeterId)))
const meterOptions = computed(() => meters.value.map(meter => ({
  ...meter,
  occupied: occupiedMeterIds.value.has(meter.id),
  label: meter.id + ' · ' + meter.user + ' · ' + meter.area + (occupiedMeterIds.value.has(meter.id) ? ' · 已占用' : ''),
})))
const filtered = computed(() => accounts.value.filter(account => (account.username + account.name + (account.boundMeterId || '')).toLowerCase().includes(keyword.value.trim().toLowerCase())))
async function reload() {
  loading.value = true; error.value = ''
  try { await Promise.all([store.loadAccounts(), store.loadMeters()]) }
  catch (e) { error.value = errorMessage(e) } finally { loading.value = false }
}
function openBinding(account: UserSession) { selected.value = account; meterId.value = account.boundMeterId || ''; dialogVisible.value = true }
async function save() {
  if (!selected.value) return
  saving.value = true
  try {
    await waterApi.bindAccount(selected.value.id, meterId.value || null)
    dialogVisible.value = false; await reload(); await store.loadNotifications()
    ElMessage.success(meterId.value ? '水表绑定已保存，居民已收到通知' : '已解除水表绑定')
  } catch (e) { ElMessage.error(errorMessage(e)) } finally { saving.value = false }
}
onMounted(() => { void reload() })
</script>
<template>
  <section class="workspace"><section class="panel data-panel"><div class="table-toolbar"><div><h2>账户与水表绑定</h2><p class="muted">新注册居民默认未绑定，只能访问本人绑定水表的数据。</p></div><div class="filter-row"><el-input v-model="keyword" placeholder="账号、姓名或水表编号" clearable /><el-button :loading="loading" @click="reload">刷新</el-button></div></div>
    <RequestState :loading="loading" :error="error" @retry="reload" />
    <el-table v-if="!loading && !error" :data="filtered" empty-text="暂无账户"><el-table-column prop="username" label="账号" min-width="150" /><el-table-column prop="name" label="姓名" min-width="110" /><el-table-column prop="phone" label="手机号" min-width="140" /><el-table-column label="角色" width="110"><template #default="{ row }">{{ roleLabels[row.role as keyof typeof roleLabels] }}</template></el-table-column><el-table-column prop="areaScope" label="授权区域" min-width="180" /><el-table-column label="绑定水表" min-width="130"><template #default="{ row }">{{ row.boundMeterId || (row.role === 'USER' ? '待绑定' : '—') }}</template></el-table-column><el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '禁用' }}</el-tag></template></el-table-column><el-table-column label="操作" width="110"><template #default="{ row }"><el-button v-if="row.role === 'USER'" link type="primary" @click="openBinding(row)">绑定水表</el-button></template></el-table-column></el-table>
    <el-dialog v-model="dialogVisible" title="绑定居民水表" width="min(480px, 94vw)"><p>{{ selected?.name }}（{{ selected?.username }}）</p><el-select v-model="meterId" filterable clearable placeholder="选择水表；清空后解除绑定" style="width:100%"><el-option v-for="meter in meterOptions" :key="meter.id" :value="meter.id" :label="meter.label" :disabled="meter.occupied" /></el-select><template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存绑定</el-button></template></el-dialog>
  </section></section>
</template>
