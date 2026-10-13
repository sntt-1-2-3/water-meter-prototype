<script setup lang="ts">
import type { Payment } from '../types/domain'
import { dateTime, money } from '../utils/format'
defineProps<{ items: Payment[] }>()
</script>
<template>
  <el-table :data="items" empty-text="暂无缴费流水"><el-table-column prop="transactionNo" label="流水号" min-width="200" /><el-table-column prop="meterId" label="水表" min-width="110" /><el-table-column prop="billItemId" label="账单编号" width="100" /><el-table-column label="金额" width="120"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column><el-table-column prop="payer" label="操作账号" min-width="110" /><el-table-column label="渠道" width="120"><template #default>模拟缴费</template></el-table-column><el-table-column label="缴费时间" min-width="170"><template #default="{ row }">{{ dateTime(row.paidAt) }}</template></el-table-column></el-table>
</template>
