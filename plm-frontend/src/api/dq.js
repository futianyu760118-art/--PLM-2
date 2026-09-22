import request from '@/utils/request'

export function listDebts(status) {
  return request({ url: '/v1/dq/debts', method: 'get', params: status ? { status } : {} })
}

export function listRules() {
  return request({ url: '/v1/dq/rules', method: 'get' })
}

export function listAttempts(params) {
  return request({ url: '/v1/dq/fix/attempts', method: 'get', params: params || {} })
}

export function fixDebt(debtId) {
  return request({ url: `/v1/dq/fix/${debtId}`, method: 'post' })
}

export function batchFixDebts(limit = 5) {
  return request({ url: '/v1/dq/fix/batch', method: 'post', params: { limit } })
}