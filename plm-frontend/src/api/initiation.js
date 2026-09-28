import axios from 'axios'
import request from '@/utils/request'

const raw = axios.create({ baseURL: '/api', timeout: 120000 })
raw.interceptors.request.use(cfg => {
  const token = localStorage.getItem('plm_token')
  if (token) cfg.headers['Authorization'] = 'Bearer ' + token
  return cfg
}, e => Promise.reject(e))

/** 下载立项申请书导入模板(xlsx) */
export async function downloadInitiationTemplate() {
  const res = await raw.get('/initiation/import-template', { responseType: 'blob' })
  const url = URL.createObjectURL(res.data)
  const a = document.createElement('a')
  a.href = url; a.download = '立项申请书导入模板.xlsx'; a.click()
  URL.revokeObjectURL(url)
}

export function pageInitiation(params) { return request({ url: '/initiation/list', method: 'get', params }) }
export function getInitiation(id) { return request({ url: `/initiation/${id}`, method: 'get' }) }
export function getInitiationByProject(projectId) { return request({ url: `/initiation/by-project/${projectId}`, method: 'get' }) }
export function initiationStats() { return request({ url: '/initiation/stats', method: 'get' }) }
export function initiationStages() { return request({ url: '/initiation/stages', method: 'get' }) }
export function createInitiation(data) { return request({ url: '/initiation', method: 'post', data }) }
export function updateInitiation(data) { return request({ url: '/initiation', method: 'put', data }) }
export function deleteInitiation(id) { return request({ url: `/initiation/${id}`, method: 'delete' }) }
export function advanceInitiation(id, data) { return request({ url: `/initiation/${id}/advance`, method: 'post', data }) }
export function rejectInitiation(id, opinion) { return request({ url: `/initiation/${id}/reject`, method: 'post', data: { opinion } }) }
export function approveToProject(id, data) { return request({ url: `/initiation/${id}/approve-to-project`, method: 'post', data }) }
