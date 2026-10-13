<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { Download, Operation } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useSystemStore } from '../../stores/system'
import { downloadCsv } from '../../utils/export'
import { dateTime, errorMessage } from '../../utils/format'
import RequestState from '../../components/RequestState.vue'
import { waterApi } from '../../api/water'
const store = useSystemStore()
const { readingProgress, readingRunning, tasks, canOperate } = storeToRefs(store)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const simulateFailure = ref(false)
const scheduler = ref({ enabled: false, intervalSeconds: 60 })
const schedulerSaving = ref(false)
let poll: ReturnType<typeof setTimeout> | undefined
let active = true
function schedulePoll() {
  if (poll) clearTimeout(poll)
  if (active && !error.value) poll = setTimeout(() => { void reload(false) }, readingRunning.value ? 1500 : 5000)
}
async function reload(showLoading = true) {
  if (showLoading) loading.value = true
  error.value = ''
  try {
    await Promise.all([store.loadTasks(), ...(store.isAdmin ? [waterApi.getScheduler().then(({ data }) => { scheduler.value = data })] : [])])
    schedulePoll()
  }
  catch (e) { error.value = errorMessage(e); if (poll) clearTimeout(poll) }
  finally { loading.value = false }
}
async function startTask() {
  if (saving.value || readingRunning.value) return
  saving.value = true
  try { await store.startReading(simulateFailure.value); error.value = ''; schedulePoll(); ElMessage.success('抄表任务已提交') }
  catch (e) { ElMessage.error(errorMessage(e)) } finally { saving.value = false }
}
async function retryTask(id?: number) {
  if (!id || saving.value) return
  saving.value = true
  try { await store.retryReading(id); error.value = ''; schedulePoll(); ElMessage.success('已提交失败补采') }
  catch (e) { ElMessage.error(errorMessage(e)) } finally { saving.value = false }
}
function exportTasks() {
  downloadCsv('抄表任务记录.csv', [['任务', '范围', '状态', '完成率', '总数', '成功', '漏抄', '失败', '重试次数', '开始时间'], ...tasks.value.map(t => [t.name, t.scope, t.status, t.progress, t.total || 0, t.success || 0, t.missed || 0, t.failed || 0, t.retryCount || 0, t.time])])
}
async function saveScheduler(enabled: boolean) {
  schedulerSaving.value = true
  try {
    scheduler.value = (await waterApi.updateScheduler(enabled)).data
    ElMessage.success(scheduler.value.enabled ? '定时采集已开启' : '定时采集已关闭')
    await reload()
  } catch (e) { ElMessage.error(errorMessage(e)) } finally { schedulerSaving.value = false }
}
onMounted(() => { void reload() })
onBeforeUnmount(() => { active = false; if (poll) clearTimeout(poll) })
</script>
<template>
  <section class="workspace">
    <section v-if="store.isAdmin" class="panel data-panel" style="margin-bottom:18px"><div class="panel-head"><div><h2>定时采集</h2><p>默认关闭。开启后由后端按照间隔启动采集任务，手动任务仍可独立执行。</p></div><el-tag :type="scheduler.enabled ? 'success' : 'info'">{{ scheduler.enabled ? '已开启' : '已关闭' }}</el-tag></div><div class="filter-row"><span>采集间隔：{{ scheduler.intervalSeconds }} 秒</span><el-button :type="scheduler.enabled ? 'danger' : 'primary'" :loading="schedulerSaving" :disabled="loading || Boolean(error)" @click="saveScheduler(!scheduler.enabled)">{{ scheduler.enabled ? '关闭定时采集' : '开启定时采集' }}</el-button></div></section>
    <div class="reading-hero"><div><span class="eyebrow">采集任务</span><h2>自动抄表与失败补采</h2><p>根据当前账号的区域权限采集，进度和结果由后端保存。</p></div><div v-if="canOperate"><label class="test-option"><input v-model="simulateFailure" type="checkbox" :disabled="readingRunning || saving"> 模拟一次采集失败</label><el-button size="large" type="primary" :loading="saving || readingRunning" :icon="Operation" @click="startTask">{{ readingRunning ? '正在执行' : '启动抄表' }}</el-button></div></div>
    <div class="page-toolbar"><span class="muted">{{ readingRunning ? '每 1.5 秒刷新任务状态' : '任务记录来自后端' }}</span><el-button :loading="loading" @click="reload()">刷新记录</el-button></div>
    <RequestState :loading="loading" :error="error" @retry="reload()" />
    <div v-if="tasks.length" class="reading-progress panel"><div class="progress-number"><strong>{{ readingProgress }}%</strong><span>{{ tasks[0]?.success || 0 }} / {{ tasks[0]?.total || 0 }} 台 · {{ tasks[0]?.status }}</span></div><el-progress :percentage="readingProgress" :stroke-width="14" :show-text="false" /></div>
    <section class="panel data-panel"><div class="panel-head"><div><h2>抄表任务记录</h2><p>失败任务可重试，审计和财务角色仅可查看</p></div><el-button :icon="Download" :disabled="!tasks.length" @click="exportTasks">导出 CSV</el-button></div>
      <el-table :data="tasks" empty-text="暂无抄表任务"><el-table-column prop="name" label="任务" min-width="180" /><el-table-column prop="scope" label="范围" min-width="150" /><el-table-column label="进度" width="150"><template #default="{ row }"><el-progress :percentage="row.progress" :stroke-width="7" /></template></el-table-column><el-table-column prop="status" label="状态" width="120" /><el-table-column prop="success" label="成功" width="65" /><el-table-column prop="missed" label="漏抄" width="65" /><el-table-column prop="failed" label="失败" width="65" /><el-table-column prop="retryCount" label="重试" width="65" /><el-table-column label="开始时间" min-width="150"><template #default="{ row }">{{ dateTime(row.time) }}</template></el-table-column><el-table-column v-if="canOperate" label="操作" width="100"><template #default="{ row }"><el-button v-if="row.status === '失败待重试'" link type="primary" :disabled="saving" @click="retryTask(row.id)">重试补采</el-button></template></el-table-column></el-table>
    </section>
  </section>
</template>
