import request from '@/utils/request'
import axios from 'axios'

/** 附件下载/预览返回原始字节流, 不走 Result 包装, 故用独立实例(与 feedback 同口径) */
const raw = axios.create({ baseURL: '/api', timeout: 300000 })
raw.interceptors.request.use(cfg => {
  const token = localStorage.getItem('plm_token')
  if (token) cfg.headers['Authorization'] = 'Bearer ' + token
  return cfg
}, e => Promise.reject(e))

// ---------- 查询(证据台账 / 资料汇总处 共用, AC-A5-1~A5-5) ----------

export function pageLedger(params) {
  return request({ url: '/ledger/list', method: 'get', params })
}

export function getLedger(id) {
  return request({ url: `/ledger/${id}`, method: 'get' })
}

/** 模块/表单侧证据区: 按关联对象取引用条目(AC-A3-1/A3-5/A3-6) */
export function ledgerByObject(objectType, objectId) {
  return request({ url: '/ledger/by-object', method: 'get', params: { objectType, objectId } })
}

/** 录入表单下拉项与边界值(类型/状态/对象类型/附件与关联上限) */
export function ledgerOptions() {
  return request({ url: '/ledger/options', method: 'get' })
}

export function searchLedgerObjects(objectType, keyword, limit = 20) {
  return request({ url: '/ledger/objects', method: 'get', params: { objectType, keyword, limit } })
}

/** 责任人候选(AC-A2-2); 非管理员录入人也可调用 */
export function searchLedgerOwners(keyword, limit = 20) {
  return request({ url: '/ledger/owners', method: 'get', params: { keyword, limit } })
}

/** 按证据编号还原完整变更轨迹(AC-A6-7) */
export function ledgerTrail(ledgerNo) {
  return request({ url: `/ledger/trail/${encodeURIComponent(ledgerNo)}`, method: 'get' })
}

// ---------- 录入(AC-A1/A2) ----------

export function createLedger(data) {
  return request({ url: '/ledger', method: 'post', data })
}

export function updateLedger(id, data) {
  return request({ url: `/ledger/${id}`, method: 'put', data })
}

export function deleteLedger(id) {
  return request({ url: `/ledger/${id}`, method: 'delete' })
}

/**
 * 批量上传: N 个文件合并为 1 条多附件证据(AC-A5-6)。
 * meta 以 JSON part 提交, 与后端 @RequestPart("meta") 对应。
 */
export function batchUploadLedger(meta, files) {
  const fd = new FormData()
  files.forEach(f => fd.append('files', f))
  fd.append('meta', new Blob([JSON.stringify(meta)], { type: 'application/json' }))
  return request({
    url: '/ledger/batch-upload', method: 'post', data: fd,
    headers: { 'Content-Type': 'multipart/form-data' }, timeout: 600000
  })
}

// ---------- 附件 ----------

/** 上传单个文件到文件中心, 取回 fileId(供 appendLedgerAttachments 使用) */
export function uploadLedgerFile(file, onProgress) {
  const fd = new FormData()
  fd.append('file', file)
  return request({
    url: '/file/upload', method: 'post', data: fd,
    headers: { 'Content-Type': 'multipart/form-data' }, timeout: 600000,
    onUploadProgress: onProgress
  })
}

export function appendLedgerAttachments(id, fileIds) {
  return request({ url: `/ledger/${id}/attachments`, method: 'post', data: { fileIds } })
}

export function removeLedgerAttachment(id, attachmentId) {
  return request({ url: `/ledger/${id}/attachments/${attachmentId}`, method: 'delete' })
}

/** 下载附件(服务端记 DOWNLOAD 操作日志, AC-A6-4) */
export async function downloadLedgerAttachment(attachmentId, name) {
  const res = await raw.get(`/ledger/attachments/${attachmentId}/download`, { responseType: 'blob' })
  const link = document.createElement('a')
  link.href = window.URL.createObjectURL(res.data)
  link.download = name || 'attachment'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(link.href)
}

/** 在线预览附件: 返回可直接绑给 img/iframe 的 blob URL */
export async function fetchLedgerPreview(attachmentId) {
  const res = await raw.get(`/ledger/attachments/${attachmentId}/preview`, { responseType: 'blob' })
  return {
    blob: res.data,
    url: window.URL.createObjectURL(res.data),
    type: res.headers['content-type'] || 'application/octet-stream'
  }
}

// ---------- 关联(A4) ----------

export function addLedgerLink(id, data) {
  return request({ url: `/ledger/${id}/links`, method: 'post', data })
}

/** 解除关联: 只解除引用, 台账本体保留(AC-A4-3) */
export function removeLedgerLink(id, linkId) {
  return request({ url: `/ledger/${id}/links/${linkId}`, method: 'delete' })
}

/** 批量关联: N 条证据关联到同一对象(AC-A5-7/A5-8) */
export function batchLinkLedger(ledgerIds, objectType, objectId) {
  return request({ url: '/ledger/batch-link', method: 'post', data: { ledgerIds, objectType, objectId } })
}

/** 模块/表单侧回写台账(双向同步, AC-A3-2) */
export function syncLedger(data) {
  return request({ url: '/ledger/sync', method: 'post', data })
}

// ---------- 变更通知(A4-7~A4-11) ----------

export function pageLedgerNotification(params) {
  return request({ url: '/ledger/notification/list', method: 'get', params })
}

export function ledgerUnreadCount() {
  return request({ url: '/ledger/notification/unread-count', method: 'get' })
}

export function getLedgerNotification(id) {
  return request({ url: `/ledger/notification/${id}`, method: 'get' })
}

export function readLedgerNotification(ids) {
  return request({ url: '/ledger/notification/read', method: 'post', data: { ids } })
}

export function readAllLedgerNotification() {
  return request({ url: '/ledger/notification/read-all', method: 'post' })
}

export function ledgerNotifySettings() {
  return request({ url: '/ledger/notification/settings', method: 'get' })
}

export function saveLedgerNotifySetting(notifyType, enabled) {
  return request({ url: '/ledger/notification/settings', method: 'put', data: { notifyType, enabled } })
}
