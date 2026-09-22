import request from '@/utils/request'

export function createExport(data) {
  return request({ url: '/v1/exports', method: 'post', data })
}
export function listExports(status, limit) {
  return request({ url: '/v1/exports', method: 'get', params: { status, limit: limit || 20 } })
}
export function getExport(id) {
  return request({ url: `/v1/exports/${id}`, method: 'get' })
}
export function downloadUrl(id) {
  return `/api/v1/exports/${id}/download`
}
