<script setup lang="ts">
import { ref } from 'vue'
import { storeToRefs } from 'pinia'
import { Tickets } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useSystemStore } from '../../stores/system'

const store = useSystemStore()
const { bills } = storeToRefs(store)
const detailVisible = ref(false)
const selectedBatch = ref('')

function calculateBills() {
  ElMessageBox.confirm('将按居民、商业、工业三类阶梯水价重新核算 1,000 户账单，是否继续？', '生成本月账单', { confirmButtonText: '开始核算', cancelButtonText: '取消', type: 'warning' })
    .then(() => ElMessage.success('账单核算任务已创建'))
    .catch(() => undefined)
}

function showBatch(batch: string) {
  selectedBatch.value = batch
  detailVisible.value = true
}

function remind(batch: string) { ElMessage.success(`${batch} 的催缴通知已加入发送队列`) }
</script>

<template>
  <section class="workspace">
    <div class="tariff-grid"><article class="tariff-card"><span>居民阶梯水价</span><strong>¥2.80 <small>/ m³ 起</small></strong><p>0–20 m³ 第一阶梯，超量自动递增</p></article><article class="tariff-card"><span>商业用水价格</span><strong>¥4.60 <small>/ m³</small></strong><p>统一商业水价，支持行业附加规则</p></article><article class="tariff-card"><span>工业用水价格</span><strong>¥4.10 <small>/ m³</small></strong><p>支持计划用水与超额累进计价</p></article></div>
    <section class="panel data-panel"><div class="panel-head"><div><h2>2026 年 9 月账单批次</h2><p>共 1,000 户，预计应收 ¥86,420</p></div><el-button type="primary" :icon="Tickets" @click="calculateBills">重新核算账单</el-button></div><el-table :data="bills"><el-table-column prop="batch" label="账单批次" min-width="200" /><el-table-column prop="users" label="用户数" width="110" /><el-table-column prop="amount" label="应收金额" min-width="140" /><el-table-column prop="paid" label="收费率" width="120" /><el-table-column label="状态" width="110"><template #default="scope"><el-tag>{{ scope.row.status }}</el-tag></template></el-table-column><el-table-column label="操作" width="180"><template #default="scope"><el-button link type="primary" @click="showBatch(scope.row.batch)">查看账单</el-button><el-button link @click="remind(scope.row.batch)">催缴</el-button></template></el-table-column></el-table></section>
    <el-drawer v-model="detailVisible" title="账单批次详情" size="460px"><el-result icon="success" title="账单数据已生成" :sub-title="`${selectedBatch}：已完成阶梯计价、违约金检查和缴费状态汇总。`"><template #extra><el-button type="primary" @click="detailVisible = false">返回列表</el-button></template></el-result></el-drawer>
  </section>
</template>
