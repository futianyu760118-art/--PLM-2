/**
 * 反馈状态流转时间线组件测试(AC F16a)
 */
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import * as Icons from '@element-plus/icons-vue'
import FeedbackTimeline from '@/components/feedback/FeedbackTimeline.vue'

function mountTimeline(items) {
  return mount(FeedbackTimeline, {
    props: { items },
    global: { plugins: [ElementPlus], components: Icons }
  })
}

describe('FeedbackTimeline', () => {
  it('无记录时展示空状态', () => {
    const wrapper = mountTimeline([])
    expect(wrapper.text()).toContain('暂无流转记录')
  })

  it('起点记录以「提交」代替空的前序状态', () => {
    const wrapper = mountTimeline([
      {
        id: 1,
        fromStatus: null,
        toStatus: 'PENDING',
        note: '用户提交反馈',
        operatorName: '用户1',
        createdAt: '2026-10-02 10:00:00'
      }
    ])
    const text = wrapper.text()
    expect(text).toContain('提交')
    expect(text).toContain('待分析')
    expect(text).toContain('用户1')
    expect(text).toContain('用户提交反馈')
    expect(text).toContain('2026-10-02 10:00:00')
  })

  it('多步流转逐条展示, 含前序状态、结论与立项编号', () => {
    const wrapper = mountTimeline([
      {
        id: 1,
        fromStatus: null,
        toStatus: 'PENDING',
        note: '用户提交反馈',
        operatorName: '用户1',
        createdAt: '2026-10-02 10:00:00'
      },
      {
        id: 2,
        fromStatus: 'PENDING',
        toStatus: 'EVALUATING',
        note: '开始评估',
        operatorName: '总控甲',
        createdAt: '2026-10-02 11:00:00'
      },
      {
        id: 3,
        fromStatus: 'EVALUATING',
        toStatus: 'INITIATED',
        note: '符合 E1-E5, 已立项',
        relatedIssueNo: 'PAND-999',
        operatorName: '总控甲',
        createdAt: '2026-10-02 12:00:00'
      }
    ])
    const text = wrapper.text()
    expect(wrapper.findAll('.el-timeline-item')).toHaveLength(3)
    expect(text).toContain('评估中')
    expect(text).toContain('已立项')
    expect(text).toContain('开始评估')
    expect(text).toContain('符合 E1-E5, 已立项')
    expect(text).toContain('PAND-999')
  })

  it('退回记录展示退回原因', () => {
    const wrapper = mountTimeline([
      {
        id: 1,
        fromStatus: 'EVALUATING',
        toStatus: 'RETURNED',
        note: '不满足 E3: 与既有能力池重复',
        operatorName: '总控乙',
        createdAt: '2026-10-02 13:00:00'
      }
    ])
    const text = wrapper.text()
    expect(text).toContain('已退回')
    expect(text).toContain('不满足 E3: 与既有能力池重复')
    expect(text).toContain('总控乙')
  })
})
