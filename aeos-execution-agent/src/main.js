// AEOS 执行面智能体入口：读取配置 → 注册能力（自描述 schema）→ 连接 Hub → 待命。
// PLM 接入版：查询能力指向恒剑 PLM 只读接口（JWT 登录），推理能力占位（未接通返回明确提示）。
import { ExecutionAgentCore } from './agent-core.js';
import { loadConfig } from './config.js';
import { registerCapability, capabilityHandlers, capabilityDefinitions } from './capabilities.js';
import { createPlmQueryAdapter, PLM_QUERY_PATHS, PLM_QUERY_INTENT } from './adapters/plm-query.js';
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
    handler: createReasoningAdapter({ url: config.targetReasoningUrl, agentId: config.targetReasoningAgentId, token: config.targetReasoningToken }),
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
