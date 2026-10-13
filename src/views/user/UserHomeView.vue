<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { ChatDotRound, Tickets, TrendCharts, Warning } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useSystemStore } from '../../stores/system'
import { waterApi } from '../../api/water'
import type { BillItem, UserSummary } from '../../types/domain'
import { dateTime, errorMessage, money, number, paymentKey } from '../../utils/format'
import RequestState from '../../components/RequestState.vue'
import BillItemsTable from '../../components/BillItemsTable.vue'
import PaymentsTable from '../../components/PaymentsTable.vue'
const store = useSystemStore()
const { currentUser, isBound, billItems, payments, anomalies, feedback: feedbackRecords, aiVisible } = storeToRefs(store)
const summary = ref<UserSummary | null>(null)
const loading = ref(false)
const error = ref('')
const saving = ref(false)
const payingId = ref<number | null>(null)
const panelVisible = ref(false)
const panelType = ref<'trend' | 'bills' | 'feedback' | 'history' | 'payments'>('bills')
const panelTitles = { trend: '近期用水趋势', bills: '个人账单', feedback: '提交异常反馈', history: '反馈处理记录', payments: '缴费流水' }
const feedback = reactive({ type: '疑似漏水', description: '' })
const pendingBill = computed(() => [...billItems.value].sort((a, b) => b.period.localeCompare(a.period)).find(item => Number(item.outstanding) > 0))
const trendMax = computed(() => Math.max(1, ...(summary.value?.trend.map(item => Number(item.usage)) || [])))
async function reload() {
  loading.value = true; error.value = ''
  try {
    await store.refreshSession()
    summary.value = (await waterApi.getUserSummary()).data
    if (isBound.value) await Promise.all([store.loadBills(), store.loadFeedback(), store.loadAnomalies()])
    else { store.billItems = []; store.payments = []; store.feedback = []; store.anomalies = [] }
  } catch (e) { error.value = errorMessage(e) } finally { loading.value = false }
}
function openPanel(type: typeof panelType.value) { panelType.value = type; panelVisible.value = true }
async function pay(item: BillItem) {
  if (payingId.value) return
  try { await ElMessageBox.confirm('记录 ' + item.period + ' 的模拟缴费 ' + money(item.outstanding) + '？不会扣除真实资金。', '模拟缴费', { confirmButtonText: '确认记录', cancelButtonText: '取消' }) } catch { return }
  payingId.value = item.id
  try {
    const { data } = await waterApi.payBill(item.id, paymentKey(item.id))
    await reload(); await store.loadNotifications()
    ElMessage.success('缴费已保存，流水号 ' + data.transactionNo)
  } catch (e) { ElMessage.error(errorMessage(e)) } finally { payingId.value = null }
}
async function submitFeedback() {
  if (feedback.description.trim().length < 5) return ElMessage.warning('问题描述至少需要 5 个字符')
  saving.value = true
  try {
    const { data } = await waterApi.createFeedback(feedback.type, feedback.description.trim())
    await store.loadFeedback()
    feedback.description = ''; panelType.value = 'history'
    ElMessage.success('反馈已保存，编号 ' + data.id)
  } catch (e) { ElMessage.error(errorMessage(e)) } finally { saving.value = false }
}
onMounted(() => { void reload() })
</script>
<template>
  <section class="user-portal">
    <div class="user-welcome"><div><span>居民用水服务</span><h2>你好，{{ currentUser?.name }}</h2><p>{{ summary?.address || '尚未绑定区域' }} · {{ summary?.meterId || '等待管理员绑定水表' }}</p></div><div class="weather-mark"><span>{{ isBound ? '累计水表读数' : '账户状态' }}</span><strong>{{ isBound ? number(summary?.reading) + ' m³' : '待绑定' }}</strong><small>{{ summary?.status || '请刷新查看最新状态' }}</small></div></div>
    <div class="page-toolbar"><span class="muted">仅展示当前账号绑定水表的数据</span><el-button :loading="loading" @click="reload">刷新个人数据</el-button></div>
    <RequestState :loading="loading" :error="error" @retry="reload" />
    <el-result v-if="!loading && !error && !isBound" icon="info" title="等待管理员绑定水表" sub-title="账号已创建。请联系管理员提供对应水表编号，绑定后刷新此页面即可查看个人用水和账单。" />
    <template v-if="!loading && !error && isBound">
      <div class="user-primary-grid"><article class="bill-card"><span>{{ pendingBill ? pendingBill.period + ' 待缴账单' : '账单已结清' }}</span><strong>{{ money(pendingBill?.outstanding) }}</strong><p v-if="pendingBill">用水 {{ number(pendingBill.usage) }} m³ · 截止 {{ pendingBill.dueDate }}</p><p v-else>暂无待缴账单；可查询历史明细</p><el-button color="#ffffff" :loading="Boolean(payingId)" :disabled="!pendingBill" @click="pendingBill && pay(pendingBill)">模拟缴费</el-button></article>
        <article class="usage-card panel"><div class="panel-head"><div><h2>当月用水</h2><p>后端账单及近期采集数据</p></div><el-tag>{{ summary?.status }}</el-tag></div><div class="usage-number"><strong>{{ number(summary?.usage) }}</strong><span>m³</span></div><div class="mini-bars" aria-label="近期每日用水"><i v-for="item in summary?.trend.slice(-14)" :key="item.date" :title="item.date + '：' + number(item.usage) + ' m³'" :style="{ height: Math.max(3, Number(item.usage) / trendMax * 100) + '%' }"></i></div><p class="muted">当月应收 {{ money(summary?.amount) }} · 已缴 {{ money(summary?.paid) }}</p></article>
      </div>
      <div class="user-action-grid">
        <button type="button" @click="openPanel('trend')"><el-icon><TrendCharts /></el-icon><strong>用水趋势</strong><span>近期每日用量</span></button>
        <button type="button" @click="openPanel('bills')"><el-icon><Tickets /></el-icon><strong>个人账单</strong><span>费用明细及缴费</span></button>
        <button type="button" @click="openPanel('feedback')"><el-icon><Warning /></el-icon><strong>异常反馈</strong><span>提交问题并跟踪结果</span></button>
        <button type="button" @click="aiVisible = true"><el-icon><ChatDotRound /></el-icon><strong>AI 用水助手</strong><span>查询本人用水与账单</span></button>
      </div>
      <section class="panel data-panel"><div class="panel-head"><div><h2>个人水表异常</h2><p>仅显示本人绑定水表的事件及处理状态</p></div><el-button @click="openPanel('feedback')">提交反馈</el-button></div><el-table :data="anomalies" empty-text="个人水表暂无异常事件"><el-table-column prop="type" label="异常类型" min-width="140" /><el-table-column prop="level" label="风险" width="80" /><el-table-column prop="status" label="状态" width="100" /><el-table-column prop="suggestion" label="处理建议" min-width="220" /><el-table-column label="时间" min-width="160"><template #default="{ row }">{{ dateTime(row.time) }}</template></el-table-column></el-table></section>
      <section class="panel data-panel section-gap"><div class="panel-head"><div><h2>最近反馈</h2><p>处理状态与结果由后台更新</p></div><div><el-button @click="openPanel('history')">全部反馈</el-button><el-button @click="openPanel('payments')">缴费流水</el-button></div></div><el-table :data="feedbackRecords.slice(0, 5)" empty-text="暂无反馈记录"><el-table-column prop="id" label="编号" width="80" /><el-table-column prop="type" label="类型" min-width="120" /><el-table-column prop="description" label="问题" min-width="200" /><el-table-column prop="status" label="状态" width="100" /><el-table-column prop="resolution" label="处理结果" min-width="200" /></el-table></section>
    </template>
    <el-dialog v-model="panelVisible" :title="panelTitles[panelType]" width="min(1100px, 94vw)">
      <BillItemsTable v-if="panelType === 'bills'" :items="billItems" allow-pay :busy-id="payingId" @pay="pay" />
      <PaymentsTable v-else-if="panelType === 'payments'" :items="payments" />
      <el-table v-else-if="panelType === 'trend'" :data="summary?.trend || []" empty-text="暂无采集趋势"><el-table-column prop="date" label="日期" /><el-table-column label="用水量"><template #default="{ row }">{{ number(row.usage) }} m³</template></el-table-column></el-table>
      <el-table v-else-if="panelType === 'history'" :data="feedbackRecords" empty-text="暂无反馈"><el-table-column prop="id" label="编号" width="80" /><el-table-column prop="type" label="类型" min-width="110" /><el-table-column prop="description" label="问题" min-width="180" /><el-table-column prop="status" label="状态" width="100" /><el-table-column prop="resolution" label="处理结果" min-width="200" /><el-table-column prop="handledBy" label="处理人" min-width="100" /><el-table-column label="更新时间" min-width="160"><template #default="{ row }">{{ dateTime(row.updatedAt) }}</template></el-table-column></el-table>
      <el-form v-else label-position="top"><el-form-item label="问题类型"><el-select v-model="feedback.type"><el-option v-for="type in ['疑似漏水', '读数异常', '账单疑问', '设备损坏']" :key="type" :value="type" /></el-select></el-form-item><el-form-item label="问题描述"><el-input v-model="feedback.description" type="textarea" :rows="4" :maxlength="2000" show-word-limit placeholder="描述发现问题的时间和现象" /></el-form-item></el-form>
      <template #footer><el-button @click="panelVisible = false">关闭</el-button><el-button v-if="panelType === 'feedback'" type="primary" :loading="saving" @click="submitFeedback">提交反馈</el-button></template>
    </el-dialog>
  </section>
</template>
