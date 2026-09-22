<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { Download, Operation } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useSystemStore } from '../../stores/system'
import { downloadCsv } from '../../utils/export'

const store = useSystemStore()
const { readingProgress, readingRunning, tasks } = storeToRefs(store)

function startTask() {
  if (readingRunning.value) return
  store.startReading()
  ElMessage.success('自动抄表任务已启动')
}

function exportTasks() {
  downloadCsv('抄表任务记录.csv', [['任务名称', '任务范围', '完成率', '状态', '开始时间'], ...tasks.value.map((task) => [task.name, task.scope, `${task.progress}%`, task.status, task.time])])
  ElMessage.success('任务记录已导出')
}
</script>

<template>
  <section class="workspace">
    <div class="reading-hero"><div><span class="eyebrow">今日任务</span><h2>自动抄表与漏抄补采</h2><p>覆盖全部区域 1,000 台水表，自动执行数据采集、异常检查和补采。</p></div><el-button size="large" type="primary" :loading="readingRunning" :icon="Operation" @click="startTask">{{ readingRunning ? '正在执行' : '立即执行' }}</el-button></div>
    <div class="reading-progress panel"><div class="progress-number"><strong>{{ readingProgress }}%</strong><span>{{ Math.round(readingProgress * 10) }} / 1,000 台</span></div><el-progress :percentage="readingProgress" :stroke-width="14" :show-text="false" /><div class="progress-steps"><span class="done">创建任务</span><span :class="{ done: readingProgress > 25 }">采集数据</span><span :class="{ done: readingProgress > 70 }">检测漏抄</span><span :class="{ done: readingProgress === 100 }">完成入库</span></div></div>
    <section class="panel data-panel"><div class="panel-head"><div><h2>最近任务</h2><p>保留每次自动抄表的执行记录</p></div><el-button :icon="Download" @click="exportTasks">导出记录</el-button></div><el-table :data="tasks"><el-table-column prop="name" label="任务名称" min-width="210" /><el-table-column prop="scope" label="任务范围" min-width="190" /><el-table-column label="完成率" width="170"><template #default="scope"><el-progress :percentage="scope.row.progress" :stroke-width="7" /></template></el-table-column><el-table-column prop="status" label="状态" width="110" /><el-table-column prop="time" label="开始时间" width="110" /></el-table></section>
  </section>
</template>
