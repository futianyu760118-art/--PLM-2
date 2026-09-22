import request from '@/utils/request'

export function pageOutsource(params) {
  return request({ url: '/outsourcing/page', method: 'get', params })
}
export function getOutsource(id) {
  return request({ url: `/outsourcing/${id}`, method: 'get' })
}
export function createOutsource(data) {
  return request({ url: '/outsourcing', method: 'post', data })
}
export function submitOutsource(id) {
  return request({ url: `/outsourcing/${id}/submit`, method: 'put' })
}
export function approveOutsource(id, comment) {
  return request({ url: `/outsourcing/${id}/approve`, method: 'put', params: { comment } })
}
export function rejectOutsource(id, comment) {
  return request({ url: `/outsourcing/${id}/reject`, method: 'put', params: { comment } })
}
export function getOutsourceFiles(id) {
  return request({ url: `/outsourcing/${id}/files`, method: 'get' })
}
export function downloadOutsource(id, fileId) {
  return request({ url: `/outsourcing/${id}/download/${fileId}`, method: 'post' })
}
