<script setup>
import { ref } from 'vue'
import { CircleCheck } from '@element-plus/icons-vue'

const props = defineProps({ detail: Object })
const emit = defineEmits(['confirm'])
const confirming = ref(false)

function handleConfirm() {
  confirming.value = true
  emit('confirm')
  setTimeout(() => { confirming.value = false }, 500)
}
</script>

<template>
  <div class="manual-panel">
    <el-alert type="info" :closable="false" show-icon>
      <template #title>人工确认步骤</template>
      <template #default>
        <p style="margin:8px 0">请在 Oracle EBS 系统中确认以下操作已完成：</p>
        <p style="margin:0;font-weight:600">
          {{ detail?.description || '请确认本月发票和付款凭证已全部录入系统' }}
        </p>
      </template>
    </el-alert>
    <div class="action-bar">
      <el-button type="primary" size="large" @click="handleConfirm"
        :loading="confirming" :disabled="detail?.status === 'COMPLETED'">
        <el-icon><CircleCheck /></el-icon>
        {{ detail?.status === 'COMPLETED' ? '已完成' : '确认完成' }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.manual-panel { }
.action-bar { margin-top: 24px; text-align: center; }
</style>
