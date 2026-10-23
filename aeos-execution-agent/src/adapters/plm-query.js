// PLM（恒剑 PLM V4）只读查询适配器：JWT 登录 + 端点白名单 + Result 信封解包。
// 复用通用适配器的白名单/错误码约定；鉴权改为「账号登录换 JWT，401 自动重登」。
//
// 接入约定（见 INTEGRATION.md）：
//   - 只放行 PLM_QUERY_PATHS 内的 GET 只读端点；
//   - PLM 响应信封 { code:200, message, data }，unwrap 取 data；
//   - 业务错误/登录失效统一抛 UPSTREAM_ERROR / 401 重试。

export const PLM_QUERY_PATHS = [
  '/project/page',
  '/rd/tracking',
  '/initiation/list',
  '/project/evidence/list',
  '/rd/sheets/plan',
  '/rd/sheets/basebom',
  '/rd/sheets/spec',
  '/rd/sheets/config',
  '/rd/sheets/sample',
  '/rd/sheets/review',
  '/rd/sheets/test',
  '/rd/sheets/trial',
  '/rd/sheets/shipment',
];

const PROJECT_STATUS_ENUM = [
  { value: 'ACTIVE', label: '进行中' },
  { value: 'MP', label: '已量产' },
  { value: 'CLOSED', label: '已关闭' },
  { value: 'ON_HOLD', label: '暂停' },
  { value: 'CANCELLED', label: '取消' },
];

const NODE_STATUS_ENUM = [
  { value: 'in_progress', label: '有进行中节点' },
  { value: 'has_date', label: '有计划日期' },
  { value: 'incomplete', label: '有未设置节点' },
  { value: 'all_done', label: '关键节点全完成' },
];

const SHEET_INTENTS = [
  { path: '/rd/sheets/plan', label: '计划表', keywords: ['计划', '甘特', 'plan'], fields: ['project_no', 'stage', 'work_item', 'month', 'plan_start', 'plan_end', 'owner', 'status'] },
  { path: '/rd/sheets/basebom', label: '基础BOM', keywords: ['BOM', '物料清单', '物料'], fields: ['project_no', 'material_code', 'material_name', 'spec', 'quantity', 'unit', 'supplier', 'key_part'] },
  { path: '/rd/sheets/spec', label: '规格书', keywords: ['规格', 'spec'], fields: ['project_no', 'model', 'product_name', 'version', 'status', 'approved_by', 'approved_at'] },
  { path: '/rd/sheets/config', label: '配置表', keywords: ['配置', 'config'], fields: ['project_no', 'model', 'status', 'remark'] },
  { path: '/rd/sheets/sample', label: '样品单', keywords: ['样品', 'sample'], fields: ['sample_no', 'project_no', 'sample_name', 'spec', 'quantity', 'require_date', 'status'] },
  { path: '/rd/sheets/review', label: '评审单', keywords: ['评审', 'review'], fields: ['review_no', 'project_no', 'review_type', 'review_date', 'conclusion', 'host'] },
  { path: '/rd/sheets/test', label: '测试报告', keywords: ['测试', '送检', 'test'], fields: ['report_no', 'project_no', 'test_type', 'test_item', 'verdict', 'test_date', 'status'] },
  { path: '/rd/sheets/trial', label: '试产报告', keywords: ['试产', 'trial'], fields: ['report_no', 'project_no', 'trial_type', 'trial_batch', 'trial_qty', 'good_qty', 'conclusion', 'trial_date'] },
  { path: '/rd/sheets/shipment', label: '出货', keywords: ['出货', '验货', 'shipment'], fields: ['order_no', 'project_no', 'customer', 'ship_date', 'verify_date', 'verdict', 'status'] },
];

// 查询语义（intent）：供 Hub 的 LLM 做「自然语言 → path+params」解析与结果渲染。
export const PLM_QUERY_INTENT = {
  paths: [
    {
      path: '/project/page',
      label: '研发项目',
      keywords: ['项目', '研发项目', 'project'],
      params: [
        { name: 'keyword', label: '关键字(编号/名称/料号/客户)' },
        { name: 'status', label: '项目状态', enum: PROJECT_STATUS_ENUM },
        { name: 'pageNum', label: '页码' },
        { name: 'pageSize', label: '每页条数' },
      ],
      output: { title: ['projectNo', 'projectName'], fields: ['projectNo', 'projectName', 'customerName', 'owner', 'projectType', 'status', 'currentGate', 'investAmount', 'orderAmount'] },
    },
    {
      path: '/rd/tracking',
      label: '研发项目跟踪',
      keywords: ['跟踪', '进度', '节点', 'tracking'],
      params: [
        { name: 'keyword', label: '关键字(编号/名称/客户/负责人)' },
        { name: 'status', label: '项目状态', enum: PROJECT_STATUS_ENUM },
        { name: 'nodeStatus', label: '节点状态', enum: NODE_STATUS_ENUM },
        { name: 'pageNum', label: '页码' },
        { name: 'pageSize', label: '每页条数' },
      ],
      output: { title: ['projectNo', 'projectName'], fields: ['projectNo', 'projectName', 'owner', 'status', 'nodeDone', 'nodeTotal', 'keyDone', 'keyTotal', 'inProgress', 'notSet'] },
    },
    {
      path: '/initiation/list',
      label: '立项申请',
      keywords: ['立项', '立项申请', 'initiation'],
      params: [
        { name: 'keyword', label: '关键字' },
        { name: 'pageNum', label: '页码' },
        { name: 'pageSize', label: '每页条数' },
      ],
      output: { title: ['initNo', 'projectName'], fields: ['initNo', 'projectName', 'projectNo', 'owner', 'department', 'applicant', 'approvalStatus', 'projectId'] },
    },
    {
      path: '/project/evidence/list',
      label: '证据台账',
      keywords: ['证据', 'evidence'],
      params: [
        { name: 'keyword', label: '关键字(证据物/说明/节点)' },
        { name: 'nodeCode', label: '节点编码(如 PLAN/BOM/SPEC)' },
        { name: 'source', label: '来源', enum: [{ value: 'FILE', label: '文件' }, { value: 'TEXT', label: '填写' }] },
        { name: 'projectId', label: '项目ID' },
        { name: 'pageNum', label: '页码' },
        { name: 'pageSize', label: '每页条数' },
      ],
      output: { title: ['fileName'], fields: ['projectNo', 'nodeCode', 'fileName', 'docType', 'source', 'uploadedBy', 'uploadedAt'] },
    },
    ...SHEET_INTENTS.map((s) => ({
      path: s.path,
      label: s.label,
      keywords: s.keywords,
      params: [
        { name: 'projectNo', label: '项目编号' },
        { name: 'keyword', label: '关键字' },
        { name: 'pageNum', label: '页码' },
        { name: 'pageSize', label: '每页条数' },
      ],
      output: { title: s.fields.slice(0, 2), fields: s.fields },
    })),
  ],
};

function errorWithCode(code, message) {
  const err = new Error(message);
  err.code = code;
  return err;
}

// config: { baseUrl, username, password }
export function createPlmQueryAdapter({ baseUrl, username, password }) {
  const base = (baseUrl || '').replace(/\/$/, '');
  let token = null;

  async function login() {
    const response = await fetch(`${base}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    });
    const body = await response.json().catch(() => null);
    const data = body && body.code === 200 ? body.data : null;
    if (!data || !data.token) {
      throw errorWithCode('UPSTREAM_UNAVAILABLE', `PLM 登录失败: ${(body && body.message) || response.status}`);
    }
    token = data.token;
    return token;
  }

  async function attempt(url, abortSignal) {
    const response = await fetch(url, {
      headers: token ? { authorization: `Bearer ${token}` } : {},
      signal: abortSignal,
    });
    const body = await response.json().catch(() => null);
    return { response, body };
  }

  return async function query(request, abortSignal) {
    const path = typeof request.path === 'string' ? request.path : '';
    const params = request.params && typeof request.params === 'object' ? request.params : {};
    if (!path) throw errorWithCode('INVALID_ARGUMENT', '查询输入缺少 path');
    if (!PLM_QUERY_PATHS.includes(path)) {
      throw errorWithCode('FORBIDDEN', `不在白名单内的查询 path: ${path}`);
    }
    const queryString = new URLSearchParams(
      Object.entries(params)
        .filter(([, value]) => value !== '' && value !== null && value !== undefined)
        .map(([key, value]) => [key, String(value)]),
    ).toString();
    const url = `${base}${path}${queryString ? `?${queryString}` : ''}`;

    if (!token) await login();
    let { response, body } = await attempt(url, abortSignal);
    if (response.status === 401 || (body && body.code === 401)) {
      token = null;
      await login();
      ({ response, body } = await attempt(url, abortSignal));
    }
    if (response.status !== 200) {
      throw errorWithCode(response.status >= 500 ? 'UPSTREAM_UNAVAILABLE' : 'UPSTREAM_ERROR', `上游返回 HTTP ${response.status}`);
    }
    if (!body || typeof body !== 'object' || body.code !== 200) {
      throw errorWithCode('UPSTREAM_ERROR', (body && body.message) || '上游返回业务错误');
    }
    const data = body.data;
    // 统一返回契约：分页结果包装为 { items, total, data_as_of, data_confidence, stale }
    if (data && typeof data === 'object' && Array.isArray(data.records)) {
      return {
        items: data.records,
        total: data.total ?? data.records.length,
        data_as_of: new Date().toISOString(),
        data_confidence: 'high',
        stale: false,
      };
    }
    return data ?? null;
  };
}
