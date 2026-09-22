import request from '@/utils/request'

export function listIssues(status, severity) {
  return request({ url: '/v1/issues', method: 'get', params: { status, severity } })
}
export function getIssue(id) {
  return request({ url: `/v1/issues/${id}`, method: 'get' })
}
export function createIssue(data) {
  return request({ url: '/v1/issues', method: 'post', data })
}
export function updateIssueStatus(id, status) {
  return request({ url: `/v1/issues/${id}/actions/update-status`, method: 'post', params: { status } })
}
export function listActions(issueId) {
  return request({ url: `/v1/issues/${issueId}/actions`, method: 'get' })
}
export function createAction(issueId, data) {
  return request({ url: `/v1/issues/${issueId}/actions`, method: 'post', data })
}
export function completeAction(actionId) {
  return request({ url: `/v1/issues/actions/${actionId}/complete`, method: 'post' })
}
export function listInsights(status) {
  return request({ url: '/v1/issues/insights', method: 'get', params: { status } })
}
export function convertInsight(id) {
  return request({ url: `/v1/issues/insights/${id}/convert`, method: 'post' })
}
export function dismissInsight(id) {
  return request({ url: `/v1/issues/insights/${id}/dismiss`, method: 'post' })
}

// 质检官L0全库巡检：聚合DQ债务生成洞察(HIGH自动开ISSUE)
export function qaPatrol() {
  return request({ url: '/v1/agents/qa/patrol', method: 'post' })
}
