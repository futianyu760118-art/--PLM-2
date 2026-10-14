import request from '@/utils/request'
import axios from 'axios'

/** 报告 Excel 返回原始字节流, 不走 Result 包装, 故用独立实例(与 ledger 附件同口径) */
const raw = axios.create({ baseURL: '/api', timeout: 300000 })
raw.interceptors.request.use(cfg => {
  const token = localStorage.getItem('plm_token')
  if (token) cfg.headers['Authorization'] = 'Bearer ' + token
  return cfg
}, e => Promise.reject(e))

// ---------- 资料分类(B1/B2) ----------

/** 可用的分类维度: 打标签与筛选用(本部门 + 共享 + 全局) */
export function categorySelect() {
  return request({ url: '/subscription/category/select', method: 'get' })
}

/** 分类管理页: 仅本部门自定义维度; editable 表示当前账号是否有管理权 */
export function categoryManage() {
  return request({ url: '/subscription/category/manage', method: 'get' })
}

export function createCategoryDim(data) {
  return request({ url: '/subscription/category/dim', method: 'post', data })
}

export function updateCategoryDim(id, data) {
  return request({ url: `/subscription/category/dim/${id}`, method: 'put', data })
}

/** 维度使用情况: 删除前二次确认提示「影响 N 条资料」 */
export function categoryDimUsage(id) {
  return request({ url: `/subscription/category/dim/${id}/usage`, method: 'get' })
}

/** 删除维度: confirm=false 时后端只回提示不删除(AC-B1-7) */
export function deleteCategoryDim(id, confirm = false) {
  return request({ url: `/subscription/category/dim/${id}`, method: 'delete', params: { confirm } })
}

export function addCategoryValue(dimId, data) {
  return request({ url: `/subscription/category/dim/${dimId}/value`, method: 'post', data })
}

export function updateCategoryValue(dimId, valueId, data) {
  return request({ url: `/subscription/category/dim/${dimId}/value/${valueId}`, method: 'put', data })
}

/** 为资料打标签: items = [{dimId, valueId}], 覆盖所选维度 */
export function assignLedgerCategory(ledgerId, items) {
  return request({ url: `/subscription/category/ledger/${ledgerId}`, method: 'post', data: { items } })
}

// ---------- 资料汇总处(B6) ----------

/**
 * 汇总处组合检索: 比台账列表多返回分类标签与附件清单。
 *
 * 分类条件按 bracket 键展开、值是数组(见 repository.vue 的 buildParams)，
 * 形如 categoryFilter.dims[0].valueIds=[12,13]。axios 默认会把数组写成
 * valueIds[]=12&valueIds[]=13，多出来的 `[]` 后端按 categoryFilter.dims[0].valueIds
 * 逐段拼路径时对不上，条件会静默失效；indexes:null 让它输出同名重复键，
 * 与后端「重复键即索引列表元素」的绑定一致(CategoryFilterBindingTest)。
 */
export function pageRepository(params) {
  return request({
    url: '/ledger/repository',
    method: 'get',
    params,
    paramsSerializer: { indexes: null }
  })
}

// ---------- 订阅(B3/B4) ----------

export function pageSubscription(params) {
  return request({ url: '/subscription', method: 'get', params })
}

export function createSubscription(data) {
  return request({ url: '/subscription', method: 'post', data })
}

export function updateSubscription(id, data) {
  return request({ url: `/subscription/${id}`, method: 'put', data })
}

export function deleteSubscription(id) {
  return request({ url: `/subscription/${id}`, method: 'delete' })
}

export function setSubscriptionStatus(id, status) {
  return request({ url: `/subscription/${id}/status`, method: 'put', params: { status } })
}

/** 资料详情页的订阅状态: {subscribed, subscriptionId, subNo} */
export function itemSubscriptionState(ledgerId) {
  return request({ url: '/subscription/item/state', method: 'get', params: { ledgerId } })
}

export function subscribeItem(ledgerId) {
  return request({ url: '/subscription/item', method: 'post', params: { ledgerId } })
}

export function unsubscribeItem(ledgerId) {
  return request({ url: '/subscription/item', method: 'delete', params: { ledgerId } })
}

// ---------- 报告(B5) ----------

export function pageReport(params) {
  return request({ url: '/subscription/report', method: 'get', params })
}

export function getReport(id) {
  return request({ url: `/subscription/report/${id}`, method: 'get' })
}

export function reportHtml(id) {
  return request({ url: `/subscription/report/${id}/html`, method: 'get' })
}

/** 下载报告 Excel 明细(AC-B5-7: 最近 12 期可回溯并下载) */
export async function downloadReportExcel(id, name) {
  const res = await raw.get(`/subscription/report/${id}/excel`, { responseType: 'blob' })
  const link = document.createElement('a')
  link.href = window.URL.createObjectURL(res.data)
  link.download = name || 'report.xlsx'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(link.href)
}
