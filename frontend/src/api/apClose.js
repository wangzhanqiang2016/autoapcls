import request from './request'

export function getPeriods() {
  return request.get('/ap-close/periods')
}

export function initTasks(periodName) {
  return request.post('/ap-close/init', null, { params: { periodName } })
}

export function getTasks() {
  return request.get('/ap-close/tasks')
}

export function getTaskDetail(stepNo) {
  return request.get(`/ap-close/tasks/${stepNo}`)
}

export function executeStep(stepNo, params) {
  return request.post(`/ap-close/tasks/${stepNo}/execute`, { params })
}

export function confirmStep(stepNo) {
  return request.post(`/ap-close/tasks/${stepNo}/confirm`)
}

export function getStepStatus(stepNo) {
  return request.get(`/ap-close/tasks/${stepNo}/status`)
}
