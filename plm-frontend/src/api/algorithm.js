import request from '@/utils/request'

export function decomposeDrawing(formData) {
  return request({ url: '/algorithm/drawing/decompose', method: 'post', data: formData, headers: { 'Content-Type': 'multipart/form-data' }, timeout: 120000 })
}
export function drawingCategories() {
  return request({ url: '/algorithm/drawing/categories', method: 'get' })
}
export function parseModel3d(formData) {
  return request({ url: '/algorithm/model3d/parse', method: 'post', data: formData, headers: { 'Content-Type': 'multipart/form-data' }, timeout: 120000 })
}
export function textTo3d(data) {
  return request({ url: '/algorithm/ai-modeling/text-to-3d', method: 'post', data })
}
export function algorithmHealth() {
  return request({ url: '/algorithm/health', method: 'get' })
}
export function integrationStatus() {
  return request({ url: '/integration/status', method: 'get' })
}
export function testIntegration(system) {
  return request({ url: `/integration/${system}/test`, method: 'get' })
}
export function syncMaterial(system, partNo) {
  return request({ url: `/integration/${system}/material/${partNo}/sync`, method: 'post' })
}
export function queryErpInventory(partNo) {
  return request({ url: `/integration/erp/inventory/${partNo}`, method: 'get' })
}
