import request from '@/utils/request'

export function pageUser(params) {
  return request({ url: '/system/user/page', method: 'get', params })
}
export function createUser(data) {
  return request({ url: '/system/user', method: 'post', data })
}
export function updateUser(data) {
  return request({ url: '/system/user', method: 'put', data })
}
export function resetPassword(id, password) {
  return request({ url: `/system/user/${id}/password`, method: 'put', params: { password } })
}
export function deleteUser(id) {
  return request({ url: `/system/user/${id}`, method: 'delete' })
}
export function listRoles() {
  return request({ url: '/system/user/roles', method: 'get' })
}
export function listRoleAll() {
  return request({ url: '/system/role/list', method: 'get' })
}
export function createRole(data) {
  return request({ url: '/system/role', method: 'post', data })
}
export function updateRole(data) {
  return request({ url: '/system/role', method: 'put', data })
}
export function deleteRole(id) {
  return request({ url: `/system/role/${id}`, method: 'delete' })
}
export function listPermissions() {
  return request({ url: '/system/role/permissions', method: 'get' })
}
export function getRolePermissions(roleId) {
  return request({ url: `/system/role/${roleId}/permissions`, method: 'get' })
}
export function assignPermissions(roleId, permissionIds) {
  return request({ url: `/system/role/${roleId}/permissions`, method: 'put', data: { permissionIds } })
}
export function pageDict(params) {
  return request({ url: '/system/dict/page', method: 'get', params })
}
export function createDict(data) {
  return request({ url: '/system/dict', method: 'post', data })
}
export function updateDict(data) {
  return request({ url: '/system/dict', method: 'put', data })
}
export function deleteDict(id) {
  return request({ url: `/system/dict/${id}`, method: 'delete' })
}
export function pageLog(params) {
  return request({ url: '/system/log/page', method: 'get', params })
}
