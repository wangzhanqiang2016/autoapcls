<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useApCloseStore } from '@/store/apClose'
import { useUserStore } from '@/store/user'
import { getTaskDetail, executeStep, confirmStep, getStepStatus } from '@/api/apClose'
import { ElMessage } from 'element-plus'
import { InfoFilled } from '@element-plus/icons-vue'
import StepManualPanel from '@/components/StepManualPanel.vue'
import StepCheckPanel from '@/components/StepCheckPanel.vue'
import StepReportPanel from '@/components/StepReportPanel.vue'
import StepRequestPanel from '@/components/StepRequestPanel.vue'

const apStore = useApCloseStore()
const userStore = useUserStore()
const currentStepNo = ref(1)
const stepDetail = ref(null)
const stepLoading = ref(false)
const pollTimer = ref(null)

const statusIcon = (s) => {
  if (s === 'COMPLETED') return 'CircleCheckFilled'
  if (s === 'RUNNING') return 'Loading'
  if (s === 'FAILED') return 'WarningFilled'
  return 'CircleCheck'
}
const statusColor = (s) => {
  if (s === 'COMPLETED') return '#67C23A'
  if (s === 'RUNNING') return '#409EFF'
  if (s === 'FAILED') return '#F56C6C'
  return '#C0C4CC'
}
const statusText = (s) => {
  if (s === 'COMPLETED') return '已完成'
  if (s === 'RUNNING') return '进行中'
  if (s === 'FAILED') return '异常'
  return '等待中'
}

async function selectStep(stepNo) {
  currentStepNo.value = stepNo
  stepLoading.value = true
  try {
    const res = await getTaskDetail(stepNo)
    stepDetail.value = res.data
  } catch (e) {
    ElMessage.error('加载步骤详情失败')
  } finally {
    stepLoading.value = false
  }
}

function stopPolling() {
  if (pollTimer.value) {
    clearInterval(pollTimer.value)
    pollTimer.value = null
  }
}

function startPolling(stepNo) {
  stopPolling() // 先清除已有的轮询
  pollTimer.value = setInterval(async () => {
    try {
      const res = await getStepStatus(stepNo)
      if (stepDetail.value) {
        stepDetail.value.status = res.data.status
        stepDetail.value.ebsRequestStatus = res.data.ebsRequestStatus
        stepDetail.value.errorMessage = res.data.errorMessage
      }
      apStore.updateTaskStatus(stepNo, res.data.status)
      if (res.data.status === 'COMPLETED' || res.data.status === 'FAILED') {
        stopPolling()
        // 完成后刷新详情（获取输出文件路径等）
        if (res.data.status === 'COMPLETED') {
          const detailRes = await getTaskDetail(stepNo)
          stepDetail.value = detailRes.data
          ElMessage.success('步骤执行完成')
        } else {
          ElMessage.error('步骤执行失败: ' + (res.data.errorMessage || '未知错误'))
        }
      }
    } catch (e) {
      // 轮询错误静默处理，不打断用户
    }
  }, 3000)
}

async function handleExecute(params) {
  stepLoading.value = true
  try {
    const res = await executeStep(currentStepNo.value, params)
    stepDetail.value = res.data
    if (res.data.status) apStore.updateTaskStatus(currentStepNo.value, res.data.status)

    if (res.data.status === 'RUNNING') {
      ElMessage.info(res.data.message || '请求已提交，正在等待执行完成...')
      startPolling(currentStepNo.value)
    } else {
      ElMessage.success(res.data.message || '操作完成')
    }
  } catch (e) {
    ElMessage.error('操作失败')
  } finally {
    stepLoading.value = false
  }
}

async function handleConfirm() {
  stepLoading.value = true
  try {
    const res = await confirmStep(currentStepNo.value)
    stepDetail.value = res.data
    apStore.updateTaskStatus(currentStepNo.value, 'COMPLETED')
    ElMessage.success('步骤已确认完成')
  } catch (e) {
    ElMessage.error('确认失败')
  } finally {
    stepLoading.value = false
  }
}

onMounted(async () => {
  await apStore.loadTasks()
  if (apStore.tasks.length > 0) {
    const firstPending = apStore.tasks.find(t => t.status !== 'COMPLETED')
    await selectStep(firstPending ? firstPending.stepNo : 1)
  }
})

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <div class="dashboard">
    <div class="sidebar">
      <div class="sidebar-header">
        <h3>月结步骤清单</h3>
        <div class="sidebar-info">
          <span>{{ userStore.defaultOuName }}</span>
          <span>{{ userStore.periodName }}</span>
        </div>
      </div>
      <div class="step-list">
        <div v-for="task in apStore.tasks" :key="task.stepNo"
          class="step-item" :class="{ active: currentStepNo === task.stepNo }"
          @click="selectStep(task.stepNo)">
          <div class="step-no">{{ task.stepNo }}</div>
          <div class="step-content">
            <div class="step-name">{{ task.stepName }}</div>
            <div class="step-type">
              <el-tag size="small"
                :type="task.stepType === 'MANUAL_CONFIRM' ? 'info' : task.stepType === 'AUTO_CHECK' ? 'warning' : 'primary'"
                effect="plain">
                {{ task.stepType === 'MANUAL_CONFIRM' ? '人工确认' : task.stepType === 'AUTO_CHECK' ? '自动检查' : task.stepType === 'REPORT_EXPORT' ? '报表导出' : 'EBS请求' }}
              </el-tag>
            </div>
          </div>
          <div class="step-status">
            <el-icon :size="20" :color="statusColor(task.status)">
              <component :is="statusIcon(task.status)" :class="{ 'is-loading': task.status === 'RUNNING' }" />
            </el-icon>
          </div>
        </div>
      </div>
    </div>

    <div class="main-panel" v-loading="stepLoading">
      <template v-if="stepDetail">
        <div class="panel-header">
          <h2>步骤 {{ stepDetail.stepNo }}：{{ stepDetail.stepName }}</h2>
          <el-tag :type="stepDetail.status === 'COMPLETED' ? 'success' : stepDetail.status === 'FAILED' ? 'danger' : stepDetail.status === 'RUNNING' ? 'warning' : 'info'">
            {{ statusText(stepDetail.status) }}
          </el-tag>
        </div>
        <div class="panel-desc" v-if="stepDetail.description">
          <el-icon><InfoFilled /></el-icon>
          {{ stepDetail.description }}
        </div>
        <div class="panel-body">
          <StepManualPanel v-if="stepDetail.stepType === 'MANUAL_CONFIRM'" :detail="stepDetail" @confirm="handleConfirm" />
          <StepCheckPanel v-else-if="stepDetail.stepType === 'AUTO_CHECK'" :detail="stepDetail" @execute="handleExecute" @confirm="handleConfirm" />
          <StepReportPanel v-else-if="stepDetail.stepType === 'REPORT_EXPORT'" :detail="stepDetail" @execute="handleExecute" />
          <StepRequestPanel v-else-if="stepDetail.stepType === 'EBS_REQUEST'" :detail="stepDetail" @execute="handleExecute" />
        </div>
      </template>
      <el-empty v-else description="请选择一个步骤开始月结流程" />
    </div>
  </div>
</template>

<style scoped>
.dashboard { display: flex; height: calc(100vh - 56px); }
.sidebar { width: 320px; background: #fff; border-right: 1px solid #e4e7ed; display: flex; flex-direction: column; overflow: hidden; }
.sidebar-header { padding: 20px; border-bottom: 1px solid #ebeef5; }
.sidebar-header h3 { font-size: 16px; margin-bottom: 8px; }
.sidebar-info { display: flex; gap: 8px; }
.sidebar-info span { font-size: 12px; color: #909399; background: #f5f7fa; padding: 2px 8px; border-radius: 4px; }
.step-list { flex: 1; overflow-y: auto; padding: 8px; }
.step-item { display: flex; align-items: center; padding: 12px; border-radius: 8px; cursor: pointer; gap: 12px; transition: all .2s; margin-bottom: 2px; }
.step-item:hover { background: #f5f7fa; }
.step-item.active { background: #ecf5ff; border: 1px solid #d9ecff; }
.step-no { width: 28px; height: 28px; border-radius: 50%; background: #f0f2f5; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 600; color: #606266; flex-shrink: 0; }
.step-item.active .step-no { background: #409EFF; color: #fff; }
.step-content { flex: 1; min-width: 0; }
.step-name { font-size: 13px; color: #303133; margin-bottom: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.step-status { flex-shrink: 0; }
.main-panel { flex: 1; padding: 24px; overflow-y: auto; }
.panel-header { display: flex; align-items: center; gap: 16px; margin-bottom: 12px; }
.panel-header h2 { font-size: 20px; color: #303133; }
.panel-desc { display: flex; align-items: flex-start; gap: 8px; padding: 12px 16px; background: #f5f7fa; border-radius: 8px; color: #606266; font-size: 14px; margin-bottom: 20px; }
.panel-body { background: #fff; border-radius: 8px; padding: 24px; box-shadow: 0 1px 4px rgba(0,0,0,.04); }
</style>
