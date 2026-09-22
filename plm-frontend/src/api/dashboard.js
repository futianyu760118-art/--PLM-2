import request from '@/utils/request'

export function getStats() {
  return request({ url: '/dashboard/stats', method: 'get' })
}
export function materialTypeDist() {
  return request({ url: '/dashboard/material-type-distribution', method: 'get' })
}
export function ecnTrend() {
  return request({ url: '/dashboard/ecn-trend', method: 'get' })
}
export function materialStatusDist() {
  return request({ url: '/dashboard/material-status-distribution', method: 'get' })
}
