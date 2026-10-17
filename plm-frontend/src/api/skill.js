import request from '@/utils/request'

export function pageSkills(params) { return request({ url: '/v1/skills', method: 'get', params }) }
export function getSkill(id) { return request({ url: `/v1/skills/${id}`, method: 'get' }) }
export function createSkill(data) { return request({ url: '/v1/skills', method: 'post', data }) }
export function updateSkill(data) { return request({ url: '/v1/skills', method: 'put', data }) }
export function deleteSkill(id) { return request({ url: `/v1/skills/${id}`, method: 'delete' }) }
export function agentGenerateSkill(description) { return request({ url: '/v1/skills/agent-generate', method: 'post', data: { description } }) }
