<script setup lang="ts">
import { ref } from 'vue'
import { storeToRefs } from 'pinia'
import { ChatDotRound } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { askAssistant } from '../api/assistant'
import { useSystemStore } from '../stores/system'
import { errorMessage } from '../utils/format'
const store = useSystemStore()
const { aiVisible } = storeToRefs(store)
const question = ref('')
const answer = ref('')
const thinking = ref(false)
const prompts = ['查询当前用水和账单', '有哪些异常需要关注？', '生成当前运营摘要']
async function send(prompt?: string) {
  const text = prompt || question.value.trim()
  if (!text || thinking.value) return
  question.value = text; answer.value = ''; thinking.value = true
  try { answer.value = await askAssistant(text) }
  catch (error) { ElMessage.error(errorMessage(error)) } finally { thinking.value = false }
}
</script>
<template>
  <el-drawer v-model="aiVisible" title="智水 AI 助手" size="420px" direction="rtl">
    <div class="ai-intro"><div class="ai-avatar"><el-icon><ChatDotRound /></el-icon></div><div><strong>你好，我可以查询系统数据</strong><p>根据当前账号权限查询后端数据。请以账单明细和系统记录为准。</p></div></div>
    <div class="prompt-list"><button v-for="prompt in prompts" :key="prompt" type="button" :disabled="thinking" @click="send(prompt)">{{ prompt }}</button></div>
    <div v-if="thinking || answer" class="ai-response"><div class="ai-avatar small"><el-icon><ChatDotRound /></el-icon></div><div><el-skeleton v-if="thinking" :rows="3" animated /><p v-else style="white-space:pre-wrap">{{ answer }}</p></div></div>
    <div class="ai-composer"><el-input v-model="question" type="textarea" :rows="3" resize="none" placeholder="请输入要查询的问题" @keydown.ctrl.enter="send()" /><div><span>Ctrl + Enter 发送</span><el-button type="primary" :loading="thinking" @click="send()">发送</el-button></div></div>
  </el-drawer>
</template>
