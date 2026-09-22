import request from '@/utils/request'

/** 解析档案树模板(建档向导预览将生成的目录节点) */
export function resolveArchiveTree(params) {
  return request({ url: '/v1/templates/archive-tree', method: 'get', params })
}

/** 解析参数模板(预览该品类应填的参数项) */
export function resolveParamTpl(params) {
  return request({ url: '/v1/templates/params', method: 'get', params })
}

/** 全部档案树模板 */
export function listArchiveTreeTpls() {
  return request({ url: '/v1/templates/archive-tree/list', method: 'get' })
}

/** 全部参数模板 */
export function listParamTpls() {
  return request({ url: '/v1/templates/params/list', method: 'get' })
}

/** 字典候选项(参数 ENUM 选项，如 ip_rating/cct/beam_angle) */
export function listDictOptions(dictType) {
  return request({ url: `/v1/templates/dict/${dictType}`, method: 'get' })
}
