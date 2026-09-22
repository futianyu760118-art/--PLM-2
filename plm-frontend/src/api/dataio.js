import axios from 'axios'
import request from '@/utils/request'

// 下载专用实例(避开 JSON 拦截器)
const raw = axios.create({ baseURL: '/api', timeout: 120000 })
raw.interceptors.request.use(cfg => {
  const token = localStorage.getItem('plm_token')
  if (token) cfg.headers['Authorization'] = 'Bearer ' + token
  return cfg
}, e => Promise.reject(e))

export function listIoModules() { return request({ url: '/data-io/modules', method: 'get' }) }
export function selfCheckModule(module) { return request({ url: `/data-io/${module}/selfcheck`, method: 'get' }) }

export function importModule(module, file, dryRun) {
  const fd = new FormData()
  fd.append('file', file)
  return request({
    url: `/data-io/${module}/import`, method: 'post', params: { dryRun },
    data: fd, headers: { 'Content-Type': 'multipart/form-data' }, timeout: 120000
  })
}

async function download(url, fileName) {
  const res = await raw.get(url, { responseType: 'blob' })
  const blob = res.data
  if (blob && blob.type && blob.type.includes('json')) {
    const text = await blob.text()
    let msg = '下载失败'
    try { msg = JSON.parse(text).message || msg } catch (e) { /* ignore */ }
    throw new Error(msg)
  }
  const link = document.createElement('a')
  const href = window.URL.createObjectURL(blob)
  link.href = href
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(href)
}

export function downloadTemplate(module) { return download(`/data-io/${module}/template`, `${module}_导入模板.xlsx`) }
export function exportModule(module) { return download(`/data-io/${module}/export`, `${module}_数据.xlsx`) }
