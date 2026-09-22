import request from '@/utils/request'

export function pageModel3d(params) {
  return request({ url: '/model3d/page', method: 'get', params })
}
export function uploadModel3d(formData) {
  return request({ url: '/model3d/upload', method: 'post', data: formData, headers: { 'Content-Type': 'multipart/form-data' } })
}
export function explodeModel3d(id) {
  return request({ url: `/model3d/${id}/explode`, method: 'post' })
}
export function convertToGlb(id) {
  return request({ url: `/model3d/${id}/convert-glb`, method: 'post', timeout: 120000 })
}
export function previewModel3d(id) {
  return request({ url: `/model3d/${id}/preview`, method: 'get' })
}
