import request from '@/utils/request'

export function getArchiveTree(partNo) {
  return request({ url: `/archive/tree/${partNo}`, method: 'get' })
}
export function generateArchive(partNo) {
  return request({ url: `/archive/generate/${partNo}`, method: 'post' })
}
export function attachFile(partNo, nodeCode, fileId) {
  return request({ url: '/archive/attach', method: 'post', params: { partNo, nodeCode, fileId } })
}
export function getNodeFiles(nodeId) {
  return request({ url: `/archive/node/${nodeId}/files`, method: 'get' })
}
export function removeArchiveFile(archiveFileId) {
  return request({ url: `/archive/file/${archiveFileId}`, method: 'delete' })
}
export function uploadFile(formData) {
  return request({ url: '/file/upload', method: 'post', data: formData, headers: { 'Content-Type': 'multipart/form-data' } })
}
export function getFileUrl(id) {
  return `/api/file/${id}/download`
}
