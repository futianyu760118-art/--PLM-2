import request from '@/utils/request'

export function listRoutes(params) { return request({ url: '/process/routes', method: 'get', params }) }
export function getRoute(id) { return request({ url: `/process/routes/${id}`, method: 'get' }) }
export function createRoute(data) { return request({ url: '/process/routes', method: 'post', data }) }
export function completeStep(id, remark) { return request({ url: `/process/steps/${id}/complete`, method: 'post', data: { remark } }) }
export function skipStep(id, remark) { return request({ url: `/process/steps/${id}/skip`, method: 'post', data: { remark } }) }
export function cancelRoute(id) { return request({ url: `/process/routes/${id}/cancel`, method: 'post' }) }
export function mySteps() { return request({ url: '/process/my-steps', method: 'get' }) }
