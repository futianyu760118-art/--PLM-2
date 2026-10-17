// AEOS PLM 五种呈现模式定义（依据 AEOS_PLM_Frontend_Execution_V1_20261017 资料包）
// 原则：仅重组导航分组与外观，不复制业务页面/状态/权限；规划能力显式标注“待接入”。
// classic 模式沿用 layout 现有 workflowStage 分组（保持老用户路径，可随时回退）。

export const MODES = {
  classic: {
    key: 'classic',
    label: '01 原版业务',
    short: '原版业务',
    desc: '经典表格 · 功能导航',
    home: '/dashboard',
    sidebar: 'dark',
    groups: null // null = 使用现有 workflowStage 分组
  },
  product: {
    key: 'product',
    label: '02 产品工作空间',
    short: '产品空间',
    desc: '产品为中心 · 跨专业资料',
    home: '/project/workspace',
    sidebar: 'dark',
    groups: [
      { stage: '产品上下文', icon: 'Box', items: [
        { path: '/project/workspace', title: '产品概览', icon: 'DataBoard' },
        { path: '/material/list', title: '物料主数据', icon: 'Box' },
        { path: '/material/wizard', title: '建档向导', icon: 'MagicStick' },
        { path: '/model3d/list', title: '3D模型', icon: 'View' }
      ] },
      { stage: '研发交付', icon: 'Connection', items: [
        { path: '/bom/list', title: 'BOM结构', icon: 'Connection' },
        { path: '/project/worksheets', title: '项目工作表', icon: 'Document' },
        { path: '/ecn/list', title: '工程变更', icon: 'Switch' },
        { path: '/archive/list', title: '档案', icon: 'FolderOpened' }
      ] },
      { stage: '制造与品质', icon: 'SetUp', items: [
        { path: '/quality/list', title: '品质标准', icon: 'CircleCheckFilled' },
        { path: '/process/list', title: '工序流转', icon: 'SetUp' },
        { path: '/mold/list', title: '模具资产', icon: 'Grid' }
      ] },
      { stage: '生命周期', icon: 'Timer', items: [
        { path: '/lifecycle', title: '全生命周期(12环节)', icon: 'Timer' },
        { path: '/help', title: '使用说明与学习', icon: 'QuestionFilled' }
      ] }
    ]
  },
  professional: {
    key: 'professional',
    label: '03 专业视图集',
    short: '专业视图',
    desc: '岗位行动 · 工程评审 · 工具画布',
    home: '/project/professional-home',
    sidebar: 'dark',
    groups: [
      { stage: '岗位行动', icon: 'Aim', items: [
        { path: '/project/professional-home', title: '专业视图首页', icon: 'Aim' },
        { path: '/project/tasks', title: '我的待办', icon: 'Tickets' },
        { path: '/project/tracking', title: '研发项目跟踪', icon: 'DataBoard' }
      ] },
      { stage: '工程评审', icon: 'Checked', items: [
        { path: '/project/review', title: '项目复盘', icon: 'Reading' },
        { path: '/ecn/list', title: '工程变更', icon: 'Switch' },
        { path: '/project/changes', title: '变更列表', icon: 'DocumentCopy' }
      ] },
      { stage: '工具画廊', icon: 'MagicStick', items: [
        { path: '/agent/list', title: '智能体中心', icon: 'MagicStick' },
        { path: '/improve/skills', title: '技能库', icon: 'Collection' },
        { path: '/dq/debts', title: '数据自检', icon: 'DataLine' },
        { path: '/metric/list', title: '度量KPI', icon: 'TrendCharts' },
        { path: '/analytics/list', title: '分析驾驶舱', icon: 'DataAnalysis' }
      ] }
    ]
  },
  collab: {
    key: 'collab',
    label: '04 项目协同',
    short: '项目协同',
    desc: '项目卡片 · 列表 · 任务看板',
    home: '/project/collab-home',
    sidebar: 'light',
    groups: [
      { stage: '我的协同', icon: 'Tickets', items: [
        { path: '/project/collab-home', title: '项目卡片墙', icon: 'Postcard' },
        { path: '/project/tasks', title: '协同任务(待办)', icon: 'Tickets' }
      ] },
      { stage: '项目', icon: 'Files', items: [
        { path: '/project/list', title: '研发项目', icon: 'Files' },
        { path: '/project/tracking', title: '项目跟踪', icon: 'DataBoard' },
        { path: '/project/worksheets', title: '项目工作表', icon: 'Document' },
        { path: '/project/initiation', title: '立项申请', icon: 'EditPen' }
      ] }
    ]
  },
  multica: {
    key: 'multica',
    label: '05 Multica 风格',
    short: 'Multica',
    desc: '工作项中心 · 人与工具协同',
    home: '/project/mcore-home',
    sidebar: 'gray',
    groups: [
      { stage: '个人', icon: 'User', items: [
        { path: '/project/mcore-home', title: '收件箱/工作项', icon: 'Bell' },
        { path: '/project/tasks', title: '我的待办', icon: 'Tickets' }
      ] },
      { stage: '工作区', icon: 'Files', items: [
        { path: '/project/collab-home', title: '项目', icon: 'Files' },
        { path: '/project/tracking', title: '跟踪', icon: 'DataBoard' },
        { path: '/metric/list', title: '度量', icon: 'TrendCharts' }
      ] },
      { stage: '配置', icon: 'Setting', items: [
        { path: '/agent/list', title: '智能体', icon: 'MagicStick' },
        { path: '/improve/skills', title: '技能库', icon: 'Collection' },
        { path: '/help', title: '帮助与学习', icon: 'QuestionFilled' }
      ] }
    ]
  }
}

export const MODE_KEYS = Object.keys(MODES)

export const MODE_LIST = MODE_KEYS.map((k) => ({ key: k, ...MODES[k] }))

export function isModeKey(k) {
  return Object.prototype.hasOwnProperty.call(MODES, k)
}
