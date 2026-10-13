<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { Odometer, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useSystemStore } from '../../stores/system'
import type { Meter, MeterStatus, MeterType } from '../../types/domain'
import { dateTime, errorMessage, number } from '../../utils/format'
import RequestState from '../../components/RequestState.vue'
const store = useSystemStore()
const { meters, meterTotal, canOperate } = storeToRefs(store)
const keyword = ref('')
const selectedType = ref('')
const currentPage = ref(1)
const pageSize = 10
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const dialogVisible = ref(false)
const detailVisible = ref(false)
const editingId = ref<string>()
const selectedMeter = ref<Meter>()
const emptyForm = (): Meter => ({ id: '', user: '', area: '', type: '居民', reading: 0, status: '在线', updated: null })
const form = reactive<Meter>(emptyForm())
const meterTypes: MeterType[] = ['居民', '商业', '工业']
const meterStatuses: MeterStatus[] = ['在线', '异常', '离线']
async function reload() {
  loading.value = true; error.value = ''
  try { await store.loadMeters({ page: currentPage.value, pageSize, keyword: keyword.value.trim(), type: selectedType.value }) }
  catch (e) { error.value = errorMessage(e) } finally { loading.value = false }
}
function search() { if (currentPage.value === 1) void reload(); else currentPage.value = 1 }
watch(currentPage, () => { void reload() })
watch(selectedType, search)
function openCreate() { editingId.value = undefined; Object.assign(form, emptyForm()); dialogVisible.value = true }
function openEdit(meter: Meter) { editingId.value = meter.id; Object.assign(form, meter); dialogVisible.value = true }
function openDetail(meter: Meter) { selectedMeter.value = meter; detailVisible.value = true }
async function submit() {
  if (saving.value) return
  if (!form.id.trim() || !form.user.trim() || !form.area.trim()) return ElMessage.warning('请填写水表编号、用户名称和所属区域')
  saving.value = true
  try { await store.saveMeter({ ...form }, editingId.value); dialogVisible.value = false; await reload(); ElMessage.success('水表档案已保存') }
  catch (e) { ElMessage.error(errorMessage(e)) } finally { saving.value = false }
}
onMounted(() => { void reload() })
</script>
<template>
  <section class="workspace"><section class="panel data-panel">
    <div class="table-toolbar"><div class="filter-row"><el-input v-model="keyword" :prefix-icon="Search" clearable placeholder="水表编号、用户或区域" @keyup.enter="search" @clear="search" /><el-select v-model="selectedType" clearable placeholder="全部类型" style="width:140px"><el-option v-for="type in meterTypes" :key="type" :label="type" :value="type" /></el-select><el-button :loading="loading" @click="search">查询</el-button></div><el-button v-if="canOperate" type="primary" :icon="Odometer" @click="openCreate">新增水表</el-button></div>
    <RequestState :loading="loading" :error="error" @retry="reload" />
    <el-table v-if="!loading && !error" :data="meters" stripe empty-text="没有符合条件的水表">
      <el-table-column prop="id" label="水表编号" min-width="120" /><el-table-column prop="user" label="用户名称" min-width="130" /><el-table-column prop="area" label="所属区域" min-width="140" /><el-table-column prop="type" label="类型" width="90" />
      <el-table-column label="累计读数" min-width="120"><template #default="{ row }">{{ number(row.reading) }} m³</template></el-table-column>
      <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status === '在线' ? 'success' : row.status === '异常' ? 'danger' : 'info'">{{ row.status }}</el-tag></template></el-table-column>
      <el-table-column label="最近更新" min-width="160"><template #default="{ row }">{{ dateTime(row.updated) }}</template></el-table-column>
      <el-table-column label="操作" width="140"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">详情</el-button><el-button v-if="canOperate" link @click="openEdit(row)">编辑</el-button></template></el-table-column>
    </el-table><div class="pagination-row"><span>共 {{ meterTotal }} 条档案</span><el-pagination v-model:current-page="currentPage" background layout="prev, pager, next" :total="meterTotal" :page-size="pageSize" /></div>
  </section>
  <el-dialog v-model="dialogVisible" :title="editingId ? '编辑水表' : '新增水表'" width="min(560px, 94vw)">
    <el-form label-position="top"><div class="dialog-grid">
      <el-form-item label="水表编号"><el-input v-model="form.id" :disabled="Boolean(editingId)" /></el-form-item><el-form-item label="用户名称"><el-input v-model="form.user" /></el-form-item><el-form-item label="所属区域"><el-input v-model="form.area" /></el-form-item>
      <el-form-item label="用户类型"><el-select v-model="form.type"><el-option v-for="type in meterTypes" :key="type" :value="type" /></el-select></el-form-item><el-form-item label="累计读数（m³）"><el-input-number v-model="form.reading" :min="0" :precision="2" /></el-form-item><el-form-item label="设备状态"><el-select v-model="form.status"><el-option v-for="status in meterStatuses" :key="status" :value="status" /></el-select></el-form-item>
    </div></el-form><template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存</el-button></template>
  </el-dialog>
  <el-drawer v-model="detailVisible" title="水表详情" size="420px"><div v-if="selectedMeter" class="detail-list"><div><span>编号</span><strong>{{ selectedMeter.id }}</strong></div><div><span>用户</span><strong>{{ selectedMeter.user }}</strong></div><div><span>区域</span><strong>{{ selectedMeter.area }}</strong></div><div><span>类型</span><strong>{{ selectedMeter.type }}</strong></div><div><span>累计读数</span><strong>{{ number(selectedMeter.reading) }} m³</strong></div><div><span>状态</span><strong>{{ selectedMeter.status }}</strong></div><div><span>更新</span><strong>{{ dateTime(selectedMeter.updated) }}</strong></div></div></el-drawer>
  </section>
</template>
