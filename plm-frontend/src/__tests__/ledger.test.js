/**
 * PAND-200 能力线 A（证据台账统一上传 + 双向同步）前端契约测试。
 *
 * 覆盖三件事：
 *  1. 台账 API 模块与后端路由的一致（URL/方法契约，AC-A1~A6 的接口面）；
 *  2. /ledger 路由组注册与权限码接入守卫（AC-A6-1/A6-2 的界面侧拦截面）；
 *  3. 三个页面的操作指引存在（操作说明书在界面内的可发现性）。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

// jsdom 不实现 Blob URL，下载走 <a download> 时需自行提供
window.URL.createObjectURL = vi.fn(() => 'blob:mock')
window.URL.revokeObjectURL = vi.fn()
// jsdom 对 <a>.click() 会走导航并打印 "Not implemented" 噪声，此处只验行为不验跳转
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
import * as ledgerApi from '@/api/ledger'

function to(...metas) {
  return { matched: metas.map((meta) => ({ meta })) }
}

describe('能力A · 台账 API 契约', () => {
  beforeEach(() => {
    requestMock.mockClear()
    rawGet.mockClear()
  })

  it('录入/查询/关联/通知接口齐备', () => {
    const names = ['pageLedger', 'getLedger', 'ledgerByObject', 'ledgerOptions', 'searchLedgerObjects',
      'searchLedgerOwners', 'ledgerTrail', 'createLedger', 'updateLedger', 'deleteLedger',
      'batchUploadLedger', 'uploadLedgerFile', 'appendLedgerAttachments', 'removeLedgerAttachment',
      'downloadLedgerAttachment', 'fetchLedgerPreview', 'addLedgerLink', 'removeLedgerLink',
      'batchLinkLedger', 'syncLedger', 'pageLedgerNotification', 'ledgerUnreadCount',
      'getLedgerNotification', 'readLedgerNotification', 'readAllLedgerNotification',
      'ledgerNotifySettings', 'saveLedgerNotifySetting']
    names.forEach(n => expect(typeof ledgerApi[n]).toBe('function'))
  })

  it('AC-A5-6 批量上传：N 个文件以 files[] + meta(JSON) 组合成一次 multipart 提交', async () => {
    const files = [new File(['a'], 'a.pdf'), new File(['b'], 'b.pdf'), new File(['c'], 'c.pdf')]
    await ledgerApi.batchUploadLedger({ name: '检验报告', type: 'TEST_REPORT' }, files)
    const [cfg] = requestMock.mock.calls[0]
    expect(cfg.url).toBe('/ledger/batch-upload')
    expect(cfg.method).toBe('post')
    expect(cfg.data).toBeInstanceOf(FormData)
    expect(cfg.data.getAll('files')).toHaveLength(3)
    expect(cfg.data.get('meta')).toBeInstanceOf(Blob)
  })

  it('AC-A5-7 批量关联：ledgerIds 数组 + 单一对象提交', async () => {
    await ledgerApi.batchLinkLedger([1, 2, 3], 'PROJECT', 'PRJ-001')
    const [cfg] = requestMock.mock.calls[0]
    expect(cfg.url).toBe('/ledger/batch-link')
    expect(cfg.data).toEqual({ ledgerIds: [1, 2, 3], objectType: 'PROJECT', objectId: 'PRJ-001' })
  })

  it('AC-A3-2 双向同步：模块侧回写走 /ledger/sync', async () => {
    await ledgerApi.syncLedger({ objectType: 'MATERIAL', objectId: 'M-1', changes: { remark: 'x' } })
    const [cfg] = requestMock.mock.calls[0]
    expect(cfg.url).toBe('/ledger/sync')
    expect(cfg.method).toBe('post')
  })

  it('AC-A6-7 变更轨迹：按证据编号定位，编号做 URL 编码', async () => {
    await ledgerApi.ledgerTrail('EV-20261014-0001')
    expect(requestMock.mock.calls[0][0].url).toBe('/ledger/trail/EV-20261014-0001')
  })

  it('附件下载走原始字节流实例（非 Result 包装）', async () => {
    await ledgerApi.downloadLedgerAttachment(9, '图纸.pdf')
    expect(rawGet).toHaveBeenCalledWith('/ledger/attachments/9/download', { responseType: 'blob' })
  })

  it('AC-A4-11 通知开关：按类型 PUT 单条设置', async () => {
    await ledgerApi.saveLedgerNotifySetting('LEDGER_CHANGED', false)
    const [cfg] = requestMock.mock.calls[0]
    expect(cfg.url).toBe('/ledger/notification/settings')
    expect(cfg.data).toEqual({ notifyType: 'LEDGER_CHANGED', enabled: false })
  })
})

describe('能力A · 路由与权限（AC-A6-1/A6-2）', () => {
  let userStore

  beforeEach(() => {
    setActivePinia(createPinia())
    userStore = useUserStore()
  })

  it('台账路由组注册在 /ledger，三个页面与菜单种子一致', async () => {
    const router = (await import('@/router')).default
    expect(router.resolve('/ledger/list').name).toBe('LedgerList')
    expect(router.resolve('/ledger/repository').name).toBe('LedgerRepository')
    expect(router.resolve('/ledger/notification').name).toBe('LedgerNotification')
    expect(router.resolve('/ledger/list').meta.permission).toBe('ledger')
  })

  it('无 ledger 权限的用户直接输入 URL 被守卫拦下（AC-A6-2）', async () => {
    const router = (await import('@/router')).default
    userStore.roles = ['USER']
    userStore.permissions = []
    expect(canAccess(router.resolve('/ledger/list'), userStore)).toBe(false)
    // 父子链路整体判定，任一级被拒即拒
    expect(canAccess(to({ permission: 'ledger' }, { title: '证据台账' }), userStore)).toBe(false)
  })

  it('拥有 ledger 权限的用户可进入台账', async () => {
    const router = (await import('@/router')).default
    userStore.roles = ['USER']
    userStore.permissions = ['ledger']
    expect(canAccess(router.resolve('/ledger/list'), userStore)).toBe(true)
    expect(canAccess(router.resolve('/ledger/repository'), userStore)).toBe(true)
  })

  it('三个子页共用「证据台账」分组权限码，未授权者全部拦下', async () => {
    const router = (await import('@/router')).default
    userStore.roles = ['USER']
    userStore.permissions = []
    expect(canAccess(router.resolve('/ledger/repository'), userStore)).toBe(false)
    expect(canAccess(router.resolve('/ledger/notification'), userStore)).toBe(false)

    // 通知的接收方（工程/品质/销售）在菜单种子中均持有 ledger 分组权限
    userStore.permissions = ['ledger']
    expect(canAccess(router.resolve('/ledger/notification'), userStore)).toBe(true)
  })
})

describe('能力A · 页面操作指引', () => {
  it('台账/资料汇总处/通知三个页面均有操作指引', () => {
    expect(operationGuides.ledger?.steps?.length).toBeGreaterThan(0)
    expect(operationGuides.ledgerRepository?.steps?.length).toBeGreaterThan(0)
    expect(operationGuides.ledgerNotify?.steps?.length).toBeGreaterThan(0)
  })
})
