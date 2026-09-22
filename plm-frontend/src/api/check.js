import request from '@/utils/request'

export function runSystemCheck() {
  return request({ url: '/system/check/run', method: 'get', timeout: 30000 })
}
