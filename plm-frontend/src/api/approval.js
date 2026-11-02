import request from '@/utils/request'

// ---------- 流程定义 ----------

export function listFlows(objectType) {
  return request({ url: '/approval/flows', method: 'get', params: { objectType } })
}

export function listNodes(flowId) {
  return request({ url: `/approval/flows/${flowId}/nodes`, method: 'get' })
}

/** data = { id, flowCode, objectType, name, versionNo, status, remark, nodes: [...] } */
export function saveFlow(data) {
  return request({ url: '/approval/flows', method: 'post', data })
}

export function changeFlowStatus(flowId, status) {
  return request({ url: `/approval/flows/${flowId}/status`, method: 'put', params: { status } })
}

export function deleteFlow(flowId) {
  return request({ url: `/approval/flows/${flowId}`, method: 'delete' })
}

// ---------- 待办与动作 ----------

export function pendingTasks() {
  return request({ url: '/approval/pending', method: 'get' })
}

/** action = APPROVE / REJECT / BACK / TERMINATE */
export function actInstance(instanceId, data) {
  return request({ url: `/approval/${instanceId}/act`, method: 'post', data })
}

export function cancelInstance(instanceId, comment) {
  return request({ url: `/approval/${instanceId}/cancel`, method: 'post', params: { comment } })
}

export function getProgress(objectType, objectNo) {
  return request({ url: '/approval/progress', method: 'get', params: { objectType, objectNo } })
}

export function getHistory(instanceId) {
  return request({ url: `/approval/${instanceId}/history`, method: 'get' })
}
