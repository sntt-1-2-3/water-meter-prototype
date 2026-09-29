<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import BrandMark from '../../components/BrandMark.vue'
import { useSystemStore } from '../../stores/system'

const router = useRouter()
const store = useSystemStore()
const loading = ref(false)
const form = reactive({ username: '', name: '', phone: '', password: '', confirmPassword: '' })

async function submit() {
  if (!form.username.trim() || !form.name.trim() || !form.phone.trim() || !form.password) return ElMessage.warning('请完整填写注册信息')
  if (!/^[a-zA-Z0-9_]{4,20}$/.test(form.username)) return ElMessage.warning('账号需为 4–20 位字母、数字或下划线')
  if (!/^1\d{10}$/.test(form.phone)) return ElMessage.warning('请输入正确的 11 位手机号')
  if (form.password.length < 6) return ElMessage.warning('密码至少需要 6 位')
  if (form.password !== form.confirmPassword) return ElMessage.warning('两次输入的密码不一致')
  loading.value = true
  try {
    const user = store.register(form)
    ElMessage.success(`注册成功，欢迎 ${user.name}`)
    await router.replace('/user/home')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth-page register-page">
    <section class="auth-visual">
      <div class="auth-brand"><BrandMark /><div><strong>智水云</strong><span>居民用水服务平台</span></div></div>
      <div class="auth-hero-copy"><span>RESIDENT SERVICE</span><h1>随时了解用水<br>安心管理账单</h1><p>注册居民账户后，可以查看用水趋势、历史账单、异常提醒并使用 AI 用水助手。</p></div>
      <div class="auth-feature-list"><span>用水数据透明可查</span><span>异常情况及时提醒</span><span>线上账单便捷管理</span></div>
    </section>
    <section class="auth-form-side">
      <div class="auth-card register-card">
        <div class="auth-card-head"><span>居民注册</span><h2>创建你的用水账户</h2><p>管理员账号由系统分配，注册账号默认为居民用户</p></div>
        <el-form label-position="top" @submit.prevent="submit">
          <div class="auth-form-grid"><el-form-item label="登录账号"><el-input v-model="form.username" placeholder="4–20 位字母、数字或下划线" /></el-form-item><el-form-item label="姓名"><el-input v-model="form.name" placeholder="请输入用户姓名" /></el-form-item><el-form-item label="手机号"><el-input v-model="form.phone" maxlength="11" placeholder="用于接收缴费和异常提醒" /></el-form-item><el-form-item label="登录密码"><el-input v-model="form.password" type="password" show-password placeholder="至少 6 位" /></el-form-item><el-form-item label="确认密码" class="auth-grid-full"><el-input v-model="form.confirmPassword" type="password" show-password placeholder="请再次输入密码" @keyup.enter="submit" /></el-form-item></div>
          <el-button class="auth-submit" type="primary" size="large" :loading="loading" @click="submit">注册并进入用户端</el-button>
        </el-form>
        <p class="auth-switch">已经有账号？<router-link to="/login">返回登录</router-link></p>
      </div>
      <p class="auth-footnote">竞赛原型 · 正式版本将由后端加密保存账号信息</p>
    </section>
  </main>
</template>
