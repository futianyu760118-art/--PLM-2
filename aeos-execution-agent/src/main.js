// AEOS 执行面智能体入口：读取配置 → 注册能力（自描述 schema）→ 连接 Hub → 待命。
// PLM 接入版：查询能力指向恒剑 PLM 只读接口（JWT 登录），推理能力占位（未接通返回明确提示）。
import { ExecutionAgentCore } from './agent-core.js';
import { loadConfig } from './config.js';
import { registerCapability, capabilityHandlers, capabilityDefinitions } from './capabilities.js';
import { createPlmQueryAdapter, PLM_QUERY_PATHS, PLM_QUERY_INTENT } from './adapters/plm-query.js';
import { createLocalFilesAdapter } from './adapters/local-files.js';
import { createReasoningAdapter } from './adapters/reasoning-adapter.js';

const log = (...args) => console.log(...args);

function main() {
  const config = loadConfig(process.env);

  // 查询能力：JWT 只读查询 PLM。inputSchema 自描述可接受的 path（PLM 只读白名单）。
  registerCapability({
    name: 'query_plm',
    description: '精确查询恒剑PLM只读业务数据（研发项目/跟踪/立项/证据/工作表）',
    version: '1.0.0',
    inputSchema: {
      type: 'object',
      properties: {
        path: { type: 'string', enum: PLM_QUERY_PATHS },
        params: { type: 'object' },
      },
      required: ['path'],
    },
    constraints: { readOnly: true },
    intent: PLM_QUERY_INTENT,
    handler: createPlmQueryAdapter({
      baseUrl: config.targetQueryBaseUrl,
      username: config.targetQueryUsername,
      password: config.targetQueryPassword,
    }),
  });

  // 本地电脑与文件访问能力（只读）：列目录/读文件/搜文件/系统信息，白名单根目录内。
  registerCapability({
    name: 'query_local_files',
    description: '只读访问本机文件与电脑信息（列目录/读文件/搜索/系统信息）',
    version: '1.0.0',
    inputSchema: {
      type: 'object',
      properties: {
        action: { type: 'string', enum: ['list', 'read', 'search', 'info'] },
        path: { type: 'string', description: '相对于白名单根的路径，如 PLM-2/docs' },
        pattern: { type: 'string', description: 'search 时按文件名正则搜索' },
        maxBytes: { type: 'number', description: 'read 时最多读取字节数(≤64KB)' },
      },
      required: ['action'],
    },
    constraints: { readOnly: true },
    intent: {
      paths: [{
        path: 'local_files',
        label: '本地文件/电脑',
        keywords: ['文件', '本地', '电脑', '目录', '磁盘', '内存', 'file', 'folder', 'disk'],
        params: [
          { name: 'action', label: '操作', enum: [{ value: 'list', label: '列目录' }, { value: 'read', label: '读文件' }, { value: 'search', label: '搜索文件' }, { value: 'info', label: '电脑信息' }] },
          { name: 'path', label: '路径(相对根目录)' },
          { name: 'pattern', label: '搜索词' },
        ],
        output: { title: ['name', 'path'], fields: ['path', 'type', 'size', 'modified'] },
      }],
    },
    handler: createLocalFilesAdapter({ roots: config.localFilesRoots }),
  });

  // 推理能力：调用 PLM 自有大模型/推理智能体（占位，未接通前明确返回未接通）。
  registerCapability({
    name: 'reason_plm',
    description: '调用恒剑PLM自有大模型或推理智能体完成只读分析',
    version: '1.0.0',
    inputSchema: {
      type: 'object',
      properties: {
        prompt: { type: 'string', maxLength: 2000 },
        task_type: { type: 'string' },
      },
      required: ['prompt', 'task_type'],
    },
    outputSchema: {
      type: 'object',
      properties: {
        conclusions: {
          type: 'array',
          items: {
            type: 'object',
            properties: { evidence_refs: { type: 'array', minItems: 1 } },
            required: ['evidence_refs'],
          },
        },
      },
    },
    constraints: { readOnly: true, requireEvidence: true },
    handler: createReasoningAdapter({
      url: config.targetReasoningUrl,
      username: config.targetQueryUsername,
      password: config.targetQueryPassword,
    }),
  });

  const core = new ExecutionAgentCore(
    {
      wsUrl: config.wsUrl,
      token: config.token,
      agentId: config.agentId,
      name: config.agentName,
      version: config.agentVersion,
      protocolVersion: config.protocolVersion,
      sourceSystem: config.sourceSystem,
      capabilities: capabilityDefinitions(),
    },
    capabilityHandlers(),
    log,
  );

  core.start();

  for (const signal of ['SIGINT', 'SIGTERM']) {
    process.on(signal, () => {
      log(`[agent] 收到 ${signal}，停止服务`);
      core.stop();
      process.exit(0);
    });
  }
}

main();
