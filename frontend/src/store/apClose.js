import { defineStore } from 'pinia'
import { getTasks as fetchTasks } from '@/api/apClose'

export const useApCloseStore = defineStore('apClose', {
  state: () => ({
    tasks: [],
    currentStep: null,
    currentStepDetail: null,
    loading: false
  }),

  actions: {
    async loadTasks() {
      this.loading = true
      try {
        const res = await fetchTasks()
        this.tasks = res.data || []
      } finally {
        this.loading = false
      }
    },

    setCurrentStep(stepNo) {
      this.currentStep = stepNo
    },

    setCurrentStepDetail(detail) {
      this.currentStepDetail = detail
    },

    updateTaskStatus(stepNo, status) {
      const task = this.tasks.find(t => t.stepNo === stepNo)
      if (task) task.status = status
    }
  }
})
