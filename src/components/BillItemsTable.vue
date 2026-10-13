<script setup lang="ts">
import type { BillItem } from '../types/domain'
import { money, number } from '../utils/format'
defineProps<{ items: BillItem[]; allowPay?: boolean; busyId?: number | null }>()
defineEmits<{ pay: [item: BillItem] }>()
</script>
<template>
  <el-table :data="items" empty-text="当前范围暂无账单">
    <el-table-column prop="period" label="账期" width="100" /><el-table-column prop="meterId" label="水表" min-width="110" /><el-table-column prop="userName" label="用户" min-width="110" /><el-table-column prop="area" label="区域" min-width="120" />
    <el-table-column label="期初 / 期末" min-width="155"><template #default="{ row }">{{ number(row.openingReading) }} / {{ number(row.closingReading) }}</template></el-table-column>
    <el-table-column label="用量" width="90"><template #default="{ row }">{{ number(row.usage) }} m³</template></el-table-column>
    <el-table-column label="水费" width="110"><template #default="{ row }">{{ money(row.waterFee) }}</template></el-table-column><el-table-column label="违约金" width="100"><template #default="{ row }">{{ money(row.penalty) }}</template></el-table-column>
    <el-table-column label="应缴" width="110"><template #default="{ row }">{{ money(row.totalDue) }}</template></el-table-column><el-table-column label="已缴" width="100"><template #default="{ row }">{{ money(row.paidAmount) }}</template></el-table-column><el-table-column label="待缴" width="110"><template #default="{ row }">{{ money(row.outstanding) }}</template></el-table-column>
    <el-table-column prop="dueDate" label="截止日期" width="115" /><el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status === '已缴' ? 'success' : row.status === '逾期' ? 'danger' : 'warning'">{{ row.status }}</el-tag></template></el-table-column>
    <el-table-column v-if="allowPay" label="操作" width="120" fixed="right"><template #default="{ row }"><el-button v-if="Number(row.outstanding) > 0" link type="primary" :loading="busyId === row.id" :disabled="Boolean(busyId && busyId !== row.id)" @click="$emit('pay', row)">模拟缴费</el-button><span v-else>已结清</span></template></el-table-column>
  </el-table>
</template>
