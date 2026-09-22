import { createRouter, createWebHistory } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { useUserStore } from '@/stores/user'

NProgress.configure({ showSpinner: false })

const Layout = () => import('@/layout/index.vue')

/**
 * 路由按"工序前后"顺序分组(严格遵循产品研发/制造流程的上游→下游):
 *   工作台 → 项目与物料(立项/建档) → 产品结构(BOM) → 3D与档案
 *     → 工程变更(ECN) → 模具与工序(模具开发/工序流转) → 品质检验
 *     → 外协协同 → 数据治理 → 度量分析 → 导出集成 → 智能改善 → 系统管理
 *
 * 每个路由通过 meta.workflowStage 指定所属工序阶段,Sidebar 按 stage 分组渲染。
 */
const routes = [
  { path: '/login', component: () => import('@/views/login/index.vue'), hidden: true },
  { path: '/share/:token', name: 'SharePublic', component: () => import('@/views/share/public.vue'), hidden: true, meta: { title: '产品3D预览' } },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/dashboard.vue'),
        meta: { title: '工作台', icon: 'HomeFilled', workflowStage: '工作台' } }
    ]
  },
  {
    // ===== 工序阶段 1: 项目与物料 (上游立项/建档) =====
    path: '/project',
    component: Layout,
    redirect: '/project/list',
    meta: { title: '项目NPI', icon: 'Flag', workflowStage: '项目与物料' },
    children: [
      { path: 'list', name: 'ProjectList', component: () => import('@/views/project/index.vue'),
        meta: { title: '研发项目' } },
      { path: 'initiation', name: 'ProjectInitiation', component: () => import('@/views/project/initiation.vue'),
        meta: { title: '立项申请' } },
      { path: 'progress', name: 'ProjectProgress', component: () => import('@/views/project/progress.vue'),
        meta: { title: '进度跟踪' } },
      { path: 'evidence', name: 'ProjectEvidence', component: () => import('@/views/project/evidence.vue'),
        meta: { title: '证据台账' } }
    ]
  },
  {
    path: '/material',
    component: Layout,
    redirect: '/material/list',
    meta: { title: '物料主数据', icon: 'Box', permission: 'material', workflowStage: '项目与物料' },
    children: [
      { path: 'list', name: 'MaterialList', component: () => import('@/views/material/index.vue'),
        meta: { title: '物料主数据' } },
      { path: 'wizard', name: 'MaterialWizard', component: () => import('@/views/material/wizard.vue'),
        meta: { title: '建档向导', activeMenu: '/material/list' } }
    ]
  },
  {
    // ===== 工序阶段 2: 产品结构 =====
    path: '/bom',
    component: Layout,
    redirect: '/bom/list',
    meta: { title: 'BOM产品结构', icon: 'Connection', permission: 'bom', workflowStage: '产品结构' },
    children: [
      { path: 'list', name: 'BomList', component: () => import('@/views/bom/index.vue'),
        meta: { title: '多级爆炸' } },
      { path: 'tree/:bomId', name: 'BomTree', component: () => import('@/views/bom/tree.vue'),
        meta: { title: 'BOM树形', activeMenu: '/bom/list' }, props: true }
    ]
  },
  {
    // ===== 工序阶段 3: 3D与档案 =====
    path: '/model3d',
    component: Layout,
    redirect: '/model3d/list',
    meta: { title: '3D模型库', icon: 'View', permission: 'model3d', workflowStage: '3D与档案' },
    children: [
      { path: 'list', name: 'Model3DList', component: () => import('@/views/model3d/index.vue'),
        meta: { title: '3D模型管理' } }
    ]
  },
  {
    path: '/archive',
    component: Layout,
    redirect: '/archive/list',
    meta: { title: '档案管理', icon: 'FolderOpened', permission: 'archive', workflowStage: '3D与档案' },
    children: [
      { path: 'list', name: 'ArchiveList', component: () => import('@/views/archive/index.vue'),
        meta: { title: '档案目录树' } }
    ]
  },
  {
    // ===== 工序阶段 4: 工程变更 =====
    path: '/ecn',
    component: Layout,
    redirect: '/ecn/list',
    meta: { title: 'ECN变更', icon: 'Switch', permission: 'ecn', workflowStage: '工程变更' },
    children: [
      { path: 'list', name: 'EcnList', component: () => import('@/views/ecn/index.vue'),
        meta: { title: '工程变更' } }
    ]
  },
  {
    // ===== 工序阶段 5: 模具与工序 (模具开发 → 工序流转) =====
    path: '/mold',
    component: Layout,
    redirect: '/mold/list',
    meta: { title: '模具资产', icon: 'Grid', permission: 'mold', workflowStage: '模具与工序' },
    children: [
      { path: 'list', name: 'MoldList', component: () => import('@/views/mold/index.vue'),
        meta: { title: '模具台账' } }
    ]
  },
  {
    path: '/process',
    component: Layout,
    redirect: '/process/list',
    meta: { title: '工序流转', icon: 'SetUp', workflowStage: '模具与工序' },
    children: [
      { path: 'list', name: 'ProcessList', component: () => import('@/views/process/index.vue'),
        meta: { title: '工序自动化' } }
    ]
  },
  {
    // ===== 工序阶段 6: 品质检验 =====
    path: '/quality',
    component: Layout,
    redirect: '/quality/list',
    meta: { title: '品质标准', icon: 'CircleCheckFilled', permission: 'quality', workflowStage: '品质检验' },
    children: [
      { path: 'list', name: 'QualityList', component: () => import('@/views/quality/index.vue'),
        meta: { title: 'IQC/IPQC检验' } }
    ]
  },
  {
    // ===== 工序阶段 7: 外协协同 =====
    path: '/outsourcing',
    component: Layout,
    redirect: '/outsourcing/list',
    meta: { title: '外协发图', icon: 'Promotion', permission: 'outsourcing', workflowStage: '外协协同' },
    children: [
      { path: 'list', name: 'OutsourceList', component: () => import('@/views/outsourcing/index.vue'),
        meta: { title: '发图审批' } }
    ]
  },
  {
    // ===== 工序阶段 8: 数据治理 =====
    path: '/dq',
    component: Layout,
    redirect: '/dq/debts',
    meta: { title: '数据自检', icon: 'DataAnalysis', permission: 'dq', workflowStage: '数据治理' },
    children: [
      { path: 'debts', name: 'DqDebts', component: () => import('@/views/dq/index.vue'),
        meta: { title: '自检与修复' } },
      { path: 'check', name: 'SysCheck', component: () => import('@/views/system/check.vue'),
        meta: { title: '系统健康自检' } }
    ]
  },
  {
    // ===== 工序阶段 9: 度量分析 =====
    path: '/metric',
    component: Layout,
    redirect: '/metric/list',
    meta: { title: '度量与KPI', icon: 'TrendCharts', workflowStage: '度量分析' },
    children: [
      { path: 'list', name: 'MetricList', component: () => import('@/views/metric/index.vue'),
        meta: { title: '度量KPI' } }
    ]
  },
  {
    path: '/okr',
    component: Layout,
    redirect: '/okr/list',
    meta: { title: 'OKR管理', icon: 'Trophy', workflowStage: '度量分析' },
    children: [
      { path: 'list', name: 'OkrList', component: () => import('@/views/okr/index.vue'),
        meta: { title: '目标与KR' } }
    ]
  },
  {
    path: '/analytics',
    component: Layout,
    redirect: '/analytics/list',
    meta: { title: '分析驾驶舱', icon: 'DataAnalysis', workflowStage: '度量分析' },
    children: [
      { path: 'list', name: 'AnalyticsList', component: () => import('@/views/analytics/index.vue'),
        meta: { title: '图表驾驶舱' } }
    ]
  },
  {
    // ===== 工序阶段 10: 导出集成 =====
    path: '/exportx',
    component: Layout,
    redirect: '/exportx/list',
    meta: { title: '导出中心', icon: 'Download', workflowStage: '导出集成' },
    children: [
      { path: 'list', name: 'ExportList', component: () => import('@/views/exportx/index.vue'),
        meta: { title: '数据导出' } }
    ]
  },
  {
    path: '/integration',
    component: Layout,
    redirect: '/integration/status',
    meta: { title: '系统对接', icon: 'Connection', permission: 'integration', workflowStage: '导出集成', roles: ['ADMIN'] },
    children: [
      { path: 'status', name: 'SysIntegration', component: () => import('@/views/system/integration.vue'),
        meta: { title: '对接状态' } }
    ]
  },
  {
    // ===== 工序阶段 11: 智能改善 =====
    path: '/agent',
    component: Layout,
    redirect: '/agent/list',
    meta: { title: '智能体中心', icon: 'MagicStick', workflowStage: '智能改善' },
    children: [
      { path: 'list', name: 'AgentCenter', component: () => import('@/views/agent/index.vue'),
        meta: { title: '智能体对话' } }
    ]
  },
  {
    path: '/improve',
    component: Layout,
    redirect: '/improve/list',
    meta: { title: '改善闭环', icon: 'Aim', workflowStage: '智能改善' },
    children: [
      { path: 'list', name: 'ImproveList', component: () => import('@/views/improve/index.vue'),
        meta: { title: '问题与洞察' } }
    ]
  },
  {
    // ===== 工序阶段 12: 系统管理(管理员) =====
    path: '/admin',
    component: Layout,
    redirect: '/admin/user',
    meta: { title: '系统管理', icon: 'Setting', roles: ['ADMIN'], workflowStage: '系统管理' },
    children: [
      { path: 'user', name: 'SysUser', component: () => import('@/views/system/user.vue'),
        meta: { title: '用户管理' } },
      { path: 'role', name: 'SysRole', component: () => import('@/views/system/role.vue'),
        meta: { title: '角色权限' } },
      { path: 'dict', name: 'SysDict', component: () => import('@/views/system/dict.vue'),
        meta: { title: '字典管理' } },
      { path: 'log', name: 'SysLog', component: () => import('@/views/system/log.vue'),
        meta: { title: '操作日志' } },
      { path: 'share', name: 'ShareList', component: () => import('@/views/share/index.vue'),
        meta: { title: '分享链接' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const WHITE_LIST = ['/login']

router.beforeEach(async (to, from, next) => {
  NProgress.start()
  const userStore = useUserStore()
  document.title = (to.meta?.title ? to.meta.title + ' - ' : '') + '恒剑光电 PLM V4.0'
  if (to.path.startsWith('/share/')) {
    next()
    NProgress.done()
    return
  }
  if (userStore.token) {
    if (to.path === '/login') {
      next('/')
    } else if (!userStore.userId) {
      try {
        await userStore.fetchInfo()
        next()
      } catch {
        userStore.reset()
        next('/login')
      }
    } else {
      next()
    }
  } else {
    if (WHITE_LIST.includes(to.path)) {
      next()
    } else {
      next(`/login?redirect=${to.path}`)
    }
  }
})

router.afterEach(() => {
  NProgress.done()
})

export default router
