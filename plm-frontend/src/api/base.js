import axios from 'axios'
import request from '@/utils/request'

const raw = axios.create({ baseURL: '/api', timeout: 120000 })
raw.interceptors.request.use(cfg => {
  const token = localStorage.getItem('plm_token')
  if (token) cfg.headers['Authorization'] = 'Bearer ' + token
  return cfg
}, e => Promise.reject(e))

export function getBaseMeta() { return request({ url: '/v1/system/base/meta', method: 'get' }) }
export function pageBase(type, params) { return request({ url: `/v1/system/base/${type}`, method: 'get', params }) }
export function createBase(type, data) { return request({ url: `/v1/system/base/${type}`, method: 'post', data }) }
export function updateBase(type, data) { return request({ url: `/v1/system/base/${type}`, method: 'put', data }) }
export function deleteBase(type, id) { return request({ url: `/v1/system/base/${type}/${id}`, method: 'delete' }) }

export async function exportBase(type) {
  const res = await raw.get(`/v1/system/base/${type}/export`, { responseType: 'blob' })
  const url = URL.createObjectURL(res.data)
  const a = document.createElement('a')
  a.href = url; a.download = `base-${type}.xlsx`; a.click()
  URL.revokeObjectURL(url)
}
export function importBase(type, formData) {
  return raw.post(`/v1/system/base/${type}/import`, formData, { headers: { 'Content-Type': 'multipart/form-data' } })
}
