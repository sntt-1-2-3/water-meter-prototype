<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { Tickets } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useSystemStore } from '../../stores/system'
import { waterApi } from '../../api/water'
import type { BillItem, MeterType } from '../../types/domain'
import { errorMessage, money, paymentKey } from '../../utils/format'
import RequestState from '../../components/RequestState.vue'
import BillItemsTable from '../../components/BillItemsTable.vue'
import PaymentsTable from '../../components/PaymentsTable.vue'
const store = useSystemStore()
const { bills, billItems, payments, priceRules, canBill } = storeToRefs(store)
const loading = ref(false)
const generating = ref(false)
const error = ref('')
const selectedBatch = ref('')
const activeTab = ref('bills')
const payingId = ref<number | null>(null)
const generationVisible = ref(false)
const now = new Date()
const period = ref(now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0'))
const dueDate = ref('')
const meterTypes: MeterType[] = ['居民', '商业', '工业']
const filteredItems = computed(() => selectedBatch.value ? billItems.value.filter(item => item.batch === selectedBatch.value) : billItems.value)
const outstanding = computed(() => billItems.value.reduce((sum, item) => sum + Number(item.outstanding), 0))
function ruleText(type: MeterType) {
  return priceRules.value.filter(rule => rule.meterType === type).map(rule => {
    const range = rule.toUsage == null ? '超过 ' + rule.fromUsage : rule.fromUsage + '–' + rule.toUsage
    return range + ' m³：' + money(rule.unitPrice) + '/m³'
  }).join('；') || '暂无规则'
}
async function reload() {
  loading.value = true; error.value = ''
  try { await store.loadBills() } catch (e) { error.value = errorMessage(e) } finally { loading.value = false }
}
async function calculateBills() {
  if (!/^\d{4}-\d{2}$/.test(period.value)) return ElMessage.warning('请选择账期')
  generating.value = true
  try {
    await waterApi.recalculateBills(period.value, dueDate.value || undefined)
    generationVisible.value = false; selectedBatch.value = ''; await reload()
    ElMessage.success('账单已生成或返回已有账单，已缴记录保持不变')
  } catch (e) { ElMessage.error(errorMessage(e)) } finally { generating.value = false }
}
function showBatch(batch: string) { selectedBatch.value = batch; activeTab.value = 'bills' }
async function pay(item: BillItem) {
  try { await ElMessageBox.confirm('记录 ' + item.meterId + ' 的模拟缴费 ' + money(item.outstanding) + '？不会扣除真实资金。', '模拟缴费', { confirmButtonText: '确认记录', cancelButtonText: '取消' }) } catch { return }
  payingId.value = item.id
  try {
    const { data } = await waterApi.payBill(item.id, paymentKey(item.id))
    await reload(); ElMessage.success('缴费已保存，流水号 ' + data.transactionNo)
  } catch (e) { ElMessage.error(errorMessage(e)) } finally { payingId.value = null }
}
onMounted(() => { void reload() })
</script>
<template>
  <section class="workspace">
    <div class="tariff-grid"><article v-for="type in meterTypes" :key="type" class="tariff-card"><span>{{ type }}用水规则</span><strong>{{ money(priceRules.find(rule => rule.meterType === type)?.unitPrice) }} <small>/m³ 起</small></strong><p>{{ ruleText(type) }}</p></article></div>
    <div class="page-toolbar"><div><span class="muted">当前范围待缴合计</span><strong style="margin-left:12px">{{ money(outstanding) }}</strong></div><div class="toolbar-actions"><el-button :loading="loading" @click="reload">刷新</el-button><el-button v-if="canBill" type="primary" :icon="Tickets" @click="generationVisible = true">生成账单</el-button></div></div>
    <RequestState :loading="loading" :error="error" @retry="reload" />
    <section v-if="!loading && !error" class="panel data-panel"><div class="panel-head"><div><h2>账单批次</h2><p>按后端计费规则生成，账单快照与缴费记录可追溯</p></div></div><el-table :data="bills" empty-text="暂无账单批次"><el-table-column prop="batch" label="批次" min-width="170" /><el-table-column prop="users" label="用户数" width="100" /><el-table-column label="应收" min-width="120"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column><el-table-column prop="paid" label="收费率" width="100" /><el-table-column prop="status" label="状态" width="100" /><el-table-column label="操作" width="110"><template #default="{ row }"><el-button link type="primary" @click="showBatch(row.batch)">查看明细</el-button></template></el-table-column></el-table></section>
    <section v-if="!loading && !error" class="panel data-panel section-gap">
      <div class="panel-head"><div><h2>{{ activeTab === 'payments' ? '缴费流水' : '账单明细' }}</h2><p>模拟缴费记录保存在后端；相同支付请求不会重复记账</p></div><div class="toolbar-actions"><el-button :type="activeTab === 'bills' ? 'primary' : 'default'" @click="activeTab = 'bills'">账单</el-button><el-button :type="activeTab === 'payments' ? 'primary' : 'default'" @click="activeTab = 'payments'">流水</el-button></div></div>
      <div v-if="activeTab === 'bills'" class="filter-row" style="margin-bottom:16px"><el-select v-model="selectedBatch" clearable placeholder="全部批次" style="width:240px"><el-option v-for="batch in bills" :key="batch.batch" :label="batch.batch" :value="batch.batch" /></el-select></div>
      <BillItemsTable v-if="activeTab === 'bills'" :items="filteredItems" :allow-pay="canBill" :busy-id="payingId" @pay="pay" /><PaymentsTable v-else :items="payments" />
    </section>
    <el-dialog v-model="generationVisible" title="生成账单" width="min(460px, 94vw)"><el-form label-position="top"><el-form-item label="账期"><el-input v-model="period" type="month" /></el-form-item><el-form-item label="缴费截止日期（可选）"><el-input v-model="dueDate" type="date" /></el-form-item></el-form><p class="muted">使用当前采集读数生成账单快照。同一期已有账单时返回原账单，不覆盖已缴记录。</p><template #footer><el-button @click="generationVisible = false">取消</el-button><el-button type="primary" :loading="generating" @click="calculateBills">生成</el-button></template></el-dialog>
  </section>
</template>
