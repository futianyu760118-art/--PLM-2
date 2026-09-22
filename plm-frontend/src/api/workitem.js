import request from '@/utils/request'

export function myWorkItems(status) { return request({ url: '/v1/work-items/my', method: 'get', params: { status } }) }
export function listWorkItems(params) { return request({ url: '/v1/work-items', method: 'get', params }) }
export function createWorkItem(data) { return request({ url: '/v1/work-items', method: 'post', data }) }
export function completeWorkItem(id) { return request({ url: `/v1/work-items/${id}/complete`, method: 'post' }) }
