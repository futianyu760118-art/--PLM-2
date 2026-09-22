<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <img src="@/assets/logo.svg" alt="logo" />
        <h2>恒剑光电 PLM 中台</h2>
        <p>V4.0 产品生命周期管理系统</p>
      </div>
      <el-form ref="loginRef" :model="form" :rules="rules" @keyup.enter="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" size="large" prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" size="large" prefix-icon="Lock" />
        </el-form-item>
        <el-button type="primary" size="large" :loading="loading" style="width:100%" @click="handleLogin">登 录</el-button>
      </el-form>
      <div class="login-tip">默认账号: admin / admin@123</div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const loginRef = ref()
const loading = ref(false)

const form = reactive({ username: 'admin', password: 'admin@123' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  await loginRef.value.validate()
  loading.value = true
  try {
    await userStore.login(form)
    ElMessage.success('登录成功')
    const redirect = route.query.redirect || '/'
    router.push(redirect)
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-container {
  height: 100vh; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #1e3c72 0%, #2a5298 50%, #409eff 100%);
}
.login-box {
  width: 400px; padding: 40px; background: #fff; border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.2);
  .login-header { text-align: center; margin-bottom: 32px;
    img { width: 64px; height: 64px; }
    h2 { margin: 12px 0 4px; color: #303133; font-size: 22px; }
    p { color: #909399; font-size: 13px; margin: 0; }
  }
  .login-tip { text-align: center; color: #c0c4cc; font-size: 12px; margin-top: 16px; }
}
</style>
