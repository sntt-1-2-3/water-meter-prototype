<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { Odometer, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useSystemStore } from '../../stores/system'
import type { Meter, MeterStatus, MeterType } from '../../types/domain'

const store = useSystemStore()
const { meters } = storeToRefs(store)
const keyword = ref('')
const selectedType = ref('全部类型')
const currentPage = ref(1)
const pageSize = 8
const dialogVisible = ref(false)
const detailVisible = ref(false)
const editingId = ref<string>()
const selectedMeter = ref<Meter>()

const emptyForm = (): Meter => ({ id: '', user: '', area: '', type: '居民', reading: 0, status: '在线', updated: '刚刚' })
const form = reactive<Meter>(emptyForm())

const filteredMeters = computed(() => meters.value.filter((meter) => {
  const typeMatch = selectedType.value === '全部类型' || meter.type === selectedType.value
  const text = keyword.value.trim().toLowerCase()
  return typeMatch && (!text || `${meter.id}${meter.user}${meter.area}`.toLowerCase().includes(text))
}))
const pagedMeters = computed(() => filteredMeters.value.slice((currentPage.value - 1) * pageSize, currentPage.value * pageSize))

watch([keyword, selectedType], () => { currentPage.value = 1 })

function openCreate() {
  editingId.value = undefined
  Object.assign(form, emptyForm(), { id: `A-${String(Date.now()).slice(-5)}` })
  dialogVisible.value = true
}

function openEdit(meter: Meter) {
  editingId.value = meter.id
  Object.assign(form, meter)
  dialogVisible.value = true
}

function openDetail(meter: Meter) {
  selectedMeter.value = meter
  detailVisible.value = true
}

function submit() {
  if (!form.id.trim() || !form.user.trim() || !form.area.trim()) {
    ElMessage.warning('请填写水表编号、用户名称和所属区域')
    return
  }
  if (!editingId.value && meters.value.some((item) => item.id === form.id)) {
    ElMessage.warning('水表编号已存在')
    return
  }
  store.saveMeter({ ...form, reading: Number(form.reading) }, editingId.value)
  dialogVisible.value = false
  ElMessage.success(editingId.value ? '水表信息已更新' : '水表已新增')
}

const meterTypes: MeterType[] = ['居民', '商业', '工业']
const meterStatuses: MeterStatus[] = ['在线', '异常', '离线']
</script>

<template>
  <section class="workspace">
    <section class="panel data-panel">
      <div class="table-toolbar">
        <div class="filter-row"><el-input v-model="keyword" :prefix-icon="Search" clearable placeholder="搜索水表编号、用户或区域" /><el-select v-model="selectedType" style="width:140px"><el-option v-for="type in ['全部类型', ...meterTypes]" :key="type" :label="type" :value="type" /></el-select></div>
        <el-button type="primary" :icon="Odometer" @click="openCreate">新增水表</el-button>
      </div>
      <el-table :data="pagedMeters" stripe empty-text="没有符合条件的水表">
        <el-table-column prop="id" label="水表编号" min-width="120" /><el-table-column prop="user" label="用户名称" min-width="130" /><el-table-column prop="area" label="所属区域" min-width="140" /><el-table-column prop="type" label="用户类型" width="100" />
        <el-table-column label="累计流量" min-width="130"><template #default="scope">{{ scope.row.reading.toLocaleString() }} m³</template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="scope.row.status === '在线' ? 'success' : scope.row.status === '异常' ? 'danger' : 'info'">{{ scope.row.status }}</el-tag></template></el-table-column>
        <el-table-column prop="updated" label="最近上报" min-width="110" /><el-table-column label="操作" width="150" fixed="right"><template #default="scope"><el-button link type="primary" @click="openDetail(scope.row)">详情</el-button><el-button link @click="openEdit(scope.row)">编辑</el-button></template></el-table-column>
      </el-table>
      <div class="pagination-row"><span>共 {{ filteredMeters.length }} 条模拟档案</span><el-pagination v-model:current-page="currentPage" background layout="prev, pager, next" :total="filteredMeters.length" :page-size="pageSize" /></div>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑水表' : '新增水表'" width="560px">
      <el-form label-position="top">
        <div class="dialog-grid"><el-form-item label="水表编号"><el-input v-model="form.id" :disabled="Boolean(editingId)" /></el-form-item><el-form-item label="用户名称"><el-input v-model="form.user" /></el-form-item><el-form-item label="所属区域"><el-input v-model="form.area" /></el-form-item><el-form-item label="用户类型"><el-select v-model="form.type"><el-option v-for="type in meterTypes" :key="type" :value="type" /></el-select></el-form-item><el-form-item label="累计流量（m³）"><el-input-number v-model="form.reading" :min="0" :precision="1" /></el-form-item><el-form-item label="设备状态"><el-select v-model="form.status"><el-option v-for="status in meterStatuses" :key="status" :value="status" /></el-select></el-form-item></div>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" @click="submit">保存</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="水表详情" size="420px">
      <div v-if="selectedMeter" class="detail-list"><div><span>水表编号</span><strong>{{ selectedMeter.id }}</strong></div><div><span>用户名称</span><strong>{{ selectedMeter.user }}</strong></div><div><span>所属区域</span><strong>{{ selectedMeter.area }}</strong></div><div><span>用户类型</span><strong>{{ selectedMeter.type }}</strong></div><div><span>累计流量</span><strong>{{ selectedMeter.reading.toLocaleString() }} m³</strong></div><div><span>设备状态</span><el-tag :type="selectedMeter.status === '在线' ? 'success' : 'danger'">{{ selectedMeter.status }}</el-tag></div><div><span>最近上报</span><strong>{{ selectedMeter.updated }}</strong></div></div>
    </el-drawer>
  </section>
</template>
