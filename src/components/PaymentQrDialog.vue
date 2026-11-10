<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import QRCode from 'qrcode'
import { waterApi } from '../api/water'
import { money, errorMessage } from '../utils/format'
import { paymentUrl, isLoopbackUrl } from '../utils/paymentQr'
import { readToken } from '../utils/session'
import type { BillItem } from '../types/domain'
const props = defineProps<{ modelValue: boolean; billId: number | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; paid: [] }>()
const bill = ref<BillItem | null>(null)
const image = ref(''), url = ref(''), error = ref(''), loading = ref(false)
let version = 0, timer: ReturnType<typeof setTimeout> | undefined
let paidNotified = false
function stop() { clearTimeout(timer); timer = undefined }
async function refresh(epoch = version, token = readToken()) {
  if (!props.modelValue || !props.billId) return
  stop(); loading.value = true
  try {
    const { data } = await waterApi.getBillItem(props.billId)
    if (epoch !== version || token !== readToken() || !props.modelValue) return
    bill.value = data; error.value = ''
    if (Number(data.outstanding) <= 0 || data.status === '已缴') {
      if (!paidNotified) { paidNotified = true; emit('paid') }
      image.value = ''; return
    }
    url.value = paymentUrl(data.id, window.location.origin, import.meta.env.VITE_PUBLIC_APP_URL)
    const png = await QRCode.toDataURL(url.value, { width: 280, margin: 4, errorCorrectionLevel: 'M' })
    if (epoch === version && token === readToken() && props.modelValue) image.value = png
  } catch (e) { if (epoch === version) { error.value = errorMessage(e); image.value = ''; bill.value = null } }
  finally {
    if (epoch === version) {
      loading.value = false
      if (props.modelValue && token === readToken() && !paidNotified && !error.value)
        timer = setTimeout(() => { void refresh(epoch, token) }, 5000)
    }
  }
}
watch(() => [props.modelValue, props.billId] as const, () => {
  version++; stop(); image.value = ''; url.value = ''; bill.value = null; error.value = ''; paidNotified = false
  if (props.modelValue && props.billId) void refresh()
}, { immediate: true })
onBeforeUnmount(() => { version++; stop() })
</script>
<template>
  <el-dialog :model-value="modelValue" title="扫码缴费 · 模拟演示" width="min(460px, 94vw)" @update:model-value="emit('update:modelValue', $event)">
    <div class="qr-payment">
      <p v-if="error" role="alert">{{ error }}</p>
      <template v-if="bill">
        <h3>{{ bill.period }} 水费 · {{ bill.meterId }}</h3><strong class="qr-amount">{{ money(bill.outstanding) }}</strong>
        <el-result v-if="bill.status === '已缴' || Number(bill.outstanding) <= 0" icon="success" title="账单已结清" />
        <template v-else>
          <img v-if="image" :src="image" alt="扫码打开账单确认页面（模拟支付）" width="280" height="280">
          
          <el-alert v-if="url && isLoopbackUrl(url)" title="本机地址，手机无法访问" type="info" :closable="false" />
          <p v-if="url"><a :href="url" target="_blank" rel="noopener noreferrer">在浏览器打开支付确认页</a></p>
          
        </template>
      </template>
      <p v-if="loading">正在查询账单…</p>
    </div>
    <template #footer><el-button :loading="loading" @click="refresh()">刷新状态</el-button><el-button @click="emit('update:modelValue', false)">关闭</el-button></template>
  </el-dialog>
</template>
<style scoped>.qr-payment{text-align:center;overflow-wrap:anywhere}.qr-payment img{display:block;max-width:100%;height:auto;margin:8px auto}.qr-amount{font-size:32px;color:#147b87}.qr-payment p{line-height:1.6}.qr-payment small{color:#64748b}.qr-payment a{color:#147b87}</style>
