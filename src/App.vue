<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import {
  Bell, ChatDotRound, CircleCheck, Coin, DataAnalysis, Document, Download,
  Fold, Grid, Menu as MenuIcon, Odometer, Operation, Refresh, Search,
  Setting, Tickets, TrendCharts, UserFilled, Wallet, Warning,
} from '@element-plus/icons-vue'

type WebMcpTool = {
  name: string
  title?: string
  description: string
  inputSchema: Record<string, unknown>
  annotations?: { readOnlyHint?: boolean; untrustedContentHint?: boolean }
  execute: (input: unknown) => unknown | Promise<unknown>
}

declare global {
  interface Document {
    modelContext?: { registerTool: (tool: WebMcpTool, options?: { signal?: AbortSignal }) => void | Promise<void> }
  }
}

type ViewKey = 'dashboard' | 'meters' | 'reading' | 'billing' | 'anomalies' | 'reports'
type Role = 'admin' | 'user'

const role = ref<Role>('admin')
const activeView = ref<ViewKey>('dashboard')
const sidebarCollapsed = ref(false)
const aiVisible = ref(false)
const aiQuestion = ref('')
const aiAnswer = ref('')
const aiThinking = ref(false)
const readingProgress = ref(86)
const readingRunning = ref(false)
const selectedMeterType = ref('全部类型')
const meterKeyword = ref('')
const anomalyLevel = ref('全部等级')

const navItems = [
  { key: 'dashboard', label: '运营总览', icon: DataAnalysis },
  { key: 'meters', label: '水表管理', icon: Odometer },
  { key: 'reading', label: '抄表中心', icon: Operation },
  { key: 'billing', label: '计费账单', icon: Wallet },
  { key: 'anomalies', label: '异常中心', icon: Warning },
  { key: 'reports', label: '统计报表', icon: TrendCharts },
] as const

const viewTitles: Record<ViewKey, { title: string; description: string }> = {
  dashboard: { title: '运营总览', description: '关键经营指标、抄表进度与异常情况' },
  meters: { title: '水表管理', description: '维护水表档案并查看设备在线状态' },
  reading: { title: '抄表中心', description: '自动抄表、漏抄补抄和任务执行记录' },
  billing: { title: '计费账单', description: '阶梯水价计算、账单生成和收费跟踪' },
  anomalies: { title: '异常中心', description: '识别高流量、零流量与设备故障并闭环处置' },
  reports: { title: '统计报表', description: '区域用水、收费率和设备运行分析' },
}

const stats = ref([
  { label: '水表总数', value: '1,000', note: '在线 986 台', icon: Odometer, tone: 'cyan' },
  { label: '今日抄表率', value: '98.6%', note: '较昨日 +1.2%', icon: CircleCheck, tone: 'green' },
  { label: '本月应收', value: '¥86,420', note: '已收 91.2%', icon: Coin, tone: 'blue' },
  { label: '待处理异常', value: '12', note: '高风险 3 条', icon: Warning, tone: 'orange' },
])

const meters = ref([
  { id: 'A-00218', user: '张晓明', area: '滨江花园', type: '居民', reading: 1286.4, status: '在线', updated: '2 分钟前' },
  { id: 'A-00308', user: '李文静', area: '滨江花园', type: '居民', reading: 892.7, status: '在线', updated: '3 分钟前' },
  { id: 'B-00126', user: '青禾餐饮', area: '中央商务区', type: '商业', reading: 5632.1, status: '在线', updated: '1 分钟前' },
  { id: 'B-00241', user: '澜庭酒店', area: '中央商务区', type: '商业', reading: 12450.8, status: '异常', updated: '8 分钟前' },
  { id: 'C-00037', user: '创智制造', area: '高新工业园', type: '工业', reading: 28641.5, status: '在线', updated: '4 分钟前' },
  { id: 'C-00112', user: '远航材料', area: '高新工业园', type: '工业', reading: 19540.2, status: '离线', updated: '2 小时前' },
])

const filteredMeters = computed(() => meters.value.filter((meter) => {
  const typeMatch = selectedMeterType.value === '全部类型' || meter.type === selectedMeterType.value
  const keyword = meterKeyword.value.trim().toLowerCase()
  return typeMatch && (!keyword || `${meter.id}${meter.user}${meter.area}`.toLowerCase().includes(keyword))
}))

const anomalies = ref([
  { id: 'AL-260919-038', meter: 'A-00038', area: '滨江花园', type: '持续高流量', level: '高', time: '09-19 08:42', status: '待处理' },
  { id: 'AL-260919-112', meter: 'C-00112', area: '高新工业园', type: '阀门故障 E03', level: '高', time: '09-19 08:31', status: '处理中' },
  { id: 'AL-260919-074', meter: 'B-00074', area: '中央商务区', type: '连续零流量', level: '中', time: '09-19 07:56', status: '待处理' },
  { id: 'AL-260918-206', meter: 'A-00206', area: '江南新城', type: '夜间小流量', level: '中', time: '09-18 23:18', status: '已完成' },
  { id: 'AL-260918-091', meter: 'A-00091', area: '滨江花园', type: '数据突变', level: '低', time: '09-18 18:05', status: '已完成' },
])

const filteredAnomalies = computed(() => anomalies.value.filter((item) => anomalyLevel.value === '全部等级' || item.level === anomalyLevel.value))

const bills = [
  { batch: '2026-09 居民用水', users: 628, amount: '¥32,680.40', paid: '94.6%', status: '收费中' },
  { batch: '2026-09 商业用水', users: 214, amount: '¥31,425.80', paid: '89.1%', status: '收费中' },
  { batch: '2026-09 工业用水', users: 158, amount: '¥22,313.80', paid: '87.5%', status: '收费中' },
]

const recentTasks = [
  { name: '09-19 日常抄表任务', scope: '全部区域 · 1,000 台', progress: 98.6, status: '执行完成', time: '09:00' },
  { name: '09-18 漏抄补采任务', scope: '滨江花园 · 14 台', progress: 100, status: '补采完成', time: '18:30' },
  { name: '09 月账单核算任务', scope: '居民 / 商业 / 工业', progress: 82, status: '计算中', time: '10:15' },
]

const trendChartEl = ref<HTMLDivElement | null>(null)
const categoryChartEl = ref<HTMLDivElement | null>(null)
let trendChart: echarts.ECharts | null = null
let categoryChart: echarts.ECharts | null = null
let webMcpLifecycle: AbortController | null = null

function renderCharts() {
  if (activeView.value !== 'dashboard') return
  nextTick(() => {
    if (trendChartEl.value) {
      trendChart?.dispose()
      trendChart = echarts.init(trendChartEl.value)
      trendChart.setOption({
        tooltip: { trigger: 'axis' },
        grid: { left: 18, right: 18, top: 24, bottom: 24, containLabel: true },
        xAxis: { type: 'category', boundaryGap: false, data: ['13日', '14日', '15日', '16日', '17日', '18日', '19日'], axisLine: { lineStyle: { color: '#d8e1e7' } }, axisLabel: { color: '#657786' } },
        yAxis: { type: 'value', name: 'm³', nameTextStyle: { color: '#657786' }, splitLine: { lineStyle: { color: '#edf1f4' } }, axisLabel: { color: '#657786' } },
        series: [{ name: '用水量', type: 'line', smooth: true, symbolSize: 7, data: [4860, 5120, 4980, 5460, 5280, 5910, 5630], lineStyle: { width: 3, color: '#087e8b' }, itemStyle: { color: '#087e8b' }, areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(8,126,139,.30)' }, { offset: 1, color: 'rgba(8,126,139,.02)' }]) } }],
      })
    }
    if (categoryChartEl.value) {
      categoryChart?.dispose()
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

function runReadingTask() {
  if (readingRunning.value) return
  readingRunning.value = true
  readingProgress.value = 0
  const timer = window.setInterval(() => {
    readingProgress.value = Math.min(100, readingProgress.value + Math.ceil(Math.random() * 9))
    if (readingProgress.value >= 100) {
      window.clearInterval(timer)
      readingRunning.value = false
      stats.value[1].value = '100%'
      stats.value[1].note = '1,000 台全部完成'
      ElMessage.success('抄表任务完成，已采集 1,000 条数据')
    }
  }, 280)
}

function calculateBills() {
  ElMessageBox.confirm('将按居民、商业、工业三类水价重新核算 1,000 户账单，是否继续？', '生成本月账单', {
    confirmButtonText: '开始核算', cancelButtonText: '取消', type: 'warning',
  }).then(() => ElMessage.success('账单核算任务已创建')).catch(() => undefined)
}

function resolveAnomaly(id: string) {
  const item = anomalies.value.find((entry) => entry.id === id)
  if (item) {
    item.status = item.status === '待处理' ? '处理中' : '已完成'
    ElMessage.success(`异常 ${id} 状态已更新`)
  }
}

const promptOptions = ['哪个小区本月用水量最高？', '列出高风险异常并说明原因', '生成本月运营摘要']

function askAi(question?: string) {
  const prompt = question || aiQuestion.value.trim()
  if (!prompt) return
  aiQuestion.value = prompt
  aiThinking.value = true
  aiAnswer.value = ''
  window.setTimeout(() => {
    aiThinking.value = false
    if (prompt.includes('小区')) aiAnswer.value = '本月用水量最高的是中央商务区，累计 42,680 m³，占全部用水量的 34%。主要来自澜庭酒店和青禾餐饮两户商业用户。'
    else if (prompt.includes('异常')) aiAnswer.value = '当前有 3 条高风险异常：A-00038 持续高流量、C-00112 阀门故障，以及 B-00241 瞬时流量突增。建议优先派单检查前两项。'
    else aiAnswer.value = '本月累计用水 125,420 m³，应收 ¥86,420，当前收费率 91.2%。抄表覆盖率 98.6%，待处理异常 12 条，整体运营稳定。'
  }, 650)
}

function exportReport(type: string) { ElMessage.success(`${type}报表已生成，原型中暂不下载真实文件`) }
function switchRole(nextRole: Role) { role.value = nextRole; if (nextRole === 'admin') activeView.value = 'dashboard' }
function selectView(key: string) { activeView.value = key as ViewKey }
function onRoleChange(value: string | number | boolean | undefined) { switchRole(value as Role) }
function onResize() { trendChart?.resize(); categoryChart?.resize() }

function registerWebMcpTools() {
  const context = document.modelContext
  if (!context?.registerTool) return
  webMcpLifecycle = new AbortController()
  const options = { signal: webMcpLifecycle.signal }
  const register = (tool: WebMcpTool) => void Promise.resolve(context.registerTool(tool, options)).catch(() => undefined)

  register({
    name: 'read_operation_summary',
    title: '读取水务运营摘要',
    description: '读取当前原型中展示的水表、抄表率、应收金额和异常数量。',
    inputSchema: { type: 'object', properties: {}, additionalProperties: false },
    annotations: { readOnlyHint: true, untrustedContentHint: false },
    execute: () => ({ meters: 1000, readingRate: stats.value[1].value, receivable: 86420, pendingAnomalies: 12 }),
  })

  register({
    name: 'navigate_system_module',
    title: '打开系统模块',
    description: '在管理员端打开指定业务模块；可选 dashboard、meters、reading、billing、anomalies、reports。',
    inputSchema: { type: 'object', properties: { module: { type: 'string', enum: ['dashboard', 'meters', 'reading', 'billing', 'anomalies', 'reports'] } }, required: ['module'], additionalProperties: false },
    annotations: { readOnlyHint: false, untrustedContentHint: false },
    async execute(input) {
      const module = (input as { module?: string })?.module
      if (!module || !Object.prototype.hasOwnProperty.call(viewTitles, module)) throw new Error('不支持的系统模块')
      role.value = 'admin'
      activeView.value = module as ViewKey
      await nextTick()
      return { role: role.value, module: activeView.value, title: viewTitles[activeView.value].title }
    },
  })

  register({
    name: 'start_meter_reading',
    title: '启动自动抄表',
    description: '打开抄表中心并启动当前模拟自动抄表任务。',
    inputSchema: { type: 'object', properties: {}, additionalProperties: false },
    annotations: { readOnlyHint: false, untrustedContentHint: false },
    async execute() {
      role.value = 'admin'
      activeView.value = 'reading'
      runReadingTask()
      await nextTick()
      return { status: 'started', progress: readingProgress.value, totalMeters: 1000 }
    },
  })
}

watch(activeView, renderCharts)
onMounted(() => { renderCharts(); registerWebMcpTools(); window.addEventListener('resize', onResize) })
onBeforeUnmount(() => { webMcpLifecycle?.abort(); window.removeEventListener('resize', onResize); trendChart?.dispose(); categoryChart?.dispose() })
</script>

<template>
  <div class="system-shell">
    <aside v-if="role === 'admin'" class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="brand">
        <div class="brand-mark"><span></span><span></span><span></span></div>
        <div v-if="!sidebarCollapsed" class="brand-copy"><strong>智水云</strong><small>抄表收费管理系统</small></div>
      </div>
      <el-menu :default-active="activeView" class="system-menu" :collapse="sidebarCollapsed" @select="selectView">
        <el-menu-item v-for="item in navItems" :key="item.key" :index="item.key">
          <el-icon><component :is="item.icon" /></el-icon><template #title>{{ item.label }}</template>
        </el-menu-item>
      </el-menu>
      <div class="sidebar-bottom"><button class="collapse-button" type="button" @click="sidebarCollapsed = !sidebarCollapsed"><el-icon><Fold v-if="!sidebarCollapsed" /><MenuIcon v-else /></el-icon><span v-if="!sidebarCollapsed">收起导航</span></button></div>
    </aside>

    <main class="main-area">
      <header class="topbar">
        <div class="mobile-brand"><div class="brand-mark"><span></span><span></span><span></span></div><strong>智水云</strong></div>
        <div class="topbar-left">
          <template v-if="role === 'admin'"><h1>{{ viewTitles[activeView].title }}</h1><p>{{ viewTitles[activeView].description }}</p></template>
          <template v-else><h1>我的用水</h1><p>滨江花园 2-1-1002 · 水表 A-00218</p></template>
        </div>
        <div class="topbar-actions">
          <el-radio-group :model-value="role" size="small" @change="onRoleChange"><el-radio-button value="admin">管理员端</el-radio-button><el-radio-button value="user">用户端</el-radio-button></el-radio-group>
          <el-badge :value="3" class="notice-badge"><el-button circle :icon="Bell" aria-label="通知" /></el-badge>
          <div class="account"><el-avatar :size="34"><el-icon><UserFilled /></el-icon></el-avatar><div><strong>{{ role === 'admin' ? '运营管理员' : '张晓明' }}</strong><small>{{ role === 'admin' ? '系统管理员' : '居民用户' }}</small></div></div>
        </div>
      </header>

      <section v-if="role === 'admin'" class="workspace">
        <template v-if="activeView === 'dashboard'">
          <div class="page-toolbar"><div class="date-context"><strong>2026 年 9 月 21 日</strong><span>数据每 5 分钟自动刷新</span></div><div class="toolbar-actions"><el-button :icon="Refresh">刷新数据</el-button><el-button type="primary" :icon="Grid" @click="runReadingTask">生成模拟数据</el-button></div></div>
          <div class="stat-grid"><article v-for="stat in stats" :key="stat.label" class="stat-card"><div class="stat-icon" :class="stat.tone"><el-icon><component :is="stat.icon" /></el-icon></div><div><span>{{ stat.label }}</span><strong>{{ stat.value }}</strong><small>{{ stat.note }}</small></div></article></div>
          <div class="dashboard-grid">
            <section class="panel trend-panel"><div class="panel-head"><div><h2>近 7 日用水趋势</h2><p>全部区域日累计用水量</p></div><el-tag effect="plain">单位：m³</el-tag></div><div ref="trendChartEl" class="chart-large" aria-label="近七日用水趋势图"></div></section>
            <section class="panel category-panel"><div class="panel-head"><div><h2>本月用水结构</h2><p>按用户类型统计</p></div></div><div ref="categoryChartEl" class="chart-small" aria-label="用户类型用水占比图"></div><div class="category-total"><span>累计用水</span><strong>125,420 <small>m³</small></strong></div></section>
            <section class="panel tasks-panel"><div class="panel-head"><div><h2>任务执行进度</h2><p>自动抄表与计费任务</p></div><el-button text type="primary" @click="activeView = 'reading'">查看全部</el-button></div><div class="task-list"><div v-for="task in recentTasks" :key="task.name" class="task-item"><div class="task-main"><strong>{{ task.name }}</strong><span>{{ task.scope }} · {{ task.time }}</span></div><div class="task-progress"><el-progress :percentage="task.progress" :stroke-width="7" :show-text="false" /><span>{{ task.progress }}%</span></div><el-tag :type="task.status.includes('完成') ? 'success' : 'primary'" effect="light">{{ task.status }}</el-tag></div></div></section>
            <section class="panel alert-panel"><div class="panel-head"><div><h2>异常待办</h2><p>按风险等级优先处理</p></div><el-button text type="primary" @click="activeView = 'anomalies'">进入中心</el-button></div><div class="alert-list"><div v-for="item in anomalies.slice(0, 3)" :key="item.id" class="alert-item"><span class="level-dot" :class="item.level"></span><div><strong>{{ item.meter }} · {{ item.type }}</strong><span>{{ item.area }} · {{ item.time }}</span></div><el-tag :type="item.level === '高' ? 'danger' : 'warning'" effect="plain">{{ item.level }}风险</el-tag></div></div></section>
          </div>
        </template>

        <template v-else-if="activeView === 'meters'">
          <section class="panel data-panel"><div class="table-toolbar"><div class="filter-row"><el-input v-model="meterKeyword" :prefix-icon="Search" clearable placeholder="搜索水表编号、用户或区域" /><el-select v-model="selectedMeterType" style="width:140px"><el-option v-for="type in ['全部类型','居民','商业','工业']" :key="type" :label="type" :value="type" /></el-select></div><el-button type="primary" :icon="Odometer">新增水表</el-button></div><el-table :data="filteredMeters" stripe><el-table-column prop="id" label="水表编号" min-width="120" /><el-table-column prop="user" label="用户名称" min-width="130" /><el-table-column prop="area" label="所属区域" min-width="140" /><el-table-column prop="type" label="用户类型" width="100" /><el-table-column label="累计流量" min-width="130"><template #default="scope">{{ scope.row.reading.toLocaleString() }} m³</template></el-table-column><el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="scope.row.status === '在线' ? 'success' : scope.row.status === '异常' ? 'danger' : 'info'">{{ scope.row.status }}</el-tag></template></el-table-column><el-table-column prop="updated" label="最近上报" min-width="110" /><el-table-column label="操作" width="150" fixed="right"><template #default><el-button link type="primary">详情</el-button><el-button link>编辑</el-button></template></el-table-column></el-table><div class="pagination-row"><span>共 1,000 台水表</span><el-pagination background layout="prev, pager, next" :total="1000" :page-size="10" /></div></section>
        </template>

        <template v-else-if="activeView === 'reading'">
          <div class="reading-hero"><div><span class="eyebrow">今日任务</span><h2>09-21 自动抄表任务</h2><p>覆盖全部区域 1,000 台水表，自动执行漏抄识别与补采。</p></div><el-button size="large" type="primary" :loading="readingRunning" :icon="Operation" @click="runReadingTask">{{ readingRunning ? '正在执行' : '立即执行' }}</el-button></div>
          <div class="reading-progress panel"><div class="progress-number"><strong>{{ readingProgress }}%</strong><span>{{ Math.round(readingProgress * 10) }} / 1,000 台</span></div><el-progress :percentage="readingProgress" :stroke-width="14" :show-text="false" /><div class="progress-steps"><span class="done">创建任务</span><span :class="{done:readingProgress>25}">采集数据</span><span :class="{done:readingProgress>70}">检测漏抄</span><span :class="{done:readingProgress===100}">完成入库</span></div></div>
          <section class="panel data-panel"><div class="panel-head"><div><h2>最近任务</h2><p>保留每次自动抄表的执行记录</p></div><el-button :icon="Download">导出记录</el-button></div><el-table :data="recentTasks"><el-table-column prop="name" label="任务名称" min-width="210" /><el-table-column prop="scope" label="任务范围" min-width="190" /><el-table-column label="完成率" width="160"><template #default="scope"><el-progress :percentage="scope.row.progress" :stroke-width="7" /></template></el-table-column><el-table-column prop="status" label="状态" width="110" /><el-table-column prop="time" label="开始时间" width="110" /></el-table></section>
        </template>

        <template v-else-if="activeView === 'billing'">
          <div class="tariff-grid"><article class="tariff-card"><span>居民生活用水</span><strong>¥2.80 <small>/ m³</small></strong><p>阶梯二 ¥3.60 · 阶梯三 ¥5.20</p></article><article class="tariff-card"><span>商业经营用水</span><strong>¥4.20 <small>/ m³</small></strong><p>按月用水量统一计费</p></article><article class="tariff-card"><span>工业生产用水</span><strong>¥5.10 <small>/ m³</small></strong><p>支持企业合同水价</p></article></div>
          <section class="panel data-panel"><div class="panel-head"><div><h2>2026 年 9 月账单批次</h2><p>共 1,000 户，预计应收 ¥86,420</p></div><el-button type="primary" :icon="Tickets" @click="calculateBills">重新核算账单</el-button></div><el-table :data="bills"><el-table-column prop="batch" label="账单批次" min-width="200" /><el-table-column prop="users" label="用户数" width="110" /><el-table-column prop="amount" label="应收金额" min-width="140" /><el-table-column prop="paid" label="收费率" width="120" /><el-table-column label="状态" width="110"><template #default="scope"><el-tag>{{ scope.row.status }}</el-tag></template></el-table-column><el-table-column label="操作" width="160"><template #default><el-button link type="primary">查看账单</el-button><el-button link>催缴</el-button></template></el-table-column></el-table></section>
        </template>

        <template v-else-if="activeView === 'anomalies'">
          <section class="panel data-panel"><div class="table-toolbar"><div class="filter-row"><el-select v-model="anomalyLevel" style="width:140px"><el-option v-for="level in ['全部等级','高','中','低']" :key="level" :label="level" :value="level" /></el-select><el-button :icon="Setting">规则配置</el-button></div><div class="risk-summary"><span><b>3</b> 高风险</span><span><b>7</b> 中风险</span><span><b>2</b> 低风险</span></div></div><el-table :data="filteredAnomalies" stripe><el-table-column prop="id" label="异常编号" min-width="150" /><el-table-column prop="meter" label="水表编号" width="110" /><el-table-column prop="area" label="区域" min-width="140" /><el-table-column prop="type" label="异常类型" min-width="150" /><el-table-column label="等级" width="90"><template #default="scope"><el-tag :type="scope.row.level === '高' ? 'danger' : scope.row.level === '中' ? 'warning' : 'info'">{{ scope.row.level }}</el-tag></template></el-table-column><el-table-column prop="time" label="发现时间" width="130" /><el-table-column prop="status" label="状态" width="100" /><el-table-column label="操作" width="120"><template #default="scope"><el-button link type="primary" @click="resolveAnomaly(scope.row.id)">{{ scope.row.status === '待处理' ? '开始处理' : '更新状态' }}</el-button></template></el-table-column></el-table></section>
        </template>

        <template v-else>
          <div class="report-grid"><article class="report-card"><div class="report-icon"><el-icon><Document /></el-icon></div><div><strong>水务运营日报</strong><span>用水量、抄表率、收费与异常汇总</span></div><el-button :icon="Download" @click="exportReport('运营日报')">导出 PDF</el-button></article><article class="report-card"><div class="report-icon blue"><el-icon><TrendCharts /></el-icon></div><div><strong>区域用水月报</strong><span>分区域、用户类型与同比趋势分析</span></div><el-button :icon="Download" @click="exportReport('区域月报')">导出 Excel</el-button></article><article class="report-card"><div class="report-icon orange"><el-icon><Warning /></el-icon></div><div><strong>设备异常报告</strong><span>异常分类、处置时效与设备故障率</span></div><el-button :icon="Download" @click="exportReport('异常报告')">导出 PDF</el-button></article></div>
          <section class="panel report-summary"><div class="panel-head"><div><h2>9 月运营摘要</h2><p>数据统计至 2026-09-21 10:30</p></div><el-tag type="success">运营稳定</el-tag></div><div class="summary-columns"><div><span>累计用水</span><strong>125,420 m³</strong><p>较上月同期增长 6.8%</p></div><div><span>累计收费</span><strong>¥78,816</strong><p>综合收费率 91.2%</p></div><div><span>设备健康度</span><strong>98.6%</strong><p>14 台设备需要关注</p></div><div><span>异常闭环率</span><strong>86.4%</strong><p>平均处置时长 3.2 小时</p></div></div></section>
        </template>
      </section>

      <section v-else class="user-portal">
        <div class="user-welcome"><div><span>下午好，张晓明</span><h2>本月用水保持平稳</h2><p>滨江花园 · 居民生活用水</p></div><div class="weather-mark"><span>供水正常</span><strong>24.6℃</strong><small>当前供水温度</small></div></div>
        <div class="user-primary-grid"><article class="bill-card"><span>2026 年 9 月待缴账单</span><strong>¥56.80</strong><p>用水量 18.6 m³ · 缴费截止 09-30</p><el-button color="#ffffff" @click="ElMessage.success('原型已进入缴费确认流程')">立即缴费</el-button></article><article class="usage-card panel"><div class="panel-head"><div><h2>本月用水</h2><p>较上月同期增加 8%</p></div><el-tag type="warning">略有上升</el-tag></div><div class="usage-number"><strong>18.6</strong><span>m³</span></div><div class="mini-bars"><i v-for="height in [32,44,38,52,47,62,58,70,65,76,68,82]" :key="height" :style="{height:`${height}%`}"></i></div></article></div>
        <div class="user-action-grid"><button type="button"><el-icon><TrendCharts /></el-icon><strong>用水趋势</strong><span>查看每日用水变化</span></button><button type="button"><el-icon><Tickets /></el-icon><strong>历史账单</strong><span>账单和缴费记录</span></button><button type="button"><el-icon><Warning /></el-icon><strong>异常反馈</strong><span>提交漏水或错抄问题</span></button><button type="button" @click="aiVisible=true"><el-icon><ChatDotRound /></el-icon><strong>AI 用水助手</strong><span>查询账单和节水建议</span></button></div>
        <div class="user-notice"><el-icon><Warning /></el-icon><div><strong>夜间小流量提醒</strong><p>系统检测到 9 月 18 日 02:00–04:00 有连续小流量用水，请检查水龙头或卫生间是否存在漏水。</p></div><el-button text type="primary">查看详情</el-button></div>
      </section>

      <button class="ai-fab" type="button" aria-label="打开 AI 助手" @click="aiVisible=true"><el-icon><ChatDotRound /></el-icon><span>AI 助手</span></button>
    </main>

    <el-drawer v-model="aiVisible" title="智水 AI 助手" size="420px" direction="rtl">
      <div class="ai-intro"><div class="ai-avatar"><el-icon><ChatDotRound /></el-icon></div><div><strong>你好，我可以查询系统数据</strong><p>回答会标注统计范围，不直接修改业务数据。</p></div></div>
      <div class="prompt-list"><button v-for="prompt in promptOptions" :key="prompt" type="button" @click="askAi(prompt)">{{ prompt }}</button></div>
      <div v-if="aiThinking || aiAnswer" class="ai-response"><div class="ai-avatar small"><el-icon><ChatDotRound /></el-icon></div><div><el-skeleton v-if="aiThinking" :rows="3" animated /><p v-else>{{ aiAnswer }}</p></div></div>
      <div class="ai-composer"><el-input v-model="aiQuestion" type="textarea" :rows="3" resize="none" placeholder="输入你想查询的问题……" @keydown.ctrl.enter="askAi()" /><div><span>Ctrl + Enter 发送</span><el-button type="primary" @click="askAi()">发送</el-button></div></div>
    </el-drawer>
  </div>
</template>
