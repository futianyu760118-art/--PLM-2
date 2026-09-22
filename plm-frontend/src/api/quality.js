import request from '@/utils/request'

export function pageQuality(params) {
  return request({ url: '/quality/page', method: 'get', params })
}
export function addQuality(data) {
  return request({ url: '/quality', method: 'post', data })
}
export function updateQuality(data) {
  return request({ url: '/quality', method: 'put', data })
}
export function deleteQuality(id) {
  return request({ url: `/quality/${id}`, method: 'delete' })
}
export function releaseQuality(id) {
  return request({ url: `/quality/${id}/release`, method: 'put' })
}
