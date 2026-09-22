<script setup lang="ts">
import { computed, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { Setting } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useSystemStore } from '../../stores/system'

const store = useSystemStore()
const { anomalies } = storeToRefs(store)
const level = ref('全部等级')
const filtered = computed(() => anomalies.value.filter((item) => level.value === '全部等级' || item.level === level.value))
const count = (target: string) => anomalies.value.filter((item) => item.level === target && item.status !== '已完成').length

function advance(id: string, status: string) {
  if (status === '已完成') return ElMessage.info('该异常已经闭环')
  store.advanceAnomaly(id)
  ElMessage.success(`异常 ${id} 状态已更新`)
}

function configureRules() {
  ElMessageBox.alert('演示规则：持续高流量 30 分钟触发高风险；连续零流量 24 小时触发中风险；设备报警码立即生成异常工单。', '异常识别规则', { confirmButtonText: '知道了' })
}
</script>

<template>
  <section class="workspace">
    <section class="panel data-panel"><div class="table-toolbar"><div class="filter-row"><el-select v-model="level" style="width:140px"><el-option v-for="item in ['全部等级', '高', '中', '低']" :key="item" :label="item" :value="item" /></el-select><el-button :icon="Setting" @click="configureRules">规则配置</el-button></div><div class="risk-summary"><span><b>{{ count('高') }}</b> 高风险</span><span><b>{{ count('中') }}</b> 中风险</span><span><b>{{ count('低') }}</b> 低风险</span></div></div><el-table :data="filtered" stripe empty-text="当前筛选条件下没有异常"><el-table-column prop="id" label="异常编号" min-width="150" /><el-table-column prop="meter" label="水表编号" width="110" /><el-table-column prop="area" label="区域" min-width="140" /><el-table-column prop="type" label="异常类型" min-width="150" /><el-table-column label="等级" width="90"><template #default="scope"><el-tag :type="scope.row.level === '高' ? 'danger' : scope.row.level === '中' ? 'warning' : 'info'">{{ scope.row.level }}</el-tag></template></el-table-column><el-table-column prop="time" label="发现时间" width="130" /><el-table-column prop="status" label="状态" width="100" /><el-table-column label="操作" width="120"><template #default="scope"><el-button link type="primary" @click="advance(scope.row.id, scope.row.status)">{{ scope.row.status === '待处理' ? '开始处理' : scope.row.status === '处理中' ? '完成处置' : '已闭环' }}</el-button></template></el-table-column></el-table></section>
  </section>
</template>
