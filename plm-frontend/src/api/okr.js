import request from '@/utils/request'

export function getCycles() { return request({ url: '/v1/okrs/cycles', method: 'get' }) }
export function createCycle(data) { return request({ url: '/v1/okrs/cycles', method: 'post', data }) }
export function getTree(cycleId) { return request({ url: `/v1/okrs/cycles/${cycleId}/tree`, method: 'get' }) }
export function createObjective(data) { return request({ url: '/v1/okrs/objectives', method: 'post', data }) }
export function createKr(data) { return request({ url: '/v1/okrs/key-results', method: 'post', data }) }
export function checkin(krId, data) { return request({ url: `/v1/okrs/key-results/${krId}/checkins`, method: 'post', data }) }
export function syncFromKpi() { return request({ url: '/v1/okrs/sync-from-kpi', method: 'post' }) }
