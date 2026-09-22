import request from '@/utils/request'

export function createSession(agentCode, data) { return request({ url: `/v1/agents/${agentCode}/sessions`, method: 'post', data }) }
export function listSessions() { return request({ url: '/v1/agents/sessions', method: 'get' }) }
export function closeSession(id) { return request({ url: `/v1/agents/sessions/${id}/close`, method: 'post' }) }
export function sendMessage(sessionId, data) { return request({ url: `/v1/agents/sessions/${sessionId}/messages`, method: 'post', data }) }
export function listMessages(sessionId) { return request({ url: `/v1/agents/sessions/${sessionId}/messages`, method: 'get' }) }
export function pendingActions() { return request({ url: '/v1/agents/actions', method: 'get' }) }
export function decideAction(id, data) { return request({ url: `/v1/agents/actions/${id}/decide`, method: 'post', data }) }
export function coachSuggestions() { return request({ url: '/v1/agents/coach/suggestions', method: 'get' }) }
