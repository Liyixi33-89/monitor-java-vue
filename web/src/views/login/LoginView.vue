<template>
  <div style="display: flex; justify-content: center; align-items: center; height: 100vh; background: #f5f7fa">
    <el-card style="width: 360px">
      <h2 style="text-align: center; margin-top: 0">项目监控面板</h2>
      <el-form @submit.prevent="doLogin">
        <el-form-item>
          <el-input v-model="username" placeholder="用户名" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="password" type="password" placeholder="密码" show-password />
        </el-form-item>
        <el-button type="primary" style="width: 100%" :loading="loading" @click="doLogin">登 录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '../../api'
import { ElMessage } from 'element-plus'

const router = useRouter()
const username = ref('admin')
const password = ref('')
const loading = ref(false)

async function doLogin() {
  if (!username.value || !password.value) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const resp = await login({ username: username.value, password: password.value })
    if (resp.ok) {
      // 认证态存于 HttpOnly Cookie（服务端 Set-Cookie），前端无需保存 token
      ElMessage.success('登录成功')
      router.push({ name: 'dashboard' })
    }
  } finally {
    loading.value = false
  }
}
</script>
