import { createRouter, createWebHistory } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

NProgress.configure({ showSpinner: false })

const Layout = () => import('@/layout/index.vue')

/**
 * 路由按"工序前后"顺序分组(严格遵循产品研发/制造流程的上游→下游):
 *   工作台 → 项目与物料(立项/建档) → 产品结构(BOM) → 3D与档案
 *     → 工程变更(ECN) → 模具与工序(模具开发/工序流转) → 品质检验
 *     → 外协协同 → 数据治理 → 指标分析 → 导出集成 → 智能改善 → 系统管理
 *
 * 每个路由通过 meta.workflowStage 指定所属工序阶段,Sidebar 按 stage 分组渲染。
 */
const routes = [
  { path: '/login', component: () => import('@/views/login/index.vue'), hidden: true },
  { path: '/share/:token', name: 'SharePublic', component: () => import('@/views/share/public.vue'), hidden: true, meta: { title: '产品3D预览' } },
  // M04 模块化改造：配置页从 /admin 迁到 /m04，旧链接保留跳转
  { path: '/admin/coderule', redirect: '/m04/coderule', hidden: true },
  { path: '/admin/approval', redirect: '/m04/flow', hidden: true },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/dashboard.vue'),
        meta: { title: '工作台', icon: 'HomeFilled', workflowStage: '工作台' } },
      { path: 'lifecycle', name: 'Lifecycle', component: () => import('@/views/lifecycle/index.vue'),
        meta: { title: '全流程总览', workflowStage: '工作台' } },
      { path: 'poms', name: 'Poms', component: () => import('@/views/poms/index.vue'), meta: { title: '工作台(新版)', icon: 'Monitor' } },
      { path: 'help', name: 'HelpCenter', component: () => import('@/views/help/index.vue'),
        meta: { title: '帮助与学习', workflowStage: '工作台' } },
      { path: 'data-entry', name: 'DataEntry', component: () => import('@/views/data-entry/index.vue'),
        meta: { title: '统一录入', icon: 'EditPen', workflowStage: '工作台' } },
      { path: 'knowledge', name: 'Knowledge', component: () => import('@/views/knowledge/index.vue'),
        meta: { title: '知识库', icon: 'Reading', workflowStage: '工作台' } }
    ]
  },
  {
    // ===== 工序阶段 1: 研发自治中心 (M04: 研发项目/进度总表/异常/推广/复盘/统计) =====
    path: '/project',
    component: Layout,
    redirect: '/project/list',
    meta: { title: '研发项目', icon: 'DataBoard', workflowStage: '研发项目' },
    children: [
      { path: 'list', name: 'ProjectList', component: () => import('@/views/project/index.vue'),
        meta: { title: '研发项目' } },
      { path: 'progress', name: 'ProjectProgress', component: () => import('@/views/project/progress.vue'),
        meta: { title: '项目进度明细' } },
      { path: 'tracking', name: 'ProjectTracking', component: () => import('@/views/project/tracking.vue'),
        meta: { title: '项目跟踪表' } },
      { path: 'gantt', name: 'ProjectGantt', component: () => import('@/views/collab/gantt.vue'),
        meta: { title: '项目时间表' } },
      { path: 'gantt-demo', name: 'ProjectGanttDemo', component: () => import('@/views/collab/gantt-demo.vue'),
        meta: { title: '时间表示例', hidden: true } },
      { path: 'worksheets', name: 'ProjectWorksheets', component: () => import('@/views/project/worksheets.vue'),
        meta: { title: '项目工作表' } },
      { path: 'changes', name: 'ProjectChanges', component: () => import('@/views/project/changes.vue'),
        meta: { title: '变更记录' } },
      { path: 'supply', name: 'ProjectSupply', component: () => import('@/views/project/supply.vue'),
        meta: { title: '供应品质问题' } },
      { path: 'sales', name: 'ProjectSales', component: () => import('@/views/project/sales.vue'),
        meta: { title: '销售推广' } },
      { path: 'review', name: 'ProjectReview', component: () => import('@/views/project/review.vue'),
        meta: { title: '项目总结' } },
      { path: 'initiation', name: 'ProjectInitiation', component: () => import('@/views/project/initiation.vue'),
        meta: { title: '立项申请' } },
      { path: 'analysis', name: 'ProjectAnalysis', component: () => import('@/views/project/analysis.vue'),
        meta: { title: '统计' } },
      { path: 'evidence', name: 'ProjectEvidence', component: () => import('@/views/project/evidence.vue'),
        meta: { title: '项目资料' } },
      { path: 'tasks', name: 'CollabTasks', component: () => import('@/views/collab/tasks.vue'),
        meta: { title: '协同任务', hidden: true } },
      { path: 'collab-home', name: 'CollabHome', component: () => import('@/views/collab/home.vue'),
        meta: { title: '项目卡片', hidden: true } },
      { path: 'workspace', name: 'ProductWorkspace', component: () => import('@/views/workspace/index.vue'),
        meta: { title: '产品空间', hidden: true } },
      { path: 'professional-home', name: 'ProfessionalHome', component: () => import('@/views/professional/index.vue'),
        meta: { title: '专业视图', hidden: true } },
      { path: 'mcore-home', name: 'McoreHome', component: () => import('@/views/mcore/index.vue'),
        meta: { title: '工作项中心', hidden: true } }
    ]
  },
  {
    path: '/material',
    component: Layout,
    redirect: '/material/list',
    meta: { title: '物料信息', icon: 'Box', permission: 'material', workflowStage: '项目与物料' },
    children: [
      { path: 'list', name: 'MaterialList', component: () => import('@/views/material/index.vue'),
        meta: { title: '物料信息' } },
      { path: 'wizard', name: 'MaterialWizard', component: () => import('@/views/material/wizard.vue'),
        meta: { title: '建档向导', activeMenu: '/material/list' } }
    ]
  },
  {
    // ===== M04 研发数据中台 · 文档中心（T05–T08：五区 + 版本链 + 检入检出） =====
    path: '/document',
    component: Layout,
    redirect: '/document/list',
    meta: { title: '文档中心', icon: 'FolderOpened', workflowStage: 'M04 研发数据中台' },
    children: [
      { path: 'list', name: 'DocumentList', component: () => import('@/views/m04/document/index.vue'),
        meta: { title: '文档中心' } }
    ]
  },
  {
    // ===== 工序阶段 2: 产品结构 =====
    path: '/bom',
    component: Layout,
    redirect: '/bom/list',
    meta: { title: '产品结构', icon: 'Connection', permission: 'bom', workflowStage: '产品结构' },
    children: [
      { path: 'list', name: 'BomList', component: () => import('@/views/bom/index.vue'),
        meta: { title: '结构展开' } },
      { path: 'tree/:bomId', name: 'BomTree', component: () => import('@/views/bom/tree.vue'),
        meta: { title: '产品结构明细', activeMenu: '/bom/list' }, props: true }
    ]
  },
  {
    // ===== 工序阶段 3: 3D与档案 =====
    path: '/model3d',
    component: Layout,
    redirect: '/model3d/list',
    meta: { title: '3D模型', icon: 'View', permission: 'model3d', workflowStage: '3D与文件' },
    children: [
      { path: 'list', name: 'Model3DList', component: () => import('@/views/model3d/index.vue'),
        meta: { title: '3D模型维护' } }
    ]
  },
  {
    path: '/archive',
    component: Layout,
    redirect: '/archive/list',
    meta: { title: '文件档案', icon: 'FolderOpened', permission: 'archive', workflowStage: '3D与文件' },
    children: [
      { path: 'list', name: 'ArchiveList', component: () => import('@/views/archive/index.vue'),
        meta: { title: '档案目录' } }
    ]
  },
  {
    // ===== 工序阶段: 资料清单 (能力线 A: 统一上传 + 双向同步) =====
    // 清单页与资料汇总处是同一行 plm_ledger 数据的两个视图, 不落副本(AC-A3-3)
    path: '/ledger',
    component: Layout,
    redirect: '/ledger/list',
    meta: { title: '项目资料', icon: 'Document', permission: 'ledger', workflowStage: '项目资料' },
    children: [
      { path: 'list', name: 'LedgerList', component: () => import('@/views/ledger/index.vue'),
        meta: { title: '资料录入' } },
      { path: 'repository', name: 'LedgerRepository', component: () => import('@/views/ledger/repository.vue'),
        meta: { title: '资料汇总' } },
      { path: 'notification', name: 'LedgerNotification', component: () => import('@/views/ledger/notification.vue'),
        meta: { title: '变更通知' } }
    ]
  },
  {
    // ===== 工序阶段: 订阅与报告 (能力线 B: 资料分类 / 订阅 / 报告推送) =====
    // 分类维度与订阅规则见 46_subscription.sql; 报告里的链接跳回上面的资料汇总处
    path: '/subscription',
    component: Layout,
    redirect: '/subscription/category',
    meta: { title: '订阅提醒', icon: 'Bell', permission: 'subscription', workflowStage: '订阅提醒' },
    children: [
      { path: 'category', name: 'SubscriptionCategory',
        component: () => import('@/views/subscription/category.vue'),
        meta: { title: '资料分类' } },
      { path: 'list', name: 'SubscriptionList',
        component: () => import('@/views/subscription/subscription.vue'),
        meta: { title: '我的订阅' } },
      { path: 'report', name: 'SubscriptionReport',
        component: () => import('@/views/subscription/report.vue'),
        meta: { title: '我的报告' } }
    ]
  },
  {
    // ===== 工序阶段 4: 工程变更 =====
    path: '/ecn',
    component: Layout,
    redirect: '/ecn/list',
    meta: { title: '设计变更', icon: 'Switch', permission: 'ecn', workflowStage: '设计变更' },
    children: [
      { path: 'list', name: 'EcnList', component: () => import('@/views/ecn/index.vue'),
        meta: { title: '设计变更' } }
    ]
  },
  {
    // ===== 工序阶段 4: 工程变更（M04 T09–T11：ECR 申请 + 审批待办） =====
    path: '/ecr',
    component: Layout,
    redirect: '/ecr/list',
    meta: { title: 'ECR变更申请', icon: 'DocumentAdd', permission: 'ecr:list', workflowStage: 'M04 研发数据中台' },
    children: [
      { path: 'list', name: 'EcrList', component: () => import('@/views/m04/ecr/index.vue'),
        meta: { title: 'ECR 申请' } }
    ]
  },
  {
    path: '/approval',
    component: Layout,
    redirect: '/approval/pending',
    meta: { title: '我的待办', icon: 'Bell', workflowStage: '个人' },
    children: [
      { path: 'pending', name: 'ApprovalPending', component: () => import('@/views/m04/approval/pending.vue'),
        meta: { title: '审批待办' } }
    ]
  },
  {
    // ===== M04 模块配置（仅管理员）：编码规则引擎 + 审批流定义 =====
    path: '/m04',
    component: Layout,
    redirect: '/m04/coderule',
    meta: { title: 'M04 模块配置', icon: 'Coin', roles: ['ADMIN'], workflowStage: 'M04 研发数据中台' },
    children: [
      { path: 'coderule', name: 'M04CodeRule', component: () => import('@/views/m04/codegen/index.vue'),
        meta: { title: '编码规则' } },
      { path: 'flow', name: 'M04ApprovalFlow', component: () => import('@/views/m04/approval/flow.vue'),
        meta: { title: '审批流配置' } }
    ]
  },
  {
    // ===== 工序阶段 5: 模具与工序 (模具开发 → 工序流转) =====
    path: '/mold',
    component: Layout,
    redirect: '/mold/list',
    meta: { title: '模具管理', icon: 'Grid', permission: 'mold', workflowStage: '模具与工序' },
    children: [
      { path: 'list', name: 'MoldList', component: () => import('@/views/mold/index.vue'),
        meta: { title: '模具清单' } }
    ]
  },
  {
    path: '/process',
    component: Layout,
    redirect: '/process/list',
    meta: { title: '生产工序', icon: 'SetUp', workflowStage: '模具与工序' },
    children: [
      { path: 'list', name: 'ProcessList', component: () => import('@/views/process/index.vue'),
        meta: { title: '工序流转' } }
    ]
  },
  {
    // ===== 工序阶段 6: 品质检验 =====
    path: '/quality',
    component: Layout,
    redirect: '/quality/list',
    meta: { title: '质量检验', icon: 'CircleCheckFilled', permission: 'quality', workflowStage: '质量检验' },
    children: [
      { path: 'list', name: 'QualityList', component: () => import('@/views/quality/index.vue'),
        meta: { title: '来料/制程检验' } }
    ]
  },
  {
    // ===== 工序阶段 7: 外协协同 =====
    path: '/outsourcing',
    component: Layout,
    redirect: '/outsourcing/list',
    meta: { title: '外协管理', icon: 'Promotion', permission: 'outsourcing', workflowStage: '外协管理' },
    children: [
      { path: 'list', name: 'OutsourceList', component: () => import('@/views/outsourcing/index.vue'),
        meta: { title: '图纸外发' } }
    ]
  },
  {
    // ===== 工序阶段 8: 数据治理 =====
    path: '/dq',
    component: Layout,
    redirect: '/dq/debts',
    meta: { title: '数据检查', icon: 'DataAnalysis', permission: 'dq', workflowStage: '数据检查' },
    children: [
      { path: 'debts', name: 'DqDebts', component: () => import('@/views/dq/index.vue'),
        meta: { title: '问题检查与修复' } },
      { path: 'check', name: 'SysCheck', component: () => import('@/views/system/check.vue'),
        meta: { title: '系统自检' } }
    ]
  },
  {
    // ===== 工序阶段 9: 指标分析 =====
    path: '/metric',
    component: Layout,
    redirect: '/metric/list',
    meta: { title: '数据分析', icon: 'TrendCharts', workflowStage: '数据分析' },
    children: [
      { path: 'list', name: 'MetricList', component: () => import('@/views/metric/index.vue'),
        meta: { title: '指标看板' } }
    ]
  },
  {
    path: '/okr',
    component: Layout,
    redirect: '/okr/list',
    meta: { title: '目标管理', icon: 'Trophy', workflowStage: '数据分析' },
    children: [
      { path: 'list', name: 'OkrList', component: () => import('@/views/okr/index.vue'),
        meta: { title: '目标与关键结果' } }
    ]
  },
  {
    path: '/analytics',
    component: Layout,
    redirect: '/analytics/list',
    meta: { title: '数据看板', icon: 'DataAnalysis', workflowStage: '数据分析' },
    children: [
      { path: 'list', name: 'AnalyticsList', component: () => import('@/views/analytics/index.vue'),
        meta: { title: '数据图表' } }
    ]
  },
  {
    // ===== 工序阶段 10: 导出集成 =====
    path: '/exportx',
    component: Layout,
    redirect: '/exportx/list',
    meta: { title: '导出数据', icon: 'Download', workflowStage: '导入导出' },
    children: [
      { path: 'list', name: 'ExportList', component: () => import('@/views/exportx/index.vue'),
        meta: { title: '导出' } }
    ]
  },
  {
    path: '/integration',
    component: Layout,
    redirect: '/integration/status',
    meta: { title: '系统连接', icon: 'Connection', permission: 'integration', workflowStage: '导入导出', roles: ['ADMIN'] },
    children: [
      { path: 'status', name: 'SysIntegration', component: () => import('@/views/system/integration.vue'),
        meta: { title: '连接设置' } }
    ]
  },
  {
    // ===== 工序阶段 11: 智能改善 =====
    path: '/agent',
    component: Layout,
    redirect: '/agent/list',
    meta: { title: 'AI助手', icon: 'MagicStick', workflowStage: '智能助手' },
    children: [
      { path: 'list', name: 'AgentCenter', component: () => import('@/views/agent/index.vue'),
        meta: { title: 'AI对话' } }
    ]
  },
  {
    path: '/improve',
    component: Layout,
    redirect: '/improve/list',
    meta: { title: '问题改进', icon: 'Aim', workflowStage: '智能助手' },
    children: [
      { path: 'list', name: 'ImproveList', component: () => import('@/views/improve/index.vue'),
        meta: { title: '问题与建议' } },
      { path: 'skills', name: 'SkillLibrary', component: () => import('@/views/skills/index.vue'),
        meta: { title: '技能', workflowStage: '智能助手' } }
    ]
  },
  {
    // ===== 工序阶段 12: 系统管理(管理员) =====
    path: '/admin',
    component: Layout,
    redirect: '/admin/user',
    meta: { title: '系统设置', icon: 'Setting', roles: ['ADMIN'], workflowStage: '系统设置' },
    children: [
      { path: 'user', name: 'SysUser', component: () => import('@/views/system/user.vue'),
        meta: { title: '用户管理' } },
      { path: 'role', name: 'SysRole', component: () => import('@/views/system/role.vue'),
        meta: { title: '角色与权限' } },
      { path: 'permission', name: 'SysPermission', component: () => import('@/views/system/permission.vue'),
        meta: { title: '菜单权限' } },
      { path: 'dict', name: 'SysDict', component: () => import('@/views/system/dict.vue'),
        meta: { title: '选项字典' } },
      { path: 'base', name: 'SysBaseData', component: () => import('@/views/system/baseData.vue'),
        meta: { title: '基础资料' } },
      { path: 'log', name: 'SysLog', component: () => import('@/views/system/log.vue'),
        meta: { title: '操作记录' } },
      { path: 'share', name: 'ShareList', component: () => import('@/views/share/index.vue'),
        meta: { title: '分享' } },
      { path: 'feedback', name: 'SysFeedback', component: () => import('@/views/feedback/manage.vue'),
        meta: { title: '问题反馈' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const WHITE_LIST = ['/login']

/**
 * 路由级越权拦截（AC7.1）：侧边栏按 meta.roles/permission 隐藏菜单，
 * 但直接输入 URL 仍会渲染路由壳，故在守卫里沿 matched 链复核一次。
 */
export function canAccess(to, userStore) {
  return to.matched.every(record => {
    const meta = record.meta || {}
    if (meta.roles?.length && !meta.roles.some(role => userStore.roles.includes(role))) {
      return false
    }
    // hasPermission 对 ADMIN 直接放行，与侧边栏口径一致
    if (meta.permission && !userStore.hasPermission(meta.permission)) {
      return false
    }
    return true
  })
}

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
      return
    }
    // 硬刷新后 store 里只有 token，必须先补齐 userId/roles/permissions 再判权限
    if (!userStore.userId) {
      try {
        await userStore.fetchInfo()
      } catch {
        userStore.reset()
        next('/login')
        return
      }
    }
    if (!canAccess(to, userStore)) {
      ElMessage.warning('无权访问该页面')
      next('/dashboard')
      return
    }
    next()
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
