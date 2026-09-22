import request from '@/utils/request'

export function listDatasets() { return request({ url: '/v1/analytics/datasets', method: 'get' }) }
export function datasetData(code) { return request({ url: `/v1/analytics/datasets/${code}/data`, method: 'post', data: {} }) }
export function listCharts() { return request({ url: '/v1/analytics/charts', method: 'get' }) }
export function chartMeta(code) { return request({ url: `/v1/analytics/charts/${code}`, method: 'get' }) }
export function chartData(code) { return request({ url: `/v1/analytics/charts/${code}/data`, method: 'post', data: {} }) }
