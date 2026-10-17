// 推理适配器（PLM 版）：调用 PLM 后端 /v1/agents/llm/answer（大模型推理端点）。
// 鉴权与查询适配器一致：账号登录换 JWT，401 自动重登。
// 未配置（TARGET_REASONING_URL 为空）时维持「未接通」占位返回，不伪造。
export function createReasoningAdapter({ url, username, password }) {
  const base = (url || '').trim();

  async function login() {
    // API 根 = 推理端点去掉 /v1/... 后的前缀（如 http://host/api）
    const root = base.split('/v1/')[0];
    const resp = await fetch(`${root}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    });
    const body = await resp.json().catch(() => null);
    const data = body && body.code === 200 ? body.data : null;
    if (!data || !data.token) throw new Error(`PLM 登录失败: ${(body && body.message) || resp.status}`);
    return data.token;
  }

  return async function reason(request, abortSignal) {
    if (!base) {
      return {
        summary: '推理能力尚未接通（TARGET_REASONING_URL 未配置）。',
        conclusions: [{
          matter: '推理接口未接通',
          evidence_refs: ['control-platform://reasoning/not-connected'],
          risk: '无法给出真实分析结论',
          recommendation: '配置 TARGET_REASONING_URL 指向 PLM 的 /v1/agents/llm/answer 后重试',
          confidence: 'low',
        }],
        data_as_of: new Date().toISOString(),
        data_confidence: 'low',
      };
    }
    const payload = { prompt: request.prompt, task_type: request.task_type };
    const attempt = (token) => fetch(base, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', authorization: `Bearer ${token}` },
      body: JSON.stringify(payload),
      signal: abortSignal,
    });
    let token = await login();
    let resp = await attempt(token);
    if (resp.status === 401) {
      token = await login();
      resp = await attempt(token);
    }
    const body = await resp.json().catch(() => null);
    if (!resp.ok || !body || body.code !== 200) {
      const err = new Error((body && body.message) || `推理上游返回 HTTP ${resp.status}`);
      err.code = 'UPSTREAM_ERROR';
      throw err;
    }
    const data = body.data || {};
    return {
      summary: data.summary || '(空)',
      conclusions: Array.isArray(data.conclusions) && data.conclusions.length
        ? data.conclusions
        : [{ matter: data.summary || '(无结论)', evidence_refs: ['plm://llm/answer'], confidence: 'medium' }],
      data_as_of: data.data_as_of || new Date().toISOString(),
      data_confidence: data.data_confidence || 'medium',
    };
  };
}
