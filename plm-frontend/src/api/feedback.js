import request from '@/utils/request'
import axios from 'axios'

const raw = axios.create({ baseURL: '/api', timeout: 120000 })
raw.interceptors.request.use(cfg => {
  const token = localStorage.getItem('plm_token')
  if (token) cfg.headers['Authorization'] = 'Bearer ' + token
  return cfg
}, e => Promise.reject(e))

export function listFeedback(params) { return request({ url: '/v1/feedback', method: 'get', params }) }
export function getFeedback(id) { return request({ url: `/v1/feedback/${id}`, method: 'get' }) }
export function createFeedback(data) { return request({ url: '/v1/feedback', method: 'post', data }) }
export function updateFeedback(id, data) { return request({ url: `/v1/feedback/${id}`, method: 'put', data }) }
export function replyFeedback(id, data) { return request({ url: `/v1/feedback/${id}/reply`, method: 'post', data }) }
export function changeFeedbackStatus(id, status) { return request({ url: `/v1/feedback/${id}/status`, method: 'patch', data: { status } }) }
export function aiQueueFeedback(id) { return request({ url: `/v1/feedback/${id}/ai-queue`, method: 'post' }) }
export function aiCancelFeedback(id) { return request({ url: `/v1/feedback/${id}/ai-cancel`, method: 'post' }) }
export function deleteFeedback(id) { return request({ url: `/v1/feedback/${id}`, method: 'delete' }) }

export function uploadFeedbackFile(file) {
  const fd = new FormData()
  fd.append('file', file)
  return request({ url: '/file/upload', method: 'post', data: fd, headers: { 'Content-Type': 'multipart/form-data' }, timeout: 120000 })
}

export async function downloadFeedbackFile(fileId, name) {
  const res = await raw.get(`/file/${fileId}/download`, { responseType: 'blob' })
  const blob = res.data
  const link = document.createElement('a')
  link.href = window.URL.createObjectURL(blob)
  link.download = name || 'attachment'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(link.href)
}
