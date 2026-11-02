import request from '@/utils/request'
import axios from 'axios'

const raw = axios.create({ baseURL: '/api', timeout: 120000 })
raw.interceptors.request.use(cfg => {
  const token = localStorage.getItem('plm_token')
  if (token) cfg.headers['Authorization'] = 'Bearer ' + token
  return cfg
}, e => Promise.reject(e))

/** 分页查询 */
export function pageDocuments(params) {
  return request({ url: '/document/page', method: 'get', params })
}

/** 五区数量统计 */
export function documentStats() {
  return request({ url: '/document/stats', method: 'get' })
}

export function getDocument(id) {
  return request({ url: `/document/${id}`, method: 'get' })
}

export function createDocument(data) {
  return request({ url: '/document', method: 'post', data })
}

export function updateDocument(data) {
  return request({ url: '/document', method: 'put', data })
}

export function deleteDocument(id) {
  return request({ url: `/document/${id}`, method: 'delete' })
}

/** 五区流转：state = RELEASED / ARCHIVED / OBSOLETE / RECYCLE / WORKING */
export function changeDocumentState(id, state, remark) {
  return request({ url: `/document/${id}/state`, method: 'put', data: { state, remark } })
}

export function listVersions(id) {
  return request({ url: `/document/${id}/versions`, method: 'get' })
}

export function currentVersion(id) {
  return request({ url: `/document/${id}/versions/current`, method: 'get' })
}

export function addVersion(id, data) {
  return request({ url: `/document/${id}/versions`, method: 'post', data })
}

export function restoreVersion(id, versionId, changeNote) {
  return request({ url: `/document/${id}/versions/${versionId}/restore`, method: 'post', params: { changeNote } })
}

export function checkoutDocument(id) {
  return request({ url: `/document/${id}/checkout`, method: 'put' })
}

export function checkinDocument(id, data) {
  return request({ url: `/document/${id}/checkin`, method: 'put', data })
}

export function cancelCheckout(id) {
  return request({ url: `/document/${id}/checkout/cancel`, method: 'put' })
}

export function listDocumentAcl(id) {
  return request({ url: `/document/${id}/acl`, method: 'get' })
}

export function grantDocumentAcl(id, acls) {
  return request({ url: `/document/${id}/acl`, method: 'post', data: acls })
}

export function revokeDocumentAcl(id, aclId) {
  return request({ url: `/document/${id}/acl/${aclId}`, method: 'delete' })
}

export function documentsByPart(partNo) {
  return request({ url: `/document/part/${partNo}`, method: 'get' })
}

export function documentsByProject(projectNo) {
  return request({ url: `/document/project/${projectNo}`, method: 'get' })
}

/** 上传文件，返回 plm_file 记录（id 用于版本绑定） */
export function uploadDocumentFile(file, partNo) {
  const fd = new FormData()
  fd.append('file', file)
  if (partNo) fd.append('partNo', partNo)
  return request({
    url: '/file/upload',
    method: 'post',
    data: fd,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

/** 下载版本文件（带 Token，避免 window.open 401） */
export async function downloadDocumentFile(fileId, fileName) {
  const res = await raw.get(`/file/${fileId}/download`, { responseType: 'blob' })
  const link = document.createElement('a')
  link.href = window.URL.createObjectURL(res.data)
  link.download = fileName || 'document'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(link.href)
}
