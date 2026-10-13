<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import BrandMark from '../../components/BrandMark.vue'
import { useSystemStore } from '../../stores/system'
import { errorMessage } from '../../utils/format'
const router = useRouter()
const store = useSystemStore()
const loading = ref(false)
const form = reactive({ username: '', name: '', phone: '', password: '', confirmPassword: '' })
async function submit() {
  if (loading.value) return
  if (!form.username.trim() || !form.name.trim() || !form.password) return ElMessage.warning('请填写账号、姓名和密码')
  if (!/^[a-zA-Z0-9_]{3,40}$/.test(form.username.trim())) return ElMessage.warning('账号需为 3–40 位字母、数字或下划线')
  if (form.name.trim().length > 60) return ElMessage.warning('姓名不能超过 60 个字符')
  if (form.phone && !/^1\d{10}$/.test(form.phone)) return ElMessage.warning('请输入正确的 11 位手机号')
  if (form.password.length < 8 || form.password.length > 72) return ElMessage.warning('密码需要 8–72 个字符')
  if (new TextEncoder().encode(form.password).length > 72) return ElMessage.warning('密码 UTF-8 长度不能超过 72 字节，请减少字符')
  if (form.password !== form.confirmPassword) return ElMessage.warning('两次输入的密码不一致')
  loading.value = true
  try {
    await store.register({ username: form.username, name: form.name, phone: form.phone, password: form.password })
    form.password = ''; form.confirmPassword = ''
    ElMessage.success('注册成功，请联系管理员绑定水表')
    await router.replace('/user/home')
  } catch (error) { ElMessage.error(errorMessage(error)) } finally { loading.value = false }
}
</script>
<template>
  <main class="auth-page register-page">
    <section class="auth-visual">
      <div class="auth-brand"><BrandMark /><div><strong>智水云</strong><span>居民用水服务平台</span></div></div>
      <div class="auth-hero-copy"><span>RESIDENT SERVICE</span><h1>随时了解用水<br>安心管理账单</h1><p>注册后由管理员绑定水表，即可查询个人账单、缴费记录与反馈结果。</p></div>
      <div class="auth-feature-list"><span>仅查询本人绑定的水表</span><span>缴费记录可追溯</span><span>反馈处理结果及时通知</span></div>
    </section>
    <section class="auth-form-side">
      <div class="auth-card register-card">
        <div class="auth-card-head"><span>居民注册</span><h2>创建居民用水账户</h2><p>管理人员账号由系统分配，注册账号默认为居民用户</p></div>
        <el-form label-position="top" @submit.prevent="submit">
          <div class="auth-form-grid">
            <el-form-item label="登录账号"><el-input v-model="form.username" autocomplete="username" maxlength="40" placeholder="3–40 位字母、数字或下划线" /></el-form-item>
            <el-form-item label="姓名"><el-input v-model="form.name" maxlength="60" placeholder="请输入用户姓名" /></el-form-item>
            <el-form-item label="手机号（可选）"><el-input v-model="form.phone" maxlength="11" placeholder="请输入 11 位手机号" /></el-form-item>
            <el-form-item label="登录密码"><el-input v-model="form.password" type="password" autocomplete="new-password" maxlength="72" show-password placeholder="8–72 个字符，最多 72 字节" /></el-form-item>
            <el-form-item label="确认密码" class="auth-grid-full"><el-input v-model="form.confirmPassword" type="password" autocomplete="new-password" show-password placeholder="请再次输入密码" @keyup.enter="submit" /></el-form-item>
          </div><el-button class="auth-submit" type="primary" size="large" :loading="loading" @click="submit">注册并进入用户端</el-button>
        </el-form><p class="auth-switch">已经有账号？<router-link to="/login">返回登录</router-link></p>
      </div><p class="auth-footnote">密码由后端加密保存，不保存在浏览器账户列表中</p>
    </section>
  </main>
</template>
