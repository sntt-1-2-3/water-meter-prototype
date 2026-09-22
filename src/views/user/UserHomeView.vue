<script setup lang="ts">
import { reactive, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { ChatDotRound, Tickets, TrendCharts, Warning } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useSystemStore } from '../../stores/system'

const store = useSystemStore()
const { aiVisible } = storeToRefs(store)
const panelVisible = ref(false)
const panelType = ref<'trend' | 'bills' | 'feedback' | 'notice'>('trend')
const panelTitles = { trend: '近 7 日用水趋势', bills: '历史账单', feedback: '提交异常反馈', notice: '异常提醒详情' }
const dailyUsage = [
  { date: '09-16', usage: 0.56 }, { date: '09-17', usage: 0.61 }, { date: '09-18', usage: 0.83 },
  { date: '09-19', usage: 0.72 }, { date: '09-20', usage: 0.65 }, { date: '09-21', usage: 0.78 }, { date: '09-22', usage: 0.69 },
]
const historyBills = [
  { month: '2026-09', usage: '18.6 m³', amount: '¥56.80', status: '待缴费' },
  { month: '2026-08', usage: '17.2 m³', amount: '¥52.88', status: '已缴费' },
  { month: '2026-07', usage: '15.9 m³', amount: '¥49.24', status: '已缴费' },
]
const feedback = reactive({ type: '疑似漏水', description: '' })

function pay() {
  ElMessageBox.confirm('确认支付 2026 年 9 月水费 ¥56.80？', '在线缴费', { confirmButtonText: '确认支付', cancelButtonText: '取消', type: 'info' })
    .then(() => ElMessage.success('模拟支付成功，电子票据已生成'))
    .catch(() => undefined)
}

function openPanel(type: typeof panelType.value) { panelType.value = type; panelVisible.value = true }
function submitFeedback() {
  if (!feedback.description.trim()) return ElMessage.warning('请填写问题描述')
  panelVisible.value = false
  feedback.description = ''
  ElMessage.success('反馈已提交，工作人员将在 24 小时内处理')
}
</script>

<template>
  <section class="user-portal">
    <div class="user-welcome"><div><span>居民用水服务</span><h2>晚上好，张晓明</h2><p>滨江花园 · 水表 A-00218 · 设备在线</p></div><div class="weather-mark"><span>本月节水评级</span><strong>A</strong><small>超过 82% 的同小区用户</small></div></div>
    <div class="user-primary-grid"><article class="bill-card"><span>2026 年 9 月待缴账单</span><strong>¥56.80</strong><p>用水量 18.6 m³ · 缴费截止 09-30</p><el-button color="#ffffff" @click="pay">立即缴费</el-button></article><article class="usage-card panel"><div class="panel-head"><div><h2>本月用水</h2><p>较上月同期增加 8%</p></div><el-tag type="warning">略有上升</el-tag></div><div class="usage-number"><strong>18.6</strong><span>m³</span></div><div class="mini-bars"><i v-for="height in [32,44,38,52,47,62,58,70,65,76,68,82]" :key="height" :style="{ height: `${height}%` }"></i></div></article></div>
    <div class="user-action-grid"><button type="button" @click="openPanel('trend')"><el-icon><TrendCharts /></el-icon><strong>用水趋势</strong><span>查看每日用水变化</span></button><button type="button" @click="openPanel('bills')"><el-icon><Tickets /></el-icon><strong>历史账单</strong><span>账单和缴费记录</span></button><button type="button" @click="openPanel('feedback')"><el-icon><Warning /></el-icon><strong>异常反馈</strong><span>提交漏水或错抄问题</span></button><button type="button" @click="aiVisible = true"><el-icon><ChatDotRound /></el-icon><strong>AI 用水助手</strong><span>查询账单和节水建议</span></button></div>
    <div class="user-notice"><el-icon><Warning /></el-icon><div><strong>夜间小流量提醒</strong><p>系统检测到 9 月 18 日 02:00–04:00 有连续小流量用水，请检查水龙头或卫生间是否存在漏水。</p></div><el-button text type="primary" @click="openPanel('notice')">查看详情</el-button></div>

    <el-dialog v-model="panelVisible" :title="panelTitles[panelType]" width="620px">
      <el-table v-if="panelType === 'trend'" :data="dailyUsage"><el-table-column prop="date" label="日期" /><el-table-column label="用水量"><template #default="scope">{{ scope.row.usage }} m³</template></el-table-column><el-table-column label="状态"><template #default="scope"><el-tag :type="scope.row.usage > 0.8 ? 'warning' : 'success'">{{ scope.row.usage > 0.8 ? '偏高' : '正常' }}</el-tag></template></el-table-column></el-table>
      <el-table v-else-if="panelType === 'bills'" :data="historyBills"><el-table-column prop="month" label="账期" /><el-table-column prop="usage" label="用水量" /><el-table-column prop="amount" label="金额" /><el-table-column label="状态"><template #default="scope"><el-tag :type="scope.row.status === '已缴费' ? 'success' : 'warning'">{{ scope.row.status }}</el-tag></template></el-table-column></el-table>
      <el-form v-else-if="panelType === 'feedback'" label-position="top"><el-form-item label="问题类型"><el-select v-model="feedback.type" style="width:100%"><el-option v-for="item in ['疑似漏水', '读数异常', '账单疑问', '设备损坏']" :key="item" :value="item" /></el-select></el-form-item><el-form-item label="问题描述"><el-input v-model="feedback.description" type="textarea" :rows="4" placeholder="请描述发现问题的时间和现象" /></el-form-item></el-form>
      <el-result v-else icon="warning" title="检测到连续小流量" sub-title="9 月 18 日 02:00–04:00，瞬时流量持续为 0.03–0.06 m³/h。建议检查水龙头、马桶水箱和室内管道。" />
      <template #footer><el-button @click="panelVisible = false">关闭</el-button><el-button v-if="panelType === 'feedback'" type="primary" @click="submitFeedback">提交反馈</el-button></template>
    </el-dialog>
  </section>
</template>
