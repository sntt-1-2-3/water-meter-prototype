<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import BrandMark from '../../components/BrandMark.vue'
import { useSystemStore } from '../../stores/system'

const router = useRouter()
const store = useSystemStore()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

function fillDemo(role: 'admin' | 'user') {
  form.username = role === 'admin' ? 'admin' : 'user'
  form.password = role === 'admin' ? 'admin123' : 'user123'
}

async function submit() {
  if (!form.username.trim() || !form.password) return ElMessage.warning('请输入账号和密码')
  loading.value = true
  try {
    const user = store.login(form.username, form.password)
    ElMessage.success(`欢迎回来，${user.name}`)
    await router.replace(user.role === 'admin' ? '/admin/dashboard' : '/user/home')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-visual">
      <div class="auth-brand"><BrandMark /><div><strong>智水云</strong><span>AI 水表抄表收费管理系统</span></div></div>
      <div class="auth-hero-copy"><span>SMART WATER OPERATIONS</span><h1>让每一滴水<br>都清晰可见</h1><p>自动抄表、阶梯计费、异常预警与数据分析一体化管理。</p></div>
      <div class="auth-metrics"><div><strong>1,000</strong><span>在线水表</span></div><div><strong>98.6%</strong><span>今日抄表率</span></div><div><strong>24h</strong><span>异常监测</span></div></div>
    </section>
    <section class="auth-form-side">
      <div class="auth-card">
        <div class="auth-card-head"><span>账户登录</span><h2>欢迎使用智水云</h2><p>请使用分配的账号进入对应工作台</p></div>
        <el-form label-position="top" @submit.prevent="submit">
          <el-form-item label="账号"><el-input v-model="form.username" size="large" :prefix-icon="User" autocomplete="username" placeholder="请输入账号" @keyup.enter="submit" /></el-form-item>
          <el-form-item label="密码"><el-input v-model="form.password" size="large" :prefix-icon="Lock" type="password" show-password autocomplete="current-password" placeholder="请输入密码" @keyup.enter="submit" /></el-form-item>
          <el-button class="auth-submit" type="primary" size="large" :loading="loading" @click="submit">登录系统</el-button>
        </el-form>
        <div class="demo-accounts"><span>演示账号快速填入</span><div><button type="button" @click="fillDemo('admin')">管理员 admin / admin123</button><button type="button" @click="fillDemo('user')">用户 user / user123</button></div></div>
        <p class="auth-switch">还没有居民账号？<router-link to="/register">立即注册</router-link></p>
      </div>
      <p class="auth-footnote">竞赛原型 · 当前账号数据保存在本机浏览器</p>
    </section>
  </main>
</template>
