import request from '@/utils/request'

export function pageMold(params) {
  return request({ url: '/mold/page', method: 'get', params })
}
export function getMold(id) {
  return request({ url: `/mold/${id}`, method: 'get' })
}
export function createMold(data) {
  return request({ url: '/mold', method: 'post', data })
}
export function updateMold(data) {
  return request({ url: '/mold', method: 'put', data })
}
export function updateShots(id, shots) {
  return request({ url: `/mold/${id}/shots`, method: 'put', params: { shots } })
}
export function scrapMold(id) {
  return request({ url: `/mold/${id}/scrap`, method: 'put' })
}
export function getTrials(moldNo) {
  return request({ url: `/mold/${moldNo}/trials`, method: 'get' })
}
export function addTrial(data) {
  return request({ url: '/mold/trial', method: 'post', data })
}
export function listSop(params) {
  return request({ url: '/injection-sop/list', method: 'get', params })
}
export function saveSop(data) {
  return request({ url: '/injection-sop', method: 'post', data })
}
export function releaseSop(id) {
  return request({ url: `/injection-sop/${id}/release`, method: 'put' })
}
