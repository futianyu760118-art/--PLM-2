import request from '@/utils/request'

export function listSuppliers(materialId) {
  return request({ url: `/v1/parts/${materialId}/suppliers`, method: 'get' })
}

export function listHistory(objectType, objectId) {
  return request({ url: `/v1/history/${objectType}/${objectId}`, method: 'get' })
}

export function snapshotAt(objectType, objectId, version) {
  return request({ url: `/v1/history/${objectType}/${objectId}/snapshot`, method: 'get', params: { version } })
}

export function uploadDrawing(formData) {
  return request({ url: '/file/upload', method: 'post', data: formData, headers: { 'Content-Type': 'multipart/form-data' } })
}