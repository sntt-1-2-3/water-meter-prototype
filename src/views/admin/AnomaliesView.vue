<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import { useSystemStore } from '../../stores/system'
import { waterApi } from '../../api/water'
import { dateTime, errorMessage } from '../../utils/format'
import RequestState from '../../components/RequestState.vue'
const store = useSystemStore()
const { anomalies, meters, canOperate, isAdmin } = storeToRefs(store)
const level = ref('')
const loading = ref(false)
const error = ref('')
const saving = ref('')
const scenario = ref('high_flow')
const meterId = ref('')
const filtered = computed(() => anomalies.value.filter(item => !level.value || item.level === level.value))
const count = (risk: string) => anomalies.value.filter(item => item.level === risk && item.status !== '已完成').length
async function reload() {
  loading.value = true; error.value = ''
  try { await Promise.all([store.loadAnomalies(), ...(isAdmin.value ? [store.loadMeters()] : [])]) }
  catch (e) { error.value = errorMessage(e) } finally { loading.value = false }
}
async function advance(id: string) {
  if (saving.value) return
  saving.value = id
  try { await store.advanceAnomaly(id); ElMessage.success('异常处理状态已保存') }
  catch (e) { ElMessage.error(errorMessage(e)) } finally { saving.value = '' }
}
async function simulate() {
  if (!meterId.value) return ElMessage.warning('请选择水表')
  saving.value = 'simulate'
  try { await waterApi.detectAnomalies(meterId.value, scenario.value); await reload(); ElMessage.success('采集场景已保存，告警与设备状态已刷新') }
  catch (e) { ElMessage.error(errorMessage(e)) } finally { saving.value = '' }
}
onMounted(() => { void reload() })
</script>
<template>
  <section class="workspace">
    <section v-if="isAdmin" class="panel data-panel" style="margin-bottom:18px"><div class="panel-head"><div><h2>采集场景演示</h2><p>向指定水表写入模拟采集数据，后端识别告警并保存处置记录</p></div></div><div class="filter-row"><el-select v-model="meterId" filterable placeholder="选择水表" style="width:250px"><el-option v-for="meter in meters" :key="meter.id" :value="meter.id" :label="meter.id + ' · ' + meter.user" /></el-select><el-select v-model="scenario" style="width:180px"><el-option label="持续高流量" value="high_flow" /><el-option label="设备报警" value="alarm" /><el-option label="设备离线" value="offline" /><el-option label="正常采集" value="normal" /></el-select><el-button type="primary" :loading="saving === 'simulate'" :disabled="Boolean(saving && saving !== 'simulate')" @click="simulate">执行采集场景</el-button></div></section>
    <section class="panel data-panel"><div class="table-toolbar"><div class="filter-row"><el-select v-model="level" clearable placeholder="全部等级" style="width:140px"><el-option v-for="item in ['高', '中', '低']" :key="item" :label="item" :value="item" /></el-select><el-button :loading="loading" @click="reload">刷新</el-button></div><div class="risk-summary"><span><b>{{ count('高') }}</b>高风险</span><span><b>{{ count('中') }}</b>中风险</span><span><b>{{ count('低') }}</b>低风险</span></div></div>
      <RequestState :loading="loading" :error="error" @retry="reload" />
      <el-table v-if="!loading && !error" :data="filtered" stripe empty-text="当前范围暂无异常"><el-table-column prop="id" label="异常编号" min-width="170" /><el-table-column prop="meter" label="水表" width="115" /><el-table-column prop="area" label="区域" min-width="130" /><el-table-column prop="type" label="异常类型" min-width="150" /><el-table-column label="风险" width="90"><template #default="{ row }"><el-tag :type="row.level === '高' ? 'danger' : 'warning'">{{ row.level }}</el-tag></template></el-table-column><el-table-column label="时间" min-width="150"><template #default="{ row }">{{ dateTime(row.time) }}</template></el-table-column><el-table-column prop="suggestion" label="处理建议" min-width="200" /><el-table-column prop="status" label="状态" width="100" /><el-table-column prop="confirmedBy" label="处理人" min-width="100" /><el-table-column v-if="canOperate" label="操作" width="110"><template #default="{ row }"><el-button v-if="row.status !== '已完成'" link type="primary" :loading="saving === row.id" :disabled="Boolean(saving && saving !== row.id)" @click="advance(row.id)">{{ row.status === '待处理' ? '开始处理' : '完成处置' }}</el-button></template></el-table-column></el-table>
    </section>
  </section>
</template>
