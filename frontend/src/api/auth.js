import request from './request'

export function login(username, password) {
  return request.post('/auth/login', { username, password })
}

export function getUserInfo() {
  return request.get('/auth/user-info')
}

export function getResponsibilities() {
  return request.get('/auth/responsibilities')
}

export function getOrganizations(respId) {
  return request.get('/auth/organizations', { params: respId ? { respId } : {} })
}

export function selectSession(data) {
  return request.post('/auth/select-session', data)
}

export function logout() {
  return request.post('/auth/logout')
}
