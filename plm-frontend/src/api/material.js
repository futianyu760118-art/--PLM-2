import request from '@/utils/request'

export function pageMaterial(params) {
  return request({ url: '/material/page', method: 'get', params })
}
export function getMaterial(id) {
  return request({ url: `/material/${id}`, method: 'get' })
}
export function getByPartNo(partNo) {
  return request({ url: `/material/partNo/${partNo}`, method: 'get' })
}
export function addMaterial(data) {
  return request({ url: '/material', method: 'post', data })
}
export function updateMaterial(data) {
  return request({ url: '/material', method: 'put', data })
}
export function deleteMaterial(id) {
  return request({ url: `/material/${id}`, method: 'delete' })
}
export function releaseMaterial(id) {
  return request({ url: `/material/${id}/release`, method: 'put' })
}
export function submitReview(id) {
  return request({ url: `/material/${id}/review`, method: 'put' })
}
export function checkPartNo(partNo, excludeId) {
  return request({ url: `/material/check/${partNo}`, method: 'get', params: { excludeId } })
}

// ===== 物料参数(灯具参数模板承载) =====

/** 获取料号参数(模板定义+实际值+ENUM选项) */
export function getMaterialParams(partNo) {
  return request({ url: `/v1/materials/${partNo}/params`, method: 'get' })
}

/** 批量保存料号参数值 */
export function saveMaterialParams(partNo, values) {
  return request({ url: `/v1/materials/${partNo}/params`, method: 'put', data: { values } })
}

export function toProduction(id) {
  return request({ url: `/material/${id}/actions/to-production`, method: 'post' })
}
export function obsoleteMaterial(id) {
  return request({ url: `/material/${id}/actions/obsolete`, method: 'post' })
}
export function sealMaterial(id) {
  return request({ url: `/material/${id}/actions/seal`, method: 'post' })
}
export function dqCheck(id) {
  return request({ url: `/material/${id}/actions/dq-check`, method: 'post' })
}
export function getScore(id) {
  return request({ url: `/material/${id}/score`, method: 'get' })
}
export function getVersions(id) {
  return request({ url: `/material/${id}/versions`, method: 'get' })
}
export function importParts(products) {
  return request({ url: '/v1/parts/import', method: 'post', data: { products } })
}
