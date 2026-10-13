<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import { useSystemStore } from '../../stores/system'
import { waterApi } from '../../api/water'
import type { Feedback, AnomalyStatus } from '../../types/domain'
import { dateTime, errorMessage } from '../../utils/format'
import RequestState from '../../components/RequestState.vue'
const store = useSystemStore()
const { feedback, canOperate } = storeToRefs(store)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const filterStatus = ref('')
const dialogVisible = ref(false)
const selected = ref<Feedback | null>(null)
const status = ref<AnomalyStatus>('处理中')
const resolution = ref('')
const filtered = computed(() => feedback.value.filter(item => !filterStatus.value || item.status === filterStatus.value))
async function reload() {
  loading.value = true; error.value = ''
  try { await store.loadFeedback() } catch (e) { error.value = errorMessage(e) } finally { loading.value = false }
}
function open(item: Feedback) {
  selected.value = item; status.value = item.status === '已完成' ? '已完成' : '处理中'
  resolution.value = item.resolution || ''; dialogVisible.value = true
}
async function save() {
  if (!selected.value) return
  if (!resolution.value.trim()) return ElMessage.warning('请填写处理说明或结果')
  saving.value = true
  try {
    await waterApi.updateFeedback(selected.value.id, status.value, resolution.value.trim())
    dialogVisible.value = false; await reload()
    ElMessage.success('处理结果已保存，居民已收到通知')
  } catch (e) { ElMessage.error(errorMessage(e)) } finally { saving.value = false }
}
onMounted(() => { void reload() })
</script>
<template>
  <section class="workspace"><section class="panel data-panel">
    <div class="table-toolbar"><div><h2>居民反馈处理</h2><p class="muted">{{ canOperate ? '受理反馈并填写处理结果，更新后通知居民。' : '审计账号仅可查看反馈与处理记录。' }}</p></div><div class="filter-row"><el-select v-model="filterStatus" clearable placeholder="全部状态" style="width:160px"><el-option v-for="value in ['待处理', '处理中', '已完成']" :key="value" :value="value" /></el-select><el-button :loading="loading" @click="reload">刷新</el-button></div></div>
    <RequestState :loading="loading" :error="error" @retry="reload" />
    <el-table v-if="!loading && !error" :data="filtered" empty-text="当前授权范围暂无反馈"><el-table-column prop="id" label="编号" width="80" /><el-table-column prop="userName" label="居民" width="110" /><el-table-column prop="meterId" label="水表" width="115" /><el-table-column prop="area" label="区域" min-width="120" /><el-table-column prop="type" label="类型" min-width="110" /><el-table-column prop="description" label="问题描述" min-width="220" /><el-table-column prop="status" label="状态" width="100" /><el-table-column prop="resolution" label="处理结果" min-width="200" /><el-table-column prop="handledBy" label="处理人" width="110" /><el-table-column label="更新时间" min-width="170"><template #default="{ row }">{{ dateTime(row.updatedAt) }}</template></el-table-column><el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" @click="open(row)">{{ canOperate && row.status !== '已完成' ? '处理' : '详情' }}</el-button></template></el-table-column></el-table>
    <el-dialog v-model="dialogVisible" :title="'反馈 #' + (selected?.id || '')" width="min(580px, 94vw)"><p><strong>{{ selected?.userName }} · {{ selected?.meterId }} · {{ selected?.type }}</strong></p><p>{{ selected?.description }}</p><p class="muted">提交于 {{ dateTime(selected?.createdAt) }}</p><el-form label-position="top"><el-form-item label="状态"><el-select v-model="status" :disabled="!canOperate || selected?.status === '已完成'"><el-option label="处理中" value="处理中" /><el-option label="已完成" value="已完成" /></el-select></el-form-item><el-form-item label="处理说明 / 结果"><el-input v-model="resolution" type="textarea" :rows="4" :maxlength="2000" :disabled="!canOperate || selected?.status === '已完成'" /></el-form-item></el-form><template #footer><el-button @click="dialogVisible = false">关闭</el-button><el-button v-if="canOperate && selected?.status !== '已完成'" type="primary" :loading="saving" @click="save">保存并通知居民</el-button></template></el-dialog>
  </section></section>
</template>
