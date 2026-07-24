<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { getResponsibilities, getOrganizations, selectSession } from '@/api/auth'
import { initTasks } from '@/api/apClose'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

const responsibilities = ref([])
const organizations = ref([])
const selectedRespId = ref(null)
const selectedOrgId = ref(null)
const selectedPeriodName = ref('2026-07')
const submitting = ref(false)
const orgsLoading = ref(false)

const periods = Array.from({length:12}, (_,i) => `2026-${String(i+1).padStart(2,'0')}`)

onMounted(async () => {
  try {
    const respRes = await getResponsibilities()
    responsibilities.value = respRes.data
    if (respRes.data.length > 0) {
      selectedRespId.value = respRes.data[0].respId
    }
  } catch (e) {
    ElMessage.error('加载职责列表失败')
  }
})

// 职责变更时重新加载库存组织
async function loadOrganizations(respId) {
  orgsLoading.value = true
  try {
    const orgRes = await getOrganizations(respId)
    organizations.value = orgRes.data
    selectedOrgId.value = orgRes.data.length > 0 ? orgRes.data[0].orgId : null
  } catch (e) {
    ElMessage.error('加载库存组织失败')
  } finally {
    orgsLoading.value = false
  }
}

watch(selectedRespId, (newVal) => {
  if (newVal) loadOrganizations(newVal)
}, { immediate: true })

async function handleSubmit() {
  if (!selectedRespId.value || !selectedOrgId.value) {
    ElMessage.warning('请选择职责和库存组织')
    return
  }
  submitting.value = true
  try {
    const resp = responsibilities.value.find(r => r.respId === selectedRespId.value)
    const org = organizations.value.find(o => o.orgId === selectedOrgId.value)
    const sessionRes = await selectSession({
      respId: resp.respId, respName: resp.respName,
      orgId: org.orgId, orgCode: org.orgCode,
      periodName: selectedPeriodName.value
    })
    userStore.setSession(sessionRes.data)
    await initTasks(selectedPeriodName.value)
    ElMessage.success('配置完成，进入月结工作台')
    router.push('/dashboard')
  } catch (e) {
    ElMessage.error('配置失败: ' + e.message)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="select-page">
    <div class="select-card">
      <h2>选择工作环境</h2>
      <p class="subtitle">登录账号：{{ userStore.feishuName }} ({{ userStore.ebsUserName }})</p>
      <el-form label-width="100px" @submit.prevent="handleSubmit">
        <el-form-item label="选择职责">
          <el-select v-model="selectedRespId" style="width:100%">
            <el-option v-for="r in responsibilities" :key="r.respId" :label="r.respName" :value="r.respId">
              <span>{{ r.respName }}</span>
              <el-tag size="small" style="margin-left:8px" type="info">{{ r.appName }}</el-tag>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="库存组织">
          <el-select v-model="selectedOrgId" style="width:100%" :loading="orgsLoading">
            <el-option v-for="o in organizations" :key="o.orgId" :label="o.orgName" :value="o.orgId">
              <span>{{ o.orgCode }} - {{ o.orgName }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="结账期间">
          <el-select v-model="selectedPeriodName" style="width:100%">
            <el-option v-for="p in periods" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSubmit" :loading="submitting" size="large" style="width:100%">
            确认并进入工作台
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.select-page { display: flex; align-items: center; justify-content: center; min-height: 100vh; background: #f0f2f5; }
.select-card { background: #fff; border-radius: 12px; padding: 40px; width: 520px; box-shadow: 0 4px 16px rgba(0,0,0,.08); }
.select-card h2 { margin-bottom: 4px; color: #303133; }
.subtitle { color: #909399; font-size: 13px; margin-bottom: 24px; }
</style>
