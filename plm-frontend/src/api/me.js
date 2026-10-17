import request from '@/utils/request'

export function meOverview() { return request({ url: '/v1/me/overview', method: 'get' }) }
export function myTodos() { return request({ url: '/v1/me/todos', method: 'get' }) }
export function myScope() { return request({ url: '/v1/me/scope', method: 'get' }) }
export function setMyScope(dataScope) { return request({ url: '/v1/me/scope', method: 'put', data: { dataScope } }) }
