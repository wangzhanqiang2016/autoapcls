<script setup>
import { ref, computed } from 'vue'
import { Promotion } from '@element-plus/icons-vue'

const props = defineProps({ detail: Object })
const emit = defineEmits(['execute'])
const submitting = ref(false)
const formData = ref({})

function handleSubmit() {
  submitting.value = true
  const params = props.detail?.defaultParams ? { ...props.detail.defaultParams, ...formData.value } : {}
  emit('execute', params)
  setTimeout(() => { submitting.value = false }, 1000)
}
</script>

<template>
  <div class="request-panel">
    <el-descriptions title="请求参数" :column="2" border
      v-if="detail?.defaultParams && Object.keys(detail.defaultParams).length">
      <el-descriptions-item v-for="(value, key) in detail.defaultParams" :key="key" :label="String(key)">
        <template v-if="String(value) === '请选择日期'">
          <el-date-picker v-model="formData[key]" type="date"
            :placeholder="'请选择' + String(key)" value-format="YYYY-MM-DD" />
        </template>
        <template v-else>
          <el-tag>{{ value }}</el-tag>
        </template>
      </el-descriptions-item>
    </el-descriptions>

    <el-empty v-else description="此步骤无参数配置" :image-size="60" />

    <div class="action-bar">
      <el-button type="primary" size="large" @click="handleSubmit"
        :loading="submitting" :disabled="detail?.status === 'COMPLETED'">
        <el-icon><Promotion /></el-icon>
        {{ detail?.status === 'COMPLETED' ? '已完成' : '提交请求' }}
      </el-button>
    </div>

    <div v-if="detail?.status === 'COMPLETED'" class="result-section">
      <el-divider />
      <el-result icon="success" title="请求已完成"
        :sub-title="`EBS请求ID: ${detail.ebsRequestId || 'N/A'}`" />
    </div>

    <div v-if="detail?.status === 'FAILED'" class="result-section">
      <el-divider />
      <el-result icon="error" title="请求失败"
        :sub-title="detail.errorMessage || '请查看EBS系统日志'" />
    </div>
  </div>
</template>

<style scoped>
.request-panel { }
.action-bar { margin-top: 24px; text-align: center; }
.result-section { margin-top: 16px; }
</style>
