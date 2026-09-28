import axios from 'axios'
import request from '@/utils/request'

const raw = axios.create({ baseURL: '/api', timeout: 120000 })
raw.interceptors.request.use(cfg => {
  const token = localStorage.getItem('plm_token')
  if (token) cfg.headers['Authorization'] = 'Bearer ' + token
  return cfg
}, e => Promise.reject(e))

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

// ===== 研发项目跟踪总表 =====
export function getTrackingNodeDefs() {
  return request({ url: '/v1/rd/tracking/node-defs', method: 'get' })
}
export function getNodeSheetMap() {
  return request({ url: '/v1/rd/tracking/node-sheet-map', method: 'get' })
}
export function pageTracking(params) {
  return request({ url: '/v1/rd/tracking', method: 'get', params })
}
export function saveTrackingCell(projectId, data) {
  return request({ url: `/v1/rd/tracking/${projectId}/cell`, method: 'put', data })
}

// ===== 项目工作表 (规格书/配置表/样品单/评审单/测试/试产/出货) =====
export function getSheetMeta() {
  return request({ url: '/v1/rd/sheets/meta', method: 'get' })
}
export function getSheetContext(projectNo) {
  return request({ url: '/v1/rd/sheets/context', method: 'get', params: { projectNo } })
}
export function pageSheet(type, params) {
  return request({ url: `/v1/rd/sheets/${type}`, method: 'get', params })
}
export function createSheet(type, data) {
  return request({ url: `/v1/rd/sheets/${type}`, method: 'post', data })
}
export function updateSheet(type, data) {
  return request({ url: `/v1/rd/sheets/${type}`, method: 'put', data })
}
export function deleteSheet(type, id) {
  return request({ url: `/v1/rd/sheets/${type}/${id}`, method: 'delete' })
}
export async function exportSheet(type) {
  const res = await raw.get(`/v1/rd/sheets/${type}/export`, { responseType: 'blob' })
  const url = URL.createObjectURL(res.data)
  const a = document.createElement('a')
  a.href = url; a.download = `rd-sheet-${type}.xlsx`; a.click()
  URL.revokeObjectURL(url)
}
export function importSheet(type, formData) {
  return raw.post(`/v1/rd/sheets/${type}/import`, formData, { headers: { 'Content-Type': 'multipart/form-data' } })
}

// ===== 项目变更列表 =====
export function pageChanges(params) {
  return request({ url: '/v1/rd/changes', method: 'get', params })
}

// ===== 完成判定汇总 (工作表/台账 -> 项目明细表节点) =====
export function rollupAll() {
  return request({ url: '/v1/rd/tracking/rollup', method: 'post' })
}
export function rollupProject(projectId) {
  return request({ url: `/v1/rd/tracking/${projectId}/rollup`, method: 'post' })
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
