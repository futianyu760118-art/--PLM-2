import request from '@/utils/request'

export function listKnowledge(keyword) { return request({ url: '/v1/knowledge', method: 'get', params: { keyword } }) }
export function createKnowledge(data) { return request({ url: '/v1/knowledge', method: 'post', data }) }
export function askKnowledge(question) { return request({ url: '/v1/knowledge/ask', method: 'post', data: { question } }) }
export function deleteKnowledge(id) { return request({ url: `/v1/knowledge/${id}`, method: 'delete' }) }
