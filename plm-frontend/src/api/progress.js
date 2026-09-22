import axios from 'axios'
import request from '@/utils/request'

const raw = axios.create({ baseURL: '/api', timeout: 120000 })
raw.interceptors.request.use(cfg => {
  const token = localStorage.getItem('plm_token')
  if (token) cfg.headers['Authorization'] = 'Bearer ' + token
  return cfg
}, e => Promise.reject(e))

export function getNodeMatrix(projectId) { return request({ url: `/project/${projectId}/nodes`, method: 'get' }) }
export function updateNode(projectId, code, data) { return request({ url: `/project/${projectId}/nodes/${code}`, method: 'put', data }) }
export function listEvidence(nodeId) { return request({ url: `/project/nodes/${nodeId}/evidence`, method: 'get' }) }
export function deleteEvidence(id) { return request({ url: `/project/nodes/evidence/${id}`, method: 'delete' }) }
export function progressCheck(projectId) { return request({ url: `/project/${projectId}/progress-check`, method: 'get' }) }

export function createTextEvidence(nodeId, data) { return request({ url: `/project/nodes/${nodeId}/evidence/text`, method: 'post', data }) }
export function getEvidence(id) { return request({ url: `/project/nodes/evidence/${id}`, method: 'get' }) }
export function updateEvidence(id, data) { return request({ url: `/project/nodes/evidence/${id}`, method: 'put', data }) }
export function evidenceList(params) { return request({ url: '/project/evidence/list', method: 'get', params }) }
export function evidenceStats(params) { return request({ url: '/project/evidence/stats', method: 'get', params }) }

/** 取文件/预览为 blob URL(带鉴权), 供系统内打开 */
export async function fetchPreview(fileId) {
  const res = await raw.get(`/file/${fileId}/preview`, { responseType: 'blob' })
  const blob = res.data
  const type = blob.type || ''
  let text = ''
  if (type.startsWith('text/')) { try { text = await blob.text() } catch (e) { /* ignore */ } }
  return { url: window.URL.createObjectURL(blob), type, text }
}

/** 取文本证据内容 */
export async function fetchTextEvidence(evidenceId) {
  const res = await request({ url: `/project/nodes/evidence/${evidenceId}`, method: 'get' })
  return res.data
}

export function uploadEvidence(nodeId, file, docType, note) {
  const fd = new FormData()
  fd.append('file', file)
  return request({
    url: `/project/nodes/${nodeId}/evidence`, method: 'post', params: { docType, note },
    data: fd, headers: { 'Content-Type': 'multipart/form-data' }, timeout: 120000
  })
}

export async function downloadEvidence(fileId, fileName) {
  const res = await raw.get(`/file/${fileId}/download`, { responseType: 'blob' })
  const blob = res.data
  const link = document.createElement('a')
  const href = window.URL.createObjectURL(blob)
  link.href = href
  link.download = fileName || 'evidence'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(href)
}
