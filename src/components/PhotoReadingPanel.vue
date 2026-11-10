<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { Camera, Refresh, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { photoReadingsApi } from '../api/photoReadings'
import { waterApi } from '../api/water'
import { useSystemStore } from '../stores/system'
import { readToken } from '../utils/session'
import { dateTime, errorMessage } from '../utils/format'
import type { Meter } from '../types/domain'
import type { PhotoCrop, PhotoReading, PhotoRotation } from '../types/photoReading'

const emit = defineEmits<{ confirmed: [] }>()
const store = useSystemStore()
const actor = computed(() => `${store.currentUser?.id || ''}:${store.role}:${store.currentUser?.areaScope || ''}:${readToken()}`)
const meters = ref<Meter[]>([])
const meterId = ref('')
const metersLoading = ref(false)
const metersError = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const cameraInput = ref<HTMLInputElement | null>(null)
const file = ref<File | null>(null)
const fileError = ref('')
const records = ref<PhotoReading[]>([])
const historyPage = ref(1)
const historyTotal = ref(0)
const historyLoading = ref(false)
const historyError = ref('')
const draft = ref<PhotoReading | null>(null)
const draftError = ref('')
const draftRefreshNotice = ref('')
const draftLoading = ref(false)
const action = ref<'' | 'upload' | 'recognize' | 'confirm'>('')
const uploadStage = ref<'sending' | 'preview'>('sending')
const uploadPercent = ref(0)
const previewUrl = ref('')
const canvas = ref<HTMLCanvasElement | null>(null)
const imageReady = ref(false)
const rotation = ref<PhotoRotation>(0)
const crop = ref<PhotoCrop | null>(null)
const reading = ref('')
const note = ref('')
const checked = ref(false)
const selectedCandidate = ref<string | null>(null)
const decimalAdjustment = ref('')
const dragging = ref(false)
let sourceImage: HTMLImageElement | null = null
let dragStart: { x: number; y: number } | null = null
let version = 0
let historyRequest = 0
let active = true
let requestController: AbortController | null = null
const busy = computed(() => Boolean(action.value) || draftLoading.value || metersLoading.value)
const confirmed = computed(() => draft.value?.status === 'CONFIRMED')
const displayWidth = computed(() => rotation.value === 90 || rotation.value === 270 ? draft.value?.imageHeight || 0 : draft.value?.imageWidth || 0)
const displayHeight = computed(() => rotation.value === 90 || rotation.value === 270 ? draft.value?.imageWidth || 0 : draft.value?.imageHeight || 0)
const cropStyle = computed(() => crop.value && displayWidth.value && displayHeight.value ? {
  left: `${crop.value.x / displayWidth.value * 100}%`, top: `${crop.value.y / displayHeight.value * 100}%`,
  width: `${crop.value.width / displayWidth.value * 100}%`, height: `${crop.value.height / displayHeight.value * 100}%`,
} : {})
const geometryChanged = computed(() => {
  if (!draft.value) return false
  const previous = draft.value.crop
  const current = crop.value
  const changedCrop = current && previous ? ['x', 'y', 'width', 'height'].some(key => current[key as keyof PhotoCrop] !== previous[key as keyof PhotoCrop]) : Boolean(current) !== Boolean(previous)
  return rotation.value !== draft.value.rotation || changedCrop
})
const decimalPattern = /^\d{1,9}(?:\.\d{1,3})?$/
function scaled(value: string) {
  const [whole, fraction = ''] = value.split('.')
  return BigInt(whole || '0') * 1000n + BigInt(fraction.padEnd(3, '0'))
}
function unscaled(value: bigint) {
  const integer = value / 1000n
  const fraction = (value % 1000n).toString().padStart(3, '0').replace(/0+$/, '')
  return `${integer}${fraction ? '.' + fraction : ''}`
}
const readingError = computed(() => {
  if (!draft.value || confirmed.value) return ''
  if (!reading.value.trim()) return '请填写原图所示的累计读数'
  if (!decimalPattern.test(reading.value.trim())) return '读数最多 9 位整数、3 位小数，仅支持数字与小数点'
  if (!decimalPattern.test(draft.value.previousReading)) return '当前累计读数格式异常，请刷新草稿或联系管理员'
  return scaled(reading.value.trim()) < scaled(draft.value.previousReading) ? '确认读数不能小于当前累计读数；请核对水表与小数位' : ''
})
const increment = computed(() => draft.value && !readingError.value && !confirmed.value ? unscaled(scaled(reading.value.trim()) - scaled(draft.value.previousReading)) : '—')
const canConfirm = computed(() => Boolean(draft.value) && !confirmed.value && !busy.value && imageReady.value && checked.value && !readingError.value && !geometryChanged.value)
const canAdjustDecimal = computed(() => selectedCandidate.value !== null && /^\d+$/.test(selectedCandidate.value))
const statusLabel = (value: PhotoReading) => value.status === 'CONFIRMED' ? '已确认入库' : !value.recognizedAt ? '已上传，待识别' : value.ocrError ? '待人工核对' : '待确认'
const isCurrent = (requestVersion: number, account: string) => active && version === requestVersion && actor.value === account

function releaseImage() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''; sourceImage = null; imageReady.value = false
}
function clearDraft() {
  version += 1
  requestController?.abort(); requestController = null
  releaseImage()
  draft.value = null; draftError.value = ''; draftRefreshNotice.value = ''; reading.value = ''; note.value = ''; checked.value = false
  rotation.value = 0; crop.value = null; selectedCandidate.value = null; decimalAdjustment.value = ''
  dragging.value = false; dragStart = null; action.value = ''; draftLoading.value = false
}
function applyDraft(value: PhotoReading) {
  draftRefreshNotice.value = ''
  draft.value = value; rotation.value = value.rotation; crop.value = value.crop ? { ...value.crop } : null
  reading.value = value.confirmedReading ?? value.suggestedReading ?? ''
  note.value = value.note || ''; checked.value = false; selectedCandidate.value = value.suggestedReading
  decimalAdjustment.value = ''; dragging.value = false; dragStart = null
}
async function loadMeters() {
  const account = actor.value
  metersLoading.value = true; metersError.value = ''
  try {
    const first = await waterApi.getMeters({ page: 1, pageSize: 200 })
    const pages = Math.ceil(first.data.total / 200)
    const rest = await Promise.all(Array.from({ length: Math.max(0, pages - 1) }, (_, index) => waterApi.getMeters({ page: index + 2, pageSize: 200 })))
    if (!active || account !== actor.value) return
    meters.value = [...first.data.items, ...rest.flatMap(item => item.data.items)]
  } catch (error) { if (active && account === actor.value) metersError.value = errorMessage(error) }
  finally { if (active && account === actor.value) metersLoading.value = false }
}
async function loadHistory() {
  if (!store.canOperate) return
  const account = actor.value
  const request = ++historyRequest
  historyLoading.value = true; historyError.value = ''
  try {
    const { data } = await photoReadingsApi.list({ meterId: meterId.value || undefined, page: historyPage.value, pageSize: 20 })
    if (!active || account !== actor.value || request !== historyRequest) return
    records.value = data.items; historyTotal.value = data.total
  } catch (error) { if (active && account === actor.value && request === historyRequest) historyError.value = errorMessage(error) }
  finally { if (active && account === actor.value && request === historyRequest) historyLoading.value = false }
}
function drawImage() {
  const element = canvas.value
  if (!element || !sourceImage || !displayWidth.value || !displayHeight.value) return
  element.width = displayWidth.value; element.height = displayHeight.value
  const context = element.getContext('2d')
  if (!context) return
  context.save()
  if (rotation.value === 90) { context.translate(element.width, 0); context.rotate(Math.PI / 2) }
  else if (rotation.value === 180) { context.translate(element.width, element.height); context.rotate(Math.PI) }
  else if (rotation.value === 270) { context.translate(0, element.height); context.rotate(-Math.PI / 2) }
  context.drawImage(sourceImage, 0, 0)
  context.restore()
}
async function prepareImage(id: number, requestVersion: number, account: string, signal?: AbortSignal) {
  const { data } = await photoReadingsApi.image(id, signal)
  if (!isCurrent(requestVersion, account)) return null
  const url = URL.createObjectURL(data)
  const image = new Image()
  image.src = url
  try { await image.decode() }
  catch {
    URL.revokeObjectURL(url)
    if (isCurrent(requestVersion, account)) throw new Error('照片预览加载失败，请重新打开草稿')
    return null
  }
  if (!isCurrent(requestVersion, account)) { URL.revokeObjectURL(url); return null }
  return { url, image }
}
function commitImage(prepared: { url: string; image: HTMLImageElement }) {
  releaseImage()
  previewUrl.value = prepared.url; sourceImage = prepared.image; imageReady.value = true
}
async function loadImage(id: number, requestVersion: number, account: string, signal?: AbortSignal) {
  const prepared = await prepareImage(id, requestVersion, account, signal)
  if (!prepared) return
  if (!isCurrent(requestVersion, account)) { URL.revokeObjectURL(prepared.url); return }
  commitImage(prepared)
  await nextTick(); drawImage()
}
async function openDraft(id: number) {
  if (busy.value) return
  const refreshingCurrent = draft.value?.id === id
  const previousBaseline = draft.value?.previousReading
  const manualInput = { reading: reading.value, note: note.value, candidate: selectedCandidate.value, adjustment: decimalAdjustment.value }
  if (refreshingCurrent) {
    version += 1; checked.value = false; draftError.value = ''; draftRefreshNotice.value = ''
    dragging.value = false; dragStart = null
  } else clearDraft()
  const requestVersion = version; const account = actor.value
  draftLoading.value = true
  try {
    const { data } = await photoReadingsApi.get(id)
    if (!isCurrent(requestVersion, account)) return
    const prepared = await prepareImage(data.id, requestVersion, account)
    if (!prepared) return
    if (!isCurrent(requestVersion, account)) { URL.revokeObjectURL(prepared.url); return }
    applyDraft(data)
    commitImage(prepared)
    if (refreshingCurrent && data.status === 'DRAFT') {
      reading.value = manualInput.reading; note.value = manualInput.note
      selectedCandidate.value = manualInput.candidate; decimalAdjustment.value = manualInput.adjustment
      draftRefreshNotice.value = previousBaseline !== data.previousReading
        ? `累计读数已从 ${previousBaseline} 更新为 ${data.previousReading} m³，请重新核对。`
        : '草稿已刷新，请重新核对。'
    }
    await nextTick(); drawImage()
  } catch (error) { if (isCurrent(requestVersion, account)) draftError.value = errorMessage(error) }
  finally { if (isCurrent(requestVersion, account)) draftLoading.value = false }
}
function chooseFile(event: Event) {
  const input = event.target as HTMLInputElement
  const candidate = input.files?.[0]
  input.value = ''
  if (!candidate) return
  fileError.value = ''
  file.value = null
  if (!['image/jpeg', 'image/png'].includes(candidate.type)) { fileError.value = '请选择 JPEG 或 PNG 照片'; return }
  if (candidate.size > 8 * 1024 * 1024) { fileError.value = '照片不能超过 8 MB'; return }
  if (!candidate.size) { fileError.value = '照片文件为空，请重新选择'; return }
  file.value = candidate
}
async function upload() {
  if (busy.value || !file.value || !meterId.value) return
  const selectedFile = file.value; const selectedId = meterId.value
  clearDraft()
  const requestVersion = version; const account = actor.value
  const controller = new AbortController(); requestController = controller
  uploadStage.value = 'sending'; uploadPercent.value = 0
  action.value = 'upload'
  try {
    const { data } = await photoReadingsApi.upload(selectedId, selectedFile, {
      signal: controller.signal,
      onUploadProgress: event => {
        if (isCurrent(requestVersion, account) && event.total) uploadPercent.value = Math.min(99, Math.round(event.loaded / event.total * 100))
      },
    })
    if (!isCurrent(requestVersion, account)) return
    applyDraft(data)
    file.value = null
    uploadStage.value = 'preview'; uploadPercent.value = 100
    historyPage.value = 1; void loadHistory()
    await loadImage(data.id, requestVersion, account, controller.signal)
    if (!isCurrent(requestVersion, account)) return
    action.value = 'recognize'
    const result = await photoReadingsApi.recognize(data.id, 0, null, controller.signal)
    if (!isCurrent(requestVersion, account)) return
    applyDraft(result.data)
    await nextTick(); drawImage()
    void loadHistory()
  } catch (error) {
    if (isCurrent(requestVersion, account)) {
      draftError.value = draft.value ? `照片已保存，但后续处理未完成：${errorMessage(error)}。可重新识别或人工填写，无需再次上传。` : errorMessage(error)
      void loadHistory()
    }
  } finally {
    if (requestController === controller) requestController = null
    if (isCurrent(requestVersion, account)) action.value = ''
  }
}
function stopWaiting() {
  if (!requestController) return
  version += 1
  requestController.abort(); requestController = null
  action.value = ''; checked.value = false
  draftRefreshNotice.value = draft.value
    ? '已停止等待，照片草稿已保留。可核对原图后人工填写，或稍后重新识别。服务器可能仍在完成当前识别。'
    : '已停止等待。若服务器已接收照片，可在下方照片记录中继续核对；请先刷新记录，避免重复上传。'
  void loadHistory()
}
async function recognize() {
  if (!draft.value || busy.value || confirmed.value) return
  const requestVersion = version; const account = actor.value
  const id = draft.value.id
  const controller = new AbortController(); requestController = controller
  action.value = 'recognize'; draftError.value = ''; checked.value = false
  try {
    const { data } = await photoReadingsApi.recognize(id, rotation.value, crop.value, controller.signal)
    if (!isCurrent(requestVersion, account)) return
    applyDraft(data)
    await nextTick(); drawImage()
    void loadHistory()
  } catch (error) { if (isCurrent(requestVersion, account)) draftError.value = errorMessage(error) }
  finally {
    if (requestController === controller) requestController = null
    if (isCurrent(requestVersion, account)) action.value = ''
  }
}
async function rotate() {
  if (busy.value || confirmed.value || !imageReady.value) return
  rotation.value = ((rotation.value + 90) % 360) as PhotoRotation
  crop.value = null; checked.value = false
  drawImage()
  await recognize()
}
function point(event: PointerEvent) {
  const bounds = canvas.value!.getBoundingClientRect()
  return { x: Math.max(0, Math.min(displayWidth.value, Math.round((event.clientX - bounds.left) / bounds.width * displayWidth.value))), y: Math.max(0, Math.min(displayHeight.value, Math.round((event.clientY - bounds.top) / bounds.height * displayHeight.value))) }
}
function startCrop(event: PointerEvent) {
  if (busy.value || confirmed.value || !imageReady.value || event.button !== 0) return
  event.preventDefault()
  canvas.value?.setPointerCapture(event.pointerId)
  dragStart = point(event); crop.value = null; dragging.value = true; checked.value = false
}
function moveCrop(event: PointerEvent) {
  if (!dragging.value || !dragStart) return
  const end = point(event)
  crop.value = { x: Math.min(dragStart.x, end.x), y: Math.min(dragStart.y, end.y), width: Math.abs(end.x - dragStart.x), height: Math.abs(end.y - dragStart.y) }
}
function finishCrop(event: PointerEvent) {
  if (!dragging.value) return
  moveCrop(event)
  dragging.value = false; dragStart = null
  if (canvas.value?.hasPointerCapture(event.pointerId)) canvas.value.releasePointerCapture(event.pointerId)
  if (!crop.value || crop.value.width < 12 || crop.value.height < 12) { crop.value = null; ElMessage.info('选区过小，请框选完整数字区域') }
}
function cancelCrop() { dragging.value = false; dragStart = null; crop.value = null }
function useCandidate(value: string) { reading.value = value; selectedCandidate.value = value; decimalAdjustment.value = ''; checked.value = false }
function editReading() { decimalAdjustment.value = ''; checked.value = false }
function applyDecimals(places: number) {
  if (!canAdjustDecimal.value || busy.value || confirmed.value) return
  const original = selectedCandidate.value!
  const digits = original.padStart(places + 1, '0')
  const integer = (places ? digits.slice(0, -places) : digits).replace(/^0+(?=\d)/, '')
  const result = places ? `${integer}.${digits.slice(-places)}` : integer
  reading.value = result
  decimalAdjustment.value = `人工小数位调整：候选 ${original} 按 ${places} 位小数应用为 ${result} m³`
  checked.value = false
}
async function confirmReading() {
  if (!canConfirm.value || !draft.value) return
  const requestVersion = version; const account = actor.value
  const id = draft.value.id; const value = reading.value.trim(); const previous = draft.value.previousReading
  const auditNote = [note.value.trim(), decimalAdjustment.value].filter(Boolean).join('；')
  action.value = 'confirm'; draftError.value = ''
  try {
    await ElMessageBox.confirm(`水表 ${draft.value.meterId}：${previous} → ${value} m³，本次增量 ${increment.value} m³。确认入库？`, '确认照片抄表入库', { confirmButtonText: '确认入库', cancelButtonText: '返回核对', type: 'warning' })
    if (!isCurrent(requestVersion, account)) return
    const { data } = await photoReadingsApi.confirm(id, value, previous, auditNote)
    if (!isCurrent(requestVersion, account)) return
    applyDraft(data)
    await loadHistory()
    emit('confirmed')
    ElMessage.success('照片抄表已入库')
  } catch (error) {
    if (isCurrent(requestVersion, account) && error !== 'cancel' && error !== 'close') { draftError.value = errorMessage(error); checked.value = false }
  } finally { if (isCurrent(requestVersion, account)) action.value = '' }
}
async function meterChanged() {
  if (busy.value) return
  clearDraft(); historyPage.value = 1
  await loadHistory()
}
watch(reading, () => { checked.value = false })
watch(actor, () => {
  clearDraft(); ++historyRequest; meters.value = []; meterId.value = ''; file.value = null
  records.value = []; historyTotal.value = 0; historyPage.value = 1; historyError.value = ''; historyLoading.value = false
  metersError.value = ''; metersLoading.value = false; fileError.value = ''
  if (store.canOperate) { void loadMeters(); void loadHistory() }
}, { immediate: true })
onBeforeUnmount(() => { active = false; version += 1; historyRequest += 1; requestController?.abort(); releaseImage() })
</script>

<template>
  <section v-if="store.canOperate" class="panel data-panel photo-panel">
    <div class="panel-head"><div><h2>拍照识别抄表</h2></div></div>
    
    <div v-if="metersError" class="photo-error" role="alert">{{ metersError }} <el-button link type="primary" :disabled="busy" @click="loadMeters">重试水表列表</el-button></div>
    <div class="photo-upload-row">
      <el-select v-model="meterId" filterable clearable :disabled="busy" :loading="metersLoading" placeholder="选择水表" class="photo-meter-select" @change="meterChanged"><el-option v-for="item in meters" :key="item.id" :value="item.id" :label="`${item.id} · ${item.user} · ${item.area}`" /></el-select>
      <input ref="fileInput" class="photo-file-input" type="file" accept="image/jpeg,image/png" :disabled="busy" @change="chooseFile">
      <input ref="cameraInput" class="photo-file-input" type="file" accept="image/jpeg,image/png" capture="environment" :disabled="busy" @change="chooseFile">
      <el-button :icon="UploadFilled" :disabled="busy" @click="fileInput?.click()">选择照片</el-button><el-button :icon="Camera" :disabled="busy" @click="cameraInput?.click()">拍照</el-button>
      <el-button type="primary" :loading="action === 'upload'" :disabled="busy || !meterId || !file" @click="upload">上传并识别</el-button>
    </div>
    <div v-if="file" class="photo-file-meta">{{ file.name }} · {{ (file.size / 1024 / 1024).toFixed(2) }} MB</div>
    <div v-if="fileError" class="photo-error" role="alert">{{ fileError }}</div>
    <div v-if="draftError" class="photo-error" role="alert">{{ draftError }}</div>
    <div v-if="draftRefreshNotice" class="photo-warning" role="status">{{ draftRefreshNotice }}</div>
    <div v-if="action === 'upload' || action === 'recognize'" class="photo-progress" role="status" aria-live="polite">
      <div><strong>{{ action === 'recognize' ? '照片已保存，正在识别数字' : uploadStage === 'preview' ? '照片已保存，正在加载原图' : `正在上传照片 · ${uploadPercent}%` }}</strong><el-button size="small" @click="stopWaiting">停止等待</el-button></div>
      <el-progress v-if="action === 'upload'" :percentage="uploadPercent" :stroke-width="6" />
      
    </div>
    <el-skeleton v-if="draftLoading || (action === 'upload' && !draft)" :rows="5" animated />
    <div v-if="draft" class="photo-workspace">
      <div class="photo-preview-column">
        <div class="photo-section-heading"><h3>原图与核对选区</h3><span>{{ draft.meterId }} · {{ draft.meterUserName }}</span></div>
        <div v-if="previewUrl" class="photo-image-stage" :class="{ 'photo-selectable': !busy && !confirmed }"><canvas ref="canvas" role="img" aria-label="水表原图，可拖动框选数字区域" @pointerdown="startCrop" @pointermove="moveCrop" @pointerup="finishCrop" @pointercancel="cancelCrop"></canvas><div v-if="crop" class="photo-crop-overlay" :style="cropStyle"><span>数字选区</span></div></div>
        <div v-else class="photo-image-empty">照片预览未就绪，请重新打开草稿加载原图</div>
        <div class="photo-image-tools"><el-button :disabled="busy || confirmed || !imageReady" @click="rotate">顺时针 90° 并识别</el-button><el-button :disabled="busy || confirmed || !crop" @click="crop = null; checked = false">清除选区</el-button><el-button type="primary" plain :loading="action === 'recognize'" :disabled="busy || confirmed || !imageReady" @click="recognize">{{ crop ? '识别选区' : '重新识别整图' }}</el-button></div>
        
        
        <div v-if="geometryChanged" class="photo-warning">已调整选区，请重新识别后再核对确认。</div>
      </div>
      <div class="photo-result-column">
        <div class="photo-section-heading"><h3>识别结果与人工核对</h3><el-tag :type="confirmed ? 'success' : 'warning'">{{ statusLabel(draft) }}</el-tag></div>
        <div v-if="draft.ocrError" class="photo-error" role="alert"><strong>识别未成功</strong><p>{{ draft.ocrError }}</p></div>
        <details class="photo-details"><summary>识别详情</summary>
          <div class="photo-raw"><span>OCR 原文</span><pre>{{ draft.rawText || '未识别到文字' }}</pre></div>
          <div class="photo-reference"><span>识别建议：<strong>{{ draft.suggestedReading ?? '无' }}</strong></span><span>参考分：{{ draft.confidence == null ? '无' : `${draft.confidence.toFixed(1)} / 100` }}</span></div>
        </details>
        
        <div v-if="draft.candidates.length" class="photo-candidates"><span>候选读数</span><el-button v-for="candidate in draft.candidates" :key="candidate" size="small" :disabled="busy || confirmed" @click="useCandidate(candidate)">{{ candidate }}</el-button></div>
        <el-form label-position="top" class="photo-confirm-form">
          <div class="photo-reading-baseline"><span>{{ confirmed ? '确认前读数' : '当前累计读数' }}</span><strong>{{ confirmed ? draft.confirmedPreviousReading : draft.previousReading }} m³</strong></div>
          <el-form-item label="确认读数（m³）"><el-input v-model="reading" inputmode="decimal" maxlength="13" placeholder="输入累计读数" :disabled="busy || confirmed" @input="editReading" /><span v-if="readingError" class="photo-field-error">{{ readingError }}</span></el-form-item>
          <div v-if="canAdjustDecimal && !confirmed" class="photo-decimal-helper"><strong>小数位调整</strong><div><el-button v-for="places in [0, 1, 2, 3]" :key="places" size="small" :disabled="busy" @click="applyDecimals(places)">{{ places }} 位小数</el-button></div></div>
          <p v-if="decimalAdjustment" class="photo-warning">{{ decimalAdjustment }}</p>
          <p v-if="!confirmed" class="photo-increment">本次增量 <strong>{{ increment }} m³</strong></p>
          <el-form-item label="备注"><el-input v-model="note" type="textarea" :rows="2" maxlength="300" show-word-limit :disabled="busy || confirmed" placeholder="填写备注" /></el-form-item>
          <template v-if="!confirmed"><label class="photo-check"><input v-model="checked" type="checkbox" :disabled="busy || !imageReady || geometryChanged"> 我已核对原图与读数</label><div class="photo-confirm-actions"><el-button type="primary" :loading="action === 'confirm'" :disabled="!canConfirm" @click="confirmReading">确认入库</el-button><el-button :disabled="busy" @click="openDraft(draft.id)">刷新草稿</el-button></div></template>
          <div v-else class="photo-confirmed-audit"><strong>已确认 {{ draft.confirmedReading }} m³</strong><span>{{ draft.confirmedBy }} · {{ dateTime(draft.confirmedAt) }}</span><span>{{ draft.manuallyEdited ? '已人工调整识别建议' : '按核对后的建议确认' }} · 抄表记录 #{{ draft.readingId }}</span><span>当前服务端累计：{{ draft.previousReading }} m³</span><p v-if="draft.note">备注：{{ draft.note }}</p></div>
        </el-form>
      </div>
    </div>
    <div class="photo-history">
      <div class="panel-head"><div><h2>照片抄表记录</h2></div><el-button :icon="Refresh" :loading="historyLoading" :disabled="busy" @click="loadHistory">刷新照片记录</el-button></div>
      <div v-if="historyError" class="photo-error" role="alert">{{ historyError }}</div>
      <el-table :data="records" empty-text="暂无照片记录，选择水表并上传第一张照片"><el-table-column prop="meterId" label="水表编号" min-width="115" /><el-table-column prop="meterUserName" label="用户" min-width="100" /><el-table-column label="识别建议" min-width="115"><template #default="{ row }">{{ row.suggestedReading ?? '未识别' }}</template></el-table-column><el-table-column label="最终读数（m³）" min-width="135"><template #default="{ row }">{{ row.confirmedReading ?? '尚未确认' }}</template></el-table-column><el-table-column label="状态" width="120"><template #default="{ row }"><el-tag :type="row.status === 'CONFIRMED' ? 'success' : 'warning'">{{ statusLabel(row) }}</el-tag></template></el-table-column><el-table-column label="上传时间" min-width="160"><template #default="{ row }">{{ dateTime(row.createdAt) }}</template></el-table-column><el-table-column label="操作人" min-width="110"><template #default="{ row }">{{ row.confirmedBy || row.uploadedBy }}</template></el-table-column><el-table-column label="操作" width="110" fixed="right"><template #default="{ row }"><el-button link type="primary" :disabled="busy" @click="openDraft(row.id)">{{ row.status === 'CONFIRMED' ? '查看原图' : '继续核对' }}</el-button></template></el-table-column></el-table>
      <div v-if="historyTotal" class="photo-pagination"><span>共 {{ historyTotal }} 条</span><el-pagination v-model:current-page="historyPage" :disabled="busy" background layout="prev, pager, next" :total="historyTotal" :page-size="20" @current-change="loadHistory" /></div>
    </div>
  </section>
</template>

<style scoped>
.photo-panel { margin-bottom: 24px; }
.photo-upload-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.photo-meter-select { width: 350px; max-width: 100%; }
.photo-file-input { display: none; }
.photo-file-meta { display: flex; flex-wrap: wrap; gap: 8px 22px; margin-top: 10px; color: #74868d; font-size: 12px; overflow-wrap: anywhere; }
.photo-error { margin: 12px 0; padding: 12px 14px; border: 1px solid #f2c5c1; border-radius: 8px; background: #fff5f3; color: #a13e34; font-size: 13px; line-height: 1.7; overflow-wrap: anywhere; }
.photo-error p { margin: 4px 0; }
.photo-progress { margin: 14px 0; padding: 14px; border: 1px solid #c9e1e5; border-radius: 9px; background: #f0f9fa; color: #305e67; }
.photo-progress > div:first-child { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.photo-progress strong { font-size: 13px; }
.photo-progress .el-progress { margin-top: 10px; }
.photo-workspace { display: grid; grid-template-columns: minmax(0, 1.06fr) minmax(0, 1fr); gap: 24px; margin-top: 24px; align-items: start; }
.photo-preview-column, .photo-result-column { min-width: 0; }
.photo-section-heading { display: flex; gap: 10px; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.photo-section-heading h3 { font-size: 14px; margin: 0; color: #274b54; }
.photo-section-heading > span { color: #75888d; font-size: 12px; }
.photo-image-stage { position: relative; width: 100%; overflow: hidden; background: #eff3f4; border: 1px solid #d8e3e6; border-radius: 10px; line-height: 0; }
.photo-image-stage canvas { display: block; width: 100%; height: auto; touch-action: none; }
.photo-selectable canvas { cursor: crosshair; }
.photo-crop-overlay { position: absolute; border: 2px solid #08b8a8; background: #07aaa416; box-shadow: 0 0 0 4000px #102c3838; pointer-events: none; }
.photo-crop-overlay span { position: absolute; top: 0; left: 0; padding: 5px 7px; line-height: 1.4; background: #087e8b; color: white; font-size: 11px; white-space: nowrap; }
.photo-image-empty { padding: 80px 16px; background: #f2f6f7; text-align: center; color: #7e9097; font-size: 13px; }
.photo-image-tools { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.photo-image-tools .el-button, .photo-candidates .el-button, .photo-decimal-helper .el-button, .photo-upload-row .el-button { margin-left: 0; }
.photo-details { margin-bottom: 16px; }
.photo-details summary { width: fit-content; padding: 4px 0; color: #587a83; font-size: 13px; cursor: pointer; }
.photo-details[open] summary { margin-bottom: 10px; }
.photo-warning { font-size: 12px; line-height: 1.8; color: #96691d; background: #fff8e9; padding: 9px 12px; border-radius: 7px; }
.photo-raw { border: 1px solid #e2eaec; border-radius: 8px; padding: 12px; }
.photo-raw > span { display: block; font-size: 12px; color: #789098; margin-bottom: 8px; }
.photo-raw pre { white-space: pre-wrap; overflow-wrap: anywhere; margin: 0; max-height: 150px; overflow: auto; font: 14px/1.7 ui-monospace, monospace; color: #294b55; }
.photo-reference { display: flex; flex-wrap: wrap; gap: 8px 20px; font-size: 13px; color: #57717a; margin-top: 12px; }
.photo-reference strong { color: #087e8b; }
.photo-candidates { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; margin: 12px 0; }
.photo-candidates > span { font-size: 12px; color: #75878d; margin-right: 5px; }
.photo-reading-baseline { padding: 12px 0; border-top: 1px solid #e6edef; display: flex; justify-content: space-between; gap: 12px; color: #6d838b; font-size: 13px; }
.photo-reading-baseline strong { color: #264d58; }
.photo-confirm-form .el-form-item { margin-bottom: 12px; }
.photo-field-error { display: block; width: 100%; margin-top: 5px; color: #b14e42; font-size: 12px; line-height: 1.6; }
.photo-decimal-helper { border: 1px dashed #c4d7d8; background: #f4f9f9; border-radius: 8px; padding: 12px; font-size: 12px; color: #4e7079; }
.photo-decimal-helper > div { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 9px; }
.photo-increment { font-size: 13px; color: #6e858b; }
.photo-increment strong { color: #087e8b; font-size: 19px; margin-left: 8px; }
.photo-check { display: flex; gap: 9px; align-items: flex-start; color: #365a64; line-height: 1.7; font-size: 13px; }
.photo-check input { margin-top: 5px; accent-color: #087e8b; width: 16px; height: 16px; flex-shrink: 0; }
.photo-confirm-actions { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 14px; }
.photo-confirm-actions .el-button { margin-left: 0; }
.photo-confirmed-audit { display: flex; flex-direction: column; gap: 7px; padding: 14px; border: 1px solid #c3e3d3; border-radius: 8px; background: #f0faf5; color: #3a7458; font-size: 13px; overflow-wrap: anywhere; }
.photo-confirmed-audit strong { font-size: 17px; }
.photo-confirmed-audit p { margin: 0; }
.photo-history { margin-top: 25px; border-top: 1px solid #e6edef; padding-top: 22px; }
.photo-pagination { display: flex; justify-content: space-between; align-items: center; margin-top: 15px; font-size: 12px; color: #75888f; }
@media (max-width: 1050px) { .photo-workspace { grid-template-columns: minmax(0, 1fr); }.photo-meter-select { width: 100%; } }
@media (max-width: 600px) { .photo-panel { padding: 15px; }.photo-section-heading, .photo-panel > .panel-head { align-items: flex-start; flex-direction: column; }.photo-reading-baseline { flex-direction: column; gap: 5px; }.photo-pagination { gap: 8px; flex-wrap: wrap; } }
</style>
