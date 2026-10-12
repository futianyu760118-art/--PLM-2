/**
 * PAND-151 / D5：路由级越权拦截（AC7.1）。
 *
 * 侧边栏只按 meta.roles/permission 隐藏菜单，直接输入 URL 仍会渲染路由壳，
 * 故守卫沿 matched 链复核一次。此处直接验证守卫的判定函数 canAccess。
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { canAccess } from '@/router'
import { useUserStore } from '@/stores/user'

/** 构造一个已解析的路由位置：每个元素对应 matched 链上一条记录 */
function to(...metas) {
  return { matched: metas.map((meta) => ({ meta })) }
}

describe('D5 路由守卫 canAccess', () => {
  let userStore

  beforeEach(() => {
    setActivePinia(createPinia())
    userStore = useUserStore()
  })

  it('无 meta 约束的路由一律放行', () => {
    expect(canAccess(to({ title: '工作台' }), userStore)).toBe(true)
    expect(canAccess({ matched: [] }, userStore)).toBe(true)
  })

  it('角色不匹配：/admin 等仅 ADMIN 的路由被拒', () => {
    userStore.roles = ['USER']
    expect(canAccess(to({ roles: ['ADMIN'] }), userStore)).toBe(false)
  })

  it('角色匹配则放行', () => {
    userStore.roles = ['ADMIN']
    expect(canAccess(to({ roles: ['ADMIN'] }), userStore)).toBe(true)
  })

  it('roles 命中其一即放行（多角色清单）', () => {
    userStore.roles = ['QA']
    expect(canAccess(to({ roles: ['ADMIN', 'QA'] }), userStore)).toBe(true)
  })

  it('权限点缺失：有权限放行，无权限拒绝', () => {
    userStore.roles = ['USER']
    userStore.permissions = ['material']
    expect(canAccess(to({ permission: 'material' }), userStore)).toBe(true)
    expect(canAccess(to({ permission: 'bom' }), userStore)).toBe(false)
  })

  it('ADMIN 凭角色直通权限点校验（与侧边栏 hasPermission 口径一致）', () => {
    userStore.roles = ['ADMIN']
    userStore.permissions = []
    expect(canAccess(to({ permission: 'bom' }), userStore)).toBe(true)
  })

  it('matched 链上任一级被拒，整次导航即被拒', () => {
    userStore.roles = ['USER']
    userStore.permissions = ['ecn']
    expect(canAccess(to({ permission: 'ecn' }, { roles: ['ADMIN'] }), userStore)).toBe(false)
  })

  it('真实路由表：管理类路由的 URL 直接访问会被守卫拦住', async () => {
    const router = (await import('@/router')).default
    // resolve 后的 meta 即守卫看到的 to.meta（父级 meta 已合并）
    expect(router.resolve('/admin/user').meta.roles).toEqual(['ADMIN'])
    expect(router.resolve('/integration/status').meta.roles).toEqual(['ADMIN'])
    expect(router.resolve('/bom/list').meta.permission).toBe('bom')

    userStore.roles = ['USER']
    userStore.permissions = []
    expect(canAccess(router.resolve('/admin/user'), userStore)).toBe(false)
    expect(canAccess(router.resolve('/bom/list'), userStore)).toBe(false)
  })
})
