import request from '@/utils/request'

export function lifecycleOverview(params) { return request({ url: '/v1/lifecycle/overview', method: 'get', params }) }
export function lifecycleProjectCard(projectId) { return request({ url: '/v1/lifecycle/project-card', method: 'get', params: { projectId } }) }
