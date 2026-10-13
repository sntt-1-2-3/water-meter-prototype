<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import BrandMark from '../../components/BrandMark.vue'
import { useSystemStore } from '../../stores/system'
import { homeFor } from '../../utils/permissions'
import { errorMessage } from '../../utils/format'
const router = useRouter()
const store = useSystemStore()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
function fillDemo(username: string) { form.username = username; form.password = 'admin123' }
async function submit() {
  if (loading.value) return
  if (!form.username.trim() || !form.password) return ElMessage.warning('请输入账号和密码')
  loading.value = true
  try {
    const user = await store.login(form.username, form.password)
    form.password = ''
    ElMessage.success('欢迎回来，' + user.name)
    await router.replace(homeFor(user.role))
  } catch (error) { ElMessage.error(errorMessage(error)) } finally { loading.value = false }
}
</script>
<template>
  <main class="auth-page">
    <section class="auth-visual">
      <div class="auth-brand"><BrandMark /><div><strong>智水云</strong><span>AI 水表抄表收费管理系统</span></div></div>
      <div class="auth-hero-copy"><span>SMART WATER OPERATIONS</span><h1>让每一滴水<br>都有迹可循</h1><p>从抄表、计费到异常处置，查询当前账号授权范围内的数据。</p></div>
      <div class="auth-feature-list"><span>按角色与区域分配权限</span><span>个人账单和缴费记录</span><span>异常反馈与结果通知</span></div>
    </section>
    <section class="auth-form-side">
      <div class="auth-card">
        <div class="auth-card-head"><span>账户登录</span><h2>欢迎使用智水云</h2><p>使用后端账号进入对应工作台</p></div>
        <el-form label-position="top" @submit.prevent="submit">
          <el-form-item label="账号"><el-input v-model="form.username" size="large" :prefix-icon="User" autocomplete="username" placeholder="请输入账号" @keyup.enter="submit" /></el-form-item>
          <el-form-item label="密码"><el-input v-model="form.password" size="large" :prefix-icon="Lock" type="password" show-password autocomplete="current-password" placeholder="请输入密码" @keyup.enter="submit" /></el-form-item>
          <el-button class="auth-submit" type="primary" size="large" :loading="loading" @click="submit">登录系统</el-button>
        </el-form>
        <div class="demo-accounts"><span>本地测试账号（初始密码 admin123）</span><div>
          <button type="button" @click="fillDemo('admin')">管理员 admin</button>
          <button type="button" @click="fillDemo('operator_east')">运营 operator_east</button>
          <button type="button" @click="fillDemo('finance01')">财务 finance01</button>
          <button type="button" @click="fillDemo('auditor01')">审计 auditor01</button>
          <button type="button" @click="fillDemo('user_res_001')">居民 user_res_001</button>
        </div></div>
        <p class="auth-switch">还没有居民账号？<router-link to="/register">立即注册</router-link></p>
      </div><p class="auth-footnote">账号由后端校验；登录过期后需重新登录</p>
    </section>
  </main>
</template>
