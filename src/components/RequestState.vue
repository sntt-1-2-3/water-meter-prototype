<script setup lang="ts">
defineProps<{ loading?: boolean; error?: string }>()
defineEmits<{ retry: [] }>()
</script>
<template>
  <el-skeleton v-if="loading" :rows="3" animated class="request-state" />
  <div v-else-if="error" class="request-error" role="alert"><span>{{ error }}</span><el-button type="primary" plain @click="$emit('retry')">重试</el-button></div>
</template>
<style scoped>
.request-state { padding: 20px 0; }
.request-error { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 18px; padding: 16px; border: 1px solid #f2cec9; border-radius: 10px; color: #ae443a; background: #fff5f3; }
</style>
