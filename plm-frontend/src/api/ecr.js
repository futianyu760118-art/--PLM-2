import request from '@/utils/request'

export function pageEcr(params) {
  return request({ url: '/ecr/page', method: 'get', params })
}

export function getEcr(id) {
  return request({ url: `/ecr/${id}`, method: 'get' })
}

export function addEcr(data) {
  return request({ url: '/ecr', method: 'post', data })
}

export function updateEcr(data) {
  return request({ url: '/ecr', method: 'put', data })
}

export function deleteEcr(id) {
  return request({ url: `/ecr/${id}`, method: 'delete' })
}

export function submitEcr(id) {
  return request({ url: `/ecr/${id}/submit`, method: 'put' })
}

/** action = APPROVE / REJECT / BACK / TERMINATE */
export function approveEcr(id, data) {
  return request({ url: `/ecr/${id}/approve`, method: 'put', data })
}

export function getEcrProgress(id) {
  return request({ url: `/ecr/${id}/progress`, method: 'get' })
}

export function toEcn(id) {
  return request({ url: `/ecr/${id}/to-ecn`, method: 'post' })
}

export function closeEcr(id) {
  return request({ url: `/ecr/${id}/close`, method: 'put' })
}

// ---------- 审批引擎（待办箱） ----------

export function pendingTasks() {
  return request({ url: '/approval/pending', method: 'get' })
}
