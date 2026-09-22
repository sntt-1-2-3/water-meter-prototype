<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { Grid, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts/core'
import { LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { useSystemStore } from '../../stores/system'

echarts.use([LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const router = useRouter()
const store = useSystemStore()
const { stats, tasks, anomalies } = storeToRefs(store)
const trendChartEl = ref<HTMLDivElement | null>(null)
const categoryChartEl = ref<HTMLDivElement | null>(null)
const refreshing = ref(false)
let trendChart: echarts.ECharts | null = null
let categoryChart: echarts.ECharts | null = null

const today = new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' }).format(new Date())

function renderCharts() {
  nextTick(() => {
    if (trendChartEl.value) {
      trendChart = echarts.init(trendChartEl.value)
      trendChart.setOption({
        tooltip: { trigger: 'axis' },
        grid: { left: 18, right: 18, top: 24, bottom: 24, containLabel: true },
        xAxis: { type: 'category', boundaryGap: false, data: ['16日', '17日', '18日', '19日', '20日', '21日', '22日'], axisLine: { lineStyle: { color: '#d8e1e7' } }, axisLabel: { color: '#657786' } },
        yAxis: { type: 'value', name: 'm³', nameTextStyle: { color: '#657786' }, splitLine: { lineStyle: { color: '#edf1f4' } }, axisLabel: { color: '#657786' } },
        series: [{ name: '用水量', type: 'line', smooth: true, symbolSize: 7, data: [4860, 5120, 4980, 5460, 5280, 5910, 5630], lineStyle: { width: 3, color: '#087e8b' }, itemStyle: { color: '#087e8b' }, areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(8,126,139,.30)' }, { offset: 1, color: 'rgba(8,126,139,.02)' }]) } }],
      })
    }
    if (categoryChartEl.value) {
      categoryChart = echarts.init(categoryChartEl.value)
      categoryChart.setOption({
        tooltip: { trigger: 'item' },
        legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { color: '#657786' } },
        series: [{ type: 'pie', radius: ['48%', '70%'], center: ['50%', '44%'], label: { show: false }, data: [
          { value: 38, name: '居民', itemStyle: { color: '#087e8b' } },
          { value: 34, name: '商业', itemStyle: { color: '#3f7cac' } },
          { value: 28, name: '工业', itemStyle: { color: '#f3a712' } },
        ] }],
      })
    }
  })
}

function refreshData() {
  refreshing.value = true
  window.setTimeout(() => {
    refreshing.value = false
    ElMessage.success('数据已刷新')
  }, 550)
}

function startReading() {
  store.startReading()
  router.push('/admin/reading')
}

function onResize() { trendChart?.resize(); categoryChart?.resize() }
onMounted(() => { renderCharts(); window.addEventListener('resize', onResize) })
onBeforeUnmount(() => { window.removeEventListener('resize', onResize); trendChart?.dispose(); categoryChart?.dispose() })
</script>

<template>
  <section class="workspace">
    <div class="page-toolbar">
      <div class="date-context"><strong>{{ today }}</strong><span>数据每 5 分钟自动刷新</span></div>
      <div class="toolbar-actions"><el-button :icon="Refresh" :loading="refreshing" @click="refreshData">刷新数据</el-button><el-button type="primary" :icon="Grid" @click="startReading">启动自动抄表</el-button></div>
    </div>
    <div class="stat-grid"><article v-for="stat in stats" :key="stat.label" class="stat-card"><div class="stat-icon" :class="stat.tone"><el-icon><component :is="stat.icon" /></el-icon></div><div><span>{{ stat.label }}</span><strong>{{ stat.value }}</strong><small>{{ stat.note }}</small></div></article></div>
    <div class="dashboard-grid">
      <section class="panel trend-panel"><div class="panel-head"><div><h2>近 7 日用水趋势</h2><p>全部区域日累计用水量</p></div><el-tag effect="plain">单位：m³</el-tag></div><div ref="trendChartEl" class="chart-large" aria-label="近七日用水趋势图"></div></section>
      <section class="panel category-panel"><div class="panel-head"><div><h2>本月用水结构</h2><p>按用户类型统计</p></div></div><div ref="categoryChartEl" class="chart-small" aria-label="用户类型用水占比图"></div><div class="category-total"><span>累计用水</span><strong>125,420 <small>m³</small></strong></div></section>
      <section class="panel tasks-panel"><div class="panel-head"><div><h2>任务执行进度</h2><p>自动抄表与计费任务</p></div><el-button text type="primary" @click="router.push('/admin/reading')">查看全部</el-button></div><div class="task-list"><div v-for="task in tasks.slice(0, 3)" :key="task.name" class="task-item"><div class="task-main"><strong>{{ task.name }}</strong><span>{{ task.scope }} · {{ task.time }}</span></div><div class="task-progress"><el-progress :percentage="task.progress" :stroke-width="7" :show-text="false" /><span>{{ task.progress }}%</span></div><el-tag :type="task.status.includes('完成') ? 'success' : 'primary'" effect="light">{{ task.status }}</el-tag></div></div></section>
      <section class="panel alert-panel"><div class="panel-head"><div><h2>异常待办</h2><p>按风险等级优先处理</p></div><el-button text type="primary" @click="router.push('/admin/anomalies')">进入中心</el-button></div><div class="alert-list"><div v-for="item in anomalies.slice(0, 3)" :key="item.id" class="alert-item"><span class="level-dot" :class="item.level"></span><div><strong>{{ item.meter }} · {{ item.type }}</strong><span>{{ item.area }} · {{ item.time }}</span></div><el-tag :type="item.level === '高' ? 'danger' : 'warning'" effect="plain">{{ item.level }}风险</el-tag></div></div></section>
    </div>
  </section>
</template>
