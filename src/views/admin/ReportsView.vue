<script setup lang="ts">
import { Document, Download, TrendCharts, Warning } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { downloadCsv } from '../../utils/export'

function exportReport(type: string) {
  downloadCsv(`${type}-2026-09.csv`, [
    ['指标', '数值', '说明'],
    ['累计用水', '125420 m³', '较上月同期增长 6.8%'],
    ['累计收费', '¥78816', '综合收费率 91.2%'],
    ['设备健康度', '98.6%', '14 台设备需要关注'],
    ['异常闭环率', '86.4%', '平均处置时长 3.2 小时'],
  ])
  ElMessage.success(`${type}已导出为 Excel 可打开的 CSV 文件`)
}
</script>

<template>
  <section class="workspace">
    <div class="report-grid"><article class="report-card"><div class="report-icon"><el-icon><Document /></el-icon></div><div><strong>水务运营日报</strong><span>用水量、抄表率、收费与异常汇总</span></div><el-button :icon="Download" @click="exportReport('水务运营日报')">导出报表</el-button></article><article class="report-card"><div class="report-icon blue"><el-icon><TrendCharts /></el-icon></div><div><strong>区域用水月报</strong><span>分区域、用户类型与同比趋势分析</span></div><el-button :icon="Download" @click="exportReport('区域用水月报')">导出报表</el-button></article><article class="report-card"><div class="report-icon orange"><el-icon><Warning /></el-icon></div><div><strong>设备异常报告</strong><span>异常分类、处置时效与设备故障率</span></div><el-button :icon="Download" @click="exportReport('设备异常报告')">导出报表</el-button></article></div>
    <section class="panel report-summary"><div class="panel-head"><div><h2>9 月运营摘要</h2><p>数据统计至今日 10:30</p></div><el-tag type="success">运营稳定</el-tag></div><div class="summary-columns"><div><span>累计用水</span><strong>125,420 m³</strong><p>较上月同期增长 6.8%</p></div><div><span>累计收费</span><strong>¥78,816</strong><p>综合收费率 91.2%</p></div><div><span>设备健康度</span><strong>98.6%</strong><p>14 台设备需要关注</p></div><div><span>异常闭环率</span><strong>86.4%</strong><p>平均处置时长 3.2 小时</p></div></div></section>
  </section>
</template>
