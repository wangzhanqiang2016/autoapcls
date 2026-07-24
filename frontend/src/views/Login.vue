<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { login } from '@/api/auth'
import { ElMessage } from 'element-plus'
import { Money } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const username = ref('')
const password = ref('')
const loading = ref(false)

// 测试账号快速选择
const testAccounts = [
  { label: 'AP_ACCOUNTANT (应付会计)', user: 'AP_ACCOUNTANT', pwd: '123456' },
  { label: 'CST_ACCOUNTANT (成本会计)', user: 'CST_ACCOUNTANT', pwd: '123456' },
  { label: 'GL_ACCOUNTANT (总账会计)', user: 'GL_ACCOUNTANT', pwd: '123456' }
]

function quickFill(acct) {
  username.value = acct.user
  password.value = acct.pwd
}

async function handleLogin() {
  if (!username.value.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (!password.value) {
    ElMessage.warning('请输入密码')
    return
  }
  loading.value = true
  try {
    const res = await login(username.value.trim(), password.value)
    userStore.setLogin(res.data.token, res.data)
    ElMessage.success('登录成功')
    router.push('/select-responsibility')
  } catch (e) {
    ElMessage.error('登录失败: ' + (e.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <el-icon :size="48" color="#409EFF"><Money /></el-icon>
      <h2>应付自动结账系统</h2>
      <p>丽珠医药集团</p>

      <div class="login-form">
        <el-input v-model="username" placeholder="用户名" size="large" clearable
          @keyup.enter="handleLogin" />
        <el-input v-model="password" type="password" placeholder="密码" size="large"
          show-password style="margin-top:12px" @keyup.enter="handleLogin" />
        <el-button type="primary" size="large" :loading="loading" @click="handleLogin"
          style="margin-top:16px;width:100%;height:48px;font-size:16px">
          登 录
        </el-button>
      </div>

      <div class="test-accounts">
        <p class="hint-title">快速选择测试账号（点击填入）：</p>
        <el-tag v-for="acct in testAccounts" :key="acct.user"
          size="small" type="info" style="margin:3px;cursor:pointer"
          @click="quickFill(acct)">
          {{ acct.label }}
        </el-tag>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page { display: flex; align-items: center; justify-content: center; min-height: 100vh; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
.login-card { background: #fff; border-radius: 12px; padding: 48px; text-align: center; box-shadow: 0 8px 32px rgba(0,0,0,.15); width: 420px; }
.login-card h2 { margin: 16px 0 8px; color: #303133; }
.login-card > p { color: #909399; margin-bottom: 24px; }
.login-form { text-align: left; }
.test-accounts { margin-top: 20px; padding-top: 14px; border-top: 1px solid #ebeef5; }
.hint-title { font-size: 12px; color: #909399; margin-bottom: 8px; }
</style>
