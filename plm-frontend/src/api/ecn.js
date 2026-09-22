import request from '@/utils/request'

export function pageEcn(params) {
  return request({ url: '/ecn/page', method: 'get', params })
}

export function getEcn(id) {
  return request({ url: `/ecn/${id}`, method: 'get' })
}

export function addEcn(data) {
  return request({ url: '/ecn', method: 'post', data })
}

export function updateEcn(data) {
  return request({ url: '/ecn', method: 'put', data })
}

export function deleteEcn(id) {
  return request({ url: `/ecn/${id}`, method: 'delete' })
}

export function submitEcn(id) {
  return request({ url: `/ecn/${id}/submit`, method: 'put' })
}

export function reviewL1Approve(id, data) {
  return request({ url: `/ecn/${id}/l1/approve`, method: 'put', data })
}

export function reviewL1Reject(id, data) {
  return request({ url: `/ecn/${id}/l1/reject`, method: 'put', data })
}

export function reviewL2Approve(id, data) {
  return request({ url: `/ecn/${id}/l2/approve`, method: 'put', data })
}

export function reviewL2Reject(id, data) {
  return request({ url: `/ecn/${id}/l2/reject`, method: 'put', data })
}

export function effectEcn(id) {
  return request({ url: `/ecn/${id}/effect`, method: 'put' })
}

export function getEcnLogs(id) {
  return request({ url: `/ecn/${id}/logs`, method: 'get' })
}

export function getEcnImpacts(ecnId) {
  return request({ url: `/v1/ecns/${ecnId}/impacts`, method: 'get' })
}

export function saveEcnImpacts(ecnId, ecnNo, impactTypes) {
  return request({ url: `/v1/ecns/${ecnId}/impacts`, method: 'post', params: { ecnNo }, data: { impactTypes } })
}
