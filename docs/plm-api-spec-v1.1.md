# PLM API 接口总规 V1.1

> **文档编号**: HJ-PLM-API-V1.1-20260722  
> **配套**: `plm-complete-redesign-lighting-sme-v1.1.md`、`10_v1_1_lean_agent_kpi.sql`  
> **目标**: **一次设计、长期取数**——业务、KPI/OKR、BI、EBMS、智能体、报表均走稳定契约，避免「每加一个看板就加一个接口」。

---

## 0. 设计原则（强制）

| # | 原则 | 说明 |
|---|------|------|
| 1 | **资源稳定、查询通用** | 主数据/单据用 REST 资源；统计用 **通用 Metric/Query/Export**，禁止为每个图表单独 CRUD |
| 2 | **读多写少分通道** | 写操作领域 API；海量读走 Query/Export/事件，不压写库连接 |
| 3 | **契约版本化** | URL 前缀 `/api/v1`；破坏性变更走 `/api/v2`，旧版至少并存 12 个月 |
| 4 | **字段可投影** | 支持 `fields=` 稀疏字段，减少后期「只要 3 个字段却加新接口」 |
| 5 | **过滤标准化** | 统一 `filter` / `sort` / `page` / `pageSize` 语法 |
| 6 | **一切可审计** | 写操作带 `X-Request-Id`；集成调用带 `X-Client-Id` |
| 7 | **事件优先推送** | 状态变更 Outbox + Webhook；下游少轮询 |
| 8 | **导出即接口** | CSV/JSON/Parquet 异步导出，BI 不直连库 |
| 9 | **权限随资源** | 同一套 RBAC；集成账号走 scope 令牌 |
| 10 | **OpenAPI 唯一真相** | 代码注解生成 `openapi.json`，禁止文档与实现长期漂移 |

---

## 1. 全局约定

### 1.1 Base

```
生产: https://{host}/api/v1
开发: http://localhost:8080/api/v1
```

### 1.2 认证

| 方式 | 用途 |
|------|------|
| `Authorization: Bearer <JWT>` | 人机 UI |
| `Authorization: Bearer <ClientCredentials>` | EBMS/ERP/BI 集成 |
| 可选 `X-Api-Key` | 只读导出网关 |

Scope 示例: `part:read` `bom:read` `metric:read` `kpi:read` `export:run` `event:subscribe`

### 1.3 统一响应

```json
{
  "code": 0,
  "message": "ok",
  "requestId": "b7c1…",
  "data": {},
  "meta": {
    "page": 1,
    "pageSize": 20,
    "total": 100,
    "serverTime": "2026-07-22T10:00:00+08:00"
  }
}
```

错误: `code != 0`；业务可预期错误用 4xx + 稳定 `errorCode`（如 `DQ_BLOCKED`、`CODE_CONFLICT`）。

### 1.4 分页 / 排序 / 过滤 / 字段

| 参数 | 示例 | 说明 |
|------|------|------|
| page, pageSize | `page=1&pageSize=50` | pageSize 最大 200；导出另走 export |
| sort | `sort=-updatedAt,partNo` | `-` 降序 |
| fields | `fields=partNo,nameZh,lifecycleState` | 投影 |
| filter | 见下 | 推荐 RSQL 或简化 DSL |

**简化 filter DSL（推荐实现）**:

```
lifecycleState=in=(RELEASED,IN_PRODUCTION)
productType==FL
updatedAt=ge=2026-01-01
q==壳体   // 全文/模糊
```

或 JSON body（复杂查询）:

```json
{
  "and": [
    { "field": "lifecycleState", "op": "in", "value": ["RELEASED"] },
    { "field": "productType", "op": "eq", "value": "FL" }
  ]
}
```

### 1.5 幂等

写接口支持头: `Idempotency-Key: <uuid>`（创建编号、ECN 提交、导出任务）。

### 1.6 限流

| 客户端 | 默认 |
|--------|------|
| UI 用户 | 120 req/min |
| 集成读 | 600 req/min |
| 导出 | 10 任务/小时/客户端 |
| 智能体 | 60 req/min/session |

---

## 2. API 分层（避免以后乱加）

```
┌─────────────────────────────────────────────────────────────┐
│ A. 领域命令 API（写 + 精确读单资源）  /parts /boms /ecns …    │
├─────────────────────────────────────────────────────────────┤
│ B. 通用查询 API（列表/下钻/关联）    /query /search /graph    │
├─────────────────────────────────────────────────────────────┤
│ C. 度量与目标 API（KPI/OKR 唯一出口） /metrics /kpis /okrs    │
├─────────────────────────────────────────────────────────────┤
│ D. 分析与导出 API（BI/报表唯一出口） /analytics /exports      │
├─────────────────────────────────────────────────────────────┤
│ E. 事件与集成 API（系统对接）         /integration /webhooks  │
├─────────────────────────────────────────────────────────────┤
│ F. 智能体与自动化                   /agents /dq /codegen     │
└─────────────────────────────────────────────────────────────┘
```

**取数红线**:

- ❌ 禁止: `/dashboard/xxxChart1`、`/report/fooBar` 无限增殖  
- ✅ 必须: 图表配置指向 `dataset_code` / `metric_code`，前端/BI 调 **C/D 层**  
- ❌ 禁止: 给 Excel 开只读数据库账号（默认）  
- ✅ 必须: `/exports` 异步落地 + 下载链接  

---

## 3. A. 领域资源 API（稳定 CRUD + 动作）

> 列表一律支持 filter/sort/page/fields；详情支持 fields。  
> **动作**用 `POST /resources/{id}/actions/{action}`，避免 `PUT /status` 语义不清。

### 3.1 主数据 Part

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/parts` | 列表 |
| GET | `/parts/{partNo}` | 详情（主键 partNo） |
| GET | `/parts/by-id/{id}` | 内部 id |
| POST | `/parts` | 创建（可 body 不带 partNo→自动编号） |
| PATCH | `/parts/{partNo}` | 补丁更新（草稿可改） |
| POST | `/parts/{partNo}/actions/submit-review` | 提审 |
| POST | `/parts/{partNo}/actions/release` | 发布（内含 DQ） |
| POST | `/parts/{partNo}/actions/to-production` | 转量产 |
| POST | `/parts/{partNo}/actions/obsolete` | 作废 |
| POST | `/parts/{partNo}/actions/seal` | 封存 |
| GET | `/parts/{partNo}/versions` | 版本历史 |
| GET | `/parts/{partNo}/versions/{versionNo}` | 快照 |
| GET | `/parts/{partNo}/params` | 参数 |
| PUT | `/parts/{partNo}/params` | 保存参数 |
| GET | `/parts/{partNo}/where-used` | 反查 |
| GET | `/parts/{partNo}/score` | 质量分（快捷，同源 DQ） |

**批量（减少以后批量接口）**:

| POST | `/parts/batch` | `{ "action":"get\|score\|lifecycle", "partNos":[] }` |

### 3.2 项目 Project

| 方法 | 路径 |
|------|------|
| GET/POST | `/projects` |
| GET/PATCH | `/projects/{projectNo}` |
| GET | `/projects/{projectNo}/gates` |
| POST | `/projects/{projectNo}/gates/{gateCode}/actions/pass\|fail\|skip` |
| GET | `/projects/{projectNo}/links` | 关联 part/订单/EBMS |

### 3.3 BOM

| 方法 | 路径 |
|------|------|
| GET | `/boms` | filter: rootPartNo, bomType, state |
| POST | `/boms` | 创建 |
| GET | `/boms/{bomId}` | |
| GET | `/boms/{bomId}/tree` | 树 |
| GET | `/boms/{bomId}/flat` | 扁平 |
| POST | `/boms/{bomId}/items` | 增行 |
| PATCH | `/boms/{bomId}/items/{itemId}` | |
| DELETE | `/boms/{bomId}/items/{itemId}` | |
| POST | `/boms/{bomId}/actions/release` | |
| POST | `/boms/{bomId}/actions/build-mbom` | |
| POST | `/boms/{bomId}/actions/build-sbom` | |
| GET | `/boms/{bomId}/versions` | |
| POST | `/boms/actions/extract` | `{ partNo, modelRef }` 3D 提取 |
| POST | `/boms/actions/compare` | `{ leftBomId, rightBomId }` |
| GET | `/parts/{partNo}/cbom` | **成本视图（EBMS 主用）** |
| GET | `/parts/{partNo}/boms/current` | 当前各类型指针 |

### 3.4 变更 ECR/ECN

| 方法 | 路径 |
|------|------|
| GET/POST | `/ecrs` `/ecns` |
| GET/PATCH | `/ecrs/{no}` `/ecns/{no}` |
| POST | `/ecns/{no}/actions/submit\|l1-approve\|l1-reject\|l2-approve\|l2-reject\|effect\|void` |
| GET | `/ecns/{no}/logs` |
| GET | `/ecns/{no}/impacts` |
| PUT | `/ecns/{no}/impacts` |
| POST | `/ecns/actions/compare` | 版本 diff |
| GET | `/ecns` + filter `partNo==` | 按料号查变更 |

### 3.5 文档 / 档案 / 文件 / 分享

| 方法 | 路径 |
|------|------|
| GET | `/parts/{partNo}/archive/tree` |
| POST | `/parts/{partNo}/archive/generate` |
| POST | `/files` | multipart 上传 |
| GET | `/files/{fileId}` |
| GET | `/files/{fileId}/download` |
| POST | `/files/{fileId}/actions/obsolete` |
| GET | `/parts/{partNo}/files` | filter docType/state |
| POST | `/share-links` |
| GET | `/share-links/{token}` | 公网受限 |

### 3.6 模具 / 工艺 / 品质 / 外协 / 贸易 / 3D

统一风格（列表+详情+actions），资源名:

```
/molds  /mold-trials  /sops  /dfm-reports  /inspection-standards
/outsource-requests  /trade-packages  /packing-calcs
/models3d  /explode-jobs  /explode-templates
```

每个资源至少具备:

- `GET /` 列表（通用 filter）  
- `GET /{id}`  
- `POST /` `PATCH /{id}`  
- `POST /{id}/actions/{action}`  
- **不**为统计再加 `/molds/stats` → 走 Metric  

### 3.7 系统

```
/auth/login  /auth/me  /auth/permissions
/users  /roles  /permissions  /dicts  /sequences
/operation-logs
/lifecycle/transitions  /lifecycle/gates
/work-items  /work-items/my
```

---

## 4. B. 通用查询 API（核心：防止接口膨胀）

### 4.1 统一查询

```http
POST /api/v1/query
Content-Type: application/json
```

```json
{
  "resource": "part",
  "fields": ["partNo", "nameZh", "lifecycleState", "versionNo", "productType"],
  "filter": {
    "and": [
      { "field": "lifecycleState", "op": "in", "value": ["RELEASED", "IN_PRODUCTION"] },
      { "field": "productType", "op": "eq", "value": "FL" }
    ]
  },
  "sort": [{ "field": "updatedAt", "dir": "desc" }],
  "page": 1,
  "pageSize": 50,
  "include": ["score", "currentEbomId"]
}
```

**白名单 resource**（可配置扩展，不必发版改路由）:

`part | project | bom | bom_item | ecn | ecr | file | mold | sop | inspection | outsource | issue | improve_action | work_item | dq_debt | code_issue_log | agent_action`

新增取数需求：**加资源白名单字段映射**，不加新 Controller。

### 4.2 全文检索

```http
GET /api/v1/search?q=投光灯&types=part,file,ecn&page=1
```

### 4.3 关系图（where-used / 影响面）

```http
POST /api/v1/graph/expand
{
  "start": { "type": "part", "id": "HJ-FL-100-001" },
  "edgeTypes": ["bom_parent", "bom_child", "ecn_impact", "project_link"],
  "depth": 2,
  "limit": 500
}
```

### 4.4 批量键取（集成高频）

```http
POST /api/v1/lookup
{
  "resource": "part",
  "keys": ["HJ-FL-100-001", "HJ-FL-100-002"],
  "keyField": "partNo",
  "fields": ["partNo", "lifecycleState", "versionNo", "nameZh"]
}
```

---

## 5. C. 度量 / KPI / OKR API（BI 与考核唯一出口）

### 5.1 指标目录

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/metrics/defs` | 指标字典 |
| GET | `/metrics/defs/{metricCode}` | |
| GET | `/kpis/defs` | KPI 字典 |
| GET | `/okrs/cycles` | OKR 周期 |

### 5.2 指标取数（万能）

```http
POST /api/v1/metrics/query
```

```json
{
  "metricCodes": ["M_DQ_SCORE", "M_ECN_CYCLE_H"],
  "timeRange": { "from": "2026-01-01", "to": "2026-07-22", "grain": "DAY" },
  "dimensions": ["productType", "dept"],
  "filters": { "productType": ["FL", "WL"] },
  "fill": "null"
}
```

响应:

```json
{
  "series": [
    {
      "metricCode": "M_DQ_SCORE",
      "points": [
        { "t": "2026-07-01", "dims": { "productType": "FL" }, "value": 91.2, "num": 91.2, "den": 1 }
      ]
    }
  ]
}
```

**以后任何趋势图、同比环比，只调这一个接口。**

### 5.3 KPI 值

```http
GET /api/v1/kpis/values?periodKey=2026-07&kpiCodes=KPI_DQ_SCORE,KPI_ECN_CYCLE_H
POST /api/v1/kpis/query
{
  "periodFrom": "2026-01",
  "periodTo": "2026-07",
  "kpiCodes": ["KPI_GATE_OTD"],
  "dims": {}
}
```

```http
PUT /api/v1/kpis/{kpiCode}/targets/{periodKey}   // 设目标（记 hist）
GET /api/v1/kpis/{kpiCode}/targets/history
```

### 5.4 OKR

```http
GET  /api/v1/okrs/cycles/{id}/tree
POST /api/v1/okrs/key-results/{krId}/checkins
POST /api/v1/okrs/sync-from-kpi          // 手动触发同步
GET  /api/v1/okrs/export?cycleId=
```

### 5.5 下钻（替代无数 detail 接口）

```http
POST /api/v1/metrics/drilldown
{
  "metricCode": "M_ECN_CYCLE_H",
  "timeRange": { "from": "2026-07-01", "to": "2026-07-22" },
  "dims": { "productType": "FL" },
  "page": 1,
  "pageSize": 50
}
```

返回贡献对象列表（ecnNo、cycleHours…），映射由 `plm_metric_lineage` 配置。

---

## 6. D. 分析 / 洞察 / 导出 API

### 6.1 数据集与图表（配置驱动）

| 方法 | 路径 |
|------|------|
| GET | `/analytics/datasets` |
| GET | `/analytics/datasets/{code}/data?params` | 或 POST body |
| GET | `/analytics/charts` |
| GET | `/analytics/charts/{code}` | 含 ECharts 模板 + 数据 |
| POST | `/analytics/charts/{code}/data` | 仅数据 |

**新增报表**: 插 `plm_analytics_dataset` + `chart` 行，**零代码接口**。

### 6.2 洞察

```http
GET  /api/v1/analytics/insights?status=NEW&severity=HIGH
POST /api/v1/analytics/insights/{insightNo}/actions/ack|dismiss|convert-issue
POST /api/v1/analytics/insights/generate     // 触发分析师作业
```

### 6.3 通用导出（BI/Excel 唯一通道）

```http
POST /api/v1/exports
{
  "exportType": "QUERY|METRIC|DATASET|RESOURCE",
  "format": "csv|json|xlsx",
  "name": "released-parts-2026H1",
  "spec": {
    "resource": "part",
    "filter": { "...": "..." },
    "fields": ["partNo", "nameZh", "versionNo", "lifecycleState"]
  },
  // 或 metricCodes + timeRange
  // 或 datasetCode + params
  "notify": true
}
```

```http
GET /api/v1/exports/{exportId}
GET /api/v1/exports/{exportId}/download
GET /api/v1/exports?status=DONE
```

异步任务；大结果进对象存储/NAS；链接时效 24–72h。

### 6.4 订阅推送（避免轮询新接口）

```http
POST /api/v1/subscriptions
{
  "name": "ecn-effective-to-ebms",
  "eventTypes": ["ecn.effective", "part.state_changed"],
  "targetUrl": "https://ebms/api/plm/webhook",
  "secret": "***",
  "filter": { "productType": ["FL"] }
}
```

---

## 7. E. 集成 API（EBMS / ERP / 外部）

> 路径固定 `/api/v1/integration/*`，**契约变更极慎重**。  
> 给 EBMS 的取数优先用本节 + C 层，不调 UI 专用接口。

### 7.1 主数据与状态

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/integration/parts/{partNo}` | 状态+版本+关键属性 |
| POST | `/integration/parts/lookup` | 批量 |
| GET | `/integration/parts/{partNo}/cbom` | 成本 BOM 展开 |
| GET | `/integration/parts/{partNo}/boms/{type}` | EBOM/MBOM/SBOM 只读快照 |
| GET | `/integration/parts/{partNo}/documents/summary` | 齐套摘要 |

### 7.2 项目与订单锁版本

| 方法 | 路径 |
|------|------|
| POST | `/integration/projects/upsert` |
| GET | `/integration/projects/{projectNo}` |
| POST | `/integration/orders/lock-version` | `{ orderNo, partNo, versionNo }` |
| GET | `/integration/orders/{orderNo}/lock` |
| POST | `/integration/orders/unlock` | 受控 |

### 7.3 变更与对账

| 方法 | 路径 |
|------|------|
| GET | `/integration/ecns` | filter since, partNo |
| GET | `/integration/ecns/{ecnNo}` |
| GET | `/integration/reconcile/daily` | `?date=2026-07-21` 哈希对账 |
| POST | `/integration/reconcile/run` | |

### 7.4 Webhook 事件目录（推送，减少拉接口）

| eventType | payload 关键字段 |
|-----------|------------------|
| `part.created` | partNo, category |
| `part.state_changed` | partNo, from, to, versionNo |
| `part.released` | partNo, versionNo |
| `bom.released` | bomId, rootPartNo, bomType, versionNo |
| `ecn.effective` | ecnNo, partNos[], versionMap |
| `dq.batch_completed` | batchNo, blockCount |
| `kpi.updated` | kpiCode, periodKey, actual, status |
| `insight.created` | insightNo, severity |
| `export.completed` | exportId |
| `gate.overdue` | projectNo, gateCode |

Payload 统一包壳:

```json
{
  "eventId": "uuid",
  "eventType": "ecn.effective",
  "occurredAt": "...",
  "data": {}
}
```

### 7.5 ERP 适配

```http
POST /integration/erp/parts/push
POST /integration/erp/mbom/push
GET  /integration/erp/prices?partNos=
```

---

## 8. F. 自动化：CodeGen / DQ / Agent / Issue

### 8.1 编号

```http
POST /api/v1/codegen/preview   { "objectType":"PART", "context":{...} }
POST /api/v1/codegen/allocate  { "objectType":"PART", "context":{...}, "objectId":"optional" }
GET  /api/v1/codegen/rules
GET  /api/v1/codegen/logs?generatedCode=
```

### 8.2 自检

```http
POST /api/v1/dq/run
{
  "objectType": "PART",
  "objectId": "HJ-FL-100-001",
  "trigger": "MANUAL",
  "ruleCodes": null
}
GET /api/v1/dq/scores?objectType=PART&objectIds=a,b
GET /api/v1/dq/debts?status=OPEN
POST /api/v1/dq/debts/{id}/actions/fix|waive|accept
POST /api/v1/dq/autofix { objectType, objectId, fixCode }
GET /api/v1/dq/rules
```

发布类 action 若 DQ BLOCK → HTTP 409 + `errorCode=DQ_BLOCKED` + `data.runs[]`。

### 8.3 问题改善

```http
GET/POST /api/v1/issues
GET/PATCH /api/v1/issues/{issueNo}
POST /api/v1/issues/{issueNo}/analyses
GET/POST /api/v1/issues/{issueNo}/actions
POST /api/v1/improve-actions/{actionNo}/actions/complete
POST /api/v1/issues/{issueNo}/actions/verify
GET  /api/v1/improve-results?issueNo=
GET/POST /api/v1/standard-works
```

### 8.4 智能体

```http
GET  /api/v1/agents
POST /api/v1/agents/{agentCode}/sessions
POST /api/v1/agents/sessions/{sessionId}/messages
{ "content": "检查该料号能否发布", "context": { "partNo": "..." } }

GET  /api/v1/agents/sessions/{sessionId}
GET  /api/v1/agents/sessions/{sessionId}/messages
GET  /api/v1/agents/actions?status=PROPOSED
POST /api/v1/agents/actions/{id}/decide  { "decision":"APPROVED|REJECTED", "comment":"" }
POST /api/v1/agents/jobs/nightly-inspect
```

智能体 **禁止**直连数据库；只调上述 API（tool allowlist）。

---

## 9. 生命周期通用动作（减少每对象一套 status 接口）

```http
POST /api/v1/lifecycle/transition
{
  "objectType": "PART",
  "objectId": "HJ-FL-100-001",
  "action": "release",
  "comment": "结构评审通过",
  "force": false
}
GET /api/v1/lifecycle/history?objectType=PART&objectId=
GET /api/v1/lifecycle/transitions?objectType=PART
```

领域 `actions/*` 内部均调同一引擎，对外保持双路径兼容。

---

## 10. 取数场景 → 接口映射表（给实施/BI）

| 取数需求 | 用哪个接口 | 不要做什么 |
|----------|------------|------------|
| 料号详情/状态 | `GET /parts/{partNo}` 或 lookup | 新造 /getPartStatus |
| 批量状态 | `POST /lookup` 或 `/integration/parts/lookup` | 循环打详情打爆网关 |
| 订单算成本 | `GET /integration/parts/{partNo}/cbom` | 复制 BOM 到 EBMS 后改 |
| 列表筛选 | `GET /parts` 或 `POST /query` | 每个筛选项一个 API |
| 任意报表 | dataset + chart 配置 或 `/metrics/query` | `/report/xxx` |
| 趋势/同比 | `/metrics/query` | 新 trend 接口 |
| KPI 看板 | `/kpis/values` `/kpis/query` | 写死聚合 SQL 在前端 |
| OKR 进度 | `/okrs/cycles/{id}/tree` | 另建 OKR 微服务接口 |
| Excel 给领导 | `/exports` | 数据库只读账号 |
| 变更通知 | Webhook `ecn.effective` | 每 5 分钟全表拉 ECN |
| 质量债务 | `/dq/debts` + metric | 另做质量微服务 |
| where-used | `/parts/{}/where-used` 或 `/graph/expand` | 递归查 BOM 客户端算 |
| 智能体要数据 | 只调 v1 API tools | Agent 拼 SQL |
| 历史快照 | `/parts/{}/versions/{}` | 拷贝表外挂 |

---

## 11. OpenAPI / SDK / 兼容策略

### 11.1 产物

| 产物 | 路径 |
|------|------|
| OpenAPI 3 | `GET /api/v1/openapi.json` |
| Knife4j/Swagger UI | `/api/doc.html` |
| 事件目录 | `GET /api/v1/integration/event-catalog` |
| 指标目录 | `GET /api/v1/metrics/defs` |
| 查询资源目录 | `GET /api/v1/query/resources` | 白名单与可过滤字段 |

### 11.2 客户端 SDK（建议生成）

- TypeScript（前端 + EBMS）  
- 可选 Java/C#  

从 OpenAPI 自动生成，禁止手写第二套 path 常量长期分叉。

### 11.3 兼容

| 变更类型 | 策略 |
|----------|------|
| 新增字段/接口 | 直接加，旧客户端忽略 |
| 字段废弃 | 标 `deprecated` ≥ 6 个月 |
| 删除/改语义 | 新版本 `/api/v2` |
| 枚举新增 | 允许；客户端要容错未知枚举 |

### 11.4 废弃 PLM-2 旧路径映射（兼容层）

| 旧 | 新 |
|----|----|
| `/api/material/**` | `/api/v1/parts/**` |
| `/api/bom/**` | `/api/v1/boms/**` |
| `/api/ecn/**` | `/api/v1/ecns/**` |
| `/api/dashboard/stats` | `/api/v1/metrics/query` + 预置 metric |

旧路径 **Gateway 转发 12 个月**，响应头 `X-Deprecated-Export: true`。

---

## 12. 权限 Scope 矩阵（集成）

| Scope | 能力 |
|-------|------|
| `part:read` | parts/query/lookup |
| `part:write` | 创建变更（慎授） |
| `bom:read` | bom/cbom |
| `ecn:read` | ecn |
| `metric:read` | metrics/kpis |
| `okr:read` | okrs |
| `okr:write` | checkin/target |
| `export:run` | exports |
| `dq:read` | dq scores/debts |
| `agent:use` | agent sessions |
| `integration:admin` | webhook 配置、对账 |

EBMS 推荐最小集: `part:read bom:read ecn:read metric:read export:run` + 订阅事件。

---

## 13. 非功能

| 项 | 要求 |
|----|------|
| 追踪 | `X-Request-Id` 全链路；日志同号 |
| 缓存 | GET 详情 ETag / Cache-Control；metric 查询可 Redis 60s |
| 超时 | 同步 ≤ 30s；导出/爆炸异步 |
| 敏感 | 成本价字段 scope `cost:read`；外网分享独立鉴权 |
| 审计 | 所有写 + 导出 + 集成拉取记 operation_log |
| 测试 | contract 测试基于 OpenAPI；核心集成用例 CBOM/lock/webhook |

---

## 14. 实施清单（后端模块）

| 包 | 负责 |
|----|------|
| `api.common` | 响应、分页、filter 解析、幂等、限流 |
| `api.query` | 通用 query/lookup/search/graph |
| `api.metric` | metrics/kpis/okrs |
| `api.analytics` | dataset/chart/insight/export |
| `api.integration` | EBMS/ERP/webhook |
| `api.agent` | agents |
| `api.dq` / `api.codegen` | 自检与编号 |
| 领域 `part/bom/ecn/...` | 资源与 actions |
| `openapi` | springdoc 生成 |

**Code review 门禁**: 新增 `@GetMapping` 若路径匹配 `/report/**`、`/stats/**`、`/dashboard/**` 且非转调 metric/query → **拒合**。

---

## 15. 最小可用接口集（MVP 上线必须齐）

第一期就上、避免返工：

1. `/parts` CRUD + actions + versions  
2. `/boms` tree/flat + release + `/parts/{}/cbom`  
3. `/ecns` 审批 actions + logs  
4. `/codegen/allocate|preview`  
5. `/dq/run` + 发布 BLOCK  
6. `/query` + `/lookup`  
7. `/metrics/defs` + `/metrics/query` + `/kpis/values`  
8. `/exports`  
9. `/integration/parts/{}/*` + `/integration/orders/lock-version`  
10. Webhook 订阅 `part.state_changed` `ecn.effective`  
11. `/work-items/my`  
12. OpenAPI 导出  

智能体、OKR 树、完整 analytics 可二期，但 **C/D/E 骨架一期不可缺**，否则必然再挖接口。

---

## 16. 总结

> **领域 API 管对象，Query/Lookup 管清单，Metric/KPI 管数，Export 管表，Webhook 管变。**  
> 遵守这五条，后面接 BI、OKR、EBMS、智能体都不用「再加一套取数接口」。

---

## 附录 A — 快速索引

| 前缀 | 用途 |
|------|------|
| `/api/v1/parts` | 物料产品 |
| `/api/v1/boms` | 结构 |
| `/api/v1/ecns` | 变更 |
| `/api/v1/query` | 通用列表 |
| `/api/v1/metrics` | 指标 |
| `/api/v1/kpis` | KPI |
| `/api/v1/okrs` | OKR |
| `/api/v1/analytics` | 数据集图表洞察 |
| `/api/v1/exports` | 导出 |
| `/api/v1/integration` | 系统对接 |
| `/api/v1/dq` | 自检 |
| `/api/v1/codegen` | 编号 |
| `/api/v1/agents` | 智能体 |
| `/api/v1/issues` | 改善 |
| `/api/v1/lifecycle` | 通用流转 |
| `/api/v1/openapi.json` | 契约 |

## 附录 B — 关联文档

- `docs/plm-complete-redesign-lighting-sme-v1.1.md`  
- `docs/ebms-plm-integration-masterplan.md`  
- `database/10_v1_1_lean_agent_kpi.sql`
