/**
 * PAND-201 能力线 B（资料分类订阅 + 报告推送 + 资料汇总处查询）前端契约测试。
 *
 * 覆盖四件事：
 *  1. 订阅/分类/报告 API 模块与后端路由一致（B1~B6 的接口面）；
 *  2. 分类条件的 GET 参数名契约 —— 这是 GET 查询串最容易与后端绑定器走岔的地方，
 *     与后端 CategoryFilterBindingTest（参数名 → LedgerQuery 对象路径）互为两端；
 *  3. /subscription 路由组注册与权限码接入守卫（AC-B6-4 的界面侧拦截面）；
 *  4. 三个页面的操作指引存在（操作说明书在界面内的可发现性）。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

/** vitest 的 cwd 即前端工程根；jsdom 下 import.meta.url 不是 file: 协议，取不到源码 */
function readSource(relPath) {
  return readFileSync(join(process.cwd(), relPath), 'utf8')
}

// jsdom 不实现 Blob URL，报告 Excel 下载走 <a download> 时需自行提供
window.URL.createObjectURL = vi.fn(() => 'blob:mock')
window.URL.revokeObjectURL = vi.fn()
HTMLAnchorElement.prototype.click = vi.fn()

// request 封装在测试中被替换为可捕获调用的桩，raw 实例来自 axios.create
// vi.mock 会被提升到文件顶部，桩必须经 vi.hoisted 定义
const { requestMock, rawGet } = vi.hoisted(() => ({
  requestMock: vi.fn(() => Promise.resolve({ data: {} })),
  rawGet: vi.fn(() => Promise.resolve({ data: new Blob(), headers: {} }))
}))
vi.mock('@/utils/request', () => ({ default: requestMock }))

vi.mock('axios', () => ({
  default: {
    create: () => ({ get: rawGet, interceptors: { request: { use: () => {} } } })
  }
}))

import { canAccess } from '@/router'
import { useUserStore } from '@/stores/user'
import { operationGuides } from '@/config/operationGuides'
import * as subApi from '@/api/subscription'

function to(...metas) {
  return { matched: metas.map((meta) => ({ meta })) }
}

describe('能力B · 分类与订阅 API 契约', () => {
  beforeEach(() => {
    requestMock.mockClear()
    rawGet.mockClear()
  })

  it('分类/订阅/报告接口齐备', () => {
    const names = ['categorySelect', 'categoryManage', 'createCategoryDim', 'updateCategoryDim',
      'categoryDimUsage', 'deleteCategoryDim', 'addCategoryValue', 'updateCategoryValue',
      'assignLedgerCategory', 'pageRepository',
      'pageSubscription', 'createSubscription', 'updateSubscription', 'deleteSubscription',
      'setSubscriptionStatus', 'itemSubscriptionState', 'subscribeItem', 'unsubscribeItem',
      'pageReport', 'getReport', 'reportHtml', 'downloadReportExcel']
    names.forEach(n => expect(typeof subApi[n]).toBe('function'))
  })

  it('AC-B1-7 删除维度：confirm=false 时不带确认标记，true 时才带', async () => {
    await subApi.deleteCategoryDim(7)
    expect(requestMock.mock.calls[0][0].params).toEqual({ confirm: false })
    await subApi.deleteCategoryDim(7, true)
    expect(requestMock.mock.calls[1][0].params).toEqual({ confirm: true })
  })

  it('AC-B1-6 打标签：items 为 [{dimId,valueId}]，同维度覆盖式提交', async () => {
    await subApi.assignLedgerCategory(100, [{ dimId: 5, valueId: 12 }])
    const [cfg] = requestMock.mock.calls[0]
    expect(cfg.url).toBe('/subscription/category/ledger/100')
    expect(cfg.method).toBe('post')
    expect(cfg.data).toEqual({ items: [{ dimId: 5, valueId: 12 }] })
  })

  it('AC-B3-1/B3-5 单条订阅：订阅与退订共用 /subscription/item，靠方法区分', async () => {
    await subApi.subscribeItem(100)
    await subApi.unsubscribeItem(100)
    expect(requestMock.mock.calls[0][0]).toMatchObject({ url: '/subscription/item', method: 'post', params: { ledgerId: 100 } })
    expect(requestMock.mock.calls[1][0]).toMatchObject({ url: '/subscription/item', method: 'delete', params: { ledgerId: 100 } })
  })

  it('AC-B4-6 暂停/恢复：状态走 query 参数而不是请求体', async () => {
    await subApi.setSubscriptionStatus(3, 'PAUSED')
    expect(requestMock.mock.calls[0][0]).toMatchObject({
      url: '/subscription/3/status', method: 'put', params: { status: 'PAUSED' }
    })
  })

  it('AC-B5-7 报告 Excel 走原始字节流实例（非 Result 包装）并触发下载', async () => {
    await subApi.downloadReportExcel(9, '订阅报告.xlsx')
    expect(rawGet).toHaveBeenCalledWith('/subscription/report/9/excel', { responseType: 'blob' })
    expect(window.URL.createObjectURL).toHaveBeenCalled()
  })

  it('AC-B6-1 资料汇总处检索由本模块转发到 /ledger/repository（与能力A同一入口，不另起一套）', async () => {
    await subApi.pageRepository({ keyword: '投光灯', pageNum: 1, pageSize: 20 })
    expect(requestMock.mock.calls[0][0]).toMatchObject({ url: '/ledger/repository', method: 'get' })
  })
})

describe('能力B · 分类筛选 GET 参数名契约（AC-B6-1）', () => {
  /**
   * 汇总处把分类条件按参数名展开（src/views/ledger/repository.vue:360-371）。
   * buildParams 是 SFC setup 内的局部函数，无法直接导入；改动它却会让后端绑定
   * 静默失配（参数名对不上时后端只会得到空条件，不报错），所以这里从源码钉住
   * 参数名的写法，再用真实 axios 序列化验证「数组值会展开成重复键」。
   */
  it('汇总处按 categoryFilter.dims[i].dimId / .valueIds 展开参数名', () => {
    const src = readSource('src/views/ledger/repository.vue')
    expect(src).toContain('categoryFilter.dims[${index}].dimId')
    expect(src).toContain('categoryFilter.dims[${index}].valueIds')
  })

  it('数组值经真实 axios 序列化后成为同名重复键（与后端索引列表绑定互为两端）', async () => {
    requestMock.mockClear()
    // 与 repository.vue 的 buildParams 同构：两个维度，第一个维度两个值
    await subApi.pageRepository({
      keyword: '投光灯',
      'categoryFilter.dims[0].dimId': 5,
      'categoryFilter.dims[0].valueIds': [12, 13],
      'categoryFilter.dims[1].dimId': 6,
      'categoryFilter.dims[1].valueIds': [21]
    })
    const [cfg] = requestMock.mock.calls[0]
    // 用真实 axios 序列化「本模块实际发出的配置」，而不是另写一份期望值：
    // 序列化器一旦被去掉，这里会退回 valueIds[]=12 而立刻失败
    const ax = (await vi.importActual('axios')).default
    const uri = ax.getUri({ url: cfg.url, params: cfg.params, paramsSerializer: cfg.paramsSerializer })
    const pairs = [...new URL(uri, 'http://localhost').searchParams.entries()]
      .filter(([k]) => k.startsWith('categoryFilter'))

    expect(pairs).toContainEqual(['categoryFilter.dims[0].dimId', '5'])
    expect(pairs).toContainEqual(['categoryFilter.dims[0].valueIds', '12'])
    expect(pairs).toContainEqual(['categoryFilter.dims[0].valueIds', '13'])
    expect(pairs).toContainEqual(['categoryFilter.dims[1].dimId', '6'])
    expect(pairs).toContainEqual(['categoryFilter.dims[1].valueIds', '21'])
    // 同维度的两个值落在同一个下标里；跨维度靠下标区分
    expect(pairs.map(([k, v]) => `${k}=${v}`)).toEqual([
      'categoryFilter.dims[0].dimId=5',
      'categoryFilter.dims[0].valueIds=12',
      'categoryFilter.dims[0].valueIds=13',
      'categoryFilter.dims[1].dimId=6',
      'categoryFilter.dims[1].valueIds=21'
    ])
  })
})

describe('能力B · 路由与权限（AC-B6-4）', () => {
  let userStore

  beforeEach(() => {
    setActivePinia(createPinia())
    userStore = useUserStore()
  })

  it('订阅路由组注册在 /subscription，页面名与菜单种子一致', async () => {
    const router = (await import('@/router')).default
    expect(router.resolve('/subscription/category').name).toBe('SubscriptionCategory')
    expect(router.resolve('/subscription/list').name).toBe('SubscriptionList')
    expect(router.resolve('/subscription/report').name).toBe('SubscriptionReport')
    expect(router.resolve('/subscription/category').meta.permission).toBe('subscription')
  })

  it('无 subscription 权限的用户直接输入 URL 被守卫拦下', async () => {
    const router = (await import('@/router')).default
    userStore.roles = ['USER']
    userStore.permissions = []
    expect(canAccess(router.resolve('/subscription/category'), userStore)).toBe(false)
    expect(canAccess(router.resolve('/subscription/list'), userStore)).toBe(false)
    expect(canAccess(router.resolve('/subscription/report'), userStore)).toBe(false)
    // 父子链路整体判定，任一级被拒即拒
    expect(canAccess(to({ permission: 'subscription' }, { title: '订阅与报告' }), userStore)).toBe(false)
  })

  it('持有 subscription 权限的用户三个页面都可进入', async () => {
    const router = (await import('@/router')).default
    userStore.roles = ['USER']
    userStore.permissions = ['subscription']
    expect(canAccess(router.resolve('/subscription/category'), userStore)).toBe(true)
    expect(canAccess(router.resolve('/subscription/list'), userStore)).toBe(true)
    expect(canAccess(router.resolve('/subscription/report'), userStore)).toBe(true)
  })

  it('报告里的链接回到汇总处并带上 ledgerId 以便定位该条（AC-B5-4/B6-6）', async () => {
    const router = (await import('@/router')).default
    expect(router.resolve('/ledger/repository').name).toBe('LedgerRepository')
    const src = readSource('src/views/subscription/report.vue')
    expect(src).toContain("path: '/ledger/repository'")
    expect(src).toContain('ledgerId: row.ledgerId')
  })
})

describe('能力B · 页面操作指引', () => {
  it('分类管理/我的订阅/我的报告三个页面均有操作指引', () => {
    expect(operationGuides.subscriptionCategory?.steps?.length).toBeGreaterThan(0)
    expect(operationGuides.subscriptionSubscription?.steps?.length).toBeGreaterThan(0)
    expect(operationGuides.subscriptionReport?.steps?.length).toBeGreaterThan(0)
  })

  it('汇总处的指引提及按分类筛选与「订阅本条」入口', () => {
    const text = JSON.stringify(operationGuides.ledgerRepository)
    expect(text).toContain('分类')
    expect(text).toContain('订阅本条')
  })
})
