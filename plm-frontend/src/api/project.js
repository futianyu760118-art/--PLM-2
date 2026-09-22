import request from '@/utils/request'

export function pageProject(params) {
  return request({ url: '/v1/projects', method: 'get', params })
}
export function getProject(id) {
  return request({ url: `/v1/projects/${id}`, method: 'get' })
}
export function createProject(data) {
  return request({ url: '/v1/projects', method: 'post', data })
}
export function updateProject(data) {
  return request({ url: '/v1/projects', method: 'put', data })
}
export function deleteProject(id) {
  return request({ url: `/v1/projects/${id}`, method: 'delete' })
}
export function getGateDefs() {
  return request({ url: '/v1/projects/gates/defs', method: 'get' })
}
export function getGateLogs(projectId) {
  return request({ url: `/v1/projects/${projectId}/gates`, method: 'get' })
}
export function passGate(projectId, gateCode, data) {
  return request({ url: `/v1/projects/${projectId}/gates/${gateCode}/pass`, method: 'post', data })
}
export function failGate(projectId, gateCode, data) {
  return request({ url: `/v1/projects/${projectId}/gates/${gateCode}/fail`, method: 'post', data })
}
