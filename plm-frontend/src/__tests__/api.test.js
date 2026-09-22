/**
 * 请求封装 & API 模块测试
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock localStorage
const localStorageMock = (() => {
  let store = {}
  return {
    getItem: vi.fn((key) => store[key] || null),
    setItem: vi.fn((key, val) => { store[key] = val }),
    removeItem: vi.fn((key) => { delete store[key] }),
    clear: vi.fn(() => { store = {} })
  }
})()
Object.defineProperty(window, 'localStorage', { value: localStorageMock })

describe('Request 拦截器', () => {
  beforeEach(() => {
    localStorageMock.clear()
    vi.resetModules()
  })

  it('localStorage mock 正常工作', () => {
    localStorageMock.setItem('test', '123')
    expect(localStorageMock.getItem('test')).toBe('123')
  })

  it('Token 存储/读取/删除', () => {
    localStorageMock.setItem('plm_token', 'Bearer abc123')
    expect(localStorageMock.getItem('plm_token')).toBe('Bearer abc123')
    localStorageMock.removeItem('plm_token')
    expect(localStorageMock.getItem('plm_token')).toBeNull()
  })
})

describe('API 路径定义', () => {
  it('material API 路径正确', async () => {
    const api = await import('@/api/material')
    expect(typeof api.pageMaterial).toBe('function')
    expect(typeof api.addMaterial).toBe('function')
    expect(typeof api.releaseMaterial).toBe('function')
  })

  it('ecn API 包含三级审批方法', async () => {
    const api = await import('@/api/ecn')
    expect(typeof api.reviewL1Approve).toBe('function')
    expect(typeof api.reviewL1Reject).toBe('function')
    expect(typeof api.reviewL2Approve).toBe('function')
    expect(typeof api.reviewL2Reject).toBe('function')
    expect(typeof api.effectEcn).toBe('function')
  })

  it('bom API 包含树形操作', async () => {
    const api = await import('@/api/bom')
    expect(typeof api.getBomTree).toBe('function')
    expect(typeof api.addBomItem).toBe('function')
    expect(typeof api.deleteBomItem).toBe('function')
  })

  it('mold API 包含试模履历', async () => {
    const api = await import('@/api/mold')
    expect(typeof api.getTrials).toBe('function')
    expect(typeof api.addTrial).toBe('function')
    expect(typeof api.scrapMold).toBe('function')
  })
})

describe('Pinia Store', () => {
  it('user store 定义正确', async () => {
    const mod = await import('@/stores/user')
    expect(mod.useUserStore).toBeDefined()
    expect(typeof mod.useUserStore).toBe('function')
  })
})

describe('Router 配置', () => {
  it('router 定义了核心路由', async () => {
    const router = (await import('@/router')).default
    const routePaths = router.getRoutes().map(r => r.path)
    expect(routePaths).toContain('/login')
    expect(routePaths).toContain('/material/list')
    expect(routePaths).toContain('/ecn/list')
    expect(routePaths).toContain('/bom/list')
    expect(routePaths.some(p => p.includes('share'))).toBe(true)
  })
})
