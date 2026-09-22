import request from '@/utils/request'

export function pageBom(params) {
  return request({ url: '/bom/page', method: 'get', params })
}
export function getBom(id) {
  return request({ url: `/bom/${id}`, method: 'get' })
}
export function createBom(data) {
  return request({ url: '/bom', method: 'post', data })
}
export function getBomTree(bomId) {
  return request({ url: `/bom/${bomId}/tree`, method: 'get' })
}
export function getBomFlat(bomId) {
  return request({ url: `/bom/${bomId}/flat`, method: 'get' })
}
export function addBomItem(data) {
  return request({ url: '/bom/item', method: 'post', data })
}
export function updateBomItem(data) {
  return request({ url: '/bom/item', method: 'put', data })
}
export function deleteBomItem(itemId) {
  return request({ url: `/bom/item/${itemId}`, method: 'delete' })
}
export function releaseBom(bomId) {
  return request({ url: `/bom/${bomId}/release`, method: 'put' })
}
export function archiveBom(bomId, ecnNo, reason) {
  return request({ url: `/bom/${bomId}/archive`, method: 'put', params: { ecnNo, reason } })
}
export function buildMbom(ebomId) {
  return request({ url: `/bom/${ebomId}/actions/build-mbom`, method: 'post' })
}
export function buildSbom(sourceBomId) {
  return request({ url: `/bom/${sourceBomId}/actions/build-sbom`, method: 'post' })
}
export function whereUsed(partNo) {
  return request({ url: `/bom/where-used/${partNo}`, method: 'get' })
}
export function cbomExpand(rootPartNo) {
  return request({ url: `/bom/cbom/${rootPartNo}`, method: 'get' })
}
