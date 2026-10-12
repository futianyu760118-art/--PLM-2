import request from '@/utils/request'

/** 唯一提交入口 */
export function submitFeedback(data) {
  return request({ url: '/v1/feedback', method: 'post', data })
}

/** 凭反馈编号查询状态(本人或总控) */
export function getFeedbackByNo(feedbackNo) {
  return request({ url: `/v1/feedback/${feedbackNo}`, method: 'get' })
}

/** 总控反馈分页列表 */
export function pageFeedback(params) {
  return request({ url: '/v1/feedback/page', method: 'get', params })
}

/** 总控反馈状态流转 */
export function transitionFeedback(id, data) {
  return request({ url: `/v1/feedback/${id}/transition`, method: 'post', data })
}
