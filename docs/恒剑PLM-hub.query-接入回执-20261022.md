# 恒剑 PLM hub.query 工具 — 接入回执

> 系统：hengjian-plm（作为跨系统「请求方」，经 Hub 借调中台只读能力）
> 日期：2026-10-22
> 安全：本回执不含令牌、密码、模型 Key；令牌仅存于后端环境变量（已 gitignore）。

## 一、验收结论

PLM 智能体输入「查中台订单前 5 条」→ 经 Hub 能力调用 → 轮询到 completed → 展示真实中台订单：**通过**。

## 二、回执字段

```text
requestId：capinv-d5843365-ff8c-4e8a-89a4-3958ab5f372a
borrowOrderId：borrow-a799846f-01d9-4ceb-a884-ccfe6de13997
runId：exec-run-ba8fc538-ddb8-4c20-87fa-60787b7279dd
最终状态：completed（先 dispatched → completed）
total：3255
items：5（前 5 条）
使用的 capability：query_control_platform（path=/external/orders/list）
异常日志（不含令牌）：无
```

后端日志证据：

```text
[Hub] 能力调用 capability=query_control_platform requestId=capinv-d5843365-... borrowOrderId=borrow-a799846f-... runId=exec-run-ba8fc538-... status=dispatched
[Hub] 借调完成 borrowOrderId=borrow-a799846f-... status=completed
```

## 三、脱敏结果样例（中台订单）

```json
{ "order_no": "HJ201810-0021-0195", "line_no": "10", "customer": "HJ003.77-373",
  "order_qty": 1100, "order_amount": 137235, "status": "completed", "risk_level": "blue" }
```

## 四、实现方式

1. 新增统一工具 `hub.query`（`HubInvocationService`）：
   - `POST /api/system/capability-invocations`（x-aeos-system-id / x-aeos-system-token / X-AEOS-Contract-Version: 4.0）
   - 解析 borrowOrderId → 轮询 `GET /api/system/borrow-orders/{id}` 到 completed/failed（超时 30s）
2. `AgentChatController`（AGENT_ORCH 总控对话）规则层接入：命中「中台/跨系统/hub.query」→ 路由到 hub.query（关键词「订单」→ query_control_platform + /external/orders/list，页码按「前 N 条」解析）。
3. 未做每系统一套工具（query_erp_tool / query_mes_tool …），只保留一个通用 `hub.query`；能力目录由 Hub 维护。
4. 配置（application.yml / application-prod.yml）新增 `plm.hub.*`，令牌走环境变量 `AEOS_HUB_SYSTEM_TOKEN`，不落代码/Git。

## 五、安全说明

- 只调用只读能力（query_control_platform），不调用写能力；
- 查询失败返回 `BusinessException`（error.message），不伪造结果；
- 内网试点阶段 Hub 为自签证书，后端仅对 Hub 出站连接关闭证书校验（`plm.hub.insecure=true`，不关闭全局 TLS），后续可换 CA 根证书。
