<script setup>
import { ref } from 'vue'
import { Promotion, Document } from '@element-plus/icons-vue'

const props = defineProps({ detail: Object })
const emit = defineEmits(['execute'])
const submitting = ref(false)

function handleSubmit() {
  submitting.value = true
  emit('execute', props.detail?.defaultParams || {})
  setTimeout(() => { submitting.value = false }, 1000)
}
</script>

<template>
  <div class="report-panel">
    <el-descriptions title="请求参数" :column="2" border v-if="detail?.defaultParams && Object.keys(detail.defaultParams).length">
      <el-descriptions-item v-for="(value, key) in detail.defaultParams" :key="key" :label="String(key)">
        <el-tag>{{ value }}</el-tag>
      </el-descriptions-item>
    </el-descriptions>

    <div class="action-bar">
      <el-button type="primary" size="large" @click="handleSubmit"
        :loading="submitting" :disabled="detail?.status === 'COMPLETED'">
        <el-icon><Promotion /></el-icon>
        {{ detail?.status === 'COMPLETED' ? '已完成' : '提交请求' }}
      </el-button>
    </div>

    <div v-if="detail?.status === 'COMPLETED'" class="output-section">
      <el-divider />
      <h4>输出文件</h4>
      <div class="file-item" v-if="detail.ebsRequestId">
        <el-icon :size="24" color="#409EFF"><Document /></el-icon>
        <span>{{ detail.stepName }}.xlsx</span>
        <el-tag size="small" type="success">请求ID: {{ detail.ebsRequestId }}</el-tag>
      </div>
      <el-empty v-else description="暂无输出文件" :image-size="60" />
    </div>
  </div>
</template>

<style scoped>
.report-panel { }
.action-bar { margin-top: 24px; text-align: center; }
.output-section { margin-top: 16px; }
.output-section h4 { margin-bottom: 12px; color: #303133; }
.file-item { display: flex; align-items: center; gap: 12px; padding: 12px 16px; background: #f5f7fa; border-radius: 8px; }
</style>
