<script setup>
import { useUserStore } from '@/store/user'
import { useRouter } from 'vue-router'

const userStore = useUserStore()
const router = useRouter()

function handleLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<template>
  <el-container class="app-container" v-if="userStore.isLoggedIn">
    <el-header class="app-header">
      <div class="header-left">
        <el-icon :size="22"><Money /></el-icon>
        <span class="app-title">应付自动结账系统</span>
      </div>
      <div class="header-right" v-if="userStore.sessionId">
        <el-tag type="success" effect="plain">{{ userStore.selectedRespName }}</el-tag>
        <el-tag effect="plain">{{ userStore.selectedOrgCode }}</el-tag>
        <el-tag type="info" effect="plain">{{ userStore.periodName }}</el-tag>
        <el-divider direction="vertical" />
        <span class="user-name">{{ userStore.feishuName }}</span>
        <el-button type="danger" text @click="handleLogout">退出</el-button>
      </div>
    </el-header>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
  <router-view v-else />
</template>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Microsoft YaHei', sans-serif; }
.app-container { min-height: 100vh; background: #f0f2f5; }
.app-header {
  display: flex; align-items: center; justify-content: space-between;
  background: #fff; box-shadow: 0 1px 4px rgba(0,0,0,.08); padding: 0 24px; height: 56px;
}
.header-left { display: flex; align-items: center; gap: 10px; }
.app-title { font-size: 18px; font-weight: 600; color: #303133; }
.header-right { display: flex; align-items: center; gap: 10px; }
.user-name { color: #606266; font-size: 14px; }
</style>
