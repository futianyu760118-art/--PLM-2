# M04 研发自治中心 · 同步契约明细 v1

> 文档编号: HJ-PLM-M04-CONTRACTS-v1.0
> 上位基线: AEOS V7.2 `AEOS/contracts/CONTRACT_PACK_V1.md`（Object / API / Event / Result / Evidence / Decision-Action）
> 关联: `docs/m04-rd-autonomous-migration-sync-plan.md`
> 状态: P0 冻结。正式 Schema 由 Architecture Board 批准。

---

## 1. Object Contract

所有跨系统同步对象至少携带：

```json
{
  "tenant_id": "hjgd",
  "object_type": "PROJECT_NODE",
  "object_id": "12345",
  "external_key": "HJ2601-001:TECH_TRANSFER",
  "source_system": "PLM2",
  "revision": 7,
  "correlation_id": "uuid",
  "updated_at": "2026-09-27 10:00:00",
  "payload": { }
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| tenant_id | ✓ | 多租户占位，恒为 `hjgd` |
| object_type | ✓ | 见第 6 节对象类型枚举 |
| object_id | ✓ | 本系统内主键 |
| external_key | ✓ | 跨系统业务唯一键（料号/项目号+节点码/资料号…） |
| source_system | ✓ | `EBMS` / `PLM2` |
| revision | ✓ | 每次变更 +1，冲突检测依据 |
| correlation_id | ✓ | 跨系统链路 ID，贯穿双向 |
| updated_at | ✓ | 最后变更时间（东八区） |
| payload | ✓ | 对象字段快照 |

---

## 2. API Contract

### 2.1 同步接口（PLM-2 提供）

| 方法 | 路径 | 用途 | 幂等 |
|---|---|---|---|
| POST | `/api/v1/sync/inbound` | 接收 EBMS 变更 | `event_id` 唯一 |
| GET | `/api/v1/sync/outbound/pending?limit=` | 供 EBMS 拉取待推送变更 | — |
| POST | `/api/v1/sync/outbound/ack` | EBMS 确认已消费 | `event_id` |
| GET | `/api/v1/sync/objects` | 查询对象映射与版本 | — |
| GET | `/api/v1/sync/conflicts` | 冲突列表 | — |
| POST | `/api/v1/sync/conflicts/{id}/resolve` | 人工裁决 | — |
| POST | `/api/v1/sync/reconcile` | 触发对账 | — |

### 2.2 入站请求体

```json
{
  "event_id": "uuid",
  "event_type": "m04.project_node.updated",
  "object_type": "PROJECT_NODE",
  "external_key": "HJ2601-001:TECH_TRANSFER",
  "operation": "UPDATE",
  "source_system": "EBMS",
  "revision": 8,
  "correlation_id": "uuid",
  "occurred_at": "2026-09-27 10:00:00",
  "payload": { "status": "DONE", "actual_date": "2026-09-26" }
}
```

响应：`{ code:200, data:{ applied:true, conflict:false } }`；冲突时 `{ conflict:true, conflict_id }`。

### 2.3 认证与安全

- 传输：HTTPS。
- 认证：JWT Bearer（复用 PLM-2 `SecurityUtils`）或 HMAC-SHA256 签名头 `X-Webhook-Signature` + `X-Timestamp`。
- 幂等：`event_id` 唯一；重复直接返回已应用。
- 审计：入站/出站均写 `plm_sync_inbox` / `plm_domain_event`，保留 `correlation_id`。

---

## 3. Event Contract

字段（AEOS Event Contract）：`event_id, event_type, object_ref{object_type,object_id,external_key}, occurred_at, source_system, correlation_id, version, evidence_refs`。

### 3.1 事件目录（M04）

| event_type | object_type | 触发 |
|---|---|---|
| `m04.product.created` / `.updated` / `.deleted` | PRODUCT | 产品/物料变更 |
| `m04.project.created` / `.updated` / `.status_changed` | PROJECT | 项目变更 |
| `m04.project_node.updated` | PROJECT_NODE | 节点状态/日期/证据变更 |
| `m04.initiation.advanced` / `.approved` | INITIATION | 立项审批推进/批准转项目 |
| `m04.review.updated` | PROJECT_REVIEW | 复盘 |
| `m04.supply_issue.updated` | SUPPLY_ISSUE | 供应链异常 |
| `m04.sales_promotion.updated` | SALES_PROMOTION | 销售推广进度 |
| `m04.bom.updated` | BOM | BOM 结构变更 |
| `m04.bom_issue.created` / `.updated` | BOM_ISSUE | BOM 问题 |
| `m04.sample.updated` | SAMPLE | 样品 |
| `m04.spec.updated` | SPEC | 规格书 |
| `m04.config.updated` | CONFIG | 配置表 |
| `m04.tech_doc.updated` / `.approved` | TECH_DOC | 技转资料 |
| `m04.tech_flow.stage_advanced` | TECH_FLOW | 技转阶段 |
| `m04.tech_change.audited` / `.executed` / `.verified` | TECH_CHANGE | 技术变更 |

### 3.2 回环规则

- 事件带 `source_system`；收到源自自身的事件仅确认，不再回推。
- 应用后比较 `plm_sync_object.checksum`，相等则跳过。

---

## 4. Result Contract

M04 向 EBMS（M03）输出管理可见 Result（EBMS 只读消费，不拼专业内部表）：

| Result | 端点 | 内容 |
|---|---|---|
| 项目结果 | `GET /api/v1/integration/projects/{projectNo}` | 阶段/状态/健康分/关键节点达成 |
| 项目节点结果 | `GET /api/v1/integration/projects/{projectNo}/nodes` | 22 节点状态矩阵 |
| 零件摘要 | `GET /api/v1/integration/parts/{partNo}` | 已实现 |
| CBOM 成本 | `GET /api/v1/integration/parts/{partNo}/cbom` | 已实现 |
| 异常 | Result 内 `exceptions[]` | 逾期/未闭环/完成证据不足 |

Result 必须带 `source_system` / `revision` / `evidence_refs`，可被验证。

---

## 5. Evidence Contract

| 场景 | Evidence 载体 | 字段 |
|---|---|---|
| 节点完成 | `plm_project_node_evidence` | `node_id, file_id/content, doc_type, version_no, uploaded_by, uploaded_at` |
| 技转资料 | `plm_tech_doc_version` + `plm_file` | `file_path, file_name, file_hash(md5), version, change_summary` |
| 审批留痕 | `plm_project_gate_log` / `plm_ecn_flow_log` | `operator, result, actual_date, comment, evidence_ref` |

要求：可追溯 `source/version/checksum/provenance`；关键结果（Release/技转完成）必须有 Evidence。

---

## 6. Decision / Action Contract

链路：`Fact → Event → DecisionCase → Decision → Action → Result → Verify → Evidence`

| 概念 | PLM-2 落点 |
|---|---|
| Fact | 各 M04 业务表 |
| Event | `plm_domain_event` |
| Decision | 审批（立项五阶段 / ECN 两级 / 技转两级 / 关键节点验收） |
| Action | `plm_work_item`（Owner/Deadline/SLA/升级） |
| Result | 第 4 节 |
| Verify | 自检 `progress-check` / 技转 verify |
| Evidence | 第 5 节 |

---

## 7. 对象类型枚举（object_type）

```
PRODUCT, PROJECT, PROJECT_NODE, INITIATION, PROJECT_REVIEW,
SUPPLY_ISSUE, SALES_PROMOTION, BOM, BOM_ITEM, BOM_ISSUE,
SAMPLE, SPEC, CONFIG, TECH_DOC, TECH_FLOW, TECH_HANDOVER,
TECH_CHANGE, TECH_REVIEW, TECH_CASE
```

节点外部键规范：`{project_no}:{node_code}`（如 `HJ2601-001:TECH_TRANSFER`）。

---

## 8. 冲突语义（双主）

```
输入: local_revision, remote_revision, local_updated_at, remote_updated_at, field
1. 若 field 属"字段级权威表"定义 → 权威端值胜, 非权威差异记 plm_sync_conflict(PENDING)
2. 否则若 remote_revision == local_revision + 1 → 直接应用(LWW)
3. 否则(revision 跳跃/时间分叉) → 记 plm_sync_conflict(PENDING), 不覆盖
4. 人工裁决: LOCAL / REMOTE / MERGED
```

字段级权威（节选）：

| object_type | 权威字段 | 权威端 |
|---|---|---|
| PROJECT_NODE | status/remark | EBMS |
| PROJECT_NODE | plan_date/actual_date/evidence | PLM2 |
| PROJECT | 全部 | PLM2 |
| PRODUCT | 全部 | PLM2 |
| SAMPLE/SPEC/CONFIG | 全部 | EBMS（过渡期） |

---

*契约冻结后，任何变更须经 Architecture Board 批准并更新本文档版本号。*
