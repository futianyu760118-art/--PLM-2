// PLM 智能体通用查询结果渲染：把任意 data 数组渲染成卡片摘要。
// 职责：字段中文标签、技术字段折叠、profile 推断、通用格式化（金额/数量/概率/状态）。

export const FIELD_LABELS = {
  order_no: '订单号',
  project_no: '项目号',
  project_name: '项目名称',
  issue_no: '异常编号',
  work_order_no: '工单号',
  title: '事项',
  name: '名称',
  customer: '客户',
  customer_code: '客户编码',
  order_qty: '订单数量',
  completed_qty: '已完成数量',
  order_amount: '订单金额',
  amount: '金额',
  status: '状态',
  current_gate: '当前阶段',
  owner: '负责人',
  owner_org: '责任部门',
  promised_date: '合同交期',
  delivery_completed_date: '完成日期',
  planned_start_date: '计划开始',
  shipping_plan: '出货计划',
  risk_level: '风险等级',
  risk_probability: '风险概率',
  node_progress: '节点进度',
  '节点进度': '节点进度',
  node_name: '节点名称',
  plan_date: '计划日期',
  defect_type: '缺陷类型',
  submit_dept: '提交部门',
  sla_due_at: '到期时间',
  updated_at: '更新时间',
  id: '记录 ID',
  line_no: '行号',
  erp_status: 'ERP 状态'
}

export const TECHNICAL_FIELDS = new Set([
  'id', 'line_no', 'erp_status', 'customer_code', 'product_code',
  'updated_at', 'created_at', 'revision', 'sync_status'
])

export const DISPLAY_PROFILES = [
  {
    entity: 'order',
    match: (row) => Boolean(row.order_no),
    title: ['order_no'],
    subtitle: ['customer'],
    fields: [
      'order_qty', 'completed_qty', 'order_amount', 'status',
      'promised_date', 'delivery_completed_date', 'risk_level', 'risk_probability'
    ]
  },
  {
    entity: 'project',
    match: (row) => Boolean(row.project_no),
    title: ['project_no'],
    subtitle: ['project_name'],
    fields: ['project_name', 'owner', 'status', 'current_gate', '节点进度']
  },
  {
    entity: 'quality',
    match: (row) => Boolean(row.issue_no),
    title: ['issue_no'],
    subtitle: ['order_no', 'product_code'],
    fields: ['defect_type', 'submit_dept', 'status', 'owner', 'occurred_at']
  },
  {
    entity: 'production',
    match: (row) => Boolean(row.work_order_no),
    title: ['work_order_no'],
    subtitle: ['order_no', 'product_code'],
    fields: ['workshop', 'status', 'planned_end_date', 'actual_qty', 'owner']
  },
  {
    entity: 'task',
    match: (row) => Boolean(row.title) && Boolean(row.sla_due_at || row.status),
    title: ['title'],
    subtitle: ['owner'],
    fields: ['status', 'sla_due_at']
  },
  {
    entity: 'generic',
    match: () => true,
    title: ['name', 'title', 'project_name', 'material_code'],
    subtitle: [],
    fields: []
  }
]

export function present(value) {
  return value !== undefined && value !== null && value !== ''
}

export function resolveDisplayProfile(row, displayMeta) {
  if (displayMeta?.fields?.length) {
    return {
      entity: displayMeta.entity || 'generic',
      title: displayMeta.title || [],
      subtitle: displayMeta.subtitle || [],
      fields: displayMeta.fields || [],
      labels: displayMeta.labels || {}
    }
  }
  const profile = DISPLAY_PROFILES.find((item) => item.match(row || {})) || DISPLAY_PROFILES[DISPLAY_PROFILES.length - 1]
  return {
    ...profile,
    labels: FIELD_LABELS
  }
}

export function splitRowFields(row, displayMeta) {
  const profile = resolveDisplayProfile(row, displayMeta)
  const labels = profile.labels || FIELD_LABELS

  const titleKey = profile.title.find((key) => present(row?.[key]))
  const subtitleKeys = profile.subtitle.filter((key) => present(row?.[key]))

  const primary = profile.fields.length
    ? profile.fields.filter((key) => present(row?.[key]))
    : Object.keys(row || {})
        .filter((key) => present(row[key]))
        .filter((key) => !TECHNICAL_FIELDS.has(key))
        .slice(0, 6)

  const primarySet = new Set(primary)
  const details = Object.keys(row || {})
    .filter((key) => present(row[key]) && !primarySet.has(key) && key !== titleKey)
    .sort((a, b) => (labels[a] || a).localeCompare(labels[b] || b, 'zh-CN'))

  return { titleKey, subtitleKeys, primary, details, labels }
}

export function formatResultValue(key, value) {
  if (!present(value)) return '—'

  if (key === 'risk_probability') {
    const n = Number(value)
    return Number.isFinite(n) ? `${(n * 100).toFixed(1)}%` : String(value)
  }

  if (['order_qty', 'completed_qty', 'actual_qty', 'task_qty'].includes(key)) {
    const n = Number(value)
    return Number.isFinite(n) ? n.toLocaleString('zh-CN') : String(value)
  }

  if (['order_amount', 'amount', 'invest_amount'].includes(key)) {
    const n = Number(value)
    return Number.isFinite(n) ? `¥${n.toLocaleString('zh-CN')}` : String(value)
  }

  if (key === 'status') return statusLabel(value)

  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

export function statusLabel(value) {
  const map = {
    completed: '已完成',
    cancelled: '已取消',
    in_progress: '进行中',
    pending: '待处理',
    ACTIVE: '进行中',
    CLOSED: '已关闭',
    ON_HOLD: '暂停'
  }
  return map[value] || String(value)
}

export function rowTitle(row, index, displayMeta) {
  const { titleKey } = splitRowFields(row, displayMeta)
  return present(row?.[titleKey]) ? row[titleKey] : `记录 ${index + 1}`
}

export function rowSubtitle(row, displayMeta) {
  const { subtitleKeys, labels } = splitRowFields(row, displayMeta)
  return subtitleKeys
    .map((key) => `${labels[key] || key}：${row[key]}`)
    .join(' · ')
}

export function primaryFields(row, displayMeta) {
  return splitRowFields(row, displayMeta).primary
}

export function detailFields(row, displayMeta) {
  return splitRowFields(row, displayMeta).details
}

export function labelFor(key, displayMeta) {
  return (displayMeta?.labels || FIELD_LABELS)[key] || key
}

export function summaryText(m) {
  const n = m.data ? m.data.length : 0
  const meta = m.display || {}
  if (meta.total != null && meta.total !== '') {
    const page = meta.page || 1
    const pageSize = meta.pageSize || n
    return `共 ${meta.total} 条，当前第 ${page} 页，本页 ${pageSize} 条`
  }
  return `当前返回 ${n} 条`
}
