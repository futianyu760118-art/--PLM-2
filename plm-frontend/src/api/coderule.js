import request from '@/utils/request'

export function listCodeRule(params) {
  return request({ url: '/v1/coderule/list', method: 'get', params })
}

export function getCodeRule(id) {
  return request({ url: `/v1/coderule/${id}`, method: 'get' })
}

export function createCodeRule(data) {
  return request({ url: '/v1/coderule', method: 'post', data })
}

export function updateCodeRule(id, data) {
  return request({ url: `/v1/coderule/${id}`, method: 'put', data })
}

export function changeCodeRuleStatus(id, status) {
  return request({ url: `/v1/coderule/${id}/status`, method: 'put', params: { status } })
}

export function deleteCodeRule(id) {
  return request({ url: `/v1/coderule/${id}`, method: 'delete' })
}

export function previewCode(objectType, context) {
  return request({ url: '/v1/coderule/preview', method: 'post', data: { objectType, context } })
}
