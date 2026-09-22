import request from '@/utils/request'

export function listTemplates() {
  return request({ url: '/v1/boms/templates/list', method: 'get' })
}

export function createFromTemplates(rootPartNo, templateCodes, includeOptional = true) {
  return request({ url: '/v1/boms/actions/create-from-templates', method: 'post', data: { rootPartNo, templateCodes, includeOptional } })
}

export function reorderItems(bomId, itemIds) {
  return request({ url: `/v1/boms/${bomId}/actions/reorder`, method: 'post', data: itemIds })
}

export function exportBom({ rootPartNo, includeSubBom = true }) {
  return request({
    url: '/v1/exports',
    method: 'post',
    data: {
      exportType: 'RESOURCE',
      format: 'csv',
      name: includeSubBom ? `${rootPartNo} 全BOM爆炸` : `${rootPartNo} 单层BOM`,
      spec: { resource: 'bom', rootPartNo, includeSubBom }
    }
  })
}