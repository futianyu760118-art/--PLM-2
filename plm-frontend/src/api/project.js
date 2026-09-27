import request from '@/utils/request'

export function pageProject(params) {
  return request({ url: '/v1/projects', method: 'get', params })
}
export function getProject(id) {
  return request({ url: `/v1/projects/${id}`, method: 'get' })
}
export function createProject(data) {
  return request({ url: '/v1/projects', method: 'post', data })
}
export function updateProject(data) {
  return request({ url: '/v1/projects', method: 'put', data })
}
export function deleteProject(id) {
  return request({ url: `/v1/projects/${id}`, method: 'delete' })
}
export function getGateDefs() {
  return request({ url: '/v1/projects/gates/defs', method: 'get' })
}
export function getGateLogs(projectId) {
  return request({ url: `/v1/projects/${projectId}/gates`, method: 'get' })
}
export function passGate(projectId, gateCode, data) {
  return request({ url: `/v1/projects/${projectId}/gates/${gateCode}/pass`, method: 'post', data })
}
export function failGate(projectId, gateCode, data) {
  return request({ url: `/v1/projects/${projectId}/gates/${gateCode}/fail`, method: 'post', data })
}

// ===== 研发自治中心 - 项目辅助 =====
export function pageSupplyIssues(params) {
  return request({ url: '/v1/rd/supply-issues', method: 'get', params })
}
export function createSupplyIssue(data) {
  return request({ url: '/v1/rd/supply-issues', method: 'post', data })
}
export function updateSupplyIssue(data) {
  return request({ url: '/v1/rd/supply-issues', method: 'put', data })
}
export function deleteSupplyIssue(id) {
  return request({ url: `/v1/rd/supply-issues/${id}`, method: 'delete' })
}

export function pageSalesPromotion(params) {
  return request({ url: '/v1/rd/sales-promotion', method: 'get', params })
}
export function createSalesPromotion(data) {
  return request({ url: '/v1/rd/sales-promotion', method: 'post', data })
}
export function updateSalesPromotion(data) {
  return request({ url: '/v1/rd/sales-promotion', method: 'put', data })
}
export function deleteSalesPromotion(id) {
  return request({ url: `/v1/rd/sales-promotion/${id}`, method: 'delete' })
}

export function pageReviews(params) {
  return request({ url: '/v1/rd/reviews', method: 'get', params })
}
export function createReview(data) {
  return request({ url: '/v1/rd/reviews', method: 'post', data })
}
export function updateReview(data) {
  return request({ url: '/v1/rd/reviews', method: 'put', data })
}
export function deleteReview(id) {
  return request({ url: `/v1/rd/reviews/${id}`, method: 'delete' })
}

export function analysisSummary() {
  return request({ url: '/v1/rd/analysis/summary', method: 'get' })
}
export function analysisMonthly() {
  return request({ url: '/v1/rd/analysis/monthly', method: 'get' })
}
export function analysisByCategory() {
  return request({ url: '/v1/rd/analysis/by-category', method: 'get' })
}
export function analysisDelay() {
  return request({ url: '/v1/rd/analysis/delay', method: 'get' })
}
