import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userId: null,
    feishuOpenId: '',
    feishuName: '',
    ebsUserName: '',
    sessionId: null,
    selectedOrgCode: '',
    selectedOrgId: null,
    selectedRespName: '',
    defaultOuName: '',
    periodName: '',
    isLoggedIn: false
  }),

  actions: {
    setLogin(token, userInfo) {
      this.token = token
      this.userId = userInfo.userId
      this.feishuOpenId = userInfo.feishuOpenId
      this.feishuName = userInfo.feishuName
      this.ebsUserName = userInfo.ebsUserName
      this.isLoggedIn = true
      localStorage.setItem('token', token)
    },

    setSession(session) {
      this.sessionId = session.id
      this.selectedOrgCode = session.selectedOrgCode
      this.selectedOrgId = session.selectedOrgId
      this.selectedRespName = session.selectedRespName
      this.defaultOuName = session.defaultOuName
      this.periodName = session.periodName
    },

    logout() {
      this.token = ''
      this.userId = null
      this.isLoggedIn = false
      this.sessionId = null
      localStorage.removeItem('token')
    }
  }
})
