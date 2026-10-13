<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { Grid, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts/core'
import { LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { useSystemStore } from '../../stores/system'
import { dateTime, errorMessage, number } from '../../utils/format'
import RequestState from '../../components/RequestState.vue'
echarts.use([LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])
const router = useRouter()
const store = useSystemStore()
const { stats, tasks, anomalies, dashboard, canOperate } = storeToRefs(store)
const trendChartEl = ref<HTMLDivElement | null>(null)
const categoryChartEl = ref<HTMLDivElement | null>(null)
const loading = ref(false)
const error = ref('')
let trendChart: echarts.ECharts | null = null
let categoryChart: echarts.ECharts | null = null
const totalUsage = computed(() => dashboard.value.categories.reduce((sum, category) => sum + Number(category.value), 0))
const today = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'long' }).format(new Date())
function renderCharts() {
  if (trendChartEl.value) {
    trendChart ??= echarts.init(trendChartEl.value)
    const dates = dashboard.value.trendDates || dashboard.value.trend.map((_, index, array) => {
      const day = new Date(); day.setDate(day.getDate() - array.length + index + 1)
      return (day.getMonth() + 1) + '-' + day.getDate()
    })
    trendChart.setOption({
      tooltip: { trigger: 'axis' }, grid: { left: 18, right: 18, top: 24, bottom: 24, containLabel: true },
      xAxis: { type: 'category', data: dates }, yAxis: { type: 'value', name: 'm³' },
      series: [{ name: '用水量', type: 'line', smooth: true, data: dashboard.value.trend, lineStyle: { width: 3, color: '#087e8b' }, itemStyle: { color: '#087e8b' }, areaStyle: { color: 'rgba(8,126,139,.13)' } }],
    }, true)
  }
  if (categoryChartEl.value) {
    categoryChart ??= echarts.init(categoryChartEl.value)
    categoryChart.setOption({ tooltip: { trigger: 'item' }, legend: { bottom: 0 }, series: [{ type: 'pie', radius: ['48%', '70%'], center: ['50%', '44%'], data: dashboard.value.categories }] }, true)
  }
}
async function reload() {
  loading.value = true; error.value = ''
  try {
    await Promise.all([store.loadDashboard(), store.loadTasks(), store.loadAnomalies()])
    await nextTick(); renderCharts()
  } catch (e) { error.value = errorMessage(e) } finally { loading.value = false }
}
async function startReading() {
  try { await store.startReading(); await router.push('/admin/reading') } catch (e) { ElMessage.error(errorMessage(e)) }
}
function onResize() { trendChart?.resize(); categoryChart?.resize() }
onMounted(() => { void reload(); window.addEventListener('resize', onResize) })
onBeforeUnmount(() => { window.removeEventListener('resize', onResize); trendChart?.dispose(); categoryChart?.dispose() })
</script>
<template>
  <section class="workspace">
    <div class="page-toolbar"><div class="date-context"><strong>{{ today }}</strong><span>{{ store.currentUser?.areaScope || '当前角色授权范围' }}</span></div><div class="toolbar-actions"><el-button :icon="Refresh" :loading="loading" @click="reload">刷新数据</el-button><el-button v-if="canOperate" type="primary" :icon="Grid" :disabled="store.readingRunning" @click="startReading">启动自动抄表</el-button></div></div>
    <RequestState :loading="loading" :error="error" @retry="reload" />
    <div class="stat-grid"><article v-for="stat in stats" :key="stat.label" class="stat-card"><div class="stat-icon" :class="stat.tone"><el-icon><component :is="stat.icon" /></el-icon></div><div><span>{{ stat.label }}</span><strong>{{ stat.value }}</strong><small>{{ stat.note }}</small></div></article></div>
    <div class="dashboard-grid">
      <section class="panel trend-panel"><div class="panel-head"><div><h2>近期用水趋势</h2><p>当前授权范围的每日用水增量</p></div><el-tag effect="plain">单位：m³</el-tag></div><div ref="trendChartEl" class="chart-large" aria-label="每日用水趋势图"></div></section>
      <section class="panel category-panel"><div class="panel-head"><div><h2>用水结构</h2><p>按用户类型汇总</p></div></div><div ref="categoryChartEl" class="chart-small" aria-label="各类型用水占比图"></div><div class="category-total"><span>统计用水</span><strong>{{ number(totalUsage) }} <small>m³</small></strong></div></section>
      <section class="panel tasks-panel"><div class="panel-head"><div><h2>最近抄表任务</h2><p>后端执行状态</p></div><el-button text type="primary" @click="router.push('/admin/reading')">查看全部</el-button></div><div class="task-list"><div v-for="task in tasks.slice(0, 3)" :key="task.id || task.name" class="task-item"><div class="task-main"><strong>{{ task.name }}</strong><span>{{ task.scope }} · {{ dateTime(task.time) }}</span></div><div class="task-progress"><el-progress :percentage="task.progress" :stroke-width="7" :show-text="false" /><span>{{ task.progress }}%</span></div><el-tag>{{ task.status }}</el-tag></div><p v-if="!tasks.length" class="muted">暂无任务</p></div></section>
      <section class="panel alert-panel"><div class="panel-head"><div><h2>异常提醒</h2><p>当前授权范围</p></div><el-button text type="primary" @click="router.push('/admin/anomalies')">处理异常</el-button></div><div class="alert-list"><div v-for="item in anomalies.filter(a => a.status !== '已完成').slice(0, 3)" :key="item.id" class="alert-item"><span class="level-dot" :class="item.level"></span><div><strong>{{ item.meter }} · {{ item.type }}</strong><span>{{ item.area }} · {{ dateTime(item.time) }}</span></div><el-tag :type="item.level === '高' ? 'danger' : 'warning'">{{ item.level }}风险</el-tag></div><p v-if="!anomalies.some(a => a.status !== '已完成')" class="muted">暂无待处理异常</p></div></section>
    </div>
  </section>
</template>
