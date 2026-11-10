<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { waterApi } from '../../api/water'
import { useSystemStore } from '../../stores/system'
import { readToken } from '../../utils/session'
import { errorMessage, money, paymentKey } from '../../utils/format'
import { homeFor } from '../../utils/permissions'
import type { BillItem } from '../../types/domain'
const route = useRoute(), router = useRouter(), store = useSystemStore()
const bill = ref<BillItem | null>(null), error = ref(''), receipt = ref('')
const loading = ref(false), paying = ref(false)
let epoch = 0
const settled = computed(() => bill.value && (bill.value.status === '已缴' || Number(bill.value.outstanding) <= 0))
async function reload() {
  const version = ++epoch, token = readToken(); error.value = ''; bill.value = null; receipt.value = ''; paying.value = false; loading.value = true
  try {
    const id = Number(route.params.id)
    if (!/^[1-9]\d*$/.test(String(route.params.id)) || !Number.isSafeInteger(id)) throw new Error('账单编号无效')
    const { data } = await waterApi.getBillItem(id)
    if (version === epoch && token === readToken()) bill.value = data
  } catch (e) { if (version === epoch) error.value = errorMessage(e) }
  finally { if (version === epoch) loading.value = false }
}
async function pay() {
  if (!bill.value || paying.value || settled.value) return
  paying.value = true; error.value = ''
  const version = epoch, token = readToken(), id = bill.value.id
  try {
    const latest = (await waterApi.getBillItem(id)).data
    if (version !== epoch || token !== readToken()) return
    bill.value = latest
    if (latest.status === '已缴' || Number(latest.outstanding) <= 0) return
    try { await ElMessageBox.confirm(`确认记录 ${latest.period} 水费 ${money(latest.outstanding)}？仅为模拟支付，不扣除真实资金。`, '确认模拟支付', { confirmButtonText: '确认模拟支付', cancelButtonText: '取消' }) } catch { return }
    if (version !== epoch || token !== readToken()) return
    const { data } = await waterApi.payBill(id, paymentKey(id))
    if (version !== epoch || token !== readToken()) return
    const current = (await waterApi.getBillItem(id)).data
    if (version === epoch && token === readToken()) { bill.value = current; receipt.value = data.transactionNo }
  } catch (e) { if (version === epoch) error.value = errorMessage(e) }
  finally { if (version === epoch) paying.value = false }
}
watch(() => route.params.id, () => { void reload() }, { immediate: true })
onBeforeUnmount(() => { epoch++ })
function logout() {
  epoch++; bill.value = null; receipt.value = ''; store.logout()
  void router.replace({ name: 'login', query: { redirect: route.fullPath } })
}
</script>
<template>
  <main class="payment-page"><section class="payment-card">
    <h1>水费支付确认</h1><el-tag type="warning">模拟演示 · 非真实收款</el-tag>
    <p>当前账号：{{ store.currentUser?.name }}</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <p v-if="loading">正在查询账单…</p>
    <template v-if="bill">
      <dl><dt>账期</dt><dd>{{ bill.period }}</dd><dt>水表</dt><dd>{{ bill.meterId }}</dd><dt>水费</dt><dd>{{ money(bill.waterFee) }}</dd><dt>违约金</dt><dd>{{ money(bill.penalty) }}</dd><dt>待缴金额</dt><dd><strong>{{ money(bill.outstanding) }}</strong></dd></dl>
      <el-result v-if="settled" icon="success" title="账单已结清" :sub-title="receipt ? '模拟流水号：' + receipt : ''" />
      <el-button v-else type="primary" size="large" :loading="paying" :disabled="loading" @click="pay">确认模拟支付 {{ money(bill.outstanding) }}</el-button>
    </template>
    
    <div class="payment-actions"><el-button :disabled="paying || loading" @click="reload">重新查询</el-button><router-link :to="homeFor(store.role)">返回工作台</router-link><el-button link :disabled="paying" @click="logout">退出并切换账号</el-button></div>
  </section></main>
</template>
<style scoped>.payment-page{min-height:100vh;background:#eff6f7;padding:24px 12px;display:flex;align-items:flex-start;justify-content:center}.payment-card{width:100%;max-width:480px;background:white;padding:28px;border-radius:18px;box-shadow:0 12px 40px #153f4910;overflow-wrap:anywhere}.payment-card h1{font-size:24px}.payment-card p{font-size:14px;color:#64748b;line-height:1.7}.payment-card dl{display:grid;grid-template-columns:100px 1fr;gap:12px;margin:28px 0}.payment-card dd{margin:0;text-align:right}.payment-card strong{font-size:25px;color:#147b87}.payment-actions{display:flex;gap:14px;align-items:center;flex-wrap:wrap}</style>
