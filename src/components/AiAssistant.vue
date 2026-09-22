<script setup lang="ts">
import { ref } from 'vue'
import { storeToRefs } from 'pinia'
import { ChatDotRound } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { askAssistant } from '../api/assistant'
import { useSystemStore } from '../stores/system'

const store = useSystemStore()
const { aiVisible } = storeToRefs(store)
const question = ref('')
const answer = ref('')
const thinking = ref(false)
const prompts = ['哪个小区本月用水量最高？', '列出高风险异常并说明原因', '生成本月运营摘要']

async function send(prompt?: string) {
  const text = prompt || question.value.trim()
  if (!text || thinking.value) return
  question.value = text
  answer.value = ''
  thinking.value = true
  try {
    answer.value = await askAssistant(text)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'AI 服务暂时不可用')
  } finally {
    thinking.value = false
  }
}
</script>

<template>
  <el-drawer v-model="aiVisible" title="智水 AI 助手" size="420px" direction="rtl">
    <div class="ai-intro">
      <div class="ai-avatar"><el-icon><ChatDotRound /></el-icon></div>
      <div><strong>你好，我可以查询系统数据</strong><p>当前默认使用模拟数据；配置后端地址后可调用真实 AI 接口。</p></div>
    </div>
    <div class="prompt-list">
      <button v-for="prompt in prompts" :key="prompt" type="button" @click="send(prompt)">{{ prompt }}</button>
    </div>
    <div v-if="thinking || answer" class="ai-response">
      <div class="ai-avatar small"><el-icon><ChatDotRound /></el-icon></div>
      <div><el-skeleton v-if="thinking" :rows="3" animated /><p v-else>{{ answer }}</p></div>
    </div>
    <div class="ai-composer">
      <el-input v-model="question" type="textarea" :rows="3" resize="none" placeholder="输入你想查询的问题……" @keydown.ctrl.enter="send()" />
      <div><span>Ctrl + Enter 发送</span><el-button type="primary" :loading="thinking" @click="send()">发送</el-button></div>
    </div>
  </el-drawer>
</template>
