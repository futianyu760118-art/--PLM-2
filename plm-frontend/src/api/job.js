import request from '@/utils/request'

export function runJob(action) {
  return request({ url: `/v1/jobs/${action}`, method: 'post' })
}