import request from '@/utils/request'

export function createShare(data) {
  return request({ url: '/share', method: 'post', data })
}
export function listShares() {
  return request({ url: '/share/list', method: 'get' })
}
export function voidShare(id) {
  return request({ url: `/share/${id}/void`, method: 'put' })
}
export function getShareLogs(id) {
  return request({ url: `/share/${id}/logs`, method: 'get' })
}
export function accessShare(token) {
  return request({ url: `/share/public/${token}`, method: 'get' })
}
