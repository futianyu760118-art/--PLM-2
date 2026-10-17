// 配置与密钥读取：只从环境变量读取，绝不把密钥写入日志/镜像/示例文件。
export function loadConfig(env = process.env) {
  const required = (name) => {
    const value = (env[name] ?? '').trim();
    if (!value) throw new Error(`缺少环境变量 ${name}`);
    return value;
  };

  return {
    wsUrl: required('EXECUTION_WS_URL'),
    token: required('EXECUTION_PLANE_TOKEN'),
    agentId: required('EXECUTION_AGENT_ID'),
    agentName: (env.EXECUTION_AGENT_NAME ?? '').trim() || required('EXECUTION_AGENT_ID'),
    agentVersion: (env.EXECUTION_AGENT_VERSION ?? '').trim() || '1.0.0',
    protocolVersion: (env.EXECUTION_PROTOCOL_VERSION ?? '').trim() || '1.0',
    sourceSystem: required('EXECUTION_SOURCE_SYSTEM'),
    targetQueryBaseUrl: required('TARGET_QUERY_BASE_URL'),
    // PLM 采用 JWT 登录鉴权：用户名/密码登录换取 Bearer Token（401 自动重登）
    targetQueryUsername: required('TARGET_QUERY_USERNAME'),
    targetQueryPassword: required('TARGET_QUERY_PASSWORD'),
    // HMAC 凭据（PLM 不使用，保留给其他目标系统）
    targetQueryAppKey: (env.TARGET_QUERY_APP_KEY ?? '').trim(),
    targetQueryAppSecret: (env.TARGET_QUERY_APP_SECRET ?? '').trim(),
    targetReasoningUrl: (env.TARGET_REASONING_URL ?? '').trim(),
    targetReasoningAgentId: (env.TARGET_REASONING_AGENT_ID ?? '').trim(),
    targetReasoningToken: (env.TARGET_REASONING_TOKEN ?? '').trim(),
    // 本地文件访问白名单根目录（逗号分隔，须与容器挂载对应）
    localFilesRoots: (env.LOCAL_FILES_ROOTS ?? '').trim() || '/local/d',
  };
}
