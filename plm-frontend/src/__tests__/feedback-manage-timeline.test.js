/**
 * 总控反馈管理页 —— 状态流转时间线展示测试(AC F16a)
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import * as Icons from '@element-plus/icons-vue'

vi.mock('@/api/feedback', () => ({
  pageFeedback: vi.fn(),
  transitionFeedback: vi.fn(),
  getFeedbackByNo: vi.fn()
}))

import { pageFeedback, getFeedbackByNo } from '@/api/feedback'
import Manage from '@/views/feedback/manage.vue'

// 状态取未流转完的 EVALUATING: 终态行的「流转」按钮按设计置灰, 用它测不到流转弹窗
const ROW = {
  id: 1,
  feedbackNo: 'FB-20261002-0001',
  type: 'BUG',
  status: 'EVALUATING',
  content: '物料列表页翻页后筛选条件被重置，需要保留筛选状态。',
  expectedResult: '翻页后保留筛选条件',
  pagePath: '/material/list',
  submitterName: '提交人甲',
  contact: '13800000000',
  createdAt: '2026-10-02 19:44:09'
}

const TRANSITIONS = [
  { id: 1, fromStatus: null, toStatus: 'PENDING', note: '用户提交反馈', operatorName: '提交人甲', createdAt: '2026-10-02 19:44:09' },
  { id: 2, fromStatus: 'PENDING', toStatus: 'EVALUATING', note: '已受理，进入需求评估', operatorName: '总控甲', createdAt: '2026-10-02 19:44:17' },
  { id: 3, fromStatus: 'EVALUATING', toStatus: 'INITIATED', note: '符合 E1-E5，已立项', relatedIssueNo: 'PAND-999', operatorName: '总控甲', createdAt: '2026-10-02 19:44:18' }
]

let wrapper = null

beforeEach(() => {
  vi.clearAllMocks()
  pageFeedback.mockResolvedValue({ data: { records: [ROW], total: 1 } })
  getFeedbackByNo.mockResolvedValue({ data: { ...ROW, transitions: TRANSITIONS } })
})

afterEach(() => {
  if (wrapper) wrapper.unmount()
  wrapper = null
  document.body.innerHTML = ''
})

async function mountManage() {
  wrapper = mount(Manage, {
    attachTo: document.body,
    global: { plugins: [ElementPlus], components: Icons }
  })
  await flushPromises()
  return wrapper
}

function findButton(text) {
  const btn = wrapper.findAll('button').find(b => b.text().trim() === text)
  expect(btn, `未找到「${text}」按钮`).toBeTruthy()
  return btn
}

describe('反馈管理页 —— 状态流转时间线', () => {
  it('列表加载后每行提供详情入口', async () => {
    await mountManage()
    expect(wrapper.text()).toContain('FB-20261002-0001')
    expect(findButton('详情')).toBeTruthy()
  })

  it('打开详情展示完整状态变更记录(时间/操作方/结论)', async () => {
    await mountManage()
    await findButton('详情').trigger('click')
    await flushPromises()

    expect(getFeedbackByNo).toHaveBeenCalledWith('FB-20261002-0001')
    const text = document.body.textContent.replace(/\s+/g, ' ')
    expect(text).toContain('状态流转记录')
    expect(text).toContain('用户提交反馈')
    expect(text).toContain('已受理，进入需求评估')
    expect(text).toContain('符合 E1-E5，已立项')
    expect(text).toContain('总控甲')
    expect(text).toContain('PAND-999')
    expect(text).toContain('2026-10-02 19:44:17')
  })

  it('流转弹窗同时展示历史时间线, 便于流转前核对', async () => {
    await mountManage()
    await findButton('流转').trigger('click')
    await flushPromises()

    const text = document.body.textContent.replace(/\s+/g, ' ')
    expect(text).toContain('状态流转记录')
    expect(text).toContain('用户提交反馈')
    expect(text).toContain('符合 E1-E5，已立项')
  })

  it('详情接口失败时降级为空时间线, 不阻断页面', async () => {
    getFeedbackByNo.mockRejectedValue(new Error('network down'))
    await mountManage()
    await findButton('详情').trigger('click')
    await flushPromises()

    expect(document.body.textContent).toContain('暂无流转记录')
  })
})
