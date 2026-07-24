<script setup>
import { ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'

const props = defineProps({ detail: Object })
const emit = defineEmits(['execute', 'confirm'])
const checking = ref(false)
const confirming = ref(false)
const checkResult = ref(null)

async function handleCheck() {
  checking.value = true
  try {
    emit('execute', {})
    checkResult.value = props.detail?.checkResult || { totalCount: 0, issues: [] }
  } finally {
    setTimeout(() => { checking.value = false }, 500)
  }
}

function handleConfirmDone() {
  confirming.value = true
  emit('confirm')
  setTimeout(() => { confirming.value = false }, 500)
}
</script>

<template>
  <div class="check-panel">
    <div class="action-bar">
      <el-button type="primary" @click="handleCheck" :loading="checking">
        <el-icon><Refresh /></el-icon> 执行检查
      </el-button>
    </div>

    <div v-if="detail?.status === 'COMPLETED' || checkResult" class="check-result">
      <el-result
        :icon="(checkResult?.totalCount || 0) === 0 ? 'success' : 'warning'"
        :title="(checkResult?.totalCount || 0) === 0 ? '检查通过' : '发现异常数据'"
        :sub-title="(checkResult?.totalCount || 0) === 0 ? '未发现异常发票' : `共 ${checkResult?.totalCount || 0} 条异常记录，请在EBS中处理后确认完成`"
      >
        <template #extra v-if="(checkResult?.totalCount || 0) > 0">
          <el-button type="primary" @click="handleConfirmDone" :loading="confirming">
            已处理完成，确认继续
          </el-button>
        </template>
      </el-result>
    </div>

    <div v-if="checkResult && checkResult.issues && checkResult.issues.length > 0" class="issue-table">
      <el-table :data="checkResult.issues" border stripe size="small" max-height="400">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column v-for="(val, key) in checkResult.issues[0]" :key="key" :prop="key" :label="key" min-width="120" />
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.check-panel { }
.action-bar { margin-bottom: 20px; text-align: center; }
.check-result { margin-bottom: 16px; }
.issue-table { margin-top: 16px; }
</style>
