# 恒剑PLM 接入 AEOS Hub 交接文档（含对方回传模板）

> 日期：2026-10-21
> 接入模式：A（执行面 Sidecar，统一标准）
> 说明：本文不含令牌、密码、模型 Key；系统令牌与业务凭据由 Hub 安全渠道发放。
> 状态：**方案 / 前置条件**，非「已接通」。执行面已部署运行，但令牌体系待迁移为每系统令牌。

---

## 一、系统角色与拓扑

```text
恒剑PLM 执行面 Sidecar (aeos-execution-agent / plm2-aeos-agent)
    → 反向 WebSocket（主动连出，ws://192.168.0.23:8788/execution-agent）→ AEOS Hub
恒剑PLM 执行面 Sidecar
    → 本地只读 HTTP (JWT Bearer) → 恒剑PLM 后端 (Spring Boot, /api)

借调方向：中台 → AEOS Hub →（已建立的反向 WS）→ PLM Sidecar → PLM 后端
```

> PLM Sidecar 主动反向连接 Hub，Hub 不主动连 PLM。PLM 不开放入站端口。

角色：**只读能力提供方**（暂不做跨系统请求方、不做跨系统写）。

---

## 二、Hub 登记信息

| 项 | 值 |
|---|---|
| systemId（建议值） | `hengjian-plm` |
| 系统显示名 | 恒剑PLM（恒剑光电 PLM 中台 V4.0） |
| tenantId | tenant-demo |
| 接入模式 | A（执行面 Sidecar） |
| Hub WebSocket | `ws://192.168.0.23:8788/execution-agent` |
| Hub HTTPS API | `https://aeos-hub.test:9444` |
| 部署环境 | 内网试点（UAT） |
| 负责人 / 联系人 | 【待填】 |

---

## 三、Agent 清单

### 3.1 连接 Hub 的执行面 Agent（1 个）

| Agent ID | 名称 | 职责 | 能力 | 只读 | 连接 Hub | 备注 |
|---|---|---|---|---|---|---|
| `runtime-execution-query` | 恒剑PLM执行面智能体 | 只读查询 PLM 业务数据 + 只读大模型推理 | `query_plm` / `reason_plm` | 是 | 是 | 已部署运行（容器 `plm2-aeos-agent`） |

> 注：`query_local_files`（本机文件只读）已实现，但按规约**不开放跨系统借调**，见 §4。

### 3.2 系统自带智能体（9 个，留本地、不连 Hub、不黑盒暴露）

| 序号 | Agent Code | 名称 | 自主级 | 工具/职责 | 是否连 Hub |
|---|---|---|---|---|---|
| 1 | AGENT_CODER | 编码助手 | L1 | 编码（本地写） | 否 |
| 2 | AGENT_QA | 质检官 | L1 | dq.run/score/autofix（含写） | 否 |
| 3 | AGENT_ANALYST | 分析师 | L0 | 洞察/图表解读（只读） | 否 |
| 4 | AGENT_COACH | 改善教练 | L1 | issue/improve（含写） | 否 |
| 5 | AGENT_ECN | 变更助理 | L1 | bom.diff/ecn.draft（含写） | 否 |
| 6 | AGENT_GATE | 技转检查员 | L0 | 齐套/放行风险（只读） | 否 |
| 7 | AGENT_BOM | 结构助理 | L1 | bom.validate/extract（含写） | 否 |
| 8 | AGENT_HELP | 问答员 | L0 | 制度问答（只读） | 否 |
| 9 | AGENT_ORCH | 总控 | L0 | 意图路由/多代理协作 | 否 |

说明：
- 实际 Agent 数量（连接 Hub）= **1**；系统自带智能体 = **9**（均留本地）。
- 含写能力的 Agent（CODER/QA/COACH/ECN/BOM）一律留本地，人工确认后执行，不通过 Hub。

---

## 四、能力清单

| 能力名 | 版本 | 类型 | 只读 | 可跨系统借调 | 说明 |
|---|---|---|---|---|---|
| `query_plm` | 1.0.0 | query | 是 | 是 | 精确查询 PLM 只读业务数据 |
| `reason_plm` | 1.0.0 | reason | 是 | 是 | 调用 PLM 大模型只读分析 |
| `query_local_files` | 1.0.0 | query | 是 | **否** | 本机文件/电脑只读，不开放跨系统 |

### query_plm 支持 path（只读白名单）

```text
/project/page            研发项目分页
/rd/tracking             研发项目跟踪总表
/initiation/list         立项申请
/project/evidence/list   证据台账
/rd/sheets/plan          计划表
/rd/sheets/basebom       基础BOM
/rd/sheets/spec          规格书
/rd/sheets/config        配置表
/rd/sheets/sample        样品单
/rd/sheets/review        评审单
/rd/sheets/test          测试报告
/rd/sheets/trial         试产报告
/rd/sheets/shipment      出货
```

输入：`{ path, params }`，`path` 必填且在白名单内。
输出：PLM 统一信封 `{ code:200, message, data }`，解包后返回 `data`；可携带 `data_as_of` / `data_confidence` / `stale`。

### reason_plm

- 端点：PLM 后端 `POST /api/v1/agents/llm/answer`
- 输入：`{ prompt(≤2000), task_type }`
- 输出：`{ summary, conclusions[{matter,evidence_refs,confidence}], data_as_of, data_confidence }`
- 模型 ID：【待填】（`PLM_LLM_MODEL`，OpenAI 兼容）；未配置时返回「未配置/低置信度」占位，不伪造。

---

## 五、执行面配置（.env 模板，不含密钥）

```dotenv
EXECUTION_WS_URL=ws://192.168.0.23:8788/execution-agent
# 待迁移：改为每系统令牌（下方两项），当前为 legacy 根令牌
EXECUTION_SYSTEM_ID=hengjian-plm
EXECUTION_SYSTEM_TOKEN=__HUB_安全渠道发放__
EXECUTION_AGENT_ID=runtime-execution-query
EXECUTION_AGENT_NAME=恒剑PLM执行面智能体
EXECUTION_SOURCE_SYSTEM=恒剑PLM
EXECUTION_AGENT_VERSION=1.0.0
EXECUTION_PROTOCOL_VERSION=1.0

TARGET_QUERY_BASE_URL=http://backend:8080/api
TARGET_QUERY_USERNAME=__只读账号（安全渠道）__
TARGET_QUERY_PASSWORD=__只读口令（安全渠道）__

TARGET_REASONING_URL=http://backend:8080/api/v1/agents/llm/answer
TARGET_REASONING_AGENT_ID=
TARGET_REASONING_TOKEN=
```

> 当前 `.env` 仍用 `EXECUTION_PLANE_TOKEN`（legacy 根令牌），需迁移为 `EXECUTION_SYSTEM_ID` + `EXECUTION_SYSTEM_TOKEN`（每系统令牌）。

---

## 六、网络与部署

| 项 | 值 |
|---|---|
| 部署机器/IP | 192.168.0.71（宿主机，Docker Desktop for Windows） |
| 操作系统 | Windows + Docker |
| Node.js | ≥18（容器内） |
| Docker | 是 |
| 服务/容器名 | `aeos-execution-agent` / `plm2-aeos-agent` |
| 启动命令 | `docker compose up -d aeos-execution-agent` |
| 停止命令 | `docker compose stop aeos-execution-agent` |
| 日志 | `docker compose logs aeos-execution-agent`（json-file，10m×3） |

连通性：

| 项 | 状态 |
|---|---|
| Hub WebSocket 8788 | 是（`ws://192.168.0.23:8788/execution-agent`） |
| Hub HTTPS 9444 | 待测（`aeos-hub.test`） |
| hosts | 是（compose `extra_hosts: aeos-hub.test→192.168.0.23`） |
| CA 根证书 | 待测（容器内已设 `NODE_TLS_REJECT_UNAUTHORIZED=0` 内网试点） |

---

## 七、业务只读接口

| 项 | 值 |
|---|---|
| Base URL | `http://backend:8080/api`（容器内）/ `http://192.168.0.71/api`（宿主机） |
| 登录方式 | JWT（`POST /auth/login` 账号密码换 Bearer Token，401 自动重登） |
| 会话有效性 | 是 |
| 已用真实数据验证 | 是（AEOS 接口 36/0；E2E 功能 16/0） |

参数契约：

```text
页码参数：pageNum / pageSize
业务键：projectNo / projectId / nodeCode / initNo
关键字：keyword（编号/名称/料号/客户/负责人）
状态：status（ACTIVE/MP/CLOSED/ON_HOLD/CANCELLED）；nodeStatus（in_progress/has_date/incomplete/all_done）
来源：source（FILE/TEXT）
```

---

## 八、assistant-events 映射

```text
是否实现 assistant-events：否 / 暂不做
原因：本机 ReAct/聊天智能体留本地，不通过 Hub 观测；执行面已通过 run_event 上报 run 状态。
```

---

## 九、借调 / 交计划

- 单能力借调：`POST /api/system/borrow-orders`（只读，`query_plm`/`reason_plm` 已在允许列表）。
- 交计划：默认关闭（`ORCHESTRATION_TASKS_ENABLED=0`），受控测试后再评估。

---

## 十、测试数据

```text
真实测试项目号：【待填】（建议 HJ.26033.V1.256JY / HJ.26036~26041 等）
数据时间：【待填】
是否允许联调：是
是否涉及敏感数据：否（项目研发元数据）
是否需要脱敏：【待填】
```

---

## 十一、用量与计价

```text
query_plm 是否经过模型：否（直接接口查询）
reason_plm 模型 ID：【待填】（PLM_LLM_MODEL）
run_complete 是否上报 tokensIn/tokensOut：待确认
是否接受 Hub 按模型目录补算成本：【待填】
```

---

## 十二、安全边界

- 默认只读；不通过 Hub 做跨系统写。
- 写 Agent（CODER/QA/COACH/ECN/BOM）留本地，人工确认后执行。
- `query_local_files` 不开放跨系统借调。
- 不使用 legacy 根令牌（待迁移为每系统令牌）。
- 不把令牌、密码、模型 Key 写进文档/代码/聊天。
- 不伪造在线状态、数据或用量。

---

## 十三、请对方（Hub 方）回传的信息

```text
1. systemId 登记：hengjian-plm（请 Hub 方在 data/systems.json 登记）
2. Agent ID 允许列表：runtime-execution-query（请加入允许列表）
3. 每系统令牌：EXECUTION_SYSTEM_TOKEN（安全渠道发放）
4. 确认 8788 可达、9444 可达（如用域名需 hosts/CA）
5. 约定联调时间
```

---

## 十四、联调顺序

```text
1. Hub 登记 systemId + Agent ID 允许列表
2. Hub 发放每系统令牌（安全渠道）
3. 迁移 .env：EXECUTION_SYSTEM_ID + EXECUTION_SYSTEM_TOKEN（替换 legacy 根令牌）
4. 重启 aeos-execution-agent，Agent 上线 authMode=system-token
5. 能力目录 available（query_plm / reason_plm）
6. 直接借调 query_plm 真实查询
7. reason_plm 借调（未配模型则返回低置信度占位）
8. （可选）受控打开交计划开关，跑一条只读计划后关闭
```

---

## 十五、验收清单

- [ ] Agent 在线（`authMode=system-token`）
- [ ] 能力注册成功、目录 `available`
- [ ] `query_plm` 真实查询返回数据（`data_as_of` / `data_confidence` / `stale` 正确）
- [ ] `reason_plm` 借调返回（含 evidence_refs）
- [ ] 借调返回 completed
- [ ] `query_local_files` 未开放跨系统借调
- [ ] 已迁移为每系统令牌（非 legacy 根令牌）
- [ ] 无跨系统写

---

## 十六、待办 / 已知限制

1. **令牌迁移（高）**：当前 `EXECUTION_PLANE_TOKEN`（legacy 根令牌）→ 改为 `EXECUTION_SYSTEM_ID` + `EXECUTION_SYSTEM_TOKEN`。
2. `query_local_files` 已实现但**不得开放跨系统借调**（需在 Hub 侧限制）。
3. `reason_plm` 依赖 `PLM_LLM_*`（模型 Base URL / Key / 模型 ID），未配置时返回「低置信度」占位。
4. 联系人/电话/邮箱/可联调时间：**【待填】**。

---

## 十七、最小回传

```text
systemId：hengjian-plm
系统显示名：恒剑PLM（恒剑光电 PLM 中台 V4.0）
实际 Agent 数量（连 Hub）：1
Agent ID：runtime-execution-query
能力：query_plm（只读）/ reason_plm（只读）
只读：是
连 Hub：是
Base URL：http://backend:8080/api（或 http://192.168.0.71/api）
真实测试键：【待填】
联系人：【待填】
可联调时间：【待填】
8788：是；9444：待测
```
