<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { Document, Download, TrendCharts, Warning } from '@element-plus/icons-vue'
import { useSystemStore } from '../../stores/system'
import { downloadCsv } from '../../utils/export'
import { errorMessage, money, number } from '../../utils/format'
import RequestState from '../../components/RequestState.vue'
const store = useSystemStore()
const { meters, billItems, anomalies } = storeToRefs(store)
const loading = ref(false)
const error = ref('')
const period = ref('')
const area = ref('')
const periods = computed(() => [...new Set(billItems.value.map(item => item.period))].sort().reverse())
const areas = computed(() => [...new Set([...meters.value.map(item => item.area), ...billItems.value.map(item => item.area), ...anomalies.value.map(item => item.area)])].filter(Boolean).sort())
const filteredBills = computed(() => billItems.value.filter(item => (!period.value || item.period === period.value) && (!area.value || item.area === area.value)))
const filteredMeters = computed(() => meters.value.filter(item => !area.value || item.area === area.value))
const filteredAnomalies = computed(() => anomalies.value.filter(item => (!area.value || item.area === area.value) && (!period.value || item.time.startsWith(period.value))))
const totalUsage = computed(() => filteredBills.value.reduce((sum, item) => sum + Number(item.usage), 0))
const totalDue = computed(() => filteredBills.value.reduce((sum, item) => sum + Number(item.totalDue), 0))
const totalPaid = computed(() => filteredBills.value.reduce((sum, item) => sum + Number(item.paidAmount), 0))
const grouped = computed(() => areas.value.filter(value => !area.value || value === area.value).map(value => {
  const items = filteredBills.value.filter(item => item.area === value)
  return { area: value, meters: filteredMeters.value.filter(item => item.area === value).length, usage: items.reduce((sum, item) => sum + Number(item.usage), 0), due: items.reduce((sum, item) => sum + Number(item.totalDue), 0), paid: items.reduce((sum, item) => sum + Number(item.paidAmount), 0), anomalies: filteredAnomalies.value.filter(item => item.area === value && item.status !== '已完成').length }
}))
async function reload() {
  loading.value = true; error.value = ''
  try { await Promise.all([store.loadMeters(), store.loadBills(), store.loadAnomalies()]) }
  catch (e) { error.value = errorMessage(e) } finally { loading.value = false }
}
function exportReport(type: 'operations' | 'usage' | 'anomalies') {
  const label = type === 'operations' ? '水务运营汇总' : type === 'usage' ? '区域用水账单' : '设备异常记录'
  let rows: Array<Array<string | number>>
  if (type === 'operations') rows = [['区域', '水表数', '计费用水m³', '应缴元', '已缴元', '待处理异常'], ...grouped.value.map(item => [item.area, item.meters, item.usage, item.due, item.paid, item.anomalies])]
  else if (type === 'usage') rows = [['账期', '水表', '用户', '区域', '类型', '用量m³', '水费元', '违约金元', '应缴元', '已缴元', '状态'], ...filteredBills.value.map(item => [item.period, item.meterId, item.userName, item.area, item.meterType, item.usage, item.waterFee, item.penalty, item.totalDue, item.paidAmount, item.status])]
  else rows = [['编号', '水表', '区域', '类型', '风险', '时间', '状态'], ...filteredAnomalies.value.map(item => [item.id, item.meter, item.area, item.type, item.level, item.time, item.status])]
  downloadCsv(label + '-' + (period.value || '全部账期') + '.csv', [['导出范围', area.value || '全部授权区域', '账期', period.value || '全部', '导出时间', new Date().toISOString()], ...rows])
}
onMounted(() => { void reload() })
</script>
<template>
  <section class="workspace">
    <div class="table-toolbar"><div class="filter-row"><el-select v-model="period" clearable placeholder="全部账期" style="width:160px"><el-option v-for="value in periods" :key="value" :value="value" /></el-select><el-select v-model="area" clearable placeholder="全部授权区域" style="width:200px"><el-option v-for="value in areas" :key="value" :value="value" /></el-select></div><el-button :loading="loading" @click="reload">刷新报表数据</el-button></div>
    <RequestState :loading="loading" :error="error" @retry="reload" />
    <template v-if="!loading && !error">
      <div class="report-grid">
        <article class="report-card"><div class="report-icon"><el-icon><Document /></el-icon></div><div><strong>水务运营汇总</strong><span>区域设备、计费用水、收费与异常汇总</span></div><el-button :icon="Download" @click="exportReport('operations')">导出 CSV</el-button></article>
        <article class="report-card"><div class="report-icon blue"><el-icon><TrendCharts /></el-icon></div><div><strong>区域用水账单</strong><span>逐表账单、阶梯计费金额和缴费状态</span></div><el-button :icon="Download" @click="exportReport('usage')">导出 CSV</el-button></article>
        <article class="report-card"><div class="report-icon orange"><el-icon><Warning /></el-icon></div><div><strong>设备异常记录</strong><span>异常分类、风险等级与处理状态</span></div><el-button :icon="Download" @click="exportReport('anomalies')">导出 CSV</el-button></article>
      </div>
      <section class="panel report-summary"><div class="panel-head"><div><h2>{{ period || '全部账期' }} · {{ area || '全部授权区域' }}</h2><p>用水量按账单快照汇总，设备数为当前档案。导出格式为 CSV。</p></div></div><div class="summary-columns"><div><span>计费用水</span><strong>{{ number(totalUsage) }} m³</strong></div><div><span>应缴合计</span><strong>{{ money(totalDue) }}</strong></div><div><span>已缴合计</span><strong>{{ money(totalPaid) }}</strong></div><div><span>收费率</span><strong>{{ totalDue > 0 ? (totalPaid / totalDue * 100).toFixed(1) : '0.0' }}%</strong></div></div></section>
      <section class="panel data-panel section-gap"><el-table :data="grouped" empty-text="暂无统计数据"><el-table-column prop="area" label="区域" min-width="150" /><el-table-column prop="meters" label="水表数" width="100" /><el-table-column label="计费用水" min-width="130"><template #default="{ row }">{{ number(row.usage) }} m³</template></el-table-column><el-table-column label="应缴" min-width="130"><template #default="{ row }">{{ money(row.due) }}</template></el-table-column><el-table-column label="已缴" min-width="130"><template #default="{ row }">{{ money(row.paid) }}</template></el-table-column><el-table-column prop="anomalies" label="待处理异常" width="120" /></el-table></section>
    </template>
  </section>
</template>
