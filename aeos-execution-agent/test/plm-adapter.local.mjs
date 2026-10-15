// 本地验证 PLM 查询适配器：node test-plm-adapter.local.mjs（在 aeos-execution-agent 目录下运行）
import { createPlmQueryAdapter, PLM_QUERY_PATHS } from '../src/adapters/plm-query.js';

const baseUrl = process.env.TARGET_QUERY_BASE_URL || 'http://localhost:8081/api';
const username = process.env.TARGET_QUERY_USERNAME || 'admin';
const password = process.env.TARGET_QUERY_PASSWORD || 'admin@123';

const query = createPlmQueryAdapter({ baseUrl, username, password });

function preview(data) {
  const total = data && typeof data === 'object' ? data.total : '';
  const records = data && Array.isArray(data.records) ? data.records : [];
  const first = records[0] || data;
  return `total=${total} records=${records.length} first=${JSON.stringify(first).slice(0, 180)}`;
}

for (const [path, params] of [
  ['/project/page', { pageSize: 3, keyword: 'HJ.26042' }],
  ['/rd/tracking', { pageSize: 3, status: 'ACTIVE' }],
  ['/initiation/list', { pageSize: 3 }],
  ['/project/evidence/list', { pageSize: 3 }],
  ['/rd/sheets/plan', { pageSize: 3, projectNo: 'HJ.26042.M1.000' }],
  ['/rd/sheets/basebom', { pageSize: 3, projectNo: 'HJ.26042.M1.000' }],
]) {
  if (!PLM_QUERY_PATHS.includes(path)) throw new Error(`白名单缺失: ${path}`);
  try {
    const data = await query({ path, params });
    console.log(`[OK] ${path} -> ${preview(data)}`);
  } catch (e) {
    console.log(`[FAIL] ${path} -> ${e.code || ''} ${e.message}`);
    process.exitCode = 1;
  }
}
// 白名单外的路径必须被拒绝
try {
  await query({ path: '/auth/info', params: {} });
  console.log('[FAIL] 白名单外路径未被拦截');
  process.exitCode = 1;
} catch (e) {
  console.log(`[OK] 白名单拦截 -> ${e.code}: ${e.message}`);
}
