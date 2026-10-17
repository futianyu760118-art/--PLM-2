import request from '@/utils/request'

export function meOverview() { return request({ url: '/v1/me/overview', method: 'get' }) }
