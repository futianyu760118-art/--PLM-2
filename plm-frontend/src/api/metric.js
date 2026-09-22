import request from '@/utils/request'

export function metricDefs() {
  return request({ url: '/v1/metrics/defs', method: 'get' })
}
export function queryMetrics(data) {
  return request({ url: '/v1/metrics/query', method: 'post', data })
}
export function kpiDefs() {
  return request({ url: '/v1/kpis/defs', method: 'get' })
}
export function kpiValues(periodKey, kpiCodes) {
  return request({ url: '/v1/kpis/values', method: 'get', params: { periodKey, kpiCodes } })
}
export function dqScores(objectType) {
  return request({ url: '/v1/dq/scores', method: 'get', params: { objectType } })
}
export function codegenPreview(objectType, context) {
  return request({ url: '/v1/codegen/preview', method: 'post', data: { objectType, context } })
}
